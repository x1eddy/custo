package app.custo.feature.widgets

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import app.custo.MainActivity
import app.custo.R
import app.custo.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Inexact, non-waking alarms for widgets that change at known moments (end of work, next prayer),
 * the photo carousel, and water reminders. Nothing here ticks faster than every 15 minutes.
 */
object WidgetAlarms {
    const val ACTION_REFRESH = "app.custo.widgets.REFRESH"
    const val ACTION_CAROUSEL = "app.custo.widgets.CAROUSEL"
    const val ACTION_WATER = "app.custo.widgets.WATER_REMINDER"
    const val ACTION_ADD_SIP = "app.custo.widgets.ADD_SIP"
    const val EXTRA_KIND = "kind"
    private const val WATER_CHANNEL = "water"
    private const val WATER_NOTIFICATION = 7301

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun pi(ctx: Context, code: Int, intent: Intent) =
        PendingIntent.getBroadcast(ctx, code, intent.setClass(ctx, WidgetTickReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Refreshes [kind] around [atMillis] (inexact; fine for minute-level text). One alarm per widget. */
    fun refreshAt(ctx: Context, appWidgetId: Int, kind: WidgetKind, atMillis: Long) {
        val intent = Intent(ACTION_REFRESH).putExtra(EXTRA_KIND, kind.name)
        am(ctx).set(AlarmManager.RTC, atMillis + 5_000, pi(ctx, 10_000 + appWidgetId, intent))
    }

    fun startCarousel(ctx: Context) {
        am(ctx).setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi(ctx, 1, Intent(ACTION_CAROUSEL)),
        )
    }

    fun stopCarousel(ctx: Context) = am(ctx).cancel(pi(ctx, 1, Intent(ACTION_CAROUSEL)))

    /** Arms (or cancels) the next water reminder from the saved settings. */
    fun scheduleWater(ctx: Context, data: WaterData) {
        val p = pi(ctx, 2, Intent(ACTION_WATER))
        if (!data.remind) { am(ctx).cancel(p); return }
        val next = HabitLogic.nextReminder(LocalDateTime.now(), data.everyHours, data.fromHour, data.toHour)
        am(ctx).set(AlarmManager.RTC, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), p)
    }

    /** Android 13+ needs the runtime permission; before that only the app's notification switch matters. */
    fun canNotify(ctx: Context) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    internal fun showWaterReminder(ctx: Context, data: WaterData) {
        if (!canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(WATER_CHANNEL, "Water reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val today = LocalDate.now().toEpochDay()
        val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val add = pi(ctx, 4, Intent(ACTION_ADD_SIP))
        val n = NotificationCompat.Builder(ctx, WATER_CHANNEL)
            .setSmallIcon(R.drawable.ic_drop)
            .setContentTitle("Time for some water")
            .setContentText("${data.count(today)} of ${data.goal} glasses so far today")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_add, "+1 glass", add)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(ctx).notify(WATER_NOTIFICATION, n)
    }

    internal fun clearWaterReminder(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(WATER_NOTIFICATION)
}

/** Handles the alarms above, the reminder's "+1 glass" button, and re-arms after a reboot. */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(ctx: Context, intent: Intent) {
        val habits = ctx.container.habits
        when (intent.action) {
            WidgetAlarms.ACTION_REFRESH -> intent.getStringExtra(WidgetAlarms.EXTRA_KIND)
                ?.let { runCatching { WidgetKind.valueOf(it) }.getOrNull() }
                ?.let { Widgets.refresh(ctx, it) }
            WidgetAlarms.ACTION_CAROUSEL -> Carousel.rotateDue(ctx)
            WidgetAlarms.ACTION_WATER -> {
                val data = habits.waterNow()
                if (data.count(LocalDate.now().toEpochDay()) < data.goal) WidgetAlarms.showWaterReminder(ctx, data)
                WidgetAlarms.scheduleWater(ctx, data)
            }
            WidgetAlarms.ACTION_ADD_SIP -> {
                habits.addSip(Drink.WATER)
                WidgetAlarms.clearWaterReminder(ctx)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WidgetAlarms.scheduleWater(ctx, habits.waterNow())
                if (GlanceAppWidgetManager(ctx).getGlanceIds(CarouselWidget::class.java).isNotEmpty()) WidgetAlarms.startCarousel(ctx)
                Widgets.refresh(ctx, WidgetKind.WORK)
                Widgets.refresh(ctx, WidgetKind.PRAYER)
            }
        }
    }
}

/** Photo carousel rotation, shared by the alarm and taps. */
object Carousel {
    val INDEX = androidx.datastore.preferences.core.intPreferencesKey("carousel_index")
    val AT = androidx.datastore.preferences.core.longPreferencesKey("carousel_at")

    suspend fun rotateDue(ctx: Context) {
        val gm = GlanceAppWidgetManager(ctx)
        val ids = gm.getGlanceIds(CarouselWidget::class.java)
        if (ids.isEmpty()) { WidgetAlarms.stopCarousel(ctx); return }
        val now = System.currentTimeMillis()
        ids.forEach { id ->
            val style = Widgets.style(ctx, id)
            var changed = false
            updateAppWidgetState(ctx, id) { p ->
                if (HabitLogic.carouselDue(p[AT] ?: 0L, now, style.rotateMinutes)) {
                    p[INDEX] = HabitLogic.carousel(p[INDEX] ?: 0, 1, style.photos.size)
                    p[AT] = now
                    changed = true
                }
            }
            if (changed) CarouselWidget().update(ctx, id)
        }
    }
}
