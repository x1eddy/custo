package app.custo.feature.overlays

import androidx.core.net.toUri
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SupportBadge
import app.custo.ui.components.SwitchRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlCenterScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ui = rememberOverlayUi()
    val p = ui.p
    val shizuku by ctx.container.shizuku.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val canWrite = remember(tick) { Settings.System.canWrite(ctx) }
    val dndAccess = remember(tick) { ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted }

    val tiles = CcLogic.decode(p[OverlayKeys.ccTiles])
    val apps = OverlayLogic.csv(p[OverlayKeys.ccApps])
    fun setTiles(t: List<CcTile>) = ui.set(OverlayKeys.ccTiles, CcLogic.encode(t))
    fun open(i: Intent) = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    ScreenScaffold("Control Center", onBack) {
        item {
            SectionCard(subtitle = "Swipe down from the top edge to open. Tap outside or swipe up to close.") {
                SwitchRow("Enabled", null, p[OverlayKeys.cc] == true) { ui.set(OverlayKeys.cc, it) }
                Text("Pull-down spot", style = MaterialTheme.typography.labelLarge)
                Segmented(CcZone.entries, enumOr(p[OverlayKeys.ccZone], CcZone.RIGHT), { it.label }) { ui.set(OverlayKeys.ccZone, it.name) }
                PrefSlider("Spot width", p[OverlayKeys.ccZoneWidth] ?: 0.3f, 0.15f..1f, ::fmtPct) { ui.set(OverlayKeys.ccZoneWidth, it) }
                SwitchRow("Show the spot", "Tints it so you can find it", p[OverlayKeys.ccShowZone] == true) { ui.set(OverlayKeys.ccShowZone, it) }
                Hint("Swipes there won't pull down the system shade. Pull from elsewhere for that.")
                PreviewButton("Open now") {
                    if (!ui.running) ui.runner.message("Turn on the overlay service first") else ControlCenterBus.open.tryEmit(Unit)
                }
            }
        }
        item {
            SectionCard("Look") {
                Segmented(CcSkin.entries, enumOr(p[OverlayKeys.ccSkin], CcSkin.DARK), { it.label }) { ui.set(OverlayKeys.ccSkin, it.name) }
                PrefSlider("Corner radius", p[OverlayKeys.ccRadiusDp] ?: 22f, 6f..36f, ::fmtDp) { ui.set(OverlayKeys.ccRadiusDp, it) }
            }
        }
        item {
            SectionCard("Tiles", subtitle = "Top to bottom, left to right. Wide tiles take a full row.") {
                tiles.forEachIndexed { i, t ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(ControlCenterView.iconFor(t)), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("   " + t.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { setTiles(CcLogic.move(tiles, i, -1)) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { setTiles(CcLogic.move(tiles, i, 1)) }, enabled = i < tiles.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = { setTiles(tiles - t) }, enabled = tiles.size > 1) { Icon(Icons.Rounded.Close, "Remove") }
                    }
                }
                val rest = CcTile.entries - tiles.toSet()
                if (rest.isNotEmpty()) {
                    Text("Add", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rest.forEach { t -> AssistChip({ setTiles(tiles + t) }, { Text(t.label) }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) }) }
                    }
                }
            }
        }
        item {
            SectionCard("Your apps", subtitle = "Up to ${CcLogic.MAX_APPS}, shown on the Your apps tile") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { apps.forEach { AppIcon(it, 40) } }
                OutlinedButton(onClick = { picking = true }) { Text("Choose apps") }
            }
        }
        item {
            SectionCard("Permissions", subtitle = "Some tiles need a little extra access.") {
                val sh = shizuku == ShizukuState.READY
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Brightness & rotation", Modifier.weight(1f))
                    SupportBadge(if (canWrite) "Allowed" else if (sh) "Via Shizuku" else "Needed", canWrite || sh)
                }
                if (!canWrite) OutlinedButton(onClick = {
                    if (sh) ui.runner.run("Allowed") { ctx.container.tweaks.grantWriteSettings(ctx.packageName); tick++ }
                    else open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${ctx.packageName}".toUri()))
                }) { Text("Allow") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Do not disturb", Modifier.weight(1f))
                    SupportBadge(if (dndAccess) "Allowed" else if (sh) "Via Shizuku" else "Needed", dndAccess || sh)
                }
                if (!dndAccess) OutlinedButton(onClick = { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) { Text("Allow") }
                Hint("Music controls use notification access, same as the dynamic island.")
            }
        }
    }

    if (picking) AppPickerDialog("Control Center apps", apps, max = CcLogic.MAX_APPS, onDismiss = { picking = false }) {
        picking = false
        ui.set(OverlayKeys.ccApps, OverlayLogic.csv(it))
    }
}
