package app.custo.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.os.SystemClock
import android.provider.AlarmClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.custo.MainActivity
import app.custo.container
import app.custo.R
import app.custo.feature.widgetsets.HudData
import app.custo.feature.widgetsets.HudMode
import app.custo.feature.widgetsets.ThemeArt
import app.custo.feature.widgetsets.WidgetTheme
import app.custo.feature.widgetsets.WidgetThemes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ---------- Shared styling ----------

private fun sys(ctx: Context, res: Int) = Color(ctx.getColor(res))

/** Background: themed (Material You, follows day/night) or a custom color, with transparency. */
@Composable
internal fun bgProvider(style: WidgetStyle, alpha: Float = style.alpha): ColorProvider {
    val ctx = LocalContext.current
    return if (style.themed) {
        androidx.glance.color.ColorProvider(
            day = sys(ctx, android.R.color.system_accent1_100).copy(alpha = alpha),
            night = sys(ctx, android.R.color.system_accent1_800).copy(alpha = alpha),
        )
    } else androidx.glance.color.ColorProvider(Color(style.bg).copy(alpha = alpha), Color(style.bg).copy(alpha = alpha))
}

@Composable
internal fun fgProvider(style: WidgetStyle, alpha: Float = 1f): ColorProvider {
    val ctx = LocalContext.current
    return if (style.themed) {
        androidx.glance.color.ColorProvider(
            day = sys(ctx, android.R.color.system_accent1_900).copy(alpha = alpha),
            night = sys(ctx, android.R.color.system_accent1_100).copy(alpha = alpha),
        )
    } else androidx.glance.color.ColorProvider(Color(style.fg).copy(alpha = alpha), Color(style.fg).copy(alpha = alpha))
}

internal fun WidgetFont.glance(): FontFamily = when (this) {
    WidgetFont.SANS -> FontFamily.SansSerif
    WidgetFont.SERIF -> FontFamily.Serif
    WidgetFont.MONO -> FontFamily.Monospace
    WidgetFont.CURSIVE -> FontFamily.Cursive
    // Glance can't load app fonts: these names are markers that [WText] draws with a font TextView.
    else -> FontFamily(MARKER + name)
}

private const val MARKER = "custo-font-"

/** The RemoteViews text layout for a bundled-font marker family, or null for system families. */
private fun FontFamily?.fontTextLayout(): Int? {
    val name = this?.family?.takeIf { it.startsWith(MARKER) }?.removePrefix(MARKER) ?: return null
    return WidgetFont.entries.firstOrNull { it.name == name }?.let { FontLayouts.text[it] }
}

/** Text in a bundled font: a RemoteViews TextView whose layout names the font. */
@Composable
private fun FontText(layout: Int, text: String, modifier: GlanceModifier, style: TextStyle, maxLines: Int, color: ColorProvider? = null) {
    val ctx = LocalContext.current
    val rv = RemoteViews(ctx.packageName, layout).apply {
        setTextViewText(R.id.text, text)
        setTextColor(R.id.text, (color ?: style.color).getColor(ctx).toArgb())
        setTextViewTextSize(R.id.text, TypedValue.COMPLEX_UNIT_SP, style.fontSize?.value ?: 14f)
        setInt(R.id.text, "setMaxLines", maxLines.coerceIn(1, 20))
        val g = when (style.textAlign) {
            TextAlign.Start, TextAlign.Left -> Gravity.START
            TextAlign.End, TextAlign.Right -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }
        setInt(R.id.text, "setGravity", g or Gravity.CENTER_VERTICAL)
    }
    AndroidRemoteViews(rv, modifier)
}

internal fun HAlign.glance(): Alignment.Horizontal = when (this) {
    HAlign.START -> Alignment.Horizontal.Start
    HAlign.CENTER -> Alignment.Horizontal.CenterHorizontally
    HAlign.END -> Alignment.Horizontal.End
}

