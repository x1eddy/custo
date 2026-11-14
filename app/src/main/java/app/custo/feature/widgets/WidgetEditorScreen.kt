package app.custo.feature.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.rememberActionRunner
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetEditorScreen(appWidgetId: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val runner = rememberActionRunner()
    var widget by remember { mutableStateOf<PlacedWidget?>(null) }
    var style by remember { mutableStateOf(WidgetStyle()) }
    var missing by remember { mutableStateOf(false) }

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
        item { WidgetPreview(w.kind, style, Modifier.fillMaxWidth().height(w.kind.previewHeight().dp)) }

        if (w.kind == WidgetKind.CLOCK) item {
            SectionCard("Clock") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ClockLook.entries.forEachIndexed { i, l ->
                        SegmentedButton(style.clock == l, { style = style.copy(clock = l) }, SegmentedButtonDefaults.itemShape(i, ClockLook.entries.size)) { Text(l.label) }
                    }
                }
                SwitchRow("Show date", null, style.showDate) { style = style.copy(showDate = it) }
            }
        }
        if (w.kind == WidgetKind.NOTE) item {
            SectionCard("Note") {
                OutlinedTextField(style.note, { style = style.copy(note = it) }, Modifier.fillMaxWidth(), minLines = 3)
            }
        }
        item {
            SectionCard("Font") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetFont.entries.forEach { f -> FilterChip(style.font == f, { style = style.copy(font = f) }, { Text(f.label) }) }
                }
            }
        }
        item {
            SectionCard("Colors") {
                SwitchRow("Match Material You", "Follows your system colors and dark mode", style.themed) { style = style.copy(themed = it) }
                if (!style.themed) {
                    Text("Background", style = MaterialTheme.typography.labelLarge)
                    ColorPicker(Color(style.bg), { style = style.copy(bg = it.toArgb()) })
                    Text("Text", style = MaterialTheme.typography.labelLarge)
                    ColorPicker(Color(style.fg), { style = style.copy(fg = it.toArgb()) }, showSliders = false)
                }
            }
        }
        item {
            SectionCard("Shape") {
                Text("Background opacity · ${(style.alpha * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(style.alpha, { style = style.copy(alpha = it) })
                Text("Corner radius · ${style.corner} dp", style = MaterialTheme.typography.labelMedium)
                Slider(style.corner.toFloat(), { style = style.copy(corner = it.roundToInt()) }, valueRange = 0f..48f)
            }
        }
    }
}
