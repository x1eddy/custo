package app.custo.feature.overlays

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.ThumbPicker

@Composable
fun ChargingScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    val style = enumOr(p[OverlayKeys.chargeAnimStyle], ChargeStyle.RING)
    ScreenScaffold("Charging", onBack, actions = {
        InfoButton("Charging animation", "Plays for a few seconds when you plug in, while the screen is on.")
    }) {
        item {
            OverlayHero(p[OverlayKeys.chargeAnim] == true, { ui.set(OverlayKeys.chargeAnim, it) }, { ui.preview(Preview.CHARGING) }) {
                ChargingPreview(style, Modifier.phoneSize())
            }
        }
        item {
            SectionCard("Style", Icons.Rounded.Bolt) {
                ThumbPicker(ChargeStyle.entries, style, { it.label }, { ui.set(OverlayKeys.chargeAnimStyle, it.name) }, thumbHeight = 104) { s, sel ->
                    ChargingPreview(s, Modifier.phoneSize(), animate = sel)
                }
            }
        }
    }
}
