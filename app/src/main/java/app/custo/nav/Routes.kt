package app.custo.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

/** The five bottom-bar tabs, picture-first. */
enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Dashboard),
    WALLPAPERS("Wallpapers", Icons.Rounded.Wallpaper),
    WIDGETS("Widgets", Icons.Rounded.Widgets),
    ICONS("Icons", Icons.Rounded.Apps),
    TWEAKS("Tweaks", Icons.Rounded.Tune),
}

/** Screens pushed on top of a tab. */
sealed interface Route {
    data object ShizukuSetup : Route
    data object QsEditor : Route
    data object Backup : Route
    data class WidgetEditor(val appWidgetId: Int) : Route
    data object WidgetTrackers : Route
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
    data object LiveWallpaper : Route
    data object WallSchedule : Route

    // Tab sub-hubs: "Make your own" wallpapers (Wallpapers tab), System UI and Overlays (Tweaks tab)
    data object WallpaperMaker : Route
    data object SystemUiHub : Route
    data object OverlaysHub : Route

    // Icons (Icons tab)
    data object IconsHub : Route
    data class IconChanger(val pkg: String? = null, val editId: String? = null) : Route
    data class IconMaker(val styleJson: String? = null, val pickApps: Boolean = false) : Route
    data object IconPacks : Route
    data class IconPack(val pkg: String) : Route
    data object ImportedIcons : Route
    data object IconShortcuts : Route
    data object IconThemes : Route
    data object OverlayControlCenter : Route
    data object OverlayStickers : Route
    data object VideoWallpaper : Route
    data object Collage : Route
    data object WidgetSets : Route
    data object AppSettings : Route
    data class WidgetSet(val id: String) : Route

    // Wallpapers from wallhaven.cc
    data class WallhavenBrowse(val query: String? = null) : Route
    data class WallpaperDetail(val id: String) : Route
    data object OfflinePacks : Route
    data object SavedWallpapers : Route
}

/**
 * The tab a screen belongs to: where Back lands from it when it is opened directly (from a widget,
 * a notification or the launcher), and which tab the bar shows while it's open.
 */
fun Route.homeTab(): Tab = when (this) {
    Route.ShizukuSetup, Route.AppSettings -> Tab.HOME
    Route.WallpaperMaker, Route.LiveWallpaper, Route.WallSchedule, Route.VideoWallpaper, Route.Collage,
    is Route.WallhavenBrowse, is Route.WallpaperDetail, Route.OfflinePacks, Route.SavedWallpapers -> Tab.WALLPAPERS
    is Route.WidgetEditor, Route.WidgetTrackers, Route.WidgetSets, is Route.WidgetSet -> Tab.WIDGETS
    Route.IconsHub, is Route.IconChanger, is Route.IconMaker, Route.IconPacks, is Route.IconPack,
    Route.ImportedIcons, Route.IconShortcuts, Route.IconThemes -> Tab.ICONS
    Route.QsEditor, Route.Backup, Route.Profiles, is Route.ProfileEditor,
    Route.SystemUiHub, Route.StatusBarExtras, Route.ScreenTweaks, Route.LockScreen, Route.Notifications,
    Route.Gestures, Route.SoundHaptics, Route.TilePack, Route.AdvancedSettings,
    Route.OverlaysHub, Route.OverlayEdge, Route.OverlayNotifLight, Route.OverlayIsland, Route.OverlayBattery,
    Route.OverlayCharging, Route.OverlayCorners, Route.OverlayNetSpeed, Route.OverlayVolume, Route.OverlayButtons,
    Route.OverlayGestures, Route.OverlaySidebar, Route.OverlayControlCenter, Route.OverlayStickers -> Tab.TWEAKS
}
