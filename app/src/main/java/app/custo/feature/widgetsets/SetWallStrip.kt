package app.custo.feature.widgetsets

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.feature.wallhaven.Shimmer
import app.custo.feature.wallhaven.WallFormat
import app.custo.feature.wallhaven.WallTile
import app.custo.feature.wallhaven.WhWallpaper
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.ui.components.PreviewRender
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.withContext

/** The set's real-name search as a small tag, e.g. "wallhaven · One Piece". Set titles stay original. */
@Composable
fun WallhavenTag(query: String, modifier: Modifier = Modifier) {
    Text(
        "wallhaven · $query",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * "Wallpapers · <query>" on a set's detail screen: the drawn wallpaper, saved pictures for the
 * search and wallhaven's top results. Tapping one makes it the set's background (saved offline,
 * colors matched); "More" opens the full wallhaven search.
 */
@Composable
fun SetWallStrip(theme: WidgetTheme, query: String, nav: Navigator) {
    val c = LocalContext.current.container
    val runner = rememberActionRunner()
    val base = remember(theme.id) { WidgetThemes.base(theme.id) ?: theme }
    val savedAll by c.wallhaven.saved.collectAsStateWithLifecycle(emptyList())
    val saved = savedAll.filter { it.query.equals(query, ignoreCase = true) || it.id == theme.chosenWall?.id }
    val online by produceState<List<WhWallpaper>?>(null, query) {
        // Pictures appear as their tags are checked (no memes, pin-ups or off-topic ones).
        runCatching { c.wallhaven.topChecked(query, 12).collect { value = it } }
        if (value == null) value = emptyList()
    }
    var busy by remember { mutableStateOf<String?>(null) }
    val chosen = theme.chosenWall?.id

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wallpapers", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            WallhavenTag(query)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { nav.push(Route.WallhavenBrowse(query)) }) {
                Text("More"); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "drawn") {
                StripCell(selected = chosen == null, busy = false, onClick = {
                    if (chosen != null) runner.run { c.setBackgrounds.clear(theme.id) }
                }) {
                    DrawnThumb(base)
                    Icon(Icons.Rounded.Brush, "Drawn wallpaper", tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(16.dp))
                }
            }
            items(saved, key = { "s" + it.id }) { w ->
                StripCell(selected = chosen == w.id, busy = busy == w.id, onClick = {
                    if (chosen == w.id || busy != null) return@StripCell
                    busy = w.id
                    runner.run { try { c.setBackgrounds.chooseSaved(base, w) } finally { busy = null } }
                }) { WallTile(c.wallhaven.savedFile(w.id)?.path, Modifier.fillMaxSize(), aspect = null, targetPx = 320, corner = 0) }
            }
            val list = online
            if (list == null) {
                items(3, key = { "sh$it" }) { Shimmer(Modifier.width(88.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp))) }
            } else {
                val savedIds = saved.mapTo(HashSet()) { it.id }
                items(list.filter { it.id !in savedIds }, key = { "o" + it.id }) { w ->
                    StripCell(selected = chosen == w.id, busy = busy == w.id, onClick = {
                        if (busy != null) return@StripCell
                        busy = w.id
                        runner.run { try { c.setBackgrounds.choose(base, w) } finally { busy = null } }
                    }) {
                        WallTile(
                            w.gridThumb, Modifier.fillMaxSize(), aspect = null, targetPx = 320, corner = 0,
                            placeholder = w.colors.firstOrNull()?.let { WallFormat.color(it) }?.let { Color(it).copy(alpha = 0.55f) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StripCell(selected: Boolean, busy: Boolean, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.width(88.dp).aspectRatio(9f / 16f).clip(shape).clickable(onClick = onClick)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
    ) {
        content()
        if (selected) Icon(
            Icons.Rounded.CheckCircle, "Chosen", tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(CircleShape).background(Color.White),
        )
        if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
        }
    }
}

/** The set's own drawn wallpaper, small. */
@Composable
private fun DrawnThumb(theme: WidgetTheme) {
    val bmp by produceState<Bitmap?>(null, theme.id) {
        value = withContext(PreviewRender) {
            createBitmap(176, 312).also { theme.wallpaper.paint(Canvas(it), 176f, 312f) }
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
