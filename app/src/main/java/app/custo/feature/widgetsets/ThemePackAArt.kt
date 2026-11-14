package app.custo.feature.widgetsets

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Small drawing helpers shared by pack A's art and wallpapers. */
internal object PackAKit {
    fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        this.color = color; this.style = style; strokeWidth = width
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    fun alpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    /** [color] darkened (f < 1) or lightened toward white (f > 1). */
    fun shade(color: Int, f: Float): Int {
        val r = (color shr 16) and 0xFF; val g = (color shr 8) and 0xFF; val b = color and 0xFF
        fun ch(v: Int) = if (f <= 1f) (v * f).toInt() else (v + (255 - v) * (f - 1f)).toInt().coerceAtMost(255)
        return (color.toLong() and 0xFF000000L).toInt() or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    fun typeface(mono: Boolean, bold: Boolean): Typeface =
        Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)

    /** A sticker drawn straight onto a wallpaper. */
    fun sticker(c: Canvas, art: StickerArt, x: Float, y: Float, size: Float, line: Int, rotate: Float = 0f) =
        ThemeArt.drawSticker(c, Sticker.STAR, x, y, size, StickerInk(InkStyle.COLOR, line, line, line, line), rotate, art)

    /** Rectangle with [cut] chamfers on the corners flagged in [corners] (tl, tr, br, bl). */
    fun chamfer(r: RectF, cut: Float, tl: Boolean, tr: Boolean, br: Boolean, bl: Boolean) = Path().apply {
        moveTo(r.left + if (tl) cut else 0f, r.top)
        lineTo(r.right - if (tr) cut else 0f, r.top)
        if (tr) lineTo(r.right, r.top + cut)
        lineTo(r.right, r.bottom - if (br) cut else 0f)
        if (br) lineTo(r.right - cut, r.bottom)
        lineTo(r.left + if (bl) cut else 0f, r.bottom)
        if (bl) lineTo(r.left, r.bottom - cut)
        lineTo(r.left, r.top + if (tl) cut else 0f)
        close()
    }

    /** Stroke drawn three times, wide and faint to thin and solid, for a neon glow. */
    fun glow(c: Canvas, p: Path, color: Int, width: Float) {
        c.drawPath(p, paint(alpha(color, 0.16f), Paint.Style.STROKE, width * 4f))
        c.drawPath(p, paint(alpha(color, 0.35f), Paint.Style.STROKE, width * 2.2f))
        c.drawPath(p, paint(color, Paint.Style.STROKE, width))
    }
}

/** A tiny pixel canvas; empty cells next to filled ones become the outline. */
private class PackAPixGrid(val w: Int, val h: Int) {
    val cells = IntArray(w * h)
    operator fun set(i: Int, j: Int, col: Int) { if (i in 0 until w && j in 0 until h) cells[j * w + i] = col }
    operator fun get(i: Int, j: Int): Int = if (i in 0 until w && j in 0 until h) cells[j * w + i] else 0
    fun rect(i0: Int, j0: Int, i1: Int, j1: Int, col: Int) { for (j in j0..j1) for (i in i0..i1) this[i, j] = col }
    fun line(i0: Int, j0: Int, i1: Int, j1: Int, col: Int) {
        val n = max(abs(i1 - i0), abs(j1 - j0))
        for (k in 0..n) {
            val t = if (n == 0) 0f else k.toFloat() / n
            this[(i0 + (i1 - i0) * t).roundToInt(), (j0 + (j1 - j0) * t).roundToInt()] = col
        }
    }
    fun put(col: Int, vararg ij: Int) { var k = 0; while (k < ij.size) { this[ij[k], ij[k + 1]] = col; k += 2 } }
    fun outline(col: Int) {
        val src = cells.copyOf()
        fun filled(i: Int, j: Int) = i in 0 until w && j in 0 until h && src[j * w + i] != 0
        for (j in 0 until h) for (i in 0 until w) {
            if (src[j * w + i] == 0 && (filled(i - 1, j) || filled(i + 1, j) || filled(i, j - 1) || filled(i, j + 1))) cells[j * w + i] = col
        }
    }
}

private const val PACK_A_PIX_OUT = 0xFF1C1A1E.toInt()

private fun Pen.paPixels(w: Int, h: Int, outline: Int = PACK_A_PIX_OUT, block: PackAPixGrid.() -> Unit) {
    val g = PackAPixGrid(w, h).apply(block)
    g.outline(outline)
    val px = 0.98f / max(w, h)
    val ox = -w * px / 2; val oy = -h * px / 2
    val e = px * 0.06f
    for (j in 0 until h) for (i in 0 until w) {
        val col = g[i, j]
        if (col != 0) solid(rectP(ox + i * px - e, oy + j * px - e, ox + (i + 1) * px + e, oy + (j + 1) * px + e), col)
    }
}

/** Text centered on ([u], [v]); [size] is the text size in sticker units. */
private fun Pen.paLabel(t: String, u: Float, v: Float, size: Float, color: Int, mono: Boolean = false, edge: Int? = null, edgeW: Float = 0.16f) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = r(size); typeface = PackAKit.typeface(mono, true); textAlign = Paint.Align.CENTER; this.color = color
    }
    val fm = p.fontMetrics
    val by = y(v) - (fm.ascent + fm.descent) / 2
    if (edge != null) c.drawText(t, x(u), by, Paint(p).apply { style = Paint.Style.STROKE; strokeWidth = r(size) * edgeW; this.color = edge; strokeJoin = Paint.Join.ROUND })
    c.drawText(t, x(u), by, p)
}

/** Vertical gradient fill from [top] to [bottom], then the ink outline. */
private fun Pen.paGfill(p: Path, top: Int, bottom: Int, v0: Float = -0.5f, v1: Float = 0.5f, outline: Boolean = true) {
    if (colorful) c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, y(v0), 0f, y(v1), top, bottom, Shader.TileMode.CLAMP) })
    if (outline || !colorful) stroke(p)
}

private fun Pen.paNeon(p: Path, color: Int, width: Float = lineW) = PackAKit.glow(c, p, color, width)

/** Thick colored stroke with an ink edge around it. */
private fun Pen.paTube(p: Path, width: Float, color: Int) {
    stroke(p, width + lineW * 1.6f)
    stroke(p, width, if (colorful) color else ink.paper)
}

private fun Pen.paWedge(u: Float, v: Float, rr: Float, start: Float, sweep: Float) = Path().apply {
    moveTo(x(u), y(v)); arcTo(RectF(x(u - rr), y(v - rr), x(u + rr), y(v + rr)), start, sweep); close()
}

/** A leaf from ([x0],[y0]) to the tip, [wd] wide, bent to one side. */
private fun Pen.paLeaf(x0: Float, y0: Float, x1: Float, y1: Float, wd: Float, col: Int, sag: Float = 0.06f) {
    val mx = (x0 + x1) / 2; val my = (y0 + y1) / 2 + sag
    val dx = x1 - x0; val dy = y1 - y0
    val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
    val nx = -dy / len; val ny = dx / len
    shape(path { m(x0, y0); q(mx + nx * wd, my + ny * wd, x1, y1); q(mx - nx * wd, my - ny * wd, x0, y0); close() }, col)
}

/**
 * Pack A stickers. Every sticker is a [StickerArt] drawn in unit coordinates (center 0,0, edges ±0.5).
 * All art is original: generic shapes that only borrow a genre's colors and era.
 */
internal object PackAArt {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF15151A.toInt()
    private const val GOLD = 0xFFFFC93C.toInt()
    private const val GOLD_D = 0xFFD99A1E.toInt()
    private const val GOLD_L = 0xFFFFE89A.toInt()
    private const val RED = 0xFFE8312F.toInt()

    // ---------- Blocky (pixel craft) ----------

    private const val WOOD = 0xFF9C6A3A.toInt()
    private const val WOOD_D = 0xFF6E4625.toInt()
    private const val GEM_L = 0xFFA6F5EF.toInt()
    private const val GEM = 0xFF3CCFC6.toInt()
    private const val GEM_D = 0xFF1F8F8A.toInt()
    private const val GRASS = 0xFF62B33F.toInt()
    private const val GRASS_L = 0xFF83CF56.toInt()
    private const val GRASS_D = 0xFF3F8A2A.toInt()
    private const val DIRT = 0xFF8A603F.toInt()
    private const val DIRT_D = 0xFF6A4630.toInt()
    private const val DIRT_L = 0xFFA57A55.toInt()

    val pickaxe: StickerArt = {
        paPixels(14, 14) {
            line(2, 11, 9, 4, WOOD)
            put(WOOD_D, 3, 10, 5, 8, 7, 6)
            put(GEM_L, 4, 2, 5, 1, 6, 1, 7, 1, 8, 1, 9, 2, 10, 3, 11, 4, 12, 5, 12, 6, 12, 7, 12, 8, 11, 9)
            put(GEM_D, 6, 2, 7, 2, 8, 2, 9, 3, 10, 4, 11, 5, 11, 6, 11, 7)
        }
    }

    val sword: StickerArt = {
        paPixels(14, 14) {
            line(12, 1, 6, 7, GEM_L)
            line(11, 1, 5, 7, GEM)
            this[12, 1] = WHITE
            line(3, 6, 7, 10, GEM_D)
            line(4, 9, 2, 11, WOOD)
            line(5, 9, 3, 11, WOOD_D)
            put(GEM_D, 1, 12, 2, 12)
        }
    }

    val slime: StickerArt = {
        val g = 0xFF8BE07A.toInt(); val d = 0xFF5DBB4C.toInt(); val e = 0xFF1F3B1A.toInt(); val w = 0xFFE4FFD9.toInt()
        paPixels(10, 10) {
            rect(1, 1, 8, 8, g)
            put(w, 2, 2, 3, 2, 2, 3)
            rect(2, 4, 3, 5, e); rect(6, 4, 7, 5, e)
            put(e, 4, 7, 5, 7)
            put(d, 8, 3, 8, 6, 7, 7, 6, 7)
            rect(1, 8, 8, 8, d)
        }
    }

