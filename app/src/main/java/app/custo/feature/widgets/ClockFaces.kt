package app.custo.feature.widgets

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.AlarmClock
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import app.custo.R
import app.custo.feature.widgets.TextFit.Line
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min

/**
 * Sizing and words for the clock faces added after [ClockLook.MINIMAL]. Pure (no Android), so it's unit tested.
 * Text sizes here must match the layouts written by tools/gen_widget_layouts.py.
 */
object ClockFaces {
    const val FLIP_SP = 52f
    const val LED_SP = 48f
    const val NEON_SP = 52f
    const val VERTICAL_SP = 34f
    const val STACKED_SP = 64f
    const val WORD_SP = 26f
    const val LEAD_SP = 13f

    /** What a face needs at full size: [w] × [h] dp of text that scales, plus [fixedW] × [fixedH] dp that doesn't. */
    data class Need(val w: Float, val h: Float, val fixedW: Float = 0f, val fixedH: Float = 0f)

    /** The face actually drawn (a cramped vertical clock falls back to [ClockLook.STACKED]) and its text scale. */
    data class Plan(val look: ClockLook, val fit: Float)

    private fun w(size: Float, chars: Int, shown: Boolean = true) = TextFit.width(Line(size, chars, shown))
    private fun h(size: Float, shown: Boolean = true) = TextFit.height(Line(size, 1, shown))

    fun need(look: ClockLook, showDate: Boolean): Need = when (look) {
        // Two cards of two digits (6 dp padding each side, 5 dp apart), the date under them.
        ClockLook.FLIP -> Need(max(2 * w(FLIP_SP, 2), w(13f, 10, showDate)), h(FLIP_SP) + h(13f, showDate), 2 * 12f + 5f, 4f + if (showDate) 4f else 0f)
        ClockLook.LED -> Need(max(w(LED_SP, 5), w(13f, 12, showDate)), h(LED_SP) + h(13f, showDate), 8f, if (showDate) 2f else 0f)
        // The glow needs room: 10 dp each side, 3 dp above and below.
        ClockLook.NEON -> Need(max(w(NEON_SP, 5), w(13f, 13, showDate)), h(NEON_SP) + h(13f, showDate), 20f, 6f)
        // One digit per line (letter-spaced, so a little wider than a digit), four lines.
        ClockLook.VERTICAL -> Need(w(VERTICAL_SP, 2), 4 * h(VERTICAL_SP) + h(11f, showDate), 0f, 2f + if (showDate) 4f else 0f)
        ClockLook.STACKED -> Need(max(w(STACKED_SP, 2), w(14f, 6, showDate)), 2 * h(STACKED_SP) + h(14f, showDate))
        ClockLook.MINIMAL -> Need(max(w(34f, 8), w(14f, 9, showDate)), h(34f) + h(14f, showDate))
        ClockLook.BIG, ClockLook.WORDS -> Need(max(w(56f, 5), w(15f, 17, showDate)), h(56f) + h(15f, showDate))
    }

    /** [area] as seen by text in [font]: wide faces get less room, narrow ones more. */
    fun forFont(area: DpSize, font: WidgetFont): DpSize = DpSize((area.width.value * 0.62f / font.charWidth).dp, area.height)

    fun fit(look: ClockLook, area: DpSize, textScale: Float, showDate: Boolean): Float {
        val n = need(look, showDate)
        return TextFit.box(area, textScale, n.w, n.h, n.fixedW, n.fixedH)
    }

    /** Picks the face and scale for [area] (already adjusted with [forFont]). */
    fun plan(look: ClockLook, area: DpSize, textScale: Float, showDate: Boolean): Plan {
        val f = fit(look, area, textScale, showDate)
        // A vertical clock in a short, wide widget would be tiny: stack hours over minutes instead.
        if (look == ClockLook.VERTICAL && f < 0.5f && area.height < area.width) {
            return Plan(ClockLook.STACKED, fit(ClockLook.STACKED, area, textScale, showDate))
        }
        return Plan(look, f)
    }

