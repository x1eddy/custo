package app.custo.feature.widgetsets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import app.custo.feature.widgets.WidgetKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Colors a sticker is drawn with. [paper] is what the sticker sits on (used to hollow out line art). */
data class StickerInk(val style: InkStyle, val line: Int, val accent: Int, val secondary: Int, val paper: Int)

/**
 * Deterministic Canvas painters for widget-set frames and stickers. The same code draws the
 * widget backgrounds (as bitmaps sized to the widget), the in-app previews and the wallpapers.
 */
object ThemeArt {
    /** Biggest sticker on a widget, in dp. */
    const val MAX_STICKER_DP = 56f

    fun ink(theme: WidgetTheme) = StickerInk(theme.ink, theme.palette.line, theme.palette.accent, theme.palette.secondary, theme.palette.background)

    /** The card inside a widget of [w] × [h] px; the margin around it is room for stickers. */
    fun cardRect(theme: WidgetTheme, w: Float, h: Float, d: Float): RectF {
        val f = theme.frame
        return RectF(f.outerSide * d, f.outerTop * d, w - f.outerSide * d, h - f.outerBottom * d)
    }

    /** Frame + stickers for one widget, [d] = pixels per dp. */
    fun drawWidget(c: Canvas, theme: WidgetTheme, kind: WidgetKind?, slot: Int, w: Float, h: Float, d: Float) {
        val card = cardRect(theme, w, h, d)
        if (card.width() <= 2 || card.height() <= 2) return
        drawFrame(c, theme, card, d, seed = (kind?.ordinal ?: 0) * 31 + slot)
        val ink = ink(theme)
        val box = SetLayout.Box(card.left, card.top, card.right, card.bottom)
        theme.decorationsFor(kind, slot).forEach { dec ->
            val size = SetLayout.stickerSize(card.width(), card.height(), dec.scale, MAX_STICKER_DP * d * dec.scale.coerceAtLeast(1f))
            val (cx, cy) = SetLayout.stickerCenter(box, dec.anchor.x, dec.anchor.y, size, w, h)
            drawSticker(c, dec.sticker, cx, cy, size, ink, dec.rotate)
        }
    }

    /** A widget background bitmap sized to the widget, at a capped density to stay under the RemoteViews memory limit. */
    fun widgetBitmap(ctx: Context, theme: WidgetTheme, kind: WidgetKind?, slot: Int, wDp: Float, hDp: Float): Bitmap {
        val d = min(ctx.resources.displayMetrics.density, 2f)
        val w = (wDp.takeIf { it > 0 } ?: 200f).times(d).toInt().coerceIn(8, 1000)
        val h = (hDp.takeIf { it > 0 } ?: 200f).times(d).toInt().coerceIn(8, 1000)
        // If the widget is huge, shrink the density so both sides fit in 1000 px.
        val dd = min(d, min(1000f / (wDp.coerceAtLeast(1f)), 1000f / (hDp.coerceAtLeast(1f))))
        val bmp = createBitmap(w, h)
        drawWidget(Canvas(bmp), theme, kind, slot, w.toFloat(), h.toFloat(), dd)
        return bmp
    }

    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        this.color = color; this.style = style; strokeWidth = width
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private fun alpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    // ---------- Frames ----------

    fun drawFrame(c: Canvas, theme: WidgetTheme, card: RectF, d: Float, seed: Int = 0) {
        val p = theme.palette
        val r = min(theme.corner * d, min(card.width(), card.height()) / 2f)
        when (theme.frame) {
            FrameStyle.PLAIN -> {
                c.drawRoundRect(card, r, r, paint(p.background))
                c.drawRoundRect(card, r, r, paint(alpha(p.line, 0.5f), Paint.Style.STROKE, 1.5f * d))
            }
            FrameStyle.CARTOON -> {
                val o = 4f * d
                c.drawRoundRect(RectF(card.left + o, card.top + o, card.right + o, card.bottom + o), r, r, paint(p.line))
                c.drawRoundRect(card, r, r, paint(p.background))
                c.drawRoundRect(card, r, r, paint(p.line, Paint.Style.STROKE, 3f * d))
                shine(c, card, r, d)
            }
            FrameStyle.DRIP -> drip(c, theme, card, r, d, seed)
            FrameStyle.FROSTED -> frosted(c, theme, card, r, d, seed)
            FrameStyle.STICKY -> sticky(c, theme, card, d)
        }
    }

    /** Little white gleam in the top-left corner. */
    private fun shine(c: Canvas, card: RectF, r: Float, d: Float) {
        val sp = paint(0xCCFFFFFF.toInt(), Paint.Style.STROKE, 3f * d)
        val inset = 6f * d
        val arc = RectF(card.left + inset, card.top + inset, card.left + inset + r * 1.4f, card.top + inset + r * 1.4f)
        c.drawArc(arc, 190f, 50f, false, sp)
        c.drawPoint(card.left + inset + r * 0.95f, card.top + inset + 0.5f * d, sp)
    }

