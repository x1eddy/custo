package app.custo.feature.themes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.custo.container
import app.custo.feature.systemui.MonetStyle
import app.custo.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

/** Settings for the day/night wallpaper switch. */
data class WallSchedule(
    val on: Boolean = false,
    val day: Look? = null,
    val night: Look? = null,
    val dayAt: Int = 7 * 60,
    val nightAt: Int = 20 * 60,
    val colors: Boolean = true,
)

/**
 * Switches between a day and a night look at set times using inexact, non-waking alarms
 * (they fire the next time the phone is awake). Re-armed after boot and clock changes.
 */
object WallScheduler {
    const val ACTION_SWITCH = "app.custo.action.WALL_SWITCH"

    suspend fun load(context: Context): WallSchedule {
        val p = context.container.prefs.data.first()
        return WallSchedule(
            on = p[ThemeKeys.schedOn] ?: false,
            day = LookCodec.decode(p[ThemeKeys.schedDay]),
            night = LookCodec.decode(p[ThemeKeys.schedNight]),
            dayAt = p[ThemeKeys.schedDayAt] ?: 7 * 60,
            nightAt = p[ThemeKeys.schedNightAt] ?: 20 * 60,
            colors = p[ThemeKeys.schedColors] ?: true,
        )
    }

    suspend fun save(context: Context, s: WallSchedule) {
        context.container.prefs.edit { p ->
            p[ThemeKeys.schedOn] = s.on
            s.day?.let { p[ThemeKeys.schedDay] = LookCodec.encode(it) } ?: p.remove(ThemeKeys.schedDay)
            s.night?.let { p[ThemeKeys.schedNight] = LookCodec.encode(it) } ?: p.remove(ThemeKeys.schedNight)
            p[ThemeKeys.schedDayAt] = s.dayAt
            p[ThemeKeys.schedNightAt] = s.nightAt
            p[ThemeKeys.schedColors] = s.colors
            p.remove(ThemeKeys.schedLast)
        }
        sync(context, force = true)
    }

    private fun minuteNow(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** Applies the current slot's look if it changed (or [force]), then arms the next switch. */
    suspend fun sync(context: Context, force: Boolean = false) {
        val s = load(context)
        arm(context, s)
        if (!s.on) return
        val day = WallLogic.isDay(minuteNow(), s.dayAt, s.nightAt)
        val slot = if (day) "day" else "night"
        val last = context.container.prefs.get(ThemeKeys.schedLast, "")
        if (!force && last == slot) return
        val look = (if (day) s.day else s.night) ?: return
        apply(context, look, s.colors)
        context.container.prefs.set(ThemeKeys.schedLast, slot)
    }

    private suspend fun apply(context: Context, look: Look, colors: Boolean) {
        val c = context.container
        if (LiveWallpaperService.isActive(context)) {
            // Keep the live wallpaper running and just swap what it draws.
            c.prefs.set(ThemeKeys.liveSpec, look.wall.encode())
        } else {
            c.wallpapers.apply(look.wall, WallTarget.BOTH)
        }
        if (colors) {
            val ready = withTimeoutOrNull(4000) { c.shizuku.state.first { it == ShizukuState.READY } } != null
            if (ready) runCatching { c.tweaks.applyMonet(look.seed, MonetStyle.of(look.style)) }
        }
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, WallScheduleReceiver::class.java).setAction(ACTION_SWITCH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun arm(context: Context, s: WallSchedule) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context)
        am.cancel(pi)
        if (!s.on || (s.day == null && s.night == null)) return
        val minutes = WallLogic.minutesUntilSwitch(minuteNow(), s.dayAt, s.nightAt)
        val at = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, minutes)
        }.timeInMillis + 15_000L
        // Inexact and non-waking: no exact-alarm permission, no wakeups.
        am.set(AlarmManager.RTC, at, pi)
    }
}

/** Handles the switch alarm, boot, app updates and clock changes. */
class WallScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { runCatching { WallScheduler.sync(context) } } finally { pending.finish() }
        }
    }

    private companion object {
        val HANDLED = setOf(
            WallScheduler.ACTION_SWITCH, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
