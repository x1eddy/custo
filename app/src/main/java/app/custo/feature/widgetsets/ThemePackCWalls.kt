package app.custo.feature.widgetsets

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import app.custo.feature.widgetsets.PackCArt as A

/** Wallpaper painters for theme pack C. Sizes are fractions of the canvas, so any resolution works. */
internal object PackCWalls {

    private fun fill(color: Int) = A.pp(color)

    private fun vgrad(c: Canvas, w: Float, h: Float, colors: IntArray, pos: FloatArray? = null) =
        c.drawRect(0f, 0f, w, h, fill(0xFF000000.toInt()).apply { shader = LinearGradient(0f, 0f, 0f, h, colors, pos, Shader.TileMode.CLAMP) })

    private fun vignette(c: Canvas, w: Float, h: Float, edge: Int) =
        c.drawRect(0f, 0f, w, h, fill(0xFF000000.toInt()).apply { shader = RadialGradient(w / 2, h / 2, h * 0.75f, 0x00000000, edge, Shader.TileMode.CLAMP) })

    private fun art(c: Canvas, a: StickerArt, x: Float, y: Float, size: Float, ink: StickerInk, rot: Float = 0f) =
        ThemeArt.drawSticker(c, Sticker.STAR, x, y, size, ink, rot, a)

    private fun colorInk(line: Int, accent: Int, secondary: Int, paper: Int) = StickerInk(InkStyle.COLOR, line, accent, secondary, paper)

    /** Like [ThemeWallpapers.scatter], for pack art: a jittered grid so nothing piles up. */
    private fun scatter(
        c: Canvas, w: Float, h: Float, seed: Int, arts: List<StickerArt>, ink: StickerInk,
        cols: Int = 4, minSize: Float = 0.07f, maxSize: Float = 0.11f, top: Float = 0f, bottom: Float = h,
        skip: Float = 0.2f, tilt: Float = 40f,
    ) {
        val rnd = Random(seed)
        val cell = w / cols
        val rows = ((bottom - top) / cell).toInt() + 1
        for (r in 0 until rows) for (col in 0 until cols) {
            if (rnd.nextFloat() < skip) { rnd.nextFloat(); continue }
            val offset = if (r % 2 == 0) 0f else cell / 2
            val x = (col * cell + offset + cell * (0.2f + rnd.nextFloat() * 0.6f)) % w
            val y = top + r * cell + cell * (0.2f + rnd.nextFloat() * 0.6f)
            if (y > bottom) continue
            val size = w * (minSize + rnd.nextFloat() * (maxSize - minSize))
            art(c, arts[rnd.nextInt(arts.size)], x, y, size, ink, rnd.nextFloat() * tilt - tilt / 2)
        }
    }

    /** Striped shop awning with a scalloped edge along the top. */
    private fun awning(c: Canvas, w: Float, h: Float, a: Int, b: Int, line: Int, n: Int = 8) {
        val depth = h * 0.09f
        val sw = w / n
        val sc = sw / 2
        for (i in 0 until n) {
            val p = Path().apply {
                moveTo(i * sw, 0f); lineTo((i + 1) * sw, 0f); lineTo((i + 1) * sw, depth)
                arcTo(RectF(i * sw, depth - sc, (i + 1) * sw, depth + sc), 0f, 180f)
                close()
            }
            c.drawPath(p, fill(if (i % 2 == 0) a else b))
            c.drawPath(p, A.pp(line, Paint.Style.STROKE, w * 0.004f))
        }
        c.drawRect(0f, depth - h * 0.004f, w, depth + sc, fill(0xFF000000.toInt()).apply {
            shader = LinearGradient(0f, depth, 0f, depth + sc * 1.6f, 0x22000000, 0x00000000, Shader.TileMode.CLAMP)
        })
    }

