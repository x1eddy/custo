package app.custo.feature.widgetsets

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Colors for a set whose background is a real wallpaper: the set keeps its character (light or
 * dark cards) but takes the wallpaper's hues, and text/accents are pushed until they stay readable.
 * Pure Kotlin (no android.graphics) so it is unit-tested.
 */
object SetPaletteMath {
    /** WCAG minimums used here: body text, and large text / bars / outlines. */
    const val TEXT_CONTRAST = 4.5
    const val ACCENT_CONTRAST = 3.0

    // ---------- Color basics ----------

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    fun luminance(c: Int): Double {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(r(c)) + 0.7152 * lin(g(c)) + 0.0722 * lin(b(c))
    }

    /** WCAG contrast ratio, 1 to 21. */
    fun contrast(x: Int, y: Int): Double {
        val la = luminance(x); val lb = luminance(y)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** [h] 0..360, [s] and [l] 0..1. */
    data class Hsl(val h: Float, val s: Float, val l: Float)

    fun toHsl(c: Int): Hsl {
        val rf = r(c) / 255f; val gf = g(c) / 255f; val bf = b(c) / 255f
        val mx = max(rf, max(gf, bf)); val mn = min(rf, min(gf, bf))
        val l = (mx + mn) / 2f
        if (mx == mn) return Hsl(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            rf -> ((gf - bf) / d + (if (gf < bf) 6f else 0f))
            gf -> ((bf - rf) / d + 2f)
            else -> ((rf - gf) / d + 4f)
        } * 60f
        return Hsl(h, s, l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Int {
        val ss = s.coerceIn(0f, 1f); val ll = l.coerceIn(0f, 1f)
        val c = (1f - abs(2f * ll - 1f)) * ss
        val hp = (((h % 360f) + 360f) % 360f) / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1 -> Triple(c, x, 0f)
            hp < 2 -> Triple(x, c, 0f)
            hp < 3 -> Triple(0f, c, x)
            hp < 4 -> Triple(0f, x, c)
            hp < 5 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = ll - c / 2f
        return rgb(((r1 + m) * 255f).roundToInt(), ((g1 + m) * 255f).roundToInt(), ((b1 + m) * 255f).roundToInt())
    }

    /**
     * [fg] made readable on [bg]: its lightness is moved away from [bg] (darker on light cards,
     * lighter on dark ones) until the contrast reaches [min]; black or white as the last resort.
     */
    fun ensureContrast(fg: Int, bg: Int, min: Double): Int {
        if (contrast(fg, bg) >= min) return fg
        val hsl = toHsl(fg)
        val darker = luminance(bg) > 0.18
        var l = hsl.l
        repeat(25) {
            l = if (darker) l - 0.04f else l + 0.04f
            val c = fromHsl(hsl.h, hsl.s, l)
            if (contrast(c, bg) >= min) return c
            if (l <= 0f || l >= 1f) return@repeat
        }
        val bw = if (darker) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val other = if (darker) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        return if (contrast(bw, bg) >= contrast(other, bg)) bw else other
    }

    // ---------- Deriving a set palette ----------

    /** The palette for [base] on a wallpaper whose main colors are [wall] (most common first). */
    fun derive(base: ThemePalette, wall: List<Int>): ThemePalette {
        val colors = wall.map { it or (0xFF shl 24) }
        if (colors.isEmpty()) return base
        val dominant = colors.first()
        val vivid = colors.filter { toHsl(it).l in 0.2f..0.85f }.maxByOrNull { toHsl(it).s } ?: dominant
        val second = colors.getOrNull(1) ?: vivid
        val lightCards = luminance(base.background) > 0.35

        val dh = toHsl(dominant)
        val background = fromHsl(dh.h, min(dh.s, 0.55f), if (lightCards) 0.91f else 0.13f)

        val textHue = toHsl(vivid)
        val text = ensureContrast(
            fromHsl(textHue.h, min(textHue.s, 0.5f), if (lightCards) 0.16f else 0.95f),
            background, TEXT_CONTRAST,
        )
        val vh = toHsl(vivid)
        val accent = ensureContrast(fromHsl(vh.h, max(vh.s, 0.55f), vh.l.coerceIn(0.4f, 0.62f)), background, ACCENT_CONTRAST)
        val sh = toHsl(second)
        val secondary = fromHsl(sh.h, min(max(sh.s, 0.3f), 0.65f), if (lightCards) 0.72f else 0.32f)
        val line = if (base.line == base.text) text
        else ensureContrast(fromHsl(textHue.h, min(textHue.s, 0.6f), if (lightCards) 0.25f else 0.8f), background, ACCENT_CONTRAST)
        return ThemePalette(background = background, text = text, accent = accent, secondary = secondary, line = line)
    }

    /** The color frames are washed with: the wallpaper's main hue at the cards' lightness. */
    fun tint(base: ThemePalette, wall: List<Int>): Int {
        val d = toHsl((wall.firstOrNull() ?: base.background) or (0xFF shl 24))
        val lightCards = luminance(base.background) > 0.35
        return fromHsl(d.h, min(max(d.s, 0.25f), 0.7f), if (lightCards) 0.8f else 0.25f)
    }

    // ---------- Dominant colors of a picture ----------

    /**
     * Up to [max] main colors of [pixels] (ARGB), most common first. Colors are bucketed at 4 bits
     * per channel; buckets too close to one already picked are skipped so the result has variety.
     */
    fun dominantColors(pixels: IntArray, max: Int = 4): List<Int> {
        val counts = IntArray(4096)
        val sr = LongArray(4096); val sg = LongArray(4096); val sb = LongArray(4096)
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val k = ((r(p) shr 4) shl 8) or ((g(p) shr 4) shl 4) or (b(p) shr 4)
            counts[k]++; sr[k] += r(p).toLong(); sg[k] += g(p).toLong(); sb[k] += b(p).toLong()
        }
        val order = (0 until 4096).filter { counts[it] > 0 }.sortedByDescending { counts[it] }
        val out = ArrayList<Int>()
        for (k in order) {
            val n = counts[k]
            val c = rgb((sr[k] / n).toInt(), (sg[k] / n).toInt(), (sb[k] / n).toInt())
            if (out.none { distance(it, c) < 48 }) out += c
            if (out.size >= max) break
        }
        return out
    }

    private fun distance(x: Int, y: Int): Int = abs(r(x) - r(y)) + abs(g(x) - g(y)) + abs(b(x) - b(y))
}