internal fun VAlign.glance(): Alignment.Vertical = when (this) {
    VAlign.TOP -> Alignment.Vertical.Top
    VAlign.CENTER -> Alignment.Vertical.CenterVertically
    VAlign.BOTTOM -> Alignment.Vertical.Bottom
}

internal fun HAlign.text(): TextAlign = when (this) {
    HAlign.START -> TextAlign.Start
    HAlign.CENTER -> TextAlign.Center
    HAlign.END -> TextAlign.End
}

internal fun WidgetStyle.gravity(): Int =
    (when (hAlign) { HAlign.START -> Gravity.START; HAlign.CENTER -> Gravity.CENTER_HORIZONTAL; HAlign.END -> Gravity.END }) or
        (when (vAlign) { VAlign.TOP -> Gravity.TOP; VAlign.CENTER -> Gravity.CENTER_VERTICAL; VAlign.BOTTOM -> Gravity.BOTTOM })

internal fun WidgetStyle.sp(base: Float): TextUnit = (base * textScale).sp

/**
 * How much the widget's text must shrink to fit its real size, set by [Frame]. 1 = full size.
 * Big numbers take the full factor; small labels shrink at most to 80% so they stay readable.
 */
internal val LocalTextFit = compositionLocalOf { 1f }

/** Reference content area a widget's base text sizes were designed for (a 2×2 widget). */
private const val REF_W = 150f
private const val REF_H = 120f

@Composable
private fun frameFit(style: WidgetStyle): Float {
    val a = contentSize(style)
    return minOf(1f, a.width.value / REF_W, a.height.value / REF_H).coerceAtLeast(0.35f)
}

/** Text style in the widget's font, color and scale. */
@Composable
internal fun WidgetStyle.text(size: Float, bold: Boolean = false, alpha: Float = 1f): TextStyle = TextStyle(
    color = fgProvider(this, alpha),
    fontSize = sp(size * LocalTextFit.current.let { f -> if (size >= 24f) f else maxOf(f, 0.8f) }),
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontFamily = font.glance(),
    textAlign = hAlign.text(),
)

/** Text shadow color for the widget being drawn, or null for none. Set by [Frame]. */
internal val LocalShadow = compositionLocalOf<Int?> { null }

/**
 * Glance text with the widget's optional shadow. RemoteViews can't set a shadow layer,
 * so the shadow is the same text drawn again, offset, underneath.
 */
@Composable
internal fun WText(text: String, modifier: GlanceModifier = GlanceModifier, style: TextStyle = TextStyle(), maxLines: Int = Int.MAX_VALUE) {
    val shadow = LocalShadow.current
    val fontLayout = style.fontFamily.fontTextLayout()
    if (fontLayout != null) {
        if (shadow == null) FontText(fontLayout, text, modifier, style, maxLines)
        else Box(modifier) {
            val o = 1.5.dp
            FontText(fontLayout, text, GlanceModifier.fillMaxWidth().padding(start = o, top = o), style, maxLines, ColorProvider(Color(shadow)))
            FontText(fontLayout, text, GlanceModifier.fillMaxWidth().padding(end = o, bottom = o), style, maxLines)
        }
        return
    }
    if (shadow == null) {
        Text(text, modifier, style, maxLines)
        return
    }
    val align = when (style.textAlign) {
        TextAlign.Start, TextAlign.Left -> Alignment.TopStart
        TextAlign.End, TextAlign.Right -> Alignment.TopEnd
        else -> Alignment.TopCenter
    }
    val o = 1.5.dp
    Box(modifier, contentAlignment = align) {
        Text(text, GlanceModifier.padding(start = o, top = o), style.copy(color = ColorProvider(Color(shadow))), maxLines)
        Text(text, GlanceModifier.padding(end = o, bottom = o), style, maxLines)
    }
}

/** Rough size buckets for responsive layouts. */
enum class SizeClass { SMALL, WIDE, TALL, LARGE }