    private fun cloud(c: Canvas, x: Float, y: Float, s: Float, color: Int, shade: Int) {
        val p = Path().apply {
            addCircle(x - s * 0.3f, y, s * 0.24f, Path.Direction.CW)
            addCircle(x, y - s * 0.12f, s * 0.32f, Path.Direction.CW)
            addCircle(x + s * 0.32f, y - s * 0.02f, s * 0.26f, Path.Direction.CW)
            addRoundRect(RectF(x - s * 0.55f, y - s * 0.02f, x + s * 0.6f, y + s * 0.22f), s * 0.12f, s * 0.12f, Path.Direction.CW)
        }
        c.drawPath(Path(p).apply { offset(0f, s * 0.06f) }, fill(shade))
        c.drawPath(p, fill(color))
    }

    // ---------- 1. Cozy Christmas: tartan with string lights ----------

    val christmas = WallPainter { c, w, h ->
        c.drawRect(0f, 0f, w, h, fill(0xFF1F4732.toInt()))
        A.plaid(c, RectF(0f, 0f, w, h), w / 8f, 0x66B3262E, 0x40FFD27A, w / 400f)
        vignette(c, w, h, 0x66000000)
        val ink = colorInk(0xFF3E1416.toInt(), 0xFFF2C14E.toInt(), 0xFF2F6B45.toInt(), 0xFFFFF6E8.toInt())
        scatter(c, w, h, 61, listOf(A.GINGERBREAD, A.ORNAMENT, A.WREATH, A.COCOA), ink, cols = 3, minSize = 0.12f, maxSize = 0.16f, top = h * 0.06f, skip = 0.45f)
        val rnd = Random(5)
        val bulbs = intArrayOf(0xFFFF5A5F.toInt(), 0xFFFFD166.toInt(), 0xFF6EE7B7.toInt(), 0xFF7FC8FF.toInt(), 0xFFFF9BD2.toInt())
        val wire = A.pp(0xFF143022.toInt(), Paint.Style.STROKE, w * 0.005f)
        for (k in 0 until 4) {
            val y0 = h * (0.08f + k * 0.25f)
            val segs = 2
            val sw = w * 1.1f / segs
            for (s in 0 until segs) {
                val x0 = -w * 0.05f + s * sw; val x1 = x0 + sw
                val sag = h * 0.06f
                c.drawPath(Path().apply { moveTo(x0, y0); quadTo((x0 + x1) / 2, y0 + sag * 2, x1, y0) }, wire)
                for (i in 1 until 7) {
                    val t = i / 7f
                    val bx = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * ((x0 + x1) / 2) + t * t * x1
                    val by = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * (y0 + sag * 2) + t * t * y0
                    val col = bulbs[rnd.nextInt(bulbs.size)]
                    val gr = w * 0.06f
                    c.drawCircle(bx, by + w * 0.03f, gr, fill(0xFF000000.toInt()).apply { shader = RadialGradient(bx, by + w * 0.03f, gr, (col and 0x00FFFFFF) or 0x77000000, col and 0x00FFFFFF, Shader.TileMode.CLAMP) })
                    c.drawRect(bx - w * 0.008f, by - w * 0.004f, bx + w * 0.008f, by + w * 0.012f, fill(0xFF3A3A3A.toInt()))
                    c.drawOval(RectF(bx - w * 0.014f, by + w * 0.01f, bx + w * 0.014f, by + w * 0.05f), fill(col))
                    c.drawOval(RectF(bx - w * 0.008f, by + w * 0.016f, bx - w * 0.001f, by + w * 0.03f), fill(0x99FFFFFF.toInt()))
                }
            }
        }
    }

    // ---------- 2. Teddy bears: patchwork quilt ----------

