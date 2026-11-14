package app.custo.feature.widgets

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import app.custo.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

internal fun timeFormat(ctx: Context): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm a", ctx.resources.configuration.locales[0])

/** A system-drawn countdown to [untilMillis] (wall clock). It ticks without waking Custo. */
@Composable
internal fun Countdown(context: Context, style: WidgetStyle, untilMillis: Long, size: Float, modifier: GlanceModifier = GlanceModifier.fillMaxWidth()) {
    val layout = fontLayout(style.font, R.layout.chrono_sans, R.layout.chrono_serif, R.layout.chrono_mono, R.layout.chrono_cursive)
    val rv = RemoteViews(context.packageName, layout).apply {
        val left = (untilMillis - System.currentTimeMillis()).coerceAtLeast(0)
        setChronometer(R.id.chrono, SystemClock.elapsedRealtime() + left, null, true)
        setChronometerCountDown(R.id.chrono, true)
        styleText(style, R.id.chrono to size)
        setInt(R.id.root, "setGravity", style.gravity())
    }
    AndroidRemoteViews(rv, modifier)
}

private fun LocalDateTime.millis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Asks for a refresh at [at], when the widget's text changes (inexact, non-waking). */
@Composable
internal fun RefreshAt(context: Context, id: GlanceId, kind: WidgetKind, at: Long) {
    LaunchedEffect(at) { WidgetAlarms.refreshAt(context, GlanceAppWidgetManager(context).getAppWidgetId(id), kind, at) }
}

// ---------- Work countdown ----------

