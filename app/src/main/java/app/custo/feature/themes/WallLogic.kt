package app.custo.feature.themes

import app.custo.feature.systemui.Tonal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/** Pure helpers for wallpapers, the scheduler and live reactions (unit-tested, no Android). */
object WallLogic {

    // ---- Colors ----

    fun lerpColor(a: Int, b: Int, f: Float): Int {
        val t = f.coerceIn(0f, 1f)
        fun ch(shift: Int) = (((a shr shift) and 0xFF) + (((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * t).roundToInt()
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Samples a palette as a smooth ramp, f in 0..1. */
    fun sample(colors: List<Int>, f: Float): Int {
        if (colors.isEmpty()) return 0xFF000000.toInt()
        if (colors.size == 1) return colors[0]
        val x = f.coerceIn(0f, 1f) * (colors.size - 1)
        val i = x.toInt().coerceAtMost(colors.size - 2)
        return lerpColor(colors[i], colors[i + 1], x - i)
    }

    fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

    private fun adjust(color: Int, hueShift: Float = 0f, satMul: Float = 1f, lightMul: Float = 1f): Int {
        val (h, s, l) = Tonal.toHsl(color)
        val rgb = Tonal.fromHsl(((h + hueShift) % 360f + 360f) % 360f, (s * satMul).coerceIn(0f, 1f), (l * lightMul).coerceIn(0f, 1f))
        return (rgb and 0x00FFFFFF) or (color and 0xFF000000.toInt())
    }

    private fun bump(x: Float, center: Float, width: Float): Float {
        val d = x - center
        return exp(-(d * d) / (2 * width * width))
    }

    /**
     * Shifts a color with the time of day: unchanged around midday, warmer at sunrise and sunset,
     * darker and calmer at night.
     */
    fun timeOfDay(color: Int, minuteOfDay: Int): Int {
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        val night = ((1 - cos(2 * PI * (m - 13 * 60) / 1440.0)) / 2).toFloat() // 0 at 13:00, 1 at 01:00
        val warm = max(bump(m.toFloat(), 390f, 70f), bump(m.toFloat(), 1140f, 70f))
        val (h, _, _) = Tonal.toHsl(color)
        // Rotate toward orange (30°) by the shortest way.
        val toOrange = (((30f - h) % 360f + 540f) % 360f) - 180f
        return adjust(color, hueShift = toOrange * 0.18f * warm, satMul = 1f - 0.25f * night, lightMul = 1f - 0.45f * night)
    }

    /** Fades color out as the battery drains: full color above 50%, washed out near empty. */
    fun battery(color: Int, level: Int): Int {
        val f = (level / 50f).coerceIn(0f, 1f)
        return adjust(color, satMul = 0.3f + 0.7f * f, lightMul = 0.8f + 0.2f * f)
    }

    // ---- Day/night schedule ----

    /** Whether [minute] (0..1439) falls in the day slot that starts at [dayAt] and ends at [nightAt]. */
    fun isDay(minute: Int, dayAt: Int, nightAt: Int): Boolean = when {
        dayAt == nightAt -> true
        dayAt < nightAt -> minute in dayAt until nightAt
        else -> minute >= dayAt || minute < nightAt
    }

    /** Minutes from [minute] until the next switch (1..1440). */
    fun minutesUntilSwitch(minute: Int, dayAt: Int, nightAt: Int): Int {
        fun until(target: Int): Int {
            val d = ((target - minute) % 1440 + 1440) % 1440
            return if (d == 0) 1440 else d
        }
        return minOf(until(dayAt), until(nightAt))
    }

    fun formatMinute(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
}