    val teddy = WallPainter { c, w, h ->
        val cols = 5
        val cell = w / cols
        val rnd = Random(17)
        val colors = intArrayOf(0xFFF6E6D3.toInt(), 0xFFFAD4D8.toInt(), 0xFFEAD0B3.toInt(), 0xFFFFF4E6.toInt(), 0xFFF2C6C2.toInt())
        val stitch = A.pp(0x887A5238.toInt(), Paint.Style.STROKE, w * 0.003f).apply { pathEffect = DashPathEffect(floatArrayOf(w * 0.012f, w * 0.008f), 0f) }
        var y = 0f
        var row = 0
        while (y < h) {
            for (i in 0 until cols) {
                val x = i * cell
                val col = colors[(i * 3 + row * 2 + rnd.nextInt(2)) % colors.size]
                c.drawRect(x, y, x + cell, y + cell, fill(col))
                when ((i + row) % 3) {
                    0 -> { val dp = fill(0x33FFFFFF); var yy = y + cell * 0.12f; while (yy < y + cell) { var xx = x + cell * 0.12f; while (xx < x + cell) { c.drawCircle(xx, yy, cell * 0.035f, dp); xx += cell * 0.2f }; yy += cell * 0.2f } }
                    1 -> { val gp = fill(0x14000000); var xx = x; while (xx < x + cell) { c.drawRect(xx, y, xx + cell * 0.1f, y + cell, gp); xx += cell * 0.2f }; var yy = y; while (yy < y + cell) { c.drawRect(x, yy, x + cell, yy + cell * 0.1f, gp); yy += cell * 0.2f } }
                    else -> {}
                }
                c.drawRect(x + cell * 0.07f, y + cell * 0.07f, x + cell * 0.93f, y + cell * 0.93f, stitch)
            }
            y += cell; row++
        }
        val seam = A.pp(0x33000000, Paint.Style.STROKE, w * 0.003f)
        for (i in 1 until cols) c.drawLine(i * cell, 0f, i * cell, h, seam)
        y = cell
        while (y < h) { c.drawLine(0f, y, w, y, seam); y += cell }
        val ink = colorInk(0xFF7A5238.toInt(), 0xFFFF9FB5.toInt(), 0xFFE8C9A6.toInt(), 0xFFFFF4E6.toInt())
        scatter(c, w, h, 23, listOf(A.TEDDY, A.TEDDY_WAVE, A.TEDDY_HEART, A.HONEY_POT, A.STITCH_HEART, A.TEDDY_FACE), ink, cols = 3, minSize = 0.15f, maxSize = 0.2f, skip = 0.4f, tilt = 24f)
    }

    // ---------- 3. Cake: pastel bakery ----------

    val cake = WallPainter { c, w, h ->
        c.drawRect(0f, 0f, w, h, fill(0xFFFFF1F4.toInt()))
        val dots = intArrayOf(0x66A6E3D0, 0x66FFC2D1, 0x66FFE08A)
        val g = w / 9f
        var y = 0f
        var row = 0
        while (y < h + g) {
            var x = if (row % 2 == 0) 0f else g / 2
            var i = row
            while (x < w + g) { c.drawCircle(x, y, w * 0.012f, fill(dots[i % dots.size])); x += g; i++ }
            y += g * 0.866f; row++
        }
        awning(c, w, h, 0xFFFFB6CF.toInt(), 0xFFFFFFFF.toInt(), 0xFF8A4B5C.toInt())
        val ink = colorInk(0xFF8A4B5C.toInt(), 0xFFFF9EBD.toInt(), 0xFFA6E3D0.toInt(), 0xFFFFF8F0.toInt())
        scatter(c, w, h, 37, listOf(A.LAYER_CAKE, A.CUPCAKE, A.STRAWBERRY, A.CANDLE, A.CAKE_SLICE), ink, cols = 3, minSize = 0.13f, maxSize = 0.17f, top = h * 0.14f, skip = 0.35f, tilt = 24f)
    }

    // ---------- 4. Pink: soft gradient, bokeh, hearts and pearls ----------

