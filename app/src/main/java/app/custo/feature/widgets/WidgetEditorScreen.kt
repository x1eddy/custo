package app.custo.feature.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.custo.feature.widgetsets.WidgetThemes
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class ColorSlot(val label: String) { BG("Background"), BG2("Gradient end"), FG("Text"), BORDER("Border"), SHADOW("Shadow") }

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Slider(value, onChange, valueRange = range, steps = steps)
}

@Composable
internal fun <T> Segments(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(selected == o, { onSelect(o) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label(o), maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetEditorScreen(appWidgetId: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val runner = rememberActionRunner()
    var widget by remember { mutableStateOf<PlacedWidget?>(null) }
    var style by remember { mutableStateOf(WidgetStyle()) }
    var missing by remember { mutableStateOf(false) }
    var slot by remember { mutableStateOf(ColorSlot.BG) }

    LaunchedEffect(appWidgetId) {
        // Launched as the widget's configuration activity: confirm so the launcher keeps the widget.
        (ctx as? Activity)?.setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        val w = Widgets.find(ctx, appWidgetId)
        if (w == null) missing = true else { widget = w; style = Widgets.style(ctx, w.glanceId) }
    }

    val w = widget
    ScreenScaffold(
        title = w?.kind?.label ?: "Widget",
        onBack = onBack,
        actions = {
            IconButton(enabled = w != null, onClick = {
                runner.run("Widget updated") { Widgets.save(ctx, w!!, style); onBack() }
            }) { Icon(Icons.Rounded.Check, "Save") }
        },
    ) {
        if (missing) item { Hint("This widget is no longer on your home screen.") }
        if (w == null) return@ScreenScaffold
        item { WidgetPreview(w.kind, style, Modifier.fillMaxWidth().height(w.kind.previewHeight.dp)) }

        item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { KindOptions(w.kind, style) { style = it } } }
        item {
            SectionCard("Set style", subtitle = "Frame, colors and stickers from a widget set") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(style.themeId == null, { style = style.copy(themeId = null) }, { Text("None") })
                    WidgetThemes.all.forEach { t ->
                        FilterChip(style.themeId == t.id, { style = t.styleFor(w.kind, style.themeSlot, style) }, { Text(t.name) })
                    }
                }
                if (style.themeId != null) Hint("Colors and shape below are set by the set. Pick None to use your own.")
            }
        }

        item {
            SectionCard("Text") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetFont.entries.forEach { f -> FilterChip(style.font == f, { style = style.copy(font = f) }, { Text(f.label) }) }
                }
                LabeledSlider("Size · ${(style.textScale * 100).roundToInt()}%", style.textScale, 0.6f..1.8f) { style = style.copy(textScale = (it * 20).roundToInt() / 20f) }
                SwitchRow("Text shadow", "Easier to read on busy wallpapers", style.textShadow) { style = style.copy(textShadow = it) }
            }
        }
        item {
            SectionCard("Colors") {
                SwitchRow("Match Material You", "Follows your system colors and dark mode", style.themed) { style = style.copy(themed = it) }
                if (!style.themed) {
                    SwitchRow("Gradient background", null, style.gradient) { style = style.copy(gradient = it) }
                    if (style.gradient) {
                        LabeledSlider("Gradient angle · ${style.gradientAngle}°", style.gradientAngle.toFloat(), 0f..360f) { style = style.copy(gradientAngle = it.roundToInt()) }
                    }
                }
                val slots = ColorSlot.entries.filter {
                    when (it) {
                        ColorSlot.BG, ColorSlot.FG -> !style.themed
                        ColorSlot.BG2 -> !style.themed && style.gradient
                        ColorSlot.BORDER -> style.borderWidth > 0
                        ColorSlot.SHADOW -> style.textShadow
                    }
                }
                if (slots.isNotEmpty()) {
                    val current = if (slot in slots) slot else slots.first()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        slots.forEach { s -> FilterChip(current == s, { slot = s }, { Text(s.label) }) }
                    }
                    val value = when (current) { ColorSlot.BG -> style.bg; ColorSlot.BG2 -> style.bg2; ColorSlot.FG -> style.fg; ColorSlot.BORDER -> style.borderColor; ColorSlot.SHADOW -> style.shadowColor }
                    ColorPicker(Color(value), { c ->
                        val v = c.toArgb()
                        style = when (current) {
                            ColorSlot.BG -> style.copy(bg = v)
                            ColorSlot.BG2 -> style.copy(bg2 = v)
                            ColorSlot.FG -> style.copy(fg = v)
                            ColorSlot.BORDER -> style.copy(borderColor = v)
                            ColorSlot.SHADOW -> style.copy(shadowColor = v)
                        }
                    })
                }
                LabeledSlider("Background opacity · ${(style.alpha * 100).roundToInt()}%", style.alpha, 0f..1f) { style = style.copy(alpha = it) }
            }
        }
        item {
            SectionCard("Shape") {
                LabeledSlider("Corner radius · ${style.corner} dp", style.corner.toFloat(), 0f..48f) { style = style.copy(corner = it.roundToInt()) }
                LabeledSlider("Border · ${style.borderWidth} dp", style.borderWidth.toFloat(), 0f..8f, steps = 7) { style = style.copy(borderWidth = it.roundToInt()) }
                LabeledSlider("Padding · ${style.padding} dp", style.padding.toFloat(), 0f..32f) { style = style.copy(padding = it.roundToInt()) }
            }
        }
        item {
            SectionCard("Motion") {
                SwitchRow("Tap animation", "A quick press pulse or pop when you tap the widget", style.tapAnim) { style = style.copy(tapAnim = it) }
            }
        }
        item {
            SectionCard("Alignment") {
                Segments(HAlign.entries, style.hAlign, { it.label }) { style = style.copy(hAlign = it) }
                Segments(VAlign.entries, style.vAlign, { it.label }) { style = style.copy(vAlign = it) }
            }
        }
        item {
            val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) runner.run("Style saved") {
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri)?.use { it.write(WidgetStyle.exportPreset(style, w.kind.name).toByteArray()) }
                            ?: error("Couldn't write that file")
                    }
                }
            }
            val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) runner.run {
                    val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
                    val preset = text?.let { WidgetStyle.importPreset(it) }
                    if (preset == null) runner.message("That file isn't a Custo widget style")
                    else { style = style.withLookOf(preset); runner.message("Style applied. Tap ✓ to keep it") }
                }
            }
            SectionCard("Share the look", subtitle = "Colors, shape and text as a small file. Notes and photos stay here.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { export.launch("custo-${w.kind.name.lowercase()}-style.json") }) { Text("Export") }
                    FilledTonalButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Import") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun KindOptions(kind: WidgetKind, style: WidgetStyle, onChange: (WidgetStyle) -> Unit) {
    val ctx = LocalContext.current
    when (kind) {
        WidgetKind.CLOCK -> SectionCard("Clock") {
            Segments(ClockLook.entries, style.clock, { it.label }) { onChange(style.copy(clock = it)) }
            SwitchRow("Show date", null, style.showDate) { onChange(style.copy(showDate = it)) }
        }
        WidgetKind.HOUR -> SectionCard("Clock") {
            SwitchRow("Show date", null, style.showDate) { onChange(style.copy(showDate = it)) }
        }
        WidgetKind.ANALOG -> SectionCard("Dial") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialStyle.entries.forEach { d -> FilterChip(style.dial == d, { onChange(style.copy(dial = d)) }, { Text(d.label) }) }
            }
            SwitchRow("Second hand", "Ticks every second while visible", style.seconds) { onChange(style.copy(seconds = it)) }
        }
        WidgetKind.WORLD -> SectionCard("Cities", subtitle = "Pick up to three") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WORLD_CITIES.forEach { (city, zone) ->
                    val on = zone in style.zones
                    FilterChip(on, {
                        val next = if (on) style.zones - zone else (style.zones + zone).takeLast(3)
                        if (next.isNotEmpty()) onChange(style.copy(zones = next))
                    }, { Text(city) })
                }
            }
        }
        WidgetKind.DATE -> SectionCard("Date") {
            SwitchRow("Year progress", "Day of the year and a progress bar", style.yearProgress) { onChange(style.copy(yearProgress = it)) }
        }
        WidgetKind.CALENDAR -> SectionCard("Calendar") {
            SwitchRow("Week starts Monday", null, style.mondayFirst) { onChange(style.copy(mondayFirst = it)) }
        }
        WidgetKind.COUNTDOWN -> SectionCard("Countdown") {
            var picking by remember { mutableStateOf(false) }
            val target = WidgetLogic.countdownTarget(style.countdownDay, LocalDate.now())
            OutlinedTextField(style.countdownTitle, { onChange(style.copy(countdownTitle = it.take(40))) }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
            FilledTonalButton(onClick = { picking = true }) {
                Icon(Icons.Rounded.CalendarMonth, null); Text("  " + target.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
            }
            if (picking) {
                val state = rememberDatePickerState(initialSelectedDateMillis = target.toEpochDay() * 86_400_000L)
                DatePickerDialog(
                    onDismissRequest = { picking = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let { onChange(style.copy(countdownDay = it / 86_400_000L)) }
                            picking = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
                ) { DatePicker(state) }
            }
        }
        WidgetKind.BATTERY_RING -> SectionCard("Ring") {
            SwitchRow("Show percent", null, style.showPercent) { onChange(style.copy(showPercent = it)) }
        }
        WidgetKind.PHOTO -> SectionCard("Photo") {
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    onChange(style.copy(photoUri = uri.toString()))
                }
            }
            FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Rounded.Image, null); Text(if (style.photoUri == null) "  Pick a photo" else "  Change photo")
            }
            Hint("Only this photo is shared with Custo.")
        }
        WidgetKind.NOTE -> SectionCard("Note") {
            OutlinedTextField(style.note, { onChange(style.copy(note = it)) }, Modifier.fillMaxWidth(), minLines = 3)
        }
        WidgetKind.APPS -> SectionCard("Apps", subtitle = "Pick up to six") {
            SwitchRow("Show names", null, style.showLabels) { onChange(style.copy(showLabels = it)) }
            AppPicker(style.apps) { onChange(style.copy(apps = it)) }
        }
        WidgetKind.BANNER -> SectionCard("Banner") {
            OutlinedTextField(style.bannerText, { onChange(style.copy(bannerText = it.take(40))) }, Modifier.fillMaxWidth(), label = { Text("Big text") }, singleLine = true)
            OutlinedTextField(style.bannerSub, { onChange(style.copy(bannerSub = it.take(60))) }, Modifier.fillMaxWidth(), label = { Text("Small text (optional)") }, singleLine = true)
        }
        WidgetKind.QUOTE -> SectionCard("Quote") {
            SwitchRow("Show author", null, style.showAuthor) { onChange(style.copy(showAuthor = it)) }
        }
        WidgetKind.STOPWATCH, WidgetKind.BATTERY, WidgetKind.ALARM, WidgetKind.STORAGE, WidgetKind.MEDIA, WidgetKind.TOGGLES -> Unit
        else -> NewKindOptions(kind, style, onChange)
    }
}

private data class LauncherApp(val pkg: String, val label: String, val icon: Bitmap?)

@Composable
private fun AppPicker(selected: List<String>, onChange: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<LauncherApp>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(main, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != ctx.packageName }
                .sortedBy { it.second.lowercase() }
                .map { (pkg, label) -> LauncherApp(pkg, label, runCatching { pm.getApplicationIcon(pkg).toBitmap(72, 72) }.getOrNull()) }
        }
    }
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search apps") }, singleLine = true)
    val shown = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        .sortedByDescending { it.pkg in selected }
        .take(30)
    Column {
        shown.forEach { app ->
            val on = app.pkg in selected
            Row(
                Modifier.fillMaxWidth().clickable {
                    onChange(if (on) selected - app.pkg else if (selected.size < 6) selected + app.pkg else selected)
                }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (app.icon != null) Image(app.icon.asImageBitmap(), null, Modifier.size(32.dp)) else Spacer(Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Checkbox(on, null, enabled = on || selected.size < 6)
            }
        }
    }
    if (apps.size > shown.size && query.isBlank()) Hint("Search to find more apps.")
}
