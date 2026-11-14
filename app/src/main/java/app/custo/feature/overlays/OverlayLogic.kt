package app.custo.feature.overlays

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure helpers shared by the overlays. No Android types, so they are unit-tested. */
object OverlayLogic {

    /** True if [minute] (0..1439) is inside [start, end). Wraps past midnight; start == end means off. */
    fun inQuietHours(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }

    fun formatMinute(m: Int): String = "%02d:%02d".format((m / 60) % 24, m % 60)

    // ---- csv lists ----

    fun csv(s: String?): List<String> = s?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList()
    fun csv(list: List<String>): String = list.distinct().joinToString(",")

    fun colorsCsv(s: String?): List<Int> = csv(s).mapNotNull { it.toLongOrNull()?.toInt() }
    fun colorsCsv(list: List<Int>): String = list.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() }

    // ---- per-app rules: "pkg=ignore;pkg=#AARRGGBB" ----

    const val IGNORE = 0

    /** Package to rule: a color, or [IGNORE]. */
    fun decodeRules(s: String?): Map<String, Int> {
        if (s.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        s.split(';').forEach { part ->
            val i = part.indexOf('=')
            if (i <= 0) return@forEach
            val pkg = part.substring(0, i).trim()
            val v = part.substring(i + 1).trim()
            val rule = if (v == "ignore") IGNORE else v.removePrefix("#").toLongOrNull(16)?.toInt() ?: return@forEach
            out[pkg] = rule
        }
        return out
    }

    fun encodeRules(rules: Map<String, Int>): String = rules.entries.joinToString(";") { (pkg, c) ->
        "$pkg=" + if (c == IGNORE) "ignore" else "#%08X".format(c)
    }

    // ---- colors ----

    private fun a(c: Int) = (c ushr 24) and 0xFF
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    fun lerpColor(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun l(x: Int, y: Int) = (x + (y - x) * f + 0.5f).toInt()
        return argb(l(a(from), a(to)), l(r(from), r(to)), l(g(from), g(to)), l(b(from), b(to)))
    }

    /** Red at 0 %, amber at 50 %, green at 100 %. */
    fun batteryGradient(level: Int): Int {
        val f = level.coerceIn(0, 100) / 100f
        return if (f < 0.5f) lerpColor(0xFFFF1744.toInt(), 0xFFFFC400.toInt(), f * 2)
        else lerpColor(0xFFFFC400.toInt(), 0xFF00E676.toInt(), (f - 0.5f) * 2)
    }

    /**
     * Dominant color of an icon: an average that ignores transparent pixels and favors colorful
     * ones over white/gray backgrounds. Returns [fallback] if nothing usable is found.
     */
    fun dominantColor(pixels: IntArray, fallback: Int): Int {
        var sr = 0.0; var sg = 0.0; var sb = 0.0; var sw = 0.0
        for (p in pixels) {
            if (a(p) < 128) continue
            val mx = maxOf(r(p), g(p), b(p)); val mn = minOf(r(p), g(p), b(p))
            val sat = if (mx == 0) 0f else (mx - mn) / mx.toFloat()
            val w = 0.05 + sat * sat
            sr += r(p) * w; sg += g(p) * w; sb += b(p) * w; sw += w
        }
        if (sw == 0.0) return fallback
        return argb(255, (sr / sw).toInt(), (sg / sw).toInt(), (sb / sw).toInt())
    }

    // ---- text ----

    /** "0 K", "12 K", "1.4 M" style rate for bytes per second. */
    fun formatRate(bytesPerSec: Long): String {
        val kb = bytesPerSec / 1024.0
        return when {
            kb < 1 -> "0 K"
            kb < 1000 -> "${kb.toInt()} K"
            kb < 10 * 1024 -> String.format(java.util.Locale.US, "%.1f M", kb / 1024)
            else -> "${(kb / 1024).toInt()} M"
        }
    }

    /** m:ss or h:mm:ss for a chronometer. Negative values are shown as zero. */
    fun formatElapsed(ms: Long): String {
        val s = (ms.coerceAtLeast(0) / 1000)
        val h = s / 3600; val m = (s / 60) % 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    // ---- motion ----

    /** Damped spring from 0 to 1. [damping] < 1 overshoots a little. */
    fun spring(t: Float, damping: Float = 0.62f, stiffness: Float = 14f): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val w = stiffness.toDouble()
        val z = damping.toDouble()
        val wd = w * sqrt(1 - z * z)
        val x = 1 - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
        return x.toFloat()
    }
}

enum class EdgeGesture { NONE, TAP, SWIPE }

/** Classifies a touch on an edge strip. [inward] is +1 if moving right is inward (left edge), -1 otherwise. */
object EdgeGestureClassifier {
    fun classify(dx: Float, dy: Float, durationMs: Long, inward: Int, swipePx: Float, slopPx: Float): EdgeGesture {
        val moved = sqrt(dx * dx + dy * dy)
        if (moved < slopPx) return if (durationMs < 400) EdgeGesture.TAP else EdgeGesture.NONE
        val inX = dx * inward
        return if (inX >= swipePx && inX > abs(dy) * 0.8f) EdgeGesture.SWIPE else EdgeGesture.NONE
    }

    /** Bottom-corner zones: swipe up or inward counts. */
    fun classifyCorner(dx: Float, dy: Float, durationMs: Long, inward: Int, swipePx: Float, slopPx: Float): EdgeGesture {
        val moved = sqrt(dx * dx + dy * dy)
        if (moved < slopPx) return if (durationMs < 400) EdgeGesture.TAP else EdgeGesture.NONE
        return if (-dy + dx * inward >= swipePx) EdgeGesture.SWIPE else EdgeGesture.NONE
    }
}

/** Detects two taps close in time and space. Pure. */
class DoubleTapDetector(private val maxGapMs: Long = 300, private val maxTapMs: Long = 250, private val slopPx: Float = 40f) {
    private var lastUpAt = -1L
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f

    fun onDown(now: Long, x: Float, y: Float) { downAt = now; downX = x; downY = y }

    /** Returns true when this up completes a double tap. */
    fun onUp(now: Long, x: Float, y: Float): Boolean {
        val isTap = now - downAt <= maxTapMs && abs(x - downX) < slopPx && abs(y - downY) < slopPx
        if (!isTap) { lastUpAt = -1; return false }
        val dbl = lastUpAt >= 0 && downAt - lastUpAt <= maxGapMs && abs(x - lastX) < slopPx * 2 && abs(y - lastY) < slopPx * 2
        if (dbl) { lastUpAt = -1; return true }
        lastUpAt = now; lastX = x; lastY = y
        return false
    }
}
