package app.custo.feature.systemui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.custo.container
import app.custo.nav.Navigator
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner
import app.custo.ui.components.skeletonItems

internal val HEADS_UP = Toggle(Namespace.GLOBAL, "heads_up_notifications_enabled", "Pop-ups", default = true, icon = Icons.Rounded.NotificationsActive)

private val NOTIFS = listOf(
    HEADS_UP,
    Toggle(Namespace.SECURE, "show_notification_snooze", "Snooze", icon = Icons.Rounded.Snooze),
    Toggle(Namespace.SECURE, "notification_history_enabled", "History", icon = Icons.Rounded.History),
    Toggle(Namespace.SECURE, "notification_badging", "Icon dots", default = true, icon = Icons.Rounded.Circle),
    Toggle(Namespace.SECURE, "status_bar_show_vibrate_icon", "Vibrate icon", icon = Icons.Rounded.Vibration),
)

private val ZEN = Ids.of(Namespace.GLOBAL, "zen_mode")
private val NOTIF_KEYS = NOTIFS.ids() + ZEN
private val NOTIF_PROBES = NOTIFS.probeKeys() + ProbeKeys.DND

private data class NotifState(val toggles: Map<String, ToggleState>, val dndSupported: Boolean, val dnd: DndMode)

private fun DndMode.icon() = when (this) {
    DndMode.OFF -> Icons.Rounded.Notifications
    DndMode.PRIORITY -> Icons.Rounded.Star
    DndMode.ALARMS -> Icons.Rounded.Alarm
    DndMode.SILENCE -> Icons.Rounded.NotificationsOff
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationsScreen(nav: Navigator) {
    val c = LocalContext.current.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()
    val st = rememberDeviceState("notifications", NOTIF_KEYS, NOTIF_PROBES) { v ->
        NotifState(v.toggles(NOTIFS), v.flag(ProbeKeys.DND), DndMode.ofZen(v.v(Namespace.GLOBAL, "zen_mode")))
    }

    fun act(msg: String? = null, block: suspend () -> Unit) = runner.run(msg) { block() }

    ScreenScaffold(title = "Notifications", onBack = { nav.pop() }) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        if (st == null) { skeletonItems(2); return@ScreenScaffold }
        item {
            SectionCard("Notifications", Icons.Rounded.Notifications) {
                ToggleRows(NOTIFS, st.toggles) { t, on -> act { c.tweaks.setToggle(t, on) } }
            }
        }
        item {
            SectionCard("Do Not Disturb", Icons.Rounded.DoNotDisturbOn) {
                if (!st.dndSupported) { NotOnPhoneRow("Do Not Disturb"); return@SectionCard }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DndMode.entries.forEach { m ->
                        FilterChip(
                            selected = st.dnd == m,
                            onClick = { act { c.tweaks.setDnd(m) } },
                            label = { Text(m.label) },
                            leadingIcon = { Icon(m.icon(), null, Modifier.size(FilterChipDefaults.IconSize)) },
                        )
                    }
                }
            }
        }
    }
}
