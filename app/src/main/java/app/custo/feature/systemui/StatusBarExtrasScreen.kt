package app.custo.feature.systemui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.custo.container
import app.custo.nav.Navigator
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.ui.components.IconSwitchRow
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner
import app.custo.ui.components.skeletonItems

private val SB_KEYS = setOf(
    Ids.of(Namespace.SECURE, "icon_blacklist"),
    Ids.of(Namespace.SYSTEM, "status_bar_show_battery_percent"),
    Ids.of(Namespace.SECURE, "clock_seconds"),
)

private data class SbState(val supported: Boolean, val hidden: Set<String>, val batteryPct: Boolean, val seconds: Boolean)

private fun DisableFlag.icon() = when (this) {
    DisableFlag.CLOCK -> Icons.Rounded.AccessTime
    DisableFlag.NOTIFICATION_ICONS -> Icons.Rounded.Notifications
    DisableFlag.SYSTEM_ICONS -> Icons.Rounded.Wifi
    DisableFlag.NOTIFICATION_ALERTS -> Icons.Rounded.NotificationsPaused
    DisableFlag.EXPANSION -> Icons.Rounded.Lock
}

private fun DisableFlag.short() = when (this) {
    DisableFlag.CLOCK -> "Clock"
    DisableFlag.NOTIFICATION_ICONS -> "App icons"
    DisableFlag.SYSTEM_ICONS -> "System icons"
    DisableFlag.NOTIFICATION_ALERTS -> "Block pop-ups"
    DisableFlag.EXPANSION -> "Lock shade"
}

@Composable
fun StatusBarExtrasScreen(nav: Navigator) {
    val c = LocalContext.current.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()
    val st = rememberDeviceState("statusbar", SB_KEYS, setOf(ProbeKeys.DISABLE_FLAGS)) { v ->
        SbState(
            v.flag(ProbeKeys.DISABLE_FLAGS),
            v.v(Namespace.SECURE, "icon_blacklist")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
            v.v(Namespace.SYSTEM, "status_bar_show_battery_percent") == "1",
            v.v(Namespace.SECURE, "clock_seconds") == "1",
        )
    }
    // The wish lives in Custo's prefs (local, instant), not in a system setting.
    var flags by remember { mutableStateOf<Set<DisableFlag>?>(null) }
    LaunchedEffect(Unit) { flags = runCatching { c.statusBarKeeper.desired() }.getOrDefault(emptySet()) }

    fun set(new: Set<DisableFlag>) {
        val old = flags
        flags = new
        runner.run { try { c.statusBarKeeper.set(new) } catch (e: Throwable) { flags = old; throw e } }
    }

    ScreenScaffold(title = "Status bar", onBack = { nav.pop() }) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        val f = flags
        if (st == null || f == null) { skeletonItems(2); return@ScreenScaffold }
        item { LiveStatusBar(st.hidden, st.batteryPct, st.seconds, f) }
        if (!st.supported) {
            item { SectionCard("Clean mode", Icons.Rounded.CleaningServices) { NotOnPhoneRow("Status bar flags") } }
            return@ScreenScaffold
        }
        item {
            SectionCard("Clean mode", Icons.Rounded.CleaningServices, badge = {
                InfoButton("Clean mode", "Hides parts of the status bar. Comes back on its own after a restart once Shizuku is running.")
            }) {
                listOf(DisableFlag.CLOCK, DisableFlag.NOTIFICATION_ICONS, DisableFlag.SYSTEM_ICONS).forEach { fl ->
                    IconSwitchRow(fl.icon(), "Hide " + fl.short().lowercase(), fl in f) { on -> set(if (on) f + fl else f - fl) }
                }
            }
        }
        item {
            SectionCard("Shade", Icons.Rounded.NotificationsOff) {
                listOf(DisableFlag.NOTIFICATION_ALERTS, DisableFlag.EXPANSION).forEach { fl ->
                    IconSwitchRow(fl.icon(), fl.short(), fl in f) { on -> set(if (on) f + fl else f - fl) }
                }
            }
        }
        if (f.isNotEmpty()) item {
            OutlinedButton(onClick = { set(emptySet()) }) {
                Icon(Icons.Rounded.Visibility, null)
                Text("  Show all")
            }
        }
    }
}