    val pixelTree: StickerArt = {
        paPixels(12, 12) {
            rect(2, 1, 9, 6, GRASS); rect(1, 2, 10, 5, GRASS)
            put(GRASS_D, 3, 2, 7, 1, 8, 4, 2, 4, 5, 5, 6, 3, 9, 3, 4, 6)
            put(GRASS_L, 4, 1, 3, 3, 6, 2, 8, 2, 1, 2, 5, 4)
            rect(5, 7, 6, 10, WOOD)
            put(WOOD_D, 5, 8, 6, 10)
        }
    }

    val pixelSun: StickerArt = {
        paPixels(10, 10) {
            rect(1, 1, 8, 8, 0xFFFFB627.toInt())
            rect(2, 2, 7, 7, 0xFFFFE15A.toInt())
            put(0xFFFFF7C2.toInt(), 3, 3, 4, 3, 3, 4)
        }
    }

    val gem: StickerArt = {
        paPixels(12, 11) {
            val rows = arrayOf(2 to (3..8), 3 to (2..9), 4 to (1..10), 5 to (2..9), 6 to (3..8), 7 to (4..7), 8 to (5..6))
            for ((j, range) in rows) for (i in range) this[i, j] = when {
                j <= 3 -> if (i < 6) GEM_L else GEM
                i >= 6 -> GEM_D
                else -> GEM
            }
            put(WHITE, 3, 3, 4, 3, 2, 4)
        }
    }

    val torch: StickerArt = {
        paPixels(10, 13) {
            rect(4, 6, 5, 11, WOOD)
            put(WOOD_D, 5, 7, 4, 9, 5, 11)
            rect(4, 1, 5, 1, 0xFFFF7A1A.toInt())
            rect(3, 2, 6, 5, 0xFFFF7A1A.toInt())
            rect(4, 2, 5, 5, 0xFFFFC23A.toInt())
            put(0xFFFFF6B8.toInt(), 4, 4, 5, 4, 4, 5, 5, 5)
        }
    }

    val grassBlock: StickerArt = {
        paPixels(10, 10) {
            rect(1, 1, 8, 8, DIRT)
            rect(1, 1, 8, 2, GRASS)
            put(GRASS, 1, 3, 3, 3, 4, 3, 7, 3, 4, 4)
            put(GRASS_L, 2, 1, 5, 2, 7, 1)
            put(GRASS_D, 3, 2, 8, 2, 1, 3)
            put(DIRT_D, 2, 5, 5, 4, 7, 6, 3, 7, 6, 8, 8, 4)
            put(DIRT_L, 4, 6, 8, 5, 1, 7, 6, 5, 2, 8)
        }
    }

    // ---------- Matrix code (terminal) ----------

    private const val CODE = 0xFF39FF6A.toInt()
    private const val CODE_DIM = 0xFF168A3A.toInt()
    private const val TERM = 0xFF04140A.toInt()
    private const val GLITCH_M = 0xFFFF2BD6.toInt()
    private const val GLITCH_C = 0xFF2BE7FF.toInt()

    val terminal: StickerArt = {
        val body = rectP(-0.46f, -0.36f, 0.46f, 0.36f, 0.06f)
        shape(body, TERM)
        c.withSave { clipPath(body); tint(rectP(-0.5f, -0.4f, 0.5f, -0.2f), 0xFF0E3A1C.toInt()) }
        stroke(body)
        for (i in 0 until 3) solid(circleP(-0.36f + i * 0.08f, -0.28f, 0.028f), if (i == 0) CODE else CODE_DIM)
        paLabel(">_", -0.12f, 0.06f, 0.32f, CODE, mono = true)
    }

    val binary: StickerArt = {
        shape(rectP(-0.42f, -0.44f, 0.42f, 0.44f, 0.08f), TERM)
        paLabel("101", 0f, -0.22f, 0.22f, CODE, mono = true)
        paLabel("010", 0f, 0.0f, 0.22f, CODE_DIM, mono = true)
        paLabel("110", 0f, 0.22f, 0.22f, CODE, mono = true)
    }

    val lock: StickerArt = {
        val shackle = path { m(-0.19f, -0.02f); l(-0.19f, -0.2f); q(-0.19f, -0.44f, 0f, -0.44f); q(0.19f, -0.44f, 0.19f, -0.2f); l(0.19f, -0.02f) }
        stroke(shackle, lineW * 2.2f, CODE)
        shape(rectP(-0.32f, -0.06f, 0.32f, 0.45f, 0.06f), TERM)
        solid(circleP(0f, 0.13f, 0.065f), CODE)
        solid(rectP(-0.028f, 0.14f, 0.028f, 0.32f, 0.02f), CODE)
    }

    val glitch: StickerArt = {
        val bars = arrayOf(
            floatArrayOf(-0.4f, -0.42f, 0.16f, -0.32f), floatArrayOf(-0.12f, -0.3f, 0.44f, -0.2f),
            floatArrayOf(-0.44f, -0.18f, 0.2f, -0.04f), floatArrayOf(-0.08f, -0.02f, 0.38f, 0.06f),
            floatArrayOf(-0.3f, 0.08f, 0.12f, 0.2f), floatArrayOf(0.0f, 0.22f, 0.46f, 0.3f),
            floatArrayOf(-0.38f, 0.32f, -0.04f, 0.42f),
        )
        val cols = intArrayOf(CODE, GLITCH_M, CODE, GLITCH_C, CODE, GLITCH_M, GLITCH_C)
        bars.forEachIndexed { i, b ->
            solid(rectP(b[0] + 0.03f, b[1] + 0.02f, b[2] + 0.03f, b[3] + 0.02f), 0xCC000000.toInt())
            solid(rectP(b[0], b[1], b[2], b[3]), cols[i])
        }
        solid(rectP(0.24f, -0.12f, 0.3f, -0.06f), WHITE)
        solid(rectP(-0.46f, 0.12f, -0.4f, 0.18f), GLITCH_C)
    }

    val codeTag: StickerArt = {
        shape(rectP(-0.44f, -0.3f, 0.44f, 0.3f, 0.08f), TERM)
        paLabel("</>", 0f, 0f, 0.3f, CODE, mono = true)
    }

    // ---------- Cube-head avatars (shared by the blocky party sets) ----------

    /** A generic cube-head figure waving. [hat]: 0 none, 1 cap, 2 hair tuft. */
    fun avatar(shirt: Int, pants: Int, skin: Int, hatColor: Int, hat: Int, shades: Boolean): StickerArt = {
        shape(rectP(-0.2f, 0.18f, -0.015f, 0.47f, 0.02f), pants)
        shape(rectP(0.015f, 0.18f, 0.2f, 0.47f, 0.02f), pants)
        shape(rectP(-0.34f, 0.1f, -0.23f, 0.2f, 0.03f), skin)
        shape(rectP(-0.34f, -0.1f, -0.23f, 0.12f, 0.02f), shirt)
        shape(rectP(0.23f, -0.45f, 0.34f, -0.35f, 0.03f), skin)
        shape(rectP(0.23f, -0.37f, 0.34f, -0.1f, 0.02f), shirt)
        shape(rectP(-0.23f, -0.12f, 0.23f, 0.21f, 0.03f), shirt)
        tint(rectP(-0.2f, -0.09f, -0.12f, 0.18f), 0x22FFFFFF)
        shape(rectP(-0.19f, -0.46f, 0.19f, -0.1f, 0.05f), skin)
        when (hat) {
            1 -> {
                shape(path { m(-0.21f, -0.34f); l(-0.21f, -0.44f); q(-0.21f, -0.5f, -0.14f, -0.5f); l(0.14f, -0.5f); q(0.21f, -0.5f, 0.21f, -0.44f); l(0.21f, -0.34f); close() }, hatColor)
                shape(rectP(0.08f, -0.37f, 0.33f, -0.32f, 0.02f), hatColor)
            }
            2 -> shape(path { m(-0.2f, -0.32f); l(-0.2f, -0.42f); q(-0.2f, -0.48f, -0.13f, -0.48f); l(0.13f, -0.48f); q(0.2f, -0.48f, 0.2f, -0.42f); l(0.2f, -0.36f); l(0.08f, -0.38f); l(0.02f, -0.33f); l(-0.06f, -0.38f); close() }, hatColor)
        }
        if (shades) {
            solid(rectP(-0.16f, -0.3f, -0.02f, -0.22f, 0.02f), BLACK)
            solid(rectP(0.02f, -0.3f, 0.16f, -0.22f, 0.02f), BLACK)
            solid(rectP(-0.03f, -0.29f, 0.03f, -0.27f), BLACK)
            tint(rectP(-0.14f, -0.29f, -0.1f, -0.27f), 0x99FFFFFF.toInt())
        } else eyes(0f, -0.26f, 0.07f, 0.028f)
        smile(0f, -0.19f, 0.06f)
    }

    // ---------- Blocky summer ----------

    private const val SEA = 0xFF29C5F6.toInt()
    private const val CORAL = 0xFFFF6B4A.toInt()
    private const val SUN_Y = 0xFFFFD84D.toInt()

    val summerKid = avatar(CORAL, 0xFF2B6CB0.toInt(), 0xFFF2C29B.toInt(), 0, 0, shades = true)
    val summerKid2 = avatar(0xFF3DDC97.toInt(), 0xFFFFD84D.toInt(), 0xFFB97A4E.toInt(), 0xFF2B6CB0.toInt(), 1, shades = false)

    val sunnySun: StickerArt = {
        for (i in 0 until 10) {
            val a = i * PI.toFloat() / 5
            val ca = cos(a); val sa = sin(a)
            shape(poly(ca * 0.47f, sa * 0.47f, cos(a + 0.2f) * 0.3f, sin(a + 0.2f) * 0.3f, cos(a - 0.2f) * 0.3f, sin(a - 0.2f) * 0.3f), 0xFFFFA62B.toInt())
        }
        shape(circleP(0f, 0f, 0.3f), SUN_Y)
        solid(path { addRoundRect(RectF(x(-0.2f), y(-0.08f), x(-0.02f), y(0.04f)), r(0.05f), r(0.05f), Path.Direction.CW) }, BLACK)
        solid(path { addRoundRect(RectF(x(0.02f), y(-0.08f), x(0.2f), y(0.04f)), r(0.05f), r(0.05f), Path.Direction.CW) }, BLACK)
        stroke(path { m(-0.02f, -0.06f); l(0.02f, -0.06f) }, lineW * 0.8f)
        smile(0f, 0.11f, 0.08f)
        tint(circleP(-0.18f, 0.12f, 0.035f), 0x66FF6B4A)
    }

