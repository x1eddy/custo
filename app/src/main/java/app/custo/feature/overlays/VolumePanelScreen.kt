package app.custo.feature.overlays

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.custo.data.Keys
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.ThumbPicker

@Composable
fun VolumePanelScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    val skin = enumOr(p[OverlayKeys.volumeSkin], VolumeSkin.PILL)
    val side = enumOr(p[Keys.volumePanelSide], PanelSide.RIGHT)
    val scale = p[OverlayKeys.volumeScale] ?: 1f
    val offset = -(p[OverlayKeys.volumeOffsetY] ?: 0f)
    ScreenScaffold("Volume panel", onBack, actions = {
        InfoButton(
            "Volume panel",
            "Replaces the volume slider with your own. Calls keep the system panel. Tap the panel to show media, ring and alarm side by side. " +
                "Hold to skip: hold volume up for next, down for previous, while music plays. Button actions, if set, take priority.",
        )
    }) {
        item {
            OverlayHero(p[Keys.volumePanelEnabled] == true, { ui.set(Keys.volumePanelEnabled, it) }, { ui.preview(Preview.VOLUME) }) {
                VolumePreview(skin, side, scale, -offset, Modifier.phoneSize())
            }
        }
        item {
            SectionCard("Look", Icons.Rounded.Palette) {
                ThumbPicker(VolumeSkin.entries, skin, { it.label }, { ui.set(OverlayKeys.volumeSkin, it.name) }, thumbHeight = 104) { s, sel ->
                    VolumePreview(s, PanelSide.RIGHT, 1.5f, 0f, Modifier.phoneSize(), animate = sel)
                }
                PrefSlider("Size", scale, 0.6f..1.6f, ::fmtPct) { ui.set(OverlayKeys.volumeScale, it) }
                Segmented(PanelSide.entries, side, { it.label }) { ui.set(Keys.volumePanelSide, it.name) }
                PrefSlider("Height", offset, -0.35f..0.35f, { if (it == 0f) "Middle" else "%+d".format((it * 100).toInt()) }) {
                    ui.set(OverlayKeys.volumeOffsetY, -it)
                }
                PrefSlider("Hide after", (p[OverlayKeys.volumeHideMs] ?: 2200) / 1000f, 1f..6f, ::fmtSec) { ui.set(OverlayKeys.volumeHideMs, (it * 1000).toInt()) }
            }
        }
        item {
            SectionCard("Extras", Icons.Rounded.Tune) {
                SwitchRow("Tap for all sliders", null, p[OverlayKeys.volumeExpand] != false) { ui.set(OverlayKeys.volumeExpand, it) }
                SwitchRow("Hold to skip tracks", null, p[OverlayKeys.volumeLongSkip] == true) { ui.set(OverlayKeys.volumeLongSkip, it) }
            }
        }
    }
}
