package app.custo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

// ---------- Info sheet ----------

/**
 * A small ⓘ button. Details that used to sit on screen as sentences (permissions, Android limits,
 * restricted-settings help) live behind it in a bottom sheet.
 */
@Composable
fun InfoButton(title: String, text: String, modifier: Modifier = Modifier) =
    InfoButton(title, modifier) { InfoText(text) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoButton(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Outlined.Info, "About $title", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
            }
        }
    }
}

/** One paragraph in an info sheet. */
@Composable
fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** An icon + short line, for info sheets and step lists. */
@Composable
fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

// ---------- Cheap animation clock ----------

/**
 * A 0..1 phase that loops every [periodMs]. It only ticks while the screen is resumed and the
 * caller is composed (lazy lists drop off-screen items, which stops it). Read it inside a draw
 * block so a tick redraws without recomposing.
 */
@Composable
fun rememberLoop(periodMs: Int, running: Boolean = true): FloatState {
    val phase = remember { mutableFloatStateOf(0.3f) }
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = running && state.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(active, periodMs) {
        if (!active) return@LaunchedEffect
        val offset = (phase.floatValue * periodMs * 1_000_000L).toLong()
        val start = withFrameNanos { it } - offset
        while (true) withFrameNanos { t -> phase.floatValue = (((t - start) / 1_000_000L) % periodMs) / periodMs.toFloat() }
    }
    return phase
}

/** 0→1→0 ease used by several previews. */
fun tri(x: Float): Float = if (x < 0.5f) x * 2f else (1f - x) * 2f

fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

// ---------- Mini screen ----------

/** Where things are on a [drawMiniScreen]. */
class MiniScreen(val rect: Rect, val corner: Float, val bar: Float, val cam: Offset, val camR: Float, val unit: Float) {
    val w get() = rect.width
    val h get() = rect.height
}

/** Colors for a mini screen, taken from the app theme so previews match the palette. */
class MiniColors(val body: Color, val top: Color, val bottom: Color, val ink: Color)

@Composable
fun miniColors(): MiniColors {
    val cs = MaterialTheme.colorScheme
    return remember(cs.primary, cs.tertiary) {
        // Deep, saturated mock wallpaper so white previews (glow, bars, text) stand out. A grey
        // palette (e.g. Mono) would make every preview look empty, so fall back to Custo's colors.
        fun chroma(c: Color) = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)
        val (a, b) = if (chroma(cs.primary) < 0.12f && chroma(cs.tertiary) < 0.12f) MarkIndigo to MarkTeal else cs.primary to cs.tertiary
        MiniColors(
            Color(0xFF26262B),
            androidx.compose.ui.graphics.lerp(a, Color.Black, 0.35f),
            androidx.compose.ui.graphics.lerp(b, Color.Black, 0.55f),
            Color.White,
        )
    }
}

/**
 * Draws a phone screen filling this scope: a thin dark bezel, the palette as wallpaper, a status
 * bar hint and a camera hole. [unit] is the size of 1 dp on a real phone, scaled to the mock,
 * so previews can map real settings (dp) to the drawing.
 */
fun DrawScope.drawMiniScreen(c: MiniColors, frame: Boolean = true, wallpaper: Boolean = true, squareCorners: Boolean = false): MiniScreen {
    val bezel = if (frame) size.minDimension * 0.035f + 2f else 0f
    val outerR = size.minDimension * 0.14f
    if (frame) drawRoundRect(c.body, cornerRadius = CornerRadius(outerR))
    val r = Rect(bezel, bezel, size.width - bezel, size.height - bezel)
    val corner = if (squareCorners) 0f else (outerR - bezel).coerceAtLeast(0f)
    if (wallpaper) {
        drawRoundRect(
            Brush.linearGradient(listOf(c.top, c.bottom), r.topLeft, r.bottomRight),
            r.topLeft, r.size, CornerRadius(corner),
        )
        // Two soft blobs so it reads as a wallpaper rather than a flat panel.
        drawCircle(c.ink.copy(alpha = 0.08f), r.width * 0.42f, Offset(r.left + r.width * 0.2f, r.top + r.height * 0.78f))
        drawCircle(c.ink.copy(alpha = 0.06f), r.width * 0.3f, Offset(r.right - r.width * 0.1f, r.top + r.height * 0.3f))
    }
    val unit = r.width / 380f
    val bar = (24f * unit).coerceAtLeast(r.height * 0.045f)
    // Status bar hints: time on the left, icons on the right.
    val ty = r.top + bar / 2
    val th = (bar * 0.28f).coerceAtLeast(2f)
    drawRoundRect(c.ink.copy(alpha = 0.75f), Offset(r.left + r.width * 0.07f, ty - th / 2), Size(r.width * 0.1f, th), CornerRadius(th / 2))
    for (i in 0 until 3) drawCircle(c.ink.copy(alpha = 0.7f), th * 0.6f, Offset(r.right - r.width * 0.07f - i * th * 2f, ty))
    val camR = (bar * 0.24f).coerceAtLeast(2.5f)
    val cam = Offset(r.center.x, ty)
    drawCircle(Color.Black, camR, cam)
    return MiniScreen(r, corner, bar, cam, camR, unit)
}

