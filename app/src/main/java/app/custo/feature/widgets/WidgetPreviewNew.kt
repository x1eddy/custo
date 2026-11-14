package app.custo.feature.widgets

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.custo.R
import app.custo.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

private fun hms(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun P.Ring(fraction: Float, side: Dp, content: @Composable () -> Unit) {
    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.minDimension * 0.11f
            val inset = Offset(sw / 2, sw / 2)
            val arc = Size(size.minDimension - sw, size.minDimension - sw)
            drawArc(fg.copy(alpha = 0.25f), 0f, 360f, false, inset, arc, style = Stroke(sw))
            drawArc(fg, -90f, 360f * fraction.coerceIn(0f, 1f), false, inset, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
private fun P.Pill(label: String) {
    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(fg).padding(horizontal = 14.dp, vertical = 7.dp)) {
        T(label, 14f, bold = true, color = bg, align = TextAlign.Center)
    }
}

/** Live previews for the second wave of widgets; same size buckets as [sizeClass]. */
@Composable
internal fun P.NewBody(kind: WidgetKind) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val w = maxWidth.value + s.padding * 2
        val h = maxHeight.value + s.padding * 2
        val cls = when {
            w >= 200 && h >= 200 -> SizeClass.LARGE
            w >= 200 -> SizeClass.WIDE
            h >= 200 -> SizeClass.TALL
            else -> SizeClass.SMALL
        }
        val minSide = minOf(maxWidth, maxHeight)
        Kind(kind, cls, minSide)
    }
}

