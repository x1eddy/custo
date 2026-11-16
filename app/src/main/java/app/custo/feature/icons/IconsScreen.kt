package app.custo.feature.icons

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.feature.profiles.AppList
import app.custo.feature.profiles.LaunchableApp
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.ui.components.Hint
import app.custo.ui.components.InfoButton
import app.custo.ui.components.HubTile
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.rememberActionRunner
import java.io.File

@Composable
fun IconsHubScreen(nav: Navigator) {
    ScreenScaffold(title = "Icons", onBack = { nav.pop() }, actions = {
        InfoButton(
            "How it works",
            "Android doesn't let apps swap another app's icon. Custo adds a home-screen shortcut with your new icon and name instead. It opens the app just like the original.\n\n" +
                "Your launcher asks before each shortcut is added. Afterwards you can remove the original icons from the home screen (they stay in the app drawer).",
        )
    }) {
        item {
            // Picture first: a strip of themes on the user's own apps leads to the gallery.
            val ctx = LocalContext.current
            val apps by produceState<List<LaunchableApp>>(emptyList()) { value = AppList.load(ctx) }
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { nav.push(Route.IconThemes) }.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("glass", "candy", "luxe").forEach { id ->
                        val t = ICON_THEMES.first { it.id == id }
                        MockHome(apps, { t.style }, Modifier.weight(1f), cols = 3, rows = 3, renderPx = 96, labels = false)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Icon themes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${ICON_THEMES.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HubTile("One icon", Icons.Rounded.SwapHoriz, Modifier.weight(1f)) { nav.push(Route.IconChanger()) }
                HubTile("Icon maker", Icons.Rounded.AutoFixHigh, Modifier.weight(1f)) { nav.push(Route.IconMaker()) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HubTile("Icon packs", Icons.Rounded.Inventory2, Modifier.weight(1f)) { nav.push(Route.IconPacks) }
                HubTile("Imported", Icons.Rounded.Collections, Modifier.weight(1f)) { nav.push(Route.ImportedIcons) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HubTile("My shortcuts", Icons.Rounded.History, Modifier.weight(1f)) { nav.push(Route.IconShortcuts) }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

// --- My shortcuts ---

@Composable
fun IconShortcutsScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val runner = rememberActionRunner()
    val queue = rememberPinQueue()
    val history by c.icons.history.collectAsStateWithLifecycle(emptyList())
    var pinned by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(history, queue.done) { pinned = Shortcuts.pinnedIds(ctx) }

    PinQueuePanel(queue)
    ScreenScaffold(title = "My shortcuts", onBack = { nav.pop() }, actions = {
        InfoButton(
            "My shortcuts",
            "Icons you add from Custo show up here so you can change them later. Edit changes a shortcut that's already on your home screen without asking again. " +
                "Forget greys it out; long-press it on the home screen to remove it.",
        )
    }) {
        if (history.isEmpty()) item { EmptyState(Icons.Rounded.History, "Nothing yet") }
        items(history.sortedByDescending { it.createdAt }, key = { it.id }) { s ->
            val icon by produceState<Bitmap?>(null, s.id, s.createdAt) { value = c.icons.loadShortcutIcon(s.id) }
            val onHome = s.id in pinned
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBitmap(icon, Modifier.size(48.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (onHome) "On home screen" else "Not on home screen") + " · " + sourceLabel(s.source),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { nav.push(Route.IconChanger(s.pkg, s.id)) }) { Icon(Icons.Rounded.Edit, null); Text("  Edit") }
                    if (!onHome) TextButton(onClick = {
                        queue.start(listOf(PinJob(s.pkg, s.label, s.source, s.style, s.style?.launcherShape ?: false, s.id) { _ ->
                            c.icons.loadShortcutIcon(s.id) ?: error("Icon file is missing. Use Edit instead")
                        }))
                    }) { Icon(Icons.Rounded.Refresh, null); Text("  Add again") }
                    TextButton(onClick = {
                        runner.run("Removed from the list") { Shortcuts.disable(ctx, s.id); c.icons.forget(s.id) }
                    }) { Icon(Icons.Rounded.Delete, null); Text("  Forget") }
                }
            }
        }
    }
}

private fun sourceLabel(s: String) = when {
    s.startsWith("pack:") -> "icon pack"
    s == "image" -> "picture"
    else -> "Custo style"
}

// --- Imported icons ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportedIconsScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val runner = rememberActionRunner()
    val queue = rememberPinQueue()
    val files by c.icons.imported.collectAsStateWithLifecycle()
    var chosen by remember { mutableStateOf<File?>(null) }
    var pickApp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { c.icons.refreshImported() }

    val images = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) runner.run { val n = c.icons.importImages(uris).size; runner.message("Added $n pictures") }
    }
    val zip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runner.run { val n = c.icons.importZip(uri); runner.message(if (n == 0) "No pictures in that ZIP" else "Added $n pictures") }
    }

    PinQueuePanel(queue)
    chosen?.let { f ->
        if (pickApp) AppPickerDialog("Use for which app?", multi = false, onDismiss = { pickApp = false }) { apps ->
            val app = apps.first()
            pickApp = false; chosen = null
            queue.start(listOf(imageJob(f, app)))
        } else AlertDialog(
            onDismissRequest = { chosen = null },
            icon = { ImportedThumb(f, Modifier.size(72.dp)) },
            title = { Text(f.nameWithoutExtension) },
            confirmButton = { TextButton(onClick = { pickApp = true }) { Text("Use for an app") } },
            dismissButton = { TextButton(onClick = { runner.run { c.icons.deleteImported(f) }; chosen = null }) { Text("Delete") } },
        )
    }

    ScreenScaffold(title = "Imported icons", onBack = { nav.pop() }, actions = {
        InfoButton("Imported icons", "Pictures are copied into Custo, so you can delete the originals. Square PNGs with a transparent background look best.")
    }) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { images.launch(arrayOf("image/*")) }) { Icon(Icons.Rounded.AddPhotoAlternate, null); Text("  Pictures") }
                OutlinedButton(onClick = { zip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) {
                    Icon(Icons.Rounded.FolderZip, null); Text("  ZIP of PNGs")
                }
            }
        }
        if (files.isEmpty()) item { EmptyState(Icons.Rounded.Collections, "No pictures yet") }
        else item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 5) {
                files.forEach { f ->
                    ImportedThumb(f, Modifier.weight(1f).aspectRatio(1f).clip(MaterialTheme.shapes.medium).clickable { chosen = f })
                }
                repeat((5 - files.size % 5) % 5) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun ImportedThumb(f: File, modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val b by produceState<Bitmap?>(null, f.path, f.lastModified()) { value = c.icons.loadImported(f) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        b?.let { Image(it.asImageBitmap(), f.nameWithoutExtension, Modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp)) }
    }
}

