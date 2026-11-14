package app.custo.feature.systemui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.custo.container
import app.custo.nav.Navigator
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.rememberActionRunner

private data class SbState(val supported: Boolean, val flags: Set<DisableFlag>)

@Composable
fun StatusBarExtrasScreen(nav: Navigator) {
    val c = LocalContext.current.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()
    var reload by remember { mutableIntStateOf(0) }
    val st = rememberSysLoad(ready, reload, runner) {
        SbState(c.tweaks.supportsDisableFlags(), c.statusBarKeeper.desired())
    }

    fun set(flags: Set<DisableFlag>) = runner.run { c.statusBarKeeper.set(flags); reload++ }

    ScreenScaffold(title = "Status bar extras", onBack = { nav.pop() }) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        if (st == null) { item { Hint("Reading your system…") }; return@ScreenScaffold }
        if (!st.supported) {
            item { SectionCard("Clean status bar", Icons.Rounded.CleaningServices) { NotOnPhoneRow("Status bar flags") } }
            return@ScreenScaffold
        }
        val clean = listOf(DisableFlag.CLOCK, DisableFlag.NOTIFICATION_ICONS, DisableFlag.SYSTEM_ICONS)
        item {
            SectionCard("Clean status bar", Icons.Rounded.CleaningServices) {
                clean.forEach { f ->
                    SwitchRow(f.label, null, f in st.flags) { on -> set(if (on) st.flags + f else st.flags - f) }
                }
            }
        }
        item {
            SectionCard("Shade & pop-ups", Icons.Rounded.NotificationsOff) {
                SwitchRow(DisableFlag.NOTIFICATION_ALERTS.label, null, DisableFlag.NOTIFICATION_ALERTS in st.flags) { on ->
                    set(if (on) st.flags + DisableFlag.NOTIFICATION_ALERTS else st.flags - DisableFlag.NOTIFICATION_ALERTS)
                }
                SwitchRow(DisableFlag.EXPANSION.label, "Can't pull down the shade", DisableFlag.EXPANSION in st.flags) { on ->
                    set(if (on) st.flags + DisableFlag.EXPANSION else st.flags - DisableFlag.EXPANSION)
                }
            }
        }
        if (st.flags.isNotEmpty()) item {
            OutlinedButton(onClick = { set(emptySet()) }) { Text("Show everything") }
        }
        item { Hint("Comes back on its own after a restart once Shizuku is running.") }
    }
}
