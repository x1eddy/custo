package app.custo.feature.overlays

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun IslandScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    val idle = p[OverlayKeys.islandIdlePill] == true

    ScreenScaffold("Dynamic island", onBack, actions = {
        InfoButton(
            "Dynamic island",
            "A black pill at the top of the screen. Tap it to expand or collapse. Music, timers and alerts need notification access. " +
                "While it's visible, touches on the pill don't reach the status bar below it.",
        )
    }) {
        item { NotificationAccessCard("The island needs it for music, timers and alerts.") }
        item {
            OverlayHero(p[OverlayKeys.island] == true, { ui.set(OverlayKeys.island, it) }, { ui.preview(Preview.ISLAND) }, extra = {
                SwitchRow("Pill when idle", null, idle) { ui.set(OverlayKeys.islandIdlePill, it) }
            }) {
                IslandPreview(Modifier.phoneSize(), idle)
            }
        }
        item {
            SectionCard("Show") {
                IconSwitch(Icons.Rounded.MusicNote, "Now playing", p[OverlayKeys.islandMedia] != false) { ui.set(OverlayKeys.islandMedia, it) }
                IconSwitch(Icons.Rounded.BatteryChargingFull, "Charging", p[OverlayKeys.islandCharging] != false) { ui.set(OverlayKeys.islandCharging, it) }
                IconSwitch(Icons.Rounded.Notifications, "Notifications", p[OverlayKeys.islandNotifs] != false) { ui.set(OverlayKeys.islandNotifs, it) }
                IconSwitch(Icons.Rounded.Timer, "Timers", p[OverlayKeys.islandTimer] != false) { ui.set(OverlayKeys.islandTimer, it) }
            }
        }
        item {
            SectionCard("Behavior", Icons.Rounded.Schedule) {
                PrefSlider("Collapse after", (p[OverlayKeys.islandCollapseSec] ?: 4).toFloat(), 2f..10f, { "${it.toInt()} s" }, steps = 7) {
                    ui.set(OverlayKeys.islandCollapseSec, it.toInt())
                }
            }
        }
    }
}
