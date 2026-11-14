package app.custo.feature.widgetsets

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ======================================================================
// Theme pack B: stickers and frames. Everything is original, drawn in code.
// ======================================================================

/** Shared drawing helpers for pack B. */
internal object PackB {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val INK = 0xFF15131A.toInt()

    fun union(vararg ps: Path): Path {
        val out = Path(ps[0])
        for (i in 1 until ps.size) out.op(ps[i], Path.Op.UNION)
        return out
    }

    fun minus(a: Path, b: Path): Path = Path(a).apply { op(b, Path.Op.DIFFERENCE) }

    fun alpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        this.color = color; this.style = style; strokeWidth = width
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    fun roundPath(r: RectF, rad: Float) = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }
}

/** Bold comic lettering, outlined in ink; in line mode the letters are hollow. */
internal fun Pen.word(t: String, u: Float, v: Float, k: Float, fill: Int, maxW: Float = 0.8f, italic: Boolean = true) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, if (italic) Typeface.BOLD_ITALIC else Typeface.BOLD)
        textSize = r(k); textAlign = Paint.Align.CENTER; strokeJoin = Paint.Join.ROUND
    }
    val mw = p.measureText(t)
    if (mw > r(maxW)) p.textSize *= r(maxW) / mw
    val by = y(v) + p.textSize * 0.36f
    p.style = Paint.Style.STROKE; p.strokeWidth = lineW * 1.4f; p.color = ink.line
    c.drawText(t, x(u), by, p)
    p.style = Paint.Style.FILL; p.color = if (colorful) fill else ink.paper
    c.drawText(t, x(u), by, p)
}

/** A jagged comic burst. */
internal fun Pen.burst(n: Int, outer: Float, inner: Float, u: Float = 0f, v: Float = 0f) = path {
    val k = floatArrayOf(1f, 0.84f, 0.96f, 0.78f, 1f, 0.9f, 0.82f)
    for (i in 0 until n * 2) {
        val rad = if (i % 2 == 0) outer * k[(i / 2) % k.size] else inner
        val a = -PI / 2 + i * PI / n
        val px = u + (cos(a) * rad).toFloat(); val py = v + (sin(a) * rad).toFloat()
        if (i == 0) m(px, py) else l(px, py)
    }
    close()
}

/** A dot in its own color even in line mode (for one tiny accent). */
internal fun Pen.dot(u: Float, v: Float, rr: Float, color: Int) {
    c.drawCircle(x(u), y(v), r(rr), PackB.paint(color))
}

/** Lightning bolt. */
internal fun Pen.bolt(fill: Int, k: Float = 1f) {
    shape(poly(0.1f * k, -0.5f * k, -0.3f * k, 0.06f * k, -0.03f * k, 0.06f * k, -0.14f * k, 0.5f * k, 0.32f * k, -0.1f * k, 0.05f * k, -0.1f * k, 0.2f * k, -0.5f * k), fill)
    tint(poly(0.08f * k, -0.42f * k, -0.18f * k, 0.0f, -0.1f * k, 0.0f), 0x88FFFFFF.toInt())
}

/** A crescent moon. */
internal fun Pen.crescent(fill: Int, k: Float = 1f) {
    val m = PackB.minus(circleP(0f, 0f, 0.42f * k), circleP(0.18f * k, -0.12f * k, 0.36f * k))
    shape(m, fill)
    tint(circleP(-0.22f * k, 0.12f * k, 0.045f * k), PackB.alpha(0xFF000000.toInt(), 0.12f))
    tint(circleP(-0.08f * k, 0.28f * k, 0.03f * k), PackB.alpha(0xFF000000.toInt(), 0.12f))
}

/** Little round cheeks. */
internal fun Pen.blush(u: Float, v: Float, gap: Float, rr: Float = 0.04f) {
    tint(ovalP(u - gap - rr, v - rr * 0.6f, u - gap + rr, v + rr * 0.6f), 0x88FF8FA3.toInt())
    tint(ovalP(u + gap - rr, v - rr * 0.6f, u + gap + rr, v + rr * 0.6f), 0x88FF8FA3.toInt())
}

/** Pack B sticker art, grouped by theme. */
internal object PackBArt {
    private const val YELLOW = 0xFFFFD43B.toInt()
    private const val RED = 0xFFE8282B.toInt()
    private const val BLUE = 0xFF1E6FD9.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DARK = 0xFF2A2230.toInt()

    // ---------- Comic hero ----------

    val pow: StickerArt = {
        shape(burst(11, 0.5f, 0.32f), YELLOW)
        tint(burst(11, 0.36f, 0.24f), 0xFFFF9F1C.toInt())
        word("POW!", 0f, 0f, 0.3f, WHITE, maxW = 0.66f)
    }

    val bam: StickerArt = {
        val bubble = PackB.union(ovalP(-0.48f, -0.4f, 0.48f, 0.22f), poly(-0.16f, 0.12f, -0.34f, 0.48f, 0.08f, 0.16f))
        shape(bubble, WHITE)
        word("BAM!", 0f, -0.09f, 0.3f, RED, maxW = 0.7f)
    }

    val zap: StickerArt = {
        shape(burst(9, 0.5f, 0.3f), BLUE)
        word("ZAP!", 0f, 0f, 0.28f, YELLOW, maxW = 0.62f)
    }

    val comicBolt: StickerArt = { bolt(YELLOW) }

    val mask: StickerArt = {
        stroke(path { m(-0.42f, -0.02f); q(-0.52f, 0.14f, -0.48f, 0.3f) }, lineW * 1.6f)
        stroke(path { m(-0.42f, -0.02f); q(-0.44f, 0.2f, -0.36f, 0.34f) }, lineW * 1.6f)
        val m = path {
            m(-0.46f, -0.1f)
            cu(-0.4f, -0.3f, -0.1f, -0.26f, 0f, -0.13f)
            cu(0.1f, -0.26f, 0.4f, -0.3f, 0.46f, -0.1f)
            cu(0.46f, 0.14f, 0.22f, 0.22f, 0.07f, 0.1f)
            q(0f, 0.04f, -0.07f, 0.1f)
            cu(-0.22f, 0.22f, -0.46f, 0.14f, -0.46f, -0.1f)
            close()
        }
        shape(m, BLUE)
        shape(path { m(-0.34f, -0.06f); q(-0.24f, -0.18f, -0.1f, -0.06f); q(-0.24f, 0.06f, -0.34f, -0.06f); close() }, WHITE)
        shape(path { m(0.34f, -0.06f); q(0.24f, -0.18f, 0.1f, -0.06f); q(0.24f, 0.06f, 0.34f, -0.06f); close() }, WHITE)
        tint(ovalP(0.2f, -0.2f, 0.36f, -0.14f), 0x66FFFFFF)
    }

    // ---------- Pirate crew ----------

    private const val STRAW = 0xFFF2C94C.toInt()
    private const val STRAW_DARK = 0xFFC99A2E.toInt()
    private const val WOOD = 0xFF9A5B34.toInt()
    private const val PARCH = 0xFFF5E1B0.toInt()
    private const val SEA = 0xFF3A8DDE.toInt()
    private const val TEAL = 0xFF2BB3A6.toInt()

    val strawHat: StickerArt = {
        shape(ovalP(-0.5f, -0.02f, 0.5f, 0.3f), STRAW)
        val dome = path { m(-0.29f, 0.1f); cu(-0.32f, -0.42f, 0.32f, -0.42f, 0.29f, 0.1f); q(0f, 0.18f, -0.29f, 0.1f); close() }
        shape(dome, STRAW)
        shape(path { m(-0.3f, 0.0f); q(0f, 0.08f, 0.3f, 0.0f); l(0.29f, 0.1f); q(0f, 0.18f, -0.29f, 0.1f); close() }, TEAL)
        val weave = if (colorful) STRAW_DARK else ink.line
        for (i in 0 until 5) {
            val a = -0.2f + i * 0.1f
            stroke(path { m(a, -0.16f); l(a + 0.03f, -0.05f) }, lineW * 0.6f, weave)
        }
        stroke(path { m(-0.4f, 0.2f); q(0f, 0.32f, 0.4f, 0.2f) }, lineW * 0.6f, weave)
    }

    val ship: StickerArt = {
        shape(rectP(-0.025f, -0.46f, 0.025f, 0.12f), WOOD)
        shape(poly(0.02f, -0.48f, 0.24f, -0.43f, 0.02f, -0.38f), DARK)
        shape(path { m(-0.3f, -0.34f); l(0.28f, -0.34f); q(0.38f, -0.14f, 0.3f, 0.04f); l(-0.3f, 0.04f); q(-0.22f, -0.15f, -0.3f, -0.34f); close() }, WHITE)
        tint(circleP(0f, -0.15f, 0.07f), DARK)
        shape(path { m(-0.48f, 0.08f); l(0.48f, 0.08f); q(0.42f, 0.3f, 0.28f, 0.34f); l(-0.3f, 0.34f); q(-0.44f, 0.28f, -0.48f, 0.08f); close() }, WOOD)
        stroke(path { m(-0.42f, 0.18f); l(0.42f, 0.18f) }, lineW * 0.6f)
        for (i in 0 until 4) shape(circleP(-0.24f + i * 0.16f, 0.25f, 0.03f), PARCH)
        stroke(path { m(-0.5f, 0.42f); q(-0.38f, 0.34f, -0.25f, 0.42f); q(-0.12f, 0.5f, 0f, 0.42f); q(0.12f, 0.34f, 0.25f, 0.42f); q(0.38f, 0.5f, 0.5f, 0.42f) }, lineW * 1.3f, if (colorful) SEA else ink.line)
    }

