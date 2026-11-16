package app.custo.feature.icons

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Icon shapes and sticker outlines as plain point lists (x0, y0, x1, y1, …), so the math is
 * testable without Android. [IconRenderer] turns them into Paths.
 */
object ShapeGeometry {
    /** [shape] fitted (keeping its proportions, centered) inside the square at ([left], [top]) of side [size]. */
    fun outline(shape: IconShape, left: Float, top: Float, size: Float, corner: Float = 0.22f, seed: Int = 0): FloatArray =
        fit(unit(shape, corner, seed), left, top, size)

    /** Sticker contours in the same square. Several contours are filled together (non-zero). */
    fun sticker(kind: Sticker, left: Float, top: Float, size: Float): List<FloatArray> {
        val parts = stickerUnit(kind)
        if (parts.isEmpty()) return emptyList()
        // Fit all contours with one transform so they stay aligned.
        val all = parts.reduce { a, b -> a + b }
        val (scale, dx, dy) = fitTransform(all, left, top, size)
        return parts.map { p -> FloatArray(p.size) { i -> if (i % 2 == 0) p[i] * scale + dx else p[i] * scale + dy } }
    }

    // --- unit shapes, roughly in 0..1 ---

    private fun unit(shape: IconShape, corner: Float, seed: Int): FloatArray = when (shape) {
        IconShape.CIRCLE -> polar(128) { 0.5 }
        IconShape.SQUIRCLE -> polar(160) { t -> 0.5 / (cos(t).pow(4) + sin(t).pow(4)).pow(0.25) }
        IconShape.ROUNDED -> roundRect(corner.coerceIn(0f, 0.5f).let { floatArrayOf(it, it, it, it) })
        IconShape.TEARDROP -> roundRect(floatArrayOf(0.5f, 0.5f, 0.12f, 0.5f))
        IconShape.LEAF -> roundRect(floatArrayOf(0.08f, 0.5f, 0.08f, 0.5f))
        IconShape.HEXAGON -> regular(6, PI / 6, 0.5)
        IconShape.OCTAGON -> roundCorners(regular(8, PI / 8, 0.5), 0.12f, 6)
        IconShape.FLOWER -> polar(240) { t -> 0.5 * (0.9 + 0.1 * cos(8 * t)) }
        IconShape.STAR -> roundCorners(star(5, 0.5, 0.26), 0.2f, 8)
        IconShape.HEART -> heart()
        IconShape.CLOUD -> cloud()
        IconShape.SHIELD -> shield()
        IconShape.DROP -> drop()
        IconShape.BLOB -> blob(seed)
    }

    private inline fun polar(steps: Int, r: (Double) -> Double): FloatArray {
        val out = FloatArray(steps * 2)
        for (i in 0 until steps) {
            val t = 2 * PI * i / steps
            val rr = r(t)
            out[i * 2] = (0.5 + rr * cos(t)).toFloat(); out[i * 2 + 1] = (0.5 + rr * sin(t)).toFloat()
        }
        return out
    }

    private fun regular(n: Int, start: Double, r: Double) = FloatArray(n * 2) { i ->
        val a = start + 2 * PI / n * (i / 2)
        if (i % 2 == 0) (0.5 + r * cos(a)).toFloat() else (0.5 + r * sin(a)).toFloat()
    }

    private fun star(points: Int, outer: Double, inner: Double): FloatArray {
        val n = points * 2
        return FloatArray(n * 2) { i ->
            val k = i / 2
            val a = -PI / 2 + PI / points * k
            val r = if (k % 2 == 0) outer else inner
            if (i % 2 == 0) (0.5 + r * cos(a)).toFloat() else (0.55 + r * sin(a)).toFloat()
        }
    }

