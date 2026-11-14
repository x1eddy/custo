package app.custo.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.custo.MainActivity
import app.custo.R

// ---------- Shared styling ----------

private fun sys(ctx: Context, res: Int) = Color(ctx.getColor(res))

/** Background: themed (Material You, follows day/night) or a custom color, with transparency. */
@Composable
private fun bgProvider(style: WidgetStyle): ColorProvider {
    val ctx = LocalContext.current
    return if (style.themed) {
        androidx.glance.color.ColorProvider(
            day = sys(ctx, android.R.color.system_accent1_100).copy(alpha = style.alpha),
            night = sys(ctx, android.R.color.system_accent1_800).copy(alpha = style.alpha),
        )
    } else androidx.glance.color.ColorProvider(Color(style.bg).copy(alpha = style.alpha), Color(style.bg).copy(alpha = style.alpha))
}

@Composable
private fun fgProvider(style: WidgetStyle): ColorProvider {
    val ctx = LocalContext.current
    return if (style.themed) {
        androidx.glance.color.ColorProvider(
            day = sys(ctx, android.R.color.system_accent1_900),
            night = sys(ctx, android.R.color.system_accent1_100),
        )
    } else androidx.glance.color.ColorProvider(Color(style.fg), Color(style.fg))
}

private fun WidgetFont.glance(): FontFamily = when (this) {
    WidgetFont.SANS -> FontFamily.SansSerif
    WidgetFont.SERIF -> FontFamily.Serif
    WidgetFont.MONO -> FontFamily.Monospace
    WidgetFont.CURSIVE -> FontFamily.Cursive
}

@Composable
private fun currentStyle(): WidgetStyle = WidgetStyle.decode(currentState(Widgets.STYLE_KEY))

@Composable
private fun Frame(style: WidgetStyle, onClick: Action?, content: @Composable () -> Unit) {
    var m = GlanceModifier.fillMaxSize().background(bgProvider(style)).cornerRadius(style.corner.dp)
    if (onClick != null) m = m.clickable(onClick)
    Box(m, contentAlignment = Alignment.Center) { content() }
}

private fun editIntent(ctx: Context, glanceId: GlanceId): Intent {
    val id = androidx.glance.appwidget.GlanceAppWidgetManager(ctx).getAppWidgetId(glanceId)
    return Intent(ctx, MainActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}

// ---------- Clock ----------

class ClockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val style = currentStyle()
            val ctx = LocalContext.current
            val layout = clockLayout(style)
            val rv = RemoteViews(ctx.packageName, layout).apply {
                val ids = listOf(R.id.time, R.id.time2, R.id.date)
                if (style.themed) ids.forEach { setColor(it, "setTextColor", R.color.widget_fg) }
                else ids.forEach { setTextColor(it, style.fg) }
                setViewVisibility(R.id.date, if (style.showDate) View.VISIBLE else View.GONE)
            }
            Frame(style, actionStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
                AndroidRemoteViews(rv, GlanceModifier.fillMaxSize())
            }
        }
    }

    private fun clockLayout(s: WidgetStyle): Int = when (s.clock) {
        ClockLook.BIG -> when (s.font) {
            WidgetFont.SANS -> R.layout.clock_big_sans; WidgetFont.SERIF -> R.layout.clock_big_serif
            WidgetFont.MONO -> R.layout.clock_big_mono; WidgetFont.CURSIVE -> R.layout.clock_big_cursive
        }
        ClockLook.STACKED -> when (s.font) {
            WidgetFont.SANS -> R.layout.clock_stacked_sans; WidgetFont.SERIF -> R.layout.clock_stacked_serif
            WidgetFont.MONO -> R.layout.clock_stacked_mono; WidgetFont.CURSIVE -> R.layout.clock_stacked_cursive
        }
        ClockLook.MINIMAL -> when (s.font) {
            WidgetFont.SANS -> R.layout.clock_minimal_sans; WidgetFont.SERIF -> R.layout.clock_minimal_serif
            WidgetFont.MONO -> R.layout.clock_minimal_mono; WidgetFont.CURSIVE -> R.layout.clock_minimal_cursive
        }
    }
}

class ClockReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ClockWidget() }

// ---------- Battery ----------

data class BatteryInfo(val level: Int, val charging: Boolean)

fun readBattery(ctx: Context): BatteryInfo {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
    val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return BatteryInfo(level, status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
}

class BatteryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val b = readBattery(context)
        provideContent {
            val style = currentStyle()
            val fg = fgProvider(style)
            Frame(style, actionStartActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))) {
                Column(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${b.level}%", style = TextStyle(color = fg, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = style.font.glance()))
                        if (b.charging) {
                            Spacer(GlanceModifier.width(4.dp))
                            Image(ImageProvider(R.drawable.ic_bolt), "Charging", GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(fg))
                        }
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = b.level / 100f,
                        modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
                        color = fg,
                        backgroundColor = androidx.glance.color.ColorProvider(Color(0x33808080), Color(0x33FFFFFF)),
                    )
                }
            }
        }
    }
}

class BatteryReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BatteryWidget() }

// ---------- Media ----------

class MediaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val np = MediaRepo.nowPlaying(context)
        val open = np?.pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
        provideContent {
            val style = currentStyle()
            val fg = fgProvider(style)
            val ts = TextStyle(color = fg, fontFamily = style.font.glance())
            Frame(style, open?.let { actionStartActivity(it) } ?: actionStartActivity(editIntent(LocalContext.current, id))) {
                Row(GlanceModifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (np?.art != null) {
                        Image(ImageProvider(np.art), "Album art", GlanceModifier.size(64.dp).cornerRadius(16.dp))
                    } else {
                        Image(ImageProvider(R.drawable.ic_music), null, GlanceModifier.size(40.dp), colorFilter = ColorFilter.tint(fg))
                    }
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(np?.title?.ifEmpty { null } ?: "Nothing playing", maxLines = 1, style = ts.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        Text(
                            if (np == null) "Allow notification access in Custo → Widgets" else np.artist,
                            maxLines = 1, style = ts.copy(fontSize = 13.sp),
                        )
                    }
                    if (np != null) {
                        MediaButton(R.drawable.ic_prev, "prev", fg)
                        MediaButton(if (np.playing) R.drawable.ic_pause else R.drawable.ic_play, "toggle", fg)
                        MediaButton(R.drawable.ic_next, "next", fg)
                    }
                }
            }
        }
    }

    @Composable
    private fun MediaButton(icon: Int, cmd: String, tint: ColorProvider) {
        Image(
            ImageProvider(icon), cmd,
            GlanceModifier.size(40.dp).padding(8.dp).clickable(actionRunCallback<MediaAction>(actionParametersOf(MediaAction.CMD to cmd))),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

class MediaAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        MediaRepo.command(context, parameters[CMD] ?: return)
        kotlinx.coroutines.delay(350)
        MediaWidget().update(context, glanceId)
    }

    companion object { val CMD = ActionParameters.Key<String>("cmd") }
}

class MediaReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MediaWidget() }

// ---------- Note ----------

class NoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val style = currentStyle()
            Frame(style, actionStartActivity(editIntent(LocalContext.current, id))) {
                Box(GlanceModifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.TopStart) {
                    Text(style.note, style = TextStyle(color = fgProvider(style), fontSize = 17.sp, fontFamily = style.font.glance()))
                }
            }
        }
    }
}

class NoteReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NoteWidget() }

