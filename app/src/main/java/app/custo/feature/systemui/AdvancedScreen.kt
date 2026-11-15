package app.custo.feature.systemui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.ui.components.InfoButton
import app.custo.ui.components.skeletonItems
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.custo.container
import app.custo.nav.Navigator
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner

private val NAMESPACES = listOf(Namespace.SYSTEM, Namespace.SECURE, Namespace.GLOBAL)

/** (key, current value) being edited; key is blank for a new setting. */
private data class Editing(val key: String, val value: String, val isNew: Boolean)

@Composable
fun AdvancedScreen(nav: Navigator) {
    val c = LocalContext.current.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()
    var nsIndex by rememberSaveable { mutableIntStateOf(1) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var rescanning by remember { mutableStateOf(false) }
    val ns = NAMESPACES[nsIndex]
    // Keeps the cached lists fresh (same batched read as every other screen).
    rememberDeviceState("advanced", emptySet()) { }
    val lists by c.deviceState.lists.collectAsStateWithLifecycle()
    fun saved(msg: String, block: suspend () -> Unit) = runner.run(msg) { block(); c.deviceState.stateChanged() }

    ScreenScaffold(
        title = "Advanced",
        onBack = { nav.pop() },
        actions = { IconButton(onClick = { editing = Editing("", "", true) }, enabled = ready) { Icon(Icons.Rounded.Add, "Add setting") } },
    ) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        item {
            SectionCard("Phone scan", Icons.Rounded.PhoneAndroid, badge = {
                InfoButton("Phone scan", "Custo remembers what your phone supports so screens open instantly. Rescan after a system update or if something looks wrong.")
            }) {
                FilledTonalButton(enabled = !rescanning, onClick = {
                    rescanning = true
                    runner.run("Phone rescanned") { try { c.deviceState.rescan() } finally { rescanning = false } }
                }) {
                    Icon(Icons.Rounded.Refresh, null)
                    Text(if (rescanning) "  Scanning…" else "  Rescan phone")
                }
            }
        }
        item {
            SectionCard("Raw settings", Icons.Rounded.Warning, badge = {
                InfoButton("Raw settings", "Wrong values can break things. Undo all reverts your edits.")
            })
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                NAMESPACES.forEachIndexed { i, n ->
                    SegmentedButton(selected = nsIndex == i, onClick = { nsIndex = i }, shape = SegmentedButtonDefaults.itemShape(i, NAMESPACES.size)) { Text(n.id) }
                }
            }
        }
        item {
            OutlinedTextField(
                query, { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search keys or values") },
            )
        }
        val list = lists[ns.id]
        if (list == null) { skeletonItems(1); return@ScreenScaffold }
        val q = query.trim()
        val shown = if (q.isEmpty()) list else list.filter { it.first.contains(q, true) || it.second.contains(q, true) }
        item { Hint("${shown.size} of ${list.size}") }
        items(shown, key = { it.first }) { (key, value) ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().clickable { editing = Editing(key, value, false) },
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(key, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(value.ifEmpty { "(empty)" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    editing?.let { e ->
        var key by remember(e) { mutableStateOf(e.key) }
        var value by remember(e) { mutableStateOf(e.value) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (e.isNew) "New ${ns.id} setting" else e.key) },
            text = {
                Column {
                    if (e.isNew) OutlinedTextField(key, { key = it.trim() }, label = { Text("Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value, { value = it }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = key.isNotBlank(), onClick = {
                    editing = null
                    saved("Saved") { c.tweaks.write(ns, key, value) }
                }) { Text("Save") }
            },
            dismissButton = {
                if (!e.isNew) TextButton(onClick = {
                    editing = null
                    saved("Deleted") { c.tweaks.write(ns, e.key, null) }
                }) { Text("Delete") }
                else TextButton(onClick = { editing = null }) { Text("Cancel") }
            },
        )
    }
}
