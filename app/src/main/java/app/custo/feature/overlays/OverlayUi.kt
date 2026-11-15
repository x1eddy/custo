package app.custo.feature.overlays

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.data.Prefs
import app.custo.ui.components.ActionRunner
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Prefs plus a setter and service state, shared by every overlay screen. */
class OverlayUiState(val p: Preferences, val running: Boolean, val runner: ActionRunner, private val prefs: Prefs) {
    fun <T> set(key: Preferences.Key<T>, v: T) = runner.run { prefs.set(key, v) }

    fun preview(kind: Preview) {
        if (!running) runner.message("Turn on the overlay service first") else OverlayBus.preview.tryEmit(kind)
    }
}

@Composable
fun rememberOverlayUi(): OverlayUiState {
    val c = LocalContext.current.container
    val runner = rememberActionRunner()
    val p by c.prefs.data.collectAsStateWithLifecycle(emptyPreferences())
    val running by OverlayBus.serviceRunning.collectAsStateWithLifecycle()
    return OverlayUiState(p, running, runner, c.prefs)
}

/** A labelled slider that saves when the finger lifts. */
@Composable
fun PrefSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String, steps: Int = 0, onDone: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text("$label · ${format(v)}", style = MaterialTheme.typography.labelMedium)
        Slider(v, { v = it }, valueRange = range, steps = steps, onValueChangeFinished = { onDone(v) })
    }
}

fun fmtDp(v: Float) = "${v.roundToInt()} dp"
fun fmtPct(v: Float) = "${(v * 100).roundToInt()} %"
fun fmtSec(v: Float) = "%.1f s".format(v)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <E : Enum<E>> ChoiceChips(entries: List<E>, selected: E, label: (E) -> String, onSelect: (E) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { e -> FilterChip(selected == e, { onSelect(e) }, { Text(label(e)) }) }
    }
}

@Composable
fun <E : Enum<E>> Segmented(entries: List<E>, selected: E, label: (E) -> String, onSelect: (E) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        entries.forEachIndexed { i, e ->
            SegmentedButton(selected == e, { onSelect(e) }, SegmentedButtonDefaults.itemShape(i, entries.size)) {
                Text(label(e), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "Label ........ [icon Action ▾]" row. */
@Composable
fun ActionRow(label: String, current: OverlayAction, choices: List<OverlayAction>, onPick: (OverlayAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) {
                if (current.icon != 0) {
                    androidx.compose.material3.Icon(painterResource(current.icon), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(current.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(open, { open = false }) {
                choices.forEach { a ->
                    DropdownMenuItem(
                        { Text(a.label) }, { onPick(a); open = false },
                        leadingIcon = { if (a.icon != 0) androidx.compose.material3.Icon(painterResource(a.icon), null, Modifier.size(20.dp)) else Spacer(Modifier.size(20.dp)) },
                    )
                }
            }
        }
    }
}

/** Switch row led by an icon. */
@Composable
fun IconSwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) { app.custo.ui.components.SwitchRow(label, null, checked, onChange = onChange) }
    }
}

/** An action as a toggleable icon tile with a tiny caption. */
@Composable
fun ActionToggle(a: OverlayAction, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(64.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .background(if (on) cs.primary else cs.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (a.icon != 0) androidx.compose.material3.Icon(painterResource(a.icon), a.label, Modifier.size(22.dp), tint = if (on) cs.onPrimary else cs.onSurfaceVariant)
        }
        Text(
            a.label.substringBefore(" /").removePrefix("Open "), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (on) cs.onSurface else cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun PreviewButton(text: String = "Try", onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text)
    }
}

@Composable
fun AppIcon(pkg: String, size: Int = 36) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { Apps.icon(ctx, pkg)?.toBitmap(96, 96)?.asImageBitmap() }
    }
    Box(Modifier.size(size.dp)) { bmp?.let { Image(it, null, Modifier.size(size.dp)) } }
}

/** Pick installed launcher apps. [max] limits how many can be chosen. */
@Composable
fun AppPickerDialog(title: String, selected: List<String>, max: Int = Int.MAX_VALUE, onDismiss: () -> Unit, onDone: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<LauncherApp>?>(null) { value = Apps.launcherApps(ctx) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (max != Int.MAX_VALUE) Text("${chosen.size} of $max", style = MaterialTheme.typography.labelMedium)
                val list = apps
                if (list == null) Text("Loading…")
                else LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }, key = { it.pkg }) { app ->
                        val on = app.pkg in chosen
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (on) chosen.remove(app.pkg) else if (chosen.size < max) chosen.add(app.pkg)
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.pkg, 32)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Checkbox(on, null)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen.toList()) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A button showing a time of day that opens a time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeButton(label: String, minute: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("$label ${OverlayLogic.formatMinute(minute)}") }
    if (open) {
        val state = rememberTimePickerState(minute / 60, minute % 60)
        AlertDialog(
            onDismissRequest = { open = false },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute); open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}
