package app.custo.feature.overlays

import androidx.compose.runtime.Composable
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun ChargingScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    ScreenScaffold("Charging animation", onBack) {
        item {
            SectionCard(subtitle = "Plays for a few seconds when you plug in, while the screen is on.") {
                SwitchRow("Enabled", null, p[OverlayKeys.chargeAnim] == true) { ui.set(OverlayKeys.chargeAnim, it) }
                Segmented(ChargeStyle.entries, enumOr(p[OverlayKeys.chargeAnimStyle], ChargeStyle.RING), { it.label }) { ui.set(OverlayKeys.chargeAnimStyle, it.name) }
                PreviewButton { ui.preview(Preview.CHARGING) }
            }
        }
    }
}
