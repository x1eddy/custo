package app.custo.feature.overlays

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The bundled stickers, drawn in code on a 100 x 100 grid and finished with a white die-cut
 * border and a soft shadow. Nothing is downloaded.
 */
object StickerArt {

    val names = listOf(
        "heart", "star", "sparkle", "sun", "moon", "cloud", "rainbow", "flower", "leaf", "cherry",
        "bolt", "fire", "note", "smiley", "cat", "paw", "crown", "diamond", "planet", "coffee",
        "cactus", "mushroom", "bow", "balloon", "ghost", "donut", "strawberry", "drop", "snowflake", "clover",
    )

    /** A square bitmap [px] wide with border and shadow. */
    fun render(name: String, px: Int): Bitmap {
        val size = px.coerceIn(16, 1024)
        val art = createBitmap(size, size)
        val inset = size * 0.08f
        val k = (size - 2 * inset) / 100f
        Canvas(art).apply { translate(inset, inset); scale(k, k) }.let { Painter(it).draw(name) }
        val out = createBitmap(size, size)
        val c = Canvas(out)
        val mask = art.extractAlpha()
        val border = size * 0.035f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x40000000
        c.drawBitmap(mask, 0f, border * 0.9f, p)
        p.color = 0xFFFFFFFF.toInt()
        for (i in 0 until 16) {
            val a = i * PI / 8
            c.drawBitmap(mask, (cos(a) * border).toFloat(), (sin(a) * border).toFloat(), p)
        }
        c.drawBitmap(art, 0f, 0f, null)
        mask.recycle(); art.recycle()
        return out
    }

    private class Painter(val c: Canvas) {
        val f = Paint(Paint.ANTI_ALIAS_FLAG)
        val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val r = RectF()