    val map: StickerArt = {
        shape(path { m(-0.38f, -0.32f); l(0.38f, -0.3f); l(0.4f, 0.3f); l(-0.36f, 0.33f); close() }, PARCH)
        shape(rectP(-0.47f, -0.36f, -0.35f, 0.37f, 0.06f), 0xFFE6C88A.toInt())
        shape(rectP(0.35f, -0.34f, 0.47f, 0.35f, 0.06f), 0xFFE6C88A.toInt())
        val trail = if (colorful) RED else ink.line
        val pts = floatArrayOf(-0.26f, 0.2f, -0.18f, 0.08f, -0.08f, 0.12f, 0.0f, 0.02f, 0.04f, -0.08f, 0.12f, -0.12f)
        var i = 0
        while (i < pts.size - 2) {
            stroke(path { m(pts[i], pts[i + 1]); l(pts[i] + (pts[i + 2] - pts[i]) * 0.55f, pts[i + 1] + (pts[i + 3] - pts[i + 1]) * 0.55f) }, lineW * 0.8f, trail)
            i += 2
        }
        stroke(path { m(0.14f, -0.22f); l(0.28f, -0.08f); m(0.28f, -0.22f); l(0.14f, -0.08f) }, lineW * 1.4f, trail)
        tint(path { m(-0.28f, -0.2f); q(-0.2f, -0.28f, -0.1f, -0.22f); q(-0.12f, -0.12f, -0.22f, -0.1f); close() }, 0x5536A26B)
    }

    val compass: StickerArt = {
        shape(circleP(0f, 0f, 0.46f), 0xFFE0A93B.toInt())
        shape(circleP(0f, 0f, 0.36f), PARCH)
        for (i in 0 until 8) {
            val a = i * PI / 4
            val l1 = if (i % 2 == 0) 0.26f else 0.3f
            stroke(path { m((cos(a) * l1).toFloat(), (sin(a) * l1).toFloat()); l((cos(a) * 0.34f).toFloat(), (sin(a) * 0.34f).toFloat()) }, lineW * 0.6f)
        }
        shape(poly(0f, -0.3f, 0.08f, 0f, -0.08f, 0f), RED)
        shape(poly(0f, 0.3f, 0.08f, 0f, -0.08f, 0f), DARK)
        shape(circleP(0f, 0f, 0.04f), 0xFFE0A93B.toInt())
        shape(rectP(-0.06f, -0.54f, 0.06f, -0.44f, 0.03f), 0xFFE0A93B.toInt())
    }

    /** An original crew flag: a skull in a tricorn hat over crossed cutlasses. */
    val jolly: StickerArt = {
        stroke(path { m(-0.44f, -0.48f); l(-0.44f, 0.5f) }, lineW * 1.8f, if (colorful) WOOD else ink.line)
        shape(path { m(-0.42f, -0.4f); q(0f, -0.5f, 0.46f, -0.4f); q(0.4f, -0.05f, 0.46f, 0.3f); q(0f, 0.2f, -0.42f, 0.3f); close() }, DARK)
        val bone = if (colorful) WHITE else ink.line
        stroke(path { m(-0.22f, 0.22f); l(0.26f, -0.12f) }, lineW * 1.3f, bone)
        stroke(path { m(-0.22f, -0.12f); l(0.26f, 0.22f) }, lineW * 1.3f, bone)
        stroke(path { m(-0.26f, 0.18f); l(-0.18f, 0.26f); m(0.22f, 0.18f); l(0.3f, 0.26f) }, lineW * 1.3f, bone)
        shape(PackB.union(circleP(0.02f, -0.04f, 0.15f), rectP(-0.07f, 0.0f, 0.11f, 0.14f, 0.03f)), WHITE)
        solid(circleP(-0.04f, -0.04f, 0.04f), DARK); solid(circleP(0.08f, -0.04f, 0.04f), DARK)
        solid(poly(0.02f, 0.02f, 0.0f, 0.06f, 0.04f, 0.06f), DARK)
        shape(path { m(-0.18f, -0.14f); q(0.02f, -0.2f, 0.22f, -0.14f); l(0.12f, -0.3f); q(0.02f, -0.24f, -0.08f, -0.3f); close() }, 0xFF5A3A6E.toInt())
        tint(circleP(0.02f, -0.2f, 0.025f), STRAW)
    }

    val wave: StickerArt = {
        val w = path {
            m(-0.5f, 0.36f)
            cu(-0.42f, -0.1f, -0.1f, -0.42f, 0.24f, -0.3f)
            cu(0.46f, -0.22f, 0.44f, 0.04f, 0.26f, 0.06f)
            cu(0.12f, 0.06f, 0.1f, -0.1f, 0.22f, -0.12f)
            cu(0.0f, -0.18f, -0.22f, 0.04f, -0.12f, 0.36f)
            close()
        }
        shape(w, SEA)
        tint(path { m(-0.34f, 0.2f); cu(-0.28f, -0.06f, -0.06f, -0.26f, 0.16f, -0.24f); cu(-0.08f, -0.16f, -0.24f, 0.0f, -0.24f, 0.24f); close() }, 0x66FFFFFF)
        shape(rectP(-0.5f, 0.32f, 0.5f, 0.44f, 0.06f), 0xFF2A6FB8.toInt())
    }

    // ---------- Cursed energy ----------

    private const val VIOLET = 0xFF7C5CFF.toInt()
    private const val ELECTRIC = 0xFF4FC3FF.toInt()
    private const val TALISMAN = 0xFFF4E9CF.toInt()
    private const val SEAL = 0xFFD7263D.toInt()

    val ofuda: StickerArt = {
        shape(rectP(-0.2f, -0.48f, 0.2f, 0.48f, 0.02f), TALISMAN)
        val red = if (colorful) SEAL else ink.line
        stroke(rectP(-0.15f, -0.43f, 0.15f, 0.43f), lineW * 0.5f, red)
        val brush = if (colorful) 0xFF1A1424.toInt() else ink.line
        stroke(path { m(-0.08f, -0.34f); l(0.08f, -0.32f) }, lineW * 1.4f, brush)
        stroke(path { m(0f, -0.36f); q(0.02f, -0.12f, -0.02f, 0.12f) }, lineW * 1.6f, brush)
        stroke(path { m(-0.09f, -0.16f); l(0.09f, -0.2f) }, lineW * 1.1f, brush)
        stroke(path { m(-0.07f, 0.0f); q(0f, 0.04f, 0.08f, -0.02f) }, lineW * 1.1f, brush)
        stroke(path { m(-0.06f, 0.2f); l(0.07f, 0.34f); m(0.07f, 0.2f); l(-0.06f, 0.34f) }, lineW * 1.0f, brush)
        solid(circleP(0f, 0.2f, 0.05f), SEAL)
    }

    val flame: StickerArt = {
        val outer = path { m(0f, 0.48f); cu(-0.42f, 0.46f, -0.44f, 0.0f, -0.2f, -0.2f); q(-0.18f, -0.02f, -0.06f, -0.04f); q(-0.16f, -0.32f, 0.08f, -0.5f); q(0.04f, -0.24f, 0.22f, -0.16f); cu(0.44f, -0.02f, 0.4f, 0.46f, 0f, 0.48f); close() }
        shape(outer, VIOLET)
        tint(path { m(0f, 0.42f); cu(-0.26f, 0.4f, -0.26f, 0.08f, -0.08f, -0.06f); q(-0.02f, 0.06f, 0.06f, 0.04f); cu(0.28f, 0.14f, 0.24f, 0.4f, 0f, 0.42f); close() }, ELECTRIC)
        tint(ovalP(-0.07f, 0.16f, 0.07f, 0.38f), 0xDDFFFFFF.toInt())
    }

    val eye: StickerArt = {
        if (colorful) c.drawCircle(cx, cy, r(0.46f), PackB.paint(PackB.alpha(VIOLET, 0.25f)))
        shape(path { m(-0.48f, 0f); q(0f, -0.44f, 0.48f, 0f); q(0f, 0.44f, -0.48f, 0f); close() }, 0xFFEDE8FF.toInt())
        shape(circleP(0f, 0f, 0.17f), VIOLET)
        tint(circleP(0f, 0f, 0.1f), ELECTRIC)
        solid(circleP(0f, 0f, 0.06f), 0xFF0E0B1E.toInt())
        tint(circleP(0.06f, -0.06f, 0.03f), WHITE)
        for (i in -2..2) stroke(path { m(i * 0.12f, -0.22f + kotlin.math.abs(i) * 0.03f); l(i * 0.16f, -0.32f + kotlin.math.abs(i) * 0.05f) }, lineW * 0.8f)
    }

    /** A raised two-finger hand sign, as one silhouette. */
    val handSign: StickerArt = {
        val hand = PackB.union(
            rectP(-0.17f, -0.5f, -0.03f, 0.05f, 0.07f),
            rectP(0.0f, -0.46f, 0.14f, 0.05f, 0.07f),
            rectP(-0.22f, -0.04f, 0.2f, 0.4f, 0.1f),
            rectP(-0.08f, 0.32f, 0.12f, 0.5f, 0.03f),
        )
        c.withRotation(-28f, x(-0.2f), y(0.14f)) { shape(rectP(-0.36f, 0.08f, -0.18f, 0.2f, 0.06f), 0xFF3A2F74.toInt()) }
        shape(hand, 0xFF3A2F74.toInt())
        stroke(path { m(-0.14f, 0.08f); l(0.16f, 0.08f); m(-0.14f, 0.18f); l(0.16f, 0.18f) }, lineW * 0.7f, if (colorful) VIOLET else ink.line)
        tint(rectP(-0.14f, -0.46f, -0.1f, -0.1f, 0.02f), PackB.alpha(ELECTRIC, 0.7f))
    }