    val palm: StickerArt = {
        val trunk = path { m(-0.03f, 0.48f); q(0.14f, 0.14f, -0.02f, -0.16f); l(0.06f, -0.16f); q(0.22f, 0.14f, 0.07f, 0.48f); close() }
        shape(trunk, 0xFFC98B4F.toInt())
        for (k in 0 until 5) {
            val t = 0.38f - k * 0.11f
            stroke(path { m(0.02f + t * 0.05f, t); l(0.09f + t * 0.06f, t - 0.03f) }, lineW * 0.7f)
        }
        val leafCol = 0xFF3FB950.toInt()
        paLeaf(0.02f, -0.18f, -0.46f, -0.02f, 0.09f, leafCol)
        paLeaf(0.02f, -0.18f, 0.48f, -0.02f, 0.09f, leafCol)
        paLeaf(0.02f, -0.18f, -0.34f, -0.42f, 0.08f, 0xFF56D364.toInt())
        paLeaf(0.02f, -0.18f, 0.38f, -0.42f, 0.08f, 0xFF56D364.toInt())
        paLeaf(0.02f, -0.18f, 0.04f, -0.49f, 0.07f, leafCol, sag = 0f)
        shape(circleP(-0.04f, -0.12f, 0.055f), 0xFF7A4B2A.toInt())
        shape(circleP(0.08f, -0.11f, 0.055f), 0xFF7A4B2A.toInt())
    }

    val beachBall: StickerArt = {
        val ball = circleP(0f, 0f, 0.43f)
        val cols = intArrayOf(CORAL, WHITE, SEA, WHITE, SUN_Y, WHITE)
        if (colorful) c.withSave {
            clipPath(ball)
            for (i in 0 until 6) solid(paWedge(-0.06f, -0.1f, 0.9f, i * 60f - 90f, 60f), cols[i])
            solid(circleP(-0.06f, -0.1f, 0.08f), WHITE)
        }
        stroke(ball)
        tint(ovalP(-0.3f, -0.32f, -0.16f, -0.2f), 0x88FFFFFF.toInt())
    }

    val surfboard: StickerArt = {
        c.withRotation(28f, x(0f), y(0f)) {
            val board = path { m(0f, -0.49f); cu(0.21f, -0.3f, 0.19f, 0.3f, 0f, 0.49f); cu(-0.19f, 0.3f, -0.21f, -0.3f, 0f, -0.49f); close() }
            shape(path { m(-0.02f, 0.3f); l(-0.02f, 0.44f); l(-0.1f, 0.44f); close() }, CORAL)
            shape(board, SEA)
            if (colorful) withSave {
                clipPath(board)
                solid(rectP(-0.3f, -0.12f, 0.3f, -0.04f), CORAL)
                solid(rectP(-0.3f, 0.0f, 0.3f, 0.04f), SUN_Y)
            }
            stroke(path { m(0f, -0.4f); l(0f, 0.4f) }, lineW * 0.5f, if (colorful) WHITE else ink.line)
            stroke(board)
        }
    }

    val iceCream: StickerArt = {
        val cone = poly(-0.18f, 0.02f, 0.18f, 0.02f, 0f, 0.49f)
        shape(cone, 0xFFE9B36A.toInt())
        c.withSave {
            clipPath(cone)
            for (i in -3..3) {
                stroke(path { m(-0.3f + i * 0.1f, 0f); l(0.0f + i * 0.1f, 0.5f) }, lineW * 0.5f, 0xFFB57A35.toInt())
                stroke(path { m(0.3f + i * 0.1f, 0f); l(0.0f + i * 0.1f, 0.5f) }, lineW * 0.5f, 0xFFB57A35.toInt())
            }
        }
        stroke(cone)
        shape(circleP(0f, -0.06f, 0.2f), 0xFFFF9EC4.toInt())
        shape(circleP(0f, -0.27f, 0.16f), 0xFF9BE8C8.toInt())
        stroke(path { m(0.03f, -0.42f); q(0.08f, -0.5f, 0.14f, -0.49f) }, lineW * 0.8f)
        shape(circleP(0.03f, -0.42f, 0.055f), RED)
        tint(ovalP(-0.13f, -0.14f, -0.04f, -0.06f), 0x88FFFFFF.toInt())
    }

    // ---------- Brick party ----------

    private const val BRICK_RED = 0xFFE3342F.toInt()
    private const val BRICK_BLUE = 0xFF2563EB.toInt()
    private const val BRICK_GREEN = 0xFF16A34A.toInt()
    private const val BRICK_YELLOW = 0xFFFACC15.toInt()

    fun brick(col: Int, studs: Int = 4): StickerArt = {
        val wd = 0.23f * studs / 2f
        val step = wd * 2 / studs
        for (i in 0 until studs) {
            val cx = -wd + step * (i + 0.5f)
            shape(rectP(cx - step * 0.3f, -0.26f, cx + step * 0.3f, -0.1f, 0.025f), col)
            tint(rectP(cx - step * 0.22f, -0.24f, cx - step * 0.08f, -0.14f), 0x55FFFFFF)
        }
        val body = rectP(-wd, -0.13f, wd, 0.3f, 0.03f)
        shape(body, col)
        tint(rectP(-wd + 0.02f, 0.2f, wd - 0.02f, 0.28f), PackAKit.shade(col, 0.75f))
        tint(rectP(-wd + 0.04f, -0.1f, -wd + 0.09f, 0.16f), 0x44FFFFFF)
        stroke(body)
    }

    val brickRed = brick(BRICK_RED)
    val brickBlue = brick(BRICK_BLUE)
    val brickYellow = brick(BRICK_YELLOW, 2)

    val builder = avatar(BRICK_BLUE, 0xFF374151.toInt(), 0xFFFFD9B8.toInt(), BRICK_RED, 1, shades = false)
    val builder2 = avatar(BRICK_YELLOW, BRICK_GREEN, 0xFF9C6B4A.toInt(), 0xFF2D1B12.toInt(), 2, shades = false)

    val coin: StickerArt = {
        shape(circleP(0f, 0f, 0.42f), GOLD)
        stroke(circleP(0f, 0f, 0.31f), lineW * 0.8f, if (colorful) GOLD_D else ink.line)
        shape(rectP(-0.13f, -0.15f, 0.13f, 0.15f, 0.04f), GOLD_L)
        shape(rectP(-0.08f, -0.24f, 0.08f, -0.15f, 0.02f), GOLD_L)
        tint(ovalP(-0.3f, -0.3f, -0.16f, -0.18f), 0xAAFFFFFF.toInt())
    }

    fun trophy(cup: Int, plinth: Int): StickerArt = {
        val dark = PackAKit.shade(cup, 0.8f)
        stroke(path { m(-0.25f, -0.33f); q(-0.47f, -0.33f, -0.37f, -0.13f); q(-0.31f, -0.04f, -0.18f, -0.03f) }, lineW * 2.6f)
        stroke(path { m(-0.25f, -0.33f); q(-0.47f, -0.33f, -0.37f, -0.13f); q(-0.31f, -0.04f, -0.18f, -0.03f) }, lineW * 1.1f, if (colorful) dark else ink.paper)
        stroke(path { m(0.25f, -0.33f); q(0.47f, -0.33f, 0.37f, -0.13f); q(0.31f, -0.04f, 0.18f, -0.03f) }, lineW * 2.6f)
        stroke(path { m(0.25f, -0.33f); q(0.47f, -0.33f, 0.37f, -0.13f); q(0.31f, -0.04f, 0.18f, -0.03f) }, lineW * 1.1f, if (colorful) dark else ink.paper)
        shape(rectP(-0.05f, 0.04f, 0.05f, 0.26f), dark)
        shape(path { m(-0.27f, -0.43f); l(0.27f, -0.43f); q(0.27f, 0.06f, 0f, 0.08f); q(-0.27f, 0.06f, -0.27f, -0.43f); close() }, cup)
        tint(path { m(-0.18f, -0.38f); q(-0.18f, -0.06f, -0.06f, 0.0f); l(-0.1f, -0.38f); close() }, 0x66FFFFFF)
        shape(starPath(0.02f, -0.2f, 0.11f, 0.05f), if (colorful) WHITE else ink.paper)
        shape(rectP(-0.18f, 0.24f, 0.18f, 0.33f, 0.02f), cup)
        shape(rectP(-0.27f, 0.32f, 0.27f, 0.47f, 0.03f), plinth)
    }

    val goldTrophy = trophy(GOLD, 0xFF3B2A4A.toInt())

    val checkpoint: StickerArt = {
        shape(rectP(-0.37f, -0.44f, -0.3f, 0.47f, 0.02f), 0xFFB0B7C3.toInt())
        val flag = path { m(-0.3f, -0.43f); q(-0.05f, -0.52f, 0.14f, -0.42f); q(0.32f, -0.33f, 0.47f, -0.42f); l(0.47f, 0.0f); q(0.32f, 0.08f, 0.14f, -0.01f); q(-0.05f, -0.1f, -0.3f, -0.01f); close() }
        shape(flag, WHITE)
        if (colorful) c.withSave {
            clipPath(flag)
            val sq = 0.1f
            for (j in 0 until 7) for (i in 0 until 9) if ((i + j) % 2 == 0) solid(rectP(-0.3f + i * sq, -0.55f + j * sq, -0.3f + (i + 1) * sq, -0.55f + (j + 1) * sq), BLACK)
        }
        stroke(flag)
        shape(circleP(-0.335f, -0.45f, 0.055f), GOLD)
        shape(rectP(-0.46f, 0.38f, -0.2f, 0.48f, 0.02f), BRICK_GREEN)
    }