        fun col(v: Long) = v.toInt()
        fun circle(x: Float, y: Float, rad: Float, color: Long) { f.color = col(color); c.drawCircle(x, y, rad, f) }
        fun oval(l: Float, t: Float, rr: Float, b: Float, color: Long) { f.color = col(color); r.set(l, t, rr, b); c.drawOval(r, f) }
        fun rrect(l: Float, t: Float, rr: Float, b: Float, rad: Float, color: Long) { f.color = col(color); r.set(l, t, rr, b); c.drawRoundRect(r, rad, rad, f) }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Long) { s.color = col(color); s.strokeWidth = w; c.drawLine(x1, y1, x2, y2, s) }
        fun fill(p: Path, color: Long) { f.color = col(color); c.drawPath(p, f) }
        fun stroke(p: Path, w: Float, color: Long) { s.color = col(color); s.strokeWidth = w; c.drawPath(p, s) }
        fun poly(vararg pts: Float) = Path().apply {
            moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
            close()
        }
        fun star(cx: Float, cy: Float, outer: Float, inner: Float, points: Int, rot: Double = -PI / 2) = Path().apply {
            for (i in 0 until points * 2) {
                val rad = if (i % 2 == 0) outer else inner
                val a = rot + i * PI / points
                val x = cx + (cos(a) * rad).toFloat(); val y = cy + (sin(a) * rad).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        fun heart(cx: Float, cy: Float, w: Float) = Path().apply {
            val k = w / 80f
            moveTo(cx, cy + 34 * k)
            cubicTo(cx - 40 * k, cy + 6 * k, cx - 45 * k, cy - 19 * k, cx - 30 * k, cy - 32 * k)
            cubicTo(cx - 17 * k, cy - 44 * k, cx - 2 * k, cy - 36 * k, cx, cy - 24 * k)
            cubicTo(cx + 2 * k, cy - 36 * k, cx + 17 * k, cy - 44 * k, cx + 30 * k, cy - 32 * k)
            cubicTo(cx + 45 * k, cy - 19 * k, cx + 40 * k, cy + 6 * k, cx, cy + 34 * k)
            close()
        }

        fun draw(name: String) {
            when (name) {
                "heart" -> { fill(heart(50f, 52f, 86f), 0xFFFF4D6D); oval(26f, 28f, 38f, 40f, 0x80FFFFFF) }
                "star" -> { fill(star(50f, 54f, 46f, 20f, 5), 0xFFFFC93C); fill(star(50f, 54f, 22f, 10f, 5), 0x66FFFFFF) }
                "sparkle" -> {
                    fill(star(44f, 54f, 42f, 9f, 4), 0xFFA78BFA)
                    fill(star(80f, 20f, 16f, 4f, 4), 0xFFC4B5FD)
                }
                "sun" -> {
                    for (i in 0 until 8) c.withRotation(i * 45f, 50f, 50f) { rrect(45f, 2f, 55f, 22f, 5f, 0xFFFB8500) }
                    circle(50f, 50f, 26f, 0xFFFFB703)
                    circle(42f, 46f, 3f, 0xFF7A4100); circle(58f, 46f, 3f, 0xFF7A4100)
                    stroke(Path().apply { moveTo(41f, 57f); quadTo(50f, 65f, 59f, 57f) }, 3.5f, 0xFF7A4100)
                }
                "moon" -> {
                    val m = Path().apply { addCircle(50f, 52f, 40f, Path.Direction.CW) }
                    m.op(Path().apply { addCircle(68f, 38f, 34f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
                    fill(m, 0xFFFFE066)
                    fill(star(78f, 70f, 9f, 3f, 4), 0xFFFFE066)
                }
                "cloud" -> {
                    val p = Path().apply {
                        addCircle(32f, 60f, 18f, Path.Direction.CW); addCircle(54f, 48f, 25f, Path.Direction.CW)
                        addCircle(74f, 62f, 16f, Path.Direction.CW); addRoundRect(14f, 58f, 90f, 78f, 10f, 10f, Path.Direction.CW)
                    }
                    fill(p, 0xFFBDE0FE)
                    circle(46f, 44f, 8f, 0x66FFFFFF)
                }
                "rainbow" -> {
                    val cols = longArrayOf(0xFFFF595E, 0xFFFFCA3A, 0xFF8AC926, 0xFF1982C4)
                    cols.forEachIndexed { i, cc ->
                        val rad = 40f - i * 8f
                        r.set(50f - rad, 72f - rad, 50f + rad, 72f + rad)
                        s.color = col(cc); s.strokeWidth = 8.5f; s.strokeCap = Paint.Cap.BUTT
                        c.drawArc(r, 180f, 180f, false, s)
                    }
                    s.strokeCap = Paint.Cap.ROUND
                    circle(14f, 74f, 9f, 0xFFFFFFFF); circle(22f, 78f, 7f, 0xFFFFFFFF)
                    circle(86f, 74f, 9f, 0xFFFFFFFF); circle(78f, 78f, 7f, 0xFFFFFFFF)
                }
                "flower" -> {
                    for (i in 0 until 6) {
                        val a = i * PI / 3
                        circle(50f + (cos(a) * 24).toFloat(), 50f + (sin(a) * 24).toFloat(), 18f, 0xFFFF8FAB)
                    }
                    circle(50f, 50f, 15f, 0xFFFFD166)
                }
                "leaf" -> {
                    fill(Path().apply { moveTo(16f, 84f); cubicTo(14f, 36f, 52f, 12f, 88f, 12f); cubicTo(90f, 50f, 66f, 88f, 16f, 84f); close() }, 0xFF52B788)
                    stroke(Path().apply { moveTo(10f, 92f); quadTo(46f, 56f, 78f, 22f) }, 3.5f, 0xFF2D6A4F)
                }
                "cherry" -> {
                    stroke(Path().apply { moveTo(34f, 66f); quadTo(40f, 34f, 60f, 16f) }, 4f, 0xFF2D6A4F)
                    stroke(Path().apply { moveTo(68f, 70f); quadTo(66f, 40f, 60f, 16f) }, 4f, 0xFF2D6A4F)
                    fill(Path().apply { moveTo(60f, 16f); quadTo(76f, 4f, 90f, 14f); quadTo(74f, 26f, 60f, 16f); close() }, 0xFF52B788)
                    circle(32f, 72f, 17f, 0xFFE63946); circle(68f, 76f, 17f, 0xFFD62839)
                    circle(26f, 66f, 4f, 0x80FFFFFF); circle(62f, 70f, 4f, 0x80FFFFFF)
                }
                "bolt" -> fill(poly(60f, 4f, 18f, 56f, 46f, 56f, 36f, 96f, 82f, 40f, 54f, 40f, 66f, 4f), 0xFFFFD60A)
                "fire" -> {
                    fill(Path().apply { moveTo(50f, 4f); cubicTo(70f, 28f, 86f, 44f, 84f, 66f); cubicTo(82f, 86f, 66f, 96f, 50f, 96f); cubicTo(32f, 96f, 16f, 84f, 16f, 64f); cubicTo(16f, 46f, 30f, 38f, 34f, 22f); cubicTo(42f, 32f, 44f, 38f, 46f, 44f); cubicTo(52f, 30f, 52f, 18f, 50f, 4f); close() }, 0xFFFF6B35)
                    fill(Path().apply { moveTo(50f, 46f); cubicTo(62f, 60f, 68f, 70f, 66f, 80f); cubicTo(64f, 90f, 56f, 94f, 50f, 94f); cubicTo(40f, 94f, 32f, 88f, 34f, 76f); cubicTo(36f, 66f, 46f, 60f, 50f, 46f); close() }, 0xFFFFD166)
                }
                "note" -> {
                    fill(poly(36f, 22f, 82f, 10f, 82f, 24f, 36f, 36f), 0xFF7B2CBF)
                    rrect(36f, 22f, 42f, 76f, 2f, 0xFF7B2CBF); rrect(76f, 10f, 82f, 66f, 2f, 0xFF7B2CBF)
                    c.withRotation(-20f, 28f, 76f) { oval(14f, 66f, 42f, 86f, 0xFF7B2CBF) }
                    c.withRotation(-20f, 68f, 66f) { oval(54f, 56f, 82f, 76f, 0xFF7B2CBF) }
                }
                "smiley" -> {
                    circle(50f, 50f, 44f, 0xFFFFD93D)
                    oval(33f, 30f, 42f, 46f, 0xFF3D2C00); oval(58f, 30f, 67f, 46f, 0xFF3D2C00)
                    stroke(Path().apply { moveTo(28f, 58f); quadTo(50f, 82f, 72f, 58f) }, 5f, 0xFF3D2C00)
                    circle(26f, 56f, 6f, 0x55FF6B6B); circle(74f, 56f, 6f, 0x55FF6B6B)
                }
                "cat" -> {
                    fill(poly(16f, 46f, 22f, 8f, 44f, 30f), 0xFFF4A261); fill(poly(84f, 46f, 78f, 8f, 56f, 30f), 0xFFF4A261)
                    fill(poly(22f, 36f, 25f, 18f, 36f, 30f), 0xFFFFB4A2); fill(poly(78f, 36f, 75f, 18f, 64f, 30f), 0xFFFFB4A2)
                    oval(10f, 24f, 90f, 90f, 0xFFF4A261)
                    oval(30f, 46f, 40f, 60f, 0xFF2B2D42); oval(60f, 46f, 70f, 60f, 0xFF2B2D42)
                    fill(poly(46f, 64f, 54f, 64f, 50f, 70f), 0xFFFF8FAB)
                    line(6f, 66f, 30f, 68f, 2f, 0xFF6B4226); line(70f, 68f, 94f, 66f, 2f, 0xFF6B4226)
                    line(8f, 76f, 30f, 72f, 2f, 0xFF6B4226); line(70f, 72f, 92f, 76f, 2f, 0xFF6B4226)
                }
                "paw" -> {
                    oval(26f, 46f, 74f, 88f, 0xFFFF8FAB)
                    oval(10f, 34f, 28f, 56f, 0xFFFF8FAB); oval(28f, 12f, 46f, 36f, 0xFFFF8FAB)
                    oval(54f, 12f, 72f, 36f, 0xFFFF8FAB); oval(72f, 34f, 90f, 56f, 0xFFFF8FAB)
                }
                "crown" -> {
                    fill(poly(10f, 76f, 14f, 26f, 34f, 50f, 50f, 18f, 66f, 50f, 86f, 26f, 90f, 76f), 0xFFFFC300)
                    rrect(10f, 72f, 90f, 86f, 3f, 0xFFF59E0B)
                    circle(14f, 24f, 6f, 0xFFFFC300); circle(50f, 16f, 6f, 0xFFFFC300); circle(86f, 24f, 6f, 0xFFFFC300)
                    circle(30f, 64f, 5f, 0xFFEF476F); circle(50f, 60f, 6f, 0xFF4CC9F0); circle(70f, 64f, 5f, 0xFF06D6A0)
                }
                "diamond" -> {
                    fill(poly(26f, 18f, 74f, 18f, 94f, 40f, 50f, 92f, 6f, 40f), 0xFF4CC9F0)
                    fill(poly(26f, 18f, 74f, 18f, 64f, 40f, 36f, 40f), 0xFF90E0EF)
                    fill(poly(36f, 40f, 64f, 40f, 50f, 92f), 0xFF3AB0DD)
                }
                "planet" -> {
                    circle(50f, 50f, 27f, 0xFF9D4EDD)
                    c.withRotation(-20f, 50f, 50f) {
                        r.set(6f, 38f, 94f, 62f); s.color = col(0xFFFFB4A2); s.strokeWidth = 6f; drawArc(r, 0f, 180f, false, s)
                    }
                    oval(36f, 34f, 48f, 42f, 0x55FFFFFF)
                    circle(14f, 18f, 3f, 0xFFFFD166); circle(86f, 82f, 4f, 0xFFFFD166)
                }
                "coffee" -> {
                    r.set(62f, 46f, 90f, 72f); s.color = col(0xFFF1FAEE); s.strokeWidth = 7f; c.drawOval(r, s)
                    fill(Path().apply { moveTo(16f, 40f); lineTo(76f, 40f); lineTo(70f, 84f); quadTo(46f, 92f, 22f, 84f); close() }, 0xFFF1FAEE)
                    oval(16f, 34f, 76f, 46f, 0xFF6F4E37)
                    stroke(Path().apply { moveTo(36f, 26f); quadTo(30f, 18f, 36f, 10f) }, 3.5f, 0xFFADB5BD)
                    stroke(Path().apply { moveTo(52f, 26f); quadTo(46f, 18f, 52f, 10f) }, 3.5f, 0xFFADB5BD)
                    fill(heart(46f, 64f, 20f), 0xFFFF8FAB)
                }
                "cactus" -> {
                    rrect(40f, 12f, 60f, 76f, 10f, 0xFF2A9D8F)
                    rrect(20f, 32f, 32f, 56f, 6f, 0xFF2A9D8F); rrect(20f, 50f, 44f, 60f, 5f, 0xFF2A9D8F)
                    rrect(68f, 24f, 80f, 48f, 6f, 0xFF2A9D8F); rrect(56f, 42f, 80f, 52f, 5f, 0xFF2A9D8F)
                    fill(poly(28f, 72f, 72f, 72f, 66f, 96f, 34f, 96f), 0xFFE76F51)
                    circle(50f, 12f, 5f, 0xFFFF8FAB)
                }
                "mushroom" -> {
                    rrect(36f, 50f, 64f, 92f, 10f, 0xFFFFF1E6)
                    fill(Path().apply { moveTo(6f, 56f); cubicTo(6f, 10f, 94f, 10f, 94f, 56f); close() }, 0xFFE63946)
                    circle(30f, 36f, 7f, 0xFFFFFFFF); circle(56f, 26f, 6f, 0xFFFFFFFF); circle(72f, 44f, 5f, 0xFFFFFFFF)
                }
                "bow" -> {
                    fill(Path().apply { moveTo(50f, 50f); cubicTo(30f, 20f, 4f, 24f, 8f, 50f); cubicTo(4f, 76f, 30f, 80f, 50f, 50f); close() }, 0xFFFF5D8F)
                    fill(Path().apply { moveTo(50f, 50f); cubicTo(70f, 20f, 96f, 24f, 92f, 50f); cubicTo(96f, 76f, 70f, 80f, 50f, 50f); close() }, 0xFFFF5D8F)
                    fill(poly(44f, 54f, 30f, 94f, 42f, 88f, 48f, 96f, 50f, 56f), 0xFFE04478)
                    fill(poly(56f, 54f, 70f, 94f, 58f, 88f, 52f, 96f, 50f, 56f), 0xFFE04478)
                    oval(40f, 40f, 60f, 60f, 0xFFE04478)
                }
                "balloon" -> {
                    stroke(Path().apply { moveTo(50f, 78f); quadTo(40f, 88f, 52f, 98f) }, 2f, 0xFF8D99AE)
                    oval(22f, 4f, 78f, 72f, 0xFFEF476F)
                    fill(poly(50f, 70f, 44f, 80f, 56f, 80f), 0xFFD64061)
                    oval(32f, 16f, 44f, 32f, 0x66FFFFFF)
                }
                "ghost" -> {
                    fill(Path().apply {
                        moveTo(14f, 92f); lineTo(14f, 44f); cubicTo(14f, 4f, 86f, 4f, 86f, 44f); lineTo(86f, 92f)
                        quadTo(80f, 82f, 74f, 92f); quadTo(68f, 82f, 62f, 92f); quadTo(56f, 82f, 50f, 92f)
                        quadTo(44f, 82f, 38f, 92f); quadTo(32f, 82f, 26f, 92f); quadTo(20f, 82f, 14f, 92f); close()
                    }, 0xFFF1F3F5)
                    oval(32f, 34f, 44f, 50f, 0xFF2B2D42); oval(56f, 34f, 68f, 50f, 0xFF2B2D42)
                    oval(44f, 56f, 56f, 66f, 0xFF2B2D42)
                }
                "donut" -> {
                    val ring = Path().apply { fillType = Path.FillType.EVEN_ODD; addCircle(50f, 50f, 44f, Path.Direction.CW); addCircle(50f, 50f, 13f, Path.Direction.CW) }
                    fill(ring, 0xFFE9C46A)
                    val icing = Path().apply { fillType = Path.FillType.EVEN_ODD; addCircle(50f, 48f, 37f, Path.Direction.CW); addCircle(50f, 50f, 15f, Path.Direction.CW) }
                    fill(icing, 0xFFFF70A6)
                    val sprinkles = listOf(30f to 30f, 66f to 26f, 74f to 52f, 28f to 60f, 50f to 22f, 60f to 72f, 38f to 76f, 22f to 44f, 78f to 38f)
                    val cs = longArrayOf(0xFFFFFFFF, 0xFF4CC9F0, 0xFFFFD166, 0xFF06D6A0)
                    sprinkles.forEachIndexed { i, (x, y) -> c.withRotation(i * 47f, x, y) { rrect(x - 4f, y - 1.5f, x + 4f, y + 1.5f, 1.5f, cs[i % cs.size]) } }
                }
                "strawberry" -> {
                    fill(Path().apply { moveTo(50f, 96f); cubicTo(18f, 76f, 8f, 42f, 22f, 30f); cubicTo(34f, 20f, 66f, 20f, 78f, 30f); cubicTo(92f, 42f, 82f, 76f, 50f, 96f); close() }, 0xFFE63946)
                    fill(star(50f, 26f, 22f, 8f, 5, PI / 2), 0xFF40916C)
                    val seeds = listOf(34f to 44f, 50f to 42f, 66f to 44f, 28f to 58f, 42f to 58f, 58f to 58f, 72f to 58f, 38f to 72f, 52f to 74f, 64f to 72f, 50f to 86f)
                    seeds.forEach { (x, y) -> oval(x - 1.8f, y - 2.8f, x + 1.8f, y + 2.8f, 0xFFFFE066) }
                }
                "drop" -> {
                    fill(Path().apply { moveTo(50f, 4f); cubicTo(64f, 30f, 84f, 48f, 84f, 66f); cubicTo(84f, 86f, 68f, 96f, 50f, 96f); cubicTo(32f, 96f, 16f, 86f, 16f, 66f); cubicTo(16f, 48f, 36f, 30f, 50f, 4f); close() }, 0xFF4CC9F0)
                    oval(28f, 58f, 40f, 78f, 0x66FFFFFF)
                }
                "snowflake" -> {
                    for (i in 0 until 6) c.withRotation(i * 60f, 50f, 50f) {
                        line(50f, 50f, 50f, 8f, 6f, 0xFF90E0EF)
                        line(50f, 24f, 40f, 15f, 5f, 0xFF90E0EF); line(50f, 24f, 60f, 15f, 5f, 0xFF90E0EF)
                    }
                    circle(50f, 50f, 7f, 0xFFCAF0F8)
                }
                "clover" -> {
                    stroke(Path().apply { moveTo(50f, 52f); quadTo(56f, 78f, 70f, 94f) }, 5f, 0xFF2D6A4F)
                    for (i in 0 until 4) c.withRotation(i * 90f + 45f, 50f, 50f) { fill(heart(50f, 30f, 38f), 0xFF40916C) }
                    circle(50f, 50f, 5f, 0xFF2D6A4F)
                }
            }
        }
    }
}
