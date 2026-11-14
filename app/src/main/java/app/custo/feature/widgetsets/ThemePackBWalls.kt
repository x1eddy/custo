package app.custo.feature.widgetsets

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withRotation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Pack B wallpapers. Sizes are relative to the canvas; every painter is deterministic. */
internal object PackBWalls {
    private val P = PackB
    private val A = PackBArt

    private fun fill(color: Int) = P.paint(color)

    private fun vertical(c: Canvas, w: Float, h: Float, colors: IntArray, stops: FloatArray? = null) {
        c.drawRect(0f, 0f, w, h, fill(0xFF000000.toInt()).apply { shader = LinearGradient(0f, 0f, 0f, h, colors, stops, Shader.TileMode.CLAMP) })
    }

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        c.drawCircle(x, y, r, fill(0xFF000000.toInt()).apply { shader = RadialGradient(x, y, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP) })
    }

    private fun art(c: Canvas, a: StickerArt, x: Float, y: Float, size: Float, ink: StickerInk, rot: Float = 0f) =
        ThemeArt.drawSticker(c, Sticker.STAR, x, y, size, ink, rot, a)

    /** Like [ThemeWallpapers.scatter], but with pack art. */
    private fun scatter(
        c: Canvas, w: Float, h: Float, seed: Int, arts: List<StickerArt>, ink: StickerInk,
        cols: Int = 4, minSize: Float = 0.07f, maxSize: Float = 0.11f, top: Float = 0f, bottom: Float = 1f,
        skip: Float = 0.3f, tilt: Float = 40f,
    ) {
        val rnd = Random(seed)
        val cell = w / cols
        val rows = ((h * bottom - top) / cell).toInt() + 1
        for (r in 0 until rows) for (col in 0 until cols) {
            if (rnd.nextFloat() < skip) continue
            val offset = if (r % 2 == 0) 0f else cell / 2
            val x = (col * cell + offset + cell * (0.2f + rnd.nextFloat() * 0.6f)) % w
            val y = top + r * cell + cell * (0.2f + rnd.nextFloat() * 0.6f)
            if (y > h * bottom) continue
            val size = w * (minSize + rnd.nextFloat() * (maxSize - minSize))
            art(c, arts[rnd.nextInt(arts.size)], x, y, size, ink, rnd.nextFloat() * tilt - tilt / 2)
        }
    }

    private fun stars(c: Canvas, w: Float, h: Float, seed: Int, n: Int, maxY: Float = 1f, color: Int = 0xFFFFFFFF.toInt()) {
        val rnd = Random(seed)
        val p = fill(color)
        repeat(n) {
            p.alpha = 60 + rnd.nextInt(190)
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * maxY, w * (0.0015f + rnd.nextFloat() * 0.004f), p)
        }
    }

    private fun vignette(c: Canvas, w: Float, h: Float, a: Int = 0x44000000) {
        c.drawRect(0f, 0f, w, h, fill(0xFF000000.toInt()).apply { shader = RadialGradient(w / 2, h / 2, h * 0.75f, 0x00000000, a, Shader.TileMode.CLAMP) })
    }

    // ---------- Comic hero ----------

    val comic = WallPainter { c, w, h ->
        c.drawRect(0f, 0f, w, h, fill(0xFFFFD43B.toInt()))
        // Action rays from a focus point.
        val fx = w * 0.5f; val fy = h * 0.38f
        val ray = fill(0xFFFFE98A.toInt())
        val n = 28
        for (i in 0 until n step 2) {
            val a0 = i * 2 * PI / n; val a1 = (i + 1) * 2 * PI / n
            val far = h * 1.5f
            c.drawPath(Path().apply {
                moveTo(fx, fy)
                lineTo(fx + (cos(a0) * far).toFloat(), fy + (sin(a0) * far).toFloat())
                lineTo(fx + (cos(a1) * far).toFloat(), fy + (sin(a1) * far).toFloat())
                close()
            }, ray)
        }
        // Halftone falling off toward the centre.
        val dot = fill(0x55E8282B)
        val step = w / 26f
        var y = 0f; var row = 0
        while (y < h) {
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < w) {
                val dist = kotlin.math.hypot(x - fx, y - fy) / h
                val k = ((dist - 0.25f) * 1.6f).coerceIn(0f, 1f)
                if (k > 0.05f) c.drawCircle(x, y, step * 0.38f * k, dot)
                x += step
            }
            y += step; row++
        }
        // Speed lines.
        val line = P.paint(0xFF15131A.toInt(), Paint.Style.STROKE, w * 0.005f)
        val rnd = Random(3)
        repeat(18) {
            val a = rnd.nextFloat() * 2 * PI
            val r0 = h * (0.3f + rnd.nextFloat() * 0.1f); val r1 = r0 + h * (0.06f + rnd.nextFloat() * 0.08f)
            c.drawLine(fx + (cos(a) * r0).toFloat(), fy + (sin(a) * r0).toFloat(), fx + (cos(a) * r1).toFloat(), fy + (sin(a) * r1).toFloat(), line)
        }
        // City skyline.
        val sky = 0xFF1B3A8C.toInt()
        val base = h * 0.8f
        var x = -w * 0.02f
        val win = fill(0xFFFFE98A.toInt())
        val outline = P.paint(0xFF15131A.toInt(), Paint.Style.STROKE, w * 0.006f)
        while (x < w) {
            val bw = w * (0.09f + rnd.nextFloat() * 0.08f)
            val bh = h * (0.08f + rnd.nextFloat() * 0.16f)
            val rect = RectF(x, base - bh, x + bw, h)
            c.drawRect(rect, fill(sky)); c.drawRect(rect, outline)
            var wy = rect.top + w * 0.025f
            while (wy < h - w * 0.03f) {
                var wx = rect.left + w * 0.018f
                while (wx < rect.right - w * 0.03f) {
                    if (rnd.nextFloat() < 0.55f) c.drawRect(wx, wy, wx + w * 0.016f, wy + w * 0.022f, win)
                    wx += w * 0.032f
                }
                wy += w * 0.045f
            }
            x += bw
        }
        c.drawRect(0f, h * 0.94f, w, h, fill(0xFF15131A.toInt()))
        val ink = StickerInk(InkStyle.COLOR, 0xFF15131A.toInt(), 0xFFE8282B.toInt(), 0xFF1E6FD9.toInt(), 0xFFFFD43B.toInt())
        art(c, A.pow, w * 0.24f, h * 0.14f, w * 0.3f, ink, -12f)
        art(c, A.bam, w * 0.78f, h * 0.3f, w * 0.28f, ink, 8f)
        art(c, A.zap, w * 0.2f, h * 0.55f, w * 0.22f, ink, 10f)
        art(c, A.comicBolt, w * 0.82f, h * 0.6f, w * 0.18f, ink, 15f)
        art(c, A.mask, w * 0.52f, h * 0.68f, w * 0.2f, ink, -6f)
    }

    // ---------- Pirate crew ----------

    val pirate = WallPainter { c, w, h ->
        val horizon = h * 0.52f
        c.drawRect(0f, 0f, w, horizon, fill(0xFF000000.toInt()).apply {
            shader = LinearGradient(0f, 0f, 0f, horizon, intArrayOf(0xFF6EC6F0.toInt(), 0xFFBFE9FF.toInt(), 0xFFFFE8B8.toInt()), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        })
        glow(c, w * 0.78f, h * 0.16f, w * 0.26f, 0x88FFF3C4.toInt())
        c.drawCircle(w * 0.78f, h * 0.16f, w * 0.08f, fill(0xFFFFF3C4.toInt()))
        // Clouds.
        fun cloud(x: Float, y: Float, k: Float) {
            val p = fill(0xEEFFFFFF.toInt())
            c.drawCircle(x, y, w * 0.06f * k, p); c.drawCircle(x + w * 0.07f * k, y - w * 0.03f * k, w * 0.075f * k, p)
            c.drawCircle(x + w * 0.15f * k, y, w * 0.055f * k, p); c.drawRect(x, y, x + w * 0.15f * k, y + w * 0.05f * k, p)
        }
        cloud(w * 0.08f, h * 0.12f, 1f); cloud(w * 0.5f, h * 0.27f, 0.7f)
        // Sea.
        c.drawRect(0f, horizon, w, h, fill(0xFF000000.toInt()).apply {
            shader = LinearGradient(0f, horizon, 0f, h, intArrayOf(0xFF3A8DDE.toInt(), 0xFF1F5FA8.toInt(), 0xFF153F78.toInt()), null, Shader.TileMode.CLAMP)
        })
        val foam = P.paint(0xCCFFFFFF.toInt(), Paint.Style.STROKE, w * 0.006f)
        val rnd = Random(9)
        var y = horizon + h * 0.03f
        while (y < h) {
            var x = -rnd.nextFloat() * w * 0.2f
            val seg = w * (0.08f + (y - horizon) / h * 0.12f)
            while (x < w) {
                if (rnd.nextFloat() < 0.6f) c.drawPath(Path().apply { moveTo(x, y); quadTo(x + seg / 2, y - seg * 0.3f, x + seg, y) }, foam)
                x += seg * 1.6f
            }
            y += h * 0.05f
        }
        // Gulls.
        val gull = P.paint(0xFF3B2414.toInt(), Paint.Style.STROKE, w * 0.005f)
        listOf(0.3f to 0.1f, 0.38f to 0.14f, 0.62f to 0.08f).forEach { (gx, gy) ->
            val x0 = w * gx; val y0 = h * gy; val s = w * 0.025f
            c.drawPath(Path().apply { moveTo(x0 - s, y0); quadTo(x0 - s / 2, y0 - s * 0.7f, x0, y0); quadTo(x0 + s / 2, y0 - s * 0.7f, x0 + s, y0) }, gull)
        }
        val ink = StickerInk(InkStyle.COLOR, 0xFF3B2414.toInt(), 0xFFD7322E.toInt(), 0xFFE7B847.toInt(), 0xFFF3E2B8.toInt())
        art(c, A.ship, w * 0.32f, horizon - w * 0.06f, w * 0.38f, ink, -3f)
        art(c, A.jolly, w * 0.82f, h * 0.4f, w * 0.2f, ink, 6f)
        art(c, A.wave, w * 0.14f, h * 0.7f, w * 0.22f, ink)
        art(c, A.map, w * 0.7f, h * 0.72f, w * 0.26f, ink, -10f)
        art(c, A.compass, w * 0.28f, h * 0.88f, w * 0.18f, ink, 12f)
        art(c, A.strawHat, w * 0.8f, h * 0.9f, w * 0.24f, ink, -8f)
    }

    // ---------- Cursed energy ----------

    val cursed = WallPainter { c, w, h ->
        vertical(c, w, h, intArrayOf(0xFF07061A.toInt(), 0xFF14122B.toInt(), 0xFF231A4A.toInt()))
        glow(c, w * 0.2f, h * 0.3f, w * 0.6f, 0x557C5CFF)
        glow(c, w * 0.85f, h * 0.7f, w * 0.55f, 0x443FB6FF)
        // Ink splashes.
        val rnd = Random(77)
        val inkP = fill(0xFF050410.toInt())
        repeat(7) {
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h; val r = w * (0.06f + rnd.nextFloat() * 0.12f)
            c.drawCircle(x, y, r, inkP)
            repeat(8) {
                val a = rnd.nextFloat() * 2 * PI; val dd = r * (1.1f + rnd.nextFloat() * 0.8f)
                c.drawCircle(x + (cos(a) * dd).toFloat(), y + (sin(a) * dd).toFloat(), r * (0.05f + rnd.nextFloat() * 0.15f), inkP)
            }
        }
        // Energy swirls.
        fun spiral(x: Float, y: Float, r: Float, turns: Float, col: Int) {
            val p = Path()
            val n = 120
            for (i in 0..n) {
                val t = i / n.toFloat()
                val a = t * turns * 2 * PI
                val rr = r * t
                val px = x + (cos(a) * rr).toFloat(); val py = y + (sin(a) * rr).toFloat()
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            c.drawPath(p, P.paint(P.alpha(col, 0.25f), Paint.Style.STROKE, w * 0.03f))
            c.drawPath(p, P.paint(P.alpha(col, 0.8f), Paint.Style.STROKE, w * 0.01f))
            c.drawPath(p, P.paint(0xCCE9E3FF.toInt(), Paint.Style.STROKE, w * 0.003f))
        }
        spiral(w * 0.22f, h * 0.28f, w * 0.26f, 2.6f, 0xFF7C5CFF.toInt())
        spiral(w * 0.8f, h * 0.72f, w * 0.22f, 2.2f, 0xFF3FB6FF.toInt())
        // Brush slash.
        val slash = Path().apply { moveTo(-w * 0.05f, h * 0.55f); quadTo(w * 0.5f, h * 0.45f, w * 1.05f, h * 0.5f) }
        c.drawPath(slash, P.paint(0x667C5CFF, Paint.Style.STROKE, w * 0.05f))
        c.drawPath(slash, P.paint(0xAA3FB6FF.toInt(), Paint.Style.STROKE, w * 0.012f))
        // Embers.
        repeat(60) {
            val p = fill(if (rnd.nextBoolean()) 0xFF9C86FF.toInt() else 0xFF7FD8FF.toInt())
            p.alpha = 80 + rnd.nextInt(150)
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, w * (0.002f + rnd.nextFloat() * 0.004f), p)
        }
        val ink = StickerInk(InkStyle.COLOR, 0xFF0A0918.toInt(), 0xFF7C5CFF.toInt(), 0xFF3FB6FF.toInt(), 0xFF14122B.toInt())
        art(c, A.eye, w * 0.5f, h * 0.12f, w * 0.3f, ink)
        art(c, A.ofuda, w * 0.12f, h * 0.62f, w * 0.26f, ink, -14f)
        art(c, A.ofuda, w * 0.88f, h * 0.36f, w * 0.24f, ink, 12f)
        art(c, A.flame, w * 0.58f, h * 0.84f, w * 0.2f, ink)
        art(c, A.handSign, w * 0.34f, h * 0.88f, w * 0.2f, ink, -8f)
        art(c, A.flame, w * 0.82f, h * 0.92f, w * 0.13f, ink, 10f)
        vignette(c, w, h, 0x66000000)
    }

    // ---------- Rock ----------

    val rock = WallPainter { c, w, h ->
        vertical(c, w, h, intArrayOf(0xFF1A1A1C.toInt(), 0xFF0E0E0F.toInt()))
        val rnd = Random(66)
        // Grunge speckle and scratches.
        repeat(900) {
            val p = fill(if (rnd.nextFloat() < 0.7f) 0xFF3A3A3E.toInt() else 0xFF050505.toInt())
            p.alpha = 40 + rnd.nextInt(120)
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h; val s = w * (0.001f + rnd.nextFloat() * 0.004f)
            c.drawRect(x, y, x + s, y + s * (0.5f + rnd.nextFloat()), p)
        }
        val scratch = P.paint(0x22FFFFFF, Paint.Style.STROKE, w * 0.0015f)
        repeat(40) {
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h; val l = w * (0.05f + rnd.nextFloat() * 0.2f)
            c.drawLine(x, y, x + l, y + l * (rnd.nextFloat() - 0.5f) * 0.3f, scratch)
        }
        // Red paint swipe.
        val swipe = Path().apply {
            moveTo(-w * 0.1f, h * 0.36f)
            var x = -w * 0.1f
            while (x < w * 1.1f) { lineTo(x, h * 0.36f - (x / w) * h * 0.12f + (rnd.nextFloat() - 0.5f) * w * 0.02f); x += w * 0.04f }
            x = w * 1.1f
            while (x > -w * 0.1f) { lineTo(x, h * 0.44f - (x / w) * h * 0.12f + (rnd.nextFloat() - 0.5f) * w * 0.03f); x -= w * 0.04f }
            close()
        }
        c.drawPath(swipe, fill(0xCCD7192A.toInt()))
        // Torn paper strip.
        c.withRotation(-6f, w / 2, h * 0.7f) {
        val strip = Path().apply {
            moveTo(-w * 0.1f, h * 0.67f)
            var x = -w * 0.1f
            while (x < w * 1.1f) { x += w * 0.03f; lineTo(x, h * 0.67f + (rnd.nextFloat() - 0.5f) * w * 0.02f) }
            while (x > -w * 0.1f) { x -= w * 0.03f; lineTo(x, h * 0.73f + (rnd.nextFloat() - 0.5f) * w * 0.02f) }
            close()
        }
        c.drawPath(strip, fill(0xFFE9E4DA.toInt()))
        val stud = fill(0xFFC9CED6.toInt())
        var sx = w * 0.04f
        while (sx < w) { c.drawCircle(sx, h * 0.7f, w * 0.011f, stud); c.drawCircle(sx - w * 0.003f, h * 0.7f - w * 0.003f, w * 0.004f, fill(0xFFFFFFFF.toInt())); sx += w * 0.07f }
        }
        val ink = StickerInk(InkStyle.COLOR, 0xFF0A0A0A.toInt(), 0xFFD7192A.toInt(), 0xFFC9CED6.toInt(), 0xFF161616.toInt())
        art(c, A.guitar, w * 0.72f, h * 0.2f, w * 0.42f, ink)
        art(c, A.skull, w * 0.24f, h * 0.18f, w * 0.3f, ink, -8f)
        art(c, A.amp, w * 0.22f, h * 0.86f, w * 0.26f, ink, -4f)
        art(c, A.rockBolt, w * 0.5f, h * 0.55f, w * 0.18f, ink, 12f)
        art(c, A.pick, w * 0.8f, h * 0.88f, w * 0.15f, ink, 20f)
        art(c, A.studHeart, w * 0.6f, h * 0.82f, w * 0.13f, ink, -10f)
        art(c, A.rockBolt, w * 0.1f, h * 0.5f, w * 0.1f, ink, -20f)
        vignette(c, w, h, 0x88000000.toInt())
    }

    // ---------- Music ----------

    val music = WallPainter { c, w, h ->
        vertical(c, w, h, intArrayOf(0xFFFFD6E2.toInt(), 0xFFE2D4FF.toInt(), 0xFFCBE9FF.toInt()))
        glow(c, w * 0.15f, h * 0.2f, w * 0.5f, 0x66FFFFFF)
        glow(c, w * 0.9f, h * 0.55f, w * 0.45f, 0x55FFE2C2)
        glow(c, w * 0.3f, h * 0.85f, w * 0.4f, 0x55FFFFFF)
        val ink = StickerInk(InkStyle.COLOR, 0xFF3D2C5E.toInt(), 0xFFFF8FB1.toInt(), 0xFF9FD8F5.toInt(), 0xFFFDF6F0.toInt())
        // Big record, half off the right edge.
        art(c, A.vinyl, w * 0.98f, h * 0.24f, w * 0.75f, ink)
        // Wavy staff lines.
        val staff = P.paint(0x553D2C5E, Paint.Style.STROKE, w * 0.003f)
        for (i in 0 until 5) {
            val y0 = h * 0.5f + i * w * 0.025f
            c.drawPath(Path().apply { moveTo(0f, y0); cubicTo(w * 0.3f, y0 - h * 0.05f, w * 0.6f, y0 + h * 0.05f, w, y0 - h * 0.01f) }, staff)
        }
        // Equalizer along the bottom.
        val cols = intArrayOf(0xFFFF8FB1.toInt(), 0xFFFFC59E.toInt(), 0xFFFFE07A.toInt(), 0xFF9FE6C4.toInt(), 0xFF9FD8F5.toInt(), 0xFFC3A6FF.toInt())
        val n = 16
        val bw = w / n
        val rnd = Random(5)
        for (i in 0 until n) {
            val bh = h * (0.04f + (0.5f + 0.5f * sin(i * 0.7f)) * 0.08f + rnd.nextFloat() * 0.03f)
            c.drawRoundRect(RectF(i * bw + bw * 0.15f, h - bh, i * bw + bw * 0.85f, h + bw), bw * 0.3f, bw * 0.3f, fill(P.alpha(cols[i % cols.size], 0.85f)))
        }
        art(c, A.headphones, w * 0.25f, h * 0.3f, w * 0.3f, ink, -8f)
        art(c, A.cassette, w * 0.7f, h * 0.68f, w * 0.28f, ink, 8f)
        scatter(c, w, h, 21, listOf(A.notes, A.notes, A.eq), ink, cols = 4, minSize = 0.06f, maxSize = 0.09f, top = h * 0.05f, bottom = 0.82f, skip = 0.7f)
    }

    // ---------- Galaxy ----------

    val galaxy = WallPainter { c, w, h ->
        vertical(c, w, h, intArrayOf(0xFF07071A.toInt(), 0xFF120F33.toInt(), 0xFF1B1840.toInt()))
        glow(c, w * 0.3f, h * 0.35f, w * 0.7f, 0x66A23BEC)
        glow(c, w * 0.75f, h * 0.55f, w * 0.6f, 0x55FF5FA2)
        glow(c, w * 0.5f, h * 0.2f, w * 0.5f, 0x4437B5FF)
        glow(c, w * 0.2f, h * 0.8f, w * 0.5f, 0x4437B5FF)
        stars(c, w, h, 12, 340)
        val ink = StickerInk(InkStyle.COLOR, 0xFF0E0C24.toInt(), 0xFFFFC94D.toInt(), 0xFF7FE0FF.toInt(), 0xFF1B1840.toInt())
        val rnd = Random(4)
        repeat(10) { art(c, A.twinkle, rnd.nextFloat() * w, rnd.nextFloat() * h, w * (0.03f + rnd.nextFloat() * 0.03f), ink) }
        // Shooting star.
        c.drawLine(w * 0.55f, h * 0.08f, w * 0.85f, h * 0.02f, P.paint(0xFF000000.toInt()).apply {
            strokeWidth = w * 0.006f; strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(w * 0.55f, h * 0.08f, w * 0.85f, h * 0.02f, 0x00FFFFFF, 0xFFFFFFFF.toInt(), Shader.TileMode.CLAMP)
        })
        art(c, A.planet, w * 0.76f, h * 0.24f, w * 0.42f, ink, 0f)
        art(c, A.moon, w * 0.16f, h * 0.12f, w * 0.18f, ink, -20f)
        art(c, A.rocket, w * 0.24f, h * 0.55f, w * 0.26f, ink, 32f)
        art(c, A.astronaut, w * 0.74f, h * 0.74f, w * 0.28f, ink, 14f)
        art(c, A.ufo, w * 0.3f, h * 0.88f, w * 0.18f, ink, -8f)
    }

    // ---------- Halloween ----------

    val halloween = WallPainter { c, w, h ->
        vertical(c, w, h, intArrayOf(0xFF140A24.toInt(), 0xFF2B1B3D.toInt(), 0xFF4B2A6E.toInt()))
        stars(c, w, h, 31, 120, maxY = 0.7f, color = 0xFFFFE9C9.toInt())
        val mx = w * 0.68f; val my = h * 0.2f
        glow(c, mx, my, w * 0.4f, 0x66FFD58A)
        c.drawCircle(mx, my, w * 0.17f, fill(0xFFFFE9A8.toInt()))
        c.drawCircle(mx - w * 0.05f, my + w * 0.04f, w * 0.03f, fill(0x22A0703A))
        c.drawCircle(mx + w * 0.06f, my - w * 0.05f, w * 0.02f, fill(0x22A0703A))
        // Hills.
        fun hill(top: Float, amp: Float, color: Int, phase: Float) {
            val p = Path().apply {
                moveTo(0f, h); lineTo(0f, top)
                var x = 0f
                while (x <= w) { lineTo(x, top - amp * sin((x / w) * PI.toFloat() * 1.4f + phase)); x += w / 40f }
                lineTo(w, h); close()
            }
            c.drawPath(p, fill(color))
        }
        hill(h * 0.8f, h * 0.04f, 0xFF2A1840.toInt(), 0.6f)
        // Bare tree.
        val br = P.paint(0xFF1A0F26.toInt(), Paint.Style.STROKE, w * 0.02f)
        c.drawLine(w * 0.12f, h * 0.82f, w * 0.14f, h * 0.6f, br)
        br.strokeWidth = w * 0.01f
        c.drawLine(w * 0.14f, h * 0.66f, w * 0.26f, h * 0.58f, br)
        c.drawLine(w * 0.135f, h * 0.7f, w * 0.04f, h * 0.63f, br)
        c.drawLine(w * 0.14f, h * 0.6f, w * 0.1f, h * 0.54f, br)
        hill(h * 0.88f, h * 0.03f, 0xFF1A0F26.toInt(), 2.4f)
        val ink = StickerInk(InkStyle.COLOR, 0xFF1A0F26.toInt(), 0xFFFF8A1F.toInt(), 0xFF8E5BD8.toInt(), 0xFF2B1B3D.toInt())
        art(c, A.pumpkin, w * 0.32f, h * 0.86f, w * 0.22f, ink, -6f)
        art(c, A.pumpkin, w * 0.52f, h * 0.89f, w * 0.15f, ink, 8f)
        art(c, A.pumpkin, w * 0.84f, h * 0.87f, w * 0.18f, ink, 4f)
        art(c, A.bat, w * 0.6f, h * 0.14f, w * 0.16f, ink, -10f)
        art(c, A.bat, w * 0.82f, h * 0.3f, w * 0.11f, ink, 12f)
        art(c, A.bat, w * 0.3f, h * 0.08f, w * 0.1f, ink, 6f)
        art(c, A.ghost, w * 0.22f, h * 0.36f, w * 0.22f, ink, -8f)
        art(c, A.ghost, w * 0.8f, h * 0.58f, w * 0.16f, ink, 10f)
        art(c, A.witchHat, w * 0.48f, h * 0.48f, w * 0.14f, ink, 14f)
        art(c, A.candyCorn, w * 0.66f, h * 0.76f, w * 0.08f, ink, -12f)
        art(c, A.candyCorn, w * 0.08f, h * 0.92f, w * 0.07f, ink, 16f)
    }

    // ---------- Dinosaurs ----------

    val dinos = WallPainter { c, w, h ->
        vertical(c, w, h, intArrayOf(0xFFBFEFFF.toInt(), 0xFFDDF7EA.toInt(), 0xFFF1FBE6.toInt()), floatArrayOf(0f, 0.55f, 1f))
        glow(c, w * 0.18f, h * 0.1f, w * 0.3f, 0x99FFF3B0.toInt())
        c.drawCircle(w * 0.18f, h * 0.1f, w * 0.08f, fill(0xFFFFE98A.toInt()))
        val ink = StickerInk(InkStyle.COLOR, 0xFF2F3B2A.toInt(), 0xFFFF9F43.toInt(), 0xFF8FD26A.toInt(), 0xFFF1FBE6.toInt())
        art(c, A.volcano, w * 0.7f, h * 0.38f, w * 0.55f, ink)
        fun hill(top: Float, amp: Float, color: Int, phase: Float) {
            val p = Path().apply {
                moveTo(0f, h); lineTo(0f, top)
                var x = 0f
                while (x <= w) { lineTo(x, top - amp * sin((x / w) * PI.toFloat() * 1.8f + phase)); x += w / 40f }
                lineTo(w, h); close()
            }
            c.drawPath(p, fill(color))
            c.drawPath(p, P.paint(0xFF2F3B2A.toInt(), Paint.Style.STROKE, w * 0.004f))
        }
        hill(h * 0.56f, h * 0.025f, 0xFFA9DE8A.toInt(), 0.3f)
        hill(h * 0.7f, h * 0.03f, 0xFF8FD26A.toInt(), 2f)
        // Footprint trail.
        for (i in 0 until 5) art(c, A.footprint, w * (0.1f + i * 0.18f), h * (0.94f - (i % 2) * 0.03f), w * 0.07f, ink, 70f)
        art(c, A.longNeck, w * 0.3f, h * 0.6f, w * 0.38f, ink)
        art(c, A.tinyRex, w * 0.76f, h * 0.76f, w * 0.3f, ink, 4f)
        art(c, A.egg, w * 0.48f, h * 0.84f, w * 0.12f, ink, -10f)
        art(c, A.egg, w * 0.56f, h * 0.86f, w * 0.09f, ink, 12f)
        art(c, A.leaf, w * 0.06f, h * 0.78f, w * 0.3f, ink, -30f)
        art(c, A.leaf, w * 0.95f, h * 0.55f, w * 0.26f, ink, 200f)
        art(c, A.leaf, w * 0.1f, h * 0.28f, w * 0.18f, ink, 160f)
    }

    // ---------- Ocean ----------

    val ocean = WallPainter { c, w, h ->
        vertical(c, w, h, intArrayOf(0xFF8FE3DE.toInt(), 0xFF2E9CC0.toInt(), 0xFF135C86.toInt(), 0xFF0B3D5C.toInt()), floatArrayOf(0f, 0.35f, 0.75f, 1f))
        // Light rays.
        val rnd = Random(8)
        repeat(6) { i ->
            val x0 = w * (0.05f + i * 0.18f + rnd.nextFloat() * 0.05f)
            val ray = Path().apply { moveTo(x0, 0f); lineTo(x0 + w * 0.08f, 0f); lineTo(x0 + w * 0.22f, h * 0.7f); lineTo(x0 + w * 0.06f, h * 0.7f); close() }
            c.drawPath(ray, fill(0xFF000000.toInt()).apply { shader = LinearGradient(0f, 0f, 0f, h * 0.7f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP) })
        }
        // Sand.
        val sand = Path().apply {
            moveTo(0f, h); lineTo(0f, h * 0.86f)
            cubicTo(w * 0.3f, h * 0.82f, w * 0.6f, h * 0.9f, w, h * 0.84f); lineTo(w, h); close()
        }
        c.drawPath(sand, fill(0xFFF2D6A2.toInt()))
        val ink = StickerInk(InkStyle.COLOR, 0xFF0D3B4A.toInt(), 0xFFFF8C7A.toInt(), 0xFF4FC3D0.toInt(), 0xFFE6F8F7.toInt())
        art(c, A.coral, w * 0.12f, h * 0.83f, w * 0.26f, ink, -4f)
        art(c, A.coral, w * 0.88f, h * 0.8f, w * 0.3f, ink, 6f)
        art(c, A.shell, w * 0.42f, h * 0.93f, w * 0.1f, ink, -10f)
        art(c, A.starfish, w * 0.62f, h * 0.92f, w * 0.12f, ink, 15f)
        art(c, A.whale, w * 0.62f, h * 0.36f, w * 0.48f, ink)
        art(c, A.jelly, w * 0.2f, h * 0.18f, w * 0.2f, ink, -6f)
        art(c, A.jelly, w * 0.86f, h * 0.6f, w * 0.14f, ink, 8f)
        art(c, A.fish, w * 0.26f, h * 0.56f, w * 0.18f, ink, 4f)
        art(c, A.fish, w * 0.5f, h * 0.68f, w * 0.12f, ink, -6f)
        // Bubbles.
        val ring = P.paint(0x99FFFFFF.toInt(), Paint.Style.STROKE, w * 0.003f)
        val hi = fill(0xBBFFFFFF.toInt())
        repeat(40) {
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h * 0.85f; val r = w * (0.006f + rnd.nextFloat() * 0.018f)
            c.drawCircle(x, y, r, ring); c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.25f, hi)
        }
    }

    // ---------- Mono ----------

    val mono = WallPainter { c, w, h ->
        c.drawRect(0f, 0f, w, h, fill(0xFFF7F6F3.toInt()))
        val hair = P.paint(0xFF111111.toInt(), Paint.Style.STROKE, w * 0.0025f)
        c.drawCircle(w * 0.62f, h * 0.26f, w * 0.3f, hair)
        c.drawLine(w * 0.08f, h * 0.5f, w * 0.92f, h * 0.5f, hair)
        c.drawLine(w * 0.08f, h * 0.88f, w * 0.38f, h * 0.88f, hair)
        // A faint dot grid in one corner.
        val dot = fill(0x33111111)
        for (i in 0 until 6) for (j in 0 until 6) c.drawCircle(w * (0.1f + i * 0.035f), h * (0.08f + j * 0.02f), w * 0.0035f, dot)
        // The one accent.
        c.drawCircle(w * 0.84f, h * 0.5f, w * 0.014f, fill(0xFFE5484D.toInt()))
    }
}
