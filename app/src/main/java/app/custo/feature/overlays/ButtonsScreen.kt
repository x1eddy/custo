package app.custo.feature.overlays

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard

@Composable
fun ButtonsScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    val choices = OverlayAction.forButtons
    ScreenScaffold("Button actions", onBack, actions = {
        InfoButton(
            "Button actions",
            "A single press still changes the volume, and calls are never touched. With a double-press action set, a single press waits a moment to be sure. " +
                "Works with the screen off too on most phones.",
        )
    }) {
        item {
            OverlayHero(p[OverlayKeys.buttons] == true, { ui.set(OverlayKeys.buttons, it) }, stageHeight = 190) {
                ButtonsPreview(
                    OverlayAction.of(p[OverlayKeys.upDouble]), OverlayAction.of(p[OverlayKeys.upLong]),
                    OverlayAction.of(p[OverlayKeys.downDouble]), OverlayAction.of(p[OverlayKeys.downLong]),
                    Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            SectionCard("Volume up", Icons.Rounded.Add) {
                ActionRow("Double press", OverlayAction.of(p[OverlayKeys.upDouble]), choices) { ui.set(OverlayKeys.upDouble, it.name) }
                ActionRow("Hold", OverlayAction.of(p[OverlayKeys.upLong]), choices) { ui.set(OverlayKeys.upLong, it.name) }
            }
        }
        item {
            SectionCard("Volume down", Icons.Rounded.Remove) {
                ActionRow("Double press", OverlayAction.of(p[OverlayKeys.downDouble]), choices) { ui.set(OverlayKeys.downDouble, it.name) }
                ActionRow("Hold", OverlayAction.of(p[OverlayKeys.downLong]), choices) { ui.set(OverlayKeys.downLong, it.name) }
            }
        }
    }
}