    private fun drip(c: Canvas, theme: WidgetTheme, card: RectF, r: Float, d: Float, seed: Int) {
        val p = theme.palette
        val clip = Path().apply { addRoundRect(card, r, r, Path.Direction.CW) }
        // Offset shadow first so the card reads as a chunky sticker.
        val o = 3f * d
        c.drawRoundRect(RectF(card.left + o, card.top + o, card.right + o, card.bottom + o), r, r, paint(alpha(p.line, 0.85f)))
        c.drawPath(clip, paint(p.background))
        val icingH = (card.height() * 0.24f).coerceIn(14f * d, 34f * d)
        val base = card.top + icingH * 0.5f
        val rnd = Random(seed + 7)
        val icing = Path().apply {
            moveTo(card.left - 2 * d, card.top - 2 * d)
            lineTo(card.right + 2 * d, card.top - 2 * d)
            lineTo(card.right + 2 * d, base)
            val n = (card.width() / (24f * d)).toInt().coerceAtLeast(3)
            val seg = (card.width() + 4 * d) / n
            var x = card.right + 2 * d
            repeat(n) { i ->
                val long = if (i % 2 == 0) 0.55f + rnd.nextFloat() * 0.45f else 0.15f + rnd.nextFloat() * 0.2f
                val depth = icingH * long
                val nx = x - seg
                cubicTo(x, base + depth * 1.35f, nx, base + depth * 1.35f, nx, base)
                x = nx
            }
            lineTo(card.left - 2 * d, card.top - 2 * d)
            close()
        }
        c.withSave {
            clipPath(clip)
            drawPath(icing, paint(p.accent))
            drawPath(icing, paint(p.line, Paint.Style.STROKE, 2.2f * d))
            // Sprinkles.
            val colors = intArrayOf(p.secondary, 0xFFFFFFFF.toInt(), p.line, 0xFF7FD3FF.toInt())
            val sp = paint(0, Paint.Style.STROKE, 2.2f * d)
            repeat((card.width() / (18f * d)).toInt().coerceIn(3, 14)) { i ->
                sp.color = colors[i % colors.size]
                val x = card.left + 8 * d + rnd.nextFloat() * (card.width() - 16 * d)
                val y = card.top + 3 * d + rnd.nextFloat() * (icingH * 0.4f)
                val a = rnd.nextFloat() * PI.toFloat()
                val l = 2.6f * d
                drawLine(x - cos(a) * l, y - sin(a) * l, x + cos(a) * l, y + sin(a) * l, sp)
            }
        }
        c.drawRoundRect(card, r, r, paint(p.line, Paint.Style.STROKE, 2.6f * d))
        shine(c, RectF(card.left, card.top + icingH * 0.6f, card.right, card.bottom), r, d)
    }