    /** Unit square with per-corner radii (top-left, top-right, bottom-right, bottom-left). */
    private fun roundRect(r: FloatArray): FloatArray {
        val pts = ArrayList<Float>()
        // Corner centers and the angle each arc starts at, clockwise from top-left.
        val corners = listOf(
            Triple(r[0], r[0], PI), Triple(1f - r[1], r[1], 1.5 * PI),
            Triple(1f - r[2], 1f - r[2], 0.0), Triple(r[3], 1f - r[3], 0.5 * PI),
        )
        corners.forEachIndexed { i, (cx, cy, a0) ->
            val rad = r[i]
            if (rad <= 0.001f) {
                pts += listOf(cx, cy)
            } else {
                val steps = 16
                for (k in 0..steps) {
                    val a = a0 + PI / 2 * k / steps
                    pts += (cx + rad * cos(a)).toFloat(); pts += (cy + rad * sin(a)).toFloat()
                }
            }
        }
        return pts.toFloatArray()
    }

    private fun heart(): FloatArray = FloatArray(160 * 2) { i ->
        val t = 2 * PI * (i / 2) / 160
        if (i % 2 == 0) (16 * sin(t).pow(3)).toFloat()
        else (-(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t))).toFloat()
    }

    /** Outline of a union of circles, ray-cast from a point inside all the big ones. */
    private fun cloud(): FloatArray {
        val circles = arrayOf(
            doubleArrayOf(0.30, 0.58, 0.20), doubleArrayOf(0.52, 0.44, 0.26), doubleArrayOf(0.74, 0.56, 0.19),
            doubleArrayOf(0.22, 0.68, 0.15), doubleArrayOf(0.80, 0.68, 0.14), doubleArrayOf(0.42, 0.70, 0.17), doubleArrayOf(0.62, 0.70, 0.17),
        )
        val ox = 0.5; val oy = 0.6
        val steps = 220
        val out = FloatArray(steps * 2)
        for (i in 0 until steps) {
            val t = 2 * PI * i / steps
            val dx = cos(t); val dy = sin(t)
            var best = 0.0
            for (c in circles) {
                val fx = c[0] - ox; val fy = c[1] - oy
                val b = dx * fx + dy * fy
                val disc = b * b - (fx * fx + fy * fy - c[2] * c[2])
                if (disc >= 0) best = max(best, b + sqrt(disc))
            }
            out[i * 2] = (ox + dx * best).toFloat(); out[i * 2 + 1] = (oy + dy * best).toFloat()
        }
        return out
    }

    private fun shield(): FloatArray {
        val pts = ArrayList<Float>()
        fun add(x: Double, y: Double) { pts += x.toFloat(); pts += y.toFloat() }
        fun quad(x0: Double, y0: Double, cx: Double, cy: Double, x1: Double, y1: Double) {
            for (k in 1..20) {
                val t = k / 20.0; val u = 1 - t
                add(u * u * x0 + 2 * u * t * cx + t * t * x1, u * u * y0 + 2 * u * t * cy + t * t * y1)
            }
        }
        add(0.08, 0.14)
        quad(0.08, 0.14, 0.32, 0.12, 0.5, 0.0)
        quad(0.5, 0.0, 0.68, 0.12, 0.92, 0.14)
        add(0.92, 0.5)
        quad(0.92, 0.5, 0.9, 0.84, 0.5, 1.0)
        quad(0.5, 1.0, 0.1, 0.84, 0.08, 0.5)
        return chaikin(pts.toFloatArray(), 2)
    }

    /** A water drop: a circle with tangent lines meeting at a point on top. */
    private fun drop(): FloatArray {
        val cx = 0.5; val cy = 0.62; val r = 0.38; val apexY = 0.0
        val d = cy - apexY
        val half = acos(r / d)
        val up = -PI / 2
        val start = up + half; val end = up - half + 2 * PI
        val steps = 120
        val pts = ArrayList<Float>()
        pts += cx.toFloat(); pts += apexY.toFloat()
        for (k in 0..steps) {
            val a = start + (end - start) * k / steps
            pts += (cx + r * cos(a)).toFloat(); pts += (cy + r * sin(a)).toFloat()
        }
        return roundCorners(pts.toFloatArray(), 0.04f, 6, only = 0)
    }

    /** A seeded smooth random pebble. Same seed, same pebble. */
    private fun blob(seed: Int): FloatArray {
        val rnd = Random(seed)
        val amps = DoubleArray(3) { 0.035 + rnd.nextDouble() * 0.05 }
        val phases = DoubleArray(3) { rnd.nextDouble() * 2 * PI }
        return polar(180) { t ->
            0.5 * (1 + amps[0] * cos(2 * t + phases[0]) + amps[1] * cos(3 * t + phases[1]) + amps[2] * cos(4 * t + phases[2]))
        }
    }

    private fun stickerUnit(kind: Sticker): List<FloatArray> = when (kind) {
        Sticker.NONE -> emptyList()
        Sticker.HEART -> listOf(heart())
        Sticker.STAR -> listOf(roundCorners(star(5, 0.5, 0.24), 0.18f, 6))
        Sticker.SPARKLE -> listOf(roundCorners(FloatArray(16) { i ->
            val k = i / 2; val a = -PI / 2 + PI / 4 * k; val r = if (k % 2 == 0) 0.5 else 0.12
            if (i % 2 == 0) (0.5 + r * cos(a)).toFloat() else (0.5 + r * sin(a)).toFloat()
        }, 0.12f, 6))
        Sticker.BOW -> listOf(
            chaikin(floatArrayOf(0.5f, 0.5f, 0.06f, 0.18f, 0.02f, 0.5f, 0.06f, 0.82f), 3),
            chaikin(floatArrayOf(0.5f, 0.5f, 0.94f, 0.82f, 0.98f, 0.5f, 0.94f, 0.18f), 3),
            regular(24, 0.0, 0.13).let { c -> FloatArray(c.size) { i -> c[i] } },
        )
        Sticker.CROWN -> listOf(roundCorners(
            floatArrayOf(0.08f, 0.86f, 0.06f, 0.3f, 0.3f, 0.56f, 0.5f, 0.14f, 0.7f, 0.56f, 0.94f, 0.3f, 0.92f, 0.86f), 0.14f, 6,
        ))
    }

    // --- helpers ---

    /** Replaces each vertex with a quadratic curve cut [f] of the way along both edges. [only] limits it to one vertex. */
    fun roundCorners(p: FloatArray, f: Float, steps: Int, only: Int = -1): FloatArray {
        val n = p.size / 2
        val out = ArrayList<Float>(p.size * steps)
        for (i in 0 until n) {
            val vx = p[i * 2]; val vy = p[i * 2 + 1]
            if (only >= 0 && i != only) { out += vx; out += vy; continue }
            val pi = (i - 1 + n) % n; val ni = (i + 1) % n
            val ax = vx + (p[pi * 2] - vx) * f; val ay = vy + (p[pi * 2 + 1] - vy) * f
            val bx = vx + (p[ni * 2] - vx) * f; val by = vy + (p[ni * 2 + 1] - vy) * f
            for (k in 0..steps) {
                val t = k.toFloat() / steps; val u = 1 - t
                out += u * u * ax + 2 * u * t * vx + t * t * bx
                out += u * u * ay + 2 * u * t * vy + t * t * by
            }
        }
        return out.toFloatArray()
    }

    /** Chaikin corner cutting: each pass rounds every corner a little. */
    fun chaikin(p: FloatArray, passes: Int): FloatArray {
        var cur = p
        repeat(passes) {
            val n = cur.size / 2
            val out = FloatArray(n * 4)
            for (i in 0 until n) {
                val j = (i + 1) % n
                val x0 = cur[i * 2]; val y0 = cur[i * 2 + 1]; val x1 = cur[j * 2]; val y1 = cur[j * 2 + 1]
                out[i * 4] = 0.75f * x0 + 0.25f * x1; out[i * 4 + 1] = 0.75f * y0 + 0.25f * y1
                out[i * 4 + 2] = 0.25f * x0 + 0.75f * x1; out[i * 4 + 3] = 0.25f * y0 + 0.75f * y1
            }
            cur = out
        }
        return cur
    }

    private fun fitTransform(p: FloatArray, left: Float, top: Float, size: Float): Triple<Float, Float, Float> {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in p.indices step 2) {
            minX = min(minX, p[i]); maxX = max(maxX, p[i]); minY = min(minY, p[i + 1]); maxY = max(maxY, p[i + 1])
        }
        val w = (maxX - minX).coerceAtLeast(1e-6f); val h = (maxY - minY).coerceAtLeast(1e-6f)
        val scale = size / max(w, h)
        val dx = left + (size - w * scale) / 2f - minX * scale
        val dy = top + (size - h * scale) / 2f - minY * scale
        return Triple(scale, dx, dy)
    }

    private fun fit(p: FloatArray, left: Float, top: Float, size: Float): FloatArray {
        val (scale, dx, dy) = fitTransform(p, left, top, size)
        return FloatArray(p.size) { i -> if (i % 2 == 0) p[i] * scale + dx else p[i] * scale + dy }
    }
}

