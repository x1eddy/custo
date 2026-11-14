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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
import app.custo.R
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

/** Text style in the widget's font, color and scale. */
@Composable
internal fun WidgetStyle.text(size: Float, bold: Boolean = false, alpha: Float = 1f): TextStyle = TextStyle(
    color = fgProvider(this, alpha),
    fontSize = sp(size),
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontFamily = font.glance(),
    textAlign = hAlign.text(),
)

@Composable
internal fun currentStyle(): WidgetStyle = WidgetStyle.decode(currentState(Widgets.STYLE_KEY))

/**
 * Shared widget shell: background (flat color, or a drawn gradient/border bitmap), corners,
 * padding, alignment and the tap action.
 */
@Composable
internal fun Frame(style: WidgetStyle, onClick: Action?, padded: Boolean = true, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val size = LocalSize.current
    var m = GlanceModifier.fillMaxSize().cornerRadius(style.corner.dp)
    if (!(style.gradient && !style.themed)) m = m.background(bgProvider(style))
    if (onClick != null) m = m.clickable(onClick)
    Box(m) {
        if (style.needsBitmapBg) {
            val bmp = remember(style, size) { WidgetArt.background(ctx, style, size.width.value, size.height.value) }
            Image(ImageProvider(bmp), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        Box(
            GlanceModifier.fillMaxSize().padding((if (padded) style.padding else 0).dp),
            contentAlignment = Alignment(style.hAlign.glance(), style.vAlign.glance()),
        ) { content() }
    }
}

internal fun editIntent(ctx: Context, glanceId: GlanceId): Intent {
    val id = GlanceAppWidgetManager(ctx).getAppWidgetId(glanceId)
    return Intent(ctx, MainActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}

/** Colors and scales TextViews/TextClocks inside a RemoteViews layout. */
internal fun RemoteViews.styleText(style: WidgetStyle, vararg views: Pair<Int, Float>) {
    views.forEach { (id, base) ->
        if (style.themed) setColor(id, "setTextColor", R.color.widget_fg) else setTextColor(id, style.fg)
        setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, base * style.textScale)
    }
}

internal fun fontLayout(font: WidgetFont, sans: Int, serif: Int, mono: Int, cursive: Int) = when (font) {
    WidgetFont.SANS -> sans; WidgetFont.SERIF -> serif; WidgetFont.MONO -> mono; WidgetFont.CURSIVE -> cursive
}

/** Every Custo widget: exact sizing (for drawn backgrounds) and one composable body. */
abstract class CustoWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content(context, id) }
    }

    @Composable
    abstract fun Content(context: Context, id: GlanceId)
}

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
        val rv = RemoteViews(context.packageName, clockLayout(style)).apply {
            when (style.clock) {
                ClockLook.BIG -> styleText(style, R.id.time to 56f, R.id.date to 15f)
                ClockLook.STACKED -> styleText(style, R.id.time to 64f, R.id.time2 to 64f, R.id.date to 14f)
                ClockLook.MINIMAL -> styleText(style, R.id.date to 14f, R.id.time to 34f)
            }
            setViewVisibility(R.id.date, if (style.showDate) View.VISIBLE else View.GONE)
            setInt(R.id.root, "setGravity", style.gravity())
        }
        Frame(style, actionStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
            AndroidRemoteViews(rv, GlanceModifier.fillMaxSize())
        }
    }

    private fun clockLayout(s: WidgetStyle): Int = when (s.clock) {
        ClockLook.BIG -> fontLayout(s.font, R.layout.clock_big_sans, R.layout.clock_big_serif, R.layout.clock_big_mono, R.layout.clock_big_cursive)
        ClockLook.STACKED -> fontLayout(s.font, R.layout.clock_stacked_sans, R.layout.clock_stacked_serif, R.layout.clock_stacked_mono, R.layout.clock_stacked_cursive)
        ClockLook.MINIMAL -> fontLayout(s.font, R.layout.clock_minimal_sans, R.layout.clock_minimal_serif, R.layout.clock_minimal_mono, R.layout.clock_minimal_cursive)
    }
}

class ClockReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = ClockWidget() }

// ---------- Big hour ----------

class HourWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val layout = fontLayout(style.font, R.layout.clock_hour_sans, R.layout.clock_hour_serif, R.layout.clock_hour_mono, R.layout.clock_hour_cursive)
        val rv = RemoteViews(context.packageName, layout).apply {
            styleText(style, R.id.time to 96f, R.id.time2 to 40f, R.id.date to 14f)
            setViewVisibility(R.id.date, if (style.showDate) View.VISIBLE else View.GONE)
            setInt(R.id.root, "setGravity", style.gravity())
        }
        Frame(style, actionStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
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
        Frame(style, actionStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
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
        val layout = fontLayout(style.font, R.layout.world_row_sans, R.layout.world_row_serif, R.layout.world_row_mono, R.layout.world_row_cursive)
        Frame(style, actionStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
            Column(GlanceModifier.fillMaxWidth(), verticalAlignment = style.vAlign.glance()) {
                style.zones.take(3).forEach { zone ->
                    val rv = RemoteViews(context.packageName, layout).apply {
                        setTextViewText(R.id.label, cityName(zone))
                        setString(R.id.time, "setTimeZone", zone)
                        setString(R.id.date, "setTimeZone", zone)
                        styleText(style, R.id.label to 15f, R.id.date to 12f, R.id.time to 26f)
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
        val toggle = actionRunCallback<StopwatchAction>(actionParametersOf(StopwatchAction.CMD to "toggle"))
        val reset = actionRunCallback<StopwatchAction>(actionParametersOf(StopwatchAction.CMD to "reset"))
        Frame(style, toggle) {
            Column(horizontalAlignment = style.hAlign.glance()) {
                if (running) {
                    val layout = fontLayout(style.font, R.layout.chrono_sans, R.layout.chrono_serif, R.layout.chrono_mono, R.layout.chrono_cursive)
                    val rv = RemoteViews(context.packageName, layout).apply {
                        setChronometer(R.id.chrono, base, null, true)
                        styleText(style, R.id.chrono to 44f)
                        setInt(R.id.root, "setGravity", style.gravity())
                    }
                    AndroidRemoteViews(rv, GlanceModifier.fillMaxWidth())
                } else {
                    Text(Stopwatch.format(elapsed), style = style.text(44f, bold = true))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (running) "Tap to pause" else if (elapsed > 0) "Paused" else "Tap to start", style = style.text(13f, alpha = 0.75f))
                    if (!running && elapsed > 0) {
                        Image(
                            ImageProvider(R.drawable.ic_reset), "Reset",
                            GlanceModifier.size(36.dp).padding(8.dp).clickable(reset),
                            colorFilter = ColorFilter.tint(fgProvider(style)),
                        )
                    }
                }
            }
        }
    }
}

class StopwatchAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val now = SystemClock.elapsedRealtime()
        updateAppWidgetState(context, glanceId) { p ->
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
        StopwatchWidget().update(context, glanceId)
    }

    companion object { val CMD = ActionParameters.Key<String>("cmd") }
}

class StopwatchReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = StopwatchWidget() }
