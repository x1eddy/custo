package app.custo.feature.overlays

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.data.Keys
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.SupportBadge
import app.custo.ui.components.SwitchRow

@Composable
fun OverlaysScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val ui = rememberOverlayUi()
    val p = ui.p
    val shizuku by c.shizuku.state.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val listenerOn = remember(tick) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }

    val a11yComponent = ComponentName(ctx, CustoAccessibilityService::class.java).flattenToString()
    val listenerComponent = ComponentName(ctx, CustoNotificationListener::class.java).flattenToString()

    fun open(action: String) = runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    ScreenScaffold(title = "Overlays") {
        item {
            SectionCard(
                "Overlay service", Icons.Rounded.Accessibility,
                "Runs as an accessibility service. It never reads your screen.",
                badge = { SupportBadge(if (ui.running) "On" else "Off", ui.running) },
            ) {
                if (!ui.running) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shizuku == ShizukuState.READY) Button(onClick = { ui.runner.run("Overlay service enabled") { c.tweaks.enableAccessibilityService(a11yComponent) } }) { Text("Enable instantly") }
                    OutlinedButton(onClick = { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("Open settings") }
                }
            }
        }
        if (!listenerOn) item {
            SectionCard("Notification access", Icons.Rounded.NotificationsActive, "Needed for edge lighting, the notification light and the island.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shizuku == ShizukuState.READY) Button(onClick = { ui.runner.run("Notification access granted") { c.tweaks.allowNotificationListener(listenerComponent); kotlinx.coroutines.delay(600); tick++ } }) { Text("Grant instantly") }
                    OutlinedButton(onClick = { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text("Open settings") }
                }
            }
        }

        item { SectionLabel("Notifications") }
        item { HubCard("Edge lighting", Icons.Rounded.BorderOuter, "Glow around the screen for new notifications", p, Keys.edgeEnabled) { nav.push(Route.OverlayEdge) } }
        item { HubCard("Notification light", Icons.Rounded.RadioButtonChecked, "Pulses by the camera while something is unread", p, OverlayKeys.notifLight) { nav.push(Route.OverlayNotifLight) } }
        item { HubCard("Dynamic island", Icons.Rounded.MusicNote, "A pill for music, timers, charging and alerts", p, OverlayKeys.island) { nav.push(Route.OverlayIsland) } }

        item { SectionLabel("Battery") }
        item { HubCard("Battery bar", Icons.Rounded.BatteryChargingFull, "Charge level as a hairline", p, Keys.batteryBarEnabled) { nav.push(Route.OverlayBattery) } }
        item { HubCard("Charging animation", Icons.Rounded.Power, "Plays when you plug in", p, OverlayKeys.chargeAnim) { nav.push(Route.OverlayCharging) } }

        item { SectionLabel("Screen") }
        item { HubCard("Rounded corners", Icons.Rounded.RoundedCorner, "Round off square screen corners", p, OverlayKeys.corners) { nav.push(Route.OverlayCorners) } }
        item { HubCard("Network speed", Icons.Rounded.NetworkCheck, "Live up and down speed in the status bar", p, OverlayKeys.netSpeed) { nav.push(Route.OverlayNetSpeed) } }
        item {
            SectionCard("Screen tint", Icons.Rounded.Brightness6, "A color layer over everything. Stacks with Extra dim.") {
                SwitchRow("Enabled", null, p[OverlayKeys.tint] == true) { ui.set(OverlayKeys.tint, it) }
                PrefSlider("Strength", p[OverlayKeys.tintOpacity] ?: 0.3f, 0.05f..ScreenTintOverlay.MAX_OPACITY, ::fmtPct) { ui.set(OverlayKeys.tintOpacity, it) }
                ColorPicker(Color(p[OverlayKeys.tintColor] ?: 0xFF000000.toInt()), { ui.set(OverlayKeys.tintColor, it.toArgb()) }, showSliders = false)
            }
        }

        item { SectionLabel("Buttons & gestures") }
        item { HubCard("Volume panel", Icons.AutoMirrored.Rounded.VolumeUp, "Your own volume slider, in four styles", p, Keys.volumePanelEnabled) { nav.push(Route.OverlayVolume) } }
        item { HubCard("Button actions", Icons.Rounded.SmartButton, "Double or long press volume keys", p, OverlayKeys.buttons) { nav.push(Route.OverlayButtons) } }
        item { HubCard("Edge gestures", Icons.Rounded.Swipe, "Tap or swipe the screen edges", p, OverlayKeys.gestures) { nav.push(Route.OverlayGestures) } }
        item { HubCard("Edge sidebar", Icons.AutoMirrored.Rounded.ViewSidebar, "Favorite apps and shortcuts from a side handle", p, OverlayKeys.sidebar) { nav.push(Route.OverlaySidebar) } }
        item {
            SectionCard("Double-tap to lock", Icons.Rounded.Lock, "Double-tap the middle of the status bar to lock.") {
                SwitchRow("Enabled", null, p[OverlayKeys.statusTap] == true) { ui.set(OverlayKeys.statusTap, it) }
                PrefSlider("Touch area width", p[OverlayKeys.statusTapWidthDp] ?: 120f, 60f..300f, ::fmtDp) { ui.set(OverlayKeys.statusTapWidthDp, it) }
                Hint("Swipes that start on this area won't pull down the shade. Keep it narrow and swipe from the sides.")
            }
        }
    }
}

@Composable
private fun HubCard(title: String, icon: ImageVector, subtitle: String, p: Preferences, key: Preferences.Key<Boolean>, onClick: () -> Unit) {
    val on = p[key] == true
    SectionCard(title, icon, subtitle, badge = { SupportBadge(if (on) "On" else "Off", on) }, onClick = onClick)
}
