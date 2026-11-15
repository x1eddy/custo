package app.custo.feature.systemui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.PanToolAlt
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.custo.container
import app.custo.nav.Navigator
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.ui.components.InfoButton
import app.custo.ui.components.skeletonItems
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner

private val AOD = listOf(
    Toggle(Namespace.SECURE, "doze_always_on", "Always on", icon = Icons.Rounded.DarkMode,
        probe = Probe.AnyOf(Probe.ResBool("config_dozeAlwaysOnDisplayAvailable"), Probe.Present)),
    Toggle(Namespace.SECURE, "doze_pulse_on_pick_up", "Lift to wake", icon = Icons.Rounded.PanToolAlt,
        probe = Probe.AnyOf(Probe.ResBool("config_dozePulsePickup"), Probe.Present)),
    Toggle(Namespace.SECURE, "doze_tap_gesture", "Tap to wake", icon = Icons.Rounded.TouchApp,
        probe = Probe.AnyOf(Probe.ResString("config_dozeTapSensorType"), Probe.Present)),
    Toggle(Namespace.SECURE, "doze_pulse_on_double_tap", "Double-tap", icon = Icons.Rounded.AdsClick,
        probe = Probe.AnyOf(Probe.ResString("config_dozeDoubleTapSensorType"), Probe.Present)),
)

private val LOCK = listOf(
    Toggle(Namespace.SECURE, "lockscreen_use_double_line_clock", "Big clock", default = true, icon = Icons.Rounded.AccessTime),
    Toggle(Namespace.SECURE, "lockscreen_show_controls", "Home controls", icon = Icons.Rounded.Home),
    Toggle(Namespace.SECURE, "lockscreen_show_wallet", "Wallet", icon = Icons.Rounded.AccountBalanceWallet),
)

private val LOCK_NOTIFS = listOf(
    Toggle(Namespace.SECURE, "lock_screen_show_notifications", "Show", default = true, icon = Icons.Rounded.Notifications),
    Toggle(Namespace.SECURE, "lock_screen_allow_private_notifications", "Sensitive content", default = true, icon = Icons.Rounded.VisibilityOff),
    Toggle(Namespace.SECURE, "lock_screen_show_silent_notifications", "Silent ones", default = true, icon = Icons.Rounded.NotificationsPaused),
)

/** Clock ids from AOSP (DEFAULT) and Pixel's clock plugins. Unverified outside Pixel. */
private val PIXEL_CLOCKS = listOf(
    "DEFAULT" to "Default",
    "DIGITAL_CLOCK_CALLIGRAPHY" to "Calligraphy",
    "DIGITAL_CLOCK_FLEX" to "Flex",
    "DIGITAL_CLOCK_GROWTH" to "Growth",
    "DIGITAL_CLOCK_HANDWRITTEN" to "Handwritten",
    "DIGITAL_CLOCK_INFLATE" to "Inflate",
    "DIGITAL_CLOCK_METRO" to "Metro",
    "DIGITAL_CLOCK_NUMBEROVERLAP" to "Overlap",
    "DIGITAL_CLOCK_WEATHER" to "Weather",
    "ANALOG_CLOCK_BIGNUM" to "Analog",
)

private data class LockState(val toggles: Map<String, ToggleState>, val clockJson: String?, val clockPicker: Boolean)

private const val CLOCK_KEY = "lock_screen_custom_clock_face"

private val LOCK_ALL = AOD + LOCK + LOCK_NOTIFS
private val LOCK_KEYS = LOCK_ALL.ids() + Ids.of(Namespace.SECURE, CLOCK_KEY)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockScreenScreen(nav: Navigator) {
    val c = LocalContext.current.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()
    val st = rememberDeviceState("lock", LOCK_KEYS, LOCK_ALL.probeKeys()) { v ->
        val json = v.v(Namespace.SECURE, CLOCK_KEY)
        val pixel = Build.MANUFACTURER.equals("google", ignoreCase = true)
        LockState(
            toggles = v.toggles(LOCK_ALL),
            clockJson = json,
            clockPicker = Build.VERSION.SDK_INT >= 34 && (json != null || pixel),
        )
    }

    fun act(block: suspend () -> Unit) = runner.run { block() }
    val onToggle: (Toggle, Boolean) -> Unit = { t, on -> act { c.tweaks.setToggle(t, on) } }

    ScreenScaffold(title = "Lock screen", onBack = { nav.pop() }) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        if (st == null) { skeletonItems(3); return@ScreenScaffold }
        item { SectionCard("Always-on", Icons.Rounded.DarkMode) { ToggleRows(AOD, st.toggles, onToggle) } }
        item { SectionCard("Lock screen", Icons.Rounded.Lock) { ToggleRows(LOCK, st.toggles, onToggle) } }
        item { SectionCard("Notifications", Icons.Rounded.Notifications) { ToggleRows(LOCK_NOTIFS, st.toggles, onToggle) } }
        item {
            SectionCard("Clock", Icons.Rounded.AccessTime, badge = { InfoButton("Clock", "Lock and unlock to see the new clock.") }) {
                if (!st.clockPicker) { NotOnPhoneRow("Clock style"); return@SectionCard }
                val current = SysCmd.clockIdOf(st.clockJson) ?: "DEFAULT"
                val pixel = Build.MANUFACTURER.equals("google", ignoreCase = true)
                val choices = (if (pixel) PIXEL_CLOCKS else PIXEL_CLOCKS.take(1)).let { list ->
                    if (list.none { it.first == current }) list + (current to current) else list
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { (id, label) ->
                        FilterChip(selected = current == id, onClick = {
                            act { c.tweaks.write(Namespace.SECURE, CLOCK_KEY, SysCmd.clockFaceJson(st.clockJson, id)) }
                        }, label = { Text(label) })
                    }
                }
            }
        }
    }
}
