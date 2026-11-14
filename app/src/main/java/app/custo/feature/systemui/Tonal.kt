package app.custo.feature.systemui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Builds a Monet-shaped 13-step tonal ramp (0..1000) from one color, keeping its hue and saturation. */
object Tonal {
    val SHADES = intArrayOf(0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)

    fun ramp(argb: Int): Map<Int, Int> {
        val (h, s, _) = toHsl(argb)
        return SHADES.associateWith { shade ->
            val lightness = 1f - shade / 1000f
            // Desaturate the extremes so near-white/near-black stay neutral, like Monet does.
            val sat = s * (1f - abs(lightness - 0.5f) * 0.6f)
            fromHsl(h, sat, lightness)
        }
    }

    fun toHsl(argb: Int): Triple<Float, Float, Float> {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        if (mx == mn) return Triple(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return Triple(h, s, l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = ((v + m).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
