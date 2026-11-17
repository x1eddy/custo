package app.custo.feature.widgetsets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetFonts
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetLogic
import app.custo.feature.widgets.WidgetStyle
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Phone-shaped previews of a whole set: wallpaper + mini widgets. Rendered off the main thread and cached. */
object SetPreviews {
    /** Phone width in dp the previews pretend to be. */
    private const val PHONE_DP = 400f
    const val ASPECT = 19.5f / 9f

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun get(theme: WidgetTheme, widthPx: Int): Bitmap {
        val w = widthPx.coerceIn(120, 900)
        val key = key(theme, w)
        cache.get(key)?.let { return it }
        // Cached inside the render block so a render that outlives its (scrolled-away) caller still counts.
        return withContext(app.custo.ui.components.PreviewRender) {
            cache.get(key) ?: render(theme, w).also { it.prepareToDraw(); cache.put(key, it) }
        }
    }

    /** The cached preview, if there is one; cheap enough to call during composition. */
    fun peek(theme: WidgetTheme, widthPx: Int): Bitmap? = cache.get(key(theme, widthPx.coerceIn(120, 900)))

    private fun key(theme: WidgetTheme, w: Int) = "${theme.id}@${theme.chosenWall?.id}@$w@${LocalDate.now()}"

    /**
     * The set's background: its chosen wallhaven picture (center-cropped) when there is one and it
     * decodes, else the drawn wallpaper.
     */
    fun paintBackground(c: Canvas, theme: WidgetTheme, w: Int, h: Int) {
        val pic = theme.chosenWall?.let { decodeCover(it.path, w, h) }
        if (pic == null) {
            theme.wallpaper.paint(c, w.toFloat(), h.toFloat())
            return
        }
        val scale = maxOf(w.toFloat() / pic.width, h.toFloat() / pic.height)
        val dw = pic.width * scale; val dh = pic.height * scale
        val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
        c.drawBitmap(pic, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        pic.recycle()
    }

    /** A picture file decoded at about [w] × [h] (never much bigger), or null. */
    private fun decodeCover(path: String, w: Int, h: Int): Bitmap? {
        val f = java.io.File(path)
        if (!f.isFile) return null
        val b = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, b)
        if (b.outWidth <= 0 || b.outHeight <= 0) return null
        var sample = 1
        while (b.outWidth / (sample * 2) >= w && b.outHeight / (sample * 2) >= h) sample *= 2
        return android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun render(theme: WidgetTheme, w: Int): Bitmap {
        val h = (w * ASPECT).roundToInt()
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val wf = w.toFloat(); val hf = h.toFloat()
        paintBackground(c, theme, w, h)
        val d = wf / PHONE_DP
        statusBar(c, wf, d)

        val left = 10 * d; val top = 40 * d
        val right = wf - 10 * d; val bottom = hf - 112 * d
        val cells = SetLayout.pack(theme.widgets.map { it.cols to it.rows })
        val (cw, ch) = SetLayout.cellSize(right - left, bottom - top, 4, SetLayout.rowsUsed(cells))
        val boxes = SetLayout.boxes(cells, left, top, cw, ch)
        val today = LocalDate.now()
        val hud = HudData.sample(today)
        boxes.forEachIndexed { i, b ->
            val sw = theme.widgets[i]
            val style = theme.styleForSetWidget(i, today)
            c.withTranslation(b.left, b.top) {
                ThemeArt.drawWidget(this, theme, sw.kind, style.themeSlot, b.width, b.height, d, style, hud)
                // Kinds a theme HUD draws are complete already; clocks still get their (sample) time on top.
                val mode = theme.hudMode(sw.kind)
                if (mode == null || mode == HudMode.BEHIND) {
                    val content = ThemeArt.contentRect(theme, ThemeArt.cardRect(theme, b.width, b.height, d), d)
                    MiniContent(this, theme, style, d, today).draw(sw.kind, content)
                }
            }
        }
        dock(c, theme, wf, hf, d)
        return bmp
    }

    private fun statusBar(c: Canvas, w: Float, d: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEEFFFFFF.toInt(); textSize = 12 * d; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(2 * d, 0f, 0f, 0x55000000) }
        c.drawText("9:41", 22 * d, 24 * d, p)
        c.drawRoundRect(RectF(w - 44 * d, 14 * d, w - 22 * d, 25 * d), 3 * d, 3 * d, p)
    }

