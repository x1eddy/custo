package app.custo.feature.overlays

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BorderVertical
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun EdgeGesturesScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    val all = OverlayAction.entries
    fun act(key: androidx.datastore.preferences.core.Preferences.Key<String>, default: OverlayAction) = p[key]?.let { OverlayAction.of(it) } ?: default
    val left = p[OverlayKeys.gestLeft] != false
    val right = p[OverlayKeys.gestRight] != false
    val corners = p[OverlayKeys.gestCorners] == true
    val width = p[OverlayKeys.gestWidthDp] ?: 14f
    val height = p[OverlayKeys.gestHeight] ?: 0.4f
    val offset = p[OverlayKeys.gestOffsetY] ?: 0f

    ScreenScaffold("Edge gestures", onBack, actions = {
        InfoButton(
            "Edge gestures",
            "Thin strips on the screen edges. Touches on them don't reach the app below. Bottom corners: swipe up or in from a corner. A shorter swipe distance is more sensitive.",
        )
    }) {
        item {
            OverlayHero(p[OverlayKeys.gestures] == true, { ui.set(OverlayKeys.gestures, it) }, extra = {
                SwitchRow("Show strips", null, p[OverlayKeys.gestShow] == true) { ui.set(OverlayKeys.gestShow, it) }
            }) {
                GesturesPreview(left, right, corners, width, height, offset, Modifier.phoneSize())
            }
        }
        item {
            SectionCard("Left edge", Icons.Rounded.BorderVertical, badge = { androidx.compose.material3.Switch(left, { ui.set(OverlayKeys.gestLeft, it) }) }) {
                if (left) {
                    ActionRow("Swipe in", act(OverlayKeys.gestLeftSwipe, OverlayAction.BACK), all) { ui.set(OverlayKeys.gestLeftSwipe, it.name) }
                    ActionRow("Tap", act(OverlayKeys.gestLeftTap, OverlayAction.NONE), all) { ui.set(OverlayKeys.gestLeftTap, it.name) }
                }
            }
        }
        item {
            SectionCard("Right edge", Icons.Rounded.BorderVertical, badge = { androidx.compose.material3.Switch(right, { ui.set(OverlayKeys.gestRight, it) }) }) {
                if (right) {
                    ActionRow("Swipe in", act(OverlayKeys.gestRightSwipe, OverlayAction.BACK), all) { ui.set(OverlayKeys.gestRightSwipe, it.name) }
                    ActionRow("Tap", act(OverlayKeys.gestRightTap, OverlayAction.NONE), all) { ui.set(OverlayKeys.gestRightTap, it.name) }
                }
            }
        }
        item {
            SectionCard("Bottom corners", Icons.Rounded.OpenWith, badge = { androidx.compose.material3.Switch(corners, { ui.set(OverlayKeys.gestCorners, it) }) }) {
                if (corners) {
                    ActionRow("Left", act(OverlayKeys.gestCornerLeft, OverlayAction.NONE), all) { ui.set(OverlayKeys.gestCornerLeft, it.name) }
                    ActionRow("Right", act(OverlayKeys.gestCornerRight, OverlayAction.NONE), all) { ui.set(OverlayKeys.gestCornerRight, it.name) }
                }
            }
        }
        item {
            SectionCard("Size", Icons.Rounded.Straighten) {
                PrefSlider("Width", width, 6f..32f, ::fmtDp) { ui.set(OverlayKeys.gestWidthDp, it) }
                PrefSlider("Height", height, 0.15f..0.9f, ::fmtPct) { ui.set(OverlayKeys.gestHeight, it) }
                PrefSlider("Position", -offset, -0.35f..0.35f, { if (it == 0f) "Middle" else "%+d".format((it * 100).toInt()) }) {
                    ui.set(OverlayKeys.gestOffsetY, -it)
                }
                PrefSlider("Swipe distance", p[OverlayKeys.gestSwipeDp] ?: 40f, 16f..120f, ::fmtDp) { ui.set(OverlayKeys.gestSwipeDp, it) }
            }
        }
    }
}
