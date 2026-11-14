package app.custo.feature.overlays

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Pref keys for the overlay features. The original keys (edge on/off, battery bar, volume panel)
 * still live in [app.custo.data.Keys] because Home reads them.
 */
object OverlayKeys {
    // Edge lighting+
    val edgeStyle = stringPreferencesKey("edge_style") // EdgeStyle
    val edgeRepeat = intPreferencesKey("edge_repeat")
    val edgeGradient = stringPreferencesKey("edge_gradient") // csv of ARGB ints
    val edgeAppRules = stringPreferencesKey("edge_app_rules") // AppRules codec
    val edgeQuiet = booleanPreferencesKey("edge_quiet")
    val edgeQuietStart = intPreferencesKey("edge_quiet_start") // minute of day
    val edgeQuietEnd = intPreferencesKey("edge_quiet_end")
    val edgeScreenOnOnly = booleanPreferencesKey("edge_screen_on_only")

    // Notification light
    val notifLight = booleanPreferencesKey("notif_light")
    val notifLightApps = stringPreferencesKey("notif_light_apps") // csv, empty = all apps
    val notifLightSizeDp = floatPreferencesKey("notif_light_size_dp")

    // Dynamic island
    val island = booleanPreferencesKey("island")
    val islandIdlePill = booleanPreferencesKey("island_idle_pill")
    val islandMedia = booleanPreferencesKey("island_media")
    val islandCharging = booleanPreferencesKey("island_charging")
    val islandNotifs = booleanPreferencesKey("island_notifs")
    val islandTimer = booleanPreferencesKey("island_timer")
    val islandCollapseSec = intPreferencesKey("island_collapse_sec")

    // Charging animation
    val chargeAnim = booleanPreferencesKey("charge_anim")
    val chargeAnimStyle = stringPreferencesKey("charge_anim_style") // ChargeStyle

    // Battery bar+
    val batteryPosition = stringPreferencesKey("battery_bar_position") // BarPosition
    val batteryColorMode = stringPreferencesKey("battery_bar_color_mode") // BarColorMode
    val batteryColor = intPreferencesKey("battery_bar_color")
    val batterySegmented = booleanPreferencesKey("battery_bar_segmented")
    val batteryBelow = intPreferencesKey("battery_bar_below") // 100 = always

    // Rounded corners
    val corners = booleanPreferencesKey("corners")
    val cornersRadiusDp = floatPreferencesKey("corners_radius_dp")
    val cornersColor = intPreferencesKey("corners_color")
    val cornersTop = booleanPreferencesKey("corners_top")
    val cornersBottom = booleanPreferencesKey("corners_bottom")

    // Network speed
    val netSpeed = booleanPreferencesKey("net_speed")
    val netPosition = stringPreferencesKey("net_position") // NetPosition
    val netNudgeX = intPreferencesKey("net_nudge_x") // dp
    val netNudgeY = intPreferencesKey("net_nudge_y") // dp
    val netTextSp = floatPreferencesKey("net_text_sp")
    val netColor = intPreferencesKey("net_color") // 0 = white
    val netHideIdle = booleanPreferencesKey("net_hide_idle")

    // Screen tint
    val tint = booleanPreferencesKey("tint")
    val tintColor = intPreferencesKey("tint_color")
    val tintOpacity = floatPreferencesKey("tint_opacity")

    // Volume panel+
    val volumeSkin = stringPreferencesKey("volume_skin") // VolumeSkin
    val volumeScale = floatPreferencesKey("volume_scale")
    val volumeOffsetY = floatPreferencesKey("volume_offset_y") // -0.4..0.4 of screen height
    val volumeHideMs = intPreferencesKey("volume_hide_ms")
    val volumeExpand = booleanPreferencesKey("volume_expand")
    val volumeLongSkip = booleanPreferencesKey("volume_long_skip")

    // Button remapper
    val buttons = booleanPreferencesKey("buttons")
    val upDouble = stringPreferencesKey("btn_up_double") // OverlayAction
    val upLong = stringPreferencesKey("btn_up_long")
    val downDouble = stringPreferencesKey("btn_down_double")
    val downLong = stringPreferencesKey("btn_down_long")

    // Edge gestures
    val gestures = booleanPreferencesKey("gestures")
    val gestLeft = booleanPreferencesKey("gest_left")
    val gestRight = booleanPreferencesKey("gest_right")
    val gestLeftTap = stringPreferencesKey("gest_left_tap")
    val gestLeftSwipe = stringPreferencesKey("gest_left_swipe")
    val gestRightTap = stringPreferencesKey("gest_right_tap")
    val gestRightSwipe = stringPreferencesKey("gest_right_swipe")
    val gestCorners = booleanPreferencesKey("gest_corners")
    val gestCornerLeft = stringPreferencesKey("gest_corner_left")
    val gestCornerRight = stringPreferencesKey("gest_corner_right")
    val gestHeight = floatPreferencesKey("gest_height") // fraction of screen height
    val gestOffsetY = floatPreferencesKey("gest_offset_y") // -0.4..0.4
    val gestWidthDp = floatPreferencesKey("gest_width_dp")
    val gestSwipeDp = floatPreferencesKey("gest_swipe_dp")
    val gestShow = booleanPreferencesKey("gest_show")

    // Double-tap status bar
    val statusTap = booleanPreferencesKey("status_tap")
    val statusTapWidthDp = floatPreferencesKey("status_tap_width_dp")

    // Edge sidebar
    val sidebar = booleanPreferencesKey("sidebar")
    val sidebarSide = stringPreferencesKey("sidebar_side") // PanelSide
    val sidebarOffsetY = floatPreferencesKey("sidebar_offset_y")
    val sidebarApps = stringPreferencesKey("sidebar_apps") // csv of packages
    val sidebarActions = stringPreferencesKey("sidebar_actions") // csv of OverlayAction
}
