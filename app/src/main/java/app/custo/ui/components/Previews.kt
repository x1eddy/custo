package app.custo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Small visual previews shared by the System UI screens: they show what a setting does instead of
// describing it in a sentence.

/** A switch row with a leading icon and a 1–3 word label. */
@Composable
fun IconSwitchRow(icon: ImageVector?, title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val toggle: (Boolean) -> Unit = { on ->
        haptics.performHapticFeedback(
            if (on) androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn else androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff,
        )
        onChange(on)
    }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled = enabled) { toggle(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val tint by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "rowIcon")
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = toggle, enabled = enabled)
    }
}

/** Icon + one word, for warnings that must stay visible (e.g. auto-revert). */
@Composable
fun IconNote(icon: ImageVector, text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

// ---------------------------------------------------------------------------------------------
// Skeletons

/** Pulsing placeholder block. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "skeletonAlpha")
    Box(modifier.height(height).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = a * 0.16f)))
}

/** A card-shaped skeleton: title bar and [rows] row placeholders. */
@Composable
fun SkeletonCard(rows: Int = 3) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.size(20.dp), 20.dp)
                Spacer(Modifier.width(10.dp))
                SkeletonBlock(Modifier.width(120.dp), 18.dp)
            }
            repeat(rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBlock(Modifier.size(22.dp), 22.dp)
                    Spacer(Modifier.width(14.dp))
                    SkeletonBlock(Modifier.weight(1f), 16.dp)
                    Spacer(Modifier.width(24.dp))
                    SkeletonBlock(Modifier.width(48.dp), 26.dp)
                }
            }
        }
    }
}

/** First-run placeholders while the phone is read for the very first time. */
fun LazyListScope.skeletonItems(cards: Int = 3) {
    items(cards, key = { "__skeleton$it" }) { SkeletonCard(rows = if (it == 0) 2 else 3) }
}

// ---------------------------------------------------------------------------------------------
// Status bar

/**
 * A status bar strip. [systemIcons] are the icons still visible; changes animate in and out.
 * The clock ticks live, with seconds when [seconds] is on.
 */
@Composable
fun StatusBarMock(
    systemIcons: List<Pair<String, ImageVector>>,
    notificationIcons: List<ImageVector>,
    showClock: Boolean,
    seconds: Boolean,
    showBattery: Boolean,
    batteryPercent: Boolean,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(seconds) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (seconds) 1000 else 15_000)
        }
    }
    val fmt = remember(seconds) { SimpleDateFormat(if (seconds) "H:mm:ss" else "H:mm", Locale.getDefault()) }
    val fg = Color.White
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color(0xFF0E0E12)) {
        Row(Modifier.padding(horizontal = 16.dp).height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(showClock, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                Text(fmt.format(Date(now)), color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp))
            }
            notificationIcons.forEach { Icon(it, null, tint = fg.copy(alpha = 0.85f), modifier = Modifier.padding(end = 4.dp).size(14.dp)) }
            Spacer(Modifier.weight(1f))
            systemIcons.forEach { (_, icon) -> Icon(icon, null, tint = fg, modifier = Modifier.padding(start = 4.dp).size(15.dp)) }
            AnimatedVisibility(showBattery, enter = fadeIn(), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(Icons.Rounded.BatteryFull, null, tint = fg, modifier = Modifier.size(16.dp))
                    AnimatedVisibility(batteryPercent) {
                        Text("100%", color = fg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Phone mocks

/** A small phone outline; [content] draws the screen. */
@Composable
fun PhoneFrame(modifier: Modifier = Modifier, screen: Color = MaterialTheme.colorScheme.surface, content: @Composable () -> Unit) {
    Box(
        modifier.aspectRatio(0.5f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1B1B20))
            .padding(4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(screen),
    ) { content() }
}

/** Big swatches for a palette. */
@Composable
fun SwatchRow(colors: List<Color>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp))) {
        colors.forEach { c ->
            val anim by animateColorAsState(c, label = "swatch")
            Box(Modifier.weight(1f).fillMaxHeight().background(anim))
        }
    }
}

/** Mini phone tinted like Material You would tint apps: [primary] header/FAB, containers, surface. */
@Composable
fun PalettePhone(primary: Color, secondary: Color, tertiary: Color, container: Color, surface: Color, modifier: Modifier = Modifier) {
    val p by animateColorAsState(primary, label = "p")
    val s by animateColorAsState(secondary, label = "s")
    val t by animateColorAsState(tertiary, label = "t")
    val c by animateColorAsState(container, label = "c")
    val bg by animateColorAsState(surface, label = "bg")
    PhoneFrame(modifier, bg) {
        Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(5.dp)).background(p))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(6.dp)).background(c))
                Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(6.dp)).background(s))
            }
            repeat(3) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (it == 1) t else s))
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(t))
            }
        }
    }
}

/**
 * Mini phone whose content scales live: [uiScale] for display size (icons + layout), [textScale]
 * for font size. 1 = stock.
 */