    val swirl: StickerArt = {
        val sp = path {
            val n = 64
            for (i in 0..n) {
                val t = i / n.toFloat()
                val a = t * 3.2 * 2 * PI
                val rad = 0.03f + t * 0.42f
                val px = (cos(a) * rad).toFloat(); val py = (sin(a) * rad).toFloat()
                if (i == 0) m(px, py) else l(px, py)
            }
        }
        if (colorful) {
            stroke(sp, lineW * 3.2f, PackB.alpha(VIOLET, 0.35f))
            stroke(sp, lineW * 1.6f, VIOLET)
            stroke(sp, lineW * 0.6f, 0xFFE3DBFF.toInt())
        } else stroke(sp, lineW)
        for (i in 0 until 3) {
            val a = i * 2 * PI / 3 + 0.4
            tint(starPath((cos(a) * 0.44f).toFloat(), (sin(a) * 0.44f).toFloat(), 0.06f, 0.02f, 4), ELECTRIC)
        }
    }

    val splash: StickerArt = {
        val k = floatArrayOf(1f, 0.7f, 0.92f, 0.62f, 1.05f, 0.75f, 0.88f, 0.6f, 0.98f, 0.7f)
        val blob = path {
            for (i in 0 until k.size) {
                val a = i * 2 * PI / k.size
                val rad = 0.32f * k[i]
                val px = (cos(a) * rad).toFloat(); val py = (sin(a) * rad).toFloat()
                if (i == 0) m(px, py) else {
                    val ma = (i - 0.5) * 2 * PI / k.size
                    q((cos(ma) * 0.22f).toFloat(), (sin(ma) * 0.22f).toFloat(), px, py)
                }
            }
            val ma = (k.size - 0.5) * 2 * PI / k.size
            q((cos(ma) * 0.22f).toFloat(), (sin(ma) * 0.22f).toFloat(), 0.32f, 0f)
            close()
        }
        shape(blob, 0xFF2B1F5C.toInt())
        shape(circleP(0.4f, -0.26f, 0.05f), 0xFF2B1F5C.toInt())
        shape(circleP(-0.38f, 0.34f, 0.04f), 0xFF2B1F5C.toInt())
        shape(circleP(0.3f, 0.4f, 0.03f), 0xFF2B1F5C.toInt())
        tint(circleP(-0.06f, -0.06f, 0.1f), PackB.alpha(VIOLET, 0.7f))
    }

    // ---------- Rock ----------

    private const val ROCK_RED = 0xFFD7192A.toInt()
    private const val SILVER = 0xFFC9CED6.toInt()
    private const val BONE = 0xFFF6F1E7.toInt()

    val guitar: StickerArt = {
        c.withRotation(38f, cx, cy) {
            shape(rectP(-0.05f, -0.52f, 0.05f, -0.38f, 0.02f), DARK)
            shape(rectP(-0.03f, -0.4f, 0.03f, 0.1f), 0xFF8D5A3B.toInt())
            shape(PackB.union(circleP(0f, 0.3f, 0.2f), circleP(0f, 0.06f, 0.15f), poly(-0.15f, 0.1f, -0.22f, -0.06f, -0.08f, 0.02f), poly(0.15f, 0.1f, 0.2f, -0.12f, 0.08f, 0.0f)), ROCK_RED)
            shape(path { m(0.02f, 0.12f); q(0.18f, 0.18f, 0.12f, 0.38f); l(0.02f, 0.38f); close() }, BONE)
            solid(rectP(-0.08f, 0.1f, 0.08f, 0.15f, 0.02f), DARK)
            solid(rectP(-0.08f, 0.22f, 0.08f, 0.27f, 0.02f), DARK)
            shape(rectP(-0.07f, 0.38f, 0.07f, 0.42f), SILVER)
            for (i in -1..1) stroke(path { m(i * 0.015f, -0.38f); l(i * 0.015f, 0.4f) }, lineW * 0.3f, if (colorful) SILVER else ink.line)
            solid(circleP(0.1f, 0.38f, 0.025f), SILVER)
        }
    }

    val rockBolt: StickerArt = { bolt(ROCK_RED) }

    val skull: StickerArt = {
        shape(PackB.union(circleP(0f, -0.06f, 0.33f), rectP(-0.18f, 0.12f, 0.18f, 0.38f, 0.08f)), BONE)
        solid(ovalP(-0.2f, -0.12f, -0.04f, 0.06f), DARK)
        solid(ovalP(0.04f, -0.12f, 0.2f, 0.06f), DARK)
        tint(circleP(-0.09f, -0.06f, 0.03f), WHITE); tint(circleP(0.15f, -0.06f, 0.03f), WHITE)
        solid(poly(0f, 0.12f, -0.04f, 0.17f, 0.04f, 0.17f), DARK)
        for (i in -1..1) stroke(path { m(i * 0.07f, 0.26f); l(i * 0.07f, 0.36f) }, lineW * 0.7f)
        // Roses on top.
        listOf(-0.24f to -0.32f, 0.08f to -0.38f).forEachIndexed { i, (u, v) ->
            if (i == 0) shape(path { m(u - 0.12f, v + 0.1f); q(u - 0.2f, v - 0.06f, u - 0.04f, v + 0.02f); close() }, 0xFF3FA34D.toInt())
            shape(circleP(u, v, if (i == 0) 0.12f else 0.1f), if (i == 0) ROCK_RED else 0xFFFF6B8A.toInt())
            stroke(path { m(u - 0.05f, v); q(u, v - 0.06f, u + 0.05f, v); q(u, v + 0.06f, u - 0.02f, v + 0.01f) }, lineW * 0.6f)
        }
        shape(path { m(0.18f, -0.36f); q(0.34f, -0.42f, 0.32f, -0.26f); close() }, 0xFF3FA34D.toInt())
    }

    val amp: StickerArt = {
        shape(rectP(-0.42f, -0.38f, 0.42f, 0.42f, 0.06f), 0xFF2B2B2E.toInt())
        shape(rectP(-0.36f, -0.32f, 0.36f, -0.18f, 0.03f), SILVER)
        for (i in 0 until 4) solid(circleP(-0.24f + i * 0.13f, -0.25f, 0.035f), DARK)
        shape(rectP(-0.36f, -0.12f, 0.36f, 0.36f, 0.03f), 0xFF444449.toInt())
        val grille = if (colorful) 0xFF5E5E66.toInt() else ink.line
        var g = -0.3f
        while (g < 0.34f) { stroke(path { m(g, -0.1f); l(g + 0.06f, 0.34f) }, lineW * 0.4f, grille); g += 0.08f }
        shape(rectP(-0.12f, 0.04f, 0.12f, 0.12f, 0.02f), ROCK_RED)
    }

    val pick: StickerArt = {
        shape(path { m(0f, 0.46f); cu(-0.48f, -0.04f, -0.42f, -0.44f, 0f, -0.44f); cu(0.42f, -0.44f, 0.48f, -0.04f, 0f, 0.46f); close() }, ROCK_RED)
        shape(starPath(0f, -0.08f, 0.17f, 0.07f), SILVER)
        tint(ovalP(-0.28f, -0.34f, -0.12f, -0.24f), 0x66FFFFFF)
    }

    /** A studded leather heart. */
    val studHeart: StickerArt = {
        shape(heartPath(0f, 0.04f, 1f), 0xFF2B2B2E.toInt())
        val pts = floatArrayOf(-0.24f, -0.12f, -0.12f, -0.2f, 0.12f, -0.2f, 0.24f, -0.12f, -0.22f, 0.06f, 0.22f, 0.06f, -0.1f, 0.22f, 0.1f, 0.22f, 0f, 0.0f)
        var i = 0
        while (i < pts.size) { shape(circleP(pts[i], pts[i + 1], 0.045f), SILVER); tint(circleP(pts[i] - 0.012f, pts[i + 1] - 0.012f, 0.014f), WHITE); i += 2 }
    }

    // ---------- Music ----------

    private const val PINK = 0xFFFF8FB1.toInt()
    private const val SKY = 0xFF9FD8F5.toInt()
    private const val LILAC = 0xFFC3A6FF.toInt()
    private const val PEACH = 0xFFFFC59E.toInt()
    private const val MINT = 0xFF9FE6C4.toInt()

    val vinyl: StickerArt = {
        shape(circleP(0f, 0f, 0.48f), 0xFF26222E.toInt())
        val groove = if (colorful) 0xFF4A4458.toInt() else ink.line
        listOf(0.4f, 0.33f, 0.26f).forEach { stroke(circleP(0f, 0f, it), lineW * 0.35f, groove) }
        shape(circleP(0f, 0f, 0.17f), PINK)
        solid(circleP(0f, 0f, 0.035f), DARK)
        if (colorful) {
            stroke(path { addArc(RectF(x(-0.38f), y(-0.38f), x(0.38f), y(0.38f)), 200f, 40f) }, lineW * 0.9f, 0x66FFFFFF)
            stroke(path { addArc(RectF(x(-0.38f), y(-0.38f), x(0.38f), y(0.38f)), 20f, 30f) }, lineW * 0.9f, 0x44FFFFFF)
        }
    }