    val obstacle: StickerArt = {
        val pole = rectP(0.1f, -0.46f, 0.2f, 0.02f, 0.02f)
        shape(pole, WHITE)
        if (colorful) c.withSave {
            clipPath(pole)
            for (k in 0 until 6) solid(rectP(0.08f, -0.46f + k * 0.16f, 0.22f, -0.38f + k * 0.16f), BRICK_RED)
        }
        stroke(pole)
        for (i in 0 until 4) shape(rectP(-0.4f + i * 0.22f, -0.04f, -0.28f + i * 0.22f, 0.04f, 0.02f), BRICK_GREEN)
        shape(rectP(-0.46f, 0.02f, 0.46f, 0.24f, 0.03f), BRICK_GREEN)
        shape(rectP(-0.3f, 0.22f, 0.3f, 0.4f, 0.03f), PackAKit.shade(BRICK_GREEN, 0.8f))
        shape(rectP(-0.42f, -0.22f, -0.24f, -0.04f, 0.03f), GOLD)
        tint(circleP(-0.33f, -0.13f, 0.04f), GOLD_L)
    }

    // ---------- Tactical red ----------

    private const val TAC_RED = 0xFFE8312F.toInt()
    private const val TAC_WHITE = 0xFFF2EDE4.toInt()
    private const val TAC_GRAY = 0xFF2A2A30.toInt()

    val crosshair: StickerArt = {
        val ring = circleP(0f, 0f, 0.31f)
        stroke(ring, lineW * 2.4f, BLACK)
        stroke(ring, lineW * 1.2f, TAC_RED)
        val ticks = path { m(0f, -0.47f); l(0f, -0.17f); m(0f, 0.17f); l(0f, 0.47f); m(-0.47f, 0f); l(-0.17f, 0f); m(0.17f, 0f); l(0.47f, 0f) }
        stroke(ticks, lineW * 2.4f, BLACK)
        stroke(ticks, lineW * 1.2f, TAC_WHITE)
        solid(circleP(0f, 0f, 0.05f), TAC_RED)
    }

    val chevrons: StickerArt = {
        val cols = intArrayOf(TAC_RED, TAC_WHITE, TAC_RED)
        for (k in 0 until 3) {
            val y0 = -0.36f + k * 0.26f
            shape(poly(-0.38f, y0 + 0.16f, 0f, y0 - 0.06f, 0.38f, y0 + 0.16f, 0.38f, y0 + 0.3f, 0f, y0 + 0.08f, -0.38f, y0 + 0.3f), cols[k])
        }
    }

    val diamond: StickerArt = {
        shape(poly(-0.48f, 0f, -0.3f, -0.09f, -0.3f, 0.09f), TAC_WHITE)
        shape(poly(0.48f, 0f, 0.3f, -0.09f, 0.3f, 0.09f), TAC_WHITE)
        shape(poly(0f, -0.47f, 0.34f, 0f, 0f, 0.47f, -0.34f, 0f), TAC_GRAY)
        shape(poly(0f, -0.26f, 0.19f, 0f, 0f, 0.26f, -0.19f, 0f), TAC_RED)
        stroke(path { m(0f, -0.26f); l(0f, 0.26f) }, lineW * 0.7f, BLACK)
    }

    val hazard: StickerArt = {
        val plate = rectP(-0.47f, -0.2f, 0.47f, 0.2f, 0.03f)
        shape(plate, TAC_RED)
        if (colorful) c.withSave {
            clipPath(plate)
            for (k in -6..6) solid(poly(k * 0.16f, -0.25f, k * 0.16f + 0.08f, -0.25f, k * 0.16f - 0.08f, 0.25f, k * 0.16f - 0.16f, 0.25f), BLACK)
        }
        stroke(plate)
    }

    val warning: StickerArt = {
        shape(path { m(0f, -0.42f); l(0.46f, 0.38f); l(-0.46f, 0.38f); close() }, TAC_RED)
        solid(rectP(-0.04f, -0.18f, 0.04f, 0.14f, 0.02f), TAC_WHITE)
        solid(circleP(0f, 0.25f, 0.045f), TAC_WHITE)
    }

    // ---------- Battle royale ----------

    private const val BR_PURPLE = 0xFF7B2FF7.toInt()
    private const val BR_BLUE = 0xFF2D7FF9.toInt()
    private const val BR_YELLOW = 0xFFFFD23F.toInt()

    private val bolt: Pen.(Float, Float, Float) -> Unit = { u, v, k ->
        shape(poly(u + 0.06f * k, v - 0.36f * k, u - 0.16f * k, v + 0.04f * k, u, v + 0.04f * k, u - 0.06f * k, v + 0.36f * k, u + 0.17f * k, v - 0.07f * k, u + 0.02f * k, v - 0.07f * k, u + 0.12f * k, v - 0.36f * k), BR_YELLOW)
    }

    val storm: StickerArt = {
        val disc = circleP(0f, 0f, 0.43f)
        paGfill(disc, 0xFFB47CFF.toInt(), 0xFF5A1BC4.toInt())
        stroke(path { addArc(RectF(x(-0.32f), y(-0.32f), x(0.32f), y(0.32f)), 200f, 120f) }, lineW * 0.8f, 0xAAFFFFFF.toInt())
        stroke(path { addArc(RectF(x(-0.24f), y(-0.24f), x(0.24f), y(0.24f)), 20f, 110f) }, lineW * 0.8f, 0x88FFFFFF.toInt())
        bolt(0f, 0.02f, 0.9f)
    }

    val island: StickerArt = {
        shape(poly(-0.42f, 0.04f, 0.42f, 0.04f, 0.22f, 0.26f, 0.06f, 0.46f, -0.08f, 0.32f, -0.26f, 0.24f), 0xFF8A6A55.toInt())
        tint(poly(0.1f, 0.08f, 0.36f, 0.08f, 0.2f, 0.24f, 0.08f, 0.38f), 0x33000000)
        stroke(path { m(-0.08f, 0.32f); l(-0.03f, 0.12f) }, lineW * 0.6f)
        shape(rectP(-0.13f, -0.24f, -0.07f, 0.0f), WOOD)
        shape(circleP(-0.1f, -0.3f, 0.15f), 0xFF34C759.toInt())
        tint(circleP(-0.15f, -0.35f, 0.05f), 0x55FFFFFF)
        shape(rectP(-0.45f, -0.04f, 0.45f, 0.08f, 0.06f), 0xFF5BD46C.toInt())
        shape(rectP(0.1f, -0.16f, 0.32f, -0.02f, 0.03f), BR_BLUE)
        shape(rectP(0.1f, -0.2f, 0.32f, -0.12f, 0.04f), BR_YELLOW)
    }

    val glider: StickerArt = {
        c.withRotation(-10f, x(0f), y(0f)) {
            stroke(path { m(0f, -0.1f); l(-0.14f, 0.26f); m(0f, -0.1f); l(0.14f, 0.26f); m(-0.18f, 0.26f); l(0.18f, 0.26f) }, lineW * 0.9f)
            val wing = path { m(-0.48f, 0.04f); l(0f, -0.34f); l(0.48f, 0.04f); q(0f, -0.12f, -0.48f, 0.04f); close() }
            shape(wing, BR_YELLOW)
            if (colorful) withSave {
                clipPath(wing)
                solid(poly(0f, -0.4f, 0.12f, -0.4f, 0.5f, 0.1f, 0.38f, 0.1f), BR_BLUE)
                solid(poly(0f, -0.4f, -0.12f, -0.4f, -0.5f, 0.1f, -0.38f, 0.1f), BR_BLUE)
            }
            stroke(wing)
            shape(rectP(-0.08f, 0.24f, 0.08f, 0.44f, 0.04f), BR_PURPLE)
        }
    }

    val lootChest: StickerArt = {
        if (colorful) for (i in 0 until 8) {
            val a = -PI.toFloat() + i * PI.toFloat() / 7
            tint(poly(0f, -0.1f, cos(a - 0.08f) * 0.5f, -0.1f + sin(a - 0.08f) * 0.4f, cos(a + 0.08f) * 0.5f, -0.1f + sin(a + 0.08f) * 0.4f), 0x66FFE26B)
        }
        shape(rectP(-0.4f, -0.04f, 0.4f, 0.38f, 0.04f), BR_BLUE)
        shape(path { m(-0.4f, -0.04f); l(-0.4f, -0.16f); q(-0.4f, -0.33f, -0.23f, -0.33f); l(0.23f, -0.33f); q(0.4f, -0.33f, 0.4f, -0.16f); l(0.4f, -0.04f); close() }, 0xFF4C97FF.toInt())
        shape(rectP(-0.3f, -0.31f, -0.2f, 0.38f), GOLD)
        shape(rectP(0.2f, -0.31f, 0.3f, 0.38f), GOLD)
        shape(rectP(-0.08f, -0.12f, 0.08f, 0.08f, 0.02f), GOLD)
        solid(circleP(0f, -0.04f, 0.025f), BLACK)
        tint(rectP(-0.36f, -0.26f, -0.32f, -0.08f), 0x66FFFFFF)
    }

    val supplyDrop: StickerArt = {
        val anchors = floatArrayOf(-0.41f, -0.14f, 0.14f, 0.41f)
        for (a in anchors) stroke(path { m(a, -0.14f); l(if (a < 0) -0.15f else 0.15f, 0.14f) }, lineW * 0.6f)
        val chute = path { m(-0.43f, -0.14f); q(-0.43f, -0.49f, 0f, -0.49f); q(0.43f, -0.49f, 0.43f, -0.14f); q(0.28f, -0.21f, 0.14f, -0.14f); q(0f, -0.21f, -0.14f, -0.14f); q(-0.28f, -0.21f, -0.43f, -0.14f); close() }
        shape(chute, BR_BLUE)
        if (colorful) c.withSave { clipPath(chute); solid(rectP(-0.13f, -0.5f, 0.13f, -0.1f), WHITE) }
        stroke(chute)
        shape(rectP(-0.2f, 0.12f, 0.2f, 0.47f, 0.02f), 0xFFE0A84A.toInt())
        stroke(path { m(-0.2f, 0.12f); l(0.2f, 0.47f); m(0.2f, 0.12f); l(-0.2f, 0.47f) }, lineW * 0.7f, if (colorful) 0xFFB07A2A.toInt() else ink.line)
        shape(rectP(-0.2f, 0.25f, 0.2f, 0.33f), BR_PURPLE)
    }

