package app.custo.feature.overlays

import androidx.compose.runtime.Composable
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun ButtonsScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    val choices = OverlayAction.forButtons
    ScreenScaffold("Button actions", onBack) {
        item {
            SectionCard(subtitle = "A single press still changes the volume. Calls are never touched.") {
                SwitchRow("Enabled", null, p[OverlayKeys.buttons] == true) { ui.set(OverlayKeys.buttons, it) }
            }
        }
        item {
            SectionCard("Volume up") {
                ActionRow("Double press", OverlayAction.of(p[OverlayKeys.upDouble]), choices) { ui.set(OverlayKeys.upDouble, it.name) }
                ActionRow("Hold", OverlayAction.of(p[OverlayKeys.upLong]), choices) { ui.set(OverlayKeys.upLong, it.name) }
            }
        }
        item {
            SectionCard("Volume down") {
                ActionRow("Double press", OverlayAction.of(p[OverlayKeys.downDouble]), choices) { ui.set(OverlayKeys.downDouble, it.name) }
                ActionRow("Hold", OverlayAction.of(p[OverlayKeys.downLong]), choices) { ui.set(OverlayKeys.downLong, it.name) }
            }
        }
        item {
            Hint("With a double-press action set, a single press waits a moment to be sure. Works with the screen off too on most phones.")
        }
    }
}