    val headphones: StickerArt = {
        val band = path { m(-0.36f, 0.08f); cu(-0.4f, -0.56f, 0.4f, -0.56f, 0.36f, 0.08f) }
        stroke(band, lineW * 3.4f)
        if (colorful) stroke(band, lineW * 1.6f, LILAC)
        shape(rectP(-0.48f, -0.04f, -0.24f, 0.38f, 0.1f), PINK)
        shape(rectP(0.24f, -0.04f, 0.48f, 0.38f, 0.1f), PINK)
        tint(rectP(-0.43f, 0.02f, -0.36f, 0.3f, 0.03f), 0x66FFFFFF)
        tint(rectP(0.29f, 0.02f, 0.36f, 0.3f, 0.03f), 0x66FFFFFF)
    }

    val notes: StickerArt = {
        val noteCol = 0xFF6A4FC2.toInt()
        shape(poly(-0.2f, -0.36f, 0.34f, -0.48f, 0.34f, -0.36f, -0.2f, -0.24f), noteCol)
        stroke(path { m(-0.18f, -0.3f); l(-0.18f, 0.22f) }, lineW * 1.2f)
        stroke(path { m(0.32f, -0.42f); l(0.32f, 0.12f) }, lineW * 1.2f)
        c.withRotation(-20f, x(-0.28f), y(0.26f)) { shape(ovalP(-0.4f, 0.16f, -0.16f, 0.36f), noteCol) }
        c.withRotation(-20f, x(0.22f), y(0.16f)) { shape(ovalP(0.1f, 0.06f, 0.34f, 0.26f), noteCol) }
        tint(circleP(-0.32f, 0.22f, 0.03f), 0x88FFFFFF.toInt())
    }

    val cassette: StickerArt = {
        shape(rectP(-0.48f, -0.32f, 0.48f, 0.32f, 0.06f), SKY)
        shape(rectP(-0.38f, -0.24f, 0.38f, 0.1f, 0.03f), 0xFFFFF6EA.toInt())
        stroke(path { m(-0.3f, -0.17f); l(0.3f, -0.17f) }, lineW * 0.5f, if (colorful) PINK else ink.line)
        shape(rectP(-0.22f, -0.08f, 0.22f, 0.06f, 0.06f), DARK)
        shape(circleP(-0.13f, -0.01f, 0.05f), WHITE)
        shape(circleP(0.13f, -0.01f, 0.05f), WHITE)
        shape(poly(-0.3f, 0.32f, -0.24f, 0.18f, 0.24f, 0.18f, 0.3f, 0.32f), LILAC)
    }

    val eq: StickerArt = {
        val hs = floatArrayOf(0.4f, 0.7f, 0.95f, 0.6f, 0.82f)
        val cols = intArrayOf(PINK, PEACH, 0xFFFFE07A.toInt(), MINT, SKY)
        for (i in 0 until 5) {
            val l = -0.46f + i * 0.19f
            shape(rectP(l, 0.46f - hs[i] * 0.9f, l + 0.14f, 0.46f, 0.05f), cols[i])
        }
    }

    // ---------- Galaxy ----------

    private const val NAVY = 0xFF1B1840.toInt()
    private const val GOLD = 0xFFFFC94D.toInt()

    val planet: StickerArt = {
        c.withRotation(-18f, cx, cy) {
            val ringBox = RectF(x(-0.5f), y(-0.13f), x(0.5f), y(0.13f))
            stroke(path { addOval(ringBox, Path.Direction.CW) }, lineW * 2.6f)
            if (colorful) stroke(path { addOval(ringBox, Path.Direction.CW) }, lineW * 1.2f, 0xFFFFE3A3.toInt())
            shape(circleP(0f, 0f, 0.28f), 0xFFFF9E6B.toInt())
            c.withClip(circleP(0f, 0f, 0.28f)) {
                tint(rectP(-0.3f, -0.12f, 0.3f, -0.05f), 0x55FFFFFF)
                tint(rectP(-0.3f, 0.06f, 0.3f, 0.12f), 0x33B23A48)
            }
            stroke(circleP(0f, 0f, 0.28f), lineW)
            stroke(path { addArc(ringBox, 0f, 180f) }, lineW * 2.6f)
            if (colorful) stroke(path { addArc(ringBox, 5f, 170f) }, lineW * 1.2f, 0xFFFFE3A3.toInt())
        }
    }

    val rocket: StickerArt = {
        shape(poly(-0.06f, 0.26f, 0f, 0.5f, 0.06f, 0.26f), 0xFFFF9F43.toInt())
        tint(poly(-0.03f, 0.26f, 0f, 0.4f, 0.03f, 0.26f), GOLD)
        shape(path { m(-0.13f, 0.06f); l(-0.26f, 0.3f); l(-0.12f, 0.26f); close() }, 0xFFFF5D73.toInt())
        shape(path { m(0.13f, 0.06f); l(0.26f, 0.3f); l(0.12f, 0.26f); close() }, 0xFFFF5D73.toInt())
        shape(path { m(0f, -0.48f); cu(0.2f, -0.32f, 0.2f, 0.1f, 0.13f, 0.28f); l(-0.13f, 0.28f); cu(-0.2f, 0.1f, -0.2f, -0.32f, 0f, -0.48f); close() }, 0xFFF4F6FF.toInt())
        c.withClip(path { m(0f, -0.48f); cu(0.2f, -0.32f, 0.2f, 0.1f, 0.13f, 0.28f); l(-0.13f, 0.28f); cu(-0.2f, 0.1f, -0.2f, -0.32f, 0f, -0.48f); close() }) {
            tint(rectP(-0.3f, -0.5f, 0.3f, -0.32f), 0xFFFF5D73.toInt())
        }
        stroke(path { m(-0.11f, -0.33f); q(0f, -0.3f, 0.11f, -0.33f) }, lineW * 0.8f)
        shape(circleP(0f, -0.1f, 0.075f), 0xFF7FE0FF.toInt())
        tint(circleP(-0.025f, -0.125f, 0.02f), WHITE)
        shape(rectP(-0.015f, 0.12f, 0.015f, 0.3f), 0xFFFF5D73.toInt())
    }

    val astronaut: StickerArt = {
        shape(rectP(-0.3f, -0.08f, 0.3f, 0.28f, 0.06f), 0xFFD8DCE8.toInt())
        c.withRotation(-30f, x(-0.24f), y(0.1f)) { shape(rectP(-0.4f, 0.04f, -0.16f, 0.18f, 0.07f), 0xFFF4F6FF.toInt()) }
        c.withRotation(30f, x(0.24f), y(0.1f)) { shape(rectP(0.16f, 0.04f, 0.4f, 0.18f, 0.07f), 0xFFF4F6FF.toInt()) }
        shape(rectP(-0.16f, 0.32f, -0.03f, 0.5f, 0.05f), 0xFFF4F6FF.toInt())
        shape(rectP(0.03f, 0.32f, 0.16f, 0.5f, 0.05f), 0xFFF4F6FF.toInt())
        shape(rectP(-0.2f, 0.04f, 0.2f, 0.4f, 0.1f), 0xFFF4F6FF.toInt())
        shape(rectP(-0.08f, 0.14f, 0.08f, 0.26f, 0.02f), 0xFFB9C2D9.toInt())
        solid(circleP(-0.035f, 0.2f, 0.022f), 0xFFFF5D73.toInt()); solid(circleP(0.035f, 0.2f, 0.022f), 0xFF7FE0FF.toInt())
        shape(circleP(0f, -0.2f, 0.27f), 0xFFF4F6FF.toInt())
        shape(rectP(-0.2f, -0.32f, 0.2f, -0.06f, 0.12f), 0xFF22305E.toInt())
        tint(rectP(-0.15f, -0.28f, -0.06f, -0.22f, 0.03f), 0xAAFFFFFF.toInt())
        tint(circleP(0.12f, -0.12f, 0.02f), 0x88FFFFFF.toInt())
    }

    val moon: StickerArt = { crescent(0xFFFFE9A8.toInt()) }

    val ufo: StickerArt = {
        if (colorful) shape(poly(-0.14f, 0.08f, 0.14f, 0.08f, 0.3f, 0.5f, -0.3f, 0.5f), PackB.alpha(0xFF7FE0FF.toInt(), 0.35f))
        shape(path { m(-0.2f, -0.04f); cu(-0.2f, -0.36f, 0.2f, -0.36f, 0.2f, -0.04f); close() }, 0xFFBDF1FF.toInt())
        tint(ovalP(-0.12f, -0.24f, -0.04f, -0.14f), 0xAAFFFFFF.toInt())
        shape(ovalP(-0.48f, -0.1f, 0.48f, 0.14f), 0xFFA88BFF.toInt())
        for (i in -2..2) solid(circleP(i * 0.17f, 0.02f, 0.035f), GOLD)
    }

    val twinkle: StickerArt = {
        shape(path { m(0f, -0.48f); q(0.06f, -0.06f, 0.48f, 0f); q(0.06f, 0.06f, 0f, 0.48f); q(-0.06f, 0.06f, -0.48f, 0f); q(-0.06f, -0.06f, 0f, -0.48f); close() }, GOLD)
        shape(circleP(0.34f, -0.34f, 0.06f), WHITE)
    }

    // ---------- Halloween ----------

