package app.custo.feature.wallhaven

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.custo.MainActivity
import app.custo.R
import app.custo.container
import app.custo.feature.widgets.WidgetAlarms
import kotlinx.coroutines.CancellationException

/**
 * Downloads an offline pack in the background: searches wallhaven page by page (portrait first),
 * saves each new SFW wallpaper compacted, and stops at the requested count. Resumable: wallpapers
 * already on the phone are skipped, and a retry after a network drop continues from there.
 */
class PackDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = applicationContext.container.wallhaven
        val pack = WallPacks.byId(inputData.getString(KEY_PACK)) ?: return Result.failure()
        val target = inputData.getInt(KEY_COUNT, 20).coerceIn(1, 200)
        val notifId = NOTIF_BASE + WallPacks.all.indexOf(pack)

        val saved = repo.savedNow()
        var done = saved.count { it.packId == pack.id }
        val skip = saved.mapTo(HashSet()) { it.id }
        var need = PackPlanner.remaining(target, done)

        fun progress(state: PackProgress.State, msg: String? = null) =
            repo.reportProgress(PackProgress(pack.id, state, done, target, msg))

        progress(PackProgress.State.RUNNING)
        notify(notifId, pack, done, target)
        var failures = 0
        try {
            val tagId = pack.tagId
            // Most-favourited first; strict favourites floor, then looser ones only if wallhaven runs short.
            floors@ for (floor in WhQuality.floors(pack.minFavorites)) {
                for (portrait in PackPlanner.phases()) {
                    var page = 1
                    var lastPage = 1
                    while (need > 0 && page <= lastPage && page <= MAX_PAGES) {
                        val res = repo.search(pack.query, pack.filters(repo.defaultFilters().atLeast, portrait), page)
                        lastPage = res.lastPage
                        val good = res.items.filter { WhQuality.keep(it, floor) }
                        for (item in PackPlanner.pick(good, skip, Int.MAX_VALUE)) {
                            if (need <= 0) break
                            skip += item.id
                            // Memes, collages, screenshots and pin-ups carry the franchise tag too: check the tags.
                            if (!repo.passesTagCheck(item, tagId)) continue
                            try {
                                repo.save(item, packId = pack.id, query = pack.query)
                                done++
                                need--
                                failures = 0
                            } catch (e: WallhavenException) {
                                // A missing or unreadable image is skipped; losing the network stops the run.
                                if (e.error == WhError.OFFLINE || e.error == WhError.RATE_LIMITED) throw e
                                if (++failures > MAX_FAILURES_IN_ROW) throw e
                            } catch (e: IllegalArgumentException) {
                                // Not SFW general/anime after all: skip.
                            }
                            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to target))
                            progress(PackProgress.State.RUNNING)
                            notify(notifId, pack, done, target)
                        }
                        // Sorted by favourites: once a page dips below the floor, later pages are all below it.
                        if (res.items.any { it.favorites < floor }) break
                        page++
                    }
                    if (need <= 0) break@floors
                }
            }
            progress(PackProgress.State.DONE, if (need > 0) "wallhaven had $done" else null)
            nm().cancel(notifId)
            return Result.success(workDataOf(KEY_DONE to done, KEY_TOTAL to target))
        } catch (e: CancellationException) {
            nm().cancel(notifId)
            // Cancelled by the user (the repository already shows that) or stopped by the system,
            // in which case WorkManager runs it again later.
            if (repo.packProgress.value[pack.id]?.state != PackProgress.State.CANCELLED) progress(PackProgress.State.QUEUED, "Paused")
            throw e
        } catch (e: WallhavenException) {
            nm().cancel(notifId)
            return if ((e.error == WhError.OFFLINE || e.error == WhError.RATE_LIMITED || e.error == WhError.SERVER) && runAttemptCount < MAX_RETRIES) {
                progress(PackProgress.State.QUEUED, e.error.message)
                Result.retry()
            } else {
                progress(PackProgress.State.FAILED, e.error.message)
                Result.failure()
            }
        } catch (e: Exception) {
            nm().cancel(notifId)
            progress(PackProgress.State.FAILED, e.message?.take(120))
            return Result.failure()
        }
    }

    private fun nm() = applicationContext.getSystemService(NotificationManager::class.java)

    private fun notify(notifId: Int, pack: WallPack, done: Int, total: Int) {
        if (!WidgetAlarms.canNotify(applicationContext)) return
        val nm = nm()
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Wallpaper downloads", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            applicationContext, notifId,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_photo)
            .setContentTitle(pack.title)
            .setContentText("$done of $total wallpapers")
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Cancel", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        runCatching { nm.notify(notifId, n) }
    }

    companion object {
        const val TAG = "wh_pack"
        const val KEY_PACK = "pack"
        const val KEY_COUNT = "count"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val CHANNEL = "wallpaper_downloads"
        private const val NOTIF_BASE = 7300
        private const val MAX_PAGES = 12
        private const val MAX_FAILURES_IN_ROW = 6
        private const val MAX_RETRIES = 5
    }
}
