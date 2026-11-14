package app.custo.feature.overlays

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun NetSpeedScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    ScreenScaffold("Network speed", onBack) {
        item {
            SectionCard(subtitle = "Updates every 1.5 s, only while the screen is on.") {
                SwitchRow("Enabled", null, p[OverlayKeys.netSpeed] == true) { ui.set(OverlayKeys.netSpeed, it) }
                SwitchRow("Hide when idle", "Below 1 KB/s", p[OverlayKeys.netHideIdle] != false) { ui.set(OverlayKeys.netHideIdle, it) }
            }
        }
        item {
            SectionCard("Position") {
                Segmented(NetPosition.entries, enumOr(p[OverlayKeys.netPosition], NetPosition.CENTER), { it.label }) { ui.set(OverlayKeys.netPosition, it.name) }
                PrefSlider("Nudge sideways", (p[OverlayKeys.netNudgeX] ?: 0).toFloat(), -120f..120f, ::fmtDp) { ui.set(OverlayKeys.netNudgeX, it.toInt()) }
                PrefSlider("Nudge down", (p[OverlayKeys.netNudgeY] ?: 0).toFloat(), -10f..40f, ::fmtDp) { ui.set(OverlayKeys.netNudgeY, it.toInt()) }
                Hint("Move it away from the clock and icons on your phone.")
            }
        }
        item {
            SectionCard("Text") {
                PrefSlider("Size", p[OverlayKeys.netTextSp] ?: 10f, 7f..16f, { "${it.toInt()} sp" }) { ui.set(OverlayKeys.netTextSp, it) }
                ColorPicker(Color((p[OverlayKeys.netColor] ?: 0).takeIf { it != 0 } ?: 0xFFFFFFFF.toInt()), { ui.set(OverlayKeys.netColor, it.toArgb()) })
            }
        }
    }
}