    private const val PUMPKIN = 0xFFFF8A1F.toInt()
    private const val PUMPKIN_DARK = 0xFFE06A0C.toInt()
    private const val PURPLE = 0xFF6B3FA0.toInt()
    private const val NIGHT = 0xFF2E1A47.toInt()

    val pumpkin: StickerArt = {
        stroke(path { m(0f, -0.24f); q(0.0f, -0.4f, 0.1f, -0.46f) }, lineW * 2.4f)
        if (colorful) stroke(path { m(0f, -0.24f); q(0.0f, -0.4f, 0.1f, -0.46f) }, lineW * 1.1f, 0xFF4CAF50.toInt())
        shape(ovalP(-0.48f, -0.26f, -0.02f, 0.42f), PUMPKIN_DARK)
        shape(ovalP(0.02f, -0.26f, 0.48f, 0.42f), PUMPKIN_DARK)
        shape(ovalP(-0.28f, -0.3f, 0.28f, 0.44f), PUMPKIN)
        solid(poly(-0.2f, 0.02f, -0.1f, -0.12f, -0.02f, 0.02f), 0xFF4A1C05.toInt())
        solid(poly(0.02f, 0.02f, 0.1f, -0.12f, 0.2f, 0.02f), 0xFF4A1C05.toInt())
        solid(path { m(-0.2f, 0.14f); q(0f, 0.36f, 0.2f, 0.14f); l(0.12f, 0.2f); l(0.06f, 0.14f); l(0f, 0.2f); l(-0.06f, 0.14f); l(-0.12f, 0.2f); close() }, 0xFF4A1C05.toInt())
        tint(ovalP(-0.22f, -0.2f, -0.12f, -0.08f), 0x55FFFFFF)
    }

    val ghost: StickerArt = {
        val g = path {
            m(-0.32f, 0.36f); l(-0.32f, -0.08f); cu(-0.32f, -0.56f, 0.32f, -0.56f, 0.32f, -0.08f); l(0.32f, 0.36f)
            q(0.26f, 0.48f, 0.16f, 0.38f); q(0.08f, 0.48f, 0f, 0.38f); q(-0.08f, 0.48f, -0.16f, 0.38f); q(-0.26f, 0.48f, -0.32f, 0.36f); close()
        }
        shape(g, 0xFFFDFBFF.toInt())
        shape(path { m(-0.32f, 0.02f); q(-0.46f, 0.06f, -0.44f, 0.16f); q(-0.38f, 0.14f, -0.32f, 0.12f) }, 0xFFFDFBFF.toInt())
        eyes(0f, -0.08f, 0.1f, 0.045f)
        blush(0f, 0.02f, 0.17f)
        solid(ovalP(-0.035f, 0.02f, 0.035f, 0.1f), 0xFF3A2A3A.toInt())
    }

    val bat: StickerArt = {
        val wing = path {
            m(0f, -0.04f)
            q(0.24f, -0.3f, 0.5f, -0.18f)
            q(0.42f, -0.06f, 0.46f, 0.08f)
            q(0.36f, 0.0f, 0.3f, 0.12f)
            q(0.22f, 0.02f, 0.14f, 0.14f)
            q(0.06f, 0.06f, 0f, 0.1f)
            q(-0.06f, 0.06f, -0.14f, 0.14f)
            q(-0.22f, 0.02f, -0.3f, 0.12f)
            q(-0.36f, 0.0f, -0.46f, 0.08f)
            q(-0.42f, -0.06f, -0.5f, -0.18f)
            q(-0.24f, -0.3f, 0f, -0.04f)
            close()
        }
        shape(wing, NIGHT)
        shape(PackB.union(circleP(0f, -0.02f, 0.15f), poly(-0.13f, -0.08f, -0.12f, -0.26f, -0.03f, -0.14f), poly(0.13f, -0.08f, 0.12f, -0.26f, 0.03f, -0.14f)), NIGHT)
        if (colorful) {
            c.drawCircle(x(-0.055f), y(-0.03f), r(0.035f), PackB.paint(0xFFFFE07A.toInt()))
            c.drawCircle(x(0.055f), y(-0.03f), r(0.035f), PackB.paint(0xFFFFE07A.toInt()))
        }
        tint(poly(-0.03f, 0.06f, -0.01f, 0.1f, 0.01f, 0.06f), WHITE)
    }

    val candyCorn: StickerArt = {
        val tri = path { m(0f, -0.48f); q(0.12f, -0.4f, 0.32f, 0.3f); q(0.34f, 0.44f, 0.18f, 0.44f); l(-0.18f, 0.44f); q(-0.34f, 0.44f, -0.32f, 0.3f); q(-0.12f, -0.4f, 0f, -0.48f); close() }
        shape(tri, 0xFFFFF4E0.toInt())
        c.withClip(tri) {
            tint(rectP(-0.5f, -0.18f, 0.5f, 0.16f), PUMPKIN)
            tint(rectP(-0.5f, 0.16f, 0.5f, 0.5f), 0xFFFFD23F.toInt())
        }
        stroke(path { m(-0.25f, -0.18f); l(0.25f, -0.18f); m(-0.32f, 0.16f); l(0.32f, 0.16f) }, lineW * 0.5f)
        stroke(tri, lineW)
        tint(ovalP(-0.1f, -0.36f, -0.03f, -0.22f), 0x88FFFFFF.toInt())
    }

    val witchHat: StickerArt = {
        shape(ovalP(-0.5f, 0.2f, 0.5f, 0.44f), PURPLE)
        shape(path { m(-0.28f, 0.3f); q(-0.14f, -0.1f, -0.02f, -0.36f); q(0.1f, -0.52f, 0.36f, -0.42f); q(0.14f, -0.36f, 0.14f, -0.12f); q(0.2f, 0.12f, 0.28f, 0.3f); q(0f, 0.38f, -0.28f, 0.3f); close() }, PURPLE)
        shape(path { m(-0.25f, 0.18f); q(0f, 0.26f, 0.25f, 0.18f); l(0.28f, 0.3f); q(0f, 0.38f, -0.28f, 0.3f); close() }, PUMPKIN)
        shape(rectP(-0.06f, 0.18f, 0.06f, 0.32f, 0.02f), 0xFFFFD23F.toInt())
        tint(starPath(0.06f, -0.12f, 0.06f, 0.025f), 0xFFFFD23F.toInt())
    }

    val spookyMoon: StickerArt = { crescent(0xFFFFE9A8.toInt()) }

    // ---------- Dinosaurs ----------

    private const val DINO_GREEN = 0xFF7CCB5B.toInt()
    private const val DINO_DARK = 0xFF4E9A3A.toInt()
    private const val DINO_ORANGE = 0xFFFFA24C.toInt()
    private const val DINO_BELLY = 0xFFFFF0C2.toInt()
    private const val LAVA = 0xFFFF6A2B.toInt()

    /** A long-neck dino. */
    val longNeck: StickerArt = {
        shape(path { m(-0.18f, 0.12f); q(-0.44f, 0.2f, -0.5f, 0.02f); q(-0.4f, 0.28f, -0.1f, 0.3f); close() }, DINO_GREEN)
        shape(rectP(-0.2f, 0.2f, -0.08f, 0.44f, 0.05f), DINO_GREEN)
        shape(rectP(0.1f, 0.2f, 0.22f, 0.44f, 0.05f), DINO_GREEN)
        shape(path { m(0.04f, 0.04f); q(0.1f, -0.24f, 0.18f, -0.34f); l(0.32f, -0.3f); q(0.24f, -0.16f, 0.24f, 0.1f); close() }, DINO_GREEN)
        shape(ovalP(-0.3f, -0.04f, 0.3f, 0.34f), DINO_GREEN)
        shape(ovalP(0.12f, -0.48f, 0.46f, -0.26f), DINO_GREEN)
        tint(circleP(-0.12f, 0.06f, 0.05f), DINO_DARK); tint(circleP(0.02f, 0.02f, 0.035f), DINO_DARK); tint(circleP(-0.02f, 0.14f, 0.04f), DINO_DARK)
        solid(circleP(0.34f, -0.39f, 0.03f), 0xFF2A2230.toInt())
        tint(circleP(0.4f, -0.33f, 0.03f), 0x88FF8FA3.toInt())
        stroke(path { m(0.36f, -0.31f); q(0.4f, -0.29f, 0.43f, -0.32f) }, lineW * 0.6f)
    }

    /** A round little upright dino with back plates. */
    val tinyRex: StickerArt = {
        for (i in 0 until 3) shape(poly(-0.24f + i * 0.02f, -0.12f + i * 0.16f, -0.38f + i * 0.02f, -0.04f + i * 0.16f, -0.24f + i * 0.04f, 0.04f + i * 0.16f), 0xFF8FD26A.toInt())
        shape(path { m(-0.18f, 0.28f); q(-0.46f, 0.34f, -0.5f, 0.2f); q(-0.38f, 0.44f, -0.06f, 0.42f); close() }, DINO_ORANGE)
        shape(rectP(-0.14f, 0.34f, -0.02f, 0.5f, 0.04f), DINO_ORANGE)
        shape(rectP(0.06f, 0.34f, 0.18f, 0.5f, 0.04f), DINO_ORANGE)
        shape(ovalP(-0.26f, -0.12f, 0.24f, 0.44f), DINO_ORANGE)
        shape(ovalP(-0.12f, 0.06f, 0.16f, 0.4f), DINO_BELLY)
        shape(PackB.union(ovalP(-0.16f, -0.48f, 0.38f, -0.04f), rectP(0.0f, -0.3f, 0.46f, -0.06f, 0.1f)), DINO_ORANGE)
        eyes(0.12f, -0.28f, 0.0f, 0.045f)
        stroke(path { m(0.18f, -0.14f); q(0.3f, -0.08f, 0.42f, -0.14f) }, lineW * 0.7f)
        solid(poly(0.24f, -0.12f, 0.27f, -0.07f, 0.3f, -0.11f), WHITE)
        shape(rectP(0.2f, 0.04f, 0.32f, 0.1f, 0.03f), DINO_ORANGE)
        blush(0.12f, -0.18f, 0.12f, 0.035f)
    }