@Composable
private fun P.Kind(kind: WidgetKind, cls: SizeClass, minSide: Dp) {
    val ctx = LocalContext.current
    val loc = LocalConfiguration.current.locales[0]
    val habits = ctx.container.habits
    val wide = Modifier.fillMaxWidth()
    val ha = when (s.hAlign) { HAlign.START -> Alignment.Start; HAlign.CENTER -> Alignment.CenterHorizontally; HAlign.END -> Alignment.End }
    val nowMs by produceState(System.currentTimeMillis()) { while (true) { delay(1000); value = System.currentTimeMillis() } }
    val tf = remember { timeFormat(ctx) }

    when (kind) {
        WidgetKind.WORK -> Column(wide, horizontalAlignment = ha) {
            val now = nowMs.let { LocalDateTime.now() }
            val st = HabitLogic.work(now, s.workStart, s.workEnd, s.workDays.toSet())
            val left = Duration.between(now, st.until).toMillis()
            when (st.phase) {
                HabitLogic.WorkPhase.WORKING, HabitLogic.WorkPhase.BEFORE -> {
                    T(if (st.phase == HabitLogic.WorkPhase.WORKING) "Work ends in" else "Work starts in", 14f, alpha = 0.8f, modifier = wide)
                    T(hms(left), if (cls == SizeClass.SMALL) 30f else 40f, bold = true, modifier = wide)
                    if (st.phase == HabitLogic.WorkPhase.WORKING) { Spacer(Modifier.height(6.dp)); Bar(st.progress) }
                }
                else -> {
                    T(if (st.phase == HabitLogic.WorkPhase.AFTER) "Done for today 🎉" else "Day off 🌿", 22f, bold = true, modifier = wide)
                    T("Back at it " + st.until.dayOfWeek.getDisplayName(TextStyle.SHORT, loc) + " " + st.until.format(tf), 13f, alpha = 0.75f, modifier = wide)
                }
            }
        }
        WidgetKind.WEEK -> Column(wide, horizontalAlignment = ha) {
            val today = LocalDate.now()
            val (top, bottom) = HabitLogic.weekText(today, s.weekDay)
            val n = HabitLogic.daysUntilWeekday(today, s.weekDay)
            if (n > 1 && !(s.weekDay == 6 && today.dayOfWeek.value >= 6)) {
                T("$n", 54f, bold = true, modifier = wide)
                T("days till ${bottom.replaceFirstChar { it.lowercase() }}", 15f, modifier = wide)
            } else {
                T(top, 15f, alpha = 0.8f, modifier = wide)
                T(bottom, 30f, bold = true, modifier = wide)
            }
            if (cls != SizeClass.SMALL) {
                Spacer(Modifier.height(10.dp))
                Row(wide) {
                    DayOfWeek.entries.forEach { d ->
                        val isToday = d == today.dayOfWeek
                        val isTarget = d.value == s.weekDay || (s.weekDay == 6 && d.value == 7)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(fg.copy(alpha = if (isToday) 1f else if (isTarget) 0.35f else 0.1f)), contentAlignment = Alignment.Center) {
                                T(d.getDisplayName(TextStyle.NARROW, loc), 11f, bold = isToday, align = TextAlign.Center, color = if (isToday) bg else fg)
                            }
                        }
                    }
                }
            }
        }
        WidgetKind.WATER -> {
            val data by habits.water.collectAsState(WaterData())
            val day = LocalDate.now().toEpochDay()
            val n = data.count(day)
            val frac = n.toFloat() / data.goal.coerceAtLeast(1)
            val ring = @Composable { side: Dp ->
                Ring(frac, side) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T(s.drink.emoji, side.value / 5f, align = TextAlign.Center)
                        T("$n/${data.goal}", side.value / 6.5f, bold = true, align = TextAlign.Center)
                    }
                }
            }
            val drinks = data.today(day).takeLast(8).joinToString("") { it.drink.emoji }.ifEmpty { "Nothing yet today" }
            when (cls) {
                SizeClass.SMALL -> ring(minSide)
                SizeClass.WIDE -> Row(wide, verticalAlignment = Alignment.CenterVertically) {
                    ring(minOf(minSide, 110.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        T(if (n >= data.goal) "Goal reached!" else "${data.goal - n} to go", 17f, bold = true, align = TextAlign.Start)
                        T(drinks, 13f, alpha = 0.8f, align = TextAlign.Start)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill("+1 ${s.drink.emoji}")
                            if (n > 0) Icon(Icons.AutoMirrored.Rounded.Undo, null, tint = fg, modifier = Modifier.padding(8.dp).size(20.dp))
                        }
                    }
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ring(minSide * 0.6f)
                    Spacer(Modifier.height(8.dp))
                    T(drinks, 14f, align = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Pill("+1 ${s.drink.label.lowercase()}")
                }
            }
        }
        WidgetKind.MOOD -> {
            val data by habits.mood.collectAsState(MoodData())
            val today = LocalDate.now()
            val mood = data.days[today.toEpochDay()]
            val face = @Composable { m: Mood?, side: Dp ->
                if (m != null && s.moodFaces) {
                    val bmp = remember(m) { WidgetArt.moodFace(m).asImageBitmap() }
                    Image(bmp, m.label, Modifier.size(side))
                } else T(m?.emoji ?: "＋", side.value * 0.62f / s.textScale, align = TextAlign.Center)
            }
            val picker = @Composable {
                Row(wide) {
                    Mood.entries.forEach { m ->
                        Box(
                            Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (m == mood) fg.copy(alpha = 0.18f) else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) { if (s.moodFaces) face(m, 22.dp) else T(m.emoji, 16f, align = TextAlign.Center) }
                    }
                }
            }
            when (cls) {
                SizeClass.SMALL -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    face(mood, 56.dp)
                    T(mood?.label ?: "How are you?", 14f, bold = true, align = TextAlign.Center)
                }
                SizeClass.WIDE -> Column(wide) {
                    T(if (mood == null) "How are you today?" else "Today: ${mood.label}", 15f, bold = true, modifier = wide)
                    Spacer(Modifier.height(8.dp))
                    picker()
                }
                else -> Column(wide) {
                    T(today.format(DateTimeFormatter.ofPattern("MMMM", loc)) + (mood?.let { " · ${it.emoji}" } ?: ""), 15f, bold = true, modifier = wide)
                    Spacer(Modifier.height(4.dp))
                    MoodMonth(today, data, s.mondayFirst)
                    if (cls == SizeClass.LARGE) { Spacer(Modifier.height(6.dp)); picker() }
                }
            }
        }
        WidgetKind.TODO -> Column(Modifier.fillMaxSize()) {
            val data by habits.todo.collectAsState(TodoData())
            Row(wide, verticalAlignment = Alignment.CenterVertically) {
                T("To-do", 17f, bold = true, align = TextAlign.Start, modifier = Modifier.weight(1f))
                if (data.items.isNotEmpty()) T("${data.items.count { !it.done }} left", 12f, alpha = 0.7f)
                Icon(Icons.Rounded.Add, null, tint = fg, modifier = Modifier.padding(6.dp).size(22.dp))
            }
            if (data.items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T("All clear. Tap + to add something", 13f, alpha = 0.75f, align = TextAlign.Center) }
            else data.items.sortedBy { it.done }.take(8).forEach { item ->
                Row(wide.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (item.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = fg.copy(alpha = if (item.done) 0.5f else 1f), modifier = Modifier.size((20 * s.textScale).dp))
                    Spacer(Modifier.width(10.dp))
                    T(item.text, 15f, alpha = if (item.done) 0.5f else 1f, align = TextAlign.Start)
                }
            }
        }
        WidgetKind.STEPS -> {
            val result by produceState<StepSensor.Result?>(null) { value = StepSensor.today(ctx) }
            when (val r = result) {
                null -> Unit
                StepSensor.Result.NoSensor -> T("This phone has no step counter", 14f, align = TextAlign.Center)
                StepSensor.Result.NoPermission -> T("Tap to allow step counting", 14f, align = TextAlign.Center)
                is StepSensor.Result.Steps -> {
                    val frac = r.today.toFloat() / s.stepGoal.coerceAtLeast(1)
                    if (cls == SizeClass.WIDE) Column(wide, horizontalAlignment = ha) {
                        T("Steps today", 14f, alpha = 0.8f, modifier = wide)
                        T("%,d".format(r.today), 36f, bold = true, modifier = wide)
                        Spacer(Modifier.height(6.dp)); Bar(frac); Spacer(Modifier.height(4.dp))
                        T("Goal %,d".format(s.stepGoal), 12f, alpha = 0.7f, modifier = wide)
                    } else Ring(frac, minSide) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            T("👟", 16f, align = TextAlign.Center)
                            T("%,d".format(r.today), 22f, bold = true, align = TextAlign.Center)
                            T("of %,d".format(s.stepGoal), 11f, alpha = 0.7f, align = TextAlign.Center)
                        }
                    }
                }
            }
        }
        WidgetKind.SCREEN_TIME -> {
            val info by produceState<ScreenTimeInfo?>(null) { value = ScreenTime.today(ctx) }
            val icons by produceState<List<Pair<Bitmap?, String>>>(emptyList(), info) {
                value = withContext(Dispatchers.IO) { info?.top?.map { WidgetArt.appIcon(ctx, it.first, 72) to WidgetArt.appLabel(ctx, it.first) } ?: emptyList() }
            }
            val i = info
            if (i == null) T("Tap to allow usage access", 14f, align = TextAlign.Center)
            else Column(wide, horizontalAlignment = ha) {
                T("Screen time today", 13f, alpha = 0.8f, modifier = wide)
                T(HabitLogic.durationText(i.totalMs), 30f, bold = true, modifier = wide)
                Spacer(Modifier.height(6.dp))
                val max = i.top.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
                i.top.forEachIndexed { idx, (_, ms) ->
                    val (icon, label) = icons.getOrNull(idx) ?: (null to "")
                    if (cls == SizeClass.SMALL) return@forEachIndexed
                    Row(wide.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(22.dp)) else Spacer(Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(wide) {
                                T(label, 13f, align = TextAlign.Start, modifier = Modifier.weight(1f))
                                T(HabitLogic.durationText(ms), 12f, alpha = 0.75f)
                            }
                            if (cls != SizeClass.WIDE) Bar(ms.toFloat() / max)
                        }
                    }
                }
            }
        }
        WidgetKind.MOON -> {
            val moon = remember { Astro.moon(Instant.now()) }
            val bmp = remember(moon) { WidgetArt.moon(moon.fraction).asImageBitmap() }
            val place = s.place()
            val sun = remember(place) { Astro.sunTimes(LocalDate.now(place.zoneId()), place) }
            val side = when (cls) { SizeClass.SMALL -> 64.dp; SizeClass.WIDE -> 80.dp; else -> 110.dp }
            val info = @Composable {
                val align = if (cls == SizeClass.WIDE) TextAlign.Start else TextAlign.Center
                Column(horizontalAlignment = if (cls == SizeClass.WIDE) Alignment.Start else Alignment.CenterHorizontally) {
                    T(moon.phase.label, 16f, bold = true, align = align)
                    T("${(moon.illumination * 100).roundToInt()}% lit", 12f, alpha = 0.75f, align = align)
                    if (s.showSun && cls != SizeClass.SMALL) {
                        Spacer(Modifier.height(6.dp))
                        T("↑ ${sun.sunrise?.format(tf) ?: "—"}   ↓ ${sun.sunset?.format(tf) ?: "—"}", 14f, align = align)
                        T(place.name, 11f, alpha = 0.7f, align = align)
                    }
                }
            }
            if (cls == SizeClass.WIDE) Row(verticalAlignment = Alignment.CenterVertically) {
                Image(bmp, null, Modifier.size(side)); Spacer(Modifier.width(14.dp)); info()
            } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bmp, null, Modifier.size(minOf(side, minSide * 0.55f))); Spacer(Modifier.height(6.dp)); info()
            }
        }
        WidgetKind.PRAYER -> {
            val place = s.place()
            val now = ZonedDateTime.now(place.zoneId())
            val times = remember(s.city, s.lat, s.lon, s.prayerMethod, s.hanafi, now.toLocalDate()) { Astro.prayerTimes(now.toLocalDate(), place, s.prayerMethod, s.hanafi) }
            val next = remember(s.city, s.lat, s.lon, s.prayerMethod, s.hanafi, nowMs / 60_000) { Astro.nextPrayer(now, place, s.prayerMethod, s.hanafi) }
            if (times == null || next == null) T("Times can't be worked out for ${place.name} today", 13f, align = TextAlign.Center)
            else Column(wide, horizontalAlignment = ha) {
                T("${next.first} · ${next.second.format(tf)}", 15f, bold = true, modifier = wide)
                T(hms(next.second.toInstant().toEpochMilli() - nowMs), if (cls == SizeClass.SMALL) 26f else 30f, bold = true, modifier = wide)
                if (cls == SizeClass.WIDE) {
                    Spacer(Modifier.height(6.dp))
                    Row(wide) {
                        times.list().forEach { (name, t) ->
                            val on = name == next.first && t == next.second
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(fg.copy(alpha = if (on) 0.18f else 0f)).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                T(name, 11f, bold = on, alpha = 0.8f, align = TextAlign.Center)
                                T(t.format(tf), 12f, bold = on, align = TextAlign.Center)
                            }
                        }
                    }
                } else if (cls != SizeClass.SMALL) {
                    Spacer(Modifier.height(6.dp))
                    times.withSunrise().forEach { (name, t) ->
                        val on = name == next.first && t == next.second
                        val a = if (name == "Sunrise") 0.6f else 1f
                        Row(wide.clip(RoundedCornerShape(10.dp)).background(fg.copy(alpha = if (on) 0.18f else 0f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            T(name, 14f, bold = on, alpha = a, align = TextAlign.Start, modifier = Modifier.weight(1f))
                            T(t.format(tf), 14f, bold = on, alpha = a)
                        }
                    }
                    T("${place.name} · ${s.prayerMethod.label}", 11f, alpha = 0.65f, modifier = wide)
                } else T(place.name, 11f, alpha = 0.7f, modifier = wide)
            }
        }
        WidgetKind.FISH -> {
            val bmp = remember { WidgetArt.woodenFish(false).asImageBitmap() }
            val label = @Composable {
                val align = if (cls == SizeClass.WIDE) TextAlign.Start else TextAlign.Center
                Column(horizontalAlignment = if (cls == SizeClass.WIDE) Alignment.Start else Alignment.CenterHorizontally) {
                    T("Tap the fish", 16f, bold = true, align = align)
                    T("Today 0", 12f, alpha = 0.75f, align = align)
                }
            }
            if (cls == SizeClass.WIDE) Row(verticalAlignment = Alignment.CenterVertically) { Image(bmp, null, Modifier.size(minSide)); Spacer(Modifier.width(12.dp)); label() }
            else Column(horizontalAlignment = Alignment.CenterHorizontally) { Image(bmp, null, Modifier.size(minSide - 44.dp)); label() }
        }
        WidgetKind.BUBBLES -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val (cols, rows) = Bubbles.grid(cls)
            val popped = setOf(1, 6, 7, 12)
            val header = cls != SizeClass.SMALL
            if (header) Row(wide.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                T("${cols * rows - popped.size} left", 14f, bold = true, align = TextAlign.Start, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Refresh, null, tint = fg, modifier = Modifier.size(20.dp))
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                val cell = minOf(maxWidth / cols, maxHeight / rows).coerceAtMost(56.dp)
                Column(Modifier.align(Alignment.Center)) {
                    for (r in 0 until rows) Row {
                        for (c in 0 until cols) {
                            val i = r * cols + c
                            Image(
                                painterResource(if (i in popped) R.drawable.bubble_popped else R.drawable.bubble_full), null,
                                Modifier.size(cell).padding(cell * 0.06f), colorFilter = ColorFilter.tint(fg),
                            )
                        }
                    }
                }
            }
        }
        WidgetKind.SWITCH -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val tw = minOf(maxOf(minSide, 80.dp), 160.dp)
            val th = tw * 0.48f
            Row(
                Modifier.width(tw).height(th).clip(RoundedCornerShape(th / 2)).background(fg).padding(th * 0.1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(th * 0.8f).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                    T("I", th.value * 0.22f / s.textScale, bold = true, align = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(8.dp))
            T("Go on, click it", 13f, alpha = 0.8f, align = TextAlign.Center)
        }
        WidgetKind.COUPLE -> {
            val p1 by produceState<Bitmap?>(null, s.photoUri) { value = withContext(Dispatchers.IO) { WidgetArt.photo(ctx, s.photoUri, 300) } }
            val p2 by produceState<Bitmap?>(null, s.photoUri2) { value = withContext(Dispatchers.IO) { WidgetArt.photo(ctx, s.photoUri2, 300) } }
            val today = LocalDate.now()
            val since = if (s.sinceDay == 0L) today.minusDays(522) else LocalDate.ofEpochDay(s.sinceDay)
            val days = HabitLogic.daysTogether(since, today)
            val avatar = if (cls == SizeClass.SMALL) 44.dp else 60.dp
            Column(wide, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p1, s.name1.ifBlank { "You" }, avatar)
                    T(" ❤️ ", avatar.value / 3f / s.textScale, align = TextAlign.Center)
                    Avatar(p2, s.name2.ifBlank { "Me" }, avatar)
                }
                Spacer(Modifier.height(6.dp))
                if (cls != SizeClass.SMALL) T("${s.name1.ifBlank { "You" }} & ${s.name2.ifBlank { "Me" }}", 14f, alpha = 0.85f, align = TextAlign.Center)
                T("Together $days days", if (cls == SizeClass.SMALL) 15f else 19f, bold = true, align = TextAlign.Center)
                if (cls != SizeClass.SMALL) {
                    val next = HabitLogic.nextAnniversary(since, today)
                    val years = HabitLogic.yearsAt(since, next)
                    T(if (years <= 0) "Since ${since.format(DateTimeFormatter.ofPattern("d MMM yyyy", loc))}" else "$years ${if (years == 1) "year" else "years"} in ${WidgetLogic.daysUntil(next, today)} days", 12f, alpha = 0.7f, align = TextAlign.Center)
                }
            }
        }
        WidgetKind.CAROUSEL -> {
            val bmp by produceState<Bitmap?>(null, s.photos.firstOrNull()) { value = withContext(Dispatchers.IO) { WidgetArt.photo(ctx, s.photos.firstOrNull(), 600) } }
            val b = bmp
            if (b != null) {
                Image(b.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape((s.corner - s.borderWidth).coerceAtLeast(0).dp)), contentScale = ContentScale.Crop)
            } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Image, null, tint = fg, modifier = Modifier.size(36.dp))
                T("Tap to pick up to 20 photos", 13f, align = TextAlign.Center)
            }
        }
        else -> Unit
    }
}

