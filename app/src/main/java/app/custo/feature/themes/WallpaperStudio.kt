package app.custo.feature.themes

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.WindowManager
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import app.custo.feature.themes.WallLogic.lerpColor
import app.custo.feature.themes.WallLogic.sample
import app.custo.feature.themes.WallLogic.withAlpha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class WallTarget(val label: String, val flags: Int) {
    HOME("Home", WallpaperManager.FLAG_SYSTEM),
    LOCK("Lock", WallpaperManager.FLAG_LOCK),
    BOTH("Both", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
}

private const val TAU = (2 * PI).toFloat()
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/** Procedurally renders wallpapers at full screen resolution. Nothing is downloaded. */
class WallpaperStudio(private val context: Context) {

    fun screenSize(): Pair<Int, Int> {
        val b = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return min(b.width(), b.height()) to max(b.width(), b.height())
    }

    suspend fun apply(spec: WallSpec, target: WallTarget) = withContext(Dispatchers.Default) {
        val (w, h) = screenSize()
        val bmp = render(spec, w, h)
        withContext(Dispatchers.IO) { WallpaperManager.getInstance(context).setBitmap(bmp, null, true, target.flags) }
        bmp.recycle()
    }

    companion object {
        /** Full render: scene, softness (downscale + box blur) and grain. */
        fun render(spec: WallSpec, w: Int, h: Int): Bitmap {
            val out = createBitmap(w, h)
            val c = Canvas(out)
            if (spec.blur > 0.01f) {
                val scale = 1f / (1f + spec.blur * 5f)
                val sw = (w * scale).toInt().coerceAtLeast(4)
                val sh = (h * scale).toInt().coerceAtLeast(4)
                val small = createBitmap(sw, sh)
                drawScene(Canvas(small), spec, sw.toFloat(), sh.toFloat(), 0f)
                boxBlur(small, (spec.blur * min(sw, sh) * 0.035f).roundToInt().coerceAtLeast(1))
                c.drawBitmap(small, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
                small.recycle()
            } else {
                drawScene(c, spec, w.toFloat(), h.toFloat(), 0f)
            }
            drawGrain(c, spec.grain, w.toFloat(), h.toFloat())
            return out
        }

        /** Scene plus grain, no softness (callers that animate apply blur themselves). */
        fun draw(canvas: Canvas, spec: WallSpec, w: Float, h: Float, t: Float = 0f) {
            drawScene(canvas, spec, w, h, t)
            drawGrain(canvas, spec.grain, w, h)
        }

        /**
         * Draws the style at animation time [t] (seconds). At t = 0 the result matches the still
         * wallpaper; motion parameters come from a separate random stream so shapes stay put.
         */
        fun drawScene(canvas: Canvas, spec: WallSpec, w: Float, h: Float, t: Float) {
            val colors = spec.colors.ifEmpty { listOf(BLACK) }
            val rnd = Random(spec.seed)
            val anim = Random(spec.seed * 31 + 7)
            val k = 0.5f + spec.complexity.coerceIn(0f, 1f)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            val g = Gen(canvas, spec, colors, rnd, anim, k, w, h, t, p)
            when (spec.style) {
                WallStyle.SOLID -> canvas.drawColor(colors[0])
                WallStyle.GRADIENT -> g.gradient()
                WallStyle.MESH -> g.mesh()
                WallStyle.WAVES -> g.waves()
                WallStyle.DOTS -> g.dots()
                WallStyle.AURORA -> g.aurora()
                WallStyle.BLOBS -> g.blobs()
                WallStyle.LOWPOLY -> g.lowPoly()
                WallStyle.STRIPES -> g.stripes()
                WallStyle.TOPO -> g.topo()
                WallStyle.BOKEH -> g.bokeh()
                WallStyle.SUNBURST -> g.sunburst()
                WallStyle.MOSAIC -> g.mosaic()
                WallStyle.RINGS -> g.rings()
            }
        }

        private val grainTile: Bitmap by lazy {
            val n = 256
            val r = Random(42)
            val px = IntArray(n * n) { val v = r.nextInt(256); BLACK or (v shl 16) or (v shl 8) or v }
            Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        }

        fun drawGrain(canvas: Canvas, amount: Float, w: Float, h: Float) {
            if (amount <= 0.01f) return
            val p = Paint().apply {
                shader = BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                alpha = (amount.coerceIn(0f, 1f) * 0.6f * 255).roundToInt()
                blendMode = BlendMode.OVERLAY
            }
            canvas.drawRect(0f, 0f, w, h, p)
        }

        /** Three-pass separable box blur (≈ Gaussian) on a small bitmap. */
        private fun boxBlur(bmp: Bitmap, r: Int, passes: Int = 3) {
            val w = bmp.width; val h = bmp.height
            val a = IntArray(w * h)
            val b = IntArray(w * h)
            bmp.getPixels(a, 0, w, 0, 0, w, h)
            repeat(passes) {
                blurPass(a, b, w, h, r) // a (w×h) -> b transposed (h×w)
                blurPass(b, a, h, w, r) // b (h×w) -> a transposed back (w×h)
            }
            bmp.setPixels(a, 0, w, 0, 0, w, h)
        }

        private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
            val div = 2 * r + 1
            for (y in 0 until h) {
                val row = y * w
                var sa = 0; var sr = 0; var sg = 0; var sb = 0
                for (i in -r..r) {
                    val px = src[row + i.coerceIn(0, w - 1)]
                    sa += px ushr 24; sr += px shr 16 and 0xFF; sg += px shr 8 and 0xFF; sb += px and 0xFF
                }
                for (x in 0 until w) {
                    dst[x * h + y] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                    val add = src[row + min(x + r + 1, w - 1)]
                    val rem = src[row + max(x - r, 0)]
                    sa += (add ushr 24) - (rem ushr 24)
                    sr += (add shr 16 and 0xFF) - (rem shr 16 and 0xFF)
                    sg += (add shr 8 and 0xFF) - (rem shr 8 and 0xFF)
                    sb += (add and 0xFF) - (rem and 0xFF)
                }
            }
        }

        /** Random but pleasant palette: analogous hues around a random base, one dark anchor. */
        fun randomPalette(rnd: Random = Random.Default): List<Int> {
            val base = rnd.nextFloat() * 360f
            val spread = 25f + rnd.nextFloat() * 50f
            fun hsl(h: Float, s: Float, l: Float) = app.custo.feature.systemui.Tonal.fromHsl(((h % 360f) + 360f) % 360f, s, l)
            return listOf(
                hsl(base, 0.55f, 0.14f),
                hsl(base + spread, 0.85f, 0.55f),
                hsl(base + spread * 2, 0.9f, 0.65f),
                hsl(base - spread, 0.8f, 0.5f),
            )
        }
    }
}

/** One render pass. [rnd] places shapes (same stream as older versions); [anim] only drives motion. */
private class Gen(
    val c: Canvas, val s: WallSpec, val colors: List<Int>, val rnd: Random, val anim: Random,
    val k: Float, val w: Float, val h: Float, val t: Float, val p: Paint,
) {
    val accents = colors.drop(1).ifEmpty { colors }

    /** Smooth offset that is exactly 0 at t = 0. */
    fun drift(amp: Float): Float {
        val ph = anim.nextFloat() * TAU
        val sp = 0.04f + anim.nextFloat() * 0.06f
        return amp * (sin(ph + t * sp * TAU) - sin(ph))
    }

    fun vertical(top: Int, bottom: Int) {
        p.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }

    fun gradient() {
        val rad = (s.angle + 20f * sin(t * 0.08f)) * PI.toFloat() / 180f
        val cx = w / 2; val cy = h / 2; val r = max(w, h) * 0.6f
        p.shader = LinearGradient(
            cx - cos(rad) * r, cy - sin(rad) * r, cx + cos(rad) * r, cy + sin(rad) * r,
            colors.toIntArray(), null, Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w, h, p)
    }

    fun mesh() {
        c.drawColor(colors[0])
        val n = (accents.size * 2 * k).roundToInt().coerceAtLeast(2)
        repeat(n) { i ->
            val col = accents[i % accents.size]
            val x = rnd.nextFloat() * w + drift(w * 0.15f)
            val y = rnd.nextFloat() * h + drift(h * 0.1f)
            val r = max(w, h) * (0.35f + rnd.nextFloat() * 0.35f)
            p.shader = RadialGradient(x, y, r, intArrayOf(col, col and 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
        }
        p.shader = null
    }

    fun waves() {
        vertical(colors[0], colors.last())
        val layers = (5 * k).roundToInt().coerceAtLeast(2)
        for (i in 0 until layers) {
            val base = h * (0.45f + i * 0.55f / layers)
            val amp = h * (0.03f + rnd.nextFloat() * 0.04f)
            val freq = 1.2f + rnd.nextFloat() * 1.5f
            val phase = rnd.nextFloat() * TAU + t * (0.25f + 0.08f * i)
            val path = Path().apply {
                moveTo(0f, h)
                var x = 0f
                while (x <= w) {
                    lineTo(x, base + amp * sin(freq * TAU * x / w + phase))
                    x += w / 60f
                }
                lineTo(w, h); close()
            }
            val col = colors[(i + 1) % colors.size]
            p.color = (col and 0x00FFFFFF) or (((0x55 + i * 0x22).coerceAtMost(0xFF)) shl 24)
            c.drawPath(path, p)
        }
    }

    fun dots() {
        c.drawColor(colors[0])
        val acc = colors.drop(1).ifEmpty { listOf(WHITE) }
        val step = w / (14f * k)
        var y = step / 2
        var row = 0
        while (y < h + step) {
            var x = if (row % 2 == 0) step / 2 else step
            while (x < w + step) {
                val f = y / h
                val pulse = 1f + 0.12f * sin(t * 0.6f + x * 0.02f + row * 0.4f) * (if (t == 0f) 0f else 1f)
                p.color = acc[((x / step).toInt() + row) % acc.size]
                p.alpha = (90 + 140 * f).toInt().coerceAtMost(255)
                c.drawCircle(x, y, step * (0.12f + 0.22f * f) * pulse, p)
                x += step
            }
            y += step * 0.87f; row++
        }
    }

    fun aurora() {
        vertical(lerpColor(colors[0], BLACK, 0.45f), colors[0])
        val n = (4 * k).roundToInt().coerceAtLeast(2)
        val tilt = ((s.angle % 90f) - 45f) * 0.3f
        c.withRotation(tilt, w / 2, h / 2) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
            p.blendMode = BlendMode.SCREEN
            repeat(n) { i ->
                val col = accents[i % accents.size]
                val baseY = h * (0.18f + 0.5f * rnd.nextFloat())
                val amp = h * (0.04f + 0.08f * rnd.nextFloat())
                val freq = 0.6f + 1.2f * rnd.nextFloat()
                val ph = rnd.nextFloat() * TAU
                val thick = h * (0.05f + 0.08f * rnd.nextFloat())
                val speed = 0.18f + 0.1f * anim.nextFloat()
                val path = Path()
                var x = -0.15f * w
                var first = true
                while (x <= 1.15f * w) {
                    val u = TAU * x / w
                    val y = baseY + amp * sin(freq * u + ph + t * speed) + amp * 0.4f * sin(2.3f * freq * u + ph * 1.7f - t * speed * 0.7f)
                    if (first) path.moveTo(x, y) else path.lineTo(x, y)
                    first = false
                    x += w / 40f
                }
                val widths = floatArrayOf(1f, 0.6f, 0.3f, 0.12f)
                val alphas = floatArrayOf(0.10f, 0.16f, 0.24f, 0.5f)
                for (j in widths.indices) {
                    p.strokeWidth = thick * widths[j]
                    p.color = withAlpha(col, alphas[j])
                    c.drawPath(path, p)
                }
            }
            p.blendMode = null
            p.style = Paint.Style.FILL
        }
    }

    fun blobs() {
        vertical(colors[0], lerpColor(colors[0], colors.last(), 0.35f))
        val n = (6 * k).roundToInt().coerceAtLeast(2)
        repeat(n) { i ->
            val col = accents[i % accents.size]
            val r = min(w, h) * (0.18f + 0.22f * rnd.nextFloat())
            val x = rnd.nextFloat() * w + drift(w * 0.18f)
            val y = rnd.nextFloat() * h + drift(h * 0.12f)
            p.shader = RadialGradient(x, y, r, intArrayOf(col, col, withAlpha(col, 0f)), floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
        }
        p.shader = null
    }

    /** 0..1 position along the spec's angle, used to lay palettes across the screen. */
    fun along(x: Float, y: Float): Float {
        val rad = s.angle * PI.toFloat() / 180f
        val dx = cos(rad); val dy = sin(rad)
        val span = abs(dx) * w + abs(dy) * h
        return (((x - w / 2) * dx + (y - h / 2) * dy) / span + 0.5f).coerceIn(0f, 1f)
    }

    fun lowPoly() {
        val cols = (6 + 6 * k).roundToInt()
        val cell = w / cols
        val rows = ceil(h / cell).toInt()
        val px = Array(rows + 1) { r -> FloatArray(cols + 1) { q -> q * cell + if (q in 1 until cols) (rnd.nextFloat() - 0.5f) * cell * 0.8f else 0f } }
        val py = Array(rows + 1) { r -> FloatArray(cols + 1) { q -> r * cell + if (r in 1 until rows) (rnd.nextFloat() - 0.5f) * cell * 0.8f else 0f } }
        p.style = Paint.Style.FILL_AND_STROKE
        p.strokeWidth = 1f
        val path = Path()
        fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val cx = (x1 + x2 + x3) / 3; val cy = (y1 + y2 + y3) / 3
            var col = sample(colors, along(cx, cy))
            val j = rnd.nextFloat() * 0.16f - 0.08f
            val shimmer = if (t == 0f) 0f else 0.05f * (sin(t * 0.7f + anim.nextFloat() * TAU) + 1f) / 2f
            col = if (j + shimmer > 0) lerpColor(col, WHITE, j + shimmer) else lerpColor(col, BLACK, -j)
            p.color = col
            path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
            c.drawPath(path, p)
        }
        for (r in 0 until rows) for (q in 0 until cols) {
            val ax = px[r][q]; val ay = py[r][q]; val bx = px[r][q + 1]; val by = py[r][q + 1]
            val cx = px[r + 1][q + 1]; val cy = py[r + 1][q + 1]; val dx = px[r + 1][q]; val dy = py[r + 1][q]
            if (rnd.nextBoolean()) { tri(ax, ay, bx, by, cx, cy); tri(ax, ay, cx, cy, dx, dy) }
            else { tri(ax, ay, bx, by, dx, dy); tri(bx, by, cx, cy, dx, dy) }
        }
        p.style = Paint.Style.FILL
    }

    fun stripes() {
        c.drawColor(colors[0])
        val diag = hypot(w, h)
        val base = diag / (9f * k)
        val bands = ArrayList<Pair<Float, Int>>()
        var len = 0f
        var i = 0
        while (len < diag * 1.2f || bands.size < 4) {
            val bw = base * (0.3f + 1.3f * rnd.nextFloat())
            bands += bw to colors[i % colors.size]
            len += bw; i++
        }
        val shift = (t * base * 0.12f) % len
        c.withRotation(s.angle, w / 2, h / 2) {
            val top = h / 2 - diag / 2 - len
            var y = top + shift
            while (y < h / 2 + diag / 2) {
                for ((bw, col) in bands) {
                    p.color = col
                    c.drawRect(w / 2 - diag / 2, y, w / 2 + diag / 2, y + bw + 0.5f, p)
                    y += bw
                }
            }
        }
    }

    fun topo() {
        c.drawColor(colors[0])
        val m = (4 + 4 * k).roundToInt()
        val bx = FloatArray(m); val by = FloatArray(m); val bs = FloatArray(m); val ba = FloatArray(m)
        val dim = min(w, h)
        for (i in 0 until m) {
            bx[i] = rnd.nextFloat() * w + drift(w * 0.05f); by[i] = rnd.nextFloat() * h + drift(h * 0.04f)
            bs[i] = dim * (0.15f + 0.3f * rnd.nextFloat()); ba[i] = (if (rnd.nextBoolean()) 1f else -0.7f) * (0.5f + rnd.nextFloat())
        }
        val f1 = TAU * (0.6f + rnd.nextFloat()) / dim; val f2 = TAU * (0.4f + rnd.nextFloat()) / dim; val ph = rnd.nextFloat() * TAU
        val cell = max(w, h) / 110f
        val gw = (w / cell).toInt() + 2; val gh = (h / cell).toInt() + 2
        val field = FloatArray(gw * gh)
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (gy in 0 until gh) for (gx in 0 until gw) {
            val x = gx * cell; val y = gy * cell
            var v = 0.25f * sin(x * f1 + y * f2 + ph + t * 0.12f)
            for (i in 0 until m) {
                val dx = x - bx[i]; val dy = y - by[i]
                v += ba[i] * exp(-(dx * dx + dy * dy) / (bs[i] * bs[i]))
            }
            field[gy * gw + gx] = v
            lo = min(lo, v); hi = max(hi, v)
        }
        val levels = (8 + 10 * k).roundToInt()
        val lineColors = accents
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val seg = FloatArray(gw * gh * 4 * 2)
        for (li in 1..levels) {
            val lv = lo + (hi - lo) * li / (levels + 1)
            var n = 0
            for (gy in 0 until gh - 1) for (gx in 0 until gw - 1) {
                val a = field[gy * gw + gx]; val b = field[gy * gw + gx + 1]
                val cc = field[(gy + 1) * gw + gx + 1]; val d = field[(gy + 1) * gw + gx]
                val x0 = gx * cell; val y0 = gy * cell
                var pts = 0
                val ex = FloatArray(8)
                fun cross(va: Float, vb: Float, xa: Float, ya: Float, xb: Float, yb: Float) {
                    if ((va > lv) != (vb > lv)) {
                        val f = (lv - va) / (vb - va)
                        ex[pts * 2] = xa + (xb - xa) * f; ex[pts * 2 + 1] = ya + (yb - ya) * f; pts++
                    }
                }
                cross(a, b, x0, y0, x0 + cell, y0)
                cross(b, cc, x0 + cell, y0, x0 + cell, y0 + cell)
                cross(d, cc, x0, y0 + cell, x0 + cell, y0 + cell)
                cross(a, d, x0, y0, x0, y0 + cell)
                if (pts >= 2 && n + 8 <= seg.size) {
                    for (q in 0 until 4) seg[n + q] = ex[q]
                    n += 4
                    if (pts == 4) { for (q in 0 until 4) seg[n + q] = ex[4 + q]; n += 4 }
                }
            }
            p.color = withAlpha(lineColors[li % lineColors.size], 0.85f)
            p.strokeWidth = dim * (if (li % 5 == 0) 0.005f else 0.0022f)
            c.drawLines(seg, 0, n, p)
        }
        p.style = Paint.Style.FILL
    }

    fun bokeh() {
        vertical(colors[0], lerpColor(colors[0], colors.getOrElse(1) { colors[0] }, 0.3f))
        val n = (30 * k).roundToInt()
        p.blendMode = BlendMode.SCREEN
        repeat(n) { i ->
            val col = accents[i % accents.size]
            val sz = rnd.nextFloat()
            val r = min(w, h) * (0.02f + 0.13f * sz * sz)
            val x0 = rnd.nextFloat() * w
            val y0 = rnd.nextFloat() * h
            val a = 0.12f + 0.35f * rnd.nextFloat()
            val speed = h * (0.004f + 0.01f * anim.nextFloat())
            val span = h + 2 * r
            val y = ((y0 + r - t * speed) % span + span) % span - r
            val x = x0 + drift(w * 0.03f)
            p.shader = RadialGradient(x, y, r, intArrayOf(withAlpha(col, a), withAlpha(col, a * 0.85f), withAlpha(col, 0f)), floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
        }
        p.shader = null
        p.blendMode = null
    }

    fun sunburst() {
        val cx = w * (0.3f + 0.4f * rnd.nextFloat())
        val cy = h * (0.25f + 0.3f * rnd.nextFloat())
        val far = hypot(max(cx, w - cx), max(cy, h - cy))
        p.shader = RadialGradient(cx, cy, far, colors.getOrElse(2) { accents[0] }, colors[0], Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val rays = (8 * k).roundToInt().coerceAtLeast(4) * 2
        val sweep = 360f / rays
        val rot = s.angle + t * 2f
        val oval = RectF(cx - far, cy - far, cx + far, cy + far)
        p.color = withAlpha(colors.getOrElse(1) { WHITE }, 0.35f)
        for (i in 0 until rays step 2) c.drawArc(oval, rot + i * sweep, sweep, true, p)
        p.shader = RadialGradient(cx, cy, min(w, h) * 0.35f, intArrayOf(withAlpha(colors.getOrElse(2) { WHITE }, 0.9f), withAlpha(colors.getOrElse(2) { WHITE }, 0f)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, min(w, h) * 0.35f, p)
        p.shader = null
    }

    fun mosaic() {
        c.drawColor(lerpColor(colors[0], BLACK, 0.3f))
        val cols = (5 + 10 * k).roundToInt()
        val size = w / cols
        val rows = ceil(h / size).toInt()
        val gap = size * 0.06f
        val rr = size * 0.18f
        for (r in 0 until rows) for (q in 0 until cols) {
            val x = q * size; val y = r * size
            val noise = rnd.nextFloat() * 0.3f - 0.15f
            val pulse = if (t == 0f) 0f else 0.08f * sin(t * 0.5f + anim.nextFloat() * TAU)
            p.color = sample(colors, along(x + size / 2, y + size / 2) + noise + pulse)
            c.drawRoundRect(x + gap, y + gap, x + size - gap, y + size - gap, rr, rr, p)
        }
    }

    fun rings() {
        val cx = w * (0.2f + 0.6f * rnd.nextFloat())
        val cy = h * (0.2f + 0.6f * rnd.nextFloat())
        val far = hypot(max(cx, w - cx), max(cy, h - cy))
        val count = (10 * k).roundToInt().coerceAtLeast(3)
        val gap = far / count
        val offset = (t * gap * 0.08f) % (gap * colors.size)
        c.drawColor(colors[0])
        var i = count + colors.size
        while (i >= -colors.size) {
            val r = i * gap + offset
            if (r > 0) {
                p.color = colors[Math.floorMod(i, colors.size)]
                c.drawCircle(cx, cy, r, p)
            }
            i--
        }
    }
}
