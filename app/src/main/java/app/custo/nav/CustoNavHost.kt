package app.custo.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.custo.feature.backup.BackupScreen
import app.custo.feature.home.HomeScreen
import app.custo.feature.home.ShizukuSetupScreen
import app.custo.feature.overlays.BatteryBarScreen
import app.custo.feature.overlays.ButtonsScreen
import app.custo.feature.overlays.ChargingScreen
import app.custo.feature.overlays.CornersScreen
import app.custo.feature.overlays.EdgeGesturesScreen
import app.custo.feature.overlays.EdgeLightingScreen
import app.custo.feature.overlays.IslandScreen
import app.custo.feature.overlays.NetSpeedScreen
import app.custo.feature.overlays.NotificationLightScreen
import app.custo.feature.overlays.OverlaysScreen
import app.custo.feature.overlays.SidebarScreen
import app.custo.feature.overlays.VolumePanelScreen
import app.custo.feature.systemui.QsEditorScreen
import app.custo.feature.systemui.SystemUiScreen
import app.custo.feature.themes.LiveWallpaperScreen
import app.custo.feature.themes.ThemesScreen
import app.custo.feature.themes.WallScheduleScreen
import app.custo.feature.widgets.WidgetEditorScreen
import app.custo.feature.widgets.WidgetsScreen
import app.custo.ui.components.LocalSnackbar
import app.custo.container
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class Navigator(start: Tab = Tab.HOME) {
    var tab by mutableStateOf(start)
    val stack = mutableStateListOf<Route>()
    fun push(r: Route) { stack.add(r) }
    fun pop(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false
    fun goTab(t: Tab) { stack.clear(); tab = t }
}

@Composable
fun CustoNavHost(initialRoute: Route? = null) {
    // The tab survives configuration changes; a screen opened directly starts on its own tab.
    var tabIndex by rememberSaveable { mutableIntStateOf(initialRoute?.homeTab()?.ordinal ?: 0) }
    val nav = remember { Navigator(Tab.entries.getOrElse(tabIndex) { Tab.HOME }).also { n -> initialRoute?.let { n.push(it) } } }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(nav.tab) { tabIndex = nav.tab.ordinal }

    // "Undo all" (Tweaks tab) runs in the app scope and may recreate this activity; show its result whenever it lands.
    val container = androidx.compose.ui.platform.LocalContext.current.container
    val undoResult by container.undoResult.collectAsStateWithLifecycle()
    LaunchedEffect(undoResult) {
        val msg = undoResult ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        container.consumeUndoResult()
    }

    BackHandler(enabled = nav.stack.isNotEmpty() || nav.tab != Tab.HOME) {
        if (!nav.pop()) nav.goTab(Tab.HOME)
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val top = nav.stack.lastOrNull()
            val reduceMotion = app.custo.ui.theme.LocalReduceMotion.current
            AnimatedContent(
                targetState = top ?: nav.tab,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                    } else if (targetState is Tab && initialState is Tab) {
                        val dir = if ((targetState as Tab).ordinal > (initialState as Tab).ordinal) 1 else -1
                        (slideInHorizontally(tween(260)) { dir * it / 8 } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(200)) { -dir * it / 8 } + fadeOut(tween(160)))
                    } else if (targetState is Route) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (scaleOut(targetScale = 0.94f) + fadeOut())
                    } else {
                        (scaleIn(initialScale = 0.94f) + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                    }
                },
                label = "nav",
            ) { target ->
                when (target) {
                    Tab.HOME -> HomeScreen(nav)
                    Tab.WALLPAPERS -> app.custo.feature.wallhaven.WallhavenBrowseScreen(nav, null, asTab = true)
                    Tab.WIDGETS -> WidgetsScreen(nav)
                    Tab.ICONS -> app.custo.feature.icons.IconsHubScreen(nav, asTab = true)
                    Tab.TWEAKS -> app.custo.feature.home.TweaksScreen(nav)
                    Route.WallpaperMaker -> ThemesScreen(nav, onBack = { nav.pop() })
                    Route.SystemUiHub -> SystemUiScreen(nav, onBack = { nav.pop() })
                    Route.OverlaysHub -> OverlaysScreen(nav, onBack = { nav.pop() })
                    Route.ShizukuSetup -> ShizukuSetupScreen(onBack = { nav.pop() })
                    Route.QsEditor -> QsEditorScreen(onBack = { nav.pop() })
                    Route.Backup -> BackupScreen(onBack = { nav.pop() })
                    is Route.WidgetEditor -> WidgetEditorScreen(target.appWidgetId, onBack = { nav.pop() })
                    Route.WidgetTrackers -> app.custo.feature.widgets.HabitsScreen(onBack = { nav.pop() })
                    Route.Profiles -> app.custo.feature.profiles.ProfilesScreen(nav, onBack = { nav.pop() })
                    is Route.ProfileEditor -> app.custo.feature.profiles.ProfileEditorScreen(target.profileId, onBack = { nav.pop() })
                    Route.StatusBarExtras -> app.custo.feature.systemui.StatusBarExtrasScreen(nav)
                    Route.ScreenTweaks -> app.custo.feature.systemui.ScreenTweaksScreen(nav)
                    Route.LockScreen -> app.custo.feature.systemui.LockScreenScreen(nav)
                    Route.Notifications -> app.custo.feature.systemui.NotificationsScreen(nav)
                    Route.Gestures -> app.custo.feature.systemui.GesturesScreen(nav)
                    Route.SoundHaptics -> app.custo.feature.systemui.SoundScreen(nav)
                    Route.TilePack -> app.custo.feature.systemui.TilePackScreen(nav)
                    Route.AdvancedSettings -> app.custo.feature.systemui.AdvancedScreen(nav)
                    Route.OverlayEdge -> EdgeLightingScreen(onBack = { nav.pop() })
                    Route.OverlayNotifLight -> NotificationLightScreen(onBack = { nav.pop() })
                    Route.OverlayIsland -> IslandScreen(onBack = { nav.pop() })
                    Route.OverlayBattery -> BatteryBarScreen(onBack = { nav.pop() })
                    Route.OverlayCharging -> ChargingScreen(onBack = { nav.pop() })
                    Route.OverlayCorners -> CornersScreen(onBack = { nav.pop() })
                    Route.OverlayNetSpeed -> NetSpeedScreen(onBack = { nav.pop() })
                    Route.OverlayVolume -> VolumePanelScreen(onBack = { nav.pop() })
                    Route.OverlayButtons -> ButtonsScreen(onBack = { nav.pop() })
                    Route.OverlayGestures -> EdgeGesturesScreen(onBack = { nav.pop() })
                    Route.OverlaySidebar -> SidebarScreen(onBack = { nav.pop() })
                    Route.LiveWallpaper -> LiveWallpaperScreen(onBack = { nav.pop() })
                    Route.WallSchedule -> WallScheduleScreen(onBack = { nav.pop() })
                    Route.IconsHub -> app.custo.feature.icons.IconsHubScreen(nav)
                    is Route.IconChanger -> app.custo.feature.icons.IconChangerScreen(nav, target.pkg, target.editId)
                    is Route.IconMaker -> app.custo.feature.icons.IconMakerScreen(nav, target.styleJson, target.pickApps)
                    Route.IconPacks -> app.custo.feature.icons.IconPacksScreen(nav)
                    is Route.IconPack -> app.custo.feature.icons.IconPackScreen(target.pkg, nav)
                    Route.ImportedIcons -> app.custo.feature.icons.ImportedIconsScreen(nav)
                    Route.IconShortcuts -> app.custo.feature.icons.IconShortcutsScreen(nav)
                    Route.IconThemes -> app.custo.feature.icons.IconThemesScreen(nav)
                    Route.OverlayControlCenter -> app.custo.feature.overlays.ControlCenterScreen(onBack = { nav.pop() })
                    Route.OverlayStickers -> app.custo.feature.overlays.StickersScreen(onBack = { nav.pop() })
                    Route.VideoWallpaper -> app.custo.feature.themes.VideoWallpaperScreen(onBack = { nav.pop() })
                    Route.Collage -> app.custo.feature.themes.CollageScreen(onBack = { nav.pop() })
                    Route.WidgetSets -> app.custo.feature.widgetsets.WidgetSetsScreen(nav)
                    Route.AppSettings -> app.custo.feature.settings.AppSettingsScreen(onBack = { nav.pop() })
                    is Route.WidgetSet -> app.custo.feature.widgetsets.WidgetSetScreen(target.id, nav)
                    is Route.WallhavenBrowse -> app.custo.feature.wallhaven.WallhavenBrowseScreen(nav, target.query)
                    is Route.WallpaperDetail -> app.custo.feature.wallhaven.WallpaperDetailScreen(target.id, nav)
                    Route.OfflinePacks -> app.custo.feature.wallhaven.OfflinePacksScreen(nav)
                    Route.SavedWallpapers -> app.custo.feature.wallhaven.SavedWallpapersScreen(nav)
                    else -> Unit
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SnackbarHost(snackbar)
                AnimatedVisibility(
                    visible = nav.stack.isEmpty(),
                    enter = slideInVertically { it * 2 } + fadeIn(),
                    exit = slideOutVertically { it * 2 } + fadeOut(),
                ) {
                    FloatingTabBar(nav.tab) { t -> nav.goTab(t) }
                }
            }
        }
    }
}

/** Floating pill bar: icons only, the active tab expands to show its name. */
@Composable
private fun FloatingTabBar(current: Tab, onSelect: (Tab) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 10.dp,
    ) {
        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            Tab.entries.forEach { t ->
                val selected = t == current
                val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "tabbg")
                val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabfg")
                Row(
                    Modifier.clip(CircleShape).background(bg)
                        .clickable { onSelect(t) }
                        .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(t.icon, t.label, tint = fg, modifier = Modifier.size(22.dp))
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Text(t.label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}