    val crown: StickerArt = {
        shape(poly(-0.42f, 0.22f, -0.42f, -0.24f, -0.21f, -0.02f, 0f, -0.36f, 0.21f, -0.02f, 0.42f, -0.24f, 0.42f, 0.22f), BR_YELLOW)
        tint(poly(-0.36f, 0.14f, -0.36f, -0.1f, -0.24f, 0.04f), 0x55FFFFFF)
        shape(rectP(-0.45f, 0.14f, 0.45f, 0.38f, 0.04f), BR_PURPLE)
        shape(circleP(-0.42f, -0.28f, 0.055f), 0xFF4CE0FF.toInt())
        shape(circleP(0f, -0.4f, 0.06f), 0xFFFF5A8A.toInt())
        shape(circleP(0.42f, -0.28f, 0.055f), 0xFF4CE0FF.toInt())
        paLabel("#1", 0f, 0.26f, 0.2f, WHITE)
    }

    // ---------- Gamer RGB ----------

    private const val RGB_R = 0xFFFF3B5C.toInt()
    private const val RGB_G = 0xFF2BFF88.toInt()
    private const val RGB_B = 0xFF3B7BFF.toInt()
    private const val RGB_C = 0xFF00E5FF.toInt()
    private const val RGB_M = 0xFFFF2BD6.toInt()
    private const val GEAR = 0xFF262634.toInt()
    private const val GEAR_L = 0xFF3D3D52.toInt()

    private fun Pen.rgbLine(p: Path, u0: Float, u1: Float, width: Float) {
        if (!colorful) { stroke(p, width); return }
        val sh = LinearGradient(x(u0), 0f, x(u1), 0f, intArrayOf(RGB_R, RGB_G, RGB_B, RGB_M), null, Shader.TileMode.CLAMP)
        c.drawPath(p, PackAKit.paint(0, Paint.Style.STROKE, width * 2.6f).apply { shader = sh; alpha = 70 })
        c.drawPath(p, PackAKit.paint(0, Paint.Style.STROKE, width).apply { shader = sh })
    }

    val controller: StickerArt = {
        val body = path {
            m(-0.3f, -0.22f); l(0.3f, -0.22f); q(0.46f, -0.22f, 0.47f, -0.02f); l(0.48f, 0.2f); q(0.48f, 0.34f, 0.36f, 0.34f)
            q(0.28f, 0.34f, 0.22f, 0.22f); l(0.15f, 0.12f); l(-0.15f, 0.12f); l(-0.22f, 0.22f); q(-0.28f, 0.34f, -0.36f, 0.34f)
            q(-0.48f, 0.34f, -0.48f, 0.2f); l(-0.47f, -0.02f); q(-0.46f, -0.22f, -0.3f, -0.22f); close()
        }
        shape(body, GEAR)
        rgbLine(path { m(-0.28f, -0.17f); l(0.28f, -0.17f) }, -0.3f, 0.3f, lineW * 0.9f)
        val pad = path {
            addRect(RectF(x(-0.33f), y(-0.06f), x(-0.21f), y(-0.01f)), Path.Direction.CW)
            addRect(RectF(x(-0.295f), y(-0.1f), x(-0.245f), y(0.03f)), Path.Direction.CW)
        }
        solid(pad, 0xFFB8B8CC.toInt())
        solid(circleP(0.27f, -0.1f, 0.035f), RGB_G)
        solid(circleP(0.33f, -0.035f, 0.035f), RGB_R)
        solid(circleP(0.21f, -0.035f, 0.035f), RGB_B)
        solid(circleP(0.27f, 0.03f, 0.035f), 0xFFFFD23F.toInt())
        shape(circleP(-0.11f, 0.06f, 0.06f), GEAR_L)
        shape(circleP(0.11f, 0.06f, 0.06f), GEAR_L)
    }

    val headset: StickerArt = {
        paTube(path { m(-0.35f, 0.04f); q(-0.37f, -0.46f, 0f, -0.46f); q(0.37f, -0.46f, 0.35f, 0.04f) }, lineW * 1.6f, GEAR_L)
        stroke(path { m(-0.36f, 0.3f); q(-0.3f, 0.46f, -0.08f, 0.42f) }, lineW * 1.2f)
        solid(circleP(-0.07f, 0.42f, 0.045f), RGB_M)
        for (s in floatArrayOf(-1f, 1f)) {
            val cup = rectP(if (s < 0) -0.47f else 0.23f, -0.08f, if (s < 0) -0.23f else 0.47f, 0.32f, 0.09f)
            shape(cup, GEAR)
            val ring = rectP(if (s < 0) -0.42f else 0.28f, -0.03f, if (s < 0) -0.28f else 0.42f, 0.27f, 0.06f)
            if (colorful) PackAKit.glow(c, ring, if (s < 0) RGB_C else RGB_M, lineW * 0.7f) else stroke(ring)
        }
    }

    val keys: StickerArt = {
        fun key(u: Float, v: Float, ch: String, glowCol: Int) {
            val k = 0.135f
            val base = rectP(u - k, v - k, u + k, v + k, 0.04f)
            if (colorful) PackAKit.glow(c, base, glowCol, lineW * 0.6f)
            shape(base, GEAR)
            tint(rectP(u - k + 0.035f, v - k + 0.025f, u + k - 0.035f, v + k - 0.06f, 0.03f), GEAR_L)
            paLabel(ch, u, v - 0.015f, 0.14f, if (colorful) WHITE else ink.line)
        }
        key(0f, -0.17f, "W", RGB_C)
        key(-0.31f, 0.15f, "A", RGB_M)
        key(0f, 0.15f, "S", RGB_G)
        key(0.31f, 0.15f, "D", RGB_B)
    }

    val gg: StickerArt = {
        val badge = rectP(-0.46f, -0.28f, 0.46f, 0.28f, 0.12f)
        shape(badge, GEAR)
        rgbLine(rectP(-0.42f, -0.24f, 0.42f, 0.24f, 0.09f), -0.46f, 0.46f, lineW * 0.8f)
        if (colorful) paLabel("GG", 0.025f, 0.02f, 0.38f, RGB_M)
        paLabel("GG", 0f, 0f, 0.38f, if (colorful) RGB_C else ink.line)
    }

    val mouse: StickerArt = {
        stroke(path { m(0f, -0.38f); q(0.04f, -0.48f, 0.2f, -0.47f) }, lineW * 0.9f)
        val body = rectP(-0.23f, -0.4f, 0.23f, 0.44f, 0.23f)
        shape(body, GEAR)
        stroke(path { m(0f, -0.4f); l(0f, -0.1f); m(-0.23f, -0.1f); q(0f, -0.06f, 0.23f, -0.1f) }, lineW * 0.6f)
        solid(rectP(-0.03f, -0.32f, 0.03f, -0.18f, 0.03f), RGB_C)
        rgbLine(path { m(-0.15f, 0.3f); q(0f, 0.38f, 0.15f, 0.3f) }, -0.15f, 0.15f, lineW * 0.9f)
    }

    // ---------- Synthwave ----------

    private const val SYN_PINK = 0xFFFF3EA5.toInt()
    private const val SYN_CYAN = 0xFF2DE2E6.toInt()
    private const val SYN_NIGHT = 0xFF1B0B3A.toInt()
    private const val SYN_SUN = 0xFFFFD319.toInt()

    /** A sun cut by horizontal gaps that widen toward the bottom. */
    fun stripedSun(cx: Float, cy: Float, rad: Float): Path {
        val sun = Path().apply { addCircle(cx, cy, rad, Path.Direction.CW) }
        val gaps = Path()
        val bands = floatArrayOf(0.05f, 0.06f, 0.08f, 0.1f, 0.12f)
        bands.forEachIndexed { i, hgt ->
            val top = cy + rad * (0.08f + i * 0.2f)
            gaps.addRect(cx - rad, top, cx + rad, top + rad * hgt, Path.Direction.CW)
        }
        sun.op(gaps, Path.Op.DIFFERENCE)
        return sun
    }

    val retroSun: StickerArt = {
        if (colorful) solid(circleP(0f, 0f, 0.49f), 0x33FF3EA5)
        paGfill(stripedSun(x(0f), y(0f), r(0.42f)), SYN_SUN, SYN_PINK, -0.42f, 0.42f, outline = false)
    }

    val neonPalm: StickerArt = {
        val trunk = path { m(-0.03f, 0.48f); q(0.14f, 0.14f, -0.02f, -0.16f); l(0.05f, -0.16f); q(0.21f, 0.14f, 0.06f, 0.48f); close() }
        val fronds = listOf(
            floatArrayOf(-0.46f, -0.02f), floatArrayOf(0.48f, -0.02f), floatArrayOf(-0.34f, -0.42f), floatArrayOf(0.38f, -0.42f), floatArrayOf(0.04f, -0.49f),
        )
        if (colorful) {
            solid(trunk, SYN_NIGHT)
            paNeon(trunk, SYN_PINK, lineW * 0.8f)
            for (f in fronds) {
                val (x1, y1) = f[0] to f[1]
                val mx = (0.02f + x1) / 2; val my = (-0.18f + y1) / 2 + 0.06f
                val dx = x1 - 0.02f; val dy = y1 + 0.18f
                val len = sqrt(dx * dx + dy * dy)
                val nx = -dy / len * 0.08f; val ny = dx / len * 0.08f
                val p = path { m(0.02f, -0.18f); q(mx + nx, my + ny, x1, y1); q(mx - nx, my - ny, 0.02f, -0.18f); close() }
                solid(p, SYN_NIGHT)
                paNeon(p, SYN_PINK, lineW * 0.8f)
            }
        } else {
            shape(trunk, 0)
            for (f in fronds) paLeaf(0.02f, -0.18f, f[0], f[1], 0.08f, 0)
        }
    }

