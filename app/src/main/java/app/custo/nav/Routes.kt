package app.custo.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Dashboard),
    SYSTEM("System UI", Icons.Rounded.Tune),
    THEMES("Themes", Icons.Rounded.Palette),
    WIDGETS("Widgets", Icons.Rounded.Widgets),
    OVERLAYS("Overlays", Icons.Rounded.Layers),
}

/** Screens pushed on top of a tab. */
sealed interface Route {
    data object ShizukuSetup : Route
    data object QsEditor : Route
    data object Backup : Route
    data class WidgetEditor(val appWidgetId: Int) : Route
    data object Profiles : Route
    data class ProfileEditor(val profileId: String?) : Route

    // System UI hub sub-screens
    data object StatusBarExtras : Route
    data object ScreenTweaks : Route
    data object LockScreen : Route
    data object Notifications : Route
    data object Gestures : Route
    data object SoundHaptics : Route
    data object TilePack : Route
    data object AdvancedSettings : Route
    data object OverlayEdge : Route
    data object OverlayNotifLight : Route
    data object OverlayIsland : Route
    data object OverlayBattery : Route
    data object OverlayCharging : Route
    data object OverlayCorners : Route
    data object OverlayNetSpeed : Route
    data object OverlayVolume : Route
    data object OverlayButtons : Route
    data object OverlayGestures : Route
    data object OverlaySidebar : Route
}
