package app.custo.feature.themes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.rememberActionRunner

@Composable
fun WallScheduleScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val runner = rememberActionRunner()
    val saved by c.looks.saved.collectAsStateWithLifecycle(emptyList())
    val shizuku by c.shizuku.state.collectAsStateWithLifecycle()
    var s by remember { mutableStateOf<WallSchedule?>(null) }
    LaunchedEffect(Unit) {
        val loaded = WallScheduler.load(ctx)
        s = loaded.copy(day = loaded.day ?: BUILT_IN_LOOKS.first { it.name == "Ocean" }, night = loaded.night ?: BUILT_IN_LOOKS.first { it.name == "Northern" })
    }
    val cur = s
    val looks = BUILT_IN_LOOKS + saved

    ScreenScaffold(
        title = "Day & night",
        onBack = onBack,
        actions = {
            IconButton(enabled = cur != null, onClick = {
                runner.run(if (cur!!.on) "Schedule saved" else "Schedule off") { WallScheduler.save(ctx, cur); onBack() }
            }) { Icon(Icons.Rounded.Check, "Save") }
        },
    ) {
        if (cur == null) return@ScreenScaffold
        item {
            SectionCard {
                SwitchRow("Switch by time", "Change the wallpaper at the times below", cur.on) { s = cur.copy(on = it) }
                SwitchRow(
                    "Match system colors",
                    if (shizuku == ShizukuState.READY) "Also apply each look's colors" else "Needs Shizuku",
                    cur.colors, enabled = shizuku == ShizukuState.READY,
                ) { s = cur.copy(colors = it) }
            }
        }
        item {
            SlotCard("Day", Icons.Rounded.LightMode, cur.dayAt, cur.day, looks, { s = cur.copy(dayAt = it) }) { s = cur.copy(day = it) }
        }
        item {
            SlotCard("Night", Icons.Rounded.DarkMode, cur.nightAt, cur.night, looks, { s = cur.copy(nightAt = it) }) { s = cur.copy(night = it) }
        }
        item { Hint("Switches happen the next time your phone is awake after the set time. If Custo live is your wallpaper, it just changes what it shows.") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotCard(title: String, icon: ImageVector, at: Int, look: Look?, looks: List<Look>, onTime: (Int) -> Unit, onLook: (Look) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    SectionCard(title, icon) {
        FilledTonalButton(onClick = { picking = true }) { Icon(Icons.Rounded.Schedule, null); Text("  From ${WallLogic.formatMinute(at)}") }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(looks, key = { it.name + it.builtIn }) { l ->
                LookCard(l, onClick = { onLook(l) }, selected = look?.name == l.name && look.wall == l.wall)
            }
        }
    }
    if (picking) {
        val state = rememberTimePickerState(initialHour = at / 60, initialMinute = at % 60)
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { onTime(state.hour * 60 + state.minute); picking = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}
