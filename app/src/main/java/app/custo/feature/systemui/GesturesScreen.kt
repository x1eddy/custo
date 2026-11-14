package app.custo.feature.systemui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.custo.container
import app.custo.nav.Navigator
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner
import kotlin.math.roundToInt

private val GESTURES = listOf(
    Toggle(Namespace.SECURE, "camera_double_tap_power_gesture_disabled", "Double-press power for camera",
        on = "0", off = "1", default = true,
        probe = Probe.AnyOf(Probe.ResBool("config_cameraDoubleTapPowerGestureEnabled"), Probe.Present)),
    Toggle(Namespace.SECURE, "system_navigation_keys_enabled", "Swipe fingerprint for notifications",
        probe = Probe.AnyOf(Probe.ResBool("config_supportSystemNavigationKeys"), Probe.Present)),
    Toggle(Namespace.SECURE, "one_handed_mode_enabled", "One-handed mode",
        probe = Probe.AnyOf(Probe.Prop("ro.support_one_handed_mode"), Probe.Present)),
    Toggle(Namespace.SECURE, "swipe_bottom_to_notification_enabled", "Swipe down bottom edge for notifications",
        probe = Probe.AnyOf(Probe.Prop("ro.support_one_handed_mode"), Probe.Present)),
    // Unverified key: only shown if the phone already has it.
    Toggle(Namespace.SECURE, "power_button_instantly_locks", "Power button locks instantly", default = true, probe = Probe.Present),
)

private data class GestureState(val toggles: Map<String, ToggleState>, val left: Float, val right: Float)

@Composable
fun GesturesScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()
    var reload by remember { mutableIntStateOf(0) }
    val st = rememberSysLoad(ready, reload, runner) {
        GestureState(
            Probes.load(ctx, c.tweaks, GESTURES),
            c.tweaks.read(Namespace.SECURE, "back_gesture_inset_scale_left")?.toFloatOrNull() ?: 1f,
            c.tweaks.read(Namespace.SECURE, "back_gesture_inset_scale_right")?.toFloatOrNull() ?: 1f,
        )
    }

    fun act(block: suspend () -> Unit) = runner.run { block(); reload++ }

    ScreenScaffold(title = "Gestures & buttons", onBack = { nav.pop() }) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        if (st == null) { item { Hint("Reading your system…") }; return@ScreenScaffold }
        item {
            SectionCard("Gestures", Icons.Rounded.Gesture) {
                ToggleRows(GESTURES, st.toggles) { t, on -> act { c.tweaks.setToggle(t, on) } }
            }
        }
        item {
            var left by remember(st.left) { mutableStateOf(st.left) }
            var right by remember(st.right) { mutableStateOf(st.right) }
            SectionCard("Back gesture sensitivity", Icons.Rounded.SwipeLeft) {
                Text("Left edge · ${"%.2f".format(left)}×", style = MaterialTheme.typography.labelMedium)
                Slider(left, { left = (it * 20).roundToInt() / 20f }, valueRange = 0.6f..2f, onValueChangeFinished = {
                    act { c.tweaks.write(Namespace.SECURE, "back_gesture_inset_scale_left", left.toString()) }
                })
                Text("Right edge · ${"%.2f".format(right)}×", style = MaterialTheme.typography.labelMedium)
                Slider(right, { right = (it * 20).roundToInt() / 20f }, valueRange = 0.6f..2f, onValueChangeFinished = {
                    act { c.tweaks.write(Namespace.SECURE, "back_gesture_inset_scale_right", right.toString()) }
                })
                Hint("Gesture navigation only. Higher = wider edge.")
            }
        }
    }
}
