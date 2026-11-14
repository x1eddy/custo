package app.custo.feature.widgetsets

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetFonts
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetLogic
import app.custo.feature.widgets.WidgetStyle
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Battle Royale set: item-shop style rarity cards (radial burst, light rays, sheen, name plates),
 * heavy italic display type, game-UI widgets (shield + health bars, a tier pass bar, storm countdown,
 * an item quick-bar) and bold cartoon stickers. All art is original; no names, logos or characters.
 */
internal object Battle {
    const val INK = 0xFF120A30.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GOLD = 0xFFFFD23F.toInt()

    /** Card colors, from plain to most precious. */
    enum class Rarity(val light: Int, val center: Int, val edge: Int, val border: Int) {
        COMMON(0xFFD9E1EA.toInt(), 0xFFA5B1BF.toInt(), 0xFF56616F.toInt(), 0xFFEEF3F8.toInt()),
        UNCOMMON(0xFFB8FF8A.toInt(), 0xFF5ACB3A.toInt(), 0xFF17661F.toInt(), 0xFFC8FFAA.toInt()),
        RARE(0xFFA6EEFF.toInt(), 0xFF3FB0FF.toInt(), 0xFF14409E.toInt(), 0xFFB5ECFF.toInt()),
        EPIC(0xFFF0B8FF.toInt(), 0xFFB45BFF.toInt(), 0xFF4A1595.toInt(), 0xFFF2CCFF.toInt()),
        LEGENDARY(0xFFFFF0A0.toInt(), 0xFFFFAE2E.toInt(), 0xFFA9460C.toInt(), 0xFFFFE7A0.toInt()),
    }

    fun rarityFor(kind: WidgetKind?, slot: Int): Rarity = when (kind) {
        WidgetKind.BANNER, WidgetKind.DATE -> Rarity.LEGENDARY
        WidgetKind.CLOCK, WidgetKind.COUNTDOWN -> Rarity.EPIC
        WidgetKind.PHOTO, WidgetKind.CALENDAR -> Rarity.RARE
        WidgetKind.BATTERY, WidgetKind.BATTERY_RING -> Rarity.UNCOMMON
        WidgetKind.APPS -> Rarity.COMMON
        else -> Rarity.entries[Math.floorMod(slot + (kind?.ordinal ?: 0), Rarity.entries.size)]
    }

    private fun aa(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun line(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = w; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }

    private fun roundPath(r: RectF, rad: Float) = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }

    // ---------- Rarity card ----------

