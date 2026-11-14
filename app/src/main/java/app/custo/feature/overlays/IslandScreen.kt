package app.custo.feature.overlays

import androidx.compose.runtime.Composable
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun IslandScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p

    ScreenScaffold("Dynamic island", onBack) {
        item { NotificationAccessCard("The island needs it for music, timers and alerts.") }
        item {
            SectionCard(subtitle = "A black pill at the top of the screen. Tap it to expand or collapse.") {
                SwitchRow("Enabled", "Needs notification access for music, timers and alerts", p[OverlayKeys.island] == true) { ui.set(OverlayKeys.island, it) }
                SwitchRow("Small pill when idle", "Otherwise it hides when there's nothing to show", p[OverlayKeys.islandIdlePill] == true) { ui.set(OverlayKeys.islandIdlePill, it) }
                PreviewButton { ui.preview(Preview.ISLAND) }
            }
        }
        item {
            SectionCard("Show") {
                SwitchRow("Now playing", "Title, artist, art and play/pause", p[OverlayKeys.islandMedia] != false) { ui.set(OverlayKeys.islandMedia, it) }
                SwitchRow("Charging", "Level when you plug in", p[OverlayKeys.islandCharging] != false) { ui.set(OverlayKeys.islandCharging, it) }
                SwitchRow("New notifications", "Tap the preview to open it", p[OverlayKeys.islandNotifs] != false) { ui.set(OverlayKeys.islandNotifs, it) }
                SwitchRow("Timers and stopwatches", "From apps that show a running clock", p[OverlayKeys.islandTimer] != false) { ui.set(OverlayKeys.islandTimer, it) }
            }
        }
        item {
            SectionCard("Behavior") {
                PrefSlider("Collapse after", (p[OverlayKeys.islandCollapseSec] ?: 4).toFloat(), 2f..10f, { "${it.toInt()} s" }, steps = 7) {
                    ui.set(OverlayKeys.islandCollapseSec, it.toInt())
                }
                Hint("While it's visible, touches on the pill don't reach the status bar below it.")
            }
        }
    }
}
