package app.custo.feature.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SidebarScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    var picking by remember { mutableStateOf(false) }
    val apps = OverlayLogic.csv(p[OverlayKeys.sidebarApps])
    val actions = p[OverlayKeys.sidebarActions]?.let { OverlayLogic.csv(it) } ?: EdgeSidebarOverlay.DEFAULT_ACTIONS
    val side = enumOr(p[OverlayKeys.sidebarSide], PanelSide.RIGHT)
    val offset = -(p[OverlayKeys.sidebarOffsetY] ?: -0.1f)

    ScreenScaffold("Edge sidebar", onBack, actions = {
        InfoButton("Edge sidebar", "Tap or swipe the handle on the screen edge to open your apps and shortcuts. Tap outside to close. Up to ${EdgeSidebarOverlay.MAX_APPS} apps.")
    }) {
        item {
            OverlayHero(p[OverlayKeys.sidebar] == true, { ui.set(OverlayKeys.sidebar, it) }, { ui.preview(Preview.SIDEBAR) }) {
                SidebarPreview(side, -offset, apps.size, Modifier.phoneSize())
            }
        }
        item {
            SectionCard("Handle", Icons.Rounded.OpenWith) {
                Segmented(PanelSide.entries, side, { it.label }) { ui.set(OverlayKeys.sidebarSide, it.name) }
                PrefSlider("Position", offset, -0.4f..0.4f, { if (it == 0f) "Middle" else "%+d".format((it * 100).toInt()) }) {
                    ui.set(OverlayKeys.sidebarOffsetY, -it)
                }
            }
        }
        item {
            SectionCard("Apps", Icons.Rounded.Apps, badge = {
                Text("${apps.size}/${EdgeSidebarOverlay.MAX_APPS}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }) {
                if (apps.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.forEach { AppIcon(it, 40) }
                }
                OutlinedButton(onClick = { picking = true }) { Icon(Icons.Rounded.Add, null); Text(" Choose") }
            }
        }
        item {
            SectionCard("Shortcuts", Icons.Rounded.Tune) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverlayAction.sidebarChoices.forEach { a ->
                        val on = a.name in actions
                        ActionToggle(a, on) {
                            ui.set(OverlayKeys.sidebarActions, OverlayLogic.csv(if (on) actions - a.name else actions + a.name))
                        }
                    }
                }
            }
        }
    }

    if (picking) AppPickerDialog("Sidebar apps", apps, max = EdgeSidebarOverlay.MAX_APPS, onDismiss = { picking = false }) {
        picking = false
        ui.set(OverlayKeys.sidebarApps, OverlayLogic.csv(it))
    }
}
