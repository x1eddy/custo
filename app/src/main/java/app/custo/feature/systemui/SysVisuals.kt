package app.custo.feature.systemui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ScreenShare
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.custo.ui.components.StatusBarMock

/** Icon for each `icon_blacklist` slot. */
val SLOT_ICONS: Map<String, ImageVector> = mapOf(
    "alarm_clock" to Icons.Rounded.Alarm,
    "rotate" to Icons.Rounded.ScreenRotation,
    "headset" to Icons.Rounded.Headset,
    "vpn" to Icons.Rounded.VpnKey,
    "nfc" to Icons.Rounded.Nfc,
    "hotspot" to Icons.Rounded.WifiTethering,
    "bluetooth" to Icons.Rounded.Bluetooth,
    "location" to Icons.Rounded.LocationOn,
    "zen" to Icons.Rounded.DoNotDisturbOn,
    "volume" to Icons.Rounded.Vibration,
    "cast" to Icons.Rounded.Cast,
    "data_saver" to Icons.Rounded.DataSaverOn,
    "managed_profile" to Icons.Rounded.Work,
    "airplane" to Icons.Rounded.AirplanemodeActive,
    "mobile" to Icons.Rounded.SignalCellularAlt,
    "wifi" to Icons.Rounded.Wifi,
    "ethernet" to Icons.Rounded.SettingsEthernet,
    "screen_record" to Icons.Rounded.Videocam,
    "sensors_off" to Icons.Rounded.SensorsOff,
    "clock" to Icons.Rounded.AccessTime,
    "battery" to Icons.Rounded.BatteryFull,
)

/** Slots drawn in the preview strip (a typical set; the rest would crowd it). */
private val PREVIEW_SLOTS = listOf("alarm_clock", "volume", "bluetooth", "location", "nfc", "wifi", "mobile")

/** Status bar preview reflecting hidden icons, battery %, clock seconds and the clean-mode flags. */
@Composable
fun LiveStatusBar(
    hidden: Set<String>,
    batteryPct: Boolean,
    clockSeconds: Boolean,
    flags: Set<DisableFlag> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val systemOff = DisableFlag.SYSTEM_ICONS in flags
    val icons = if (systemOff) emptyList() else PREVIEW_SLOTS.filter { it !in hidden }.map { it to SLOT_ICONS.getValue(it) }
    StatusBarMock(
        systemIcons = icons,
        notificationIcons = if (DisableFlag.NOTIFICATION_ICONS in flags) emptyList() else listOf(Icons.AutoMirrored.Rounded.Chat, Icons.Rounded.Email),
        showClock = "clock" !in hidden && DisableFlag.CLOCK !in flags,
        seconds = clockSeconds,
        showBattery = "battery" !in hidden && !systemOff,
        batteryPercent = batteryPct,
        modifier = modifier,
    )
}

/** Icon for a Quick Settings tile spec. */
fun qsIcon(spec: String): ImageVector = when (spec) {
    "internet", "wifi" -> Icons.Rounded.Wifi
    "cell" -> Icons.Rounded.SignalCellularAlt
    "bt" -> Icons.Rounded.Bluetooth
    "flashlight" -> Icons.Rounded.FlashlightOn
    "dnd" -> Icons.Rounded.DoNotDisturbOn
    "airplane" -> Icons.Rounded.AirplanemodeActive
    "rotation" -> Icons.Rounded.ScreenRotation
    "battery" -> Icons.Rounded.BatterySaver
    "dark" -> Icons.Rounded.Brightness4
    "night" -> Icons.Rounded.NightsStay
    "location" -> Icons.Rounded.LocationOn
    "hotspot" -> Icons.Rounded.WifiTethering
    "nfc" -> Icons.Rounded.Nfc
    "cast" -> Icons.Rounded.Cast
    "screenrecord" -> Icons.Rounded.Videocam
    "saver" -> Icons.Rounded.DataSaverOn
    "alarm" -> Icons.Rounded.Alarm
    "inversion" -> Icons.Rounded.Contrast
    "color_correction" -> Icons.Rounded.Palette
    "qr_code_scanner" -> Icons.Rounded.QrCodeScanner
    "controls" -> Icons.Rounded.SmartButton
    "wallet" -> Icons.Rounded.Wallet
    "mictoggle" -> Icons.Rounded.Mic
    "cameratoggle" -> Icons.Rounded.CameraAlt
    "work" -> Icons.Rounded.Work
    "font_scaling" -> Icons.Rounded.FormatSize
    "reduce_brightness" -> Icons.Rounded.BrightnessLow
    "onehanded" -> Icons.Rounded.PanTool
    else -> when {
        spec.startsWith("custom(") -> Icons.Rounded.Widgets
        spec.contains("cast") -> Icons.AutoMirrored.Rounded.ScreenShare
        spec.contains("web") || spec.contains("dns") -> Icons.Rounded.Language
        else -> Icons.Rounded.Apps
    }
}

/**
 * Approximate Material You colors for [seed] in [style], for the preview only (SystemUI computes the
 * real ones). Returns primary, secondary, tertiary, container, surface.
 */
fun stylePreviewColors(seed: Int, style: MonetStyle?, dark: Boolean): List<Color> {
    val (h, s, _) = Tonal.toHsl(seed)
    fun c(hue: Float, sat: Float, light: Float) = Color(Tonal.fromHsl(((hue % 360f) + 360f) % 360f, sat.coerceIn(0f, 1f), light))
    val sat = when (style) {
        MonetStyle.VIBRANT, MonetStyle.FRUIT_SALAD -> 0.9f
        MonetStyle.EXPRESSIVE, MonetStyle.RAINBOW -> 0.7f
        MonetStyle.SPRITZ -> 0.18f
        MonetStyle.MONOCHROMATIC -> 0f
        MonetStyle.CONTENT -> s
        else -> s.coerceAtMost(0.6f)
    }
    val (dSec, dTer) = when (style) {
        MonetStyle.EXPRESSIVE -> 240f to 300f
        MonetStyle.RAINBOW -> 60f to 120f
        MonetStyle.FRUIT_SALAD -> -50f to 30f
        MonetStyle.VIBRANT -> 15f to 40f
        else -> 0f to 60f
    }
    val l1 = if (dark) 0.7f else 0.42f
    val l2 = if (dark) 0.3f else 0.86f
    val surf = if (dark) 0.1f else 0.96f
    return listOf(
        c(h, sat, l1),
        c(h + dSec, sat * 0.45f, l1),
        c(h + dTer, sat * 0.7f, l1),
        c(h, sat * 0.6f, l2),
        c(h, sat * 0.15f, surf),
    )
}
