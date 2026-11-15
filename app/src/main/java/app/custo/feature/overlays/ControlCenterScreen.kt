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
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ThumbPicker
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SwipeDown
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

    val skin = enumOr(p[OverlayKeys.ccSkin], CcSkin.DARK)
    val radius = p[OverlayKeys.ccRadiusDp] ?: 22f
    ScreenScaffold("Control Center", onBack, actions = {
        InfoButton(
            "Control Center",
            "Swipe down from the pull-down spot on the top edge to open it. Tap outside or swipe up to close. " +
                "Swipes there won't pull down the system shade, so pull from elsewhere for that. Show the spot tints it so you can find it.\n\n" +
                "Brightness and rotation tiles need permission to change system settings; Do not disturb needs its own access. " +
                "Music controls use notification access, same as the dynamic island.",
        )
    }) {
        item {
            OverlayHero(p[OverlayKeys.cc] == true, { ui.set(OverlayKeys.cc, it) }, {
                if (!ui.running) ui.runner.message("Turn on the overlay service first") else ControlCenterBus.open.tryEmit(Unit)
            }, stageHeight = 260) {
                ControlCenterPreview(skin, tiles, radius, Modifier.phoneSize())
            }
        }
        item {
            SectionCard("Pull-down spot", Icons.Rounded.SwipeDown) {
                Segmented(CcZone.entries, enumOr(p[OverlayKeys.ccZone], CcZone.RIGHT), { it.label }) { ui.set(OverlayKeys.ccZone, it.name) }
                PrefSlider("Width", p[OverlayKeys.ccZoneWidth] ?: 0.3f, 0.15f..1f, ::fmtPct) { ui.set(OverlayKeys.ccZoneWidth, it) }
                SwitchRow("Show the spot", null, p[OverlayKeys.ccShowZone] == true) { ui.set(OverlayKeys.ccShowZone, it) }
            }
        }
        item {
            SectionCard("Look", Icons.Rounded.Palette) {
                ThumbPicker(CcSkin.entries, skin, { it.label.removePrefix("Frosted ").replaceFirstChar { c -> c.uppercase() } }, { ui.set(OverlayKeys.ccSkin, it.name) }, thumbWidth = 84, thumbHeight = 120) { s, _ ->
                    ControlCenterPreview(s, tiles, radius, Modifier.phoneSize(), maxTiles = 6)
                }
                PrefSlider("Corner radius", radius, 6f..36f, ::fmtDp) { ui.set(OverlayKeys.ccRadiusDp, it) }
            }
        }
        item {
            SectionCard("Tiles", Icons.Rounded.GridView) {
                tiles.forEachIndexed { i, t ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(ControlCenterView.iconFor(t)), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(t.label, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { setTiles(CcLogic.move(tiles, i, -1)) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { setTiles(CcLogic.move(tiles, i, 1)) }, enabled = i < tiles.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = { setTiles(tiles - t) }, enabled = tiles.size > 1) { Icon(Icons.Rounded.Close, "Remove") }
                    }
                }
                val rest = CcTile.entries - tiles.toSet()
                if (rest.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rest.forEach { t ->
                            AssistChip({ setTiles(tiles + t) }, { Text(t.label) }, leadingIcon = { Icon(painterResource(ControlCenterView.iconFor(t)), null, Modifier.size(18.dp)) }, trailingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) })
                        }
                    }
                }
            }
        }
        item {
            SectionCard("Your apps", Icons.Rounded.Apps, badge = {
                Text("${apps.size}/${CcLogic.MAX_APPS}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { apps.forEach { AppIcon(it, 40) } }
                OutlinedButton(onClick = { picking = true }) { Icon(Icons.Rounded.Add, null); Text(" Choose") }
            }
        }
        item {
            SectionCard("Access", Icons.Rounded.Key) {
                val sh = shizuku == ShizukuState.READY
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(ControlCenterView.iconFor(CcTile.BRIGHTNESS)), null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                    Text("Brightness & rotation", Modifier.weight(1f))
                    SupportBadge(if (canWrite) "Allowed" else if (sh) "Via Shizuku" else "Needed", canWrite || sh)
                }
                if (!canWrite) OutlinedButton(onClick = {
                    if (sh) ui.runner.run("Allowed") { ctx.container.tweaks.grantWriteSettings(ctx.packageName); tick++ }
                    else open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${ctx.packageName}".toUri()))
                }) { Text("Allow") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(ControlCenterView.iconFor(CcTile.DND)), null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                    Text("Do not disturb", Modifier.weight(1f))
                    SupportBadge(if (dndAccess) "Allowed" else if (sh) "Via Shizuku" else "Needed", dndAccess || sh)
                }
                if (!dndAccess) OutlinedButton(onClick = { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) { Text("Allow") }
            }
        }
    }

    if (picking) AppPickerDialog("Control Center apps", apps, max = CcLogic.MAX_APPS, onDismiss = { picking = false }) {
        picking = false
        ui.set(OverlayKeys.ccApps, OverlayLogic.csv(it))
    }
}