class WorkWidget : ResponsiveWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val now = LocalDateTime.now()
        val st = HabitLogic.work(now, style.workStart, style.workEnd, style.workDays.toSet())
        RefreshAt(context, id, WidgetKind.WORK, st.until.millis())
        val loc = context.resources.configuration.locales[0]
        val big = if (sizeClass() == SizeClass.SMALL) 30f else 40f
        Frame(style, actionStartActivity(editIntent(context, id))) {
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = style.hAlign.glance()) {
                when (st.phase) {
                    HabitLogic.WorkPhase.WORKING -> {
                        WText("Work ends in", style = style.text(14f, alpha = 0.8f), modifier = GlanceModifier.fillMaxWidth())
                        Countdown(context, style, st.until.millis(), big)
                        Spacer(GlanceModifier.height(6.dp))
                        GBar(style, st.progress)
                        if (sizeClass() != SizeClass.SMALL) {
                            Spacer(GlanceModifier.height(4.dp))
                            WText("${(st.progress * 100).roundToInt()}% of today done", style = style.text(12f, alpha = 0.7f), modifier = GlanceModifier.fillMaxWidth())
                        }
                    }
                    HabitLogic.WorkPhase.BEFORE -> {
                        WText("Work starts in", style = style.text(14f, alpha = 0.8f), modifier = GlanceModifier.fillMaxWidth())
                        Countdown(context, style, st.until.millis(), big)
                    }
                    else -> {
                        WText(if (st.phase == HabitLogic.WorkPhase.AFTER) "Done for today 🎉" else "Day off 🌿", style = style.text(22f, bold = true), modifier = GlanceModifier.fillMaxWidth())
                        val next = st.until.dayOfWeek.getDisplayName(TextStyle.SHORT, loc) + " " + st.until.format(timeFormat(context))
                        WText("Back at it $next", style = style.text(13f, alpha = 0.75f), modifier = GlanceModifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

class WorkReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = WorkWidget() }

// ---------- Week countdown ----------

class WeekWidget : ResponsiveWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val today = LocalDate.now()
        val (top, bottom) = HabitLogic.weekText(today, style.weekDay)
        val n = HabitLogic.daysUntilWeekday(today, style.weekDay)
        val loc = context.resources.configuration.locales[0]
        Frame(style, actionStartActivity(editIntent(context, id))) {
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = style.hAlign.glance()) {
                val showBig = n > 1 && !(style.weekDay == 6 && today.dayOfWeek.value >= 6)
                if (showBig) {
                    WText("$n", style = style.text(54f, bold = true), modifier = GlanceModifier.fillMaxWidth())
                    WText("days till ${bottom.replaceFirstChar { it.lowercase() }}", style = style.text(15f), modifier = GlanceModifier.fillMaxWidth())
                } else {
                    WText(top, style = style.text(15f, alpha = 0.8f), modifier = GlanceModifier.fillMaxWidth())
                    WText(bottom, style = style.text(30f, bold = true), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
                }
                if (sizeClass() != SizeClass.SMALL) {
                    Spacer(GlanceModifier.height(10.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        DayOfWeek.entries.forEach { d ->
                            val isToday = d == today.dayOfWeek
                            val isTarget = d.value == style.weekDay || (style.weekDay == 6 && d.value == 7)
                            Box(GlanceModifier.defaultWeight().padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                                Box(
                                    GlanceModifier.size(26.dp).cornerRadius(13.dp).background(fgProvider(style, if (isToday) 1f else if (isTarget) 0.35f else 0.1f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    WText(
                                        d.getDisplayName(TextStyle.NARROW, loc),
                                        style = style.text(11f, bold = isToday).copy(textAlign = TextAlign.Center, color = if (isToday) bgProvider(style, 1f) else fgProvider(style)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class WeekReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = WeekWidget() }

// ---------- Moon & sun ----------

class MoonWidget : ResponsiveWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val now = Instant.now()
        val moon = Astro.moon(now)
        val place = style.place()
        val today = LocalDate.now(place.zoneId())
        val sun = Astro.sunTimes(today, place)
        val bmp = remember((moon.fraction * 200).toInt()) { WidgetArt.moon(moon.fraction) }
        val tf = timeFormat(context)
        val cls = sizeClass()
        Frame(style, actionStartActivity(editIntent(context, id))) {
            val moonSide = when (cls) { SizeClass.SMALL -> 64f; SizeClass.WIDE -> 80f; else -> 110f }
            val info = @Composable {
                Column(horizontalAlignment = if (cls == SizeClass.WIDE) Alignment.Start else Alignment.CenterHorizontally) {
                    val align = if (cls == SizeClass.WIDE) TextAlign.Start else TextAlign.Center
                    WText(moon.phase.label, style = style.text(16f, bold = true).copy(textAlign = align), maxLines = 1)
                    WText("${(moon.illumination * 100).roundToInt()}% lit", style = style.text(12f, alpha = 0.75f).copy(textAlign = align), maxLines = 1)
                    if (style.showSun && cls != SizeClass.SMALL) {
                        Spacer(GlanceModifier.height(6.dp))
                        WText("↑ ${sun.sunrise?.format(tf) ?: "—"}   ↓ ${sun.sunset?.format(tf) ?: "—"}", style = style.text(14f).copy(textAlign = align), maxLines = 1)
                        WText(place.name, style = style.text(11f, alpha = 0.7f).copy(textAlign = align), maxLines = 1)
                    }
                    if (cls == SizeClass.LARGE || cls == SizeClass.TALL) {
                        Spacer(GlanceModifier.height(6.dp))
                        val full = Astro.daysUntilPhase(now, 0.5)
                        WText(if (full == 0) "Full moon tonight" else "Full moon in $full days", style = style.text(12f, alpha = 0.75f).copy(textAlign = align), maxLines = 1)
                    }
                }
            }
            if (cls == SizeClass.WIDE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(bmp), moon.phase.label, GlanceModifier.size(moonSide.dp))
                    Spacer(GlanceModifier.width(14.dp))
                    info()
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(ImageProvider(bmp), moon.phase.label, GlanceModifier.size(moonSide.dp))
                    Spacer(GlanceModifier.height(6.dp))
                    info()
                }
            }
        }
    }
}

class MoonReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = MoonWidget() }

// ---------- Prayer times ----------

class PrayerWidget : ResponsiveWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val place = style.place()
        val now = ZonedDateTime.now(place.zoneId())
        val times = Astro.prayerTimes(now.toLocalDate(), place, style.prayerMethod, style.hanafi)
        val next = Astro.nextPrayer(now, place, style.prayerMethod, style.hanafi)
        if (next != null) RefreshAt(context, id, WidgetKind.PRAYER, next.second.toInstant().toEpochMilli())
        val tf = timeFormat(context)
        val cls = sizeClass()
        Frame(style, actionStartActivity(editIntent(context, id))) {
            if (times == null || next == null) {
                WText("Times can't be worked out for ${place.name} today", style = style.text(13f).copy(textAlign = TextAlign.Center))
                return@Frame
            }
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = style.hAlign.glance()) {
                WText("${next.first} · ${next.second.format(tf)}", style = style.text(15f, bold = true), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
                Countdown(context, style, next.second.toInstant().toEpochMilli(), if (cls == SizeClass.SMALL) 26f else 30f)
                if (cls == SizeClass.SMALL) {
                    WText(place.name, style = style.text(11f, alpha = 0.7f), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
                } else if (cls == SizeClass.WIDE) {
                    Spacer(GlanceModifier.height(6.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        times.list().forEach { (name, t) ->
                            val on = name == next.first && t == next.second
                            Column(
                                GlanceModifier.defaultWeight().cornerRadius(10.dp).background(fgProvider(style, if (on) 0.18f else 0f)).padding(vertical = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                WText(name, style = style.text(11f, bold = on, alpha = 0.8f).copy(textAlign = TextAlign.Center), maxLines = 1)
                                WText(t.format(tf), style = style.text(12f, bold = on).copy(textAlign = TextAlign.Center), maxLines = 1)
                            }
                        }
                    }
                } else {
                    Spacer(GlanceModifier.height(6.dp))
                    // Own column: Glance containers hold at most 10 children.
                    Column(GlanceModifier.fillMaxWidth()) { times.withSunrise().forEach { (name, t) ->
                        val on = name == next.first && t == next.second
                        Row(
                            GlanceModifier.fillMaxWidth().cornerRadius(10.dp).background(fgProvider(style, if (on) 0.18f else 0f)).padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WText(name, style = style.text(14f, bold = on, alpha = if (name == "Sunrise") 0.6f else 1f).copy(textAlign = TextAlign.Start), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                            WText(t.format(tf), style = style.text(14f, bold = on, alpha = if (name == "Sunrise") 0.6f else 1f), maxLines = 1)
                        }
                    } }
                    Spacer(GlanceModifier.height(4.dp))
                    WText("${place.name} · ${style.prayerMethod.label}", style = style.text(11f, alpha = 0.65f), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
                }
            }
        }
    }
}

class PrayerReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = PrayerWidget() }