fun imageJob(f: File, app: LaunchableApp, label: String = app.label, editId: String? = null) =
    PinJob(app.pkg, label, "image", null, adaptive = false, editId = editId) { ctx ->
        ctx.container.icons.loadImported(f) ?: error("That picture is gone")
    }

// --- Icon packs ---

@Composable
fun IconPacksScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val packs by produceState<List<IconPack>?>(null) { value = IconPacks.installed(ctx) }
    ScreenScaffold(title = "Icon packs", onBack = { nav.pop() }, actions = {
        InfoButton("Icon packs", "Any icon pack made for Nova, Lawnchair or ADW shows up here. Packs work offline: Custo reads the pack's icon list and draws nothing new.")
    }) {
        when {
            packs == null -> item { Hint("Looking for icon packs…") }
            packs!!.isEmpty() -> item {
                EmptyState(Icons.Rounded.Inventory2, "No icon packs found")
            }
            else -> items(packs!!, key = { it.pkg }) { p ->
                SectionCard(onClick = { nav.push(Route.IconPack(p.pkg)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (p.icon != null) Image(p.icon.asImageBitmap(), null, Modifier.size(44.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(p.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private class PackMatch(val app: LaunchableApp, val icon: Bitmap)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPackScreen(packPkg: String, nav: Navigator) {
    val ctx = LocalContext.current
    val queue = rememberPinQueue()
    val label = remember(packPkg) {
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(packPkg, 0)).toString() }.getOrDefault("Icon pack")
    }
    val matches by produceState<List<PackMatch>?>(null, packPkg) {
        value = AppList.load(ctx).mapNotNull { app -> IconPacks.iconFor(ctx, packPkg, app.pkg, 192)?.let { PackMatch(app, it) } }
    }
    var bulk by remember { mutableStateOf(false) }
    var single by remember { mutableStateOf<PackMatch?>(null) }

    fun job(app: LaunchableApp) = PinJob(app.pkg, app.label, "pack:$packPkg", null, adaptive = false) { c ->
        IconPacks.iconFor(c, packPkg, app.pkg) ?: error("The pack has no icon for ${app.label}")
    }

    PinQueuePanel(queue)
    if (bulk) AppPickerDialog("Apply $label to…", multi = true, only = matches?.map { it.app.pkg }?.toSet(), onDismiss = { bulk = false }) { apps ->
        bulk = false
        queue.start(apps.map(::job))
    }
    single?.let { m ->
        AlertDialog(
            onDismissRequest = { single = null },
            icon = { Image(m.icon.asImageBitmap(), null, Modifier.size(72.dp)) },
            title = { Text(m.app.label) },
            text = { Text("Add this icon to your home screen?") },
            confirmButton = { TextButton(onClick = { queue.start(listOf(job(m.app))); single = null }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { nav.push(Route.IconChanger(m.app.pkg)); single = null }) { Text("More options") } },
        )
    }

    ScreenScaffold(title = label, onBack = { nav.pop() }) {
        val list = matches
        if (list == null) item { Hint("Reading the pack…") }
        else {
            item {
                SectionCard("Whole pack", Icons.Rounded.Inventory2, badge = { Text("${list.size} apps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }) {
                    FilledTonalButton(onClick = { bulk = true }, enabled = list.isNotEmpty()) { Text("Pick apps") }
                }
            }
            item { SectionLabel("Your apps") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 4) {
                    list.forEach { m ->
                        Column(
                            Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { single = m }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(m.icon.asImageBitmap(), m.app.label, Modifier.fillMaxWidth().aspectRatio(1f))
                            Text(m.app.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat((4 - list.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Shared by the changer: is any icon available for this app in this pack? */
@Composable
internal fun PackIconRow(appPkg: String, selected: String?, onPick: (packPkg: String, Bitmap) -> Unit) {
    val ctx = LocalContext.current
    val found by produceState<List<Pair<IconPack, Bitmap>>?>(null, appPkg) {
        value = IconPacks.installed(ctx).mapNotNull { p -> IconPacks.iconFor(ctx, p.pkg, appPkg, 192)?.let { p to it } }
    }
    when {
        found == null -> Hint("Checking your icon packs…")
        found!!.isEmpty() -> Hint("No pack icon for this app")
        else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            found!!.forEach { (p, b) ->
                Column(
                    Modifier.width(76.dp).clip(MaterialTheme.shapes.medium)
                        .background(if (selected == p.pkg) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onPick(p.pkg, b) }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(b.asImageBitmap(), null, Modifier.size(52.dp))
                    Text(p.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Big icon + one short line, for empty lists. */
@Composable
internal fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        app.custo.ui.components.IconBadge(icon, size = 72, container = MaterialTheme.colorScheme.surfaceContainerHigh, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