    /** An item-shop card: radial burst in the rarity color, light rays, a diagonal sheen, a bright inner border. */
    fun card(c: Canvas, r: RectF, rad: Float, d: Float, ra: Rarity, shadow: Boolean = true) {
        if (shadow) c.drawRoundRect(RectF(r.left, r.top + 3.5f * d, r.right, r.bottom + 3.5f * d), rad, rad, aa(0x8C120A30.toInt()))
        val clip = roundPath(r, rad)
        val cx = r.centerX(); val cy = r.top + r.height() * 0.4f
        val big = max(r.width(), r.height())
        c.withClip(clip) {
            drawRect(r, Paint().apply {
                shader = RadialGradient(cx, cy, big * 0.75f, intArrayOf(ra.light, ra.center, ra.edge), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            })
            // Light rays from the burst center.
            val rays = Path()
            val n = 18
            for (i in 0 until n) {
                val a0 = i * 2 * PI / n; val a1 = a0 + PI / n * 0.55
                rays.moveTo(cx, cy)
                rays.lineTo(cx + (cos(a0) * big).toFloat(), cy + (sin(a0) * big).toFloat())
                rays.lineTo(cx + (cos(a1) * big).toFloat(), cy + (sin(a1) * big).toFloat())
                rays.close()
            }
            drawPath(rays, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, big * 0.7f, intArrayOf(0x40FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            })
            // Diagonal sheen.
            val sheen = Path().apply {
                moveTo(r.left + r.width() * 0.08f, r.top); lineTo(r.left + r.width() * 0.34f, r.top)
                lineTo(r.left + r.width() * 0.08f, r.bottom); lineTo(r.left - r.width() * 0.18f, r.bottom); close()
            }
            drawPath(sheen, aa(0x14FFFFFF))
            // Darker bottom so text sitting low stays readable.
            drawRect(r, Paint().apply {
                shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(0x00000000, 0x00000000, 0x40000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            })
        }
        val i = 3.2f * d
        c.drawRoundRect(RectF(r.left + i, r.top + i, r.right - i, r.bottom - i), rad - i * 0.7f, rad - i * 0.7f, line(PackAKit.alpha(ra.border, 0.85f), 1.6f * d))
        c.drawRoundRect(r, rad, rad, line(INK, 2.4f * d))
    }

    /** A dark band across the bottom of a card, for the item name. Returns the band. */
    private fun namePlate(c: Canvas, card: RectF, rad: Float, top: Float, d: Float, ra: Rarity): RectF {
        val band = RectF(card.left, top, card.right, card.bottom)
        c.withClip(roundPath(card, rad)) {
            drawRect(band, Paint().apply { shader = LinearGradient(0f, band.top, 0f, band.bottom, 0xD9140A36.toInt(), 0xF20A0620.toInt(), Shader.TileMode.CLAMP) })
            drawRect(band.left, band.top, band.right, band.top + 1.6f * d, aa(PackAKit.alpha(ra.border, 0.9f)))
        }
        c.drawRoundRect(card, rad, rad, line(INK, 2.4f * d))
        return band
    }

    val frame = FramePainter { c, _, rect, r, d, seed ->
        val kind = WidgetKind.entries.getOrNull(seed / 31)
        if (kind == WidgetKind.APPS) return@FramePainter
        card(c, rect, r, d, rarityFor(kind, seed % 31))
    }

    // ---------- Heavy type ----------

    private fun hp(size: Float, color: Int = WHITE, align: Paint.Align = Paint.Align.CENTER) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = WidgetFonts.typeface(WidgetFont.HEAVY); textSize = size; this.color = color; textAlign = align; textSkewX = -0.14f
    }

    private fun Paint.fitTo(s: String, maxW: Float): Paint {
        val w = measureText(s)
        if (w > maxW && w > 0) textSize *= maxW / w
        return this
    }

    private fun Paint.mid(cy: Float) = cy - (fontMetrics.ascent + fontMetrics.descent) / 2

    /** Heavy text with a dark outline and a hard drop shadow; [fill] paints a gradient instead of the color. */
    private fun shout(c: Canvas, s: String, x: Float, y: Float, p: Paint, fill: Shader? = null, edge: Float = 0.12f) {
        val e = p.textSize * edge
        c.drawText(s, x, y + p.textSize * 0.08f, Paint(p).apply { color = 0xB3120A30.toInt(); style = Paint.Style.STROKE; strokeWidth = e; strokeJoin = Paint.Join.ROUND })
        c.drawText(s, x, y, Paint(p).apply { color = INK; style = Paint.Style.STROKE; strokeWidth = e; strokeJoin = Paint.Join.ROUND })
        c.drawText(s, x, y, Paint(p).apply { if (fill != null) shader = fill })
    }

    private fun goldFill(top: Float, bottom: Float) =
        LinearGradient(0f, top, 0f, bottom, intArrayOf(0xFFFFFBD0.toInt(), 0xFFFFD84A.toInt(), 0xFFF29A12.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)

    // ---------- HUD ----------

    val hud = object : ThemeHud {
        override fun mode(kind: WidgetKind): HudMode? = when (kind) {
            WidgetKind.CLOCK -> HudMode.BEHIND
            WidgetKind.BANNER, WidgetKind.BATTERY, WidgetKind.DATE, WidgetKind.COUNTDOWN, WidgetKind.CALENDAR, WidgetKind.PHOTO -> HudMode.REPLACE
            WidgetKind.APPS -> HudMode.SLOTS
            else -> null
        }

        override fun appSlots(apps: Int, w: Float, h: Float): Int =
            max(apps, min(5, floor(w / h.coerceAtLeast(1f)).toInt()).coerceAtLeast(1))

        override fun draw(c: Canvas, theme: WidgetTheme, kind: WidgetKind, style: WidgetStyle, data: HudData, card: RectF, content: RectF, d: Float) {
            val rad = min(theme.corner * d, min(card.width(), card.height()) / 2f)
            when (kind) {
                WidgetKind.BANNER -> banner(c, style, card, d)
                WidgetKind.CLOCK -> namePlate(c, card, rad, content.bottom - 21f * d, d, Rarity.EPIC)
                WidgetKind.PHOTO -> mystery(c, card, rad, d)
                WidgetKind.COUNTDOWN -> storm(c, style, data, card, rad, d)
                WidgetKind.BATTERY -> vitals(c, data, card, d)
                WidgetKind.DATE -> pass(c, data, card, d)
                WidgetKind.CALENDAR -> calendar(c, style, data, card, d)
                WidgetKind.APPS -> quickBar(c, theme, style, content, d)
                else -> Unit
            }
        }
    }

    /** Laurel-wreathed #1 medal on the left, the banner line in gold heavy italics. */
    private fun banner(c: Canvas, style: WidgetStyle, card: RectF, d: Float) {
        val h = card.height()
        val mr = h * 0.33f
        val mx = card.left + 10f * d + mr * 1.25f
        val my = card.centerY()
        medal(c, mx, my, mr, d)
        val text = RectF(mx + mr * 1.45f, card.top + 6f * d, card.right - max(12f * d, card.width() * 0.08f), card.bottom - 8f * d)
        val loc = java.util.Locale.getDefault()
        val title = style.bannerText.ifBlank { " " }.uppercase(loc)
        val sub = style.bannerSub.trim().uppercase(loc)
        val tp = hp(text.height() * (if (sub.isEmpty()) 0.7f else 0.52f)).fitTo(title, text.width())
        val ty = if (sub.isEmpty()) tp.mid(text.centerY()) else text.top + text.height() * 0.58f
        val asc = tp.fontMetrics.ascent
        shout(c, title, text.centerX(), ty, tp, goldFill(ty + asc, ty))
        if (sub.isNotEmpty()) {
            val sp = hp(text.height() * 0.24f).fitTo(sub, text.width())
            shout(c, sub, text.centerX(), ty + sp.textSize * 1.25f, sp)
        }
    }

    private fun medal(c: Canvas, x: Float, y: Float, r: Float, d: Float) {
        // Laurel: leaves along two arcs.
        val leaf = aa(0xFF3FB83A.toInt()); val leafD = aa(0xFF1E7A23.toInt()); val ink = line(INK, 1.4f * d)
        for (side in listOf(-1f, 1f)) {
            for (k in 0 until 6) {
                val a = (PI / 2 + side * (0.35 + k * 0.42)).toFloat()
                val lx = x + cos(a) * r * 1.28f; val ly = y + sin(a) * r * 1.18f
                c.withRotation((a * 180 / PI).toFloat() + side * 70f, lx, ly) {
                    val o = RectF(lx - r * 0.26f, ly - r * 0.12f, lx + r * 0.26f, ly + r * 0.12f)
                    drawOval(o, if (k % 2 == 0) leaf else leafD); drawOval(o, ink)
                }
            }
        }
        c.drawCircle(x, y + 2.5f * d, r, aa(0x99120A30.toInt()))
        c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(x, y - r, x, y + r, intArrayOf(0xFFFFF4B0.toInt(), 0xFFFFC234.toInt(), 0xFFD9800E.toInt()), null, Shader.TileMode.CLAMP)
        })
        c.drawCircle(x, y, r * 0.8f, line(0xFFFFF3B8.toInt(), 1.4f * d))
        c.drawCircle(x, y, r, line(INK, 2.2f * d))
        val p = hp(r * 1.05f, INK)
        c.drawText("#1", x - r * 0.04f, p.mid(y), p)
        // Stars over the medal.
        listOf(-0.8f to -1.15f, 0f to -1.42f, 0.8f to -1.15f).forEachIndexed { i, (u, v) ->
            star(c, x + u * r, y + v * r * 0.92f, r * (if (i == 1) 0.3f else 0.22f), GOLD, d)
        }
    }

    private fun starPath(x: Float, y: Float, outer: Float, inner: Float, points: Int = 5) = Path().apply {
        for (i in 0 until points * 2) {
            val rr = if (i % 2 == 0) outer else inner
            val a = -PI / 2 + i * PI / points
            val px = x + (cos(a) * rr).toFloat(); val py = y + (sin(a) * rr).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }

    private fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int, d: Float) {
        val p = starPath(x, y, r, r * 0.48f)
        c.drawPath(p, aa(color)); c.drawPath(p, line(INK, 1.3f * d))
    }

