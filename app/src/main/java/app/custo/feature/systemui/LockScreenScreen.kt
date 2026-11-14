package app.custo.feature.systemui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Lock
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

private val AOD = listOf(
    Toggle(Namespace.SECURE, "doze_always_on", "Always-on display",
        probe = Probe.AnyOf(Probe.ResBool("config_dozeAlwaysOnDisplayAvailable"), Probe.Present)),
    Toggle(Namespace.SECURE, "doze_pulse_on_pick_up", "Lift to check phone",
        probe = Probe.AnyOf(Probe.ResBool("config_dozePulsePickup"), Probe.Present)),
    Toggle(Namespace.SECURE, "doze_tap_gesture", "Tap to check phone",
        probe = Probe.AnyOf(Probe.ResString("config_dozeTapSensorType"), Probe.Present)),
    Toggle(Namespace.SECURE, "doze_pulse_on_double_tap", "Double-tap to check phone",
        probe = Probe.AnyOf(Probe.ResString("config_dozeDoubleTapSensorType"), Probe.Present)),
)

private val LOCK = listOf(
    Toggle(Namespace.SECURE, "lockscreen_use_double_line_clock", "Big clock", "Shrinks when notifications show", default = true),
    Toggle(Namespace.SECURE, "lockscreen_show_controls", "Device controls"),
    Toggle(Namespace.SECURE, "lockscreen_show_wallet", "Wallet"),
)

private val LOCK_NOTIFS = listOf(
    Toggle(Namespace.SECURE, "lock_screen_show_notifications", "Show notifications", default = true),
    Toggle(Namespace.SECURE, "lock_screen_allow_private_notifications", "Show sensitive content", default = true),
    Toggle(Namespace.SECURE, "lock_screen_show_silent_notifications", "Show silent notifications", default = true),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockScreenScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()
    var reload by remember { mutableIntStateOf(0) }
    val st = rememberSysLoad(ready, reload, runner) {
        val json = c.tweaks.read(Namespace.SECURE, CLOCK_KEY)
        val pixel = Build.MANUFACTURER.equals("google", ignoreCase = true)
        LockState(
            toggles = Probes.load(ctx, c.tweaks, AOD + LOCK + LOCK_NOTIFS),
            clockJson = json,
            clockPicker = Build.VERSION.SDK_INT >= 34 && (json != null || pixel),
        )
    }

    fun act(block: suspend () -> Unit) = runner.run { block(); reload++ }
    val onToggle: (Toggle, Boolean) -> Unit = { t, on -> act { c.tweaks.setToggle(t, on) } }

    ScreenScaffold(title = "Lock screen", onBack = { nav.pop() }) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        if (st == null) { item { Hint("Reading your system…") }; return@ScreenScaffold }
        item { SectionCard("Always-on display", Icons.Rounded.DarkMode) { ToggleRows(AOD, st.toggles, onToggle) } }
        item { SectionCard("Lock screen", Icons.Rounded.Lock) { ToggleRows(LOCK, st.toggles, onToggle) } }
        item { SectionCard("Notifications", Icons.Rounded.Notifications) { ToggleRows(LOCK_NOTIFS, st.toggles, onToggle) } }
        item {
            SectionCard("Clock style", Icons.Rounded.AccessTime) {
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
                Hint("Lock and unlock to see it.")
            }
        }
    }
}