@Composable
private fun P.Avatar(bmp: Bitmap?, name: String, side: Dp) {
    if (bmp != null) Image(bmp.asImageBitmap(), name, Modifier.size(side).clip(CircleShape), contentScale = ContentScale.Crop)
    else Box(Modifier.size(side).clip(CircleShape).background(fg.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        T(name.take(1).uppercase(), side.value / 2.6f / s.textScale, bold = true, align = TextAlign.Center)
    }
}

/** Month grid colored by mood; shared by the preview and the in-app history. */
@Composable
internal fun P.MoodMonth(month: LocalDate, data: MoodData, mondayFirst: Boolean, onDay: ((LocalDate) -> Unit)? = null) {
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        WidgetLogic.monthGrid(month, mondayFirst).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    val date = if (d == 0) null else month.withDayOfMonth(d)
                    val m = date?.let { data.days[it.toEpochDay()] }
                    val color = when {
                        m != null -> Color(m.color)
                        date != null -> fg.copy(alpha = if (date == today) 0.3f else 0.08f)
                        else -> Color.Transparent
                    }
                    var mod = Modifier.weight(1f).padding(horizontal = 1.dp).height((20 * s.textScale).dp).clip(RoundedCornerShape(6.dp)).background(color)
                    if (onDay != null && date != null && !date.isAfter(today)) mod = mod.clickable { onDay(date) }
                    Box(mod, contentAlignment = Alignment.Center) {
                        if (d != 0) T("$d", 11f, align = TextAlign.Center, color = if (m != null) Color(0xFF1B1B1B) else fg)
                    }
                }
            }
        }
    }
}

