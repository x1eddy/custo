package app.custo.feature.wallhaven

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.nav.Navigator
import app.custo.nav.Route
import kotlinx.coroutines.launch

/** Grid page shell shared by the packs and saved screens: back row, big title, 2 columns. */
@Composable
private fun PictureGridPage(title: String, nav: Navigator, actions: @Composable () -> Unit = {}, content: LazyGridScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.statusBarsPadding().padding(top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    actions()
                }
            }
            content()
        }
    }
}

/** Phone storage used by saved wallpapers and the picture cache, with a cache clear button. */
@Composable
fun WallStorageCard(modifier: Modifier = Modifier) {
    val repo = LocalContext.current.container.wallhaven
    val saved by repo.saved.collectAsStateWithLifecycle(emptyList())
    var cache by remember { mutableLongStateOf(-1L) }
    val used by produceState(-1L, saved.size) { value = repo.storageUsed() }
    LaunchedEffect(Unit) { cache = repo.cacheUsed() }
    val scope = rememberCoroutineScope()
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (used < 0) "…" else "${saved.size} saved · ${WallFormat.bytes(used)}", style = MaterialTheme.typography.titleSmall)
                if (cache >= 0) Text("Cache ${WallFormat.bytes(cache)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (cache > 0) TextButton(onClick = { scope.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.clearCache() }; cache = 0 } }) { Text("Clear cache") }
        }
    }
}

// ---------- Offline packs ----------

