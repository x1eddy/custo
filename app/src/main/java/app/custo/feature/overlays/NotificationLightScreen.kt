package app.custo.feature.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationLightScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    var picking by remember { mutableStateOf(false) }
    val apps = OverlayLogic.csv(p[OverlayKeys.notifLightApps])
    val dot = p[OverlayKeys.notifLightSizeDp] ?: 8f

    ScreenScaffold("Notification light", onBack, actions = {
        InfoButton(
            "Notification light",
            "A ring pulses around the camera while you have unread notifications, each app in its own color. It stops when you clear the notification. " +
                "Phones without a camera hole get a dot at the top. Needs notification access.",
        )
    }) {
        item { NotificationAccessCard("The notification light needs it to know what's unread.") }
        item {
            OverlayHero(p[OverlayKeys.notifLight] == true, { ui.set(OverlayKeys.notifLight, it) }, { ui.preview(Preview.NOTIF_LIGHT) }, extra = {
                PrefSlider("Dot size", dot, 4f..16f, ::fmtDp) { ui.set(OverlayKeys.notifLightSizeDp, it) }
            }) {
                NotifLightPreview(dot, Modifier.phoneSize())
            }
        }
        item {
            SectionCard("Apps", Icons.Rounded.Apps, badge = {
                Text(if (apps.isEmpty()) "All" else "${apps.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }) {
                if (apps.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.forEach { AppIcon(it, 36) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = true }) { Icon(Icons.Rounded.Add, null); Text(" Choose") }
                    if (apps.isNotEmpty()) OutlinedButton(onClick = { ui.set(OverlayKeys.notifLightApps, "") }) { Text("All apps") }
                }
            }
        }
    }

    if (picking) AppPickerDialog("Light up for", apps, onDismiss = { picking = false }) {
        picking = false
        ui.set(OverlayKeys.notifLightApps, OverlayLogic.csv(it))
    }
}
