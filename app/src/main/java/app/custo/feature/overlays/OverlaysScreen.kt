package app.custo.feature.overlays

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.data.Keys
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.IconBadge
import app.custo.ui.components.InfoButton
import app.custo.ui.components.OnDot
import app.custo.ui.components.PreviewStage
import app.custo.ui.components.PreviewTile
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.SwitchRow

private enum class Sheet { TINT, STATUS_TAP }

@Composable
fun OverlaysScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val ui = rememberOverlayUi()
    val p = ui.p
    val shizuku by c.shizuku.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    val a11yComponent = ComponentName(ctx, CustoAccessibilityService::class.java).flattenToString()
    fun open(action: String) = runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val accent = MaterialTheme.colorScheme.primary
    fun on(k: androidx.datastore.preferences.core.Preferences.Key<Boolean>) = p[k] == true
    val fill = Modifier.fillMaxSize()

    ScreenScaffold(
        title = "Overlays",
        actions = {
            InfoButton("Overlay service", "Overlays run as an accessibility service. Custo uses it only to draw on screen and to read volume keys and gestures. It never reads your screen.")
        },
    ) {
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Accessibility, size = 40)
                    Spacer(Modifier.width(12.dp))
                    Text("Overlay service", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OnDot(ui.running)
                }
                if (!ui.running) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shizuku == ShizukuState.READY) Button(onClick = { ui.runner.run("Overlay service enabled") { c.tweaks.enableAccessibilityService(a11yComponent) } }) { Text("Turn on") }
                    OutlinedButton(onClick = { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("Settings") }
                }
            }
        }
        item { NotificationAccessCard("Edge lighting, the notification light and the island's music and alerts need it.") }

        item { SectionLabel("Notifications") }
        item {
            PreviewTile("Edge lighting", on(Keys.edgeEnabled), Modifier.fillMaxWidth(), previewHeight = 132, onClick = { nav.push(Route.OverlayEdge) }) {
                EdgeLightPreview(
                    enumOr(p[OverlayKeys.edgeStyle], EdgeStyle.SWEEP), edgeColors(enumOr(p[Keys.edgeColorMode], EdgeColorMode.APP), OverlayLogic.colorsCsv(p[OverlayKeys.edgeGradient]), accent),
                    p[Keys.edgeWidthDp] ?: 4f, Modifier.fillMaxHeight().aspectRatio(0.5f), periodMs = p[Keys.edgeDurationMs] ?: 2500, animate = on(Keys.edgeEnabled),
                )
            }
        }
        item {
            TileRow(
                { m -> PreviewTile("Notification light", on(OverlayKeys.notifLight), m, onClick = { nav.push(Route.OverlayNotifLight) }) {
                    NotifLightPreview(10f, fill, animate = on(OverlayKeys.notifLight))
                } },
                { m -> PreviewTile("Dynamic island", on(OverlayKeys.island), m, onClick = { nav.push(Route.OverlayIsland) }) {
                    IslandPreview(fill, animate = on(OverlayKeys.island))
                } },
            )
        }

        item { SectionLabel("Battery") }
        item {
            TileRow(
                { m -> PreviewTile("Battery bar", on(Keys.batteryBarEnabled), m, onClick = { nav.push(Route.OverlayBattery) }) {
                    BatteryBarPreview(
                        enumOr(p[Keys.batteryBarStyle], BatteryBarStyle.TOP), enumOr(p[OverlayKeys.batteryPosition], BarPosition.TOP),
                        (p[Keys.batteryBarThicknessDp] ?: 3f) + 1f, p[OverlayKeys.batterySegmented] == true,
                        enumOr(p[OverlayKeys.batteryColorMode], BarColorMode.LEVEL), p[OverlayKeys.batteryColor] ?: 0xFF00E5FF.toInt(), fill, animate = on(Keys.batteryBarEnabled),
                    )
                } },
                { m -> PreviewTile("Charging", on(OverlayKeys.chargeAnim), m, onClick = { nav.push(Route.OverlayCharging) }) {
                    ChargingPreview(enumOr(p[OverlayKeys.chargeAnimStyle], ChargeStyle.RING), fill, animate = on(OverlayKeys.chargeAnim))
                } },
            )
        }

        item { SectionLabel("Screen") }
        item {
            TileRow(
                { m -> PreviewTile("Rounded corners", on(OverlayKeys.corners), m, onClick = { nav.push(Route.OverlayCorners) }) {
                    CornersPreview(p[OverlayKeys.cornersRadiusDp] ?: 16f, p[OverlayKeys.cornersColor] ?: 0xFF000000.toInt(), p[OverlayKeys.cornersTop] != false, p[OverlayKeys.cornersBottom] != false, fill)
                } },
                { m -> PreviewTile("Stickers", on(OverlayKeys.stickers), m, onClick = { nav.push(Route.OverlayStickers) }) { StickersPreview(fill) } },
            )
        }
        item {
            TileRow(
                { m -> PreviewTile("Network speed", on(OverlayKeys.netSpeed), m, onClick = { nav.push(Route.OverlayNetSpeed) }) {
                    NetSpeedPreview(NetPosition.CENTER, p[OverlayKeys.netColor] ?: 0, 13f, 0, 0, fill, animate = on(OverlayKeys.netSpeed))
                } },
                { m -> PreviewTile("Screen tint", on(OverlayKeys.tint), m, onClick = { sheet = Sheet.TINT }) {
                    TintPreview(p[OverlayKeys.tintColor] ?: 0xFF000000.toInt(), p[OverlayKeys.tintOpacity] ?: 0.3f, on(OverlayKeys.tint), fill)
                } },
            )
        }

        item { SectionLabel("Buttons & gestures") }
        item {
            TileRow(
                { m -> PreviewTile("Volume panel", on(Keys.volumePanelEnabled), m, onClick = { nav.push(Route.OverlayVolume) }) {
                    VolumePreview(enumOr(p[OverlayKeys.volumeSkin], VolumeSkin.PILL), enumOr(p[Keys.volumePanelSide], PanelSide.RIGHT), 1.4f, 0f, fill, animate = on(Keys.volumePanelEnabled))
                } },
                { m -> PreviewTile("Button actions", on(OverlayKeys.buttons), m, onClick = { nav.push(Route.OverlayButtons) }) {
                    ButtonsPreview(
                        OverlayAction.of(p[OverlayKeys.upDouble]), OverlayAction.of(p[OverlayKeys.upLong]),
                        OverlayAction.of(p[OverlayKeys.downDouble]), OverlayAction.of(p[OverlayKeys.downLong]),
                        fill.padding(vertical = 4.dp), compact = true,
                    )
                } },
            )
        }
        item {
            TileRow(
                { m -> PreviewTile("Edge gestures", on(OverlayKeys.gestures), m, onClick = { nav.push(Route.OverlayGestures) }) {
                    GesturesPreview(p[OverlayKeys.gestLeft] != false, p[OverlayKeys.gestRight] != false, p[OverlayKeys.gestCorners] == true, 14f, 0.5f, 0f, fill, animate = on(OverlayKeys.gestures))
                } },
                { m -> PreviewTile("Control Center", on(OverlayKeys.cc), m, onClick = { nav.push(Route.OverlayControlCenter) }) {
                    ControlCenterPreview(enumOr(p[OverlayKeys.ccSkin], CcSkin.DARK), CcLogic.decode(p[OverlayKeys.ccTiles]), 22f, fill, maxTiles = 8)
                } },
            )
        }
        item {
            TileRow(
                { m -> PreviewTile("Edge sidebar", on(OverlayKeys.sidebar), m, onClick = { nav.push(Route.OverlaySidebar) }) {
                    SidebarPreview(enumOr(p[OverlayKeys.sidebarSide], PanelSide.RIGHT), 0f, OverlayLogic.csv(p[OverlayKeys.sidebarApps]).size, fill, animate = on(OverlayKeys.sidebar))
                } },
                { m -> PreviewTile("Double-tap lock", on(OverlayKeys.statusTap), m, onClick = { sheet = Sheet.STATUS_TAP }) {
                    StatusTapPreview(p[OverlayKeys.statusTapWidthDp] ?: 120f, fill, animate = on(OverlayKeys.statusTap))
                } },
            )
        }
    }

    when (sheet) {
        Sheet.TINT -> QuickSheet("Screen tint", "A color layer over everything. It stacks with Android's Extra dim.", { sheet = null }) {
            PreviewStage(height = 200) {
                TintPreview(p[OverlayKeys.tintColor] ?: 0xFF000000.toInt(), p[OverlayKeys.tintOpacity] ?: 0.3f, true, Modifier.phoneSize())
            }
            SwitchRow("Enabled", null, p[OverlayKeys.tint] == true) { ui.set(OverlayKeys.tint, it) }
            PrefSlider("Strength", p[OverlayKeys.tintOpacity] ?: 0.3f, 0.05f..ScreenTintOverlay.MAX_OPACITY, ::fmtPct) { ui.set(OverlayKeys.tintOpacity, it) }
            ColorPicker(Color(p[OverlayKeys.tintColor] ?: 0xFF000000.toInt()), { ui.set(OverlayKeys.tintColor, it.toArgb()) }, showSliders = false)
        }
        Sheet.STATUS_TAP -> QuickSheet(
            "Double-tap to lock",
            "Double-tap the middle of the status bar to lock. Swipes that start on this area won't pull down the shade, so keep it narrow and swipe from the sides.",
            { sheet = null },
        ) {
            PreviewStage(height = 200) { StatusTapPreview(p[OverlayKeys.statusTapWidthDp] ?: 120f, Modifier.phoneSize()) }
            SwitchRow("Enabled", null, p[OverlayKeys.statusTap] == true) { ui.set(OverlayKeys.statusTap, it) }
            PrefSlider("Touch width", p[OverlayKeys.statusTapWidthDp] ?: 120f, 60f..300f, ::fmtDp) { ui.set(OverlayKeys.statusTapWidthDp, it) }
        }
        null -> Unit
    }
}

/** Two tiles side by side. */
@Composable
private fun TileRow(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        a(Modifier.weight(1f))
        b(Modifier.weight(1f))
    }
}

/** Bottom sheet holding a small overlay's controls (no screen of its own). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickSheet(title: String, info: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                InfoButton(title, info)
            }
            content()
        }
    }
}
