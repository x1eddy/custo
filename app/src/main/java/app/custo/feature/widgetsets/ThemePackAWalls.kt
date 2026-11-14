package app.custo.feature.widgetsets

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Pack A wallpapers. Sizes are fractions of the canvas; every painter is seeded and deterministic. */
internal object PackAWalls {
    private fun fill(color: Int) = PackAKit.paint(color)
    private fun line(color: Int, width: Float) = PackAKit.paint(color, Paint.Style.STROKE, width)
    private fun alpha(color: Int, a: Float) = PackAKit.alpha(color, a)

    private fun vGradient(c: Canvas, top: Float, bottom: Float, w: Float, colors: IntArray, stops: FloatArray? = null) {
        c.drawRect(0f, top, w, bottom, fill(0xFF000000.toInt()).apply { shader = LinearGradient(0f, top, 0f, bottom, colors, stops, Shader.TileMode.CLAMP) })
    }

    private fun vignette(c: Canvas, w: Float, h: Float, strength: Int = 0x55) {
        c.drawRect(0f, 0f, w, h, fill(0xFF000000.toInt()).apply {
            shader = RadialGradient(w / 2, h / 2, h * 0.75f, 0x00000000, strength shl 24, Shader.TileMode.CLAMP)
        })
    }

    private fun puff(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        val p = fill(color)
        c.drawCircle(x, y, s * 0.5f, p)
        c.drawCircle(x - s * 0.5f, y + s * 0.15f, s * 0.36f, p)
        c.drawCircle(x + s * 0.55f, y + s * 0.12f, s * 0.4f, p)
        c.drawRoundRect(RectF(x - s * 0.85f, y + s * 0.1f, x + s * 0.95f, y + s * 0.5f), s * 0.2f, s * 0.2f, p)
    }

    // ---------- Blocky ----------

    // ---------- Matrix code ----------

    private const val GLYPHS = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789:=+*<>"

