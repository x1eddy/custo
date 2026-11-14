package app.custo.feature.systemui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.custo.container
import app.custo.nav.Navigator
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner

internal val HEADS_UP = Toggle(Namespace.GLOBAL, "heads_up_notifications_enabled", "Pop-up notifications", default = true)

private val NOTIFS = listOf(
    HEADS_UP,
    Toggle(Namespace.SECURE, "show_notification_snooze", "Snooze button"),
    Toggle(Namespace.SECURE, "notification_history_enabled", "Notification history"),
    Toggle(Namespace.SECURE, "notification_badging", "App icon dots", default = true),
    Toggle(Namespace.SECURE, "status_bar_show_vibrate_icon", "Vibrate icon in status bar"),
)

private data class NotifState(val toggles: Map<String, ToggleState>, val dndSupported: Boolean, val dnd: DndMode)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationsScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()
    var reload by remember { mutableIntStateOf(0) }
    val st = rememberSysLoad(ready, reload, runner) {
        NotifState(Probes.load(ctx, c.tweaks, NOTIFS), c.tweaks.supportsDnd(), c.tweaks.dndMode())
    }

    fun act(msg: String? = null, block: suspend () -> Unit) = runner.run(msg) { block(); reload++ }

    ScreenScaffold(title = "Notifications", onBack = { nav.pop() }) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        if (st == null) { item { Hint("Reading your system…") }; return@ScreenScaffold }
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
                        FilterChip(selected = st.dnd == m, onClick = { act("Do Not Disturb: ${m.label}") { c.tweaks.setDnd(m) } }, label = { Text(m.label) })
                    }
                }
            }
        }
    }
}