/** Curated packs: cover grid with saved count, live progress, download sheet with size estimates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinePacksScreen(nav: Navigator) {
    val repo = LocalContext.current.container.wallhaven
    val counts by repo.packCounts.collectAsStateWithLifecycle(emptyMap())
    val bytes by repo.packBytes.collectAsStateWithLifecycle(emptyMap())
    val progress by repo.packProgress.collectAsStateWithLifecycle()
    val saved by repo.saved.collectAsStateWithLifecycle(emptyList())
    var open by remember { mutableStateOf<WallPack?>(null) }
    LaunchedEffect(Unit) { repo.refreshPackStates() }
    val cellPx = (LocalWindowInfo.current.containerSize.width / 2).coerceAtLeast(360)

    PictureGridPage("Offline packs", nav) {
        item(key = "storage", span = { GridItemSpan(maxLineSpan) }) { WallStorageCard() }
        items(repo.packs, key = { it.id }) { pack ->
            val cover = saved.firstOrNull { it.packId == pack.id }?.let { repo.savedFile(it.id)?.path }
            val online by produceState<String?>(null, pack.id, cover) {
                if (cover == null) value = runCatching { repo.topFor(pack.query, 1).firstOrNull()?.gridThumb }.getOrNull()
            }
            val p = progress[pack.id]
            WallTile(cover ?: online, aspect = 3f / 4f, targetPx = cellPx, onClick = { open = pack }) {
                Box(Modifier.fillMaxSize().background(BottomScrim))
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text(pack.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    val n = counts[pack.id] ?: 0
                    if (p?.active == true) {
                        Text("${p.done} / ${p.total}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                    } else if (n > 0) {
                        Text("$n saved · ${WallFormat.bytes(bytes[pack.id] ?: 0)}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                    }
                }
                if ((counts[pack.id] ?: 0) > 0 && p?.active != true) {
                    Icon(Icons.Rounded.DownloadDone, "Downloaded", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(18.dp))
                }
            }
        }
        item(key = "credit", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Pictures come from wallhaven.cc (SFW only) and are shrunk to your screen to save space.",
                Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    open?.let { pack ->
        ModalBottomSheet(onDismissRequest = { open = null }) {
            PackSheet(pack, counts[pack.id] ?: 0, progress[pack.id], nav) { open = null }
        }
    }
}

@Composable
private fun PackSheet(pack: WallPack, have: Int, p: PackProgress?, nav: Navigator, close: () -> Unit) {
    val repo = LocalContext.current.container.wallhaven
    var size by remember { mutableIntStateOf(WallPacks.sizes.firstOrNull { it > have } ?: WallPacks.sizes.last()) }
    var wifiOnly by remember { mutableStateOf(true) }
    val (stored, download) = repo.estimate(PackPlanner.remaining(size, have))
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(pack.title, style = MaterialTheme.typography.headlineSmall)
        if (p?.active == true) {
            Text(
                if (p.state == PackProgress.State.QUEUED) (p.message ?: "Waiting for Wi-Fi / battery") else "Downloading ${p.done} of ${p.total}",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { repo.cancelPack(pack.id); close() }) { Text("Cancel download") }
        } else {
            p?.message?.takeIf { p.state == PackProgress.State.FAILED || p.state == PackProgress.State.DONE }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WallPacks.sizes.forEachIndexed { i, n ->
                    SegmentedButton(selected = size == n, onClick = { size = n }, shape = SegmentedButtonDefaults.itemShape(i, WallPacks.sizes.size)) { Text("$n") }
                }
            }
            val remaining = PackPlanner.remaining(size, have)
            Text(
                if (remaining == 0) "Already on your phone ($have)"
                else "≈ ${WallFormat.bytes(stored)} on your phone · ≈ ${WallFormat.bytes(download)} to download",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wi-Fi only", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(wifiOnly, { wifiOnly = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { repo.downloadPack(pack, size, wifiOnly); close() }, enabled = remaining > 0) {
                    Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(6.dp)); Text(if (have > 0) "Get more" else "Download")
                }
                OutlinedButton(onClick = { close(); nav.push(Route.WallhavenBrowse(pack.query)) }) {
                    Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(6.dp)); Text("Browse")
                }
            }
        }
        // The sheet's own scope ends when it closes, which would cancel the delete halfway.
        if (have > 0) TextButton(onClick = { repo.deletePackLater(pack.id); close() }) {
            Icon(Icons.Rounded.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete pack (keeps favorites)")
        }
    }
}

// ---------- Saved ----------

/** Wallpapers on the phone. No network needed. */
@Composable
fun SavedWallpapersScreen(nav: Navigator) {
    val repo = LocalContext.current.container.wallhaven
    val runner = app.custo.ui.components.rememberActionRunner()
    val saved by repo.saved.collectAsStateWithLifecycle(null)
    var favOnly by remember { mutableStateOf(false) }
    var packFilter by remember { mutableStateOf<String?>(null) }
    val cellPx = (LocalWindowInfo.current.containerSize.width / 2).coerceAtLeast(360)
    val all = saved.orEmpty()
    val packsPresent = all.mapNotNull { it.packId }.distinct().mapNotNull { WallPacks.byId(it) }
    val shown = all.filter { (!favOnly || it.favorite) && (packFilter == null || it.packId == packFilter) }

    PictureGridPage("My wallpapers", nav, actions = {
        IconButton(onClick = { nav.push(Route.OfflinePacks) }) { Icon(Icons.Rounded.Inventory2, "Offline packs") }
    }) {
        item(key = "storage", span = { GridItemSpan(maxLineSpan) }) { WallStorageCard() }
        item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(!favOnly && packFilter == null, { favOnly = false; packFilter = null }, { Text("All") }) }
                item {
                    FilterChip(favOnly, { favOnly = !favOnly }, { Text("Favorites") },
                        leadingIcon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp)) })
                }
                packsPresent.forEach { p ->
                    item(key = p.id) { FilterChip(packFilter == p.id, { packFilter = if (packFilter == p.id) null else p.id }, { Text(p.title) }) }
                }
            }
        }
        items(shown, key = { it.id }) { w ->
            var menu by remember { mutableStateOf(false) }
            WallTile(repo.savedFile(w.id)?.path, targetPx = cellPx, onClick = { nav.push(Route.WallpaperDetail(w.id)) }, onLongClick = { menu = true }) {
                NotThisMenu(menu, { menu = false }) { runner.run(if (w.packId != null) "Removed · getting another" else "Removed") { repo.notThis(w.id) } }
                if (w.favorite) Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(18.dp))
            }
        }
        if (saved != null && shown.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Bookmark, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Nothing saved yet", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { nav.push(Route.WallhavenBrowse()) }) { Text("Find wallpapers") }
            }
        }
    }
}