    /** Four themed app icons at the bottom so the preview reads as a home screen. */
    private fun dock(c: Canvas, theme: WidgetTheme, w: Float, h: Float, d: Float) {
        val size = 54 * d
        val gap = (w - 4 * size) / 5
        val y = h - 30 * d - size
        val ink = ThemeArt.ink(theme)
        val glyphs = listOf(Sticker.PHONE, Sticker.ENVELOPE, Sticker.CAMERA, Sticker.HEART)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.icons?.bg?.firstOrNull() ?: theme.palette.background }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * d; color = theme.palette.line }
        glyphs.forEachIndexed { i, g ->
            val x = gap + i * (size + gap)
            val r = RectF(x, y, x + size, y + size)
            c.drawRoundRect(r, size * 0.3f, size * 0.3f, fill)
            c.drawRoundRect(r, size * 0.3f, size * 0.3f, line)
            ThemeArt.drawSticker(c, g, r.centerX(), r.centerY(), size * 0.62f, ink)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
        c.drawCircle(w / 2 - 6 * d, y - 14 * d, 2.5f * d, dot)
        dot.alpha = 110
        c.drawCircle(w / 2 + 6 * d, y - 14 * d, 2.5f * d, dot)
    }
}

/** Typeface for a widget font, as close to Glance's families as Canvas allows. */
internal fun WidgetFont.typeface(bold: Boolean): Typeface {
    val style = if (bold) Typeface.BOLD else Typeface.NORMAL
    return when (this) {
        WidgetFont.SANS -> Typeface.create(Typeface.SANS_SERIF, style)
        WidgetFont.SERIF -> Typeface.create(Typeface.SERIF, style)
        WidgetFont.MONO -> Typeface.create(Typeface.MONOSPACE, style)
        WidgetFont.CURSIVE -> Typeface.create("cursive", style)
        // Bundled faces are already as heavy as they get; never fake-bold them.
        else -> WidgetFonts.typeface(this)
    }
}

/** Draws sample content for one mini widget with Canvas text. */
private class MiniContent(val c: Canvas, val theme: WidgetTheme, val ws: WidgetStyle, val d: Float, val today: LocalDate) {
    val fg = theme.palette.text
    val accent = if (theme.frame == FrameStyle.FROSTED) theme.palette.text else theme.palette.accent
    val loc: Locale = Locale.getDefault()

