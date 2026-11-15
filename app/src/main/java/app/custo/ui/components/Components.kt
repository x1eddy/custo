package app.custo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalSnackbar = compositionLocalOf<SnackbarHostState> { error("No SnackbarHostState") }

/** Runs a privileged action off the UI and turns failures into a friendly snackbar. */
class ActionRunner(private val scope: CoroutineScope, private val snackbar: SnackbarHostState) {
    fun run(success: String? = null, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                if (success != null) snackbar.showSnackbar(success)
            } catch (e: app.custo.shizuku.ShellUnavailableException) {
                snackbar.showSnackbar("Start Shizuku first. Open Home → Set up Shizuku")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                snackbar.showSnackbar(e.message?.take(160) ?: "Something went wrong")
            }
        }
    }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }
}

@Composable
fun rememberActionRunner(): ActionRunner {
    val scope = rememberCoroutineScope()
    val snack = LocalSnackbar.current
    return remember(scope, snack) { ActionRunner(scope, snack) }
}

/**
 * Page shell: no stock app bar. The title is set big in the display face and scrolls with
 * the content; sub-screens get a back button and optional actions above it.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    leading: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    // Surface supplies onSurface as the default text color for everything on the page.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "__header") {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = if (onBack != null) 4.dp else 28.dp, bottom = 14.dp)) {
                if (onBack != null) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(Modifier.weight(1f))
                        actions()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
                    Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                    if (onBack == null) actions()
                }
            }
        }
        content()
    } }
}

/** A flat panel. Press feedback is a quick squish rather than a ripple. */
@Composable
fun SectionCard(
    title: String? = null,
    icon: ImageVector? = null,
    subtitle: String? = null,
    badge: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && !app.custo.ui.theme.LocalReduceMotion.current) 0.975f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(s)
            .then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.large).clickable(interaction, null, onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    badge?.invoke()
                    if (onClick != null && badge == null) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

/** Compact grid tile for hub screens: icon above a label that has the full width to itself. */
@Composable
fun HubTile(title: String, icon: ImageVector, modifier: Modifier = Modifier, badge: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && !app.custo.ui.theme.LocalReduceMotion.current) 0.95f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "hub")
    Column(
        modifier.scale(s).clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(interaction, null, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.weight(1f))
            badge?.invoke()
        }
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val toggle: (Boolean) -> Unit = { on ->
        haptics.performHapticFeedback(
            if (on) androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn else androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff,
        )
        onChange(on)
    }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled) { toggle(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = toggle, enabled = enabled)
    }
}

/** Status as a small glowing dot plus a word, instead of a filled pill. */
@Composable
fun SupportBadge(text: String, ok: Boolean) {
    val dot by animateColorAsState(if (ok) Color(0xFF3DDC84) else MaterialTheme.colorScheme.error, label = "badge")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 2.dp))
}

@Composable
fun ColorDot(color: Color, selected: Boolean, size: Int = 44, onClick: () -> Unit) {
    val ring by animateFloatAsState(if (selected) 1f else 0f, label = "ring")
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f * ring))
            .clickable(onClick = onClick)
            .padding((3 * ring).dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding((2 * ring).dp)) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(color))
        }
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
