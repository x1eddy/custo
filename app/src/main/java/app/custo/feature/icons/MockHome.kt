package app.custo.feature.icons

import android.app.WallpaperManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.custo.feature.profiles.LaunchableApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Last known wallpaper colors, kept for the process so previews don't flash. */
@Volatile private var lastWallColors: List<Int>? = null

/**
 * The user's wallpaper as a gradient of its colors. Reading the wallpaper picture itself needs a
 * storage permission, so the preview uses the colors Android hands out freely.
 */
@Composable
fun rememberWallpaperBrush(): Brush {
    val ctx = LocalContext.current
    val colors by produceState(lastWallColors) { value = wallpaperColors(ctx).also { lastWallColors = it } }
    val list = colors.orEmpty()
    return if (list.isEmpty()) Brush.verticalGradient(listOf(Color(0xFF2A2F45), Color(0xFF12141C)))
    else Brush.linearGradient(list.map { Color(it) } + Color(0xFF101014))
}

private suspend fun wallpaperColors(ctx: Context): List<Int> = withContext(Dispatchers.IO) {
    runCatching {
        val wc = WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        listOfNotNull(wc?.primaryColor, wc?.secondaryColor, wc?.tertiaryColor).map { it.toArgb() }
    }.getOrDefault(emptyList())
}

/** [value], but only after it stopped changing for [ms]: keeps slider drags from re-rendering every frame. */
@Composable
fun <T> rememberDebounced(value: T, ms: Long = 120): T {
    var shown by remember { mutableStateOf(value) }
    LaunchedEffect(value) { delay(ms); shown = value }
    return shown
}

/**
 * A pretend home screen: the user's real apps on their wallpaper colors, drawn in [styleFor].
 * Icons are spaced like [PuzzleMath] expects, so puzzle styles line up across the grid.
 */
@Composable
fun MockHome(
    apps: List<LaunchableApp>,
    styleFor: (LaunchableApp) -> IconStyle,
    modifier: Modifier = Modifier,
    cols: Int = 4,
    rows: Int = 5,
    renderPx: Int = 144,
    labels: Boolean = true,
    selected: String? = null,
    onTap: ((LaunchableApp) -> Unit)? = null,
) {
    val brush = rememberWallpaperBrush()
    val shown = apps.take(cols * rows)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(22.dp)).background(brush).padding(horizontal = 10.dp, vertical = 12.dp)) {
        val density = LocalDensity.current
        val cell = maxWidth / cols
        val icon = cell / (1f + PuzzleMath.GAP)
        val labelSize = with(density) { (icon.toPx() * 0.17f).coerceIn(14f, 30f).toSp() }
        Column {
            shown.chunked(cols).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { i, app ->
                        val index = r * cols + i
                        val style = styleFor(app).forBulk()
                        val tile = if (style.puzzle && style.usesImage) Tile(index, shown.size) else null
                        Column(Modifier.width(cell), horizontalAlignment = Alignment.CenterHorizontally) {
                            val ring = if (app.pkg == selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(30)) else Modifier
                            Box(
                                Modifier.size(icon).then(ring)
                                    .then(if (onTap != null) Modifier.clip(RoundedCornerShape(30)).clickable { onTap(app) } else Modifier),
                            ) {
                                IconBitmap(rememberStyledIcon(style, app.pkg, app.label, renderPx, tile), Modifier.size(icon), app.label)
                            }
                            if (labels) Text(
                                app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                style = TextStyle(color = Color.White, fontSize = labelSize, shadow = Shadow(Color(0x99000000), blurRadius = 4f)),
                                modifier = Modifier.padding(top = 2.dp).height(icon * PuzzleMath.GAP - 4.dp),
                            ) else Box(Modifier.height(icon * PuzzleMath.GAP))
                        }
                    }
                }
            }
            if (shown.isEmpty()) Text(
                "…", color = Color.White, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center,
            )
        }
    }
}

/** Whole-grid renders for galleries, keyed by style, apps and width. */
private val gridCache = object : android.util.LruCache<String, android.graphics.Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
}

/**
 * [MockHome] for galleries (no labels, no taps): all icons are drawn into one bitmap on a
 * background thread, so a card is one image instead of 16 loading spinners and 16 uploads.
 * Same layout as [MockHome], so puzzle styles still line up.
 */
@Composable
fun MockHomeGrid(
    apps: List<LaunchableApp>,
    style: IconStyle,
    modifier: Modifier = Modifier,
    cols: Int = 4,
    rows: Int = 4,
    renderPx: Int = 96,
) {
    val ctx = LocalContext.current
    val brush = rememberWallpaperBrush()
    val shown = apps.take(cols * rows)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(22.dp)).background(brush).padding(horizontal = 10.dp, vertical = 12.dp)) {
        val widthPx = constraints.maxWidth
        val cell = maxWidth / cols
        val lines = ((shown.size + cols - 1) / cols).coerceAtLeast(rows)
        val key = remember(style, shown, widthPx) { "${style.hashCode()}|${shown.joinToString(",") { it.pkg }.hashCode()}|$widthPx|$cols" }
        val bmp by produceState(gridCache.get(key), key) {
            if (value != null || shown.isEmpty() || widthPx <= 0) return@produceState
            value = withContext(app.custo.ui.components.PreviewRender) {
                renderGrid(ctx, style, shown, widthPx, cols, lines, renderPx)
            }.also { gridCache.put(key, it) }
        }
        Box(Modifier.fillMaxWidth().height(cell * lines)) {
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize()) }
        }
    }
}

private suspend fun renderGrid(
    ctx: Context, style: IconStyle, apps: List<LaunchableApp>, widthPx: Int, cols: Int, lines: Int, renderPx: Int,
): android.graphics.Bitmap {
    val cell = widthPx.toFloat() / cols
    val icon = cell / (1f + PuzzleMath.GAP)
    val out = android.graphics.Bitmap.createBitmap(widthPx, (cell * lines).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
    val st = style.forBulk()
    apps.forEachIndexed { index, app ->
        val tile = if (st.puzzle && st.usesImage) Tile(index, apps.size) else null
        val b = runCatching { IconRenderer.render(ctx, st, app.pkg, app.label, renderPx, tile) }.getOrNull() ?: return@forEachIndexed
        val left = (index % cols) * cell + (cell - icon) / 2f
        val top = (index / cols) * cell
        canvas.drawBitmap(b, null, android.graphics.RectF(left, top, left + icon, top + icon), paint)
    }
    return out
}
