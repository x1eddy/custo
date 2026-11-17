package app.custo.feature.wallhaven

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import app.custo.container
import app.custo.feature.themes.WallTarget
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Quick picks above the grid. Topic chips are plain search terms. */
enum class BrowseChip(
    val label: String,
    val query: String?,
    val sorting: WhSorting,
    val general: Boolean = true,
    val anime: Boolean = true,
    val range: WhTopRange = WhTopRange.YEAR,
) {
    TOP("Top", null, WhSorting.TOPLIST, range = WhTopRange.MONTH),
    NEW("New", null, WhSorting.DATE_ADDED),
    RANDOM("Random", null, WhSorting.RANDOM),
    ANIME("Anime", null, WhSorting.TOPLIST, general = false, range = WhTopRange.MONTH),
    GAMES("Games", "video games", WhSorting.FAVORITES),
    DARK("Dark", "dark", WhSorting.FAVORITES),
    NATURE("Nature", "nature", WhSorting.FAVORITES, anime = false),
    SPACE("Space", "space", WhSorting.FAVORITES),
    MINIMAL("Minimal", "minimalism", WhSorting.FAVORITES),
}

// ---------- Browse ----------

/**
 * wallhaven browser: search, quick chips, portrait toggle and an endless 2-column picture grid.
 * Opening it with [initialQuery] searches for that term (e.g. from a widget set). As the
 * Wallpapers tab ([asTab]) it has no back arrow and shows picture cards for offline packs, saved
 * wallpapers and the wallpaper maker above the grid.
 */
@Composable
fun WallhavenBrowseScreen(nav: Navigator, initialQuery: String? = null, asTab: Boolean = false) {
    val repo = LocalContext.current.container.wallhaven
    val pager = if (initialQuery != null) repo.queryPager else repo.browsePager
    val state by pager.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(if (initialQuery != null) initialQuery else pager.searchText) }
    var chip by remember { mutableStateOf(if (initialQuery != null) null else pager.chip) }
    var portrait by remember { mutableStateOf(pager.portrait) }
    val focus = LocalFocusManager.current
    val grid = rememberLazyGridState(pager.firstVisible, pager.firstVisibleOffset)
    val scope = rememberCoroutineScope()

    fun run() {
        pager.searchText = text; pager.chip = chip; pager.portrait = portrait
        val q = text.trim().ifEmpty { null } ?: chip?.query
        val c = chip
        val base = repo.defaultFilters(portrait, c?.sorting ?: if (q != null) WhSorting.RELEVANCE else WhSorting.TOPLIST)
        val before = pager.state.value
        pager.reset(q, base.copy(general = c?.general ?: true, anime = c?.anime ?: true, topRange = c?.range ?: WhTopRange.MONTH))
        if (pager.state.value !== before) scope.launch { grid.scrollToItem(0) }
    }
    LaunchedEffect(Unit) { run() }

    DisposableEffect(grid) {
        onDispose { pager.firstVisible = grid.firstVisibleItemIndex; pager.firstVisibleOffset = grid.firstVisibleItemScrollOffset }
    }
    // Next page when the grid gets within a few rows of the end (re-armed after every page).
    LaunchedEffect(grid, state.page, state.items.size) {
        snapshotFlow { grid.layoutInfo.let { (it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - 8 } }
            .distinctUntilChanged().filter { it }.collect { pager.loadMore() }
    }

    val cellPx = (LocalWindowInfo.current.containerSize.width / 2).coerceAtLeast(360)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = if (asTab) 120.dp else 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.statusBarsPadding().padding(top = if (asTab) 20.dp else 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (asTab) {
                        Text("Wallpapers", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                        WallLibraryCards(nav)
                    } else Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                        Text(initialQuery ?: "Wallpapers", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = { nav.push(Route.OfflinePacks) }) { Icon(Icons.Rounded.Inventory2, "Offline packs") }
                        IconButton(onClick = { nav.push(Route.SavedWallpapers) }) { Icon(Icons.Rounded.Bookmark, "My wallpapers") }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(100) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search wallhaven") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (text.isNotEmpty()) IconButton(onClick = { text = ""; if (chip == null) chip = BrowseChip.TOP; run() }) { Icon(Icons.Rounded.Close, "Clear") }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { chip = null; focus.clearFocus(); run() }),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(portrait, { portrait = !portrait; run() }, { Text("Portrait") },
                                leadingIcon = { Icon(Icons.Rounded.StayCurrentPortrait, null, Modifier.size(18.dp)) })
                        }
                        items(BrowseChip.entries) { c ->
                            FilterChip(chip == c, { chip = c; text = ""; run() }, { Text(c.label) })
                        }
                    }
                }
            }
            items(state.items, key = { it.id }) { w ->
                WallTile(
                    w.gridThumb, targetPx = cellPx,
                    placeholder = w.colors.firstOrNull()?.let { WallFormat.color(it) }?.let { Color(it).copy(alpha = 0.55f) },
                    onClick = { nav.push(Route.WallpaperDetail(w.id)) },
                )
            }
            if (state.loading) {
                items(if (state.items.isEmpty()) 8 else 2, key = { "sh$it" }) { Shimmer(Modifier.fillMaxWidth().height(300.dp).clip(MaterialTheme.shapes.large)) }
            }
            state.error?.let { err ->
                item(key = "err", span = { GridItemSpan(maxLineSpan) }) { ErrorPanel(err) { pager.retry() } }
            }
            if (!state.loading && state.error == null && state.items.isEmpty() && state.filters != null) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Nothing found", Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.items.isNotEmpty()) item(key = "credit", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Wallpapers from wallhaven.cc · SFW only",
                    Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ErrorPanel(error: WhError, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (error == WhError.OFFLINE) Icons.Rounded.CloudOff else Icons.Rounded.Refresh, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(error.message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        FilledTonalButton(onClick = onRetry) { Text("Try again") }
    }
}

