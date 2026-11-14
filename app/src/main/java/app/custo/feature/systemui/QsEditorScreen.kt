package app.custo.feature.systemui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.custo.container
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.rememberActionRunner

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QsEditorScreen(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val runner = rememberActionRunner()
    val tiles = remember { mutableStateListOf<String>() }
    var loaded by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { c.tweaks.qsTiles() }
            .onSuccess { tiles.clear(); tiles.addAll(it); loaded = true }
            .onFailure { runner.message(it.message ?: "Shizuku is not ready") }
    }

    fun move(from: Int, to: Int) {
        if (to !in tiles.indices) return
        val t = tiles.removeAt(from); tiles.add(to, t); dirty = true
    }

    ScreenScaffold(
        title = "Quick Settings",
        onBack = onBack,
        actions = {
            IconButton(enabled = dirty, onClick = {
                runner.run("Quick Settings updated") { c.tweaks.setQsTiles(tiles.toList()); dirty = false }
            }) { Icon(Icons.Rounded.Save, "Save") }
        },
    ) {
        item { Hint("Top 4 show in the collapsed shade. Save when done.") }
        if (!loaded) return@ScreenScaffold
        item { SectionLabel("In your shade · ${tiles.size}") }
        itemsIndexed(tiles, key = { _, t -> t }) { i, spec ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (i < 4) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().animateItem(),
            ) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(qsLabel(spec), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { move(i, i - 1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                    IconButton(onClick = { move(i, i + 1) }, enabled = i < tiles.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                    IconButton(onClick = { tiles.removeAt(i); dirty = true }) { Icon(Icons.Rounded.Close, "Remove") }
                }
            }
        }
        val available = QS_TILES.keys.filter { it !in tiles }
        if (available.isNotEmpty()) {
            item { SectionLabel("Add tiles") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { spec ->
                        AssistChip(onClick = { tiles.add(spec); dirty = true }, label = { Text(qsLabel(spec)) }, leadingIcon = { Icon(Icons.Rounded.Add, null) })
                    }
                }
            }
            item { Hint("Some tiles only show if your phone supports them.") }
        }
    }
}