    val egg: StickerArt = {
        shape(path { m(0f, -0.46f); cu(0.3f, -0.46f, 0.38f, 0.08f, 0.34f, 0.2f); cu(0.3f, 0.5f, -0.3f, 0.5f, -0.34f, 0.2f); cu(-0.38f, 0.08f, -0.3f, -0.46f, 0f, -0.46f); close() }, 0xFFFFF6DE.toInt())
        tint(circleP(-0.14f, 0.18f, 0.07f), 0xFF9AD77A.toInt()); tint(circleP(0.16f, 0.06f, 0.06f), 0xFF9AD77A.toInt()); tint(circleP(0.04f, 0.32f, 0.045f), 0xFF9AD77A.toInt())
        stroke(path { m(-0.3f, -0.08f); l(-0.18f, -0.18f); l(-0.08f, -0.06f); l(0.04f, -0.18f); l(0.14f, -0.06f); l(0.24f, -0.16f); l(0.32f, -0.06f) }, lineW * 0.9f)
        tint(ovalP(-0.2f, -0.36f, -0.08f, -0.22f), 0xAAFFFFFF.toInt())
    }

    val volcano: StickerArt = {
        shape(circleP(0.06f, -0.38f, 0.1f), 0xFFDCD6D0.toInt())
        shape(circleP(-0.08f, -0.3f, 0.08f), 0xFFE8E3DE.toInt())
        shape(circleP(0.2f, -0.28f, 0.08f), 0xFFE8E3DE.toInt())
        shape(path { m(-0.5f, 0.44f); l(-0.14f, -0.16f); q(0f, -0.2f, 0.14f, -0.16f); l(0.5f, 0.44f); close() }, 0xFF9C6B4E.toInt())
        shape(path { m(-0.14f, -0.16f); q(0f, -0.2f, 0.14f, -0.16f); l(0.2f, -0.06f); q(0.16f, 0.06f, 0.12f, -0.02f); q(0.08f, 0.16f, 0.02f, -0.02f); q(-0.04f, 0.1f, -0.08f, -0.04f); q(-0.14f, 0.04f, -0.2f, -0.06f); close() }, LAVA)
        tint(path { m(-0.3f, 0.44f); l(-0.06f, 0.04f); l(-0.02f, 0.44f); close() }, 0x22000000)
    }

    val leaf: StickerArt = {
        val lf = path { m(-0.36f, 0.42f); cu(-0.5f, -0.1f, -0.1f, -0.46f, 0.42f, -0.44f); cu(0.42f, 0.04f, 0.06f, 0.42f, -0.36f, 0.42f); close() }
        shape(lf, DINO_GREEN)
        val vein = if (colorful) DINO_DARK else ink.line
        stroke(path { m(-0.4f, 0.48f); q(-0.06f, 0.0f, 0.36f, -0.38f) }, lineW * 0.8f, vein)
        for (i in 0 until 4) {
            val t = 0.15f + i * 0.18f
            val bx = -0.3f + t * 0.66f; val by = 0.36f - t * 0.72f
            stroke(path { m(bx, by); l(bx - 0.12f, by - 0.06f) }, lineW * 0.5f, vein)
            stroke(path { m(bx, by); l(bx + 0.06f, by + 0.1f) }, lineW * 0.5f, vein)
        }
    }

    val footprint: StickerArt = {
        val col = 0xFF5C8F3E.toInt()
        shape(ovalP(-0.2f, -0.02f, 0.2f, 0.44f), col)
        shape(ovalP(-0.4f, -0.36f, -0.2f, -0.04f), col)
        shape(ovalP(-0.1f, -0.48f, 0.1f, -0.12f), col)
        shape(ovalP(0.2f, -0.36f, 0.4f, -0.04f), col)
    }

    // ---------- Ocean ----------

    private const val CORAL = 0xFFFF8C7A.toInt()
    private const val AQUA = 0xFF4FC3D0.toInt()
    private const val DEEP = 0xFF2A7FB8.toInt()

    val fish: StickerArt = {
        shape(path { m(0.18f, 0f); l(0.48f, -0.22f); q(0.4f, 0f, 0.48f, 0.22f); close() }, 0xFFFFB37A.toInt())
        val body = path { m(-0.46f, 0f); cu(-0.34f, -0.34f, 0.18f, -0.36f, 0.3f, 0f); cu(0.18f, 0.36f, -0.34f, 0.34f, -0.46f, 0f); close() }
        shape(body, CORAL)
        c.withClip(body) {
            tint(rectP(-0.12f, -0.4f, -0.04f, 0.4f), 0xEEFFFFFF.toInt())
            tint(rectP(0.1f, -0.4f, 0.17f, 0.4f), 0xEEFFFFFF.toInt())
        }
        stroke(body, lineW)
        stroke(path { m(-0.12f, -0.24f); l(-0.04f, 0.24f); m(0.1f, -0.24f); l(0.17f, 0.24f) }, lineW * 0.4f)
        shape(path { m(-0.08f, -0.24f); q(0.06f, -0.42f, 0.16f, -0.22f); close() }, 0xFFFFB37A.toInt())
        eyes(-0.28f, -0.05f, 0f, 0.045f)
        smile(-0.36f, 0.06f, 0.035f)
    }

    val jelly: StickerArt = {
        val tent = if (colorful) 0xFFB79CFF.toInt() else ink.line
        for (i in 0 until 4) {
            val u = -0.2f + i * 0.13f
            stroke(path { m(u, 0.06f); q(u - 0.06f, 0.18f, u, 0.28f); q(u + 0.06f, 0.38f, u, 0.48f) }, lineW * 0.9f, tent)
        }
        shape(path { m(-0.36f, 0.08f); cu(-0.38f, -0.5f, 0.38f, -0.5f, 0.36f, 0.08f); q(0.27f, 0.14f, 0.18f, 0.08f); q(0.09f, 0.14f, 0f, 0.08f); q(-0.09f, 0.14f, -0.18f, 0.08f); q(-0.27f, 0.14f, -0.36f, 0.08f); close() }, 0xFFD9C6FF.toInt())
        tint(ovalP(-0.24f, -0.3f, -0.1f, -0.16f), 0xAAFFFFFF.toInt())
        eyes(0f, -0.08f, 0.09f, 0.035f)
        blush(0f, -0.01f, 0.16f, 0.035f)
    }

    val whale: StickerArt = {
        val spout = if (colorful) AQUA else ink.line
        stroke(path { m(-0.08f, -0.2f); q(-0.1f, -0.36f, -0.22f, -0.44f); m(-0.08f, -0.2f); q(-0.06f, -0.38f, 0.06f, -0.46f) }, lineW * 0.9f, spout)
        shape(path { m(0.3f, 0.02f); q(0.4f, -0.04f, 0.42f, -0.2f); q(0.46f, -0.08f, 0.5f, -0.14f); q(0.48f, 0.06f, 0.36f, 0.12f); close() }, DEEP)
        val body = path { m(-0.48f, 0.06f); cu(-0.48f, -0.34f, 0.24f, -0.34f, 0.36f, 0.08f); cu(0.3f, 0.34f, -0.48f, 0.4f, -0.48f, 0.06f); close() }
        shape(body, 0xFF5AA9E6.toInt())
        c.withClip(body) { tint(ovalP(-0.5f, 0.1f, 0.3f, 0.5f), 0xFFDFF3FF.toInt()) }
        stroke(body, lineW)
        eyes(-0.26f, -0.02f, 0f, 0.04f)
        smile(-0.32f, 0.08f, 0.05f)
        blush(-0.26f, 0.06f, 0f, 0.035f)
        shape(path { m(-0.04f, 0.14f); q(0.06f, 0.3f, 0.16f, 0.24f); q(0.08f, 0.16f, -0.04f, 0.14f); close() }, DEEP)
    }

    val shell: StickerArt = {
        val fan = path { m(0f, 0.36f); l(-0.44f, -0.04f); cu(-0.38f, -0.5f, 0.38f, -0.5f, 0.44f, -0.04f); close() }
        shape(fan, 0xFFFFC7B3.toInt())
        for (i in -2..2) stroke(path { m(0f, 0.34f); l(i * 0.16f, -0.32f + kotlin.math.abs(i) * 0.06f) }, lineW * 0.6f)
        shape(rectP(-0.12f, 0.3f, 0.12f, 0.44f, 0.04f), 0xFFFFC7B3.toInt())
    }

    val starfish: StickerArt = {
        shape(starPath(0f, 0.04f, 0.48f, 0.22f), 0xFFFFA45C.toInt())
        for (i in 0 until 5) {
            val a = -PI / 2 + i * 2 * PI / 5
            tint(circleP((cos(a) * 0.26f).toFloat(), 0.04f + (sin(a) * 0.26f).toFloat(), 0.03f), 0xAAFFFFFF.toInt())
        }
        eyes(0f, 0.04f, 0.07f, 0.03f)
        smile(0f, 0.11f, 0.04f)
    }

