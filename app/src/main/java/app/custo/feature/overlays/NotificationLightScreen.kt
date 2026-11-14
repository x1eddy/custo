package app.custo.feature.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationLightScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    var picking by remember { mutableStateOf(false) }
    val apps = OverlayLogic.csv(p[OverlayKeys.notifLightApps])

    ScreenScaffold("Notification light", onBack) {
        item {
            SectionCard(subtitle = "A ring pulses around the camera while you have unread notifications. Phones without a camera hole get a dot at the top.") {
                SwitchRow("Enabled", "Needs notification access", p[OverlayKeys.notifLight] == true) { ui.set(OverlayKeys.notifLight, it) }
                PrefSlider("Dot size", p[OverlayKeys.notifLightSizeDp] ?: 8f, 4f..16f, ::fmtDp) { ui.set(OverlayKeys.notifLightSizeDp, it) }
                PreviewButton { ui.preview(Preview.NOTIF_LIGHT) }
                Hint("Each app pulses in its own color. It stops when you clear the notification.")
            }
        }
        item {
            SectionCard("Apps", subtitle = if (apps.isEmpty()) "All apps" else "${apps.size} chosen") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.forEach { AppIcon(it, 36) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = true }) { Text("Choose apps") }
                    if (apps.isNotEmpty()) OutlinedButton(onClick = { ui.set(OverlayKeys.notifLightApps, "") }) { Text("Use all apps") }
                }
            }
        }
    }

    if (picking) AppPickerDialog("Light up for", apps, onDismiss = { picking = false }) {
        picking = false
        ui.set(OverlayKeys.notifLightApps, OverlayLogic.csv(it))
    }
}