@Composable
fun ScaledPhoneMock(uiScale: Float, textScale: Float, modifier: Modifier = Modifier) {
    val ui by animateFloatAsState(uiScale, label = "ui")
    val tx by animateFloatAsState(textScale, label = "tx")
    val cs = MaterialTheme.colorScheme
    PhoneFrame(modifier, cs.surfaceContainerHighest) {
        Column(Modifier.fillMaxSize().padding(6.dp)) {
            Text("12:30", fontSize = (14 * ui * tx).sp, fontWeight = FontWeight.Bold, color = cs.onSurface, maxLines = 1)
            Text("Sunday", fontSize = (7 * ui * tx).sp, color = cs.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height((6 * ui).dp))
            val icon = (14 * ui).dp
            val colors = listOf(cs.primary, cs.tertiary, cs.secondary, cs.error)
            repeat(3) { row ->
                Row(Modifier.padding(bottom = (5 * ui).dp), horizontalArrangement = Arrangement.spacedBy((5 * ui).dp)) {
                    repeat(4) { col ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(icon).clip(CircleShape).background(colors[(row + col) % 4]))
                            Text("App", fontSize = (5 * ui * tx).sp, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Clip)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Motion

/** A card that slides in, waits, and slides out on a loop, at [scale]× the normal duration (0 = instant). */
@Composable
fun AnimationDemo(scale: Float, modifier: Modifier = Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(scale) {
        while (true) {
            p.snapTo(0f)
            delay(350)
            val ms = (450 * scale).toInt()
            if (ms <= 0) p.snapTo(1f) else p.animateTo(1f, tween(ms))
            delay(900)
            if (ms <= 0) p.snapTo(2f) else p.animateTo(2f, tween(ms))
        }
    }
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHighest)) {
        val w = maxWidth
        val x = (p.value - 1f) * w.value
        Row(
            Modifier.offset(x = x.dp).fillMaxSize().padding(10.dp).clip(RoundedCornerShape(10.dp)).background(cs.primaryContainer).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(cs.primary))
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.width(90.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(cs.onPrimaryContainer.copy(alpha = 0.7f)))
                Box(Modifier.width(56.dp).height(6.dp).clip(RoundedCornerShape(4.dp)).background(cs.onPrimaryContainer.copy(alpha = 0.4f)))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Navigation

enum class NavMockKind { GESTURE, THREE_BUTTON, TWO_BUTTON }

/** Selectable card showing the bottom of a phone in one navigation mode. */
@Composable
fun NavModeCard(kind: NavMockKind, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) cs.primary else Color.Transparent, label = "navBorder")
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).border(BorderStroke(2.dp, border), RoundedCornerShape(16.dp))
            .background(if (selected) cs.primaryContainer else cs.surfaceContainerHighest)
            .clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val fg = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(cs.surface).padding(bottom = 8.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            when (kind) {
                NavMockKind.GESTURE -> Box(Modifier.width(34.dp).height(4.dp).clip(CircleShape).background(fg))
                NavMockKind.THREE_BUTTON -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = fg, modifier = Modifier.size(12.dp))
                    Icon(Icons.Rounded.Circle, null, tint = fg, modifier = Modifier.size(12.dp))
                    Icon(Icons.Rounded.CropSquare, null, tint = fg, modifier = Modifier.size(13.dp))
                }
                NavMockKind.TWO_BUTTON -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = fg, modifier = Modifier.size(12.dp))
                    Box(Modifier.width(24.dp).height(8.dp).clip(CircleShape).background(fg))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Screen filters

/**
 * A sample picture (sky, sun, hills) with screen filters applied: [dim] 0..1 darkening, [warmth]
 * 0..1 night-light amber, [grayscale], [invert].
 */
@Composable
fun FilterSample(dim: Float, warmth: Float, grayscale: Boolean, invert: Boolean, modifier: Modifier = Modifier) {
    val d by animateFloatAsState(dim, label = "dim")
    val w by animateFloatAsState(warmth, label = "warm")
    val g by animateFloatAsState(if (grayscale) 1f else 0f, label = "gray")
    val i by animateFloatAsState(if (invert) 1f else 0f, label = "inv")
    val filter = remember(g, i) {
        val m = ColorMatrix().apply { setToSaturation(1f - g) }
        if (i > 0f) {
            val k = 1f - 2f * i
            val off = 255f * i
            m.timesAssign(ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, off, 0f, k, 0f, 0f, off, 0f, 0f, k, 0f, off, 0f, 0f, 0f, 1f, 0f)))
        }
        ColorFilter.colorMatrix(m)
    }
    Canvas(
        modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp))
            .graphicsLayer()
            .drawWithContent {
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply { colorFilter = filter }
                    canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), paint)
                    drawContent()
                    canvas.restore()
                }
                drawRect(Color(0xFFFF9A2E).copy(alpha = 0.35f * w))
                drawRect(Color.Black.copy(alpha = 0.6f * d))
            },
    ) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3D7BFF), Color(0xFF9FD3FF))))
        drawCircle(Color(0xFFFFD54F), size.minDimension * 0.16f, Offset(size.width * 0.75f, size.height * 0.32f))
        val back = Path().apply {
            moveTo(0f, size.height * 0.7f)
            quadraticTo(size.width * 0.3f, size.height * 0.35f, size.width * 0.6f, size.height * 0.7f)
            lineTo(size.width * 0.6f, size.height); lineTo(0f, size.height); close()
        }
        drawPath(back, Color(0xFF2E9E5B))
        val front = Path().apply {
            moveTo(size.width * 0.25f, size.height)
            quadraticTo(size.width * 0.7f, size.height * 0.5f, size.width, size.height * 0.72f)
            lineTo(size.width, size.height); close()
        }
        drawPath(front, Color(0xFF1F7A44))
        drawRect(Color(0xFFE53935), Offset(size.width * 0.12f, size.height * 0.78f), Size(size.width * 0.08f, size.height * 0.12f))
    }
}

// ---------------------------------------------------------------------------------------------
// Quick Settings

/** A Quick Settings tile: rounded rectangle with an icon and a label. */
@Composable
fun QsTileChip(label: String, icon: ImageVector, active: Boolean, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (active) cs.primary else cs.surfaceContainerHighest, label = "qsBg")
    val fg = if (active) cs.onPrimary else cs.onSurface
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(20.dp)).background(bg).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
