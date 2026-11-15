package app.custo.feature.overlays

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun CornersScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    val radius = p[OverlayKeys.cornersRadiusDp] ?: 16f
    val color = p[OverlayKeys.cornersColor] ?: 0xFF000000.toInt()
    val top = p[OverlayKeys.cornersTop] != false
    val bottom = p[OverlayKeys.cornersBottom] != false
    ScreenScaffold("Rounded corners", onBack, actions = {
        InfoButton("Rounded corners", "Paints small masks in the screen corners, also over the status and navigation bars.")
    }) {
        item {
            OverlayHero(p[OverlayKeys.corners] == true, { ui.set(OverlayKeys.corners, it) }) {
                CornersPreview(radius, color, top, bottom, Modifier.phoneSize())
            }
        }
        item {
            SectionCard("Shape", Icons.Rounded.Tune) {
                PrefSlider("Radius", radius, 4f..48f, ::fmtDp) { ui.set(OverlayKeys.cornersRadiusDp, it) }
                SwitchRow("Top", null, top) { ui.set(OverlayKeys.cornersTop, it) }
                SwitchRow("Bottom", null, bottom) { ui.set(OverlayKeys.cornersBottom, it) }
            }
        }
        item {
            SectionCard("Color", Icons.Rounded.Palette) {
                ColorPicker(Color(color), { ui.set(OverlayKeys.cornersColor, it.toArgb()) })
            }
        }
    }
}
