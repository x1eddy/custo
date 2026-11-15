package app.custo.feature.systemui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.SwipeDown
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.custo.container
import app.custo.nav.Navigator
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner
import app.custo.ui.components.skeletonItems
import kotlin.math.roundToInt

private val GESTURES = listOf(
    Toggle(Namespace.SECURE, "camera_double_tap_power_gesture_disabled", "Power ×2: camera",
        on = "0", off = "1", default = true, icon = Icons.Rounded.CameraAlt,
        probe = Probe.AnyOf(Probe.ResBool("config_cameraDoubleTapPowerGestureEnabled"), Probe.Present)),
    Toggle(Namespace.SECURE, "system_navigation_keys_enabled", "Fingerprint swipe", icon = Icons.Rounded.Fingerprint,
        probe = Probe.AnyOf(Probe.ResBool("config_supportSystemNavigationKeys"), Probe.Present)),
    Toggle(Namespace.SECURE, "one_handed_mode_enabled", "One-handed", icon = Icons.Rounded.PanTool,
        probe = Probe.AnyOf(Probe.Prop("ro.support_one_handed_mode"), Probe.Present)),
    Toggle(Namespace.SECURE, "swipe_bottom_to_notification_enabled", "Swipe down: shade", icon = Icons.Rounded.SwipeDown,
        probe = Probe.AnyOf(Probe.Prop("ro.support_one_handed_mode"), Probe.Present)),
    // Unverified key: only shown if the phone already has it.
    Toggle(Namespace.SECURE, "power_button_instantly_locks", "Power locks", default = true, probe = Probe.Present, icon = Icons.Rounded.Lock),
)

private const val LEFT = "back_gesture_inset_scale_left"
private const val RIGHT = "back_gesture_inset_scale_right"
private val GESTURE_KEYS = GESTURES.ids() + Ids.of(Namespace.SECURE, LEFT) + Ids.of(Namespace.SECURE, RIGHT)

private data class GestureState(val toggles: Map<String, ToggleState>, val left: Float, val right: Float)

@Composable
fun GesturesScreen(nav: Navigator) {
    val c = LocalContext.current.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()
    val st = rememberDeviceState("gestures", GESTURE_KEYS, GESTURES.probeKeys()) { v ->
        GestureState(
            v.toggles(GESTURES),
            v.v(Namespace.SECURE, LEFT)?.toFloatOrNull() ?: 1f,
            v.v(Namespace.SECURE, RIGHT)?.toFloatOrNull() ?: 1f,
        )
    }

    fun act(block: suspend () -> Unit) = runner.run { block() }

    ScreenScaffold(title = "Gestures", onBack = { nav.pop() }) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        if (st == null) { skeletonItems(2); return@ScreenScaffold }
        item {
            SectionCard("Gestures", Icons.Rounded.Gesture) {
                ToggleRows(GESTURES, st.toggles) { t, on -> act { c.tweaks.setToggle(t, on) } }
            }
        }
        item {
            var left by remember(st.left) { mutableStateOf(st.left) }
            var right by remember(st.right) { mutableStateOf(st.right) }
            SectionCard("Back swipe", Icons.Rounded.SwipeLeft, badge = {
                InfoButton("Back swipe", "How wide the edge area is that starts the back gesture. Gesture navigation only. Higher = wider.")
            }) {
                EdgeSlider(Icons.AutoMirrored.Rounded.KeyboardArrowRight, left, { left = it }) {
                    act { c.tweaks.write(Namespace.SECURE, LEFT, left.toString()) }
                }
                EdgeSlider(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, right, { right = it }) {
                    act { c.tweaks.write(Namespace.SECURE, RIGHT, right.toString()) }
                }
            }
        }
    }
}

@Composable
private fun EdgeSlider(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Slider(value, { onChange((it * 20).roundToInt() / 20f) }, valueRange = 0.6f..2f, onValueChangeFinished = onDone, modifier = Modifier.weight(1f))
        Text("%.2f×".format(value), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
    }
}
