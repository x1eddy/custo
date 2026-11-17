package app.custo.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow
import app.custo.ui.theme.AccentSource
import app.custo.ui.theme.AppAppearance
import app.custo.ui.theme.CornerStyle
import app.custo.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings for Custo itself: theme, AMOLED black, accent, corner roundness, reduced motion, updates and licenses. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.container.prefs
    val scope = rememberCoroutineScope()
    val look by remember { prefs.data.map(AppAppearance::read) }.collectAsStateWithLifecycle(AppAppearance())
    fun <T> save(key: Preferences.Key<T>, value: T) { scope.launch { prefs.set(key, value) } }
    var license by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "Settings", onBack = onBack) {
        item {
            SectionCard("Theme", Icons.Rounded.Contrast) {
                Choice(ThemeMode.entries, look.mode, { it.label }) { save(AppAppearance.MODE, it.name) }
                SwitchRow("AMOLED black", "Pure black backgrounds in dark mode", look.amoled) { save(AppAppearance.AMOLED, it) }
            }
        }
        item {
            SectionCard("Accent", Icons.Rounded.Palette, subtitle = "The color of buttons, switches and highlights in Custo") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentSource.entries.forEach { a ->
                        FilterChip(look.accent == a, { save(AppAppearance.ACCENT, a.name) }, {
                            Text(when (a) { AccentSource.MATERIAL_YOU -> "Material You"; AccentSource.CUSTO -> "Custo violet"; AccentSource.CUSTOM -> "Custom" })
                        })
                    }
                }
                when (look.accent) {
                    AccentSource.MATERIAL_YOU -> Hint("Follows your wallpaper colors, like the rest of the system.")
                    AccentSource.CUSTO -> Hint("Custo's own violet, whatever your wallpaper.")
                    AccentSource.CUSTOM -> ColorPicker(Color(look.customAccent), { save(AppAppearance.CUSTOM_ACCENT, it.toArgb()) })
                }
            }
        }
        item {
            SectionCard("Corners", Icons.Rounded.RoundedCorner) {
                Choice(CornerStyle.entries, look.corners, { it.label }) { save(AppAppearance.CORNERS, it.name) }
            }
        }
        item {
            SectionCard("Motion", Icons.Rounded.Animation) {
                SwitchRow("Reduce motion", "Skips decorative animations: Home's entrance, press bounces, the logo and sliding screens", look.reduceMotion) {
                    save(AppAppearance.REDUCE_MOTION, it)
                }
            }
        }
        item {
            SectionCard("Updates", Icons.Rounded.SystemUpdate) { app.custo.feature.update.UpdateSettings() }
        }
        item {
            val files by produceState(emptyList<String>()) {
                value = withContext(Dispatchers.IO) { runCatching { ctx.assets.list("licenses")?.sorted().orEmpty() }.getOrDefault(emptyList()) }
            }
            SectionCard("Open-source licenses", Icons.Rounded.Description, subtitle = "Fonts bundled with Custo") {
                Column {
                    files.forEach { f ->
                        Text(
                            f.removePrefix("OFL-").removeSuffix(".txt").replace(Regex("(?<=[a-z])(?=[A-Z])"), " ") + " · SIL Open Font License",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { license = f }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }

    license?.let { name ->
        val text by produceState("", name) {
            value = withContext(Dispatchers.IO) { runCatching { ctx.assets.open("licenses/$name").bufferedReader().use { it.readText() } }.getOrDefault("") }
        }
        AlertDialog(
            onDismissRequest = { license = null },
            title = { Text(name.removeSuffix(".txt")) },
            text = { Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { license = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(selected == o, { onSelect(o) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label(o), maxLines = 1) }
        }
    }
}