/** Which piece of a puzzle picture an icon shows: the [index]-th of [count] icons, in grid order. */
data class Tile(val index: Int, val count: Int)

/**
 * Cuts one picture into icon tiles laid out like a home screen grid: [maxCols] per row, with
 * [gap] (in icon widths) between icons so the picture lines up across the gaps.
 */
object PuzzleMath {
    const val COLS = 4
    const val GAP = 0.4f

    fun grid(count: Int, maxCols: Int = COLS): Pair<Int, Int> {
        val n = count.coerceAtLeast(1)
        val cols = min(n, maxCols.coerceAtLeast(1))
        return cols to ceil(n / cols.toFloat()).toInt()
    }

    /**
     * The visible crop of an [imgW]×[imgH] picture for a target of aspect [aspect] (w/h):
     * the largest centered crop, shrunk by [zoom], panned by [panX]/[panY] (-1..1). Returns l, t, r, b.
     */
    fun crop(imgW: Float, imgH: Float, aspect: Float, zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f): FloatArray {
        var w = imgW; var h = imgW / aspect
        if (h > imgH) { h = imgH; w = imgH * aspect }
        val z = zoom.coerceIn(1f, 8f)
        w /= z; h /= z
        val l = (imgW - w) / 2f * (1f + panX.coerceIn(-1f, 1f))
        val t = (imgH - h) / 2f * (1f + panY.coerceIn(-1f, 1f))
        return floatArrayOf(l, t, l + w, t + h)
    }