    /** No photo yet: a mystery item with a big question mark and a name plate. */
    private fun mystery(c: Canvas, card: RectF, rad: Float, d: Float) {
        val plateH = max(card.height() * 0.26f, 30f * d)
        val band = namePlate(c, card, rad, card.bottom - plateH, d, Rarity.RARE)
        val area = RectF(card.left, card.top, card.right, band.top)
        c.drawCircle(area.centerX(), area.centerY(), min(area.width(), area.height()) * 0.36f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(area.centerX(), area.centerY(), min(area.width(), area.height()) * 0.36f, intArrayOf(0x80FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        })
        val q = hp(area.height() * 0.62f)
        shout(c, "?", area.centerX(), q.mid(area.centerY()), q, edge = 0.08f)
        val t = hp(band.height() * 0.4f).fitTo("ADD A PHOTO", band.width() * 0.85f)
        c.drawText("ADD A PHOTO", band.centerX(), t.mid(band.top + band.height() * 0.4f), t)
        val s = hp(band.height() * 0.22f, 0xFFB5ECFF.toInt())
        c.drawText("TAP TO EQUIP", band.centerX(), s.mid(band.top + band.height() * 0.76f), s)
    }

    /** Storm countdown: swirling clouds, the title as a small header, the days big. */
    private fun storm(c: Canvas, style: WidgetStyle, data: HudData, card: RectF, rad: Float, d: Float) {
        c.withClip(roundPath(card, rad)) {
            val cx = card.right - card.height() * 0.4f; val cy = card.centerY()
            for (k in 0 until 5) {
                val rr = card.height() * (0.18f + k * 0.16f)
                drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), k * 70f, 200f, false, line(0x33FFFFFF, 3f * d))
            }
            bolt(this, cx + card.height() * 0.05f, cy, card.height() * 0.5f, d)
        }
        val loc = data.locale
        val text = RectF(card.left + 12f * d, card.top + 6f * d, card.right - card.height() * 0.75f, card.bottom - 7f * d)
        val target = WidgetLogic.countdownTarget(style.countdownDay, data.today)
        val days = WidgetLogic.daysUntil(target, data.today)
        val title = style.countdownTitle.ifBlank { "Countdown" }.uppercase(loc)
        val tp = hp(text.height() * 0.27f, WHITE, Paint.Align.LEFT).fitTo(title, text.width())
        shout(c, title, text.left, tp.mid(text.top + text.height() * 0.17f), tp, edge = 0.16f)
        val big = if (days > 1) "$days" else WidgetLogic.countdownText(days).uppercase(loc)
        val bp = hp(text.height() * 0.66f, WHITE, Paint.Align.LEFT).fitTo(big + if (days > 1) " DAYS" else "", text.width())
        val by = text.bottom - bp.fontMetrics.descent * 0.6f
        shout(c, big, text.left + bp.textSize * 0.05f, by, bp)
        if (days > 1) {
            val dp = hp(bp.textSize * 0.42f, GOLD, Paint.Align.LEFT)
            shout(c, "DAYS", text.left + bp.measureText(big) + bp.textSize * 0.16f, by, dp)
        }
    }

    private fun bolt(c: Canvas, x: Float, y: Float, s: Float, d: Float) {
        val p = Path().apply {
            moveTo(x + s * 0.1f, y - s * 0.5f); lineTo(x - s * 0.22f, y + s * 0.05f); lineTo(x - s * 0.02f, y + s * 0.05f)
            lineTo(x - s * 0.14f, y + s * 0.5f); lineTo(x + s * 0.24f, y - s * 0.1f); lineTo(x + s * 0.03f, y - s * 0.1f); close()
        }
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66C9F4FF; maskFilter = null; style = Paint.Style.STROKE; strokeWidth = 5f * d; strokeJoin = Paint.Join.ROUND })
        c.drawPath(p, aa(0xFFF4FDFF.toInt()))
    }

    /** Battery as shield (top half of the charge) over health (bottom half), plus the percent. */
    private fun vitals(c: Canvas, data: HudData, card: RectF, d: Float) {
        val plate = RectF(card.left + 7f * d, card.top + 7f * d, card.right - 7f * d, card.bottom - 7f * d)
        c.drawRoundRect(plate, 6f * d, 6f * d, aa(0x99100A30.toInt()))
        val pctW = plate.width() * 0.3f
        val bars = RectF(plate.left + 5f * d, plate.top + 4f * d, plate.right - pctW - 4f * d, plate.bottom - 4f * d)
        val shield = ((data.battery - 50) * 2).coerceIn(0, 100)
        val health = (data.battery * 2).coerceIn(0, 100)
        val rowH = bars.height() / 2
        bar(c, RectF(bars.left, bars.top, bars.right, bars.top + rowH), shield, 0xFF7FD6FF.toInt(), 0xFF2E7BFF.toInt(), shieldIcon = true, d = d)
        bar(c, RectF(bars.left, bars.top + rowH, bars.right, bars.bottom), health, 0xFFB4FF7A.toInt(), 0xFF2FB43A.toInt(), shieldIcon = false, d = d)
        val pr = RectF(plate.right - pctW, plate.top, plate.right - 4f * d, plate.bottom)
        c.drawLine(pr.left, plate.top + 5f * d, pr.left, plate.bottom - 5f * d, line(0x40FFFFFF, 1.2f * d))
        val pct = "${data.battery}%"
        val p = hp(pr.height() * 0.62f).fitTo(pct, pr.width() * 0.86f)
        shout(c, pct, pr.centerX(), p.mid(pr.centerY()), p)
        if (data.charging) bolt(c, pr.right - 2f * d, pr.top + 6f * d, pr.height() * 0.36f, d)
    }

    /** One chunky, slightly skewed, segmented bar with an icon and its number. */
    private fun bar(c: Canvas, r: RectF, value: Int, light: Int, dark: Int, shieldIcon: Boolean, d: Float) {
        val h = r.height() * 0.7f
        val cy = r.centerY()
        val icon = h * 1.05f
        val ix = r.left + icon / 2
        if (shieldIcon) {
            val s = Path().apply {
                moveTo(ix, cy - icon / 2); lineTo(ix + icon * 0.42f, cy - icon * 0.36f); lineTo(ix + icon * 0.36f, cy + icon * 0.1f)
                quadTo(ix + icon * 0.2f, cy + icon * 0.38f, ix, cy + icon / 2)
                quadTo(ix - icon * 0.2f, cy + icon * 0.38f, ix - icon * 0.36f, cy + icon * 0.1f); lineTo(ix - icon * 0.42f, cy - icon * 0.36f); close()
            }
            c.drawPath(s, aa(dark)); c.drawPath(s, line(WHITE, 1.3f * d))
        } else {
            val a = icon * 0.16f
            val plus = Path().apply {
                addRect(ix - a, cy - icon * 0.42f, ix + a, cy + icon * 0.42f, Path.Direction.CW)
                addRect(ix - icon * 0.42f, cy - a, ix + icon * 0.42f, cy + a, Path.Direction.CW)
            }
            c.drawPath(plus, aa(WHITE))
        }
        val numW = h * 1.9f
        val bx0 = r.left + icon + 4f * d
        val bx1 = r.right - numW
        val skew = h * 0.35f
        val segs = 5
        val gap = 1.6f * d
        val segW = (bx1 - bx0 - gap * (segs - 1)) / segs
        val fill = value / 100f * segs
        for (i in 0 until segs) {
            val l = bx0 + i * (segW + gap)
            val seg = Path().apply { moveTo(l + skew, cy - h / 2); lineTo(l + segW + skew, cy - h / 2); lineTo(l + segW, cy + h / 2); lineTo(l, cy + h / 2); close() }
            c.drawPath(seg, aa(0x66000000))
            val f = (fill - i).coerceIn(0f, 1f)
            if (f > 0f) c.withClip(RectF(l, cy - h, l + skew + segW * f, cy + h)) {
                drawPath(seg, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, cy - h / 2, 0f, cy + h / 2, light, dark, Shader.TileMode.CLAMP) })
                drawRect(l, cy - h / 2, l + segW + skew, cy - h / 2 + h * 0.22f, aa(0x55FFFFFF))
            }
            c.drawPath(seg, line(INK, 1.1f * d))
        }
        val np = hp(h * 1.12f, WHITE, Paint.Align.RIGHT)
        c.drawText("$value", r.right - 2f * d, np.mid(cy), np)
    }

    /** Year progress as a tier pass: star badge with the week number, a chunked bar, the percent. */
    private fun pass(c: Canvas, data: HudData, card: RectF, d: Float) {
        val h = card.height()
        val sr = h * 0.36f
        val sx = card.left + 10f * d + sr
        val sy = card.centerY()
        val starP = starPath(sx, sy, sr, sr * 0.55f)
        c.withTranslation(0f, 2.5f * d) { drawPath(starP, aa(0x99120A30.toInt())) }
        c.drawPath(starP, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(sx, sy - sr, sx, sy + sr, intArrayOf(0xFF8BF0FF.toInt(), 0xFF2E8BFF.toInt(), 0xFF3B2BD9.toInt()), null, Shader.TileMode.CLAMP)
        })
        c.drawPath(starP, line(INK, 2f * d))
        val week = data.today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val wp = hp(sr * 0.72f)
        shout(c, "$week", sx, wp.mid(sy + sr * 0.08f), wp, edge = 0.1f)
        val area = RectF(sx + sr + 8f * d, card.top + 7f * d, card.right - 10f * d, card.bottom - 9f * d)
        val frac = WidgetLogic.yearProgress(data.today)
        val pct = (frac * 100).roundToInt()
        val lp = hp(area.height() * 0.34f, WHITE, Paint.Align.LEFT).fitTo("TIER $week", area.width() * 0.6f)
        shout(c, "TIER $week", area.left, area.top - lp.fontMetrics.ascent * 0.92f, lp)
        val rp = hp(lp.textSize, GOLD, Paint.Align.RIGHT)
        shout(c, "$pct%", area.right, area.top - lp.fontMetrics.ascent * 0.92f, rp)
        val bh = area.height() * 0.28f
        val bt = area.top + area.height() * 0.5f
        val segs = 10
        val gap = 1.4f * d
        val skew = bh * 0.35f
        val segW = (area.width() - skew - gap * (segs - 1)) / segs
        val fill = frac * segs
        for (i in 0 until segs) {
            val l = area.left + i * (segW + gap)
            val seg = Path().apply { moveTo(l + skew, bt); lineTo(l + segW + skew, bt); lineTo(l + segW, bt + bh); lineTo(l, bt + bh); close() }
            c.drawPath(seg, aa(0x80120A30.toInt()))
            val f = (fill - i).coerceIn(0f, 1f)
            if (f > 0f) c.withClip(RectF(l, bt - bh, l + skew + segW * f, bt + bh * 2)) {
                drawPath(seg, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, bt, 0f, bt + bh, 0xFFFFF6A0.toInt(), 0xFFFFB81F.toInt(), Shader.TileMode.CLAMP) })
            }
            c.drawPath(seg, line(INK, 1.1f * d))
        }
        val cap = "DAY ${data.today.dayOfYear} OF ${data.today.lengthOfYear()}"
        val cp = hp(area.height() * 0.22f, 0xFFFFF6D8.toInt(), Paint.Align.LEFT).fitTo(cap, area.width())
        c.drawText(cap, area.left, area.bottom - cp.fontMetrics.descent * 0.4f, cp)
    }

    private fun calendar(c: Canvas, style: WidgetStyle, data: HudData, card: RectF, d: Float) {
        val loc = data.locale
        val inner = RectF(card.left + 10f * d, card.top + 8f * d, card.right - 10f * d, card.bottom - 9f * d)
        val titleH = min(inner.height() * 0.17f, 24f * d)
        val month = data.today.month.getDisplayName(TextStyle.FULL, loc).uppercase(loc)
        val tp = hp(titleH, WHITE, Paint.Align.LEFT).fitTo(month, inner.width() * 0.7f)
        shout(c, month, inner.left + 1f * d, inner.top + titleH * 0.86f, tp)
        val yp = hp(titleH * 0.6f, 0xFFB5ECFF.toInt(), Paint.Align.RIGHT)
        c.drawText("${data.today.year}", inner.right, inner.top + titleH * 0.86f, yp)
        val grid = WidgetLogic.monthGrid(data.today, style.mondayFirst)
        val heads = WidgetLogic.weekdayInitials(style.mondayFirst, loc)
        val top = inner.top + titleH + 3f * d
        c.drawRoundRect(RectF(inner.left - 4f * d, top - 1f * d, inner.right + 4f * d, inner.bottom + 3f * d), 7f * d, 7f * d, aa(0x8C100A30.toInt()))
        val rows = grid.size + 1
        val cellH = (inner.bottom - top) / rows
        val cellW = inner.width() / 7
        val hpP = hp(min(cellH * 0.62f, cellW * 0.55f), 0xFF8FD8FF.toInt())
        heads.forEachIndexed { i, s -> c.drawText(s.uppercase(loc), inner.left + cellW * (i + 0.5f), hpP.mid(top + cellH * 0.5f), hpP) }
        val np = hp(min(cellH * 0.7f, cellW * 0.6f))
        grid.forEachIndexed { ri, week ->
            week.forEachIndexed { ci, day ->
                if (day == 0) return@forEachIndexed
                val x = inner.left + cellW * (ci + 0.5f)
                val y = top + cellH * (ri + 1.5f)
                if (day == data.today.dayOfMonth) {
                    val s = min(cellW, cellH) * 0.98f
                    val r = RectF(x - s / 2, y - s / 2, x + s / 2, y + s / 2)
                    c.drawRoundRect(r, s * 0.24f, s * 0.24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xFFFFF3A0.toInt(), 0xFFFFB21F.toInt(), Shader.TileMode.CLAMP) })
                    c.drawRoundRect(r, s * 0.24f, s * 0.24f, line(INK, 1.6f * d))
                    c.drawText("$day", x, np.mid(y), Paint(np).apply { color = INK })
                } else {
                    c.drawText("$day", x, np.mid(y), Paint(np).apply { alpha = if (day < data.today.dayOfMonth) 120 else 255 })
                }
            }
        }
    }

    private val SAMPLE: List<StickerArt> by lazy { listOf(pickaxe, shieldPotion, medKit, lootChest, glider) }

    /** Item quick-bar: a tile per slot in rising rarity; the first one selected. */
    private fun quickBar(c: Canvas, theme: WidgetTheme, style: WidgetStyle, content: RectF, d: Float) {
        val apps = style.apps.take(6)
        val n = theme.hud!!.appSlots(apps.size, content.width() / d, content.height() / d)
        val pitch = content.width() / n
        val size = min(pitch * 0.92f, content.height() + 14f * d)
        val ink = StickerInk(InkStyle.COLOR, INK, GOLD, 0xFF3FB0FF.toInt(), WHITE)
        for (i in 0 until n) {
            val cx = content.left + pitch * (i + 0.5f)
            val r = RectF(cx - size / 2, content.centerY() - size / 2, cx + size / 2, content.centerY() + size / 2)
            card(c, r, size * 0.2f, d * 0.8f, Rarity.entries[(i + 1) % Rarity.entries.size])
            if (apps.isEmpty()) ThemeArt.drawSticker(c, Sticker.STAR, cx, r.centerY(), size * 0.72f, ink, 0f, SAMPLE[i % SAMPLE.size])
        }
        val sel = RectF(content.left + pitch / 2 - size / 2 - 2.5f * d, content.centerY() - size / 2 - 2.5f * d, content.left + pitch / 2 + size / 2 + 2.5f * d, content.centerY() + size / 2 + 2.5f * d)
        c.drawRoundRect(sel, size * 0.24f, size * 0.24f, line(WHITE, 2.4f * d))
    }

    // ---------- Stickers (bold cartoon: thick dark outline, glossy highlights) ----------

    private fun Pen.bold(p: Path, fill: Int) { if (colorful) c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }); stroke(p, lineW * 1.5f, INK) }
    private fun Pen.grad(p: Path, top: Int, bottom: Int, v0: Float = -0.5f, v1: Float = 0.5f) {
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, y(v0), 0f, y(v1), top, bottom, Shader.TileMode.CLAMP) })
        stroke(p, lineW * 1.5f, INK)
    }
    private fun Pen.gloss(l: Float, t: Float, r: Float, b: Float) = c.drawOval(RectF(x(l), y(t), x(r), y(b)), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8CFFFFFF.toInt() })
    private fun Pen.glow(u: Float, v: Float, rr: Float, color: Int) = c.drawCircle(x(u), y(v), r(rr), Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(x(u), y(v), r(rr), intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
    })
    private fun Pen.sparkle(u: Float, v: Float, k: Float) {
        val p = path { m(u, v - k); q(u, v, u + k * 0.7f, v); q(u, v, u, v + k); q(u, v, u - k * 0.7f, v); q(u, v, u, v - k); close() }
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
    }

    /** Gold loot chest glowing with rays. */
    val lootChest: StickerArt = {
        glow(0f, 0.02f, 0.55f, 0xCCFFE27A.toInt())
        val rays = Path()
        for (i in 0 until 10) {
            val a = i * PI / 5 - PI / 2
            rays.moveTo(x(0f), y(0.02f))
            rays.lineTo(x((cos(a - 0.12) * 0.56).toFloat()), y((0.02 + sin(a - 0.12) * 0.56).toFloat()))
            rays.lineTo(x((cos(a + 0.12) * 0.56).toFloat()), y((0.02 + sin(a + 0.12) * 0.56).toFloat()))
            rays.close()
        }
        c.drawPath(rays, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFF4B0 })
        grad(rectP(-0.36f, -0.02f, 0.36f, 0.34f, 0.04f), 0xFFFFD54A.toInt(), 0xFFD9850F.toInt(), -0.02f, 0.34f)
        grad(path { m(-0.36f, -0.02f); l(-0.36f, -0.14f); q(-0.36f, -0.32f, 0f, -0.32f); q(0.36f, -0.32f, 0.36f, -0.14f); l(0.36f, -0.02f); close() }, 0xFFFFF0A0.toInt(), 0xFFFFC234.toInt(), -0.32f, -0.02f)
        listOf(-0.22f, 0.22f).forEach { u -> bold(rectP(u - 0.05f, -0.3f, u + 0.05f, 0.34f), 0xFFB8731A.toInt()) }
        bold(rectP(-0.08f, -0.1f, 0.08f, 0.1f, 0.03f), 0xFF6B3A10.toInt())
        solid(circleP(0f, -0.02f, 0.025f), 0xFFFFE27A.toInt())
        gloss(-0.3f, -0.27f, -0.06f, -0.2f)
        sparkle(0.36f, -0.36f, 0.1f); sparkle(-0.4f, -0.3f, 0.07f); sparkle(0.42f, 0.12f, 0.06f)
    }

    /** Supply crate hanging from a blue balloon. */
    val supplyDrop: StickerArt = {
        stroke(path { m(-0.13f, -0.12f); l(-0.2f, 0.08f); m(0.13f, -0.12f); l(0.2f, 0.08f) }, lineW * 1.1f, INK)
        val balloon = path { m(0f, -0.08f); cu(-0.32f, -0.12f, -0.3f, -0.5f, 0f, -0.5f); cu(0.3f, -0.5f, 0.32f, -0.12f, 0f, -0.08f); close() }
        grad(balloon, 0xFF8FE0FF.toInt(), 0xFF1F6BE0.toInt(), -0.5f, -0.08f)
        gloss(-0.17f, -0.43f, -0.05f, -0.3f)
        grad(rectP(-0.25f, 0.08f, 0.25f, 0.46f, 0.03f), 0xFF5AA0FF.toInt(), 0xFF1F4FC0.toInt(), 0.08f, 0.46f)
        listOf(0.2f, 0.33f).forEach { v -> stroke(path { m(-0.25f, v); l(0.25f, v) }, lineW, INK) }
        bold(rectP(-0.06f, 0.08f, 0.06f, 0.46f), 0xFFFFD23F.toInt())
        gloss(-0.21f, 0.1f, -0.05f, 0.15f)
    }

    /** Small shield potion: round bottle of glowing blue. */
    val shieldPotion: StickerArt = {
        glow(0f, 0.1f, 0.45f, 0x8859C8FF.toInt())
        bold(rectP(-0.09f, -0.36f, 0.09f, -0.12f, 0.02f), 0xFFDDF4FF.toInt())
        bold(rectP(-0.11f, -0.46f, 0.11f, -0.34f, 0.03f), 0xFFB07A45.toInt())
        val body = circleP(0f, 0.1f, 0.3f)
        grad(body, 0xFF9BE8FF.toInt(), 0xFF1F7BFF.toInt(), -0.2f, 0.4f)
        c.withClip(body) { drawRect(x(-0.4f), y(-0.2f), x(0.4f), y(0.02f), Paint().apply { color = 0x55E6F8FF }) }
        stroke(body, lineW * 1.5f, INK)
        gloss(-0.2f, -0.08f, -0.06f, 0.08f)
        solid(circleP(0.1f, 0.2f, 0.035f), 0xCCFFFFFF.toInt()); solid(circleP(0.03f, 0.3f, 0.025f), 0xCCFFFFFF.toInt())
    }

    /** First-aid kit with a white plus on green. */
    val medKit: StickerArt = {
        stroke(path { m(-0.14f, -0.2f); l(-0.14f, -0.32f); l(0.14f, -0.32f); l(0.14f, -0.2f) }, lineW * 2.6f, INK)
        stroke(path { m(-0.14f, -0.2f); l(-0.14f, -0.32f); l(0.14f, -0.32f); l(0.14f, -0.2f) }, lineW * 1.2f, 0xFFB9C6D6.toInt())
        grad(rectP(-0.4f, -0.2f, 0.4f, 0.34f, 0.08f), 0xFF7BEA8C.toInt(), 0xFF1F9A45.toInt(), -0.2f, 0.34f)
        bold(path {
            m(-0.07f, -0.11f); l(0.07f, -0.11f); l(0.07f, 0.0f); l(0.18f, 0.0f); l(0.18f, 0.14f); l(0.07f, 0.14f); l(0.07f, 0.25f)
            l(-0.07f, 0.25f); l(-0.07f, 0.14f); l(-0.18f, 0.14f); l(-0.18f, 0.0f); l(-0.07f, 0.0f); close()
        }, WHITE)
        gloss(-0.34f, -0.16f, -0.12f, -0.1f)
    }

    /** Harvesting pickaxe: wooden handle, bright two-tone head. */
    val pickaxe: StickerArt = {
        val handle = path { m(-0.32f, 0.4f); l(0.16f, -0.16f) }
        stroke(handle, r(0.13f), INK); stroke(handle, r(0.07f), 0xFFB07A45.toInt())
        stroke(path { m(-0.3f, 0.36f); l(-0.2f, 0.25f) }, r(0.07f), 0xFF3A2A5A.toInt())
        val head = path {
            m(-0.28f, -0.3f); q(0.12f, -0.5f, 0.44f, -0.06f); q(0.48f, 0.08f, 0.42f, 0.18f)
            q(0.26f, -0.12f, 0.08f, -0.14f); q(-0.06f, -0.2f, -0.28f, -0.3f); close()
        }
        grad(head, 0xFFE6D9FF.toInt(), 0xFF7B4DFF.toInt(), -0.4f, 0.18f)
        bold(circleP(0.14f, -0.17f, 0.07f), 0xFFFFD23F.toInt())
        gloss(-0.1f, -0.36f, 0.12f, -0.3f)
    }

    /** A striped paraglider canopy with its bar. */
    val glider: StickerArt = {
        val lines = path { m(-0.38f, -0.06f); l(-0.06f, 0.3f); m(-0.14f, -0.02f); l(-0.04f, 0.3f); m(0.14f, -0.02f); l(0.04f, 0.3f); m(0.38f, -0.06f); l(0.06f, 0.3f) }
        stroke(lines, lineW * 0.8f, INK)
        bold(rectP(-0.12f, 0.28f, 0.12f, 0.34f, 0.03f), 0xFF3A3A5A.toInt())
        val canopy = path {
            m(-0.46f, -0.04f); cu(-0.4f, -0.46f, 0.4f, -0.46f, 0.46f, -0.04f)
            q(0.37f, -0.1f, 0.28f, -0.03f); q(0.14f, -0.1f, 0f, -0.03f); q(-0.14f, -0.1f, -0.28f, -0.03f); q(-0.37f, -0.1f, -0.46f, -0.04f); close()
        }
        grad(canopy, 0xFFFF8A5C.toInt(), 0xFFE83A3A.toInt(), -0.4f, -0.03f)
        c.withClip(canopy) {
            listOf(-0.28f, 0f, 0.28f).forEach { u ->
                drawPath(path { m(u - 0.07f, -0.5f); l(u + 0.07f, -0.5f); l(u + 0.05f, 0f); l(u - 0.05f, 0f); close() }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD23F.toInt() })
            }
        }
        stroke(canopy, lineW * 1.5f, INK)
        gloss(-0.3f, -0.34f, -0.06f, -0.27f)
    }

    /** Purple storm orb with a lightning bolt. */
    val stormOrb: StickerArt = {
        glow(0f, 0f, 0.56f, 0xAAB45BFF.toInt())
        val orb = circleP(0f, 0f, 0.38f)
        c.drawPath(orb, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x(-0.1f), y(-0.12f), r(0.45f), intArrayOf(0xFFF0C8FF.toInt(), 0xFF9B45F0.toInt(), 0xFF3A0F7A.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        })
        c.withClip(orb) {
            for (k in 0 until 3) {
                val rr = r(0.12f + k * 0.1f)
                drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), k * 110f, 180f, false, line(0x55FFFFFF, lineW))
            }
        }
        stroke(orb, lineW * 1.5f, INK)
        val b = path { m(0.06f, -0.3f); l(-0.13f, 0.03f); l(0.0f, 0.03f); l(-0.08f, 0.3f); l(0.15f, -0.06f); l(0.02f, -0.06f); close() }
        c.drawPath(b, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF7B0.toInt() })
        stroke(b, lineW * 0.9f, INK)
        gloss(-0.24f, -0.26f, -0.08f, -0.16f)
    }

    private fun Pen.wallTile(face: Int, faceD: Int, detail: Pen.() -> Unit) {
        bold(path { m(-0.36f, -0.36f); l(0.42f, -0.36f); l(0.42f, 0.42f); l(-0.36f, 0.42f); close() }, PackAKit.shade(faceD, 0.7f))
        val front = rectP(-0.42f, -0.42f, 0.36f, 0.36f, 0.03f)
        grad(front, face, faceD, -0.42f, 0.36f)
        c.withClip(front) { detail() }
        stroke(front, lineW * 1.5f, INK)
    }

    /** Wooden build wall. */
    val wallWood: StickerArt = {
        wallTile(0xFFE0A86A.toInt(), 0xFFB0743A.toInt()) {
            listOf(-0.16f, 0.1f).forEach { v -> stroke(path { m(-0.42f, v); l(0.36f, v) }, lineW, INK) }
            listOf(-0.36f to -0.36f, 0.3f to -0.36f, -0.36f to 0.3f, 0.3f to 0.3f).forEach { (u, v) -> solid(circleP(u, v, 0.025f), 0xFF5A3A1A.toInt()) }
            stroke(path { m(-0.3f, -0.3f); l(-0.12f, -0.27f) }, lineW * 0.6f, 0x66FFFFFF)
        }
    }

    /** Stone build wall. */
    val wallStone: StickerArt = {
        wallTile(0xFFC9CED6.toInt(), 0xFF7D8694.toInt()) {
            listOf(-0.16f, 0.1f).forEach { v -> stroke(path { m(-0.42f, v); l(0.36f, v) }, lineW, INK) }
            stroke(path { m(-0.05f, -0.42f); l(-0.05f, -0.16f); m(-0.2f, -0.16f); l(-0.2f, 0.1f); m(0.16f, -0.16f); l(0.16f, 0.1f); m(-0.03f, 0.1f); l(-0.03f, 0.36f) }, lineW, INK)
        }
    }

    /** Metal build wall with rivets and hazard stripes. */
    val wallMetal: StickerArt = {
        wallTile(0xFFB8D4F0.toInt(), 0xFF5C7FA6.toInt()) {
            for (k in -4..4) c.drawPath(path { m(k * 0.18f - 0.05f, 0.36f); l(k * 0.18f + 0.05f, 0.36f); l(k * 0.18f + 0.2f, 0.2f); l(k * 0.18f + 0.1f, 0.2f); close() }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (k % 2 == 0) 0xFFFFD23F.toInt() else 0xFF2A2A3A.toInt() })
            stroke(path { m(-0.42f, 0.2f); l(0.36f, 0.2f) }, lineW, INK)
            listOf(-0.32f to -0.32f, 0.26f to -0.32f, -0.32f to 0.08f, 0.26f to 0.08f).forEach { (u, v) -> solid(circleP(u, v, 0.035f), 0xFF3A4A60.toInt()) }
            gloss(-0.34f, -0.26f, -0.04f, -0.16f)
        }
    }

    /** "+1" elimination burst. */
    val plusOne: StickerArt = {
        val burst = Path().apply {
            for (i in 0 until 24) {
                val rr = if (i % 2 == 0) 0.47f else 0.36f
                val a = -PI / 2 + i * PI / 12
                val px = x((cos(a) * rr).toFloat()); val py = y((sin(a) * rr).toFloat())
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        grad(burst, 0xFFFFC14D.toInt(), 0xFFFF3D5A.toInt())
        val p = hp(r(0.42f), WHITE)
        val by = p.mid(cy)
        shout(c, "+1", cx, by, p)
    }

    /** Gold crown with gems. */
    val crown: StickerArt = {
        glow(0f, 0f, 0.5f, 0x66FFE27A)
        val body = path {
            m(-0.4f, 0.26f); l(-0.44f, -0.22f); l(-0.2f, 0.0f); l(0f, -0.34f); l(0.2f, 0.0f); l(0.44f, -0.22f); l(0.4f, 0.26f); close()
        }
        grad(body, 0xFFFFF0A0.toInt(), 0xFFE0920F.toInt(), -0.34f, 0.26f)
        grad(rectP(-0.42f, 0.2f, 0.42f, 0.36f, 0.04f), 0xFFFFD54A.toInt(), 0xFFC97A0C.toInt(), 0.2f, 0.36f)
        bold(circleP(0f, 0.08f, 0.07f), 0xFFE8314F.toInt())
        bold(circleP(-0.24f, 0.13f, 0.05f), 0xFF3FB0FF.toInt())
        bold(circleP(0.24f, 0.13f, 0.05f), 0xFF3FB0FF.toInt())
        listOf(-0.44f to -0.22f, 0f to -0.34f, 0.44f to -0.22f).forEach { (u, v) -> bold(circleP(u, v, 0.05f), 0xFFFFF0A0.toInt()) }
        gloss(-0.3f, -0.1f, -0.16f, 0.08f)
        sparkle(0.36f, -0.4f, 0.08f)
    }

    // ---------- Wallpaper ----------

    /** Bright cartoon sky, a floating island with trees and a lookout tower, the storm closing in, a glider and a drop. */
    val wall = WallPainter { c, w, h ->
        c.drawRect(0f, 0f, w, h, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF2F8BFF.toInt(), 0xFF5DB8FF.toInt(), 0xFFA6E3FF.toInt(), 0xFFD6F4FF.toInt()), floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawCircle(w * 0.78f, h * 0.12f, w * 0.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(w * 0.78f, h * 0.12f, w * 0.3f, intArrayOf(0x99FFFBE0.toInt(), 0x00FFFBE0), null, Shader.TileMode.CLAMP)
        })
        c.drawCircle(w * 0.78f, h * 0.12f, w * 0.07f, aa(0xFFFFF8D8.toInt()))
        // Soft clouds.
        fun cloud(x: Float, y: Float, s: Float, a: Int = 0xF2) {
            val shade = aa(((a * 0.55f).toInt() shl 24) or 0x9CC8EE)
            val white = aa((a shl 24) or 0xFFFFFF)
            listOf(-0.5f to 0.1f, 0f to -0.12f, 0.45f to 0.05f, -0.2f to 0.12f, 0.2f to 0.14f).forEach { (u, v) -> c.drawCircle(x + u * s, y + v * s + s * 0.08f, s * 0.36f, shade) }
            listOf(-0.5f to 0.1f, 0f to -0.12f, 0.45f to 0.05f, -0.2f to 0.12f, 0.2f to 0.14f).forEach { (u, v) -> c.drawCircle(x + u * s, y + v * s, s * 0.34f, white) }
        }
        // A far-off islet, hazy with distance.
        run {
            val fx = w * 0.3f; val fy = h * 0.27f; val fw = w * 0.2f
            val rock = Path().apply { moveTo(fx - fw / 2, fy); lineTo(fx + fw / 2, fy); lineTo(fx + fw * 0.1f, fy + fw * 0.45f); lineTo(fx - fw * 0.18f, fy + fw * 0.2f); close() }
            c.drawPath(rock, aa(0xFFA98AA8.toInt()))
            c.drawRoundRect(RectF(fx - fw * 0.55f, fy - fw * 0.06f, fx + fw * 0.55f, fy + fw * 0.05f), fw * 0.05f, fw * 0.05f, aa(0xFF8FCB9A.toInt()))
            listOf(-0.25f, 0.05f, 0.3f).forEach { u -> c.drawCircle(fx + fw * u, fy - fw * 0.14f, fw * 0.1f, aa(0xFF6FAE86.toInt())) }
        }
        cloud(w * 0.2f, h * 0.18f, w * 0.2f)
        cloud(w * 0.62f, h * 0.3f, w * 0.16f, 0xD9)
        cloud(w * 0.3f, h * 0.48f, w * 0.13f, 0xCC)
        cloud(w * 0.8f, h * 0.55f, w * 0.18f, 0xE6)
        cloud(w * 0.12f, h * 0.72f, w * 0.15f, 0xCC)
        // The floating island.
        val ix = w * 0.52f; val iy = h * 0.66f; val iw = w * 0.5f
        val rock = Path().apply {
            moveTo(ix - iw * 0.5f, iy)
            lineTo(ix + iw * 0.5f, iy)
            lineTo(ix + iw * 0.36f, iy + iw * 0.18f); lineTo(ix + iw * 0.2f, iy + iw * 0.24f); lineTo(ix + iw * 0.1f, iy + iw * 0.42f)
            lineTo(ix - iw * 0.02f, iy + iw * 0.3f); lineTo(ix - iw * 0.16f, iy + iw * 0.36f); lineTo(ix - iw * 0.26f, iy + iw * 0.2f)
            lineTo(ix - iw * 0.4f, iy + iw * 0.14f); close()
        }
        c.drawPath(rock, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, iy, 0f, iy + iw * 0.42f, 0xFFB98A5E.toInt(), 0xFF6A4630.toInt(), Shader.TileMode.CLAMP) })
        c.drawPath(Path().apply { moveTo(ix - iw * 0.1f, iy); lineTo(ix + iw * 0.05f, iy + iw * 0.3f); lineTo(ix + iw * 0.1f, iy) ; close() }, aa(0x33000000))
        c.drawPath(rock, line(INK, w * 0.006f))
        val grass = RectF(ix - iw * 0.54f, iy - iw * 0.07f, ix + iw * 0.54f, iy + iw * 0.05f)
        c.drawRoundRect(grass, iw * 0.06f, iw * 0.06f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, grass.top, 0f, grass.bottom, 0xFF8CF05A.toInt(), 0xFF3FAA3A.toInt(), Shader.TileMode.CLAMP) })
        c.drawRoundRect(grass, iw * 0.06f, iw * 0.06f, line(INK, w * 0.006f))
        // Lookout tower.
        val tx = ix + iw * 0.22f; val tb = grass.top + iw * 0.02f; val tw = iw * 0.12f; val th = iw * 0.42f
        val legs = line(0xFF8A5A30.toInt(), w * 0.008f)
        c.drawLine(tx - tw * 0.45f, tb, tx - tw * 0.3f, tb - th * 0.7f, legs); c.drawLine(tx + tw * 0.45f, tb, tx + tw * 0.3f, tb - th * 0.7f, legs)
        c.drawLine(tx - tw * 0.4f, tb - th * 0.25f, tx + tw * 0.38f, tb - th * 0.5f, legs)
        val cabin = RectF(tx - tw * 0.5f, tb - th * 0.92f, tx + tw * 0.5f, tb - th * 0.66f)
        c.drawRect(cabin, aa(0xFFE0A86A.toInt())); c.drawRect(cabin, line(INK, w * 0.005f))
        c.drawRect(RectF(cabin.left + tw * 0.2f, cabin.top + tw * 0.12f, cabin.right - tw * 0.2f, cabin.bottom - tw * 0.18f), aa(0xFF3A2A5A.toInt()))
        val roof = Path().apply { moveTo(cabin.left - tw * 0.15f, cabin.top); lineTo(tx, cabin.top - tw * 0.6f); lineTo(cabin.right + tw * 0.15f, cabin.top); close() }
        c.drawPath(roof, aa(0xFFE8463A.toInt())); c.drawPath(roof, line(INK, w * 0.005f))
        // Round cartoon trees.
        listOf(-0.36f to 1f, -0.18f to 1.3f, 0.02f to 0.9f, 0.4f to 0.8f).forEach { (u, k) ->
            val x = ix + iw * u; val base = grass.top + iw * 0.02f
            val s = iw * 0.09f * k
            c.drawRect(x - s * 0.15f, base - s * 1.1f, x + s * 0.15f, base, aa(0xFF8A5A30.toInt()))
            c.drawCircle(x, base - s * 1.4f, s * 0.75f, aa(0xFF2F8F3A.toInt()))
            c.drawCircle(x - s * 0.18f, base - s * 1.55f, s * 0.55f, aa(0xFF4FC24A.toInt()))
            c.drawCircle(x - s * 0.3f, base - s * 1.75f, s * 0.18f, aa(0x66FFFFFF))
            c.drawCircle(x, base - s * 1.4f, s * 0.75f, line(INK, w * 0.004f))
        }
        // The storm wall closing in from both sides, with lightning.
        fun stormSide(left: Boolean) {
            val edge = if (left) w * 0.2f else w * 0.82f
            val p = Path()
            val outer = if (left) -w * 0.1f else w * 1.1f
            p.moveTo(outer, 0f)
            p.lineTo(edge + (if (left) -w * 0.04f else w * 0.04f), 0f)
            val steps = 10
            for (i in 1..steps) {
                val yy = h * i / steps
                val wob = (sin(i * 1.7 + if (left) 0.0 else 2.0) * w * 0.05).toFloat()
                p.quadTo(edge + wob + (if (left) w * 0.05f else -w * 0.05f), yy - h / steps / 2, edge + wob, yy)
            }
            p.lineTo(outer, h); p.close()
            c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(if (left) 0f else w, 0f, edge, 0f, intArrayOf(0xF2351073.toInt(), 0xD96A2BD8.toInt(), 0x80A86BFF.toInt()), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
            })
            c.drawPath(p, line(0x99E0B8FF.toInt(), w * 0.006f))
        }
        stormSide(true); stormSide(false)
        val rnd = Random(77)
        listOf(w * 0.07f to h * 0.3f, w * 0.93f to h * 0.2f, w * 0.9f to h * 0.78f, w * 0.06f to h * 0.84f).forEach { (x, y) ->
            val p = Path().apply {
                moveTo(x, y - h * 0.08f)
                var cx = x; var cy = y - h * 0.08f
                repeat(5) { cx += (rnd.nextFloat() - 0.5f) * w * 0.06f; cy += h * 0.035f; lineTo(cx, cy) }
            }
            c.drawPath(p, line(0x66D9C2FF, w * 0.02f))
            c.drawPath(p, line(0xFFF6F0FF.toInt(), w * 0.006f))
        }
        // A glider coming in and a supply drop falling.
        PackAKit.sticker(c, glider, w * 0.66f, h * 0.42f, w * 0.18f, INK, -10f)
        PackAKit.sticker(c, supplyDrop, w * 0.3f, h * 0.36f, w * 0.16f, INK, 6f)
    }
}
