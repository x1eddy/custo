package app.custo.feature.widgetsets

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Wallpaper painters for widget sets. All sizes are relative to the canvas so any resolution works. */
object ThemeWallpapers {

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { this.color = color }

    /**
     * Scatters stickers on a loose grid with jitter so they never pile up.
     * [cols] stickers per row; sizes are a fraction of the width.
     */
    fun scatter(
        c: Canvas, w: Float, h: Float, seed: Int, stickers: List<Sticker>, ink: StickerInk,
        cols: Int = 4, minSize: Float = 0.06f, maxSize: Float = 0.1f, top: Float = 0f, skipChance: Float = 0.15f,
    ) {
        val rnd = Random(seed)
        val cell = w / cols
        val rows = ((h - top) / cell).toInt() + 1
        for (r in 0 until rows) for (col in 0 until cols) {
            if (rnd.nextFloat() < skipChance) continue
            val offset = if (r % 2 == 0) 0f else cell / 2
            val x = (col * cell + offset + cell * (0.2f + rnd.nextFloat() * 0.6f)) % w
            val y = top + r * cell + cell * (0.2f + rnd.nextFloat() * 0.6f)
            val size = w * (minSize + rnd.nextFloat() * (maxSize - minSize))
            ThemeArt.drawSticker(c, stickers[rnd.nextInt(stickers.size)], x, y, size, ink, rnd.nextFloat() * 50f - 25f)
        }
    }

