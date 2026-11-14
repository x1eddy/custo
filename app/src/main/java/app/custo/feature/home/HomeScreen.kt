package app.custo.feature.home

import android.app.WallpaperManager
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.data.Keys
import app.custo.feature.overlays.OverlayBus
import app.custo.feature.systemui.MonetJson
import app.custo.feature.themes.WallPreview
import app.custo.feature.themes.WallSpec
import app.custo.feature.themes.WallStyle
import app.custo.feature.widgets.Widgets
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.nav.Tab
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SupportBadge
import app.custo.ui.components.rememberActionRunner

@Composable
fun HomeScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val cs = MaterialTheme.colorScheme
    val state by c.shizuku.state.collectAsStateWithLifecycle()
    val prefs by c.prefs.data.collectAsStateWithLifecycle(emptyPreferences())
    val overlaysOn by OverlayBus.serviceRunning.collectAsStateWithLifecycle()
    val runner = rememberActionRunner()
    var confirmReset by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    var widgetCount by remember { mutableIntStateOf(0) }
    var styleName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tick, state) {
        widgetCount = runCatching { Widgets.placed(ctx).size }.getOrDefault(0)
        if (state == ShizukuState.READY) styleName = runCatching { MonetJson.styleOf(c.tweaks.monetJson())?.label }.getOrNull()
    }
    // The user's real wallpaper colors (no storage permission needed), drawn as a mesh.
    val wallSpec = remember(tick) {
        val wc = WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        val colors = listOfNotNull(wc?.primaryColor, wc?.secondaryColor, wc?.tertiaryColor).map { it.toArgb() }
        WallSpec(WallStyle.MESH, (listOf(0xFF101014.toInt()) + colors).ifEmpty { WallSpec().colors }, seed = 4)
    }
    val ready = state == ShizukuState.READY

    ScreenScaffold(
        title = "Custo",
        actions = {
            Box(Modifier.clip(CircleShape).clickable { nav.push(Route.ShizukuSetup) }.padding(8.dp)) {
                SupportBadge(if (ready) "System access" else "No system access", ready)
            }
        },
    ) {
        if (!ready) item {
            Tile(cs.errorContainer, cs.onErrorContainer, Modifier.fillMaxWidth(), onClick = {
                if (state == ShizukuState.NO_PERMISSION) c.shizuku.requestPermission() else nav.push(Route.ShizukuSetup)
            }) {
                Text("Unlock the real system UI", style = MaterialTheme.typography.titleLarge)
                Text(
                    when (state) {
                        ShizukuState.NO_PERMISSION -> "Shizuku is running. Tap to let Custo use it."
                        ShizukuState.NOT_RUNNING -> "Shizuku is installed. Start it, then come back."
                        else -> "One-time setup with Shizuku. No root."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Your setup: live wallpaper colors + current palette.
        item {
            Tile(cs.primaryContainer, cs.onPrimaryContainer, Modifier.fillMaxWidth().height(208.dp), onClick = { nav.goTab(Tab.THEMES) }) {
                Row(Modifier.fillMaxHeight()) {
                    WallPreview(wallSpec, Modifier.fillMaxHeight())
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Your look", style = MaterialTheme.typography.labelLarge)
                            Text(styleName ?: "Material You", style = MaterialTheme.typography.headlineMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(cs.primary, cs.secondary, cs.tertiary, cs.surfaceContainerHighest).forEach {
                                Box(Modifier.size(26.dp).clip(CircleShape).background(it))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("New look", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(cs.tertiaryContainer, cs.onTertiaryContainer, Modifier.weight(1f).height(168.dp), onClick = { nav.goTab(Tab.SYSTEM) }) {
                    Text("System UI", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Text("Colors, status bar, tiles, nav, motion", style = MaterialTheme.typography.bodySmall)
                }
                Tile(cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f).height(168.dp), onClick = { nav.goTab(Tab.WIDGETS) }) {
                    Text("Widgets", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Text("$widgetCount", style = MaterialTheme.typography.displayMedium)
                    Text("on your home screen", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Tile(cs.surfaceContainerHigh, cs.onSurface, Modifier.fillMaxWidth(), onClick = { nav.goTab(Tab.OVERLAYS) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Overlays", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    SupportBadge(if (overlaysOn) "Running" else "Off", overlaysOn)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Edge light", prefs[Keys.edgeEnabled] == true)
                    Chip("Battery bar", prefs[Keys.batteryBarEnabled] == true)
                    Chip("Volume", prefs[Keys.volumePanelEnabled] == true)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(cs.surfaceContainerLow, cs.onSurface, Modifier.weight(1f), onClick = { nav.push(Route.Backup) }) {
                    Icon(Icons.Rounded.Backup, null)
                    Text("Backup", style = MaterialTheme.typography.titleMedium)
                }
                Tile(cs.surfaceContainerLow, cs.error, Modifier.weight(1f), onClick = { confirmReset = true }) {
                    Text("↺", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Undo all", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Undo everything?") },
            text = { Text("Puts back every system setting Custo changed. Wallpapers and widgets stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    runner.run {
                        val notes = c.tweaks.restoreAll()
                        runner.message(if (notes.isEmpty()) "All back to how it was" else notes.joinToString("\n"))
                    }
                }) { Text("Undo all") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Tile(
    container: Color,
    content: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "tile")
    Column(
        modifier.scale(s).clip(RoundedCornerShape(28.dp)).background(container)
            .clickable(interaction, null, onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides content) { body() }
    }
}

@Composable
private fun Chip(label: String, on: Boolean) {
    val cs = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (on) cs.onPrimary else cs.onSurfaceVariant,
        modifier = Modifier.clip(CircleShape).background(if (on) cs.primary else cs.surfaceContainerHighest).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