    /** The font a face really uses ([ClockLook.LED] is always the pixel face). */
    fun fontOf(look: ClockLook, font: WidgetFont): WidgetFont = if (look == ClockLook.LED) WidgetFont.PIXEL else font
}

/** "Half past ten": the time in words, to the nearest five minutes, with "almost" / "just after" in between. */
object WordClock {
    private val HOURS = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven")
    private val MINUTES = mapOf(5 to "five", 10 to "ten", 15 to "quarter", 20 to "twenty", 25 to "twenty-five", 30 to "half")

    /** A small lead-in line ("almost", "just after" or none) and the big lines. */
    data class Words(val lead: String?, val lines: List<String>) {
        val text: String get() = (listOfNotNull(lead) + lines).joinToString(" ")
    }

    fun words(hour: Int, minute: Int): Words {
        val m = minute.coerceIn(0, 59)
        val nearest = (m + 2) / 5 * 5
        val diff = m - nearest
        var h = hour
        var m5 = nearest
        if (m5 == 60) { m5 = 0; h += 1 }
        val lead = when { diff < 0 -> "almost"; diff > 0 -> "just after"; else -> null }
        val name = HOURS[Math.floorMod(h, 12)]
        val next = HOURS[Math.floorMod(h + 1, 12)]
        val lines = when {
            m5 == 0 -> listOf(name, "o'clock")
            m5 <= 30 -> listOf("${MINUTES.getValue(m5)} past", name)
            else -> listOf("${MINUTES.getValue(60 - m5)} to", next)
        }
        return Words(lead, lines)
    }

    /** Lines to draw and their scale: the phrase as is, or one word per line when that fits bigger. */
    fun arrange(words: Words, area: DpSize, textScale: Float): Pair<List<String>, Float> {
        val asIs = words.lines
        val split = words.lines.flatMap { it.split(' ') }
        fun fit(lines: List<String>): Float {
            val lead = Line(ClockFaces.LEAD_SP, words.lead?.length ?: 0, words.lead != null)
            val big = lines.map { Line(ClockFaces.WORD_SP, it.length) }
            val needW = max(TextFit.width(lead), big.maxOf { TextFit.width(it) })
            val needH = TextFit.height(lead) + big.sumOf { TextFit.height(it).toDouble() }.toFloat()
            return TextFit.box(area, textScale, needW, needH)
        }
        val a = fit(asIs)
        val b = fit(split)
        return if (b > a * 1.08f) split to b else asIs to a
    }
}

// ---------- Glance ----------

/** Draws [ClockLook.FLIP], [ClockLook.LED], [ClockLook.NEON], [ClockLook.VERTICAL] and [ClockLook.WORDS]. */
@Composable
internal fun ClockFaceContent(context: Context, id: GlanceId, style: WidgetStyle) {
    val tap = tapStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    val font = ClockFaces.fontOf(style.clock, style.font)
    val area = ClockFaces.forFont(contentSize(style), font)
    if (style.clock == ClockLook.WORDS) {
        WordsFace(context, id, style, area, tap)
        return
    }
    val plan = ClockFaces.plan(style.clock, area, style.textScale, style.showDate)
    val layout = when (plan.look) {
        ClockLook.FLIP -> FontLayouts.flip(font)
        ClockLook.LED -> R.layout.clock_led
        ClockLook.NEON -> FontLayouts.neon(font)
        ClockLook.VERTICAL -> FontLayouts.vertical(font)
        else -> fontLayout(font, R.layout.clock_stacked_sans, R.layout.clock_stacked_serif, R.layout.clock_stacked_mono, R.layout.clock_stacked_cursive, R.layout.clock_stacked_pixel, R.layout.clock_stacked_heavy)
    }
    val rv = RemoteViews(context.packageName, layout).apply {
        val f = plan.fit
        when (plan.look) {
            ClockLook.FLIP -> styleText(style, R.id.time to ClockFaces.FLIP_SP, R.id.time2 to ClockFaces.FLIP_SP, R.id.date to 13f, fit = f)
            ClockLook.LED -> styleText(style, R.id.ghost to ClockFaces.LED_SP, R.id.time to ClockFaces.LED_SP, R.id.date to 13f, fit = f)
            ClockLook.NEON -> styleText(style, R.id.time to ClockFaces.NEON_SP, R.id.date to 13f, fit = f)
            ClockLook.VERTICAL -> styleText(style, R.id.time to ClockFaces.VERTICAL_SP, R.id.time2 to ClockFaces.VERTICAL_SP, R.id.date to 11f, fit = f)
            else -> styleText(style, R.id.time to ClockFaces.STACKED_SP, R.id.time2 to ClockFaces.STACKED_SP, R.id.date to 14f, fit = f)
        }
        setViewVisibility(R.id.date, if (style.showDate) View.VISIBLE else View.GONE)
        setInt(R.id.root, "setGravity", style.gravity())
    }
    val size = LocalSize.current
    Frame(style, tap) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (plan.look == ClockLook.NEON) {
                // A soft halo in the text color behind the tubes (the TextView glow itself is white).
                Image(ImageProvider(FaceArt.glow), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.tint(fgProvider(style, 0.42f)))
            }
            AndroidRemoteViews(rv, GlanceModifier.fillMaxSize())
            if (plan.look == ClockLook.LED) {
                val grid = remember(size) { FaceArt.ledGrid(context, size.width.value, size.height.value) }
                Image(ImageProvider(grid), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
        }
    }
}

