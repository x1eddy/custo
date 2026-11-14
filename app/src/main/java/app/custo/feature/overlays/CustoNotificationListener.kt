package app.custo.feature.overlays

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.datastore.preferences.core.Preferences
import app.custo.container
import app.custo.data.Keys
import app.custo.feature.widgets.MediaRepo
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Feeds the overlays: edge lighting and island triggers, unread notifications for the
 * notification light, running timers, and now playing. Also keeps the Now playing widget in
 * sync. Notification content is never stored or sent anywhere.
 */
class CustoNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var msm: MediaSessionManager? = null
    private val controllers = mutableListOf<MediaController>()
    private var pendingRefresh: Job? = null
    private var prefs: Preferences? = null
    private var connected = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> track(list ?: emptyList()) }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refreshMedia()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshMedia()
    }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            container.prefs.data.collectLatest { p ->
                val before = prefs
                prefs = p
                // Turning a feature on picks up what is already in the shade.
                if (connected && (before == null || before[OverlayKeys.notifLight] != p[OverlayKeys.notifLight] ||
                        before[OverlayKeys.island] != p[OverlayKeys.island])) scan()
            }
        }
    }

    override fun onListenerConnected() {
        connected = true
        OverlayBus.listenerRunning.value = true
        val m = getSystemService(MediaSessionManager::class.java)
        msm = m
        val me = ComponentName(this, CustoNotificationListener::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, me)
            track(m.getActiveSessions(me))
        }
        scan()
    }

    override fun onListenerDisconnected() {
        connected = false
        OverlayBus.listenerRunning.value = false
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { it.unregisterCallback(mediaCallback) }
        controllers.clear()
        OverlayBus.unread.value = emptyMap()
        OverlayBus.timer.value = null
        OverlayBus.media.value = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun track(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(mediaCallback) }
        controllers.clear()
        list.forEach { it.registerCallback(mediaCallback); controllers += it }
        refreshMedia()
    }

    /** Debounced so a burst of metadata updates causes one widget redraw. */
    private fun refreshMedia() {
        pendingRefresh?.cancel()
        pendingRefresh = scope.launch {
            delay(300)
            Widgets.refresh(this@CustoNotificationListener, WidgetKind.MEDIA)
            OverlayBus.media.value = if (prefs?.get(OverlayKeys.island) == true) {
                MediaRepo.nowPlaying(this@CustoNotificationListener)?.let { IslandMedia(it.title, it.artist, it.art, it.playing) }
            } else null
        }
    }

    private fun isAlerting(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (sbn.packageName == packageName) return false
        if (sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        return n.category != Notification.CATEGORY_TRANSPORT && n.category != Notification.CATEGORY_PROGRESS
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (isAlerting(sbn)) {
            val p = prefs
            if (p?.get(Keys.edgeEnabled) == true) {
                OverlayBus.edge.tryEmit(EdgeEvent(sbn.packageName, Apps.colorFor(this, sbn.packageName, n.color)))
            }
            if (p?.get(OverlayKeys.island) == true && p[OverlayKeys.islandNotifs] != false) {
                val ex = n.extras
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: Apps.label(this, sbn.packageName)
                val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                OverlayBus.islandNotif.tryEmit(IslandNotif(sbn.packageName, title, text, n.getLargeIcon(), n.contentIntent))
            }
        }
        scan()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = scan()

    /** Recomputes unread notifications and the running chronometer from the shade. */
    private fun scan() {
        val p = prefs ?: return
        val wantUnread = p[OverlayKeys.notifLight] == true
        val wantTimer = p[OverlayKeys.island] == true && p[OverlayKeys.islandTimer] != false
        if (!wantUnread && !wantTimer) {
            OverlayBus.unread.value = emptyMap(); OverlayBus.timer.value = null
            return
        }
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        if (wantUnread) {
            val map = LinkedHashMap<String, Int>()
            active.filter { isAlerting(it) && it.isClearable }.forEach { sbn ->
                if (sbn.packageName !in map) map[sbn.packageName] = Apps.colorFor(this, sbn.packageName, sbn.notification.color)
            }
            if (map != OverlayBus.unread.value) OverlayBus.unread.value = map
        } else OverlayBus.unread.value = emptyMap()

        OverlayBus.timer.value = if (wantTimer) {
            active.firstOrNull { it.notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) }?.let {
                IslandTimer(it.packageName, it.notification.`when`, it.notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
            }
        } else null
    }
}
