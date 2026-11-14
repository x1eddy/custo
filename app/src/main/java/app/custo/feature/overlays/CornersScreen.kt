package app.custo.feature.overlays

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun CornersScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    ScreenScaffold("Rounded corners", onBack) {
        item {
            SectionCard(subtitle = "Paints small masks in the corners, also over the status and navigation bars.") {
                SwitchRow("Enabled", null, p[OverlayKeys.corners] == true) { ui.set(OverlayKeys.corners, it) }
                PrefSlider("Radius", p[OverlayKeys.cornersRadiusDp] ?: 16f, 4f..48f, ::fmtDp) { ui.set(OverlayKeys.cornersRadiusDp, it) }
                SwitchRow("Top corners", null, p[OverlayKeys.cornersTop] != false) { ui.set(OverlayKeys.cornersTop, it) }
                SwitchRow("Bottom corners", null, p[OverlayKeys.cornersBottom] != false) { ui.set(OverlayKeys.cornersBottom, it) }
            }
        }
        item {
            SectionCard("Color") {
                ColorPicker(Color(p[OverlayKeys.cornersColor] ?: 0xFF000000.toInt()), { ui.set(OverlayKeys.cornersColor, it.toArgb()) })
            }
        }
    }
}
