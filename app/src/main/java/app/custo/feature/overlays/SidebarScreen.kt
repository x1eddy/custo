package app.custo.feature.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SidebarScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    var picking by remember { mutableStateOf(false) }
    val apps = OverlayLogic.csv(p[OverlayKeys.sidebarApps])
    val actions = p[OverlayKeys.sidebarActions]?.let { OverlayLogic.csv(it) } ?: EdgeSidebarOverlay.DEFAULT_ACTIONS

    ScreenScaffold("Edge sidebar", onBack) {
        item {
            SectionCard(subtitle = "Tap or swipe the handle to open. Tap outside to close.") {
                SwitchRow("Enabled", null, p[OverlayKeys.sidebar] == true) { ui.set(OverlayKeys.sidebar, it) }
                Segmented(PanelSide.entries, enumOr(p[OverlayKeys.sidebarSide], PanelSide.RIGHT), { it.label }) { ui.set(OverlayKeys.sidebarSide, it.name) }
                PrefSlider("Handle position", -(p[OverlayKeys.sidebarOffsetY] ?: -0.1f), -0.4f..0.4f, { if (it == 0f) "Middle" else "%+d".format((it * 100).toInt()) }) {
                    ui.set(OverlayKeys.sidebarOffsetY, -it)
                }
                PreviewButton("Open panel") { ui.preview(Preview.SIDEBAR) }
            }
        }
        item {
            SectionCard("Apps", subtitle = "Up to ${EdgeSidebarOverlay.MAX_APPS}") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.forEach { AppIcon(it, 40) }
                }
                OutlinedButton(onClick = { picking = true }) { Text("Choose apps") }
            }
        }
        item {
            SectionCard("Shortcuts") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayAction.sidebarChoices.forEach { a ->
                        val on = a.name in actions
                        FilterChip(on, {
                            ui.set(OverlayKeys.sidebarActions, OverlayLogic.csv(if (on) actions - a.name else actions + a.name))
                        }, { Text(a.label) })
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