    /** Yellow checkerboard with a white icing band on top and cute stickers. */
    val goodLuck = WallPainter { c, w, h ->
        val a = 0xFFFFE57A.toInt(); val b = 0xFFFFF0A8.toInt()
        c.drawRect(0f, 0f, w, h, fill(a))
        val sq = w / 8f
        val pb = fill(b)
        var y = 0f; var row = 0
        while (y < h) {
            var x = if (row % 2 == 0) 0f else sq
            while (x < w) { c.drawRect(x, y, x + sq, y + sq, pb); x += sq * 2 }
            y += sq; row++
        }
        // Icing band.
        val base = h * 0.06f
        val rnd = Random(4)
        val icing = Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, base)
            val n = 9
            val seg = w / n
            var x = w
            repeat(n) { i ->
                val depth = h * (if (i % 2 == 0) 0.03f + rnd.nextFloat() * 0.03f else 0.008f)
                cubicTo(x, base + depth * 1.35f, x - seg, base + depth * 1.35f, x - seg, base)
                x -= seg
            }
            close()
        }
        c.drawPath(icing, fill(0xFFFFFFFF.toInt()))
        c.drawPath(icing, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = w * 0.006f; color = 0xFF6B4226.toInt() })
        val ink = StickerInk(InkStyle.COLOR, 0xFF6B4226.toInt(), 0xFFFF7EA8.toInt(), 0xFF8FD3FF.toInt(), a)
        scatter(
            c, w, h, 11,
            listOf(Sticker.STAR, Sticker.HEART, Sticker.CLOVER, Sticker.SPARKLE, Sticker.BOW, Sticker.CANDY, Sticker.FLOWER, Sticker.LOLLIPOP),
            ink, cols = 4, minSize = 0.07f, maxSize = 0.11f, top = h * 0.1f, skipChance = 0.35f,
        )
    }

    /** Snowy night: deep teal sky, warm bokeh, falling snow, hills with little trees. */
    val frostedNight = WallPainter { c, w, h ->
        val sky = fill(0).apply { shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF0B2A3C.toInt(), 0xFF145A63.toInt(), 0xFF3C8C8A.toInt()), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w, h, sky)
        val rnd = Random(21)
        // Bokeh lights.
        val warm = intArrayOf(0xFFFFD27A.toInt(), 0xFFFF8A80.toInt(), 0xFFFFF3C4.toInt())
        repeat(16) {
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h * 0.7f
            val r = w * (0.03f + rnd.nextFloat() * 0.07f)
            val col = warm[rnd.nextInt(warm.size)]
            c.drawCircle(x, y, r, fill(0).apply { shader = RadialGradient(x, y, r, (col and 0x00FFFFFF) or 0x55000000, col and 0x00FFFFFF, Shader.TileMode.CLAMP) })
        }
        // Moon.
        c.drawCircle(w * 0.8f, h * 0.12f, w * 0.06f, fill(0xFFFFF6DA.toInt()))
        c.drawCircle(w * 0.8f, h * 0.12f, w * 0.12f, fill(0).apply { shader = RadialGradient(w * 0.8f, h * 0.12f, w * 0.12f, 0x44FFF6DA, 0x00FFF6DA, Shader.TileMode.CLAMP) })
        // Hills.
        fun hill(top: Float, amp: Float, color: Int, phase: Float) {
            val p = Path().apply {
                moveTo(0f, h)
                var x = 0f
                lineTo(0f, top)
                while (x <= w) { lineTo(x, top - amp * sin((x / w) * PI.toFloat() * 1.6f + phase)); x += w / 40f }
                lineTo(w, h); close()
            }
            c.drawPath(p, fill(color))
        }
        hill(h * 0.8f, h * 0.035f, 0xFFD8ECF5.toInt(), 0.4f)
        val ink = StickerInk(InkStyle.COLOR, 0xFF1D3B4A.toInt(), 0xFFE53945.toInt(), 0xFFFFC94D.toInt(), 0xFFFFFFFF.toInt())
        for (i in 0 until 6) {
            val x = w * (0.08f + i * 0.17f + rnd.nextFloat() * 0.04f)
            ThemeArt.drawSticker(c, Sticker.TREE, x, h * (0.78f - 0.02f * sin(i.toFloat())), w * (0.1f + rnd.nextFloat() * 0.05f), ink)
        }
        hill(h * 0.86f, h * 0.025f, 0xFFF4FAFD.toInt(), 2.1f)
        ThemeArt.drawSticker(c, Sticker.SNOWMAN, w * 0.78f, h * 0.86f, w * 0.2f, ink)
        // Snow.
        val snow = fill(0xFFFFFFFF.toInt())
        repeat(220) {
            snow.alpha = 90 + rnd.nextInt(160)
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, w * (0.002f + rnd.nextFloat() * 0.006f), snow)
        }
        val flakes = StickerInk(InkStyle.LINE, 0xCCFFFFFF.toInt(), 0, 0, 0)
        repeat(9) { ThemeArt.drawSticker(c, Sticker.SNOWFLAKE, rnd.nextFloat() * w, rnd.nextFloat() * h * 0.7f, w * (0.03f + rnd.nextFloat() * 0.04f), flakes, rnd.nextFloat() * 60f) }
    }

    /** A colored board with a faint grid and white line-art doodles. */
    fun doodle(bg: Int, bgDark: Int, ink: Int, seed: Int) = WallPainter { c, w, h ->
        c.drawRect(0f, 0f, w, h, fill(bg))
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgDark; strokeWidth = w * 0.002f }
        val step = w / 14f
        var x = 0f
        while (x < w) { c.drawLine(x, 0f, x, h, grid); x += step }
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w, y, grid); y += step }
        // Little dots and dashes between doodles.
        val rnd = Random(seed)
        val dot = fill((ink and 0x00FFFFFF) or 0x88000000.toInt())
        repeat(70) { c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, w * 0.004f, dot) }
        val line = StickerInk(InkStyle.LINE, (ink and 0x00FFFFFF) or 0xDD000000.toInt(), bg, bg, bg)
        scatter(
            c, w, h, seed,
            listOf(Sticker.STAR, Sticker.SNOWFLAKE, Sticker.TREE, Sticker.GIFT, Sticker.BELL, Sticker.CANDY_CANE, Sticker.STOCKING, Sticker.HEART, Sticker.SPARKLE),
            line, cols = 4, minSize = 0.08f, maxSize = 0.12f, skipChance = 0.2f,
        )
        // A soft vignette keeps widgets readable.
        c.drawRect(0f, 0f, w, h, fill(0).apply {
            shader = RadialGradient(w / 2, h / 2, h * 0.7f, 0x00000000, 0x33000000, Shader.TileMode.CLAMP)
        })
    }
}
