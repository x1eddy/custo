package app.custo.feature.systemui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.custo.container
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.ui.components.InfoButton
import app.custo.ui.components.QsTileChip
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.rememberActionRunner
import app.custo.ui.components.skeletonItems

private const val QS_KEY = "sysui_qs_tiles"
private val QS_KEYS = setOf(Ids.of(Namespace.SECURE, QS_KEY))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QsEditorScreen(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val runner = rememberActionRunner()
    val tiles = remember { mutableStateListOf<String>() }
    var loaded by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    val current = rememberDeviceState("qs", QS_KEYS) { v ->
        v.v(Namespace.SECURE, QS_KEY)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }
    // Take the system's list (cached first, then refreshed) until the user starts editing.
    LaunchedEffect(current) {
        if (current != null && !dirty) { tiles.clear(); tiles.addAll(current); loaded = true }
    }

    fun move(from: Int, to: Int) {
        if (to !in tiles.indices) return
        val t = tiles.removeAt(from); tiles.add(to, t); dirty = true
    }

    ScreenScaffold(
        title = "Quick Settings",
        onBack = onBack,
        actions = {
            InfoButton("Quick Settings", "The first 4 tiles show in the collapsed shade. Some tiles only appear if your phone supports them. Tap save when done.")
            IconButton(enabled = dirty, onClick = {
                runner.run("Saved") { c.tweaks.setQsTiles(tiles.toList()); dirty = false }
            }) { Icon(Icons.Rounded.Save, "Save") }
        },
    ) {
        if (!loaded) { skeletonItems(1); return@ScreenScaffold }
        item { SectionLabel("In your shade · ${tiles.size}") }
        itemsIndexed(tiles, key = { _, t -> t }) { i, spec ->
            QsTileChip(
                label = qsLabel(spec),
                icon = qsIcon(spec),
                active = i < 4,
                modifier = Modifier.fillMaxWidth().animateItem(),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { move(i, i - 1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { move(i, i + 1) }, enabled = i < tiles.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = { tiles.removeAt(i); dirty = true }) { Icon(Icons.Rounded.Close, "Remove") }
                    }
                },
            )
        }
        val available = QS_TILES.keys.filter { it !in tiles }
        if (available.isNotEmpty()) {
            item { SectionLabel("Add") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { spec ->
                        QsTileChip(
                            label = qsLabel(spec),
                            icon = qsIcon(spec),
                            active = false,
                            modifier = Modifier.fillMaxWidth(0.48f).clip(RoundedCornerShape(20.dp)).clickable { tiles.add(spec); dirty = true },
                            trailing = { Icon(Icons.Rounded.Add, "Add", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                        )
                    }
                }
            }
        }
    }
}
