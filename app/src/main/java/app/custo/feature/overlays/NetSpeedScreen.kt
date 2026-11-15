package app.custo.feature.overlays

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun NetSpeedScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    val pos = enumOr(p[OverlayKeys.netPosition], NetPosition.CENTER)
    val color = (p[OverlayKeys.netColor] ?: 0).takeIf { it != 0 } ?: 0xFFFFFFFF.toInt()
    val size = p[OverlayKeys.netTextSp] ?: 10f
    val nx = p[OverlayKeys.netNudgeX] ?: 0
    val ny = p[OverlayKeys.netNudgeY] ?: 0
    ScreenScaffold("Network speed", onBack, actions = {
        InfoButton(
            "Network speed",
            "Live download speed in the status bar. Updates every 1.5 s, only while the screen is on. Hide when idle hides it below 1 KB/s. " +
                "Use the nudge sliders to move it away from the clock and icons on your phone.",
        )
    }) {
        item {
            OverlayHero(p[OverlayKeys.netSpeed] == true, { ui.set(OverlayKeys.netSpeed, it) }, stageHeight = 150, extra = {
                SwitchRow("Hide when idle", null, p[OverlayKeys.netHideIdle] != false) { ui.set(OverlayKeys.netHideIdle, it) }
            }) {
                // Only the top of the phone matters here: show it wide.
                NetSpeedPreview(pos, color, size, nx, ny, Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 16.dp))
            }
        }
        item {
            SectionCard("Position", Icons.Rounded.OpenWith) {
                Segmented(NetPosition.entries, pos, { it.label }) { ui.set(OverlayKeys.netPosition, it.name) }
                PrefSlider("Sideways", nx.toFloat(), -120f..120f, ::fmtDp) { ui.set(OverlayKeys.netNudgeX, it.toInt()) }
                PrefSlider("Down", ny.toFloat(), -10f..40f, ::fmtDp) { ui.set(OverlayKeys.netNudgeY, it.toInt()) }
            }
        }
        item {
            SectionCard("Text", Icons.Rounded.TextFields) {
                PrefSlider("Size", size, 7f..16f, { "${it.toInt()} sp" }) { ui.set(OverlayKeys.netTextSp, it) }
                ColorPicker(Color(color), { ui.set(OverlayKeys.netColor, it.toArgb()) })
            }
        }
    }
}
