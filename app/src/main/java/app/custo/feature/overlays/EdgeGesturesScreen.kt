package app.custo.feature.overlays

import androidx.compose.runtime.Composable
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun EdgeGesturesScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    val all = OverlayAction.entries
    fun act(key: androidx.datastore.preferences.core.Preferences.Key<String>, default: OverlayAction) = p[key]?.let { OverlayAction.of(it) } ?: default

    ScreenScaffold("Edge gestures", onBack) {
        item {
            SectionCard(subtitle = "Thin strips on the screen edges. Touches on them don't reach the app below.") {
                SwitchRow("Enabled", null, p[OverlayKeys.gestures] == true) { ui.set(OverlayKeys.gestures, it) }
                SwitchRow("Show strips", "Tint them so you can see where they are", p[OverlayKeys.gestShow] == true) { ui.set(OverlayKeys.gestShow, it) }
            }
        }
        item {
            SectionCard("Left edge") {
                SwitchRow("On", null, p[OverlayKeys.gestLeft] != false) { ui.set(OverlayKeys.gestLeft, it) }
                ActionRow("Swipe in", act(OverlayKeys.gestLeftSwipe, OverlayAction.BACK), all) { ui.set(OverlayKeys.gestLeftSwipe, it.name) }
                ActionRow("Tap", act(OverlayKeys.gestLeftTap, OverlayAction.NONE), all) { ui.set(OverlayKeys.gestLeftTap, it.name) }
            }
        }
        item {
            SectionCard("Right edge") {
                SwitchRow("On", null, p[OverlayKeys.gestRight] != false) { ui.set(OverlayKeys.gestRight, it) }
                ActionRow("Swipe in", act(OverlayKeys.gestRightSwipe, OverlayAction.BACK), all) { ui.set(OverlayKeys.gestRightSwipe, it.name) }
                ActionRow("Tap", act(OverlayKeys.gestRightTap, OverlayAction.NONE), all) { ui.set(OverlayKeys.gestRightTap, it.name) }
            }
        }
        item {
            SectionCard("Bottom corners") {
                SwitchRow("On", "Swipe up or in from a bottom corner", p[OverlayKeys.gestCorners] == true) { ui.set(OverlayKeys.gestCorners, it) }
                ActionRow("Left corner", act(OverlayKeys.gestCornerLeft, OverlayAction.NONE), all) { ui.set(OverlayKeys.gestCornerLeft, it.name) }
                ActionRow("Right corner", act(OverlayKeys.gestCornerRight, OverlayAction.NONE), all) { ui.set(OverlayKeys.gestCornerRight, it.name) }
            }
        }
        item {
            SectionCard("Size") {
                PrefSlider("Width", p[OverlayKeys.gestWidthDp] ?: 14f, 6f..32f, ::fmtDp) { ui.set(OverlayKeys.gestWidthDp, it) }
                PrefSlider("Height", p[OverlayKeys.gestHeight] ?: 0.4f, 0.15f..0.9f, ::fmtPct) { ui.set(OverlayKeys.gestHeight, it) }
                PrefSlider("Position", -(p[OverlayKeys.gestOffsetY] ?: 0f), -0.35f..0.35f, { if (it == 0f) "Middle" else "%+d".format((it * 100).toInt()) }) {
                    ui.set(OverlayKeys.gestOffsetY, -it)
                }
                PrefSlider("Swipe distance", p[OverlayKeys.gestSwipeDp] ?: 40f, 16f..120f, ::fmtDp) { ui.set(OverlayKeys.gestSwipeDp, it) }
                Hint("Shorter swipe distance is more sensitive.")
            }
        }
    }
}