    fun text(size: Float, bold: Boolean = false, alpha: Float = 1f, align: Paint.Align = Paint.Align.CENTER) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fg; this.alpha = (alpha * 255).toInt(); textSize = size; typeface = ws.font.typeface(bold); textAlign = align
        if (ws.textShadow) setShadowLayer(1.5f * d, 0.8f * d, 0.8f * d, ws.shadowColor)
    }

    /** Shrinks [p] until [s] fits [maxW]. */
    fun fit(p: Paint, s: String, maxW: Float): Paint {
        val wNow = p.measureText(s)
        if (wNow > maxW && wNow > 0) p.textSize *= maxW / wNow
        return p
    }

    /** Draws lines stacked and centered vertically in [r]. Each pair: text to paint. */
    fun stack(r: RectF, lines: List<Pair<String, Paint>>, gap: Float = 2 * d) {
        val heights = lines.map { (_, p) -> p.fontMetrics.let { it.descent - it.ascent } }
        var y = r.centerY() - (heights.sum() + gap * (lines.size - 1)) / 2
        lines.forEachIndexed { i, (s, p) ->
            fit(p, s, r.width())
            val fm = p.fontMetrics
            c.drawText(s, r.centerX(), y - fm.ascent, p)
            y += heights[i] + gap
        }
    }

    fun bar(r: RectF, y: Float, frac: Float) {
        val h = 6 * d
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (fg and 0x00FFFFFF) or 0x33000000 }
        val full = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        c.drawRoundRect(RectF(r.left, y, r.right, y + h), h / 2, h / 2, track)
        c.drawRoundRect(RectF(r.left, y, r.left + r.width() * frac.coerceIn(0.05f, 1f), y + h), h / 2, h / 2, full)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = 1f * d; color = theme.palette.line; alpha = 90 }
        c.drawRoundRect(RectF(r.left, y, r.right, y + h), h / 2, h / 2, edge)
    }

    fun ring(cx: Float, cy: Float, rad: Float, frac: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = rad * 0.2f; strokeCap = Paint.Cap.ROUND; color = (fg and 0x00FFFFFF) or 0x33000000 }
        val o = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
        c.drawArc(o, 0f, 360f, false, p)
        p.color = accent
        c.drawArc(o, -90f, 360f * frac, false, p)
    }

    fun draw(kind: WidgetKind, r: RectF) {
        if (r.width() < 8 || r.height() < 8) return
        val h = r.height()
        when (kind) {
            WidgetKind.CLOCK -> if (ws.font == WidgetFont.HEAVY) {
                // Matches clock_big_heavy.xml: big time, date on the bottom edge.
                val date = today.format(DateTimeFormatter.ofPattern("EEEE, d MMM", loc)).uppercase(loc)
                val dp = fit(text(min(h * 0.13f, 14 * d)), date, r.width())
                c.drawText(date, r.centerX(), r.bottom - dp.fontMetrics.descent, dp)
                val dh = dp.fontMetrics.let { it.descent - it.ascent }
                stack(RectF(r.left, r.top, r.right, r.bottom - dh), listOf("10:08" to text(min(h * 0.5f, r.width() / 2.2f))))
            } else stack(r, listOf(
                "10:08" to text(min(h * 0.45f, r.width() / 2.6f), bold = true),
                today.format(DateTimeFormatter.ofPattern("EEE, d MMM", loc)) to text(min(h * 0.13f, 14 * d), alpha = 0.85f),
            ))
            WidgetKind.HOUR -> stack(r, listOf(
                "10:08" to text(min(h * 0.45f, r.width() / 2.6f), bold = true),
                today.format(DateTimeFormatter.ofPattern("EEE, d MMM", loc)) to text(min(h * 0.13f, 14 * d), alpha = 0.85f),
            ))
            WidgetKind.BANNER -> {
                val lines = mutableListOf(ws.bannerText to text(min(h * (if (ws.bannerSub.isBlank()) 0.6f else 0.45f), 30 * d), bold = true))
                if (ws.bannerSub.isNotBlank()) lines += ws.bannerSub to text(min(h * 0.2f, 13 * d), alpha = 0.85f)
                // Keep the words clear of the side stickers.
                stack(RectF(r.left + r.width() * 0.12f, r.top, r.right - r.width() * 0.12f, r.bottom), lines)
            }
            WidgetKind.PHOTO, WidgetKind.CAROUSEL -> Unit // the set's art fills it
            WidgetKind.CALENDAR -> calendar(r)
            WidgetKind.STEPS -> {
                val rad = min(h, r.width()) * 0.42f
                ring(r.centerX(), r.centerY(), rad, 0.44f)
                stack(RectF(r.centerX() - rad, r.centerY() - rad, r.centerX() + rad, r.centerY() + rad), listOf(
                    "3,482" to text(rad * 0.45f, bold = true),
                    "steps" to text(rad * 0.28f, alpha = 0.75f),
                ), 0f)
            }
            WidgetKind.BATTERY -> {
                stack(RectF(r.left, r.top, r.right, r.bottom - 10 * d), listOf("Battery" to text(min(h * 0.18f, 12 * d), alpha = 0.8f), "76%" to text(min(h * 0.4f, 30 * d), bold = true)))
                bar(r, r.bottom - 7 * d, 0.76f)
            }
            WidgetKind.BATTERY_RING -> {
                val rad = min(h, r.width()) * 0.42f
                ring(r.centerX(), r.centerY(), rad, 0.76f)
                stack(RectF(r.centerX() - rad, r.centerY() - rad, r.centerX() + rad, r.centerY() + rad), listOf("76%" to text(rad * 0.5f, bold = true)))
            }
            WidgetKind.DATE -> {
                val pct = (WidgetLogic.yearProgress(today) * 100).roundToInt()
                stack(RectF(r.left, r.top, r.right, r.bottom - 10 * d), listOf(
                    "${today.year}" to text(min(h * 0.18f, 12 * d), alpha = 0.8f),
                    "$pct% passed" to text(min(h * 0.32f, 24 * d), bold = true),
                ))
                bar(r, r.bottom - 7 * d, pct / 100f)
            }
            WidgetKind.COUNTDOWN -> {
                val target = WidgetLogic.countdownTarget(ws.countdownDay, today)
                stack(r, listOf(
                    ws.countdownTitle to text(min(h * 0.2f, 13 * d), alpha = 0.85f),
                    WidgetLogic.countdownText(WidgetLogic.daysUntil(target, today)) to text(min(h * 0.38f, 28 * d), bold = true),
                ))
            }
            WidgetKind.APPS -> {
                val n = if (r.width() > r.height() * 3.2f) 4 else 3
                val size = min(r.height() * 0.8f, r.width() / n * 0.78f)
                val ink = ThemeArt.ink(theme)
                val glyphs = listOf(Sticker.PHONE, Sticker.CAMERA, Sticker.ENVELOPE, Sticker.HEART)
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.palette.secondary }
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = 1.2f * d; color = theme.palette.line }
                for (i in 0 until n) {
                    val cx = r.left + r.width() * (i + 0.5f) / n
                    val box = RectF(cx - size / 2, r.centerY() - size / 2, cx + size / 2, r.centerY() + size / 2)
                    c.drawRoundRect(box, size * 0.3f, size * 0.3f, fill)
                    c.drawRoundRect(box, size * 0.3f, size * 0.3f, line)
                    ThemeArt.drawSticker(c, glyphs[i], cx, r.centerY(), size * 0.62f, ink)
                }
            }
            WidgetKind.NOTE -> stack(r, listOf(ws.note.take(28) to text(min(h * 0.18f, 15 * d))))
            WidgetKind.QUOTE -> stack(r, listOf("“Little by little”" to text(min(h * 0.2f, 15 * d)), "Tolkien" to text(min(h * 0.14f, 11 * d), alpha = 0.7f)))
            else -> stack(r, listOf(kind.label to text(min(h * 0.22f, 15 * d), bold = true)))
        }
    }

    private fun calendar(r: RectF) {
        val title = today.month.getDisplayName(TextStyle.FULL, loc)
        val titleP = text(min(r.height() * 0.11f, 14 * d), bold = true)
        fit(titleP, title, r.width())
        val tfm = titleP.fontMetrics
        c.drawText(title, r.centerX(), r.top - tfm.ascent, titleP)
        val grid = WidgetLogic.monthGrid(today, true)
        val heads = WidgetLogic.weekdayInitials(true, loc)
        val top = r.top + (tfm.descent - tfm.ascent) + 2 * d
        val rows = grid.size + 1
        val cellH = (r.bottom - top) / rows
        val cellW = r.width() / 7
        val p = text(min(cellH * 0.62f, cellW * 0.6f))
        val head = text(min(cellH * 0.62f, cellW * 0.6f), bold = true, alpha = 0.6f)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        fun cy(row: Int) = top + cellH * (row + 0.5f) - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2
        heads.forEachIndexed { i, s -> c.drawText(s, r.left + cellW * (i + 0.5f), cy(0), head) }
        grid.forEachIndexed { ri, week ->
            week.forEachIndexed { ci, day ->
                if (day == 0) return@forEachIndexed
                val x = r.left + cellW * (ci + 0.5f)
                if (day == today.dayOfMonth) {
                    c.drawCircle(x, top + cellH * (ri + 1.5f), min(cellW, cellH) * 0.48f, dot)
                    val onDot = Paint(p).apply { color = if (accent == fg) theme.palette.line else fg; clearShadowLayer() }
                    c.drawText("$day", x, cy(ri + 1), onDot)
                } else c.drawText("$day", x, cy(ri + 1), p)
            }
        }
    }
}