/** A canvas showing a mini screen with [content] drawn on top. */
@Composable
fun MiniScreenCanvas(modifier: Modifier, frame: Boolean = true, squareCorners: Boolean = false, content: DrawScope.(MiniScreen) -> Unit) {
    val c = miniColors()
    Canvas(modifier) { content(drawMiniScreen(c, frame, squareCorners = squareCorners)) }
}

// ---------- Visual pickers ----------

/**
 * A row of thumbnails with a one-word caption each. The selected one gets a ring.
 * Used instead of chips with words where a picture explains the choice.
 */
@Composable
fun <T> ThumbPicker(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    thumbWidth: Int = 72,
    thumbHeight: Int = 72,
    thumb: @Composable BoxScope.(T, Boolean) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items) { t -> ThumbChoice(label(t), t == selected, thumbWidth, thumbHeight, { onSelect(t) }) { thumb(t, t == selected) } }
    }
}

@Composable
fun ThumbChoice(label: String, selected: Boolean, width: Int, height: Int, onClick: () -> Unit, thumb: @Composable BoxScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.5f), label = "thumb")
    Column(
        Modifier.width(width.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(width.dp, height.dp).clip(RoundedCornerShape(18.dp))
                .border(if (selected) 3.dp else 1.dp, ring, RoundedCornerShape(18.dp))
                .background(cs.surfaceContainerHigh).padding(if (selected) 5.dp else 4.dp),
            contentAlignment = Alignment.Center,
            content = thumb,
        )
        Text(
            label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) cs.onSurface else cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A swatch for "color mode" pickers: a circle filled with [brush]. */
@Composable
fun BrushSwatch(brush: Brush, modifier: Modifier = Modifier.size(40.dp)) {
    Box(modifier.clip(CircleShape).background(brush))
}

/** On/off dot without a word. */
@Composable
fun OnDot(on: Boolean, modifier: Modifier = Modifier) {
    val c by animateColorAsState(if (on) Color(0xFF3DDC84) else MaterialTheme.colorScheme.outlineVariant, label = "dot")
    Box(modifier.size(10.dp).clip(CircleShape).background(c))
}

/**
 * Hub tile with a picture on top and a short title + on dot underneath.
 * The [preview] fills a fixed-height area so a grid of these lines up.
 */
@Composable
fun PreviewTile(
    title: String,
    on: Boolean?,
    modifier: Modifier = Modifier,
    previewHeight: Int = 104,
    onClick: () -> Unit,
    preview: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "tile")
    Column(
        modifier.scale(s).clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(interaction, null, onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(previewHeight.dp).clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
            content = preview,
        )
        Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (on != null) OnDot(on)
        }
    }
}

/** A big centered stage for a live preview at the top of a detail screen. */
@Composable
fun PreviewStage(modifier: Modifier = Modifier, height: Int = 220, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Icon in a tinted rounded square, for step lists and icon-first rows. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Int = 44, container: Color = MaterialTheme.colorScheme.primaryContainer, tint: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(modifier.size(size.dp).clip(RoundedCornerShape((size * 0.34f).dp)).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * 0.5f).dp), tint = tint)
    }
}