@Composable
internal fun sizeClass(): SizeClass {
    val s = LocalSize.current
    return sizeClassOf(s.width.value, s.height.value)
}

/** One-row heights (under 100 dp) always get the compact layout, even when wide. */
internal fun sizeClassOf(widthDp: Float, heightDp: Float): SizeClass {
    if (heightDp < 100f) return SizeClass.SMALL
    val w = widthDp >= 200f
    val h = heightDp >= 200f
    return when { w && h -> SizeClass.LARGE; w -> SizeClass.WIDE; h -> SizeClass.TALL; else -> SizeClass.SMALL }
}

@Composable
internal fun currentStyle(): WidgetStyle = WidgetStyle.decode(currentState(Widgets.STYLE_KEY))

/**
 * Shared widget shell: background (flat color, or a drawn gradient/border bitmap), corners,
 * padding, alignment and the tap action.
 */
@Composable
internal fun Frame(style: WidgetStyle, onClick: Tap?, padded: Boolean = true, content: @Composable () -> Unit) {
    val theme = WidgetThemes.byId(style.themeId)
    val fit = frameFit(style)
    androidx.compose.runtime.CompositionLocalProvider(LocalTextFit provides fit) {
        if (theme != null) ThemedFrame(style, theme, onClick, padded, content)
        else PlainFrame(style, onClick, padded, content)
    }
}

/** Accent for bars and rings: the set's accent when the widget belongs to one, else the text color. */
@Composable
internal fun accentProvider(style: WidgetStyle): ColorProvider {
    val theme = WidgetThemes.byId(style.themeId) ?: return fgProvider(style)
    val c = Color(if (theme.frame == app.custo.feature.widgetsets.FrameStyle.FROSTED) theme.palette.text else theme.palette.accent)
    return androidx.glance.color.ColorProvider(c, c)
}

