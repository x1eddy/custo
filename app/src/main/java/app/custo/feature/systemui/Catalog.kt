package app.custo.feature.systemui

/** Status bar icon slots that SystemUI's `icon_blacklist` understands (AOSP names). */
val STATUS_BAR_SLOTS: List<Pair<String, String>> = listOf(
    "alarm_clock" to "Alarm",
    "rotate" to "Rotation lock",
    "headset" to "Headset",
    "vpn" to "VPN",
    "nfc" to "NFC",
    "hotspot" to "Hotspot",
    "bluetooth" to "Bluetooth",
    "location" to "Location",
    "zen" to "Do not disturb",
    "volume" to "Vibrate / mute",
    "cast" to "Cast",
    "data_saver" to "Data saver",
    "managed_profile" to "Work profile",
    "airplane" to "Airplane mode",
    "mobile" to "Mobile signal",
    "wifi" to "Wi-Fi",
    "ethernet" to "Ethernet",
    "screen_record" to "Screen record",
    "sensors_off" to "Sensors off",
    "clock" to "Clock",
    "battery" to "Battery",
)

/** Known stock Quick Settings tile specs with friendly labels. Unknown specs are shown raw. */
val QS_TILES: Map<String, String> = linkedMapOf(
    "internet" to "Internet",
    "wifi" to "Wi-Fi",
    "cell" to "Mobile data",
    "bt" to "Bluetooth",
    "flashlight" to "Flashlight",
    "dnd" to "Do not disturb",
    "airplane" to "Airplane mode",
    "rotation" to "Auto-rotate",
    "battery" to "Battery saver",
    "dark" to "Dark theme",
    "night" to "Night light",
    "location" to "Location",
    "hotspot" to "Hotspot",
    "nfc" to "NFC",
    "cast" to "Cast",
    "screenrecord" to "Screen record",
    "saver" to "Data saver",
    "alarm" to "Alarm",
    "inversion" to "Color inversion",
    "color_correction" to "Color correction",
    "qr_code_scanner" to "QR scanner",
    "controls" to "Device controls",
    "wallet" to "Wallet",
    "mictoggle" to "Mic access",
    "cameratoggle" to "Camera access",
    "work" to "Work apps",
    "font_scaling" to "Font size",
    "reduce_brightness" to "Extra dim",
    "onehanded" to "One-handed mode",
)

fun qsLabel(spec: String): String = QS_TILES[spec]
    ?: if (spec.startsWith("custom(")) spec.removePrefix("custom(").removeSuffix(")").substringBefore('/').substringAfterLast('.')
        .replaceFirstChar { it.uppercase() } + " (app)"
    else spec

enum class NavMode(val overlay: String, val label: String) {
    GESTURAL("com.android.internal.systemui.navbar.gestural", "Gestures"),
    THREE_BUTTON("com.android.internal.systemui.navbar.threebutton", "3-button"),
    TWO_BUTTON("com.android.internal.systemui.navbar.twobutton", "2-button"),
}

data class AnimationScales(val window: Float, val transition: Float, val animator: Float)

enum class AnimationPreset(val label: String, val scale: Float) {
    OFF("Off", 0f), SNAPPY("Snappy", 0.5f), FAST("Fast", 0.75f), DEFAULT("Default", 1f), SMOOTH("Smooth", 1.25f), CINEMATIC("Cinematic", 2f)
}