    /** Source rect (l, t, r, b) in picture pixels for [tile]; null tile = the whole crop as one square icon. */
    fun sourceRect(imgW: Float, imgH: Float, tile: Tile?, zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f, gap: Float = GAP, maxCols: Int = COLS): FloatArray {
        if (tile == null || tile.count <= 1) return crop(imgW, imgH, 1f, zoom, panX, panY)
        val (cols, rows) = grid(tile.count, maxCols)
        val gw = cols + (cols - 1) * gap; val gh = rows + (rows - 1) * gap
        val c = crop(imgW, imgH, gw / gh, zoom, panX, panY)
        val unit = (c[2] - c[0]) / gw
        val i = tile.index.coerceIn(0, tile.count - 1)
        val l = c[0] + (i % cols) * (1 + gap) * unit
        val t = c[1] + (i / cols) * (1 + gap) * unit
        return floatArrayOf(l, t, l + unit, t + unit)
    }
}

/** Seeded per-app colors for "rainbow" styles. Pure ARGB math. */
object IconPalette {
    /** A stable hue shift (degrees) for [pkg] under [seed]: same app + seed, same color. */
    fun hueFor(pkg: String, seed: Int): Float = Random(seed * 31 + pkg.hashCode()).nextFloat() * 360f

    fun rotateHue(argb: Int, degrees: Float): Int {
        val a = argb ushr 24
        val hsl = toHsl(argb)
        val h = ((hsl[0] + degrees) % 360f + 360f) % 360f
        // Greys have no hue to turn: give them a gentle tint so rainbow still shows.
        val s = if (hsl[1] < 0.08f && hsl[2] in 0.1f..0.9f) 0.45f else hsl[1]
        return (a shl 24) or (fromHsl(h, s, hsl[2]) and 0xFFFFFF)
    }

    fun toHsl(argb: Int): FloatArray {
        val r = (argb shr 16 and 0xFF) / 255f; val g = (argb shr 8 and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        if (d < 1e-6f) return floatArrayOf(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (mx) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
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

    /** Angle in degrees of a vector, used by the shadow direction picker. */
    fun angleOf(dx: Float, dy: Float): Float = ((Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() % 360f) + 360f) % 360f
}