    val pink = WallPainter { c, w, h ->
        vgrad(c, w, h, intArrayOf(0xFFFFE1EE.toInt(), 0xFFFFB9D4.toInt(), 0xFFFFD6E6.toInt()), floatArrayOf(0f, 0.55f, 1f))
        val rnd = Random(44)
        repeat(22) {
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
            val r = w * (0.03f + rnd.nextFloat() * 0.09f)
            c.drawCircle(x, y, r, fill(0xFF000000.toInt()).apply { shader = RadialGradient(x, y, r, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP) })
        }
        val ink = colorInk(0xFFC23B74.toInt(), 0xFFFF7FB0.toInt(), 0xFFFFC1D8.toInt(), 0xFFFFE3EE.toInt())
        scatter(c, w, h, 52, listOf(A.GLOSSY_HEART, A.RIBBON, A.PEARLS, A.PERFUME, A.TIARA, A.GLOSSY_HEART), ink, cols = 3, minSize = 0.12f, maxSize = 0.16f, skip = 0.4f, tilt = 30f)
        repeat(30) { art(c, A.SPARKLES, rnd.nextFloat() * w, rnd.nextFloat() * h, w * (0.03f + rnd.nextFloat() * 0.03f), ink) }
    }

    // ---------- 5. Squishy: pastel sky with puffy clouds and blob friends ----------