// ---------- Detail ----------

/** What the detail screen shows: a live result, a saved wallpaper, or both. */
private data class DetailData(val item: WhWallpaper?, val saved: SavedWall?)

/**
 * Full-screen picture with Save offline, Favorite, Set as Home/Lock/Both and a link to the
 * wallpaper's wallhaven page. Works offline for saved wallpapers.
 */
@Composable
fun WallpaperDetailScreen(id: String, nav: Navigator) {
    val ctx = LocalContext.current
    val repo = ctx.container.wallhaven
    val runner = rememberActionRunner()
    val scope = rememberCoroutineScope()
    val savedList by repo.saved.collectAsStateWithLifecycle(null)
    val saved = savedList?.firstOrNull { it.id == id }
    val item by produceState(repo.cached(id), id) { if (value == null) value = repo.item(id) }
    var busy by remember { mutableStateOf<String?>(null) }
    var pickTarget by remember { mutableStateOf(false) }
    val data = DetailData(item, saved)
    val localFile = saved?.let { repo.savedFile(it.id)?.path }

    val screenPx = LocalWindowInfo.current.containerSize.height.coerceAtLeast(1280)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Small thumbnail first (usually already in memory from the grid), then the sharp image.
        val thumb by rememberWallImage(data.item?.gridThumb, 480)
        val full by rememberWallImage(localFile ?: data.item?.path, screenPx)
        (full ?: thumb)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (full == null) {
            if (savedList != null && localFile == null && item == null) {
                Text("Not available offline", Modifier.align(Alignment.Center), color = Color.White)
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), color = Color.White.copy(alpha = 0.8f), strokeWidth = 3.dp)
            }
        }

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { nav.pop() }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f), contentColor = Color.White)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Spacer(Modifier.weight(1f))
            val page = data.item?.pageUrl ?: data.saved?.sourceUrl
            if (!page.isNullOrBlank()) TextButton(onClick = {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, page.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) {
                Text("wallhaven", color = Color.White); Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, "View on wallhaven", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(BottomScrim).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val res = data.item?.resolution?.ifBlank { null } ?: data.saved?.resolution?.ifBlank { null }
                res?.let { InfoChip(it) }
                data.item?.fileSize?.takeIf { it > 0 }?.let { InfoChip(WallFormat.bytes(it)) }
                data.saved?.let { InfoChip("Saved · " + WallFormat.bytes(it.bytes)) }
                (data.item?.colors ?: data.saved?.colors).orEmpty().take(4).mapNotNull { WallFormat.color(it) }.forEach { col ->
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color(col)).align(Alignment.CenterVertically))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { pickTarget = true },
                    enabled = busy == null && (localFile != null || data.item != null),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Rounded.Wallpaper, null); Spacer(Modifier.width(8.dp)); Text(busy ?: "Set wallpaper", maxLines = 1)
                }
                val fav = saved?.favorite == true
                FilledTonalIconButton(
                    onClick = {
                        val it0 = data.item
                        busy = if (it0 != null && saved == null) "Saving…" else null
                        runner.run(if (!fav) "Added to favorites" else null) {
                            try {
                                if (it0 != null) repo.toggleFavorite(it0) else repo.setFavorite(id, !fav)
                            } finally { busy = null }
                        }
                    },
                    enabled = busy == null && (data.item != null || saved != null),
                    modifier = Modifier.size(52.dp),
                ) { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (fav) "Unfavorite" else "Favorite") }
                if (saved != null) {
                    FilledTonalIconButton(onClick = { scope.launch { repo.delete(id); nav.pop() } }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Delete, "Delete from phone")
                    }
                } else {
                    FilledTonalIconButton(
                        onClick = {
                            val it0 = data.item ?: return@FilledTonalIconButton
                            busy = "Saving…"
                            runner.run("Saved offline") { try { repo.save(it0) } finally { busy = null } }
                        },
                        enabled = busy == null && data.item != null,
                        modifier = Modifier.size(52.dp),
                    ) { Icon(Icons.Rounded.Download, "Save offline") }
                }
            }
        }
    }

    if (pickTarget) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickTarget = false },
            title = { Text("Set as") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WallTarget.entries.forEach { t ->
                        FilledTonalButton(onClick = {
                            pickTarget = false
                            busy = "Setting…"
                            runner.run("Wallpaper set") {
                                try {
                                    val it0 = data.item
                                    if (localFile != null || it0 == null) repo.applySaved(id, t) else repo.apply(it0, t)
                                } finally { busy = null }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (t == WallTarget.BOTH) "Home & lock screen" else "${t.label} screen") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickTarget = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InfoChip(text: String) {
    AssistChip(
        onClick = {}, label = { Text(text, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = 0.45f), labelColor = Color.White),
        border = null,
    )
}
