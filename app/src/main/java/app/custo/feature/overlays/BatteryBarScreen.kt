package app.custo.feature.overlays

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.custo.data.Keys
import app.custo.ui.components.BrushSwatch
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.ThumbPicker

@Composable
fun BatteryBarScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    val accent = MaterialTheme.colorScheme.primary
    val style = enumOr(p[Keys.batteryBarStyle], BatteryBarStyle.TOP)
    val pos = enumOr(p[OverlayKeys.batteryPosition], BarPosition.TOP)
    val thick = p[Keys.batteryBarThicknessDp] ?: 3f
    val seg = p[OverlayKeys.batterySegmented] == true
    val mode = enumOr(p[OverlayKeys.batteryColorMode], BarColorMode.LEVEL)
    val custom = p[OverlayKeys.batteryColor] ?: 0xFF00E5FF.toInt()

    ScreenScaffold("Battery bar", onBack, actions = {
        InfoButton(
            "Battery bar",
            "Your charge level as a hairline along the screen edge, or as a ring around the camera. " +
                "Phones without a camera hole fall back to the top edge.\n\nBy level: green while charging, amber under 35 %, red under 15 %. Gradient shifts from red to green as it fills.",
        )
    }) {
        item {
            OverlayHero(p[Keys.batteryBarEnabled] == true, { ui.set(Keys.batteryBarEnabled, it) }) {
                BatteryBarPreview(style, pos, thick, seg, mode, custom, Modifier.phoneSize())
            }
        }
        item {
            SectionCard("Style", Icons.Rounded.Tune) {
                ThumbPicker(BatteryBarStyle.entries, style, { if (it == BatteryBarStyle.TOP) "Edge" else "Camera" }, { ui.set(Keys.batteryBarStyle, it.name) }, thumbHeight = 104) { s, _ ->
                    BatteryBarPreview(s, pos, thick + 2f, seg, mode, custom, Modifier.phoneSize(), animate = false)
                }
                if (style == BatteryBarStyle.TOP) {
                    Segmented(BarPosition.entries, pos, { it.label }) { ui.set(OverlayKeys.batteryPosition, it.name) }
                }
                PrefSlider("Thickness", thick, 1f..8f, { "%.1f dp".format(it) }) { ui.set(Keys.batteryBarThicknessDp, it) }
                SwitchRow("Segments", null, seg) { ui.set(OverlayKeys.batterySegmented, it) }
            }
        }
        item {
            SectionCard("Color", Icons.Rounded.Palette) {
                ThumbPicker(BarColorMode.entries, mode, { it.label }, { ui.set(OverlayKeys.batteryColorMode, it.name) }, thumbWidth = 72, thumbHeight = 56) { m, _ ->
                    BrushSwatch(
                        when (m) {
                            BarColorMode.LEVEL -> Brush.sweepGradient(listOf(Color(0xFF00E676), Color(0xFF00E676), Color(0xFFFFC400), Color(0xFFFF1744), Color(0xFF00E676)))
                            else -> barColor(m, custom, accent)
                        },
                    )
                }
                if (mode == BarColorMode.CUSTOM) ColorPicker(Color(custom), { ui.set(OverlayKeys.batteryColor, it.toArgb()) })
            }
        }
        item {
            SectionCard("When", Icons.Rounded.Schedule) {
                val below = p[OverlayKeys.batteryBelow] ?: 100
                PrefSlider("Show below", below.toFloat(), 10f..100f, { if (it >= 100f) "Always" else "${it.toInt()} %" }, steps = 17) {
                    ui.set(OverlayKeys.batteryBelow, it.toInt())
                }
            }
        }
    }
}
