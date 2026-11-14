package app.custo.feature.widgets

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.custo.container
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** In-app home for the tracker widgets: water, mood and to-do. */
@Composable
fun HabitsScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "Trackers", onBack = onBack) {
        item { SectionCard("Water") { WaterPanel() } }
        item { SectionCard("Mood") { MoodPanel() } }
        item { SectionCard("To-do") { TodoPanel() } }
        item { Hint("Everything stays on this phone. Changes save right away.") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterPanel() {
    val ctx = LocalContext.current
    val habits = ctx.container.habits
    val scope = rememberCoroutineScope()
    val data by habits.water.collectAsState(WaterData())
    val today = LocalDate.now()
    val n = data.count(today.toEpochDay())
    fun update(block: (WaterData) -> WaterData) = scope.launch {
        habits.updateWater(block)
        WidgetAlarms.scheduleWater(ctx, habits.waterNow())
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) update { it.copy(remind = true) }
    }

    Text("$n of ${data.goal} today", style = MaterialTheme.typography.headlineSmall)
    Text(data.today(today.toEpochDay()).joinToString(" ") { it.drink.emoji }.ifEmpty { "Nothing yet today" }, style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Drink.entries.forEach { d ->
            AssistChip({ scope.launch { habits.addSip(d) } }, { Text("${d.emoji} +1 ${d.label.lowercase()}") })
        }
        if (n > 0) AssistChip({ update { it.undo(today.toEpochDay()) } }, { Text("Undo") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Undo, null) })
    }
    Text("Daily goal · ${data.goal} glasses", style = MaterialTheme.typography.labelMedium)
    Slider(data.goal.toFloat(), { v -> update { it.copy(goal = v.roundToInt()) } }, valueRange = 1f..20f, steps = 18)

    Text("Last 7 days", style = MaterialTheme.typography.labelMedium)
    val loc = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        (6 downTo 0).forEach { back ->
            val d = today.minusDays(back.toLong())
            val c = data.count(d.toEpochDay())
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text("$c", style = MaterialTheme.typography.labelSmall)
                val frac = (c.toFloat() / data.goal.coerceAtLeast(1)).coerceIn(0.04f, 1f)
                Box(
                    Modifier.fillMaxWidth().height((50 * frac).dp).clip(RoundedCornerShape(6.dp))
                        .background(if (c >= data.goal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                )
                Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, loc), style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    SwitchRow("Remind me", "A gentle nudge every ${data.everyHours} h from ${data.fromHour}:00 to ${data.toHour}:00", data.remind) { on ->
        if (on && !WidgetAlarms.canNotify(ctx) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else update { it.copy(remind = on) }
    }
    if (data.remind) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 4).forEach { h -> FilterChip(data.everyHours == h, { update { it.copy(everyHours = h) } }, { Text("Every $h h") }) }
        }
        RangeSlider(
            data.fromHour.toFloat()..data.toHour.toFloat(),
            { r -> update { it.copy(fromHour = r.start.roundToInt(), toHour = r.endInclusive.roundToInt().coerceAtLeast(r.start.roundToInt())) } },
            valueRange = 0f..23f, steps = 22,
        )
        Hint("Reminders skip once you reach your goal.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodPanel(faces: Boolean = false) {
    val ctx = LocalContext.current
    val habits = ctx.container.habits
    val scope = rememberCoroutineScope()
    val data by habits.mood.collectAsState(MoodData())
    var day by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    val loc = LocalConfiguration.current.locales[0]
    val current = data.days[day.toEpochDay()]

    Text(
        if (day == LocalDate.now()) "How are you today?" else "How was ${day.format(DateTimeFormatter.ofPattern("EEE d MMM", loc))}?",
        style = MaterialTheme.typography.titleSmall,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mood.entries.forEach { m ->
            FilterChip(current == m, { scope.launch { habits.setMood(day, if (current == m) null else m) } }, { Text("${m.emoji} ${m.label}") })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", loc)), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        IconButton({ month = month.plusMonths(1) }, enabled = month.isBefore(LocalDate.now().withDayOfMonth(1))) { Icon(Icons.Rounded.ChevronRight, "Next month") }
    }
    val p = P(WidgetStyle(moodFaces = faces), MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surface)
    p.MoodMonth(month, data, mondayFirst = true) { day = it }
    val counts = data.days.filterKeys { LocalDate.ofEpochDay(it).withDayOfMonth(1) == month }.values.groupingBy { it }.eachCount()
    if (counts.isNotEmpty()) {
        Text(counts.entries.sortedByDescending { it.value }.joinToString("   ") { "${it.key.emoji} ${it.value}" }, style = MaterialTheme.typography.bodyMedium)
    }
    Hint("Tap a day to change it.")
}

@Composable
fun TodoPanel() {
    val ctx = LocalContext.current
    val habits = ctx.container.habits
    val scope = rememberCoroutineScope()
    val data by habits.todo.collectAsState(TodoData())
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Todo?>(null) }

    fun add() {
        val t = text.trim()
        if (t.isEmpty()) return
        scope.launch { habits.updateTodo { it.add(t, System.currentTimeMillis()) } }
        text = ""
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(text, { text = it.take(80) }, Modifier.weight(1f), label = { Text("New item") }, singleLine = true)
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = ::add, enabled = text.isNotBlank()) { Icon(Icons.Rounded.Add, "Add") }
    }
    Column {
        data.items.forEach { item ->
            Row(Modifier.fillMaxWidth().clickable { scope.launch { habits.updateTodo { it.toggle(item.id) } } }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.done, null)
                Text(
                    item.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                IconButton({ editing = item }) { Icon(Icons.Rounded.Edit, "Edit") }
                IconButton({ scope.launch { habits.updateTodo { d -> d.copy(items = d.items.filterNot { it.id == item.id }) } } }) { Icon(Icons.Rounded.Delete, "Delete") }
            }
        }
    }
    if (data.items.any { it.done }) TextButton({ scope.launch { habits.updateTodo { it.clearDone() } } }) { Text("Clear done") }
    if (data.items.isEmpty()) Hint("Nothing here yet. Add your first item above.")

    editing?.let { item ->
        var value by remember(item.id) { mutableStateOf(item.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit item") },
            text = { OutlinedTextField(value, { value = it.take(80) }, singleLine = true) },
            confirmButton = {
                TextButton({
                    val v = value.trim()
                    if (v.isNotEmpty()) scope.launch { habits.updateTodo { d -> d.copy(items = d.items.map { if (it.id == item.id) it.copy(text = v) else it }) } }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } },
        )
    }
}