    val cassette: StickerArt = {
        shape(rectP(-0.47f, -0.3f, 0.47f, 0.3f, 0.05f), 0xFF2A1450.toInt())
        val lbl = rectP(-0.38f, -0.23f, 0.38f, 0.06f, 0.03f)
        if (colorful) c.withSave {
            clipPath(lbl)
            val cols = intArrayOf(SYN_PINK, 0xFFFF7A45.toInt(), SYN_SUN, SYN_CYAN)
            for (i in cols.indices) solid(rectP(-0.4f, -0.24f + i * 0.075f, 0.4f, -0.165f + i * 0.075f), cols[i])
        }
        stroke(lbl)
        shape(rectP(-0.22f, -0.11f, 0.22f, 0.02f, 0.06f), SYN_NIGHT)
        shape(circleP(-0.13f, -0.045f, 0.045f), WHITE)
        shape(circleP(0.13f, -0.045f, 0.045f), WHITE)
        shape(poly(-0.27f, 0.3f, -0.2f, 0.15f, 0.2f, 0.15f, 0.27f, 0.3f), 0xFF3A1E6A.toInt())
    }

    val chromeStar: StickerArt = {
        val sp = path { m(0f, -0.47f); q(0.07f, -0.07f, 0.47f, 0f); q(0.07f, 0.07f, 0f, 0.47f); q(-0.07f, 0.07f, -0.47f, 0f); q(-0.07f, -0.07f, 0f, -0.47f); close() }
        if (colorful) c.drawPath(sp, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, y(-0.47f), 0f, y(0.47f), intArrayOf(WHITE, 0xFFB9F6FF.toInt(), 0xFF8E5BD8.toInt(), WHITE), floatArrayOf(0f, 0.45f, 0.55f, 1f), Shader.TileMode.CLAMP)
        })
        stroke(sp)
        tint(circleP(0f, 0f, 0.05f), WHITE)
    }

    val shades: StickerArt = {
        stroke(path { m(-0.46f, -0.12f); l(0.46f, -0.12f) }, lineW * 1.4f)
        for (s in floatArrayOf(-1f, 1f)) {
            val lens = path {
                m(s * 0.04f, -0.13f); l(s * 0.44f, -0.13f); q(s * 0.46f, 0.2f, s * 0.24f, 0.2f); q(s * 0.04f, 0.2f, s * 0.04f, -0.13f); close()
            }
            paGfill(lens, SYN_CYAN, SYN_PINK, -0.13f, 0.2f)
            tint(path { m(s * 0.12f, -0.08f); l(s * 0.22f, -0.08f); l(s * 0.14f, 0.08f); close() }, 0x77FFFFFF)
        }
    }

    // ---------- Neon cyberpunk ----------

    private const val CY_MAG = 0xFFFF2E97.toInt()
    private const val CY_YEL = 0xFFFCE93A.toInt()
    private const val CY_CYAN = 0xFF21F0FF.toInt()
    private const val CY_DARK = 0xFF120F24.toInt()

    val circuit: StickerArt = {
        val traces = listOf(
            path { m(-0.44f, -0.22f); l(-0.1f, -0.22f); l(0.06f, -0.38f); l(0.42f, -0.38f) } to CY_YEL,
            path { m(-0.44f, 0.04f); l(0.16f, 0.04f); l(0.3f, -0.1f); l(0.44f, -0.1f) } to CY_MAG,
            path { m(-0.3f, 0.42f); l(-0.1f, 0.22f); l(0.44f, 0.22f) } to CY_YEL,
        )
        for ((p, col) in traces) if (colorful) paNeon(p, col, lineW * 0.9f) else stroke(p)
        val nodes = floatArrayOf(-0.44f, -0.22f, 0.42f, -0.38f, -0.44f, 0.04f, 0.44f, -0.1f, -0.3f, 0.42f, 0.44f, 0.22f, 0.16f, 0.04f)
        var k = 0
        while (k < nodes.size) { shape(circleP(nodes[k], nodes[k + 1], 0.05f), CY_DARK); solid(circleP(nodes[k], nodes[k + 1], 0.022f), CY_CYAN); k += 2 }
    }

    val chip: StickerArt = {
        for (i in 0 until 3) {
            val o = -0.16f + i * 0.16f
            solid(rectP(o - 0.03f, -0.46f, o + 0.03f, -0.3f), 0xFF9AA0B5.toInt())
            solid(rectP(o - 0.03f, 0.3f, o + 0.03f, 0.46f), 0xFF9AA0B5.toInt())
            solid(rectP(-0.46f, o - 0.03f, -0.3f, o + 0.03f), 0xFF9AA0B5.toInt())
            solid(rectP(0.3f, o - 0.03f, 0.46f, o + 0.03f), 0xFF9AA0B5.toInt())
        }
        val body = rectP(-0.32f, -0.32f, 0.32f, 0.32f, 0.05f)
        shape(body, CY_DARK)
        if (colorful) paNeon(body, CY_MAG, lineW * 0.6f)
        val core = rectP(-0.15f, -0.15f, 0.15f, 0.15f, 0.02f)
        if (colorful) c.drawPath(core, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x(-0.15f), y(-0.15f), x(0.15f), y(0.15f), CY_MAG, CY_CYAN, Shader.TileMode.CLAMP) })
        stroke(core, lineW * 0.6f)
    }

    val neonSign: StickerArt = {
        val board = rectP(-0.47f, -0.26f, 0.47f, 0.26f, 0.08f)
        shape(board, CY_DARK)
        if (colorful) paNeon(rectP(-0.41f, -0.2f, 0.41f, 0.2f, 0.05f), CY_CYAN, lineW * 0.6f)
        paLabel("24H", 0f, 0f, 0.28f, if (colorful) WHITE else ink.line, edge = if (colorful) PackAKit.alpha(CY_MAG, 0.8f) else null, edgeW = 0.22f)
    }

    val umbrella: StickerArt = {
        if (colorful) for (i in 0 until 6) {
            val u = -0.42f + i * 0.17f
            stroke(path { m(u, -0.06f + (i % 2) * 0.1f); l(u - 0.04f, 0.1f + (i % 2) * 0.1f) }, lineW * 0.5f, 0x8821F0FF.toInt())
        }
        val canopy = path {
            m(-0.44f, -0.02f); q(-0.42f, -0.46f, 0f, -0.47f); q(0.42f, -0.46f, 0.44f, -0.02f)
            q(0.33f, -0.1f, 0.22f, -0.02f); q(0.11f, -0.1f, 0f, -0.02f); q(-0.11f, -0.1f, -0.22f, -0.02f); q(-0.33f, -0.1f, -0.44f, -0.02f); close()
        }
        shape(canopy, 0xFF3A0F3E.toInt())
        if (colorful) {
            paNeon(canopy, CY_MAG, lineW * 0.8f)
            stroke(path { m(0f, -0.47f); l(-0.22f, -0.04f); m(0f, -0.47f); l(0f, -0.04f); m(0f, -0.47f); l(0.22f, -0.04f) }, lineW * 0.5f, CY_MAG)
        }
        val handle = path { m(0f, -0.02f); l(0f, 0.36f); q(0f, 0.46f, -0.1f, 0.44f); q(-0.16f, 0.42f, -0.16f, 0.36f) }
        if (colorful) paNeon(handle, CY_YEL, lineW * 0.8f) else stroke(handle)
    }

    val hudRing: StickerArt = {
        val hex = path {
            for (i in 0 until 6) {
                val a = PI / 6 + i * PI / 3
                val px = (cos(a) * 0.42f).toFloat(); val py = (sin(a) * 0.42f).toFloat()
                if (i == 0) m(px, py) else l(px, py)
            }
            close()
        }
        shape(hex, 0xCC120F24.toInt())
        if (colorful) paNeon(hex, CY_CYAN, lineW * 0.7f)
        stroke(path { addArc(RectF(x(-0.26f), y(-0.26f), x(0.26f), y(0.26f)), 200f, 140f) }, lineW * 0.9f, if (colorful) CY_MAG else ink.line)
        stroke(path { addArc(RectF(x(-0.26f), y(-0.26f), x(0.26f), y(0.26f)), 20f, 140f) }, lineW * 0.9f, if (colorful) CY_MAG else ink.line)
        shape(poly(0f, -0.1f, 0.09f, 0.07f, -0.09f, 0.07f), CY_YEL)
    }

    // ---------- Football ----------

    private const val PITCH = 0xFF2FA84F.toInt()
    private const val CARD_Y = 0xFFFFD23F.toInt()

    val ball: StickerArt = {
        val disc = circleP(0f, 0f, 0.44f)
        shape(disc, WHITE)
        c.withSave {
            clipPath(disc)
            fun pent(u: Float, v: Float, rr: Float, rot: Float) = path {
                for (i in 0 until 5) {
                    val a = rot + i * 2 * PI.toFloat() / 5
                    val px = u + cos(a) * rr; val py = v + sin(a) * rr
                    if (i == 0) m(px, py) else l(px, py)
                }
                close()
            }
            val up = -PI.toFloat() / 2
            solid(pent(0f, 0f, 0.15f, up), BLACK)
            for (i in 0 until 5) {
                val a = up + PI.toFloat() / 5 + i * 2 * PI.toFloat() / 5
                val ex = cos(a) * 0.15f; val ey = sin(a) * 0.15f
                val px = cos(a) * 0.46f; val py = sin(a) * 0.46f
                stroke(path { m(ex, ey); l(cos(a) * 0.3f, sin(a) * 0.3f) }, lineW * 0.6f, BLACK)
                solid(pent(px, py, 0.15f, a + PI.toFloat()), BLACK)
            }
        }
        stroke(disc)
        tint(ovalP(-0.3f, -0.32f, -0.16f, -0.22f), 0x99FFFFFF.toInt())
    }

    val goal: StickerArt = {
        shape(rectP(-0.48f, 0.34f, 0.48f, 0.44f, 0.02f), PITCH)
        val net = 0xFFCFD8DC.toInt()
        for (i in 0..8) stroke(path { m(-0.42f + i * 0.105f, -0.24f); l(-0.42f + i * 0.105f, 0.36f) }, lineW * 0.4f, if (colorful) net else ink.line)
        for (j in 0..5) stroke(path { m(-0.42f, -0.24f + j * 0.12f); l(0.42f, -0.24f + j * 0.12f) }, lineW * 0.4f, if (colorful) net else ink.line)
        paTube(path { m(-0.44f, 0.38f); l(-0.44f, -0.28f); l(0.44f, -0.28f); l(0.44f, 0.38f) }, lineW * 1.6f, WHITE)
        shape(circleP(0.22f, 0.27f, 0.08f), WHITE)
        solid(circleP(0.22f, 0.27f, 0.03f), BLACK)
    }

    val whistle: StickerArt = {
        stroke(path { m(0.34f, -0.2f); q(0.48f, -0.42f, 0.24f, -0.48f) }, lineW * 1.3f, if (colorful) RED else ink.line)
        stroke(circleP(0.3f, -0.15f, 0.07f), lineW * 1.1f)
        val metal = 0xFFCFD8DC.toInt()
        shape(rectP(-0.47f, -0.16f, 0.1f, 0.04f, 0.04f), metal)
        shape(circleP(0.08f, 0.14f, 0.27f), metal)
        tint(ovalP(-0.08f, 0.0f, 0.04f, 0.08f), 0xAAFFFFFF.toInt())
        solid(rectP(-0.08f, -0.12f, 0.02f, -0.03f, 0.02f), BLACK)
        tint(rectP(-0.42f, -0.12f, -0.14f, -0.08f, 0.02f), 0x88FFFFFF.toInt())
    }

    val cupTrophy = trophy(GOLD, 0xFF1D3B2A.toInt())

    val cards: StickerArt = {
        c.withRotation(-14f, x(-0.12f), y(0f)) { shape(rectP(-0.32f, -0.3f, 0.08f, 0.3f, 0.04f), CARD_Y) }
        c.withRotation(12f, x(0.12f), y(0.04f)) {
            shape(rectP(-0.06f, -0.26f, 0.32f, 0.34f, 0.04f), RED)
            tint(rectP(-0.02f, -0.22f, 0.04f, 0.3f), 0x33FFFFFF)
        }
    }

    val scoreboard: StickerArt = {
        shape(rectP(-0.47f, -0.28f, 0.47f, 0.28f, 0.06f), 0xFF0F1720.toInt())
        tint(rectP(-0.43f, -0.24f, 0.43f, -0.12f, 0.03f), PITCH)
        paLabel("2-1", 0f, 0.07f, 0.26f, if (colorful) CARD_Y else ink.line, mono = true)
    }

    val cornerFlag: StickerArt = {
        stroke(path { addArc(RectF(x(-0.55f), y(0.22f), x(-0.05f), y(0.72f)), 270f, 90f) }, lineW * 0.9f, if (colorful) WHITE else ink.line)
        shape(ovalP(-0.42f, 0.38f, -0.1f, 0.48f), PITCH)
        shape(rectP(-0.3f, -0.46f, -0.24f, 0.44f, 0.02f), WHITE)
        val flag = path { m(-0.24f, -0.44f); q(0.1f, -0.38f, 0.42f, -0.36f); q(0.12f, -0.22f, -0.24f, -0.12f); close() }
        shape(flag, CARD_Y)
        if (colorful) c.withSave { clipPath(flag); solid(poly(0.0f, -0.5f, 0.12f, -0.5f, -0.1f, 0f, -0.22f, 0f), RED); solid(poly(0.24f, -0.5f, 0.36f, -0.5f, 0.14f, 0f, 0.02f, 0f), RED) }
        stroke(flag)
    }
}

