package app.custo.feature.widgets

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import app.custo.R
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import kotlin.math.min
import kotlin.math.roundToInt

private val track = androidx.glance.color.ColorProvider(Color(0x33808080), Color(0x33FFFFFF))

@Composable
private fun Bar(style: WidgetStyle, fraction: Float) {
    LinearProgressIndicator(
        progress = fraction.coerceIn(0f, 1f),
        modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
        color = accentProvider(style),
        backgroundColor = track,
    )
}

// ---------- Date ----------

class DateWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val today = LocalDate.now()
        val loc = context.resources.configuration.locales[0]
        val calendar = Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
        Frame(style, actionStartActivity(calendar)) {
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = style.hAlign.glance()) {
                WText(today.dayOfWeek.getDisplayName(TextStyle.FULL, loc), style = style.text(15f, alpha = 0.8f), modifier = GlanceModifier.fillMaxWidth())
                WText("${today.dayOfMonth}", style = style.text(54f, bold = true), modifier = GlanceModifier.fillMaxWidth())
                WText(today.format(DateTimeFormatter.ofPattern("MMMM yyyy", loc)), style = style.text(15f), modifier = GlanceModifier.fillMaxWidth())
                if (style.yearProgress) {
                    Spacer(GlanceModifier.height(8.dp))
                    val pct = (WidgetLogic.yearProgress(today) * 100).roundToInt()
                    WText("Day ${today.dayOfYear} · $pct% of the year", style = style.text(12f, alpha = 0.75f), modifier = GlanceModifier.fillMaxWidth())
                    Spacer(GlanceModifier.height(4.dp))
                    Bar(style, WidgetLogic.yearProgress(today))
                }
            }
        }
    }
}

class DateReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = DateWidget() }

// ---------- Calendar month ----------

class CalendarWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val today = LocalDate.now()
        val loc = context.resources.configuration.locales[0]
        val rows = WidgetLogic.monthGrid(today, style.mondayFirst)
        val cell = style.text(13f).copy(textAlign = TextAlign.Center)
        val calendar = Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
        Frame(style, actionStartActivity(calendar)) {
            Column(GlanceModifier.fillMaxWidth()) {
                WText(today.format(DateTimeFormatter.ofPattern("MMMM yyyy", loc)), style = style.text(16f, bold = true), modifier = GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    WidgetLogic.weekdayInitials(style.mondayFirst, loc).forEach {
                        WText(it, style = cell.copy(color = fgProvider(style, 0.6f)), modifier = GlanceModifier.defaultWeight())
                    }
                }
                rows.forEach { week ->
                    Row(GlanceModifier.fillMaxWidth().height((22 * style.textScale).dp), verticalAlignment = Alignment.CenterVertically) {
                        week.forEach { d ->
                            Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                                if (d == today.dayOfMonth) {
                                    Box(
                                        GlanceModifier.size((22 * style.textScale).dp).cornerRadius((11 * style.textScale).dp).background(fgProvider(style)),
                                        contentAlignment = Alignment.Center,
                                    ) { WText("$d", style = cell.copy(color = bgProvider(style, 1f))) }
                                } else {
                                    WText(if (d == 0) "" else "$d", style = cell)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class CalendarReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = CalendarWidget() }

// ---------- Countdown ----------

class CountdownWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val today = LocalDate.now()
        val target = WidgetLogic.countdownTarget(style.countdownDay, today)
        val days = WidgetLogic.daysUntil(target, today)
        Frame(style, actionStartActivity(editIntent(context, id))) {
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = style.hAlign.glance()) {
                WText(style.countdownTitle.ifBlank { "Countdown" }, maxLines = 1, style = style.text(15f, alpha = 0.8f), modifier = GlanceModifier.fillMaxWidth())
                if (days > 1) {
                    WText("$days", style = style.text(48f, bold = true), modifier = GlanceModifier.fillMaxWidth())
                    WText("days left", style = style.text(14f), modifier = GlanceModifier.fillMaxWidth())
                } else {
                    WText(WidgetLogic.countdownText(days), style = style.text(32f, bold = true), modifier = GlanceModifier.fillMaxWidth())
                }
                WText(
                    target.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", context.resources.configuration.locales[0])),
                    style = style.text(12f, alpha = 0.7f), modifier = GlanceModifier.fillMaxWidth(),
                )
            }
        }
    }
}

class CountdownReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = CountdownWidget() }

// ---------- Battery ----------

data class BatteryInfo(val level: Int, val charging: Boolean)

fun readBattery(ctx: Context): BatteryInfo {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
    val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return BatteryInfo(level, status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
}

class BatteryWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val b = readBattery(context)
        Frame(style, actionStartActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))) {
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = style.hAlign.glance()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WText("${b.level}%", style = style.text(30f, bold = true))
                    if (b.charging) {
                        Spacer(GlanceModifier.width(4.dp))
                        Image(ImageProvider(R.drawable.ic_bolt), "Charging", GlanceModifier.size((22 * style.textScale).dp), colorFilter = ColorFilter.tint(fgProvider(style)))
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                Bar(style, b.level / 100f)
            }
        }
    }
}

class BatteryReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = BatteryWidget() }

class BatteryRingWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val b = readBattery(context)
        val size = LocalSize.current
        val side = (min(size.width.value, size.height.value) - style.padding * 2).coerceAtLeast(48f)
        val ring = remember(b.level) { WidgetArt.ring(b.level / 100f) }
        Frame(style, actionStartActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))) {
            Box(GlanceModifier.size(side.dp), contentAlignment = Alignment.Center) {
                Image(ImageProvider(ring), "Battery ${b.level}%", GlanceModifier.size(side.dp), colorFilter = ColorFilter.tint(accentProvider(style)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (b.charging) Image(ImageProvider(R.drawable.ic_bolt), "Charging", GlanceModifier.size((18 * style.textScale).dp), colorFilter = ColorFilter.tint(fgProvider(style)))
                    if (style.showPercent) WText("${b.level}%", style = style.text(min(26f, side / 4.2f), bold = true).copy(textAlign = TextAlign.Center))
                }
            }
        }
    }
}

class BatteryRingReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = BatteryRingWidget() }

// ---------- Next alarm ----------

class AlarmWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val next = context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime
        Frame(style, actionStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_alarm), null, GlanceModifier.size((30 * style.textScale).dp), colorFilter = ColorFilter.tint(fgProvider(style)))
                Spacer(GlanceModifier.width(12.dp))
                Column {
                    if (next == null) {
                        WText("No alarm set", style = style.text(18f, bold = true))
                    } else {
                        val d = Date(next)
                        WText(android.text.format.DateFormat.getTimeFormat(context).format(d), style = style.text(28f, bold = true))
                        WText(alarmDay(next), style = style.text(13f, alpha = 0.8f))
                    }
                }
            }
        }
    }

    private fun alarmDay(millis: Long): String {
        val day = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (day) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
        }
    }
}

class AlarmReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = AlarmWidget() }

// ---------- Storage ----------

data class StorageInfo(val used: Long, val total: Long)

fun readStorage(): StorageInfo = runCatching {
    val s = StatFs(Environment.getDataDirectory().path)
    StorageInfo(s.totalBytes - s.availableBytes, s.totalBytes)
}.getOrDefault(StorageInfo(0, 1))

class StorageWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val s = readStorage()
        Frame(style, actionStartActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))) {
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = style.hAlign.glance()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.ic_storage), null, GlanceModifier.size((18 * style.textScale).dp), colorFilter = ColorFilter.tint(fgProvider(style)))
                    Spacer(GlanceModifier.width(6.dp))
                    WText("Storage", style = style.text(14f, alpha = 0.8f))
                }
                WText("${WidgetLogic.bytesText(s.used)} used", style = style.text(22f, bold = true), modifier = GlanceModifier.fillMaxWidth())
                WText("of ${WidgetLogic.bytesText(s.total)}", style = style.text(12f, alpha = 0.7f), modifier = GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.height(6.dp))
                Bar(style, s.used.toFloat() / s.total.coerceAtLeast(1))
            }
        }
    }
}

class StorageReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = StorageWidget() }
