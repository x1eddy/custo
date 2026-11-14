package app.custo.feature.widgets

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.custo.container
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.Hint
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SupportBadge
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.rememberActionRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

private fun hm(min: Int) = "%d:%02d".format(min / 60, min % 60)

/** Editor options for the second wave of widget kinds. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun NewKindOptions(kind: WidgetKind, style: WidgetStyle, onChange: (WidgetStyle) -> Unit) {
    val ctx = LocalContext.current
    val loc = LocalConfiguration.current.locales[0]
    when (kind) {
        WidgetKind.WORK -> SectionCard("Work hours") {
            var picking by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
            Row {
                FilledTonalButton({ picking = true }) { Icon(Icons.Rounded.Schedule, null); Text("  Start ${hm(style.workStart)}") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton({ picking = false }) { Text("End ${hm(style.workEnd)}") }
            }
            Text("Workdays")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { d ->
                    val on = d.value in style.workDays
                    FilterChip(on, {
                        val next = if (on) style.workDays - d.value else (style.workDays + d.value).sorted()
                        if (next.isNotEmpty()) onChange(style.copy(workDays = next))
                    }, { Text(d.getDisplayName(TextStyle.SHORT, loc)) })
                }
            }
            Hint("Counts down only during work hours. Outside them it just says when you're back.")
            picking?.let { start ->
                val m = if (start) style.workStart else style.workEnd
                val state = rememberTimePickerState(m / 60, m % 60, android.text.format.DateFormat.is24HourFormat(ctx))
                AlertDialog(
                    onDismissRequest = { picking = null },
                    confirmButton = {
                        TextButton({
                            val v = state.hour * 60 + state.minute
                            onChange(if (start) style.copy(workStart = v) else style.copy(workEnd = v))
                            picking = null
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton({ picking = null }) { Text("Cancel") } },
                    text = { TimePicker(state) },
                )
            }
            if (style.workEnd <= style.workStart) Hint("The end time should be after the start.")
        }
        WidgetKind.WEEK -> SectionCard("Count down to") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { d ->
                    FilterChip(style.weekDay == d, { onChange(style.copy(weekDay = d)) }, { Text(DayOfWeek.of(d).getDisplayName(TextStyle.FULL, loc)) })
                }
                FilterChip(style.weekDay == 6, { onChange(style.copy(weekDay = 6)) }, { Text("The weekend") })
                FilterChip(style.weekDay == 7, { onChange(style.copy(weekDay = 7)) }, { Text(DayOfWeek.SUNDAY.getDisplayName(TextStyle.FULL, loc)) })
            }
        }
        WidgetKind.WATER -> {
            SectionCard("Button") {
                Text("Which drink the +1 button adds")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Drink.entries.forEach { d -> FilterChip(style.drink == d, { onChange(style.copy(drink = d)) }, { Text("${d.emoji} ${d.label}") }) }
                }
            }
            SectionCard("Water") { WaterPanel() }
        }
        WidgetKind.MOOD -> {
            SectionCard("Look") {
                SwitchRow("Drawn faces", "Instead of emoji", style.moodFaces) { onChange(style.copy(moodFaces = it)) }
                SwitchRow("Week starts Monday", null, style.mondayFirst) { onChange(style.copy(mondayFirst = it)) }
            }
            SectionCard("Mood") { MoodPanel(style.moodFaces) }
        }
        WidgetKind.TODO -> SectionCard("To-do") { TodoPanel() }
        WidgetKind.STEPS -> SectionCard("Steps") {
            var granted by remember { mutableStateOf(StepSensor.granted(ctx)) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
            if (!StepSensor.present(ctx)) Hint("This phone has no step counter, so this widget can't count.")
            else if (!granted) {
                Hint("Custo needs activity access to read the step counter. Steps never leave your phone.")
                FilledTonalButton({ launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) { Text("Allow step counting") }
            } else SupportBadge("Counting steps", true)
            Text("Daily goal · %,d".format(style.stepGoal))
            androidx.compose.material3.Slider(style.stepGoal.toFloat(), { onChange(style.copy(stepGoal = (it / 500).roundToInt() * 500)) }, valueRange = 2000f..20000f)
            Hint("Updates about every 30 minutes, or when you tap it.")
        }
        WidgetKind.SCREEN_TIME -> SectionCard("Usage access") {
            val runner = rememberActionRunner()
            var tick by remember { mutableIntStateOf(0) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
            val granted = remember(tick) { ScreenTime.granted(ctx) }
            val shizuku by ctx.container.shizuku.state.collectAsState()
            SupportBadge(if (granted) "Usage access on" else "Usage access off", granted)
            if (!granted) {
                Hint("Screen time needs usage access. Find Custo in the list and turn it on.")
                Row {
                    FilledTonalButton({
                        val i = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${ctx.packageName}".toUri())
                        runCatching { ctx.startActivity(i) }.onFailure { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    }) { Text("Open settings") }
                    if (shizuku == ShizukuState.READY) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton({
                            runner.run("Usage access on") {
                                val r = ctx.container.shizuku.run(ScreenTime.grantCommand(ctx.packageName))
                                if (r.code != 0) error(r.output.ifBlank { "Couldn't turn on usage access" })
                                tick++
                                Widgets.refresh(ctx, WidgetKind.SCREEN_TIME)
                            }
                        }) { Text("Grant with Shizuku") }
                    }
                }
            }
            Hint("Counted on your phone from today's app usage. Updates hourly, or when you tap it.")
        }
        WidgetKind.MOON -> {
            SectionCard("Moon & sun") {
                SwitchRow("Sunrise and sunset", null, style.showSun) { onChange(style.copy(showSun = it)) }
            }
            if (style.showSun) PlacePicker(style, onChange)
        }
        WidgetKind.PRAYER -> {
            SectionCard("Calculation") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrayerMethod.entries.forEach { m -> FilterChip(style.prayerMethod == m, { onChange(style.copy(prayerMethod = m)) }, { Text(m.label) }) }
                }
                Text("Asr")
                Segments(listOf(false, true), style.hanafi, { if (it) "Hanafi" else "Standard (Shafi'i)" }) { onChange(style.copy(hanafi = it)) }
                Hint("Worked out on your phone from the sun's position. Check your local mosque's timetable for exact times.")
            }
            PlacePicker(style, onChange)
        }
        WidgetKind.FISH, WidgetKind.BUBBLES, WidgetKind.SWITCH -> SectionCard("Just tap") {
            Hint("Nothing to set up. Tap the widget on your home screen and relax.")
        }
        WidgetKind.COUPLE -> SectionCard("Together") {
            var picking by remember { mutableStateOf(false) }
            val pick1 = photoPicker { onChange(style.copy(photoUri = it)) }
            val pick2 = photoPicker { onChange(style.copy(photoUri2 = it)) }
            Row {
                OutlinedTextField(style.name1, { onChange(style.copy(name1 = it.take(20))) }, Modifier.weight(1f), label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(style.name2, { onChange(style.copy(name2 = it.take(20))) }, Modifier.weight(1f), label = { Text("Name") }, singleLine = true)
            }
            Row {
                FilledTonalButton({ pick1.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Image, null); Text(if (style.photoUri == null) "  Photo 1" else "  Change")
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton({ pick2.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Image, null); Text(if (style.photoUri2 == null) "  Photo 2" else "  Change")
                }
            }
            val since = if (style.sinceDay == 0L) null else LocalDate.ofEpochDay(style.sinceDay)
            FilledTonalButton({ picking = true }) {
                Icon(Icons.Rounded.CalendarMonth, null)
                Text(if (since == null) "  Together since…" else "  Since " + since.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
            }
            if (picking) {
                val state = rememberDatePickerState(initialSelectedDateMillis = (since ?: LocalDate.now()).toEpochDay() * 86_400_000L)
                DatePickerDialog(
                    onDismissRequest = { picking = false },
                    confirmButton = {
                        TextButton({
                            state.selectedDateMillis?.let { onChange(style.copy(sinceDay = (it / 86_400_000L).coerceAtMost(LocalDate.now().toEpochDay()))) }
                            picking = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton({ picking = false }) { Text("Cancel") } },
                ) { DatePicker(state) }
            }
            Hint("Only the photos you pick are shared with Custo.")
        }
        WidgetKind.CAROUSEL -> SectionCard("Photos", subtitle = "${style.photos.size} of 20") {
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
                if (uris.isNotEmpty()) {
                    uris.forEach { runCatching { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                    onChange(style.copy(photos = (style.photos + uris.map { it.toString() }).distinct().takeLast(20)))
                }
            }
            Row {
                FilledTonalButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Rounded.Image, null); Text(if (style.photos.isEmpty()) "  Pick photos" else "  Add photos")
                }
                if (style.photos.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton({
                        style.photos.forEach { u -> runCatching { ctx.contentResolver.releasePersistableUriPermission(u.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                        onChange(style.copy(photos = emptyList()))
                    }) { Text("Clear") }
                }
            }
            Text("Change photo every")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60).forEach { m -> FilterChip(style.rotateMinutes == m, { onChange(style.copy(rotateMinutes = m)) }, { Text("$m min") }) }
            }
            Hint("Tap the widget for the next photo. Only the photos you pick are shared with Custo.")
        }
        else -> Unit
    }
}

@Composable
private fun photoPicker(onPicked: (String) -> Unit) = run {
    val ctx = LocalContext.current
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onPicked(uri.toString())
        }
    }
}

/** City list (searchable) or custom coordinates. No location permission needed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlacePicker(style: WidgetStyle, onChange: (WidgetStyle) -> Unit) {
    SectionCard("Place", subtitle = style.place().name) {
        var query by remember { mutableStateOf("") }
        val custom = findCity(style.city) == null
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search cities") }, singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val shown = CITIES.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                .sortedByDescending { it.name == style.city }
                .take(if (query.isBlank()) 12 else 30)
            shown.forEach { c ->
                FilterChip(style.city == c.name, { onChange(style.copy(city = c.name, lat = c.lat, lon = c.lon, zone = c.zone)) }, { Text(c.name) })
            }
            FilterChip(custom, { onChange(style.copy(city = "", zone = "")) }, { Text("Custom…") })
        }
        if (custom) {
            var lat by remember { mutableStateOf(style.lat.toString()) }
            var lon by remember { mutableStateOf(style.lon.toString()) }
            Row {
                OutlinedTextField(lat, { v ->
                    lat = v; v.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }?.let { onChange(style.copy(lat = it)) }
                }, Modifier.weight(1f), label = { Text("Latitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(lon, { v ->
                    lon = v; v.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }?.let { onChange(style.copy(lon = it)) }
                }, Modifier.weight(1f), label = { Text("Longitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Hint("Use decimal degrees, like 51.5 and -0.13. Times show in your phone's time zone.")
        }
    }
}