/** Card frames painted by pack A themes. */
internal object PackAFrames {
    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 0f) = PackAKit.paint(color, style, width)
    private fun alpha(color: Int, a: Float) = PackAKit.alpha(color, a)

    /** Rectangle with corners cut in pixel steps. */
    private fun stepped(r: RectF, p: Float) = Path().apply {
        val l = r.left; val t = r.top; val rr = r.right; val b = r.bottom
        moveTo(l + 2 * p, t); lineTo(rr - 2 * p, t); lineTo(rr - 2 * p, t + p); lineTo(rr - p, t + p); lineTo(rr - p, t + 2 * p); lineTo(rr, t + 2 * p)
        lineTo(rr, b - 2 * p); lineTo(rr - p, b - 2 * p); lineTo(rr - p, b - p); lineTo(rr - 2 * p, b - p); lineTo(rr - 2 * p, b)
        lineTo(l + 2 * p, b); lineTo(l + 2 * p, b - p); lineTo(l + p, b - p); lineTo(l + p, b - 2 * p); lineTo(l, b - 2 * p)
        lineTo(l, t + 2 * p); lineTo(l + p, t + 2 * p); lineTo(l + p, t + p); lineTo(l + 2 * p, t + p); close()
    }

    /** A grass-topped dirt block with pixel texture and stepped corners. */
    val pixelBlock = FramePainter { c, theme, card, _, d, seed ->
        val p = 3f * d
        val body = stepped(card, p)
        c.withSave { translate(p, p); drawPath(body, paint(0x66000000)) }
        c.drawPath(body, paint(theme.palette.background))
        val rnd = Random(seed + 101)
        val cell = p * 2
        val grassH = (cell * 3).coerceAtMost(card.height() * 0.3f)
        c.withSave {
            clipPath(body)
            var y = card.top
            while (y < card.bottom) {
                var x = card.left
                while (x < card.right) {
                    val roll = rnd.nextFloat()
                    if (roll < 0.18f) drawRect(x, y, x + cell, y + cell, paint(0xFF6A4630.toInt()))
                    else if (roll < 0.3f) drawRect(x, y, x + cell, y + cell, paint(0xFFA57A55.toInt()))
                    x += cell
                }
                y += cell
            }
            var x = card.left
            while (x < card.right) {
                val drip = cell * rnd.nextInt(0, 3)
                drawRect(x, card.top, x + cell, card.top + grassH + drip, paint(theme.palette.accent))
                if (rnd.nextFloat() < 0.4f) drawRect(x, card.top + cell * rnd.nextInt(0, 2), x + cell, card.top + cell * rnd.nextInt(1, 3), paint(0xFF83CF56.toInt()))
                if (drip > 0) drawRect(x, card.top + grassH + drip - p, x + cell, card.top + grassH + drip, paint(0xFF3F8A2A.toInt()))
                x += cell
            }
        }
        c.drawPath(body, paint(0xFF1C1A1E.toInt(), Paint.Style.STROKE, 1.8f * d).apply { strokeJoin = Paint.Join.MITER })
    }

    /** Terminal window: title bar with dots, scanlines, green outline and a cursor block. */
    val terminal = FramePainter { c, theme, card, r, d, _ ->
        val green = theme.palette.accent
        val body = Path().apply { addRoundRect(card, r, r, Path.Direction.CW) }
        c.drawPath(body, paint(theme.palette.background))
        c.withSave {
            clipPath(body)
            val bar = 9f * d
            drawRect(card.left, card.top, card.right, card.top + bar, paint(0xFF0B2A12.toInt()))
            drawLine(card.left, card.top + bar, card.right, card.top + bar, paint(alpha(green, 0.5f), Paint.Style.STROKE, 0.8f * d))
            for (i in 0 until 3) drawCircle(card.left + (7f + i * 6f) * d, card.top + bar / 2, 1.6f * d, paint(if (i == 0) green else alpha(green, 0.4f)))
            val tp = paint(alpha(green, 0.55f)).apply { textSize = 6f * d; typeface = PackAKit.typeface(mono = true, bold = false); textAlign = Paint.Align.RIGHT }
            drawText("~/home", card.right - 6f * d, card.top + bar / 2 + 2.1f * d, tp)
            val scan = paint(alpha(green, 0.06f), Paint.Style.STROKE, 1f * d)
            var y = card.top + bar + 2f * d
            while (y < card.bottom) { drawLine(card.left, y, card.right, y, scan); y += 3f * d }
            drawRect(card.right - 9f * d, card.bottom - 9f * d, card.right - 5f * d, card.bottom - 3.5f * d, paint(alpha(green, 0.85f)))
        }
        PackAKit.glow(c, body, alpha(green, 0.9f), 1.1f * d)
    }

    private val brickColors = intArrayOf(0xFFE3342F.toInt(), 0xFF2563EB.toInt(), 0xFF16A34A.toInt(), 0xFFEA580C.toInt())

    /** A studded toy brick: studs along the top, a darker base strip, color picked by slot. */
    val brick = FramePainter { c, theme, card, r, d, seed ->
        val col = brickColors[Math.floorMod(seed, brickColors.size)]
        val line = theme.palette.line
        val o = 3f * d
        c.drawRoundRect(RectF(card.left + o, card.top + o, card.right + o, card.bottom + o), r, r, paint(alpha(line, 0.7f)))
        val sw = 9f * d
        val n = ((card.width() - 8f * d) / (sw * 1.9f)).toInt().coerceAtLeast(2)
        val gap = (card.width() - n * sw) / n
        for (i in 0 until n) {
            val x = card.left + gap / 2 + i * (sw + gap)
            val sr = RectF(x, card.top - 4f * d, x + sw, card.top + 2f * d)
            c.drawRoundRect(sr, 1.5f * d, 1.5f * d, paint(col))
            c.drawRect(x + 1.5f * d, card.top - 3f * d, x + 3.5f * d, card.top, paint(0x55FFFFFF))
            c.drawRoundRect(sr, 1.5f * d, 1.5f * d, paint(line, Paint.Style.STROKE, 1.6f * d))
        }
        val body = Path().apply { addRoundRect(card, r, r, Path.Direction.CW) }
        c.drawPath(body, paint(col))
        c.withSave {
            clipPath(body)
            drawRect(card.left, card.bottom - 4f * d, card.right, card.bottom, paint(PackAKit.shade(col, 0.78f)))
            drawRect(card.left, card.top, card.right, card.top + 2.5f * d, paint(0x33FFFFFF))
            drawRect(card.left + 3f * d, card.top + 5f * d, card.left + 6f * d, card.bottom - 8f * d, paint(0x22FFFFFF))
        }
        c.drawPath(body, paint(line, Paint.Style.STROKE, 2.2f * d))
    }

    /** Angular plate: chamfered corners, a red edge accent, hazard stripes in the corner. */
    val tactical = FramePainter { c, theme, card, _, d, _ ->
        val pal = theme.palette
        val cut = 11f * d
        val plate = PackAKit.chamfer(card, cut, tl = true, tr = false, br = true, bl = false)
        c.withSave { translate(2.5f * d, 2.5f * d); drawPath(plate, paint(0x88000000.toInt())) }
        c.drawPath(plate, paint(pal.background))
        c.withSave {
            clipPath(plate)
            val hz = RectF(card.left + 6f * d, card.bottom - 7f * d, card.left + 6f * d + min(30f * d, card.width() * 0.3f), card.bottom - 3.5f * d)
            clipRect(hz)
            drawRect(hz, paint(pal.accent))
            var x = hz.left - hz.height()
            val stripe = paint(0xFF101012.toInt(), Paint.Style.STROKE, 1.6f * d).apply { strokeCap = Paint.Cap.BUTT }
            while (x < hz.right) { drawLine(x, hz.bottom, x + hz.height(), hz.top, stripe); x += 3.5f * d }
        }
        c.drawPath(plate, paint(alpha(pal.text, 0.22f), Paint.Style.STROKE, 1.1f * d))
        val accent = paint(pal.accent, Paint.Style.STROKE, 2.6f * d).apply { strokeCap = Paint.Cap.BUTT }
        c.drawLine(card.left, card.top + cut + 10f * d, card.left, card.top + cut, accent)
        c.drawLine(card.left, card.top + cut, card.left + cut, card.top, accent)
        c.drawLine(card.left + cut, card.top, card.left + cut + min(26f * d, card.width() * 0.3f), card.top, accent)
        c.drawLine(card.right - cut, card.bottom, card.right, card.bottom - cut, accent)
        val tick = paint(alpha(pal.text, 0.6f), Paint.Style.STROKE, 1f * d)
        for (i in 0 until 3) c.drawLine(card.right - (8f + i * 4f) * d, card.top + 4f * d, card.right - (8f + i * 4f) * d, card.top + 7f * d, tick)
    }

    /** Chunky cartoon panel: purple gradient, bold outline, offset shadow and a yellow gleam. */
    val battlePanel = FramePainter { c, theme, card, r, d, _ ->
        val pal = theme.palette
        val o = 4f * d
        c.drawRoundRect(RectF(card.left + o, card.top + o, card.right + o, card.bottom + o), r, r, paint(pal.line))
        c.drawRoundRect(card, r, r, paint(0xFF000000.toInt()).apply {
            shader = LinearGradient(0f, card.top, 0f, card.bottom, PackAKit.shade(pal.background, 1.18f), PackAKit.shade(pal.background, 0.7f), Shader.TileMode.CLAMP)
        })
        c.withSave {
            clipPath(Path().apply { addRoundRect(card, r, r, Path.Direction.CW) })
            drawRect(card.left, card.top, card.right, card.top + card.height() * 0.42f, paint(0x1AFFFFFF))
        }
        c.drawRoundRect(card, r, r, paint(pal.line, Paint.Style.STROKE, 3f * d))
        val inset = 5f * d
        val gleam = paint(pal.accent, Paint.Style.STROKE, 3f * d)
        val arc = RectF(card.left + inset, card.top + inset, card.left + inset + r * 1.4f, card.top + inset + r * 1.4f)
        c.drawArc(arc, 190f, 55f, false, gleam)
        c.drawPoint(card.left + inset + r * 1.0f, card.top + inset + 0.5f * d, gleam)
    }

    /** Dark card wrapped in a rainbow glow; the gradient's start turns with the slot. */
    val rgbGlow = FramePainter { c, theme, card, r, d, seed ->
        c.drawRoundRect(card, r, r, paint(theme.palette.background))
        c.withSave {
            clipPath(Path().apply { addRoundRect(card, r, r, Path.Direction.CW) })
            drawRect(card.left, card.top, card.right, card.bottom, paint(0xFF000000.toInt()).apply {
                shader = LinearGradient(0f, card.top, 0f, card.bottom, 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            })
        }
        val sweep = SweepGradient(card.centerX(), card.centerY(),
            intArrayOf(0xFFFF3B5C.toInt(), 0xFFFFD23F.toInt(), 0xFF2BFF88.toInt(), 0xFF00E5FF.toInt(), 0xFF3B7BFF.toInt(), 0xFFFF2BD6.toInt(), 0xFFFF3B5C.toInt()), null)
        sweep.setLocalMatrix(Matrix().apply { setRotate(seed * 47f, card.centerX(), card.centerY()) })
        val widths = floatArrayOf(7f, 4f, 1.8f)
        val alphas = intArrayOf(40, 90, 255)
        for (i in widths.indices) c.drawRoundRect(card, r, r, paint(0, Paint.Style.STROKE, widths[i] * d).apply { shader = sweep; alpha = alphas[i] })
    }

    /** Night-sky card with a little perspective grid along the bottom and a pink neon edge. */
    val synth = FramePainter { c, theme, card, r, d, _ ->
        val pal = theme.palette
        val body = Path().apply { addRoundRect(card, r, r, Path.Direction.CW) }
        c.drawPath(body, paint(0xFF000000.toInt()).apply {
            shader = LinearGradient(0f, card.top, 0f, card.bottom, pal.background, PackAKit.shade(pal.secondary, 0.55f), Shader.TileMode.CLAMP)
        })
        c.withSave {
            clipPath(body)
            val horizon = card.bottom - card.height() * 0.3f
            val grid = paint(alpha(pal.accent, 0.28f), Paint.Style.STROKE, 0.9f * d)
            for (k in 1..5) { val t = k / 5f; val y = horizon + (card.bottom - horizon) * t * t; drawLine(card.left, y, card.right, y, grid) }
            val cx = card.centerX()
            for (k in -8..8) drawLine(cx + k * 4f * d, horizon, cx + k * 28f * d, card.bottom + 2f * d, grid)
            drawLine(card.left, horizon, card.right, horizon, paint(alpha(pal.secondary, 0.6f), Paint.Style.STROKE, 1f * d))
        }
        PackAKit.glow(c, body, pal.accent, 1.6f * d)
        val inset = 3.5f * d
        c.drawRoundRect(RectF(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset), r * 0.8f, r * 0.8f, paint(alpha(pal.secondary, 0.55f), Paint.Style.STROKE, 0.8f * d))
    }

    /** Heads-up display panel: chamfered corners, magenta glow, yellow corner brackets and ticks. */
    val hud = FramePainter { c, theme, card, _, d, _ ->
        val pal = theme.palette
        val cut = 10f * d
        val plate = PackAKit.chamfer(card, cut, tl = false, tr = true, br = false, bl = true)
        c.drawPath(plate, paint(pal.background))
        c.withSave {
            clipPath(plate)
            val scan = paint(alpha(pal.accent, 0.05f), Paint.Style.STROKE, 1f * d)
            var y = card.top
            while (y < card.bottom) { drawLine(card.left, y, card.right, y, scan); y += 4f * d }
        }
        PackAKit.glow(c, plate, pal.accent, 1.2f * d)
        val br = paint(pal.secondary, Paint.Style.STROKE, 2.2f * d).apply { strokeCap = Paint.Cap.SQUARE; strokeJoin = Paint.Join.MITER }
        val o = 2.5f * d; val len = 11f * d
        c.drawPath(Path().apply { moveTo(card.left - o, card.top - o + len); lineTo(card.left - o, card.top - o); lineTo(card.left - o + len, card.top - o) }, br)
        c.drawPath(Path().apply { moveTo(card.right + o, card.bottom + o - len); lineTo(card.right + o, card.bottom + o); lineTo(card.right + o - len, card.bottom + o) }, br)
        val tick = paint(alpha(pal.secondary, 0.8f), Paint.Style.STROKE, 1f * d)
        for (i in 0 until 4) c.drawLine(card.right - cut - (6f + i * 4f) * d, card.top + 3f * d, card.right - cut - (6f + i * 4f) * d, card.top + (if (i == 0) 7f else 5f) * d, tick)
        c.drawRect(card.left - 1.2f * d, card.centerY() - 6f * d, card.left + 1.2f * d, card.centerY() + 6f * d, paint(pal.secondary))
    }

    /** Stadium scoreboard: dark panel with a striped pitch header and corner bolts. */
    val scoreboard = FramePainter { c, theme, card, r, d, _ ->
        val pal = theme.palette
        val o = 2.5f * d
        c.drawRoundRect(RectF(card.left, card.top + o, card.right, card.bottom + o), r, r, paint(0x66000000))
        val body = Path().apply { addRoundRect(card, r, r, Path.Direction.CW) }
        c.drawPath(body, paint(pal.background))
        val head = 9f * d
        c.withSave {
            clipPath(body)
            drawRect(card.left, card.top, card.right, card.top + head, paint(pal.accent))
            val band = 8f * d
            var x = card.left
            var k = 0
            while (x < card.right) { if (k % 2 == 0) drawRect(x, card.top, x + band, card.top + head, paint(0x1FFFFFFF)); x += band; k++ }
            drawLine(card.left, card.top + head + 1f * d, card.right, card.top + head + 1f * d, paint(0xDDFFFFFF.toInt(), Paint.Style.STROKE, 1f * d))
            val dots = paint(alpha(pal.secondary, 0.12f))
            var y = card.top + head + 6f * d
            while (y < card.bottom) { var dx = card.left + 4f * d; while (dx < card.right) { drawCircle(dx, y, 0.7f * d, dots); dx += 5f * d }; y += 5f * d }
        }
        c.drawPath(body, paint(0xFF34424F.toInt(), Paint.Style.STROKE, 1.6f * d))
        val bolt = paint(0xFFB8C2CC.toInt())
        c.drawCircle(card.left + 5f * d, card.top + head / 2, 1.5f * d, bolt)
        c.drawCircle(card.right - 5f * d, card.top + head / 2, 1.5f * d, bolt)
    }
}
