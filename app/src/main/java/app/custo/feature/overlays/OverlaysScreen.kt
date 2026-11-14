package app.custo.feature.overlays

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.data.Keys
import app.custo.nav.Navigator
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.SupportBadge
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.rememberActionRunner
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverlaysScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val runner = rememberActionRunner()
    val p by c.prefs.data.collectAsStateWithLifecycle(emptyPreferences())
    val running by OverlayBus.serviceRunning.collectAsStateWithLifecycle()
    val shizuku by c.shizuku.state.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val listenerOn = remember(tick) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }

    val a11yComponent = ComponentName(ctx, CustoAccessibilityService::class.java).flattenToString()
    val listenerComponent = ComponentName(ctx, CustoNotificationListener::class.java).flattenToString()

    fun <T> set(key: Preferences.Key<T>, v: T) = runner.run { c.prefs.set(key, v) }
    fun open(action: String) = runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    ScreenScaffold(title = "Overlays") {
        item {
            SectionCard(
                "Overlay service", Icons.Rounded.Accessibility,
                "Runs as an accessibility service. It never reads your screen.",
                badge = { SupportBadge(if (running) "On" else "Off", running) },
            ) {
                if (!running) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shizuku == ShizukuState.READY) Button(onClick = { runner.run("Overlay service enabled") { c.tweaks.enableAccessibilityService(a11yComponent) } }) { Text("Enable instantly") }
                    OutlinedButton(onClick = { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("Open settings") }
                }
            }
        }
        if (!listenerOn) item {
            SectionCard("Notification access", Icons.Rounded.NotificationsActive, "So edge lighting knows when something arrives.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shizuku == ShizukuState.READY) Button(onClick = { runner.run("Notification access granted") { c.tweaks.allowNotificationListener(listenerComponent); kotlinx.coroutines.delay(600); tick++ } }) { Text("Grant instantly") }
                    OutlinedButton(onClick = { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text("Open settings") }
                }
            }
        }

        item { SectionLabel("Edge lighting") }
        item {
            SectionCard("Glow around the screen on new notifications") {
                SwitchRow("Enabled", null, p[Keys.edgeEnabled] == true) { set(Keys.edgeEnabled, it) }
                val mode = p[Keys.edgeColorMode] ?: EdgeColorMode.APP.name
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EdgeColorMode.entries.forEach { m -> FilterChip(mode == m.name, { set(Keys.edgeColorMode, m.name) }, { Text(m.label) }) }
                }
                var width by remember(p[Keys.edgeWidthDp]) { mutableStateOf(p[Keys.edgeWidthDp] ?: 4f) }
                Text("Thickness · ${width.roundToInt()} dp", style = MaterialTheme.typography.labelMedium)
                Slider(width, { width = it }, valueRange = 2f..14f, onValueChangeFinished = { set(Keys.edgeWidthDp, width) })
                var dur by remember(p[Keys.edgeDurationMs]) { mutableStateOf((p[Keys.edgeDurationMs] ?: 2500).toFloat()) }
                Text("Duration · ${"%.1f".format(dur / 1000)} s", style = MaterialTheme.typography.labelMedium)
                Slider(dur, { dur = it }, valueRange = 1000f..6000f, onValueChangeFinished = { set(Keys.edgeDurationMs, dur.roundToInt()) })
                FilledTonalButton(onClick = {
                    if (!running) runner.message("Turn on the overlay service first") else OverlayBus.edge.tryEmit(null)
                }) { Text("Preview") }
            }
        }

        item { SectionLabel("Battery bar") }
        item {
            SectionCard("Charge level as a hairline") {
                SwitchRow("Enabled", null, p[Keys.batteryBarEnabled] == true) { set(Keys.batteryBarEnabled, it) }
                val style = p[Keys.batteryBarStyle] ?: BatteryBarStyle.TOP.name
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    BatteryBarStyle.entries.forEachIndexed { i, s ->
                        SegmentedButton(style == s.name, { set(Keys.batteryBarStyle, s.name) }, SegmentedButtonDefaults.itemShape(i, BatteryBarStyle.entries.size)) { Text(s.label) }
                    }
                }
                var t by remember(p[Keys.batteryBarThicknessDp]) { mutableStateOf(p[Keys.batteryBarThicknessDp] ?: 3f) }
                Text("Thickness · ${"%.1f".format(t)} dp", style = MaterialTheme.typography.labelMedium)
                Slider(t, { t = it }, valueRange = 1f..8f, onValueChangeFinished = { set(Keys.batteryBarThicknessDp, t) })
                Hint("Green charging · amber <35% · red <15%")
            }
        }

        item { SectionLabel("Volume panel") }
        item {
            SectionCard("A slim, draggable volume slider") {
                SwitchRow("Enabled", "Calls keep the system panel", p[Keys.volumePanelEnabled] == true) { set(Keys.volumePanelEnabled, it) }
                val side = p[Keys.volumePanelSide] ?: PanelSide.RIGHT.name
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PanelSide.entries.forEachIndexed { i, s ->
                        SegmentedButton(side == s.name, { set(Keys.volumePanelSide, s.name) }, SegmentedButtonDefaults.itemShape(i, PanelSide.entries.size)) { Text(s.label) }
                    }
                }
            }
        }
    }
}