    val coral: StickerArt = {
        val br = path {
            m(0f, 0.48f); l(0f, -0.1f); q(0f, -0.3f, -0.12f, -0.42f)
            m(0f, 0.12f); q(-0.24f, 0.08f, -0.3f, -0.24f)
            m(0f, 0.0f); q(0.2f, -0.02f, 0.24f, -0.36f)
            m(0.12f, -0.12f); q(0.3f, -0.12f, 0.38f, -0.2f)
            m(-0.18f, 0.06f); q(-0.36f, 0.08f, -0.42f, -0.04f)
        }
        stroke(br, lineW * 4f)
        if (colorful) stroke(br, lineW * 2.4f, CORAL)
    }

    val bubbles: StickerArt = {
        listOf(Triple(-0.1f, 0.18f, 0.26f), Triple(0.26f, -0.18f, 0.16f), Triple(-0.22f, -0.3f, 0.1f)).forEach { (u, v, rr) ->
            shape(circleP(u, v, rr), 0x66BFF3FF)
            tint(circleP(u - rr * 0.35f, v - rr * 0.35f, rr * 0.25f), 0xCCFFFFFF.toInt())
        }
    }

    // ---------- Mono (line art) ----------

    val monoCircle: StickerArt = {
        stroke(circleP(0f, 0f, 0.4f), lineW * 0.6f)
        dot(0.28f, -0.28f, 0.05f, ink.accent)
    }

    val monoArch: StickerArt = {
        stroke(path { m(-0.36f, 0.42f); l(-0.36f, 0f); cu(-0.36f, -0.48f, 0.36f, -0.48f, 0.36f, 0f); l(0.36f, 0.42f) }, lineW * 0.6f)
        stroke(path { m(-0.22f, 0.42f); l(-0.22f, 0.02f); cu(-0.22f, -0.3f, 0.22f, -0.3f, 0.22f, 0.02f); l(0.22f, 0.42f) }, lineW * 0.5f)
    }

    val monoSprig: StickerArt = {
        stroke(path { m(0f, 0.48f); q(0.04f, 0f, -0.04f, -0.46f) }, lineW * 0.6f)
        for (i in 0 until 4) {
            val v = 0.3f - i * 0.2f
            val side = if (i % 2 == 0) 1f else -1f
            stroke(path { m(0.01f, v); q(0.2f * side, v - 0.02f, 0.26f * side, v - 0.16f); q(0.08f * side, v - 0.16f, 0.01f, v) }, lineW * 0.5f)
        }
    }

    val monoStar: StickerArt = {
        for (i in 0 until 4) {
            val a = i * PI / 4
            stroke(path { m((cos(a) * 0.38f).toFloat(), (sin(a) * 0.38f).toFloat()); l((-cos(a) * 0.38f).toFloat(), (-sin(a) * 0.38f).toFloat()) }, lineW * 0.5f)
        }
    }

    val monoWave: StickerArt = {
        stroke(path { m(-0.48f, 0f); q(-0.36f, -0.2f, -0.24f, 0f); q(-0.12f, 0.2f, 0f, 0f); q(0.12f, -0.2f, 0.24f, 0f); q(0.36f, 0.2f, 0.48f, 0f) }, lineW * 0.6f)
    }

    val monoDot: StickerArt = { dot(0f, 0f, 0.2f, ink.accent) }
}

/** Pack B card frames. */
internal object PackBFrames {
    private val P = PackB

    private fun shadow(c: Canvas, card: RectF, r: Float, ox: Float, oy: Float, color: Int) {
        c.drawRoundRect(RectF(card.left + ox, card.top + oy, card.right + ox, card.bottom + oy), r, r, P.paint(color))
    }

    private fun halftone(c: Canvas, card: RectF, ox: Float, oy: Float, reach: Float, step: Float, color: Int) {
        val p = P.paint(color)
        var y = card.top
        while (y < card.bottom + step) {
            var x = card.left
            val shift = if (((y - card.top) / step).toInt() % 2 == 0) 0f else step / 2
            while (x < card.right + step) {
                val dist = kotlin.math.hypot(x + shift - ox, y - oy)
                val k = 1f - dist / reach
                if (k > 0.05f) c.drawCircle(x + shift, y, step * 0.42f * k, p)
                x += step
            }
            y += step
        }
    }

    /** Comic panel: thick ink, hard shadow, halftone dots in one corner. */
    val comic = FramePainter { c, t, card, r, d, seed ->
        val p = t.palette
        shadow(c, card, r, 5f * d, 5f * d, p.line)
        c.drawRoundRect(card, r, r, P.paint(p.background))
        val corner = seed % 2 == 0
        c.withClip(P.roundPath(card, r)) {
            halftone(this, card, if (corner) card.right else card.left, if (corner) card.top else card.bottom,
                min(card.width(), card.height()) * 0.9f, 7f * d, P.alpha(p.accent, 0.28f))
        }
        c.drawRoundRect(card, r, r, P.paint(p.line, Paint.Style.STROKE, 3.4f * d))
    }