    private fun frosted(c: Canvas, theme: WidgetTheme, card: RectF, r: Float, d: Float, seed: Int) {
        val p = theme.palette
        val glass = paint(0).apply {
            shader = LinearGradient(card.left, card.top, card.right, card.bottom, alpha(p.background, 0.62f), alpha(p.secondary, 0.42f), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(card, r, r, glass)
        // Soft diagonal highlight band.
        val clip = Path().apply { addRoundRect(card, r, r, Path.Direction.CW) }
        c.withSave {
            clipPath(clip)
            val band = Path().apply {
                moveTo(card.left, card.top + card.height() * 0.15f)
                lineTo(card.left + card.width() * 0.35f, card.top)
                lineTo(card.left + card.width() * 0.55f, card.top)
                lineTo(card.left, card.top + card.height() * 0.6f)
                close()
            }
            drawPath(band, paint(0x22FFFFFF))
        }
        c.drawRoundRect(card, r, r, paint(0xCCFFFFFF.toInt(), Paint.Style.STROKE, 1.6f * d))
        // Snow cap: bumpy pile along the top with a few icicle drips.
        val rnd = Random(seed + 3)
        val top = card.top
        val snow = Path().apply {
            val start = card.left + r * 0.25f
            val end = card.right - r * 0.25f
            moveTo(start - 2 * d, top + r * 0.45f)
            val n = ((end - start) / (22f * d)).toInt().coerceAtLeast(2)
            val seg = (end - start) / n
            var x = start
            lineTo(x, top + 2 * d)
            repeat(n) {
                val hgt = (5f + rnd.nextFloat() * 6f) * d
                quadTo(x + seg / 2, top - hgt * 1.6f, x + seg, top + 1 * d)
                x += seg
            }
            lineTo(end + 2 * d, top + r * 0.45f)
            // Underside with drips, going back left.
            x = end
            val drips = (n * 1.5f).toInt().coerceAtLeast(3)
            val dseg = (end - start) / drips
            repeat(drips) { i ->
                val depth = if (i % 3 == 1) (6f + rnd.nextFloat() * 5f) * d else (2f + rnd.nextFloat() * 2f) * d
                val nx = x - dseg
                cubicTo(x, top + 4 * d + depth, nx, top + 4 * d + depth, nx, top + 4 * d)
                x = nx
            }
            close()
        }
        c.drawPath(snow, paint(0xFFFFFFFF.toInt()))
        c.drawPath(snow, paint(0xFFB9D7EE.toInt(), Paint.Style.STROKE, 1.3f * d))
        val dots = paint(0xFFFFFFFF.toInt())
        repeat(5) {
            c.drawCircle(card.left + rnd.nextFloat() * card.width(), card.top + card.height() * (0.35f + rnd.nextFloat() * 0.6f), (0.8f + rnd.nextFloat()) * d, dots)
        }
    }

    private fun sticky(c: Canvas, theme: WidgetTheme, card: RectF, d: Float) {
        val p = theme.palette
        val r = 4f * d
        val fold = min(16f * d, min(card.width(), card.height()) * 0.18f)
        c.drawRoundRect(RectF(card.left + 1.5f * d, card.top + 3f * d, card.right + 1.5f * d, card.bottom + 3f * d), r, r, paint(0x30000000))
        val paper = Path().apply {
            moveTo(card.left + r, card.top)
            lineTo(card.right - r, card.top)
            quadTo(card.right, card.top, card.right, card.top + r)
            lineTo(card.right, card.bottom - fold)
            lineTo(card.right - fold, card.bottom)
            lineTo(card.left + r, card.bottom)
            quadTo(card.left, card.bottom, card.left, card.bottom - r)
            lineTo(card.left, card.top + r)
            quadTo(card.left, card.top, card.left + r, card.top)
            close()
        }
        c.drawPath(paper, paint(p.background))
        val curl = Path().apply {
            moveTo(card.right, card.bottom - fold)
            lineTo(card.right - fold, card.bottom)
            lineTo(card.right - fold * 0.9f, card.bottom - fold * 0.9f)
            close()
        }
        c.drawPath(curl, paint(p.secondary))
        val line = paint(p.line, Paint.Style.STROKE, 1.6f * d)
        c.drawPath(paper, line)
        c.drawPath(curl, line)
        // Hand-drawn inner dashes.
        val dash = paint(alpha(p.line, 0.45f), Paint.Style.STROKE, 1.2f * d).apply { pathEffect = DashPathEffect(floatArrayOf(5f * d, 4f * d), 0f) }
        val inset = 5f * d
        c.drawRoundRect(RectF(card.left + inset, card.top + inset, card.right - inset - fold * 0.3f, card.bottom - inset), r, r, dash)
        // Washi tape on the two top corners.
        val tapeL = min(38f * d, card.width() * 0.32f)
        tape(c, card.left + tapeL * 0.22f, card.top + 1.5f * d, tapeL, -38f, p, d)
        tape(c, card.right - tapeL * 0.22f, card.top + 1.5f * d, tapeL, 38f, p, d)
    }

    private fun tape(c: Canvas, cx: Float, cy: Float, len: Float, angle: Float, p: ThemePalette, d: Float) {
        val th = len * 0.32f
        c.withRotation(angle, cx, cy) {
            val rect = RectF(cx - len / 2, cy - th / 2, cx + len / 2, cy + th / 2)
            val tape = Path().apply {
                moveTo(rect.left, rect.top)
                lineTo(rect.right, rect.top)
                // Torn right edge.
                val teeth = 4
                for (i in 1..teeth) lineTo(rect.right - (if (i % 2 == 1) th * 0.12f else 0f), rect.top + rect.height() * i / teeth)
                lineTo(rect.left, rect.bottom)
                for (i in 1..teeth) lineTo(rect.left + (if (i % 2 == 1) th * 0.12f else 0f), rect.bottom - rect.height() * i / teeth)
                close()
            }
            drawPath(tape, paint(alpha(p.accent, 0.88f)))
            withSave {
                clipPath(tape)
                val sp = paint(alpha(p.secondary, 0.7f), Paint.Style.STROKE, th * 0.18f)
                var x = rect.left - th
                while (x < rect.right + th) { drawLine(x, rect.bottom, x + th, rect.top, sp); x += th * 0.55f }
            }
            drawPath(tape, paint(alpha(p.line, 0.5f), Paint.Style.STROKE, 0.9f * d))
        }
    }

    // ---------- Stickers ----------

    /** Draws [sticker] centered at ([cx], [cy]) in a box of [size] px. */
    fun drawSticker(c: Canvas, sticker: Sticker, cx: Float, cy: Float, size: Float, ink: StickerInk, rotate: Float = 0f) {
        if (size < 3f) return
        c.withRotation(rotate, cx, cy) { Pen(this, cx, cy, size, ink).draw(sticker) }
    }
}

/** Sticker drawing in unit coordinates: (0,0) is the center, ±0.5 the box edges. */
private class Pen(val c: Canvas, val cx: Float, val cy: Float, val s: Float, val ink: StickerInk) {
    val colorful = ink.style == InkStyle.COLOR
    val lineW = (s * if (colorful) 0.045f else 0.05f).coerceAtLeast(1.2f)
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = lineW; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = ink.line
    }

    fun x(u: Float) = cx + u * s
    fun y(v: Float) = cy + v * s
    fun r(rr: Float) = rr * s

    fun path(block: Path.() -> Unit) = Path().apply(block)
    fun Path.m(u: Float, v: Float) = moveTo(x(u), y(v))
    fun Path.l(u: Float, v: Float) = lineTo(x(u), y(v))
    fun Path.q(u1: Float, v1: Float, u: Float, v: Float) = quadTo(x(u1), y(v1), x(u), y(v))
    fun Path.cu(u1: Float, v1: Float, u2: Float, v2: Float, u: Float, v: Float) = cubicTo(x(u1), y(v1), x(u2), y(v2), x(u), y(v))

    /** Filled shape with an outline (color) or outline only (line art). */
    fun shape(p: Path, fill: Int) {
        if (colorful) { fillP.color = fill; c.drawPath(p, fillP) }
        c.drawPath(p, lineP)
    }

    /** Small solid detail (eyes, buttons): its own color in color mode, ink in line mode. */
    fun solid(p: Path, fill: Int = ink.line) {
        fillP.color = if (colorful) fill else ink.line
        c.drawPath(p, fillP)
    }

    /** Detail that only exists in color mode (highlights, blush). */
    fun tint(p: Path, fill: Int) {
        if (!colorful) return
        fillP.color = fill
        c.drawPath(p, fillP)
    }

    fun stroke(p: Path, width: Float = lineW, col: Int = ink.line) {
        val old = lineP.strokeWidth; val oc = lineP.color
        lineP.strokeWidth = width; lineP.color = col
        c.drawPath(p, lineP)
        lineP.strokeWidth = old; lineP.color = oc
    }

    fun circleP(u: Float, v: Float, rr: Float) = path { addCircle(x(u), y(v), r(rr), Path.Direction.CW) }
    fun ovalP(l: Float, t: Float, rr: Float, b: Float) = path { addOval(RectF(x(l), y(t), x(rr), y(b)), Path.Direction.CW) }
    fun rectP(l: Float, t: Float, rr: Float, b: Float, rad: Float = 0f) = path { addRoundRect(RectF(x(l), y(t), x(rr), y(b)), r(rad), r(rad), Path.Direction.CW) }
    fun poly(vararg pts: Float) = path {
        m(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { l(pts[i], pts[i + 1]); i += 2 }
        close()
    }

    fun eyes(u: Float, v: Float, gap: Float, rr: Float = 0.035f) {
        solid(circleP(u - gap, v, rr), DARK)
        solid(circleP(u + gap, v, rr), DARK)
        tint(circleP(u - gap + rr * 0.35f, v - rr * 0.35f, rr * 0.35f), WHITE)
        tint(circleP(u + gap + rr * 0.35f, v - rr * 0.35f, rr * 0.35f), WHITE)
    }

    fun smile(u: Float, v: Float, w: Float) = stroke(path { m(u - w, v); q(u, v + w * 0.9f, u + w, v) }, lineW * 0.8f)

    fun heartPath(u: Float, v: Float, k: Float) = path {
        m(u, v + 0.38f * k)
        cu(u - 0.55f * k, v + 0.02f * k, u - 0.35f * k, v - 0.42f * k, u, v - 0.15f * k)
        cu(u + 0.35f * k, v - 0.42f * k, u + 0.55f * k, v + 0.02f * k, u, v + 0.38f * k)
        close()
    }

    fun starPath(u: Float, v: Float, outer: Float, inner: Float, points: Int = 5) = path {
        for (i in 0 until points * 2) {
            val rad = if (i % 2 == 0) outer else inner
            val a = -PI / 2 + i * PI / points
            val px = u + (cos(a) * rad).toFloat(); val py = v + (sin(a) * rad).toFloat()
            if (i == 0) m(px, py) else l(px, py)
        }
        close()
    }

    fun draw(st: Sticker) {
        when (st) {
            Sticker.HEART -> {
                shape(heartPath(0f, 0f, 1f), ink.accent)
                tint(ovalP(-0.26f, -0.2f, -0.12f, -0.08f), 0x88FFFFFF.toInt())
                if (colorful) { eyes(0f, 0.02f, 0.1f, 0.03f); smile(0f, 0.1f, 0.05f) }
            }
            Sticker.STAR -> {
                lineP.strokeJoin = Paint.Join.ROUND
                shape(starPath(0f, 0.03f, 0.48f, 0.22f), STAR_YELLOW)
                if (colorful) { eyes(0f, 0.05f, 0.08f, 0.03f); smile(0f, 0.12f, 0.05f) }
            }
            Sticker.SPARKLE -> {
                val sp = path { m(0f, -0.45f); q(0.06f, -0.06f, 0.45f, 0f); q(0.06f, 0.06f, 0f, 0.45f); q(-0.06f, 0.06f, -0.45f, 0f); q(-0.06f, -0.06f, 0f, -0.45f); close() }
                shape(sp, if (colorful) STAR_YELLOW else ink.line)
                val small = path { m(0.32f, -0.42f); q(0.34f, -0.34f, 0.42f, -0.32f); q(0.34f, -0.3f, 0.32f, -0.22f); q(0.3f, -0.3f, 0.22f, -0.32f); q(0.3f, -0.34f, 0.32f, -0.42f); close() }
                shape(small, if (colorful) WHITE else ink.line)
            }
            Sticker.BOW -> {
                val left = path { m(0f, 0f); cu(-0.15f, -0.32f, -0.52f, -0.28f, -0.46f, 0f); cu(-0.52f, 0.26f, -0.15f, 0.3f, 0f, 0f); close() }
                val right = path { m(0f, 0f); cu(0.15f, -0.32f, 0.52f, -0.28f, 0.46f, 0f); cu(0.52f, 0.26f, 0.15f, 0.3f, 0f, 0f); close() }
                shape(poly(-0.04f, 0.04f, -0.24f, 0.44f, -0.1f, 0.38f, 0f, 0.12f), ink.accent)
                shape(poly(0.04f, 0.04f, 0.24f, 0.44f, 0.1f, 0.38f, 0f, 0.12f), ink.accent)
                shape(left, ink.accent); shape(right, ink.accent)
                stroke(path { m(-0.12f, -0.05f); q(-0.24f, -0.02f, -0.3f, 0.06f) }, lineW * 0.7f)
                stroke(path { m(0.12f, -0.05f); q(0.24f, -0.02f, 0.3f, 0.06f) }, lineW * 0.7f)
                shape(ovalP(-0.1f, -0.1f, 0.1f, 0.1f), ink.accent)
                tint(ovalP(-0.38f, -0.12f, -0.26f, -0.04f), 0x66FFFFFF)
            }
            Sticker.FLOWER -> {
                for (i in 0 until 5) {
                    val a = -PI / 2 + i * 2 * PI / 5
                    shape(circleP((cos(a) * 0.24f).toFloat(), (sin(a) * 0.24f).toFloat(), 0.19f), PETAL)
                }
                shape(circleP(0f, 0f, 0.14f), STAR_YELLOW)
                if (colorful) { eyes(0f, -0.01f, 0.05f, 0.022f) }
            }
            Sticker.CLOVER -> {
                stroke(path { m(0f, 0.05f); q(0.05f, 0.3f, 0.18f, 0.46f) }, lineW * 1.6f, if (colorful) LEAF_DARK else ink.line)
                for (i in 0 until 4) {
                    c.withRotation(i * 90f + 45f, cx, cy) { shape(heartPath(0f, -0.2f, 0.5f), LEAF) }
                }
                tint(circleP(0f, 0f, 0.05f), LEAF_DARK)
            }
            Sticker.CANDY -> {
                shape(poly(-0.2f, 0f, -0.48f, -0.2f, -0.42f, 0f, -0.48f, 0.2f), ink.secondary)
                shape(poly(0.2f, 0f, 0.48f, -0.2f, 0.42f, 0f, 0.48f, 0.2f), ink.secondary)
                shape(ovalP(-0.24f, -0.18f, 0.24f, 0.18f), ink.accent)
                stroke(path { m(-0.1f, -0.16f); l(0.02f, 0.17f) }, lineW * 1.4f, if (colorful) WHITE else ink.line)
                stroke(path { m(0.06f, -0.17f); l(0.16f, 0.12f) }, lineW * 1.4f, if (colorful) WHITE else ink.line)
            }
            Sticker.LOLLIPOP -> {
                shape(rectP(-0.035f, 0.05f, 0.035f, 0.49f, 0.03f), STICK)
                shape(circleP(0f, -0.14f, 0.31f), ink.accent)
                val sw = path {
                    addArc(RectF(x(-0.22f), y(-0.36f), x(0.22f), y(0.08f)), 180f, 270f)
                    addArc(RectF(x(-0.13f), y(-0.27f), x(0.13f), y(-0.01f)), 0f, 270f)
                }
                stroke(sw, lineW * 1.2f, if (colorful) WHITE else ink.line)
            }
            Sticker.GUMBALL -> {
                shape(rectP(-0.08f, -0.49f, 0.08f, -0.41f, 0.03f), RED)
                shape(circleP(0f, -0.14f, 0.3f), GLASS)
                val balls = intArrayOf(RED, STAR_YELLOW, 0xFF6FCF97.toInt(), 0xFF7FB8FF.toInt(), PETAL, 0xFFFFA25F.toInt(), 0xFFB197FC.toInt())
                val pos = floatArrayOf(-0.14f, -0.06f, 0.0f, 0.0f, 0.14f, -0.05f, -0.07f, -0.2f, 0.08f, -0.2f, -0.17f, -0.25f, 0.18f, -0.22f)
                for (i in balls.indices) shape(circleP(pos[i * 2], pos[i * 2 + 1], 0.065f), balls[i])
                tint(ovalP(-0.2f, -0.36f, -0.08f, -0.26f), 0xAAFFFFFF.toInt())
                shape(poly(-0.2f, 0.14f, 0.2f, 0.14f, 0.28f, 0.47f, -0.28f, 0.47f), RED)
                solid(rectP(-0.06f, 0.25f, 0.06f, 0.34f, 0.02f), DARK)
            }
            Sticker.BALLOON -> {
                stroke(path { m(0f, 0.24f); cu(-0.1f, 0.32f, 0.1f, 0.4f, 0f, 0.49f) }, lineW * 0.8f)
                shape(poly(0f, 0.17f, -0.06f, 0.25f, 0.06f, 0.25f), ink.accent)
                shape(ovalP(-0.29f, -0.47f, 0.29f, 0.19f), ink.accent)
                tint(ovalP(-0.18f, -0.36f, -0.06f, -0.2f), 0x88FFFFFF.toInt())
            }
            Sticker.GIRL -> girl()
            Sticker.SNOWMAN -> {
                shape(circleP(0f, 0.23f, 0.25f), SNOW)
                shape(circleP(0f, -0.13f, 0.19f), SNOW)
                shape(rectP(-0.2f, 0.0f, 0.2f, 0.07f, 0.03f), RED)
                shape(rectP(0.05f, 0.03f, 0.14f, 0.22f, 0.02f), RED)
                shape(rectP(-0.13f, -0.49f, 0.13f, -0.29f, 0.02f), HAT)
                shape(rectP(-0.21f, -0.31f, 0.21f, -0.26f, 0.02f), HAT)
                tint(rectP(-0.13f, -0.35f, 0.13f, -0.31f), RED)
                eyes(0f, -0.16f, 0.07f, 0.028f)
                shape(poly(0f, -0.11f, 0.18f, -0.08f, 0f, -0.05f), CARROT)
                solid(circleP(0f, 0.16f, 0.025f), DARK)
                solid(circleP(0f, 0.26f, 0.025f), DARK)
                solid(circleP(0f, 0.36f, 0.025f), DARK)
                tint(circleP(-0.11f, -0.08f, 0.035f), 0x66FF8FA3)
            }
            Sticker.TREE -> {
                shape(rectP(-0.06f, 0.3f, 0.06f, 0.47f, 0.02f), TRUNK)
                shape(poly(0f, -0.05f, -0.4f, 0.33f, 0.4f, 0.33f), PINE)
                shape(poly(0f, -0.22f, -0.31f, 0.11f, 0.31f, 0.11f), PINE)
                shape(poly(0f, -0.38f, -0.22f, -0.08f, 0.22f, -0.08f), PINE)
                val orn = intArrayOf(RED, STAR_YELLOW, 0xFF7FB8FF.toInt(), RED, STAR_YELLOW)
                val op = floatArrayOf(-0.08f, -0.14f, 0.1f, 0.02f, -0.14f, 0.18f, 0.18f, 0.25f, 0.02f, 0.27f)
                for (i in orn.indices) solid(circleP(op[i * 2], op[i * 2 + 1], 0.04f), orn[i])
                shape(starPath(0f, -0.4f, 0.11f, 0.05f), STAR_YELLOW)
            }
            Sticker.SANTA -> {
                shape(circleP(0f, 0.04f, 0.24f), SKIN)
                val beard = path {
                    m(-0.29f, 0.03f); q(-0.31f, 0.32f, -0.12f, 0.4f); q(0f, 0.5f, 0.12f, 0.4f); q(0.31f, 0.32f, 0.29f, 0.03f)
                    q(0.15f, 0.18f, 0f, 0.14f); q(-0.15f, 0.18f, -0.29f, 0.03f); close()
                }
                shape(beard, SNOW)
                shape(ovalP(-0.16f, 0.1f, 0.0f, 0.18f), SNOW)
                shape(ovalP(0.0f, 0.1f, 0.16f, 0.18f), SNOW)
                shape(circleP(0f, 0.09f, 0.045f), 0xFFFF9AA2.toInt())
                eyes(0f, 0.01f, 0.09f, 0.03f)
                val hat = path { m(-0.28f, -0.1f); q(-0.1f, -0.56f, 0.34f, -0.36f); q(0.16f, -0.3f, 0.28f, -0.1f); close() }
                shape(hat, RED)
                shape(rectP(-0.33f, -0.17f, 0.33f, -0.05f, 0.06f), SNOW)
                shape(circleP(0.35f, -0.35f, 0.075f), SNOW)
                tint(circleP(-0.16f, 0.08f, 0.04f), 0x66FF8FA3)
                tint(circleP(0.16f, 0.08f, 0.04f), 0x66FF8FA3)
            }
            Sticker.STOCKING -> {
                val sock = path {
                    m(-0.14f, -0.3f); l(0.13f, -0.3f); l(0.13f, 0.18f)
                    cu(0.13f, 0.42f, -0.1f, 0.47f, -0.3f, 0.41f); cu(-0.45f, 0.36f, -0.43f, 0.17f, -0.28f, 0.15f); l(-0.14f, 0.11f); close()
                }
                shape(sock, RED)
                stroke(path { m(-0.13f, -0.08f); l(0.12f, -0.08f) }, lineW * 1.6f, if (colorful) PINE else ink.line)
                stroke(path { m(-0.13f, 0.04f); l(0.12f, 0.04f) }, lineW * 1.6f, if (colorful) PINE else ink.line)
                shape(rectP(-0.19f, -0.44f, 0.18f, -0.27f, 0.05f), SNOW)
                tint(ovalP(-0.38f, 0.2f, -0.24f, 0.38f), SNOW)
            }
            Sticker.BELL -> {
                val bell = path { m(-0.3f, 0.25f); q(-0.22f, 0.18f, -0.2f, -0.05f); q(-0.18f, -0.33f, 0f, -0.34f); q(0.18f, -0.33f, 0.2f, -0.05f); q(0.22f, 0.18f, 0.3f, 0.25f); close() }
                shape(circleP(0f, 0.33f, 0.07f), GOLD_DARK)
                shape(bell, GOLD)
                shape(rectP(-0.33f, 0.21f, 0.33f, 0.3f, 0.04f), GOLD)
                tint(ovalP(-0.13f, -0.24f, -0.05f, 0.08f), 0x88FFFFFF.toInt())
                shape(ovalP(-0.2f, -0.47f, -0.02f, -0.35f), RED)
                shape(ovalP(0.02f, -0.47f, 0.2f, -0.35f), RED)
                shape(circleP(0f, -0.38f, 0.045f), RED)
            }
            Sticker.GIFT -> {
                shape(rectP(-0.32f, -0.08f, 0.32f, 0.42f, 0.03f), ink.accent)
                shape(rectP(-0.37f, -0.2f, 0.37f, -0.06f, 0.03f), ink.accent)
                shape(rectP(-0.06f, -0.2f, 0.06f, 0.42f), ink.secondary)
                shape(ovalP(-0.28f, -0.4f, -0.02f, -0.2f), ink.secondary)
                shape(ovalP(0.02f, -0.4f, 0.28f, -0.2f), ink.secondary)
                shape(circleP(0f, -0.22f, 0.05f), ink.secondary)
            }
            Sticker.ENVELOPE -> {
                shape(rectP(-0.43f, -0.27f, 0.43f, 0.29f, 0.05f), PAPER)
                stroke(path { m(-0.4f, -0.24f); l(0f, 0.06f); l(0.4f, -0.24f) })
                stroke(path { m(-0.4f, 0.26f); l(-0.12f, 0.0f) }, lineW * 0.7f)
                stroke(path { m(0.4f, 0.26f); l(0.12f, 0.0f) }, lineW * 0.7f)
                shape(heartPath(0f, 0.05f, 0.32f), ink.accent)
            }
            Sticker.PHONE -> {
                shape(rectP(-0.23f, -0.43f, 0.23f, 0.43f, 0.08f), ink.accent)
                shape(rectP(-0.17f, -0.33f, 0.17f, 0.27f, 0.03f), SCREEN)
                solid(circleP(0f, 0.35f, 0.035f), WHITE)
                shape(heartPath(0f, -0.05f, 0.3f), RED)
            }
            Sticker.CAMERA -> {
                shape(rectP(-0.18f, -0.33f, 0.06f, -0.18f, 0.03f), ink.accent)
                shape(rectP(-0.43f, -0.22f, 0.43f, 0.3f, 0.08f), ink.accent)
                shape(circleP(0f, 0.04f, 0.2f), DARK)
                shape(circleP(0f, 0.04f, 0.12f), LENS)
                tint(circleP(-0.04f, 0f, 0.035f), WHITE)
                shape(rectP(0.24f, -0.16f, 0.35f, -0.09f, 0.02f), STAR_YELLOW)
            }
            Sticker.SNOWFLAKE -> {
                val col = if (colorful) ICE else ink.line
                val w = lineW * (if (colorful) 1.4f else 1f)
                for (i in 0 until 6) {
                    c.withRotation(i * 60f, cx, cy) {
                        stroke(path { m(0f, 0f); l(0f, -0.45f) }, w, col)
                        stroke(path { m(-0.11f, -0.36f); l(0f, -0.26f); l(0.11f, -0.36f) }, w, col)
                    }
                }
                solid(circleP(0f, 0f, 0.06f), col)
            }
            Sticker.CANDY_CANE -> {
                val cane = path {
                    m(0.12f, 0.45f); l(0.12f, -0.2f)
                    arcTo(RectF(x(-0.18f), y(-0.35f), x(0.12f), y(-0.05f)), 0f, -180f)
                    l(-0.18f, -0.08f)
                }
                val thick = s * 0.15f
                stroke(cane, thick + lineW * 2, ink.line)
                stroke(cane, thick, if (colorful) WHITE else ink.paper)
                val stripes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = thick; this.color = if (colorful) RED else ink.line
                    pathEffect = DashPathEffect(floatArrayOf(s * 0.07f, s * 0.07f), 0f)
                    alpha = if (colorful) 255 else 140
                }
                c.drawPath(cane, stripes)
            }
            Sticker.SHOE -> {
                val shoe = path {
                    m(-0.43f, 0.2f); l(-0.43f, 0.02f); q(-0.41f, -0.08f, -0.29f, -0.1f); l(-0.1f, -0.22f)
                    q(0f, -0.28f, 0.08f, -0.18f); l(0.22f, -0.02f); q(0.43f, 0.04f, 0.45f, 0.2f); close()
                }
                shape(shoe, ink.accent)
                shape(rectP(-0.45f, 0.17f, 0.47f, 0.3f, 0.06f), WHITE)
                for (i in 0..2) stroke(path { m(-0.18f + i * 0.08f, -0.17f + i * 0.04f); l(-0.1f + i * 0.08f, -0.08f + i * 0.04f) }, lineW * 0.8f)
            }
            Sticker.BATTERY -> {
                shape(rectP(0.31f, -0.08f, 0.4f, 0.1f, 0.02f), WHITE)
                shape(rectP(-0.38f, -0.22f, 0.33f, 0.24f, 0.07f), WHITE)
                shape(rectP(-0.3f, -0.14f, 0.1f, 0.16f, 0.03f), 0xFF6FCF97.toInt())
                solid(poly(0.04f, -0.12f, -0.06f, 0.03f, 0.0f, 0.03f, -0.04f, 0.14f, 0.07f, -0.02f, 0.01f, -0.02f), STAR_YELLOW)
            }
        }
    }

    private fun girl() {
        // Hair behind, buns, face, bangs, face details, bow.
        shape(circleP(-0.33f, -0.27f, 0.13f), HAIR)
        shape(circleP(0.33f, -0.27f, 0.13f), HAIR)
        shape(path { addRoundRect(RectF(x(-0.37f), y(-0.32f), x(0.37f), y(0.36f)), r(0.34f), r(0.3f), Path.Direction.CW) }, HAIR)
        shape(circleP(0f, 0.07f, 0.29f), SKIN)
        val bangs = path {
            m(-0.31f, 0.06f); cu(-0.34f, -0.34f, 0.34f, -0.34f, 0.31f, 0.06f)
            q(0.24f, -0.06f, 0.14f, -0.03f); q(0.06f, -0.12f, 0f, -0.04f); q(-0.08f, -0.13f, -0.14f, -0.03f); q(-0.24f, -0.07f, -0.31f, 0.06f); close()
        }
        shape(bangs, HAIR)
        eyes(0f, 0.12f, 0.11f, 0.04f)
        tint(ovalP(-0.25f, 0.17f, -0.13f, 0.24f), 0x77FF8FA3)
        tint(ovalP(0.13f, 0.17f, 0.25f, 0.24f), 0x77FF8FA3)
        smile(0f, 0.22f, 0.05f)
        // Little bow on the right bun.
        c.withSave {
            val k = 0.36f
            val bx = 0.3f; val by = -0.4f
            shape(path { m(bx, by); cu(bx - 0.15f * k, by - 0.3f * k, bx - 0.5f * k, by - 0.28f * k, bx - 0.46f * k, by); cu(bx - 0.5f * k, by + 0.26f * k, bx - 0.15f * k, by + 0.3f * k, bx, by); close() }, ink.accent)
            shape(path { m(bx, by); cu(bx + 0.15f * k, by - 0.3f * k, bx + 0.5f * k, by - 0.28f * k, bx + 0.46f * k, by); cu(bx + 0.5f * k, by + 0.26f * k, bx + 0.15f * k, by + 0.3f * k, bx, by); close() }, ink.accent)
            shape(circleP(bx, by, 0.04f), ink.accent)
        }
    }

    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val DARK = 0xFF3A2A2A.toInt()
        const val STAR_YELLOW = 0xFFFFD54F.toInt()
        const val PETAL = 0xFFFFB3C7.toInt()
        const val LEAF = 0xFF7BCB6B.toInt()
        const val LEAF_DARK = 0xFF4E9A45.toInt()
        const val STICK = 0xFFF3E3C3.toInt()
        const val RED = 0xFFE53945.toInt()
        const val GLASS = 0xFFE3F6FF.toInt()
        const val SNOW = 0xFFFDFEFF.toInt()
        const val HAT = 0xFF35364A.toInt()
        const val CARROT = 0xFFFF8A3D.toInt()
        const val TRUNK = 0xFF8D5A3B.toInt()
        const val PINE = 0xFF2E8B57.toInt()
        const val SKIN = 0xFFFFE1CC.toInt()
        const val HAIR = 0xFF5A3A2E.toInt()
        const val GOLD = 0xFFFFC94D.toInt()
        const val GOLD_DARK = 0xFFC9952B.toInt()
        const val PAPER = 0xFFFFF8EC.toInt()
        const val SCREEN = 0xFFDFF3FF.toInt()
        const val LENS = 0xFF5FA8E8.toInt()
        const val ICE = 0xFFBFE6FF.toInt()
    }
}