    val squishy = WallPainter { c, w, h ->
        vgrad(c, w, h, intArrayOf(0xFFDCD3FF.toInt(), 0xFFFFD9EC.toInt(), 0xFFFFE9D6.toInt()))
        val rnd = Random(73)
        repeat(40) {
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, w * (0.003f + rnd.nextFloat() * 0.004f), fill(0xAAFFFFFF.toInt()))
        }
        val ink = colorInk(0xFF6E5D80.toInt(), 0xFFFFC4DD.toInt(), 0xFFE6DDF7.toInt(), 0xFFFFFBF5.toInt())
        val blobs = listOf(A.BLOB_CAT, A.BLOB_FROG, A.BLOB_COW, A.BLOB_AXOLOTL, A.BLOB_BUNNY)
        val rows = 5
        for (i in 0 until rows) {
            val y = h * (0.14f + i * 0.19f)
            val left = i % 2 == 0
            val x = w * (if (left) 0.28f else 0.72f) + (rnd.nextFloat() - 0.5f) * w * 0.1f
            val s = w * (0.42f + rnd.nextFloat() * 0.08f)
            cloud(c, x, y, s, 0xFFFFFFFF.toInt(), 0x22806AA0)
            art(c, blobs[i % blobs.size], x, y - s * 0.3f, s * 0.42f, ink)
            cloud(c, w * (if (left) 0.82f else 0.18f), y + h * 0.06f, w * 0.22f, 0xCCFFFFFF.toInt(), 0x11806AA0)
        }
        repeat(10) { art(c, A.PUFF_STAR, rnd.nextFloat() * w, rnd.nextFloat() * h, w * (0.04f + rnd.nextFloat() * 0.03f), ink, rnd.nextFloat() * 40f - 20f) }
    }

    // ---------- 6. Café: awning, latte wall and a wooden counter ----------

    val cafe = WallPainter { c, w, h ->
        vgrad(c, w, h, intArrayOf(0xFFF6E7D4.toInt(), 0xFFE9CFB2.toInt()))
        val rnd = Random(81)
        val bean = fill(0x1F6B3E26)
        repeat(90) {
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
            val s = w * 0.012f
            c.save(); c.rotate(rnd.nextFloat() * 180f, x, y)
            c.drawOval(RectF(x - s, y - s * 1.4f, x + s, y + s * 1.4f), bean)
            c.restore()
        }
        awning(c, w, h, 0xFF8A5A3B.toInt(), 0xFFFFF4E6.toInt(), 0xFF3E2618.toInt(), n = 7)
        val ink = colorInk(0xFF3E2618.toInt(), 0xFFE8B98A.toInt(), 0xFFA06A3F.toInt(), 0xFFF6EFE3.toInt())
        val counter = h * 0.88f
        scatter(c, w, h, 83, listOf(A.COFFEE_CUP, A.LATTE_ART, A.CROISSANT, A.BEANS, A.TAKEAWAY), ink, cols = 3, minSize = 0.12f, maxSize = 0.16f, top = h * 0.13f, bottom = counter - w * 0.12f, skip = 0.35f, tilt = 24f)
        c.drawRect(0f, counter, w, h, fill(0xFF8A5A3B.toInt()))
        c.drawRect(0f, counter, w, counter + h * 0.012f, fill(0xFFA06A3F.toInt()))
        val grain = A.pp(0x33000000, Paint.Style.STROKE, w * 0.003f)
        for (i in 1..3) c.drawLine(0f, counter + i * (h - counter) / 4, w, counter + i * (h - counter) / 4 + h * 0.003f, grain)
        art(c, A.COFFEE_CUP, w * 0.24f, counter - w * 0.08f, w * 0.22f, ink)
        art(c, A.CROISSANT, w * 0.72f, counter - w * 0.05f, w * 0.2f, ink, -8f)
    }

    // ---------- 7. Sakura: a blossoming branch, lanterns and falling petals ----------

    val sakura = WallPainter { c, w, h ->
        vgrad(c, w, h, intArrayOf(0xFFFFF4F6.toInt(), 0xFFFFE3EA.toInt(), 0xFFFAD0DC.toInt()))
        c.drawCircle(w * 0.68f, h * 0.3f, w * 0.2f, fill(0x55FFFFFF))
        // Distant hills.
        fun hill(top: Float, amp: Float, color: Int, phase: Float) {
            val p = Path().apply {
                moveTo(0f, h); lineTo(0f, top)
                var x = 0f
                while (x <= w) { lineTo(x, top - amp * sin((x / w) * PI.toFloat() * 1.3f + phase)); x += w / 40f }
                lineTo(w, h); close()
            }
            c.drawPath(p, fill(color))
        }
        hill(h * 0.84f, h * 0.04f, 0xFFF3C1D0.toInt(), 0.8f)
        hill(h * 0.9f, h * 0.025f, 0xFFE9A9BE.toInt(), 2.4f)
        val ink = colorInk(0xFF5B3A3F.toInt(), 0xFFFF9DB8.toInt(), 0xFFFAD6E0.toInt(), 0xFFFFF8F6.toInt())
        // Lanterns on strings from the top.
        for ((fx, fy) in listOf(0.62f to 0.2f, 0.78f to 0.32f, 0.92f to 0.17f)) {
            c.drawLine(w * fx, 0f, w * fx, h * fy - w * 0.06f, A.pp(0xFF5B3A3F.toInt(), Paint.Style.STROKE, w * 0.003f))
            art(c, A.PAPER_LANTERN, w * fx, h * fy, w * 0.16f, ink)
        }
        // Branch from the top-left.
        val bark = A.pp(0xFF6B4436.toInt(), Paint.Style.STROKE, w * 0.03f)
        val branch = Path().apply { moveTo(-w * 0.05f, h * 0.06f); quadTo(w * 0.25f, h * 0.1f, w * 0.48f, h * 0.06f) }
        c.drawPath(branch, bark)
        c.drawPath(Path().apply { moveTo(w * 0.14f, h * 0.085f); quadTo(w * 0.2f, h * 0.16f, w * 0.34f, h * 0.2f) }, bark.apply { strokeWidth = w * 0.018f })
        c.drawPath(Path().apply { moveTo(w * 0.3f, h * 0.085f); quadTo(w * 0.36f, h * 0.03f, w * 0.44f, h * 0.0f) }, bark.apply { strokeWidth = w * 0.012f })
        val rnd = Random(91)
        for ((fx, fy) in listOf(0.04f to 0.07f, 0.12f to 0.1f, 0.22f to 0.08f, 0.2f to 0.15f, 0.3f to 0.19f, 0.36f to 0.08f, 0.45f to 0.05f, 0.4f to 0.02f, 0.27f to 0.12f)) {
            art(c, A.BLOSSOM, w * fx, h * fy, w * (0.09f + rnd.nextFloat() * 0.04f), ink, rnd.nextFloat() * 70f)
        }
        // Falling petals.
        repeat(55) {
            art(c, A.PETAL_ONE, rnd.nextFloat() * w, h * 0.12f + rnd.nextFloat() * h * 0.86f, w * (0.025f + rnd.nextFloat() * 0.03f), ink, rnd.nextFloat() * 360f)
        }
        repeat(4) { art(c, A.BLOSSOM, w * (0.1f + rnd.nextFloat() * 0.8f), h * (0.86f + rnd.nextFloat() * 0.1f), w * 0.06f, ink, rnd.nextFloat() * 70f) }
    }

    // ---------- 8. Kitty: paw prints and cats ----------

    val kitty = WallPainter { c, w, h ->
        vgrad(c, w, h, intArrayOf(0xFFFFF0E6.toInt(), 0xFFF3E4FF.toInt()))
        val paw = fill(0x22B07A9A)
        val step = w / 6f
        var y = step * 0.5f
        var row = 0
        while (y < h) {
            var x = step * (if (row % 2 == 0) 0.5f else 1f)
            while (x < w) {
                val s = w * 0.022f
                c.save(); c.rotate(if (row % 2 == 0) -20f else 20f, x, y)
                c.drawOval(RectF(x - s * 1.2f, y - s * 0.2f, x + s * 1.2f, y + s * 1.4f), paw)
                for ((dx, dy) in listOf(-1.5f to -0.5f, -0.55f to -1.4f, 0.55f to -1.4f, 1.5f to -0.5f)) c.drawCircle(x + dx * s, y + dy * s, s * 0.45f, paw)
                c.restore()
                x += step
            }
            y += step; row++
        }
        val ink = colorInk(0xFF5E4452.toInt(), 0xFFFFB0C8.toInt(), 0xFFFFD79A.toInt(), 0xFFFFF7EF.toInt())
        scatter(c, w, h, 101, listOf(A.CAT_FACE, A.CAT_SIT, A.YARN, A.FISH, A.PAW, A.CAT_FACE), ink, cols = 3, minSize = 0.13f, maxSize = 0.18f, skip = 0.4f, tilt = 26f)
    }

    // ---------- 9. Autumn: faded flannel, falling leaves, a mossy ground ----------

    val autumn = WallPainter { c, w, h ->
        c.drawRect(0f, 0f, w, h, fill(0xFFF6E3C8.toInt()))
        A.plaid(c, RectF(0f, 0f, w, h), w / 7f, 0x30E07A2F, 0x309C4A1F, w / 400f)
        val ink = colorInk(0xFF5A3018.toInt(), 0xFFE07A2F.toInt(), 0xFF9C4A1F.toInt(), 0xFFFFF5E6.toInt())
        val ground = h * 0.87f
        scatter(c, w, h, 113, listOf(A.MAPLE, A.MAPLE_RED, A.LEAF, A.MAPLE, A.ACORN), ink, cols = 4, minSize = 0.08f, maxSize = 0.12f, bottom = ground - w * 0.1f, skip = 0.3f, tilt = 80f)
        c.drawPath(Path().apply {
            moveTo(0f, h); lineTo(0f, ground)
            quadTo(w * 0.25f, ground - h * 0.025f, w * 0.5f, ground); quadTo(w * 0.75f, ground + h * 0.02f, w, ground - h * 0.01f)
            lineTo(w, h); close()
        }, fill(0xFF9C5A2E.toInt()))
        art(c, A.MUSHROOM, w * 0.14f, ground - w * 0.04f, w * 0.16f, ink, -6f)
        art(c, A.PUMPKIN, w * 0.42f, ground - w * 0.03f, w * 0.2f, ink)
        art(c, A.ACORN, w * 0.64f, ground - w * 0.01f, w * 0.1f, ink, 12f)
        art(c, A.MUSHROOM, w * 0.84f, ground - w * 0.03f, w * 0.13f, ink, 8f)
        val rnd = Random(7)
        repeat(14) { art(c, if (it % 2 == 0) A.MAPLE else A.LEAF, rnd.nextFloat() * w, ground + rnd.nextFloat() * (h - ground), w * 0.06f, ink, rnd.nextFloat() * 360f) }
    }

    // ---------- 10. Camping: starry night over the mountains ----------

    val camping = WallPainter { c, w, h ->
        vgrad(c, w, h, intArrayOf(0xFF0E1A3A.toInt(), 0xFF26386A.toInt(), 0xFF6B5B8E.toInt(), 0xFFE39A7A.toInt()), floatArrayOf(0f, 0.45f, 0.7f, 0.82f))
        val rnd = Random(131)
        val star = fill(0xFFFFFFFF.toInt())
        repeat(170) {
            star.alpha = 80 + rnd.nextInt(175)
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.6f, w * (0.0015f + rnd.nextFloat() * 0.004f), star)
        }
        val sparkle = colorInk(0xFF0E1A3A.toInt(), 0xFFFFD54F.toInt(), 0xFFFFD54F.toInt(), 0xFF0E1A3A.toInt())
        repeat(6) { art(c, SPARKLE4, rnd.nextFloat() * w, rnd.nextFloat() * h * 0.45f, w * (0.025f + rnd.nextFloat() * 0.02f), sparkle) }
        // Moon.
        c.drawCircle(w * 0.76f, h * 0.13f, w * 0.16f, fill(0xFF000000.toInt()).apply { shader = RadialGradient(w * 0.76f, h * 0.13f, w * 0.16f, 0x44FFF3C4, 0x00FFF3C4, Shader.TileMode.CLAMP) })
        c.drawCircle(w * 0.76f, h * 0.13f, w * 0.07f, fill(0xFFFFF3C4.toInt()))
        c.drawCircle(w * 0.79f, h * 0.12f, w * 0.06f, fill(0xFF14224A.toInt()))
        // Mountains.
        fun range(base: Float, peaks: List<Pair<Float, Float>>, color: Int, snow: Boolean) {
            val p = Path().apply {
                moveTo(0f, h); lineTo(0f, base)
                for ((px, py) in peaks) lineTo(w * px, h * py)
                lineTo(w, base); lineTo(w, h); close()
            }
            c.drawPath(p, fill(color))
            if (snow) for ((px, py) in peaks) {
                val s = w * 0.05f
                c.drawPath(Path().apply {
                    moveTo(w * px, h * py); lineTo(w * px + s, h * py + s * 1.1f); lineTo(w * px + s * 0.4f, h * py + s * 0.8f)
                    lineTo(w * px, h * py + s * 1.15f); lineTo(w * px - s * 0.4f, h * py + s * 0.8f); lineTo(w * px - s, h * py + s * 1.1f); close()
                }, fill(0xFFE9ECF8.toInt()))
            }
        }
        range(h * 0.66f, listOf(0.12f to 0.56f, 0.3f to 0.62f, 0.5f to 0.52f, 0.72f to 0.6f, 0.9f to 0.55f), 0xFF4A5285.toInt(), true)
        range(h * 0.74f, listOf(0.2f to 0.66f, 0.45f to 0.71f, 0.66f to 0.64f, 0.95f to 0.7f), 0xFF343A66.toInt(), false)
        // Pine silhouettes.
        val pine = fill(0xFF1C2A3E.toInt())
        var x = -w * 0.02f
        while (x < w * 1.05f) {
            val th = h * (0.07f + rnd.nextFloat() * 0.06f)
            val base = h * 0.8f
            val tw = th * 0.42f
            c.drawPath(Path().apply { moveTo(x, base - th); lineTo(x + tw, base); lineTo(x - tw, base); close() }, pine)
            x += w * (0.05f + rnd.nextFloat() * 0.05f)
        }
        c.drawRect(0f, h * 0.8f, w, h, fill(0xFF1C2A3E.toInt()))
        c.drawPath(Path().apply { moveTo(0f, h * 0.84f); quadTo(w * 0.5f, h * 0.81f, w, h * 0.85f); lineTo(w, h); lineTo(0f, h); close() }, fill(0xFF2B3A2E.toInt()))
        // Campfire glow, tent, fire.
        c.drawCircle(w * 0.66f, h * 0.88f, w * 0.35f, fill(0xFF000000.toInt()).apply { shader = RadialGradient(w * 0.66f, h * 0.88f, w * 0.35f, 0x66FF9A3D, 0x00FF9A3D, Shader.TileMode.CLAMP) })
        val ink = colorInk(0xFF2B1A10.toInt(), 0xFFF2994A.toInt(), 0xFFC98E52.toInt(), 0xFFE3B57D.toInt())
        art(c, A.TENT_ART, w * 0.3f, h * 0.855f, w * 0.34f, ink)
        art(c, A.CAMPFIRE, w * 0.66f, h * 0.865f, w * 0.2f, ink)
        art(c, A.MARSHMALLOW, w * 0.82f, h * 0.88f, w * 0.13f, ink, 10f)
    }

    private val SPARKLE4: StickerArt = {
        val k = 0.45f
        val p = path { m(0f, -k); q(k * 0.12f, -k * 0.12f, k, 0f); q(k * 0.12f, k * 0.12f, 0f, k); q(-k * 0.12f, k * 0.12f, -k, 0f); q(-k * 0.12f, -k * 0.12f, 0f, -k); close() }
        solid(p, 0xFFFFF3C4.toInt())
    }

    // ---------- 11. Rainbow: big rainbow, sunshine and clouds ----------

    val rainbow = WallPainter { c, w, h ->
        vgrad(c, w, h, intArrayOf(0xFF8ED8FF.toInt(), 0xFFC9ECFF.toInt(), 0xFFFFF1F7.toInt()))
        val cx = w * 0.5f; val cy = h * 0.66f
        val outer = w * 0.78f
        val band = w * 0.05f
        A.RAINBOW.forEachIndexed { i, col ->
            val r = outer - band * (i + 0.5f)
            c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f, false, A.pp(col, Paint.Style.STROKE, band + 1f).apply { strokeCap = Paint.Cap.BUTT })
        }
        cloud(c, cx - outer + band * 3f, cy, w * 0.42f, 0xFFFFFFFF.toInt(), 0x223F6FA0)
        cloud(c, cx + outer - band * 3f, cy, w * 0.42f, 0xFFFFFFFF.toInt(), 0x223F6FA0)
        val ink = colorInk(0xFF3F3A66.toInt(), 0xFFFF6B8B.toInt(), 0xFF7FD3FF.toInt(), 0xFFFFFFFF.toInt())
        art(c, A.SUNSHINE, w * 0.8f, h * 0.1f, w * 0.26f, ink)
        val rnd = Random(141)
        for ((fx, fy) in listOf(0.18f to 0.12f, 0.5f to 0.22f, 0.14f to 0.86f, 0.8f to 0.9f, 0.5f to 0.8f)) {
            cloud(c, w * fx, h * fy, w * (0.2f + rnd.nextFloat() * 0.08f), 0xEEFFFFFF.toInt(), 0x153F6FA0)
        }
        scatter(c, w, h, 149, listOf(A.RAINBOW_HEART, A.CLOUD, A.RAINBOW_ART, A.RAINBOW_HEART), ink, cols = 3, minSize = 0.1f, maxSize = 0.14f, top = h * 0.3f, bottom = h * 0.6f, skip = 0.45f, tilt = 24f)
        repeat(12) {
            art(c, SPARKLE4, rnd.nextFloat() * w, rnd.nextFloat() * h, w * (0.03f + rnd.nextFloat() * 0.03f), ink)
        }
    }
}
