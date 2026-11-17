package app.custo.feature.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.feature.overlays.EdgeColorMode
import app.custo.feature.overlays.EdgeLightPreview
import app.custo.feature.overlays.EdgeStyle
import app.custo.feature.overlays.IslandPreview
import app.custo.feature.overlays.OverlayBus
import app.custo.feature.overlays.edgeColors
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.HubTile
import app.custo.ui.components.OnDot
import app.custo.ui.components.PalettePhone
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SupportBadge
import app.custo.ui.components.enterAnimation

/** Tweaks tab: System UI and Overlays as two big picture tiles, then profiles, backup and Undo all. */
@Composable
fun TweaksScreen(nav: Navigator) {
    val c = LocalContext.current.container
    val cs = MaterialTheme.colorScheme
    val state by c.shizuku.state.collectAsStateWithLifecycle()
    val overlaysOn by OverlayBus.serviceRunning.collectAsStateWithLifecycle()
    val ready = state == ShizukuState.READY
    var confirmReset by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Tweaks", actions = {
        Box(Modifier.clip(CircleShape).clickable { nav.push(Route.ShizukuSetup) }.padding(8.dp)) {
            SupportBadge(if (ready) "System access" else "No system access", ready)
        }
    }) {
        item {
            PictureTile("System UI", null, Modifier.enterAnimation(0), onClick = { nav.push(Route.SystemUiHub) }) {
                Row(Modifier.fillMaxSize().padding(top = 18.dp, start = 18.dp, end = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PalettePhone(cs.primary, cs.secondary, cs.tertiary, cs.primaryContainer, cs.surface, Modifier.weight(1f).fillMaxHeight())
                    PalettePhone(cs.tertiary, cs.primaryContainer, cs.primary, cs.secondaryContainer, cs.surfaceContainerHigh, Modifier.weight(1f).fillMaxHeight())
                    PalettePhone(cs.secondary, cs.tertiaryContainer, cs.secondary, cs.tertiaryContainer, cs.surfaceContainerLow, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        item {
            PictureTile("Overlays", overlaysOn, Modifier.enterAnimation(1), onClick = { nav.push(Route.OverlaysHub) }) {
                Row(Modifier.fillMaxSize().padding(top = 18.dp, start = 18.dp, end = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EdgeLightPreview(EdgeStyle.SWEEP, edgeColors(EdgeColorMode.RAINBOW, emptyList(), cs.primary), 4f, Modifier.weight(1f).fillMaxHeight())
                    IslandPreview(Modifier.weight(1f).fillMaxHeight())
                    EdgeLightPreview(EdgeStyle.COMET, edgeColors(EdgeColorMode.ACCENT, emptyList(), cs.primary), 5f, Modifier.weight(1f).fillMaxHeight(), periodMs = 3200)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.enterAnimation(2)) {
                HubTile("Per-app profiles", Icons.Rounded.Person, Modifier.weight(1f)) { nav.push(Route.Profiles) }
                HubTile("Backup", Icons.Rounded.Backup, Modifier.weight(1f)) { nav.push(Route.Backup) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.enterAnimation(3)) {
                HubTile("Undo all", Icons.AutoMirrored.Rounded.Undo, Modifier.weight(1f)) { confirmReset = true }
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Undo everything?") },
            text = { Text("Puts back every system setting Custo changed. Wallpapers and widgets stay.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; c.undoAll() }) { Text("Undo all") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

/** A big tile: a live picture filling the top, the name at the bottom. [on] adds a status dot. */
@Composable
private fun PictureTile(title: String, on: Boolean?, modifier: Modifier, onClick: () -> Unit, picture: @Composable BoxScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bounce = !app.custo.ui.theme.LocalReduceMotion.current
    val s by animateFloatAsState(if (pressed && bounce) 0.97f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "pt")
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth().height(250.dp).scale(s).clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(cs.surfaceContainerHigh, cs.surfaceContainerLow)))
            .clickable(interaction, null, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(186.dp), content = picture)
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
            if (on != null) OnDot(on)
        }
    }
}