/** The time in words. Refreshed by an inexact, non-waking alarm a moment after each minute. */
@Composable
private fun WordsFace(context: Context, id: GlanceId, style: WidgetStyle, area: DpSize, tap: Tap) {
    val now = LocalTime.now()
    val words = WordClock.words(now.hour, now.minute)
    val (lines, fit) = WordClock.arrange(words, area, style.textScale)
    val next = System.currentTimeMillis().let { it - it % 60_000 + 60_000 }
    LaunchedEffect(next) {
        // refreshAt adds a few seconds; aim just past the minute.
        WidgetAlarms.refreshAt(context, GlanceAppWidgetManager(context).getAppWidgetId(id), WidgetKind.CLOCK, next - 4_000)
    }
    Frame(style, tap) {
        CompositionLocalProvider(LocalTextFit provides fit) {
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = style.hAlign.glance()) {
                words.lead?.let { WText(it, GlanceModifier.fillMaxWidth(), style.text(ClockFaces.LEAD_SP, alpha = 0.75f), maxLines = 1) }
                lines.forEach { WText(it, GlanceModifier.fillMaxWidth(), style.text(ClockFaces.WORD_SP, bold = true), maxLines = 1) }
            }
        }
    }
}

/** Small bitmaps for the clock faces. */
internal object FaceArt {
    /** White radial halo, tinted by the caller; small on purpose (it's stretched, and blur looks right). */
    val glow: Bitmap by lazy {
        val w = 160; val h = 90
        createBitmap(w, h).also { b ->
            val c = Canvas(b)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(w / 2f, h / 2f, w / 2f, intArrayOf(0xFFFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.save(); c.scale(1f, h.toFloat() / w, w / 2f, h / 2f)
            c.drawCircle(w / 2f, h / 2f, w / 2f, p)
            c.restore()
        }
    }

    /** A fine dark grid over the LED face, so the digits read as lit dots behind a panel. */
    fun ledGrid(ctx: Context, wDp: Float, hDp: Float): Bitmap {
        val d = min(ctx.resources.displayMetrics.density, 2f)
        val w = (wDp.coerceAtLeast(8f) * d).toInt().coerceIn(8, 1000)
        val h = (hDp.coerceAtLeast(8f) * d).toInt().coerceIn(8, 1000)
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val p = Paint().apply { color = 0x47000000; strokeWidth = max(1f, d * 0.8f) }
        val step = 3f * d
        var x = step / 2
        while (x < w) { c.drawLine(x, 0f, x, h.toFloat(), p); x += step }
        var y = step / 2
        while (y < h) { c.drawLine(0f, y, w.toFloat(), y, p); y += step }
        return bmp
    }
}
