package app.custo.feature.widgetsets

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.withClip
import app.custo.feature.themes.WallEdge
import app.custo.feature.themes.WallSpec
import app.custo.feature.themes.WallStyle
import app.custo.feature.themes.WallpaperStudio
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetFonts
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetLogic
import app.custo.feature.widgets.WidgetStyle
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Art for theme pack D (Carbon): dark-glass pill cards with 1 px metal hairlines, a HUD that draws the
 * banner, battery ring, calendar and round app buttons, and the Carbon bands wallpaper.
 */
internal object PackDArt {
    const val GOLD = 0xFFD9B26A.toInt()
    const val SILVER = 0xFFD6DAE0.toInt()
    const val TEXT = 0xFFE4E4E8.toInt()
    private const val DIM = 0xFF8C8D94.toInt()
    private const val INK = 0xFF0E0F12.toInt()

    private fun pp(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        this.color = color; this.style = style; strokeWidth = width; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private fun fade(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    /** Wide cards become pills; squarer ones get soft corners. */
    private fun radius(card: RectF, d: Float): Float =
        if (card.width() > card.height() * 1.45f) card.height() / 2f else min(24f * d, min(card.width(), card.height()) / 2f)

    /** Dark smoked glass with a soft drop shadow, a faint top sheen and a 1 px metal hairline. */
    fun glassFrame(metal: Int) = FramePainter { c, _, card, _, d, _ ->
        val r = radius(card, d)
        val body = Path().apply { addRoundRect(card, r, r, Path.Direction.CW) }
        c.drawPath(Path(body).apply { offset(0f, 2.5f * d) }, pp(0x66000000))
        c.drawPath(body, pp(0).apply {
            shader = LinearGradient(0f, card.top, 0f, card.bottom, 0xDE1B1C21.toInt(), 0xF00A0A0D.toInt(), Shader.TileMode.CLAMP)
        })
        c.withClip(body) {
            drawRect(card.left, card.top, card.right, card.centerY(), pp(0).apply {
                shader = LinearGradient(0f, card.top, 0f, card.centerY(), 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            })
        }
        val hw = max(1f, 0.75f * d)
        val rim = RectF(card.left + hw / 2, card.top + hw / 2, card.right - hw / 2, card.bottom - hw / 2)
        c.drawRoundRect(rim, r - hw / 2, r - hw / 2, pp(0, Paint.Style.STROKE, hw).apply {
            shader = LinearGradient(card.left, card.top, card.right, card.bottom,
                intArrayOf(fade(metal, 0.95f), fade(metal, 0.45f), fade(metal, 0.9f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        })
        val i = 2.5f * d
        c.drawRoundRect(RectF(card.left + i, card.top + i, card.right - i, card.bottom - i), max(0f, r - i), max(0f, r - i), pp(0x0FFFFFFF, Paint.Style.STROKE, 0.8f * d))
    }

    /** A small four-point glint in the metal color. */
    fun glint(metal: Int): StickerArt = {
        val star = path { m(0f, -0.45f); q(0.05f, -0.05f, 0.45f, 0f); q(0.05f, 0.05f, 0f, 0.45f); q(-0.05f, 0.05f, -0.45f, 0f); q(-0.05f, -0.05f, 0f, -0.45f); close() }
        c.drawCircle(x(0f), y(0f), r(0.3f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x(0f), y(0f), r(0.3f), fade(metal, 0.45f), fade(metal, 0f), Shader.TileMode.CLAMP)
        })
        c.drawPath(star, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = metal })
    }

    /** The set's wallpaper: the Carbon bands premium style with this accent. */
    fun wallpaper(edge: WallEdge, seed: Int) = WallPainter { c, w, h ->
        WallpaperStudio.drawScene(c, WallSpec(WallStyle.CARBON, listOf(INK), seed = seed, angle = 8f, complexity = 0.6f, edge = edge), w, h, 0f)
    }

    /** Banner, battery ring and calendar drawn in the set's style; apps get round buttons with metal rims. */
    class Hud(private val metal: Int) : ThemeHud {
        override fun mode(kind: WidgetKind): HudMode? = when (kind) {
            WidgetKind.BANNER, WidgetKind.BATTERY_RING, WidgetKind.CALENDAR -> HudMode.REPLACE
            WidgetKind.APPS -> HudMode.SLOTS
            else -> null
        }

        override fun appSlots(apps: Int, w: Float, h: Float): Int =
            max(apps, min(5, floor(w / h.coerceAtLeast(1f)).toInt()).coerceAtLeast(1))

        override fun draw(c: Canvas, theme: WidgetTheme, kind: WidgetKind, style: WidgetStyle, data: HudData, card: RectF, content: RectF, d: Float) {
            when (kind) {
                WidgetKind.BANNER -> banner(c, style, content, d)
                WidgetKind.BATTERY_RING -> ring(c, data, content, d)
                WidgetKind.CALENDAR -> calendar(c, style, data, content, d)
                WidgetKind.APPS -> buttons(c, style, content, d)
                else -> Unit
            }
        }

        private fun text(size: Float, color: Int, face: Typeface, align: Paint.Align = Paint.Align.CENTER) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = face; textAlign = align
        }

        private fun Paint.fitTo(s: String, maxW: Float): Paint {
            val m = measureText(s)
            if (m > maxW && m > 0f) textSize *= maxW / m
            return this
        }

        private fun Paint.mid(y: Float): Float = y - (fontMetrics.ascent + fontMetrics.descent) / 2f

        /** Elegant serif words between two metal hairlines with small diamonds. */
        private fun banner(c: Canvas, style: WidgetStyle, r: RectF, d: Float) {
            val title = style.bannerText.ifBlank { " " }
            val sub = style.bannerSub
            val serif = WidgetFonts.typeface(WidgetFont.ELEGANT)
            val tp = text(r.height() * (if (sub.isBlank()) 0.62f else 0.5f), TEXT, serif).fitTo(title, r.width() * 0.72f)
            val cy = if (sub.isBlank()) r.centerY() else r.top + r.height() * 0.4f
            c.drawText(title, r.centerX(), tp.mid(cy), tp)
            val half = tp.measureText(title) / 2f
            val gap = 10f * d
            val hair = pp(0, Paint.Style.STROKE, max(1f, 0.75f * d))
            fun ornament(from: Float, to: Float) {
                if (kotlin.math.abs(to - from) < 12f * d) return
                hair.shader = LinearGradient(from, 0f, to, 0f, fade(metal, 0f), fade(metal, 0.95f), Shader.TileMode.CLAMP)
                c.drawLine(from, cy, to, cy, hair)
                hair.shader = null
                val s = 2.6f * d
                c.drawPath(Path().apply { moveTo(to, cy - s); lineTo(to + s, cy); lineTo(to, cy + s); lineTo(to - s, cy); close() }, pp(metal))
            }
            ornament(r.left + 4f * d, r.centerX() - half - gap)
            ornament(r.right - 4f * d, r.centerX() + half + gap)
            if (sub.isNotBlank()) {
                val sp = text(r.height() * 0.2f, fade(TEXT, 0.7f), WidgetFonts.typeface(WidgetFont.SANS)).apply { letterSpacing = 0.18f }
                    .fitTo(sub.uppercase(), r.width() * 0.8f)
                c.drawText(sub.uppercase(), r.centerX(), sp.mid(r.top + r.height() * 0.82f), sp)
            }
        }

        /** A thin metal ring; wide cards put the percentage beside it. */
        private fun ring(c: Canvas, data: HudData, r: RectF, d: Float) {
            val wide = r.width() > r.height() * 1.6f
            val dia = min(r.height(), r.width()) * 0.92f
            val cx = if (wide) r.left + dia / 2f + 4f * d else r.centerX()
            val cy = r.centerY()
            val sw = max(1.6f, 2.2f * d)
            val oval = RectF(cx - dia / 2 + sw, cy - dia / 2 + sw, cx + dia / 2 - sw, cy + dia / 2 - sw)
            c.drawOval(oval, pp(fade(metal, 0.18f), Paint.Style.STROKE, max(1f, 0.9f * d)))
            val sweep = 360f * data.battery.coerceIn(0, 100) / 100f
            c.drawArc(oval, -90f, sweep, false, pp(metal, Paint.Style.STROKE, sw))
            val a = Math.toRadians((sweep - 90f).toDouble())
            c.drawCircle(oval.centerX() + oval.width() / 2 * kotlin.math.cos(a).toFloat(), oval.centerY() + oval.height() / 2 * kotlin.math.sin(a).toFloat(), sw * 1.1f, pp(TEXT))
            val sans = Typeface.create("sans-serif-light", Typeface.NORMAL)
            val pct = "${data.battery}%"
            if (wide) {
                val left = cx + dia / 2 + 10f * d
                val big = text(dia * 0.42f, TEXT, sans, Paint.Align.LEFT).fitTo(pct, r.right - left)
                c.drawText(pct, left, big.mid(cy - dia * 0.08f), big)
                val label = if (data.charging) "CHARGING" else "BATTERY"
                val small = text(dia * 0.15f, metal, Typeface.DEFAULT, Paint.Align.LEFT).apply { letterSpacing = 0.2f }.fitTo(label, r.right - left)
                c.drawText(label, left, small.mid(cy + dia * 0.26f), small)
            } else {
                val big = text(dia * 0.26f, TEXT, sans).fitTo(pct, dia * 0.6f)
                c.drawText(pct, cx, big.mid(cy), big)
            }
        }

        /** Month in serif, the days in a light sans, today in a filled metal circle. */
        private fun calendar(c: Canvas, style: WidgetStyle, data: HudData, r: RectF, d: Float) {
            val weeks = WidgetLogic.monthGrid(data.today, style.mondayFirst)
            val titleH = min(r.height() * 0.17f, 22f * d)
            val title = data.today.format(DateTimeFormatter.ofPattern("MMMM yyyy", data.locale))
            val tp = text(titleH * 0.85f, TEXT, WidgetFonts.typeface(WidgetFont.ELEGANT)).fitTo(title, r.width())
            c.drawText(title, r.centerX(), tp.mid(r.top + titleH / 2f), tp)
            c.drawLine(r.centerX() - 14f * d, r.top + titleH + 1.5f * d, r.centerX() + 14f * d, r.top + titleH + 1.5f * d, pp(fade(metal, 0.8f), Paint.Style.STROKE, max(1f, 0.75f * d)))
            val top = r.top + titleH + 4f * d
            val rowH = (r.bottom - top) / (weeks.size + 1)
            val colW = r.width() / 7f
            val size = min(rowH * 0.6f, colW * 0.5f)
            val head = text(size * 0.85f, fade(TEXT, 0.5f), Typeface.DEFAULT)
            WidgetLogic.weekdayInitials(style.mondayFirst, data.locale).forEachIndexed { i, s ->
                c.drawText(s, r.left + colW * (i + 0.5f), head.mid(top + rowH / 2f), head)
            }
            val day = text(size, TEXT, Typeface.create("sans-serif-light", Typeface.NORMAL))
            val dot = pp(metal)
            weeks.forEachIndexed { wi, week ->
                week.forEachIndexed { i, n ->
                    if (n == 0) return@forEachIndexed
                    val x = r.left + colW * (i + 0.5f)
                    val y = top + rowH * (wi + 1.5f)
                    if (n == data.today.dayOfMonth) {
                        c.drawCircle(x, y, min(rowH, colW) * 0.46f, dot)
                        c.drawText("$n", x, day.mid(y), Paint(day).apply { color = INK; typeface = Typeface.DEFAULT_BOLD })
                    } else {
                        c.drawText("$n", x, day.mid(y), Paint(day).apply { if (n < data.today.dayOfMonth) color = DIM })
                    }
                }
            }
        }

        /** Round dark buttons with metal rims, one per app slot. */
        private fun buttons(c: Canvas, style: WidgetStyle, r: RectF, d: Float) {
            val n = appSlots(style.apps.take(6).size, r.width() / d, r.height() / d)
            val pitch = r.width() / n
            val size = min(pitch * 0.86f, r.height())
            for (i in 0 until n) {
                val cx = r.left + pitch * (i + 0.5f)
                val cy = r.centerY()
                val rad = size / 2f
                c.drawCircle(cx, cy + 1.5f * d, rad, pp(0x59000000))
                c.drawCircle(cx, cy, rad, pp(0).apply {
                    shader = RadialGradient(cx - rad * 0.3f, cy - rad * 0.4f, rad * 1.3f, 0xFF2C2D33.toInt(), 0xFF0B0B0E.toInt(), Shader.TileMode.CLAMP)
                })
                c.drawCircle(cx, cy, rad - 0.6f * d, pp(metal, Paint.Style.STROKE, max(1f, 1.1f * d)))
                c.drawCircle(cx, cy, rad - 2.6f * d, pp(0x14FFFFFF, Paint.Style.STROKE, 0.8f * d))
                if (style.apps.isEmpty()) {
                    val k = rad * 0.28f
                    val plus = pp(fade(metal, 0.7f), Paint.Style.STROKE, max(1f, 1.2f * d))
                    c.drawLine(cx - k, cy, cx + k, cy, plus); c.drawLine(cx, cy - k, cx, cy + k, plus)
                }
            }
        }
    }
}
