package app.custo.feature.overlays

import androidx.compose.runtime.Composable
import app.custo.data.Keys
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun VolumePanelScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    ScreenScaffold("Volume panel", onBack) {
        item {
            SectionCard {
                SwitchRow("Enabled", "Calls keep the system panel", p[Keys.volumePanelEnabled] == true) { ui.set(Keys.volumePanelEnabled, it) }
                PreviewButton { ui.preview(Preview.VOLUME) }
            }
        }
        item {
            SectionCard("Look") {
                ChoiceChips(VolumeSkin.entries, enumOr(p[OverlayKeys.volumeSkin], VolumeSkin.PILL), { it.label }) { ui.set(OverlayKeys.volumeSkin, it.name) }
                PrefSlider("Size", p[OverlayKeys.volumeScale] ?: 1f, 0.6f..1.6f, ::fmtPct) { ui.set(OverlayKeys.volumeScale, it) }
                Segmented(PanelSide.entries, enumOr(p[Keys.volumePanelSide], PanelSide.RIGHT), { it.label }) { ui.set(Keys.volumePanelSide, it.name) }
                PrefSlider("Height on screen", -(p[OverlayKeys.volumeOffsetY] ?: 0f), -0.35f..0.35f, { if (it == 0f) "Middle" else "%+d".format((it * 100).toInt()) }) {
                    ui.set(OverlayKeys.volumeOffsetY, -it)
                }
                PrefSlider("Hide after", (p[OverlayKeys.volumeHideMs] ?: 2200) / 1000f, 1f..6f, ::fmtSec) { ui.set(OverlayKeys.volumeHideMs, (it * 1000).toInt()) }
            }
        }
        item {
            SectionCard("Extras") {
                SwitchRow("Tap to show all sliders", "Media, ring and alarm side by side", p[OverlayKeys.volumeExpand] != false) { ui.set(OverlayKeys.volumeExpand, it) }
                SwitchRow("Hold to skip tracks", "Hold volume up for next, down for previous, while music plays", p[OverlayKeys.volumeLongSkip] == true) { ui.set(OverlayKeys.volumeLongSkip, it) }
                Hint("Button actions, if set, take priority over holding to skip.")
            }
        }
    }
}