// ---------- Entry card ----------

/**
 * Big picture card that opens the wallhaven browser, with shortcuts to packs and saved ones.
 * Shows saved wallpapers, or wallhaven's all-time favourites when nothing is saved yet.
 */
@Composable
fun WallpapersEntryCard(nav: Navigator, modifier: Modifier = Modifier) {
    val repo = LocalContext.current.container.wallhaven
    val saved by repo.saved.collectAsStateWithLifecycle(emptyList())
    val local = saved.take(3).mapNotNull { repo.savedFile(it.id)?.path }
    val online by produceState(emptyList<String>(), local.size >= 3) {
        if (local.size < 3) value = runCatching { repo.topFor("", 3).map { it.gridThumb } }.getOrDefault(emptyList())
    }
    val pics = (local + online).take(3)
    Surface(
        modifier.fillMaxWidth().height(210.dp).clip(MaterialTheme.shapes.large).clickable { nav.push(Route.WallhavenBrowse()) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { i ->
                    val src = pics.getOrNull(i)
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        if (src != null) WallTile(src, Modifier.fillMaxSize(), aspect = null, targetPx = 360, corner = 0)
                        else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))))
                    }
                }
            }
            Box(Modifier.fillMaxSize().background(BottomScrim))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text("Wallpapers", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Packs", Icons.Rounded.Inventory2) { nav.push(Route.OfflinePacks) }
                    PillButton("Saved", Icons.Rounded.Bookmark) { nav.push(Route.SavedWallpapers) }
                }
            }
        }
    }
}

@Composable
private fun PillButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.22f)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------- Wallpapers tab header ----------

/**
 * Three picture cards above the Wallpapers grid: offline packs, the user's saved wallpapers and
 * "Make your own" (the wallpaper maker, live, video, day & night, collage and looks).
 */
@Composable
fun WallLibraryCards(nav: Navigator) {
    val repo = LocalContext.current.container.wallhaven
    val saved by repo.saved.collectAsStateWithLifecycle(emptyList())
    val packCover = saved.firstOrNull { it.packId != null }?.let { repo.savedFile(it.id)?.path }
    val mine = (saved.firstOrNull { it.packId == null } ?: saved.getOrNull(1))?.let { repo.savedFile(it.id)?.path }
    val needOnline = packCover == null || mine == null
    val online by produceState(emptyList<String>(), needOnline) {
        if (needOnline) value = runCatching { repo.topFor("", 3).map { it.gridThumb } }.getOrDefault(emptyList())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LibraryCard("Offline packs", packCover ?: online.getOrNull(0), Modifier.weight(1f)) { nav.push(Route.OfflinePacks) }
        LibraryCard("My wallpapers", mine ?: online.getOrNull(1), Modifier.weight(1f)) { nav.push(Route.SavedWallpapers) }
        Box(Modifier.weight(1f).height(150.dp).clip(MaterialTheme.shapes.large).clickable { nav.push(Route.WallpaperMaker) }) {
            app.custo.feature.themes.WallPreview(app.custo.feature.themes.BUILT_IN_LOOKS.first().wall, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(BottomScrim))
            Text("Make your own", Modifier.align(Alignment.BottomStart).padding(10.dp), style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

@Composable
private fun LibraryCard(title: String, src: String?, modifier: Modifier, onClick: () -> Unit) {
    WallTile(src, modifier.height(150.dp), aspect = null, targetPx = 360, onClick = onClick) {
        Box(Modifier.fillMaxSize().background(BottomScrim))
        Text(title, Modifier.align(Alignment.BottomStart).padding(10.dp), style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 2)
    }
}
