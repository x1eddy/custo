package app.custo.feature.profiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.feature.overlays.OverlayBus
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.rememberActionRunner
import java.util.UUID

@Composable
private fun appIcons(): Map<String, LaunchableApp> {
    val ctx = LocalContext.current
    val apps by produceState(emptyList<LaunchableApp>()) { value = AppList.load(ctx) }
    return remember(apps) { apps.associateBy { it.pkg } }
}

@Composable
fun ProfilesScreen(nav: Navigator, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val runner = rememberActionRunner()
    val profiles by c.profiles.profiles.collectAsStateWithLifecycle(emptyList())
    val running by OverlayBus.serviceRunning.collectAsStateWithLifecycle()
    val apps = appIcons()

    ScreenScaffold(
        title = "Per-app",
        onBack = onBack,
        actions = {
            app.custo.ui.components.InfoButton(
                "Per-app profiles",
                "Change settings automatically while an app is open. They switch back when you leave it. " +
                    "Try: Instagram in grayscale, or games with fast animations and no pop-ups.\n\nNeeds the overlay service (Overlays tab) and Shizuku.",
            )
            IconButton(onClick = { nav.push(Route.ProfileEditor(null)) }) { Icon(Icons.Rounded.Add, "New profile") }
        },
    ) {
        if (!running) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                app.custo.ui.components.SupportBadge("Overlay service off", false)
            }
        }
        if (profiles.isEmpty()) item {
            SectionCard {
                // The idea as a picture: an app, then its settings flip while it's open.
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    app.custo.ui.components.IconBadge(Icons.Rounded.Apps, size = 56)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.padding(horizontal = 12.dp))
                    app.custo.ui.components.IconBadge(Icons.Rounded.InvertColors, size = 56, container = MaterialTheme.colorScheme.secondaryContainer, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    app.custo.ui.components.IconBadge(Icons.Rounded.Speed, size = 56, container = MaterialTheme.colorScheme.tertiaryContainer, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Button(onClick = { nav.push(Route.ProfileEditor(null)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text(" New profile") }
            }
        }
        items(profiles, key = { it.id }) { p ->
            SectionCard(p.name, null, summary(p), onClick = { nav.push(Route.ProfileEditor(p.id)) }, badge = {
                Switch(p.enabled, { on -> runner.run { c.profiles.save(p.copy(enabled = on)) } })
            }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.packages.take(8).forEach { pkg ->
                        apps[pkg]?.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(28.dp).clip(CircleShape)) }
                    }
                    if (p.packages.size > 8) Text("+${p.packages.size - 8}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun summary(p: AppProfile): String = buildList {
    p.toggles.forEach { (k, on) -> ProfileSetting.entries.firstOrNull { it.name == k }?.let { add("${it.label} ${if (on) "on" else "off"}") } }
    p.animationScale?.let { add("Animations ${it}×") }
    p.screenTimeoutSec?.let { add("Timeout ${if (it < 60) "${it}s" else "${it / 60}m"}") }
}.joinToString(" · ").ifEmpty { "No changes yet" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorScreen(profileId: String?, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val runner = rememberActionRunner()
    var p by remember { mutableStateOf(AppProfile(UUID.randomUUID().toString(), "New profile")) }
    LaunchedEffect(profileId) { if (profileId != null) c.profiles.all().firstOrNull { it.id == profileId }?.let { p = it } }
    val ctx = LocalContext.current
    val apps by produceState(emptyList<LaunchableApp>()) { value = AppList.load(ctx) }
    var query by remember { mutableStateOf("") }

    ScreenScaffold(
        title = p.name.ifBlank { "Profile" },
        onBack = onBack,
        actions = {
            if (profileId != null) IconButton(onClick = { runner.run { c.profiles.delete(p.id); onBack() } }) { Icon(Icons.Rounded.Delete, "Delete") }
            IconButton(onClick = { runner.run("Saved") { c.profiles.save(p); onBack() } }) { Icon(Icons.Rounded.Check, "Save") }
        },
    ) {
        item { OutlinedTextField(p.name, { p = p.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true) }

        item { SectionLabel("While open") }
        item {
            SectionCard {
                ProfileSetting.entries.forEach { s ->
                    Text(s.label, style = MaterialTheme.typography.bodyLarge)
                    val cur = p.toggles[s.name]
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf<Boolean?>(null, true, false).forEachIndexed { i, v ->
                            SegmentedButton(cur == v, {
                                p = p.copy(toggles = if (v == null) p.toggles - s.name else p.toggles + (s.name to v))
                            }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(when (v) { null -> "Leave"; true -> "On"; false -> "Off" }) }
                        }
                    }
                }
                Text("Animation speed", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Float?>(null, 0f, 0.5f, 1f, 1.5f).forEach { v ->
                        FilterChip(p.animationScale == v, { p = p.copy(animationScale = v) }, { Text(if (v == null) "Leave" else if (v == 0f) "Off" else "${v}×") })
                    }
                }
                Text("Screen timeout", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Int?>(null, 15, 30, 60, 300, 600).forEach { v ->
                        FilterChip(p.screenTimeoutSec == v, { p = p.copy(screenTimeoutSec = v) }, {
                            Text(when { v == null -> "Leave"; v < 60 -> "${v}s"; else -> "${v / 60} min" })
                        })
                    }
                }
            }
        }

        item { SectionLabel("Apps · ${p.packages.size}") }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search apps") }, singleLine = true) }
        val shown = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .sortedByDescending { it.pkg in p.packages }
        items(shown, key = { it.pkg }) { app ->
            val checked = app.pkg in p.packages
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .clickable { p = p.copy(packages = if (checked) p.packages - app.pkg else p.packages + app.pkg) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                app.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(36.dp).clip(CircleShape)) }
                Spacer(Modifier.width(12.dp))
                Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Checkbox(checked, null)
            }
        }
    }
}