    /** Wanted-poster parchment: ragged edges, burnt rim, two nail holes. */
    val poster = FramePainter { c, t, card, _, d, seed ->
        val p = t.palette
        val rnd = Random(seed * 13 + 5)
        val j = 2.2f * d
        val sheet = Path().apply {
            val stepX = 14f * d; val stepY = 14f * d
            moveTo(card.left, card.top)
            var x = card.left
            while (x < card.right) { x = min(x + stepX, card.right); lineTo(x, card.top + rnd.nextFloat() * j) }
            var y = card.top
            while (y < card.bottom) { y = min(y + stepY, card.bottom); lineTo(card.right - rnd.nextFloat() * j, y) }
            x = card.right
            while (x > card.left) { x = kotlin.math.max(x - stepX, card.left); lineTo(x, card.bottom - rnd.nextFloat() * j) }
            y = card.bottom
            while (y > card.top) { y = kotlin.math.max(y - stepY, card.top); lineTo(card.left + rnd.nextFloat() * j, y) }
            close()
        }
        c.withTranslation(2f * d, 3f * d) { drawPath(sheet, P.paint(0x40000000)) }
        c.drawPath(sheet, P.paint(p.background))
        c.withClip(sheet) {
            val burn = P.paint(0xFF000000.toInt()).apply {
                shader = android.graphics.RadialGradient(card.centerX(), card.centerY(), kotlin.math.hypot(card.width(), card.height()) * 0.55f,
                    intArrayOf(0x00000000, 0x00000000, P.alpha(0xFF8A5A2B.toInt(), 0.45f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            }
            drawRect(card, burn)
            // Fibres.
            val fib = P.paint(P.alpha(p.line, 0.08f), Paint.Style.STROKE, 0.8f * d)
            repeat(6) {
                val y0 = card.top + rnd.nextFloat() * card.height()
                drawLine(card.left + rnd.nextFloat() * card.width() * 0.5f, y0, card.left + card.width() * (0.5f + rnd.nextFloat() * 0.5f), y0 + rnd.nextFloat() * 4f * d, fib)
            }
        }
        c.drawPath(sheet, P.paint(P.alpha(p.line, 0.55f), Paint.Style.STROKE, 1.2f * d))
        val inset = 5f * d
        c.drawRect(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset, P.paint(P.alpha(p.line, 0.35f), Paint.Style.STROKE, 0.9f * d))
        // Nail holes.
        listOf(card.left + 9f * d, card.right - 9f * d).forEach { x ->
            val y = card.top + 9f * d
            c.drawCircle(x, y, 3.2f * d, P.paint(0xFF8A8F98.toInt()))
            c.drawCircle(x, y, 3.2f * d, P.paint(0xFF3B2414.toInt(), Paint.Style.STROKE, 0.9f * d))
            c.drawCircle(x - 0.9f * d, y - 0.9f * d, 1f * d, P.paint(0xCCFFFFFF.toInt()))
        }
    }

    /** Brushstroke frame with an energy glow. */
    val brush = FramePainter { c, t, card, r, d, seed ->
        val p = t.palette
        c.drawRoundRect(card, r, r, P.paint(0xFF000000.toInt()).apply {
            shader = LinearGradient(card.left, card.top, card.right, card.bottom, p.background, blend(p.background, p.accent, 0.22f), Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(card, r, r, P.paint(P.alpha(p.accent, 0.22f), Paint.Style.STROKE, 7f * d))
        val rnd = Random(seed * 7 + 1)
        fun pass(width: Float, color: Int, jit: Float) {
            val path = Path()
            val n = 28
            for (i in 0..n) {
                val tt = i / n.toFloat()
                val per = 2 * (card.width() + card.height())
                var s = tt * per
                val (x, y) = when {
                    s < card.width() -> card.left + s to card.top
                    s < card.width() + card.height() -> { s -= card.width(); card.right to card.top + s }
                    s < 2 * card.width() + card.height() -> { s -= card.width() + card.height(); card.right - s to card.bottom }
                    else -> { s -= 2 * card.width() + card.height(); card.left to card.bottom - s }
                }
                val jx = (rnd.nextFloat() - 0.5f) * jit; val jy = (rnd.nextFloat() - 0.5f) * jit
                if (i == 0) path.moveTo(x + jx, y + jy) else path.lineTo(x + jx, y + jy)
            }
            c.drawPath(path, P.paint(color, Paint.Style.STROKE, width))
        }
        c.withClip(RectF(card.left - 8 * d, card.top - 8 * d, card.right + 8 * d, card.bottom + 8 * d)) {
            pass(3.4f * d, p.accent, 2.4f * d)
            pass(1.8f * d, p.secondary, 2f * d)
            pass(0.8f * d, P.alpha(0xFFFFFFFF.toInt(), 0.7f), 1.4f * d)
        }
        // Brush flick and splatter at one corner.
        val fx = if (seed % 2 == 0) card.right else card.left
        val dir = if (seed % 2 == 0) -1f else 1f
        val flick = Path().apply { moveTo(fx + dir * 18f * d, card.bottom + 1f * d); quadTo(fx + dir * 6f * d, card.bottom - 1f * d, fx - dir * 3f * d, card.bottom - 10f * d) }
        c.drawPath(flick, P.paint(p.accent, Paint.Style.STROKE, 2.6f * d))
        repeat(4) { c.drawCircle(fx + dir * (4f + rnd.nextFloat() * 18f) * d, card.top + (2f + rnd.nextFloat() * 10f) * d, (0.8f + rnd.nextFloat() * 1.6f) * d, P.paint(P.alpha(p.accent, 0.9f))) }
    }

    /** Black leather with stitching and silver studs. */
    val leather = FramePainter { c, t, card, r, d, _ ->
        val p = t.palette
        shadow(c, card, r, 0f, 3f * d, 0x66000000)
        c.drawRoundRect(card, r, r, P.paint(0xFF000000.toInt()).apply {
            shader = LinearGradient(card.left, card.top, card.left, card.bottom, 0xFF2A2A2D.toInt(), p.background, Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(card, r, r, P.paint(P.alpha(p.secondary, 0.55f), Paint.Style.STROKE, 1.4f * d))
        val inset = 4.5f * d
        val stitch = P.paint(P.alpha(p.accent, 0.8f), Paint.Style.STROKE, 1.2f * d).apply { pathEffect = DashPathEffect(floatArrayOf(4f * d, 3f * d), 0f) }
        val ir = (r - inset).coerceAtLeast(2f * d)
        c.drawRoundRect(RectF(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset), ir, ir, stitch)
        // Studs along the top and bottom edges.
        val gap = 16f * d
        val n = ((card.width() - 2 * r) / gap).toInt().coerceAtLeast(1)
        val span = (card.width() - 2 * r) / n
        for (row in listOf(card.top, card.bottom)) for (i in 0..n) {
            val x = card.left + r + i * span
            c.drawCircle(x, row, 2.8f * d, P.paint(p.secondary))
            c.drawCircle(x, row, 2.8f * d, P.paint(0xFF0A0A0A.toInt(), Paint.Style.STROKE, 0.8f * d))
            c.drawCircle(x - 0.8f * d, row - 0.8f * d, 0.9f * d, P.paint(0xEEFFFFFF.toInt()))
        }
    }

    /** A record sleeve with a faint vinyl peeking from a corner. */
    val sleeve = FramePainter { c, t, card, r, d, seed ->
        val p = t.palette
        shadow(c, card, r, 0f, 3f * d, 0x22000000)
        c.drawRoundRect(card, r, r, P.paint(0xFF000000.toInt()).apply {
            shader = LinearGradient(card.left, card.top, card.right, card.bottom, p.background, blend(p.background, p.secondary, 0.25f), Shader.TileMode.CLAMP)
        })
        c.withClip(P.roundPath(card, r)) {
            val rad = min(card.width(), card.height()) * 0.6f
            val vx = if (seed % 2 == 0) card.right - rad * 0.2f else card.left + rad * 0.2f
            val vy = card.bottom + rad * 0.15f
            drawCircle(vx, vy, rad, P.paint(P.alpha(p.line, 0.07f)))
            var g = rad * 0.92f
            while (g > rad * 0.4f) { drawCircle(vx, vy, g, P.paint(P.alpha(p.line, 0.06f), Paint.Style.STROKE, 0.8f * d)); g -= 4f * d }
            drawCircle(vx, vy, rad * 0.3f, P.paint(P.alpha(p.accent, 0.3f)))
        }
        c.drawRoundRect(card, r, r, P.paint(P.alpha(p.line, 0.3f), Paint.Style.STROKE, 1.3f * d))
    }

    /** Deep-space card with tiny stars and a soft glow edge. */
    val starry = FramePainter { c, t, card, r, d, seed ->
        val p = t.palette
        c.drawRoundRect(RectF(card).apply { inset(-3f * d, -3f * d) }, r + 3f * d, r + 3f * d, P.paint(P.alpha(p.secondary, 0.18f)))
        c.drawRoundRect(card, r, r, P.paint(0xFF000000.toInt()).apply {
            shader = LinearGradient(card.left, card.top, card.right, card.bottom, p.background, 0xFF35206B.toInt(), Shader.TileMode.CLAMP)
        })
        val rnd = Random(seed * 11 + 3)
        c.withClip(P.roundPath(card, r)) {
            val n = (card.width() * card.height() / (220f * d * d)).toInt().coerceIn(6, 60)
            repeat(n) {
                val dot = P.paint(P.alpha(0xFFFFFFFF.toInt(), 0.3f + rnd.nextFloat() * 0.6f))
                drawCircle(card.left + rnd.nextFloat() * card.width(), card.top + rnd.nextFloat() * card.height(), (0.4f + rnd.nextFloat() * 0.9f) * d, dot)
            }
            drawCircle(card.right, card.top, min(card.width(), card.height()) * 0.5f, P.paint(0xFF000000.toInt()).apply {
                shader = android.graphics.RadialGradient(card.right, card.top, min(card.width(), card.height()) * 0.5f, P.alpha(0xFFB36BFF.toInt(), 0.35f), 0x00B36BFF, Shader.TileMode.CLAMP)
            })
        }
        c.drawRoundRect(card, r, r, P.paint(P.alpha(p.secondary, 0.4f), Paint.Style.STROKE, 3.5f * d))
        c.drawRoundRect(card, r, r, P.paint(P.alpha(0xFFFFFFFF.toInt(), 0.75f), Paint.Style.STROKE, 1.1f * d))
    }

    /** Sea glass: a light card with a wave along the bottom and rising bubbles. */
    val tide = FramePainter { c, t, card, r, d, seed ->
        val p = t.palette
        shadow(c, card, r, 0f, 3f * d, 0x2A0D3B4A)
        c.drawRoundRect(card, r, r, P.paint(0xFF000000.toInt()).apply {
            shader = LinearGradient(card.left, card.top, card.left, card.bottom, p.background, blend(p.background, p.secondary, 0.3f), Shader.TileMode.CLAMP)
        })
        c.withClip(P.roundPath(card, r)) {
            val base = card.bottom - min(card.height() * 0.2f, 14f * d)
            val waveH = 3.5f * d
            val wave = Path().apply {
                moveTo(card.left, card.bottom); lineTo(card.left, base)
                var x = card.left; val seg = 18f * d; var up = true
                while (x < card.right) { quadTo(x + seg / 2, base + if (up) -waveH else waveH, x + seg, base); x += seg; up = !up }
                lineTo(card.right + seg, card.bottom); close()
            }
            drawPath(wave, P.paint(P.alpha(p.secondary, 0.28f)))
            val rnd = Random(seed * 5 + 9)
            val bx = if (seed % 2 == 0) card.right - 12f * d else card.left + 12f * d
            repeat(3) { i ->
                val br = (2.2f + i * 1.1f) * d
                drawCircle(bx + (rnd.nextFloat() - 0.5f) * 8f * d, base - (8f + i * 11f) * d, br, P.paint(P.alpha(p.secondary, 0.7f), Paint.Style.STROKE, 1f * d))
            }
        }
        c.drawRoundRect(card, r, r, P.paint(P.alpha(p.secondary, 0.9f), Paint.Style.STROKE, 2f * d))
        val sp = P.paint(0xCCFFFFFF.toInt(), Paint.Style.STROKE, 2.5f * d)
        val inset = 5f * d
        c.drawArc(RectF(card.left + inset, card.top + inset, card.left + inset + r * 1.4f, card.top + inset + r * 1.4f), 190f, 50f, false, sp)
    }

    /** Editorial hairline card with crop marks and one tiny accent dot. */
    val hairline = FramePainter { c, t, card, _, d, seed ->
        val p = t.palette
        c.drawRect(card, P.paint(p.background))
        c.drawRect(card, P.paint(p.line, Paint.Style.STROKE, 0.9f * d))
        val m = P.paint(p.line, Paint.Style.STROKE, 0.7f * d)
        val o = 2f * d; val l = 4f * d
        listOf(card.left to card.top, card.right to card.top, card.left to card.bottom, card.right to card.bottom).forEach { (x, y) ->
            val sx = if (x == card.left) -1f else 1f; val sy = if (y == card.top) -1f else 1f
            c.drawLine(x + sx * o, y, x + sx * (o + l), y, m)
            c.drawLine(x, y + sy * o, x, y + sy * (o + l), m)
        }
        val inset = 7f * d
        val ax = if (seed % 2 == 0) card.right - inset else card.left + inset
        c.drawCircle(ax, card.top + inset, 2f * d, P.paint(p.accent))
    }

    private fun blend(a: Int, b: Int, k: Float): Int {
        fun ch(x: Int, sh: Int) = (x shr sh) and 0xFF
        fun mix(sh: Int) = (ch(a, sh) + (ch(b, sh) - ch(a, sh)) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}