/** A widget from a set: the theme's frame and stickers as one bitmap, live content on top. */
@Composable
private fun ThemedFrame(style: WidgetStyle, theme: WidgetTheme, onClick: Tap?, padded: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val size = LocalSize.current
    val kind = remember(style.themeKind) { WidgetKind.entries.firstOrNull { it.name == style.themeKind } }
    // A HUD draws some kinds' content into the bitmap (hearts, hotbars...), from live values.
    val mode = if (padded) theme.hudMode(kind) else null
    val hud = if (mode != null) readBattery(ctx).let { b -> HudData(java.time.LocalDate.now(), b.level, b.charging, ctx.resources.configuration.locales[0]) } else null
    val hudStyle = if (mode != null) style else null
    TapSurface(style, onClick, GlanceModifier.fillMaxSize()) {
        val bmp = remember(theme.id, theme.palette, theme.chosenWall, kind, style.themeSlot, size, hud, hudStyle) {
            ThemeArt.widgetBitmap(ctx, theme, kind, style.themeSlot, size.width.value, size.height.value, hudStyle, hud)
        }
        Image(ImageProvider(bmp), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        val f = theme.frame
        val k = if (padded) 1f else 0f
        val window = if (padded) 0f else 3f
        Box(
            GlanceModifier.fillMaxSize().padding(
                start = (f.outerSide + f.innerSide * k + window).dp, top = (f.outerTop + f.innerTop * k + window).dp,
                end = (f.outerSide + f.innerSide * k + window).dp, bottom = (f.outerBottom + f.innerBottom * k + window).dp,
            ),
            contentAlignment = Alignment(style.hAlign.glance(), style.vAlign.glance()),
        ) {
            if (mode != HudMode.REPLACE) {
                CompositionLocalProvider(LocalShadow provides (if (style.textShadow) style.shadowColor else null)) { content() }
            }
        }
        TapFlash(style, theme.corner.toInt())
    }
}

/** A light flash over the whole widget while a [TapFx.WHOLE] effect plays (a still view otherwise). */
@Composable
private fun TapFlash(style: WidgetStyle, corner: Int) {
    if (!style.tapAnim) return
    val a = TapFrames.flash(tapFrame(TapFx.WHOLE))
    Box(GlanceModifier.fillMaxSize().cornerRadius(corner.dp).background(fgProvider(style, a))) {}
}

@Composable
private fun PlainFrame(style: WidgetStyle, onClick: Tap?, padded: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val size = LocalSize.current
    var m = GlanceModifier.fillMaxSize().cornerRadius(style.corner.dp)
    // Drawn shapes and fills (pill, circle, glass, photo...) come entirely from the bitmap.
    if (!(style.gradient && !style.themed) && !style.drawnShape) m = m.background(bgProvider(style))
    val (insetX, insetY) = if (style.drawnShape) WidgetShapes.contentInset(style.shape, size.width.value, size.height.value) else 0f to 0f
    TapSurface(style, onClick, GlanceModifier.fillMaxSize().cornerRadius(style.corner.dp)) { Box(m) {
        if (style.needsBitmapBg) {
            val bmp = remember(style, size) { WidgetArt.background(ctx, style, size.width.value, size.height.value) }
            Image(ImageProvider(bmp), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        val pad = (if (padded) style.padding else 0).toFloat()
        Box(
            GlanceModifier.fillMaxSize().padding(horizontal = (pad + insetX).dp, vertical = (pad + insetY).dp),
            contentAlignment = Alignment(style.hAlign.glance(), style.vAlign.glance()),
        ) {
            CompositionLocalProvider(LocalShadow provides (if (style.textShadow) style.shadowColor else null)) { content() }
        }
        TapFlash(style, style.corner)
    } }
}

internal fun editIntent(ctx: Context, glanceId: GlanceId): Intent {
    val id = GlanceAppWidgetManager(ctx).getAppWidgetId(glanceId)
    return Intent(ctx, MainActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}

/** Colors and scales TextViews/TextClocks inside a RemoteViews layout. */
/**
 * Colors and sizes system-drawn text. [fit] (from [TextFit]) shrinks everything together so it
 * stays inside the widget. Sizes are in dp, so a large system font size can't push digits out.
 */
internal fun RemoteViews.styleText(style: WidgetStyle, vararg views: Pair<Int, Float>, fit: Float = 1f) {
    views.forEach { (id, base) ->
        if (style.themed) setColor(id, "setTextColor", R.color.widget_fg) else setTextColor(id, style.fg)
        setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, base * style.textScale * fit)
    }
}

/** The widget's current size minus its frame, so text can be fitted to the real space. */
@Composable
internal fun contentSize(style: WidgetStyle): DpSize {
    val size = androidx.glance.LocalSize.current
    val theme = app.custo.feature.widgetsets.WidgetThemes.byId(style.themeId)
    val (padW, padH) = if (theme != null) {
        val f = theme.frame
        (f.outerSide + f.innerSide) * 2 to (f.outerTop + f.innerTop + f.outerBottom + f.innerBottom)
    } else {
        val p = (style.padding + style.borderWidth + 4).toFloat()
        val (ix, iy) = if (style.drawnShape) WidgetShapes.contentInset(style.shape, size.width.value, size.height.value) else 0f to 0f
        (p + ix) * 2 to (p + iy) * 2
    }
    return DpSize((size.width.value - padW).coerceAtLeast(1f).dp, (size.height.value - padH).coerceAtLeast(1f).dp)
}

internal fun fontLayout(font: WidgetFont, sans: Int, serif: Int, mono: Int, cursive: Int, pixel: Int, heavy: Int) = when (font) {
    WidgetFont.SANS -> sans; WidgetFont.SERIF -> serif; WidgetFont.MONO -> mono; WidgetFont.CURSIVE -> cursive
    WidgetFont.PIXEL -> pixel; WidgetFont.HEAVY -> heavy
    else -> FontLayouts.extra(sans, font)
}

/** Every Custo widget: exact sizing (for drawn backgrounds) and one composable body. */
abstract class CustoWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        context.container.setBackgrounds.ensureLoaded()
        provideContent { Content(context, id) }
    }

    /** The widget body. Kinds that load data first override [provideGlance] instead. */
    @Composable
    open fun Content(context: Context, id: GlanceId) = Unit
}

/**
 * A widget with small (2×2), wide (4×2), tall (2×4) and large (4×4) layouts, picked by
 * [sizeClass] from the widget's real size ([CustoWidget]'s exact sizing). Exact rather than a few
 * fixed buckets: with buckets a one-row widget (2×1, 4×1 are ~70–90 dp tall) got the 110 dp
 * layout and was cut off top and bottom.
 */
abstract class ResponsiveWidget : CustoWidget()

/** Receiver that also refreshes on clock, locale and alarm changes (declared per widget in the manifest). */
abstract class CustoReceiver : GlanceAppWidgetReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in REFRESH_ACTIONS) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try { Widgets.refreshWidget(context, glanceAppWidget) } finally { pending.finish() }
            }
        }
    }

    companion object {
        val REFRESH_ACTIONS = setOf(
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_DATE_CHANGED, android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
        )
    }
}

