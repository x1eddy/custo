package app.custo.feature.overlays

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.custo.data.Keys
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun BatteryBarScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    ScreenScaffold("Battery bar", onBack) {
        item {
            SectionCard {
                SwitchRow("Enabled", null, p[Keys.batteryBarEnabled] == true) { ui.set(Keys.batteryBarEnabled, it) }
                Segmented(BatteryBarStyle.entries, enumOr(p[Keys.batteryBarStyle], BatteryBarStyle.TOP), { it.label }) { ui.set(Keys.batteryBarStyle, it.name) }
                if (enumOr(p[Keys.batteryBarStyle], BatteryBarStyle.TOP) == BatteryBarStyle.TOP) {
                    Segmented(BarPosition.entries, enumOr(p[OverlayKeys.batteryPosition], BarPosition.TOP), { it.label }) { ui.set(OverlayKeys.batteryPosition, it.name) }
                } else Hint("Falls back to the top edge on phones without a camera hole.")
                PrefSlider("Thickness", p[Keys.batteryBarThicknessDp] ?: 3f, 1f..8f, { "%.1f dp".format(it) }) { ui.set(Keys.batteryBarThicknessDp, it) }
                SwitchRow("Segments", "Ten blocks instead of one line", p[OverlayKeys.batterySegmented] == true) { ui.set(OverlayKeys.batterySegmented, it) }
            }
        }
        item {
            SectionCard("Color") {
                val mode = enumOr(p[OverlayKeys.batteryColorMode], BarColorMode.LEVEL)
                ChoiceChips(BarColorMode.entries, mode, { it.label }) { ui.set(OverlayKeys.batteryColorMode, it.name) }
                when (mode) {
                    BarColorMode.LEVEL -> Hint("Green charging · amber <35% · red <15%")
                    BarColorMode.GRADIENT -> Hint("Shifts from red to green as it fills.")
                    BarColorMode.CUSTOM -> ColorPicker(Color(p[OverlayKeys.batteryColor] ?: 0xFF00E5FF.toInt()), { ui.set(OverlayKeys.batteryColor, it.toArgb()) })
                    BarColorMode.ACCENT -> Unit
                }
            }
        }
        item {
            SectionCard("When") {
                val below = p[OverlayKeys.batteryBelow] ?: 100
                PrefSlider("Show below", below.toFloat(), 10f..100f, { if (it >= 100f) "Always" else "${it.toInt()} %" }, steps = 17) {
                    ui.set(OverlayKeys.batteryBelow, it.toInt())
                }
            }
        }
    }
}