    /** Falling columns of glowing green glyphs on black, with faint scanlines. */
    val codeRain = WallPainter { c, w, h ->
        c.drawRect(0f, 0f, w, h, fill(0xFF000603.toInt()))
        val cols = 22
        val cw = w / cols
        val rowH = cw * 1.12f
        val rows = (h / rowH).toInt() + 1
        val rnd = Random(77)
        val tp = PackAKit.paint(0).apply { textSize = cw * 0.86f; typeface = PackAKit.typeface(mono = true, bold = false); textAlign = Paint.Align.CENTER }
        fun glyph() = GLYPHS[rnd.nextInt(GLYPHS.length)].toString()
        // Faint static glyphs behind the streams.
        tp.color = 0x1A39FF6A
        repeat(cols * rows / 5) { c.drawText(glyph(), (rnd.nextInt(cols) + 0.5f) * cw, (rnd.nextInt(rows) + 1) * rowH, tp) }
        for (col in 0 until cols) {
            repeat(1 + rnd.nextInt(2)) {
                val head = rnd.nextInt(rows + 8)
                val len = 8 + rnd.nextInt(22)
                for (k in 0 until len) {
                    val row = head - k
                    if (row < 0 || row > rows) continue
                    val fade = 1f - k.toFloat() / len
                    val x = (col + 0.5f) * cw; val y = (row + 1) * rowH
                    if (k == 0) {
                        tp.color = 0xFFDFFFE6.toInt()
                        tp.setShadowLayer(cw * 0.5f, 0f, 0f, 0xFF39FF6A.toInt())
                    } else {
                        tp.color = alpha(if (k < 3) 0xFF7CFF9E.toInt() else 0xFF2BE85E.toInt(), 0.15f + 0.85f * fade * fade)
                        tp.clearShadowLayer()
                    }
                    c.drawText(glyph(), x, y, tp)
                }
            }
        }
        tp.clearShadowLayer()
        val scan = line(0x14000000, h * 0.0012f)
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w, y, scan); y += h * 0.004f }
        vignette(c, w, h, 0x88)
    }

    // ---------- Blocky summer ----------

    /** Bright beach: sky, big sun, sea with wave lines, sand with palms, a ball, a board and two cube-head friends. */
    val beach = WallPainter { c, w, h ->
        vGradient(c, 0f, h * 0.56f, w, intArrayOf(0xFF3FB6F2.toInt(), 0xFF8ED8FA.toInt(), 0xFFD6F2FF.toInt()), floatArrayOf(0f, 0.7f, 1f))
        c.drawCircle(w * 0.78f, h * 0.12f, w * 0.2f, fill(0xFF000000.toInt()).apply { shader = RadialGradient(w * 0.78f, h * 0.12f, w * 0.2f, 0x88FFF2A8.toInt(), 0x00FFF2A8, Shader.TileMode.CLAMP) })
        c.drawCircle(w * 0.78f, h * 0.12f, w * 0.09f, fill(0xFFFFE15A.toInt()))
        puff(c, w * 0.2f, h * 0.1f, w * 0.12f, 0xF2FFFFFF.toInt())
        puff(c, w * 0.55f, h * 0.24f, w * 0.09f, 0xE6FFFFFF.toInt())
        puff(c, w * 0.9f, h * 0.34f, w * 0.07f, 0xD9FFFFFF.toInt())
        vGradient(c, h * 0.56f, h * 0.78f, w, intArrayOf(0xFF1E88E5.toInt(), 0xFF26C6DA.toInt(), 0xFF7FE3E8.toInt()), floatArrayOf(0f, 0.6f, 1f))
        val wave = line(0x88FFFFFF.toInt(), w * 0.006f)
        val rnd = Random(12)
        repeat(14) {
            val x = rnd.nextFloat() * w; val y = h * (0.58f + rnd.nextFloat() * 0.17f); val s = w * (0.03f + rnd.nextFloat() * 0.04f)
            c.drawPath(Path().apply { moveTo(x - s, y); quadTo(x - s / 2, y - s * 0.4f, x, y); quadTo(x + s / 2, y - s * 0.4f, x + s, y) }, wave)
        }
        val sand = Path().apply {
            moveTo(0f, h * 0.79f)
            var x = 0f
            while (x <= w) { lineTo(x, h * 0.785f + sin(x / w * PI.toFloat() * 3f) * h * 0.008f); x += w / 30f }
            lineTo(w, h); lineTo(0f, h); close()
        }
        c.drawPath(sand, fill(0xFFF2F7F5.toInt()))
        c.withSave { translate(0f, h * 0.008f); drawPath(sand, fill(0xFFFFDE9E.toInt())) }
        repeat(160) { c.drawCircle(rnd.nextFloat() * w, h * (0.8f + rnd.nextFloat() * 0.2f), w * 0.003f, fill(0x33B07A35)) }
        val ink = 0xFF0B3556.toInt()
        PackAKit.sticker(c, PackAArt.palm, w * 0.13f, h * 0.68f, w * 0.38f, ink)
        PackAKit.sticker(c, PackAArt.palm, w * 0.93f, h * 0.71f, w * 0.28f, ink, 6f)
        PackAKit.sticker(c, PackAArt.surfboard, w * 0.8f, h * 0.84f, w * 0.24f, ink, 8f)
        PackAKit.sticker(c, PackAArt.summerKid, w * 0.36f, h * 0.86f, w * 0.17f, ink)
        PackAKit.sticker(c, PackAArt.summerKid2, w * 0.56f, h * 0.87f, w * 0.17f, ink)
        PackAKit.sticker(c, PackAArt.beachBall, w * 0.17f, h * 0.9f, w * 0.1f, ink)
    }

    // ---------- Brick party ----------

    private fun studBrick(c: Canvas, x: Float, y: Float, studs: Int, unit: Float, color: Int, hgt: Float = 1f) {
        val ink = 0xFF1B2433.toInt()
        val wd = studs * unit
        val bh = unit * hgt
        for (i in 0 until studs) {
            val sx = x + i * unit + unit * 0.2f
            val sr = RectF(sx, y - unit * 0.25f, sx + unit * 0.6f, y + unit * 0.05f)
            c.drawRoundRect(sr, unit * 0.08f, unit * 0.08f, fill(color))
            c.drawRect(sx + unit * 0.08f, y - unit * 0.2f, sx + unit * 0.2f, y, fill(0x44FFFFFF))
            c.drawRoundRect(sr, unit * 0.08f, unit * 0.08f, line(ink, unit * 0.05f))
        }
        val body = RectF(x, y, x + wd, y + bh)
        c.drawRoundRect(body, unit * 0.1f, unit * 0.1f, fill(color))
        c.drawRect(x, y + bh * 0.78f, x + wd, y + bh, fill(PackAKit.shade(color, 0.78f)))
        c.drawRoundRect(body, unit * 0.1f, unit * 0.1f, line(ink, unit * 0.06f))
    }

    /** Toy obstacle course: sky, studded baseplate, floating bricks, coins, a checkpoint flag and builders. */
    val brickCourse = WallPainter { c, w, h ->
        vGradient(c, 0f, h, w, intArrayOf(0xFF4AA8FF.toInt(), 0xFF9AD4FF.toInt(), 0xFFD8F0FF.toInt()), floatArrayOf(0f, 0.6f, 1f))
        puff(c, w * 0.22f, h * 0.08f, w * 0.1f, 0xEEFFFFFF.toInt())
        puff(c, w * 0.75f, h * 0.18f, w * 0.12f, 0xEEFFFFFF.toInt())
        puff(c, w * 0.4f, h * 0.36f, w * 0.08f, 0xCCFFFFFF.toInt())
        val unit = w / 12f
        // Baseplate.
        val base = h * 0.86f
        c.drawRect(0f, base, w, h, fill(0xFF22A04B.toInt()))
        c.drawRect(0f, base, w, base + unit * 0.12f, fill(0xFF178A3C.toInt()))
        var sy = base + unit * 0.5f
        while (sy < h) {
            var sx = unit * 0.5f
            while (sx < w) {
                c.drawCircle(sx, sy, unit * 0.28f, fill(0xFF1B8B40.toInt()))
                c.drawCircle(sx - unit * 0.02f, sy - unit * 0.04f, unit * 0.25f, fill(0xFF2DB257.toInt()))
                sx += unit
            }
            sy += unit
        }
        val red = 0xFFE3342F.toInt(); val blue = 0xFF2563EB.toInt(); val yellow = 0xFFFACC15.toInt(); val green = 0xFF16A34A.toInt()
        data class Plat(val x: Float, val y: Float, val studs: Int, val col: Int)
        val plats = listOf(
            Plat(0.04f, 0.78f, 3, red), Plat(0.36f, 0.72f, 2, yellow), Plat(0.58f, 0.64f, 3, blue),
            Plat(0.1f, 0.56f, 2, green), Plat(0.72f, 0.5f, 3, red), Plat(0.4f, 0.44f, 2, blue),
        )
        for (p in plats) {
            val x = w * p.x; val y = h * p.y
            c.drawOval(RectF(x, y + unit * 1.3f, x + p.studs * unit, y + unit * 1.55f), fill(0x22000000))
            studBrick(c, x, y, p.studs, unit, p.col)
        }
        // Spinning bar on the blue platform.
        val bx = w * 0.58f + unit * 1.5f; val by = h * 0.64f - unit * 0.3f
        c.withRotation(-18f, bx, by - unit * 0.9f) {
            drawRoundRect(RectF(bx - unit * 1.6f, by - unit * 1.05f, bx + unit * 1.6f, by - unit * 0.75f), unit * 0.15f, unit * 0.15f, fill(0xFFFF5A5A.toInt()))
            drawRoundRect(RectF(bx - unit * 1.6f, by - unit * 1.05f, bx + unit * 1.6f, by - unit * 0.75f), unit * 0.15f, unit * 0.15f, line(0xFF1B2433.toInt(), unit * 0.06f))
        }
        c.drawRect(bx - unit * 0.08f, by - unit * 0.9f, bx + unit * 0.08f, by, fill(0xFF9AA3B2.toInt()))
        val ink = 0xFF1B2433.toInt()
        val rnd = Random(3)
        for (p in plats) if (rnd.nextFloat() < 0.7f) PackAKit.sticker(c, PackAArt.coin, w * p.x + unit * p.studs / 2f, h * p.y - unit * 1.1f, unit * 0.7f, ink)
        PackAKit.sticker(c, PackAArt.checkpoint, w * 0.72f + unit * 2.2f, h * 0.5f - unit * 0.95f, unit * 1.6f, ink)
        PackAKit.sticker(c, PackAArt.builder, w * 0.1f + unit * 1f, h * 0.56f - unit * 0.95f, unit * 1.6f, ink)
        PackAKit.sticker(c, PackAArt.builder2, w * 0.3f, base - unit * 0.8f, unit * 1.6f, ink)
        PackAKit.sticker(c, PackAArt.goldTrophy, w * 0.82f, base - unit * 0.7f, unit * 1.3f, ink)
    }

    // ---------- Tactical red ----------

    /** Off-black field with a faint grid, sharp red and grey shards, a hazard band and a ghost crosshair. */
    val shards = WallPainter { c, w, h ->
        c.drawRect(0f, 0f, w, h, fill(0xFF101013.toInt()))
        val grid = line(0xFF19191E.toInt(), w * 0.002f)
        val step = w / 14f
        var gx = 0f
        while (gx < w) { c.drawLine(gx, 0f, gx, h, grid); gx += step }
        var gy = 0f
        while (gy < h) { c.drawLine(0f, gy, w, gy, grid); gy += step }
        val rnd = Random(66)
        val palette = intArrayOf(0xFF8E1218.toInt(), 0xFFE8312F.toInt(), 0xFF26262C.toInt(), 0xFF3A3A42.toInt(), 0xFFB71C22.toInt(), 0xFF1E1E24.toInt())
        // Shards along a diagonal sweep from bottom-left to the upper right.
        repeat(34) { i ->
            val t = rnd.nextFloat()
            val cx = w * (-0.1f + t * 1.2f) + (rnd.nextFloat() - 0.5f) * w * 0.25f
            val cy = h * (0.95f - t * 0.6f) + (rnd.nextFloat() - 0.5f) * h * 0.2f
            val s = w * (0.06f + rnd.nextFloat() * 0.2f)
            val a0 = rnd.nextFloat() * 2 * PI.toFloat()
            val tri = Path().apply {
                moveTo(cx + cos(a0) * s, cy + sin(a0) * s)
                lineTo(cx + cos(a0 + 2.3f) * s * 0.5f, cy + sin(a0 + 2.3f) * s * 0.5f)
                lineTo(cx + cos(a0 + 3.6f) * s * 0.7f, cy + sin(a0 + 3.6f) * s * 0.7f)
                close()
            }
            val col = if (i % 9 == 0) 0xFFF2EDE4.toInt() else palette[rnd.nextInt(palette.size)]
            c.drawPath(tri, fill(alpha(col, if (i % 9 == 0) 0.85f else 0.92f)))
        }
        // Hazard band.
        c.withRotation(-24f, w / 2, h * 0.32f) {
            val band = RectF(-w, h * 0.32f - h * 0.018f, w * 2, h * 0.32f + h * 0.018f)
            withSave {
                clipRect(band)
                drawRect(band, fill(0xFFE8312F.toInt()))
                val stripe = line(0xFF101013.toInt(), h * 0.012f).apply { strokeCap = Paint.Cap.BUTT }
                var x = band.left
                while (x < band.right) { drawLine(x, band.bottom, x + band.height(), band.top, stripe); x += h * 0.03f }
            }
        }
        // Ghost crosshair and chevrons.
        val ghost = line(0x40F2EDE4, w * 0.004f)
        val cx = w * 0.74f; val cy = h * 0.16f; val rr = w * 0.13f
        c.drawCircle(cx, cy, rr, ghost)
        c.drawLine(cx - rr * 1.4f, cy, cx - rr * 0.4f, cy, ghost); c.drawLine(cx + rr * 0.4f, cy, cx + rr * 1.4f, cy, ghost)
        c.drawLine(cx, cy - rr * 1.4f, cx, cy - rr * 0.4f, ghost); c.drawLine(cx, cy + rr * 0.4f, cx, cy + rr * 1.4f, ghost)
        val chev = line(0x30F2EDE4, w * 0.012f).apply { strokeCap = Paint.Cap.BUTT; strokeJoin = Paint.Join.MITER }
        for (k in 0 until 3) {
            val y = h * 0.5f + k * w * 0.05f
            c.drawPath(Path().apply { moveTo(w * 0.06f, y + w * 0.04f); lineTo(w * 0.12f, y); lineTo(w * 0.18f, y + w * 0.04f) }, chev)
        }
        val tp = PackAKit.paint(0x66F2EDE4).apply { textSize = w * 0.03f; typeface = PackAKit.typeface(mono = true, bold = true) }
        c.drawText("// SECTOR 07", w * 0.06f, h * 0.045f, tp)
        vignette(c, w, h, 0x66)
    }

    // ---------- Battle royale ----------

    // ---------- Gamer RGB ----------

    /** Near-black desk with a dot grid, rainbow light streaks and faint gear. */
    val rgbDesk = WallPainter { c, w, h ->
        c.drawRect(0f, 0f, w, h, fill(0xFF07070C.toInt()))
        val dot = fill(0xFF16161F.toInt())
        val step = w / 20f
        var y = step / 2
        while (y < h) { var x = step / 2; while (x < w) { c.drawCircle(x, y, w * 0.003f, dot); x += step }; y += step }
        val rainbow = intArrayOf(0xFFFF3B5C.toInt(), 0xFFFFD23F.toInt(), 0xFF2BFF88.toInt(), 0xFF00E5FF.toInt(), 0xFF3B7BFF.toInt(), 0xFFFF2BD6.toInt())
        for ((k, y0) in floatArrayOf(0.18f, 0.5f, 0.82f).withIndex()) {
            val sh = LinearGradient(0f, 0f, w, 0f, rainbow, null, Shader.TileMode.MIRROR)
            val p = Path().apply { moveTo(-w * 0.1f, h * (y0 + 0.12f)); cubicTo(w * 0.3f, h * (y0 - 0.02f), w * 0.6f, h * (y0 + 0.1f), w * 1.1f, h * (y0 - 0.1f)) }
            val widths = floatArrayOf(0.09f, 0.04f, 0.012f)
            val alphas = intArrayOf(28, 60, if (k == 1) 220 else 160)
            for (i in widths.indices) c.drawPath(p, line(0, w * widths[i]).apply { shader = sh; alpha = alphas[i] })
        }
        c.saveLayerAlpha(0f, 0f, w, h, 70)
        val ink = 0xFF050508.toInt()
        PackAKit.sticker(c, PackAArt.controller, w * 0.24f, h * 0.36f, w * 0.26f, ink, -12f)
        PackAKit.sticker(c, PackAArt.headset, w * 0.78f, h * 0.68f, w * 0.26f, ink, 10f)
        PackAKit.sticker(c, PackAArt.keys, w * 0.74f, h * 0.12f, w * 0.2f, ink, 8f)
        PackAKit.sticker(c, PackAArt.mouse, w * 0.18f, h * 0.7f, w * 0.16f, ink, -18f)
        c.restore()
        vignette(c, w, h, 0x99)
    }

    // ---------- Synthwave ----------

    /** 80s sunset: purple-to-pink sky, striped sun, mountains, neon palms and a perspective grid. */
    val synthSunset = WallPainter { c, w, h ->
        val horizon = h * 0.62f
        vGradient(c, 0f, horizon, w, intArrayOf(0xFF0D0221.toInt(), 0xFF2B0B59.toInt(), 0xFF8E1F7E.toInt(), 0xFFFF6A88.toInt()), floatArrayOf(0f, 0.4f, 0.8f, 1f))
        val rnd = Random(84)
        repeat(90) {
            val p = fill(alpha(0xFFFFFFFF.toInt(), 0.3f + rnd.nextFloat() * 0.6f))
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.38f, w * (0.0015f + rnd.nextFloat() * 0.003f), p)
        }
        val sunR = w * 0.3f
        val sunY = horizon - sunR * 0.45f
        c.drawCircle(w / 2, sunY, sunR * 1.5f, fill(0xFF000000.toInt()).apply { shader = RadialGradient(w / 2, sunY, sunR * 1.5f, 0x66FF3EA5, 0x00FF3EA5, Shader.TileMode.CLAMP) })
        c.drawPath(PackAArt.stripedSun(w / 2, sunY, sunR), fill(0xFF000000.toInt()).apply {
            shader = LinearGradient(0f, sunY - sunR, 0f, sunY + sunR, 0xFFFFD319.toInt(), 0xFFFF3EA5.toInt(), Shader.TileMode.CLAMP)
        })
        fun mountains(color: Int, rim: Int, peaks: FloatArray, seed: Int) {
            val r2 = Random(seed)
            val p = Path().apply {
                moveTo(0f, horizon)
                for (i in peaks.indices) lineTo(w * (i + 0.5f) / peaks.size + (r2.nextFloat() - 0.5f) * w * 0.05f, horizon - h * peaks[i])
                lineTo(w, horizon); close()
            }
            c.drawPath(p, fill(color))
            c.drawPath(p, line(rim, w * 0.003f))
        }
        mountains(0xFF2A0A4A.toInt(), 0x88FF3EA5.toInt(), floatArrayOf(0.06f, 0.11f, 0.03f, 0.01f, 0.0f, 0.02f, 0.09f, 0.05f), 2)
        mountains(0xFF16052E.toInt(), 0xCC2DE2E6.toInt(), floatArrayOf(0.035f, 0.065f, 0.02f, 0.0f, 0.0f, 0.0f, 0.04f, 0.07f), 4)
        vGradient(c, horizon, h, w, intArrayOf(0xFF1A0436.toInt(), 0xFF0A0118.toInt()))
        val gp = line(0xCCFF3EA5.toInt(), w * 0.003f)
        val glow = line(0x33FF3EA5, w * 0.012f)
        for (k in 1..14) {
            val t = k / 14f
            val y = horizon + (h - horizon) * t * t
            c.drawLine(0f, y, w, y, glow); c.drawLine(0f, y, w, y, gp)
        }
        for (k in -14..14) {
            val xb = w / 2 + k * w * 0.16f
            c.drawLine(w / 2 + k * w * 0.012f, horizon, xb, h, glow)
            c.drawLine(w / 2 + k * w * 0.012f, horizon, xb, h, gp)
        }
        PackAKit.glow(c, Path().apply { moveTo(0f, horizon); lineTo(w, horizon) }, 0xFF2DE2E6.toInt(), w * 0.004f)
        val ink = 0xFF0D0420.toInt()
        PackAKit.sticker(c, PackAArt.neonPalm, w * 0.1f, horizon - w * 0.16f, w * 0.38f, ink, -4f)
        PackAKit.sticker(c, PackAArt.neonPalm, w * 0.9f, horizon - w * 0.12f, w * 0.3f, ink, 5f)
    }

    // ---------- Neon cyberpunk ----------

    /** Rainy night city: layered towers with lit windows, glowing signs, rain and street reflections. */
    val neonCity = WallPainter { c, w, h ->
        vGradient(c, 0f, h, w, intArrayOf(0xFF07051A.toInt(), 0xFF1E0B3A.toInt(), 0xFF4A1450.toInt(), 0xFF120822.toInt()), floatArrayOf(0f, 0.45f, 0.78f, 1f))
        val rnd = Random(2077)
        val neon = intArrayOf(0xFFFF2E97.toInt(), 0xFFFCE93A.toInt(), 0xFF21F0FF.toInt())
        val street = h * 0.86f
        fun layer(color: Int, minH: Float, maxH: Float, minW: Float, maxW: Float, windows: Float, signs: Boolean) {
            var x = -w * 0.05f
            while (x < w) {
                val bw = w * (minW + rnd.nextFloat() * (maxW - minW))
                val top = street - h * (minH + rnd.nextFloat() * (maxH - minH))
                c.drawRect(x, top, x + bw, street, fill(color))
                if (rnd.nextFloat() < 0.4f) c.drawRect(x + bw * 0.4f, top - h * 0.03f, x + bw * 0.45f, top, fill(color))
                val wx = w * 0.018f; val wy = h * 0.012f
                var yy = top + wy
                while (yy < street - wy) {
                    var xx = x + wx * 0.6f
                    while (xx < x + bw - wx) {
                        if (rnd.nextFloat() < windows) c.drawRect(xx, yy, xx + wx * 0.6f, yy + wy * 0.6f, fill(alpha(neon[rnd.nextInt(3)], 0.25f + rnd.nextFloat() * 0.5f)))
                        xx += wx
                    }
                    yy += wy * 1.6f
                }
                if (signs && rnd.nextFloat() < 0.6f) {
                    val col = neon[rnd.nextInt(3)]
                    val sx = x + bw * (0.2f + rnd.nextFloat() * 0.5f)
                    val sy = top + h * (0.04f + rnd.nextFloat() * 0.15f)
                    val sign = RectF(sx, sy, sx + w * 0.04f, sy + h * (0.06f + rnd.nextFloat() * 0.06f))
                    c.drawRect(sign, fill(0xFF0B0716.toInt()))
                    PackAKit.glow(c, Path().apply { addRect(sign, Path.Direction.CW) }, col, w * 0.003f)
                    var by = sign.top + sign.height() * 0.15f
                    while (by < sign.bottom - sign.height() * 0.12f) { c.drawRect(sign.left + sign.width() * 0.25f, by, sign.right - sign.width() * 0.25f, by + sign.height() * 0.05f, fill(alpha(col, 0.8f))); by += sign.height() * 0.13f }
                }
                x += bw + w * 0.01f
            }
        }
        layer(0xFF1A1030.toInt(), 0.35f, 0.62f, 0.1f, 0.2f, 0.12f, false)
        c.drawRect(0f, h * 0.3f, w, street, fill(0xFF000000.toInt()).apply { shader = LinearGradient(0f, h * 0.3f, 0f, street, 0x00FF2E97, 0x33FF2E97, Shader.TileMode.CLAMP) })
        layer(0xFF0B0716.toInt(), 0.18f, 0.42f, 0.14f, 0.26f, 0.22f, true)
        // Wet street with smeared reflections.
        c.drawRect(0f, street, w, h, fill(0xFF0A0614.toInt()))
        repeat(26) {
            val x = rnd.nextFloat() * w
            c.drawRect(x, street, x + w * (0.004f + rnd.nextFloat() * 0.02f), street + (h - street) * (0.3f + rnd.nextFloat() * 0.7f), fill(alpha(neon[rnd.nextInt(3)], 0.12f + rnd.nextFloat() * 0.2f)))
        }
        c.drawLine(0f, street, w, street, line(0x88FF2E97.toInt(), h * 0.002f))
        // Rain.
        val rain = line(0x40CFE8FF, w * 0.0025f)
        repeat(320) {
            val x = rnd.nextFloat() * w * 1.1f; val y = rnd.nextFloat() * h
            val len = h * (0.015f + rnd.nextFloat() * 0.03f)
            rain.alpha = 30 + rnd.nextInt(60)
            c.drawLine(x, y, x - len * 0.25f, y + len, rain)
        }
        vignette(c, w, h, 0x77)
    }

    // ---------- Football ----------

    /** A mown pitch seen from above, with all the markings. */
    val pitch = WallPainter { c, w, h ->
        val bands = 12
        val bh = h / bands
        for (i in 0 until bands) c.drawRect(0f, i * bh, w, (i + 1) * bh, fill(if (i % 2 == 0) 0xFF2E8B3E.toInt() else 0xFF379C48.toInt()))
        val lp = line(0xE6FFFFFF.toInt(), w * 0.007f)
        val m = w * 0.06f
        val field = RectF(m, h * 0.035f, w - m, h * 0.965f)
        c.drawRect(field, lp)
        c.drawLine(field.left, h / 2, field.right, h / 2, lp)
        c.drawCircle(w / 2, h / 2, w * 0.17f, lp)
        c.drawCircle(w / 2, h / 2, w * 0.012f, fill(0xE6FFFFFF.toInt()))
        for (top in booleanArrayOf(true, false)) {
            val sgn = if (top) 1f else -1f
            val edge = if (top) field.top else field.bottom
            val boxW = w * 0.6f; val boxD = h * 0.13f
            val gw = w * 0.3f; val gd = h * 0.045f
            c.drawRect(RectF(w / 2 - boxW / 2, minOf(edge, edge + sgn * boxD), w / 2 + boxW / 2, maxOf(edge, edge + sgn * boxD)), lp)
            c.drawRect(RectF(w / 2 - gw / 2, minOf(edge, edge + sgn * gd), w / 2 + gw / 2, maxOf(edge, edge + sgn * gd)), lp)
            val spot = edge + sgn * h * 0.09f
            c.drawCircle(w / 2, spot, w * 0.009f, fill(0xE6FFFFFF.toInt()))
            val arcR = w * 0.15f
            c.drawArc(RectF(w / 2 - arcR, spot - arcR, w / 2 + arcR, spot + arcR), if (top) 37f else 217f, 106f, false, lp)
            // Goal net behind the line.
            val net = RectF(w / 2 - w * 0.12f, if (top) 0f else field.bottom, w / 2 + w * 0.12f, if (top) field.top else h)
            c.drawRect(net, fill(0x33FFFFFF))
            val np = line(0x66FFFFFF, w * 0.002f)
            var nx = net.left
            while (nx <= net.right) { c.drawLine(nx, net.top, nx, net.bottom, np); nx += w * 0.02f }
        }
        val cr = w * 0.04f
        c.drawArc(RectF(field.left - cr, field.top - cr, field.left + cr, field.top + cr), 0f, 90f, false, lp)
        c.drawArc(RectF(field.right - cr, field.top - cr, field.right + cr, field.top + cr), 90f, 90f, false, lp)
        c.drawArc(RectF(field.left - cr, field.bottom - cr, field.left + cr, field.bottom + cr), 270f, 90f, false, lp)
        c.drawArc(RectF(field.right - cr, field.bottom - cr, field.right + cr, field.bottom + cr), 180f, 90f, false, lp)
        // Floodlight glow from the top corners.
        for (x in floatArrayOf(0f, w)) c.drawCircle(x, 0f, w * 0.7f, fill(0xFF000000.toInt()).apply { shader = RadialGradient(x, 0f, w * 0.7f, 0x33FFFFF0, 0x00FFFFF0, Shader.TileMode.CLAMP) })
        PackAKit.sticker(c, PackAArt.ball, w * 0.5f, h * 0.5f, w * 0.07f, 0xFF05090C.toInt(), 20f)
        vignette(c, w, h, 0x55)
    }
}
