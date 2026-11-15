package app.custo.feature.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.mutableActionParametersOf
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import app.custo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Timing and shape of the short effects a widget plays when tapped (pure, so it's unit tested).
 *
 * RemoteViews can't run code-driven animations, so an effect is a few redraws of the widget:
 * frame 1 right away, then one every [INTERVAL_MS], then back to rest (frame 0). Each curve below
 * gives a value per frame; frame 0 (or anything out of range) is the resting value.
 */
object TapFrames {
    const val COUNT = 4
    const val INTERVAL_MS = 80L

    /** Whole effect, from the first frame to rest. Kept under 400 ms so taps never feel laggy. */
    const val TOTAL_MS = COUNT * INTERVAL_MS

    /** A frame left in state for longer than this (process killed mid-effect) is drawn as rest. */
    const val STALE_MS = 1_500L

    /** The frame to draw now: [frame] while the effect is fresh, else 0. */
    fun live(frame: Int, startedAt: Long, now: Long): Int =
        if (frame in 1..COUNT && now - startedAt in 0..STALE_MS) frame else 0

    private fun pick(frame: Int, rest: Float, vararg values: Float): Float = if (frame in 1..COUNT) values[frame - 1] else rest

    /** Size multiplier: swell, squash, settle. */
    fun pop(frame: Int): Float = pick(frame, 1f, 1.3f, 0.88f, 1.1f, 0.98f)

    /** [pop] toned down by [strength] (0..1), for things that are already big. */
    fun pop(frame: Int, strength: Float): Float = 1f + (pop(frame) - 1f) * strength

    /** How far a "+1" has floated up, 0..1. */
    fun rise(frame: Int): Float = pick(frame, 0f, 0.15f, 0.45f, 0.75f, 1f)

    /** Opacity of a floating "+1": solid, then fading out. Invisible at rest. */
    fun fade(frame: Int): Float = pick(frame, 0f, 1f, 0.85f, 0.55f, 0.2f)

    /** Opacity of a light flash over the whole widget. */
    fun flash(frame: Int): Float = pick(frame, 0f, 0.3f, 0.18f, 0.09f, 0.03f)

    /** Opacity of fresh content fading in (a new quote). */
    fun reveal(frame: Int): Float = pick(frame, 1f, 0.2f, 0.5f, 0.8f, 0.95f)

    /** Sideways knock, -1..1 (times a few dp): hit, bounce back, settle. */
    fun wobble(frame: Int): Float = pick(frame, 0f, -1f, 0.7f, -0.4f, 0.15f)

    /** Progress from the old position to the new one, 0..1 (a switch thumb). */
    fun slide(frame: Int): Float = pick(frame, 1f, 0.3f, 0.7f, 0.95f, 1f)

    /** Width multiplier while sliding: stretch on the way, a little squash on arrival. */
    fun stretch(frame: Int): Float = pick(frame, 1f, 1.25f, 1.35f, 0.9f, 1f)

    enum class Burst { NONE, SWELL, BURST, SHARDS }

    /** A bubble being popped: swells, bursts, leaves shards, then shows as popped. */
    fun burst(frame: Int): Burst = when (frame) {
        1 -> Burst.SWELL
        2 -> Burst.BURST
        3 -> Burst.SHARDS
        else -> Burst.NONE
    }
}

/** Plays [TapFrames] effects by writing the current frame into the widget's Glance state. */
object TapFx {
    val TARGET = stringPreferencesKey("tap_target")
    val FRAME = intPreferencesKey("tap_frame")
    val AT = longPreferencesKey("tap_at")

    /** Target for effects on the whole widget (a flash). */
    const val WHOLE = "whole"

    private val seq = AtomicLong()
    private val running = ConcurrentHashMap<String, Long>()

    /**
     * Applies [change] (the tap's real work on widget state, if any) and shows frame 1 in the same
     * redraw, then steps through the remaining frames. A newer tap on the same widget takes over,
     * so fast tapping never queues up effects. With the style's tap animation off, only [change] runs.
     */
    suspend fun play(
        context: Context, id: GlanceId, widget: GlanceAppWidget, target: String,
        change: (MutablePreferences) -> Unit = {},
    ) {
        if (!Widgets.style(context, id).tapAnim) {
            updateAppWidgetState(context, id) { change(it) }
            widget.update(context, id)
            return
        }
        val key = id.toString()
        val me = seq.incrementAndGet()
        running[key] = me
        updateAppWidgetState(context, id) {
            change(it)
            it[TARGET] = target
            it[FRAME] = 1
            it[AT] = System.currentTimeMillis()
        }
        widget.update(context, id)
        for (f in 2..TapFrames.COUNT + 1) {
            delay(TapFrames.INTERVAL_MS)
            if (running[key] != me) return
            val frame = if (f > TapFrames.COUNT) 0 else f
            updateAppWidgetState(context, id) { it[FRAME] = frame }
            widget.update(context, id)
        }
        running.remove(key, me)
    }
}

/** The frame of the effect playing on [target] in this widget, or 0 when none is. */
@Composable
internal fun tapFrame(target: String): Int {
    if (currentState(TapFx.TARGET) != target) return 0
    return TapFrames.live(currentState(TapFx.FRAME) ?: 0, currentState(TapFx.AT) ?: 0L, System.currentTimeMillis())
}

/**
 * What a tap does. It becomes a Glance [Action] for Glance views, or a [PendingIntent] for the
 * press-animated views Custo inflates itself (Glance's clickable can't be put on those: its
 * ripple overlay would clash with the view ids of the Glance content inside them).
 */
sealed interface Tap {
    fun glance(): Action
    fun pendingIntent(context: Context, appWidgetId: Int): PendingIntent

    data class Launch(val intent: Intent) : Tap {
        override fun glance(): Action = actionStartActivity(intent)
        override fun pendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val i = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Request codes keep PendingIntents for different widgets and targets apart (extras alone don't).
            val code = "$appWidgetId|${intent.toUri(0)}".hashCode()
            return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    /** Runs [callback] with [params] (String, Int, Long or Boolean values). */
    data class Run(val callback: Class<out ActionCallback>, val params: List<Pair<String, Any>>) : Tap {
        override fun glance(): Action =
            actionRunCallback(callback, actionParametersOf(*params.map { (k, v) -> ActionParameters.Key<Any>(k) to v }.toTypedArray()))

        override fun pendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val tag = callback.name + params.joinToString("") { (k, v) -> "|$k=$v" }
            val i = Intent(context, TapReceiver::class.java)
                .setData("custo-tap://$appWidgetId/${Uri.encode(tag)}".toUri())
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(TapReceiver.CALLBACK, callback.name)
            params.forEach { (k, v) ->
                when (v) {
                    is String -> i.putExtra(TapReceiver.PARAM + k, v)
                    is Int -> i.putExtra(TapReceiver.PARAM + k, v)
                    is Long -> i.putExtra(TapReceiver.PARAM + k, v)
                    is Boolean -> i.putExtra(TapReceiver.PARAM + k, v)
                    else -> error("Unsupported tap parameter $k")
                }
            }
            return PendingIntent.getBroadcast(context, tag.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}

internal fun tapStart(intent: Intent): Tap = Tap.Launch(intent)
internal inline fun <reified T : ActionCallback> tapRun(vararg params: Pair<String, Any>): Tap = Tap.Run(T::class.java, params.toList())

/** Runs a [Tap.Run] callback for a tap on a Custo-inflated widget view. */
class TapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val name = intent.getStringExtra(CALLBACK) ?: return
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || !name.startsWith("app.custo.")) return
        val params = mutableActionParametersOf()
        val extras = intent.extras
        extras?.keySet()?.filter { it.startsWith(PARAM) }?.forEach { k ->
            @Suppress("DEPRECATION")
            extras.get(k)?.let { params[ActionParameters.Key<Any>(k.removePrefix(PARAM))] = it }
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val callback = Class.forName(name).asSubclass(ActionCallback::class.java).getDeclaredConstructor().newInstance()
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
                callback.onAction(context, glanceId, params)
            } catch (e: Exception) {
                android.util.Log.w("Custo", "Widget tap failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val CALLBACK = "app.custo.tap.CALLBACK"
        const val PARAM = "app.custo.tap.p."
    }
}

@Composable
private fun appWidgetId(): Int = GlanceAppWidgetManager(LocalContext.current).getAppWidgetId(LocalGlanceId.current)

/**
 * A whole tappable widget: it shrinks a little while pressed and springs back (a launcher-side
 * state-list animator, so it costs nothing until touched and plays even for taps that open
 * another app). Without a [tap] it's a plain box. Must be the last view at its level and below
 * it (Glance views added after it could pick up ids from inside it).
 */
@Composable
internal fun TapSurface(
    style: WidgetStyle, tap: Tap?, modifier: GlanceModifier,
    contentAlignment: Alignment = Alignment.TopStart, content: @Composable () -> Unit,
) {
    if (tap == null) {
        Box(modifier, contentAlignment = contentAlignment) { content() }
        return
    }
    val ctx = LocalContext.current
    val rv = RemoteViews(ctx.packageName, if (style.tapAnim) R.layout.tap_press else R.layout.tap_still).apply {
        setOnClickPendingIntent(R.id.tap_root, tap.pendingIntent(ctx, appWidgetId()))
    }
    AndroidRemoteViews(rv, R.id.tap_container, modifier) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = contentAlignment) { content() }
    }
}

/** What a [PressButton] shows. Sizes in dp, text in sp; colors are ARGB. */
internal data class ButtonLook(
    val icon: Int = 0,
    val bitmap: Bitmap? = null,
    val iconDp: Float = 24f,
    val tint: Int? = null,
    val label: String? = null,
    val labelSp: Float = 14f,
    val labelColor: Int = 0xFFFFFFFF.toInt(),
    val bold: Boolean = false,
    /** Gap between icon and label. */
    val gapDp: Float = 0f,
    /** A shape drawable behind the content ([R.drawable.tap_shape_round] or [R.drawable.tap_shape_circle]), or 0. */
    val shape: Int = 0,
    val shapeColor: Int = 0,
    /** Square shape size; null = wrap the content (plus padding). */
    val shapeDp: Float? = null,
    /** Shape fills the whole button. */
    val fill: Boolean = false,
    val padH: Float = 0f,
    val padV: Float = 0f,
    val desc: String? = null,
)

/** A small control that squishes while pressed (unless the widget's tap animation is off). */
@Composable
internal fun PressButton(style: WidgetStyle, tap: Tap, modifier: GlanceModifier, look: ButtonLook) {
    val ctx = LocalContext.current
    val dp = ctx.resources.displayMetrics.density
    val dip = TypedValue.COMPLEX_UNIT_DIP
    val rv = RemoteViews(ctx.packageName, if (style.tapAnim) R.layout.tap_button else R.layout.tap_button_still).apply {
        setOnClickPendingIntent(R.id.tap_btn, tap.pendingIntent(ctx, appWidgetId()))
        (look.desc ?: look.label)?.let { setContentDescription(R.id.tap_btn, it) }
        if (look.bitmap != null || look.icon != 0) {
            setViewVisibility(R.id.tap_btn_icon, View.VISIBLE)
            if (look.bitmap != null) setImageViewBitmap(R.id.tap_btn_icon, look.bitmap) else setImageViewResource(R.id.tap_btn_icon, look.icon)
            setViewLayoutWidth(R.id.tap_btn_icon, look.iconDp, dip)
            setViewLayoutHeight(R.id.tap_btn_icon, look.iconDp, dip)
            look.tint?.let { tintImage(R.id.tap_btn_icon, it) }
        }
        if (look.label != null) {
            setViewVisibility(R.id.tap_btn_label, View.VISIBLE)
            val text: CharSequence = if (look.bold) SpannableString(look.label).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else look.label
            setTextViewText(R.id.tap_btn_label, text)
            setTextViewTextSize(R.id.tap_btn_label, TypedValue.COMPLEX_UNIT_SP, look.labelSp)
            setTextColor(R.id.tap_btn_label, look.labelColor)
            if (look.gapDp > 0f) setViewLayoutMargin(R.id.tap_btn_label, RemoteViews.MARGIN_TOP, look.gapDp, dip)
        }
        if (look.shape != 0) {
            setViewVisibility(R.id.tap_btn_shape, View.VISIBLE)
            setImageViewResource(R.id.tap_btn_shape, look.shape)
            tintImage(R.id.tap_btn_shape, look.shapeColor)
        }
        when {
            look.fill -> {
                setViewLayoutWidth(R.id.tap_btn_body, ViewGroup.LayoutParams.MATCH_PARENT.toFloat(), TypedValue.COMPLEX_UNIT_PX)
                setViewLayoutHeight(R.id.tap_btn_body, ViewGroup.LayoutParams.MATCH_PARENT.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            }
            look.shapeDp != null -> {
                setViewLayoutWidth(R.id.tap_btn_body, look.shapeDp, dip)
                setViewLayoutHeight(R.id.tap_btn_body, look.shapeDp, dip)
            }
        }
        if (look.padH > 0f || look.padV > 0f) {
            val h = (look.padH * dp).toInt()
            val v = (look.padV * dp).toInt()
            setViewPadding(R.id.tap_btn_content, h, v, h, v)
        }
    }
    AndroidRemoteViews(rv, modifier)
}

/** The widget's text color as ARGB, for views Custo inflates itself. */
@Composable
internal fun fgArgb(style: WidgetStyle, alpha: Float = 1f): Int = fgProvider(style, alpha).getColor(LocalContext.current).toArgb()

/** The widget's background color (opaque by default) as ARGB. */
@Composable
internal fun bgArgb(style: WidgetStyle, alpha: Float = 1f): Int = bgProvider(style, alpha).getColor(LocalContext.current).toArgb()

/** Tints a white image to [argb], keeping the color's transparency. */
private fun RemoteViews.tintImage(id: Int, argb: Int) {
    setInt(id, "setColorFilter", argb or 0xFF000000.toInt())
    setInt(id, "setImageAlpha", (argb ushr 24) and 0xFF)
}