// ---------- Digital clock ----------

class ClockWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        if (style.clock.ordinal > ClockLook.MINIMAL.ordinal) {
            ClockFaceContent(context, id, style)
            return
        }
        val area = ClockFaces.forFont(contentSize(style), style.font)
        val rv = RemoteViews(context.packageName, clockLayout(style)).apply {
            val h12 = !android.text.format.DateFormat.is24HourFormat(context)
            when (style.clock) {
                ClockLook.BIG -> styleText(style, R.id.time to 56f, R.id.date to 15f, fit = TextFit.stack(area, style.textScale,
                    TextFit.Line(56f, 5), TextFit.Line(15f, 17, style.showDate)))
                ClockLook.STACKED -> styleText(style, R.id.time to 64f, R.id.time2 to 64f, R.id.date to 14f, fit = TextFit.stack(area, style.textScale,
                    TextFit.Line(64f, 2), TextFit.Line(64f, 2), TextFit.Line(14f, 6, style.showDate)))
                ClockLook.MINIMAL -> styleText(style, R.id.date to 14f, R.id.time to 34f, fit = TextFit.stack(area, style.textScale,
                    TextFit.Line(14f, 9, style.showDate), TextFit.Line(34f, if (h12) 8 else 5)))
                else -> Unit
            }
            setViewVisibility(R.id.date, if (style.showDate) View.VISIBLE else View.GONE)
            setInt(R.id.root, "setGravity", style.gravity())
        }
        Frame(style, tapStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
            AndroidRemoteViews(rv, GlanceModifier.fillMaxSize())
        }
    }

    private fun clockLayout(s: WidgetStyle): Int = when (s.clock) {
        // Drawn by ClockFaceContent; never reached.
        ClockLook.FLIP, ClockLook.LED, ClockLook.NEON, ClockLook.VERTICAL, ClockLook.WORDS -> R.layout.clock_big_sans
        ClockLook.BIG -> fontLayout(s.font, R.layout.clock_big_sans, R.layout.clock_big_serif, R.layout.clock_big_mono, R.layout.clock_big_cursive, R.layout.clock_big_pixel, R.layout.clock_big_heavy)
        ClockLook.STACKED -> fontLayout(s.font, R.layout.clock_stacked_sans, R.layout.clock_stacked_serif, R.layout.clock_stacked_mono, R.layout.clock_stacked_cursive, R.layout.clock_stacked_pixel, R.layout.clock_stacked_heavy)
        ClockLook.MINIMAL -> fontLayout(s.font, R.layout.clock_minimal_sans, R.layout.clock_minimal_serif, R.layout.clock_minimal_mono, R.layout.clock_minimal_cursive, R.layout.clock_minimal_pixel, R.layout.clock_minimal_heavy)
    }
}

class ClockReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = ClockWidget() }

// ---------- Big hour ----------

class HourWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val layout = fontLayout(style.font, R.layout.clock_hour_sans, R.layout.clock_hour_serif, R.layout.clock_hour_mono, R.layout.clock_hour_cursive, R.layout.clock_hour_pixel, R.layout.clock_hour_heavy)
        val rv = RemoteViews(context.packageName, layout).apply {
            val area = contentSize(style)
            // Big hour beside a small column (minutes over date): fit both the row width and the tall hour.
            val fit = minOf(
                TextFit.row(area, style.textScale, TextFit.Line(96f, 2), TextFit.Line(40f, 2)),
                TextFit.stack(area, style.textScale, TextFit.Line(96f, 2)),
            )
            styleText(style, R.id.time to 96f, R.id.time2 to 40f, R.id.date to 14f, fit = fit)
            setViewVisibility(R.id.date, if (style.showDate) View.VISIBLE else View.GONE)
            setInt(R.id.root, "setGravity", style.gravity())
        }
        Frame(style, tapStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
            AndroidRemoteViews(rv, GlanceModifier.fillMaxSize())
        }
    }
}

class HourReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = HourWidget() }

// ---------- Analog clock ----------

internal fun DialStyle.dialRes() = when (this) {
    DialStyle.MINIMAL -> R.drawable.dial_minimal
    DialStyle.CLASSIC -> R.drawable.dial_classic
    DialStyle.DOTS -> R.drawable.dial_dots
    DialStyle.BOLD -> R.drawable.dial_bold
    DialStyle.RING -> R.drawable.dial_ring
}

class AnalogWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val bold = style.dial == DialStyle.BOLD
        val rv = RemoteViews(context.packageName, R.layout.analog_clock).apply {
            val v = R.id.analog
            setIcon(v, "setDial", Icon.createWithResource(context, style.dial.dialRes()))
            setIcon(v, "setHourHand", Icon.createWithResource(context, if (bold) R.drawable.hand_hour_bold else R.drawable.hand_hour))
            setIcon(v, "setMinuteHand", Icon.createWithResource(context, if (bold) R.drawable.hand_minute_bold else R.drawable.hand_minute))
            if (style.seconds) setIcon(v, "setSecondHand", Icon.createWithResource(context, R.drawable.hand_second))
            listOf("setDialTintList", "setHourHandTintList", "setMinuteHandTintList").forEach { m ->
                if (style.themed) setColorStateList(v, m, R.color.widget_fg) else setColorStateList(v, m, ColorStateList.valueOf(style.fg))
            }
            if (style.seconds) {
                if (style.themed) setColorStateList(v, "setSecondHandTintList", R.color.widget_accent)
                else setColorStateList(v, "setSecondHandTintList", ColorStateList.valueOf(style.fg))
            }
        }
        Frame(style, tapStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
            AndroidRemoteViews(rv, GlanceModifier.fillMaxSize())
        }
    }
}

class AnalogReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = AnalogWidget() }

// ---------- World clock ----------

class WorldWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val layout = fontLayout(style.font, R.layout.world_row_sans, R.layout.world_row_serif, R.layout.world_row_mono, R.layout.world_row_cursive, R.layout.world_row_pixel, R.layout.world_row_heavy)
        val rows = style.zones.take(3).size.coerceAtLeast(1)
        val area = contentSize(style)
        val rowArea = DpSize(area.width, (area.height.value / rows - 6f).coerceAtLeast(1f).dp)
        val fit = minOf(
            TextFit.row(rowArea, style.textScale, TextFit.Line(15f, 10), TextFit.Line(26f, 5)),
            TextFit.stack(rowArea, style.textScale, TextFit.Line(26f, 5)),
        )
        Frame(style, tapStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
            Column(GlanceModifier.fillMaxWidth(), verticalAlignment = style.vAlign.glance()) {
                style.zones.take(3).forEach { zone ->
                    val rv = RemoteViews(context.packageName, layout).apply {
                        setTextViewText(R.id.label, cityName(zone))
                        setString(R.id.time, "setTimeZone", zone)
                        setString(R.id.date, "setTimeZone", zone)
                        styleText(style, R.id.label to 15f, R.id.date to 12f, R.id.time to 26f, fit = fit)
                    }
                    AndroidRemoteViews(rv, GlanceModifier.fillMaxWidth().padding(vertical = 3.dp))
                }
            }
        }
    }
}

class WorldReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = WorldWidget() }

// ---------- Stopwatch ----------

object Stopwatch {
    val BASE = longPreferencesKey("sw_base")
    val ELAPSED = longPreferencesKey("sw_elapsed")
    val RUNNING = booleanPreferencesKey("sw_running")

    fun format(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }
}

class StopwatchWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val running = currentState(Stopwatch.RUNNING) == true
        val now = SystemClock.elapsedRealtime()
        val base = (currentState(Stopwatch.BASE) ?: now).coerceAtMost(now)
        val elapsed = currentState(Stopwatch.ELAPSED) ?: 0L
        val toggle = tapRun<StopwatchAction>("cmd" to "toggle")
        val reset = tapRun<StopwatchAction>("cmd" to "reset")
        val pop = TapFrames.pop(tapFrame("sw"), 0.5f)
        Frame(style, toggle) {
            Column(horizontalAlignment = style.hAlign.glance()) {
                if (running) {
                    val layout = fontLayout(style.font, R.layout.chrono_sans, R.layout.chrono_serif, R.layout.chrono_mono, R.layout.chrono_cursive, R.layout.chrono_pixel, R.layout.chrono_heavy)
                    val rv = RemoteViews(context.packageName, layout).apply {
                        setChronometer(R.id.chrono, base, null, true)
                        // Leave room for the buttons under the time.
                        val area = contentSize(style)
                        styleText(style, R.id.chrono to 44f, fit = TextFit.stack(DpSize(area.width, area.height * 0.6f), style.textScale, TextFit.Line(44f, 5)) * pop)
                        setInt(R.id.root, "setGravity", style.gravity())
                    }
                    AndroidRemoteViews(rv, GlanceModifier.fillMaxWidth())
                } else {
                    WText(Stopwatch.format(elapsed), style = style.text(44f * pop, bold = true))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WText(if (running) "Tap to pause" else if (elapsed > 0) "Paused" else "Tap to start", style = style.text(13f, alpha = 0.75f))
                    if (!running && elapsed > 0) {
                        PressButton(style, reset, GlanceModifier.size(36.dp), ButtonLook(icon = R.drawable.ic_reset, iconDp = 20f, tint = fgArgb(style), desc = "Reset"))
                    }
                }
            }
        }
    }
}

class StopwatchAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val now = SystemClock.elapsedRealtime()
        TapFx.play(context, glanceId, StopwatchWidget(), "sw") { p ->
            when (parameters[CMD]) {
                "toggle" -> if (p[Stopwatch.RUNNING] == true) {
                    p[Stopwatch.ELAPSED] = (now - (p[Stopwatch.BASE] ?: now)).coerceAtLeast(0)
                    p[Stopwatch.RUNNING] = false
                } else {
                    p[Stopwatch.BASE] = now - (p[Stopwatch.ELAPSED] ?: 0L)
                    p[Stopwatch.RUNNING] = true
                }
                "reset" -> { p[Stopwatch.RUNNING] = false; p[Stopwatch.ELAPSED] = 0L }
            }
        }
    }

    companion object { val CMD = ActionParameters.Key<String>("cmd") }
}

class StopwatchReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = StopwatchWidget() }
