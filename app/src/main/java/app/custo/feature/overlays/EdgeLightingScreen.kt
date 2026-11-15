package app.custo.feature.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.custo.data.Keys
import app.custo.ui.components.ColorDot
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.BrushSwatch
import app.custo.ui.components.InfoButton
import app.custo.ui.components.ThumbPicker
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.ui.graphics.Brush
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow

@Composable
fun EdgeLightingScreen(onBack: () -> Unit) {
    val ui = rememberOverlayUi()
    val p = ui.p
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var slot by remember { mutableIntStateOf(0) }
    val rules = OverlayLogic.decodeRules(p[OverlayKeys.edgeAppRules])
    fun saveRules(r: Map<String, Int>) = ui.set(OverlayKeys.edgeAppRules, OverlayLogic.encodeRules(r))

    val accent = MaterialTheme.colorScheme.primary
    val style = enumOr(p[OverlayKeys.edgeStyle], EdgeStyle.SWEEP)
    val mode = enumOr(p[Keys.edgeColorMode], EdgeColorMode.APP)
    val custom = OverlayLogic.colorsCsv(p[OverlayKeys.edgeGradient]).ifEmpty { EdgeLightingOverlay.DEFAULT_GRADIENT }
    val colors = edgeColors(mode, custom, accent)
    val width = p[Keys.edgeWidthDp] ?: 4f
    val duration = p[Keys.edgeDurationMs] ?: 2500

    ScreenScaffold("Edge lighting", onBack, actions = {
        InfoButton("Edge lighting", "A glow runs around the screen when a notification arrives. It needs notification access to know when something comes in. App color uses the notification's color, or the app icon's main color.")
    }) {
        item { NotificationAccessCard("Edge lighting needs it to know when something arrives.") }
        item {
            OverlayHero(p[Keys.edgeEnabled] == true, { ui.set(Keys.edgeEnabled, it) }, { ui.preview(Preview.EDGE) }) {
                EdgeLightPreview(style, colors, width, Modifier.phoneSize(), duration)
            }
        }
        item {
            SectionCard("Animation") {
                ThumbPicker(EdgeStyle.entries, style, { it.label }, { ui.set(OverlayKeys.edgeStyle, it.name) }, thumbHeight = 104) { st, sel ->
                    EdgeLightPreview(st, colors, 8f, Modifier.phoneSize(), 2200, animate = sel)
                }
                PrefSlider("Thickness", width, 2f..14f, ::fmtDp) { ui.set(Keys.edgeWidthDp, it) }
                PrefSlider("Duration", duration / 1000f, 1f..6f, ::fmtSec) { ui.set(Keys.edgeDurationMs, (it * 1000).toInt()) }
                PrefSlider("Repeat", (p[OverlayKeys.edgeRepeat] ?: 1).toFloat(), 1f..5f, { "${it.toInt()}×" }, steps = 3) { ui.set(OverlayKeys.edgeRepeat, it.toInt()) }
            }
        }
        item {
            SectionCard("Color") {
                ThumbPicker(EdgeColorMode.entries, mode, { it.label }, { ui.set(Keys.edgeColorMode, it.name) }, thumbWidth = 64, thumbHeight = 56) { m, _ ->
                    BrushSwatch(Brush.sweepGradient(edgeColors(m, custom, accent).let { it + it.first() }))
                }
                if (mode == EdgeColorMode.CUSTOM) {
                    val i = slot.coerceIn(0, custom.lastIndex)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        custom.forEachIndexed { k, c -> ColorDot(Color(c), selected = k == i) { slot = k } }
                        if (custom.size < 3) IconButton(onClick = { ui.set(OverlayKeys.edgeGradient, OverlayLogic.colorsCsv(custom + custom.last())); slot = custom.size }) { Icon(Icons.Rounded.Add, "Add color") }
                        if (custom.size > 1) IconButton(onClick = { ui.set(OverlayKeys.edgeGradient, OverlayLogic.colorsCsv(custom.filterIndexed { k, _ -> k != i })); slot = 0 }) { Icon(Icons.Rounded.Remove, "Remove color") }
                    }
                    ColorPicker(Color(custom[i]), { c -> ui.set(OverlayKeys.edgeGradient, OverlayLogic.colorsCsv(custom.toMutableList().also { it[i] = c.toArgb() })) })
                }
            }
        }
        item {
            SectionCard("Per app", Icons.Rounded.Apps) {
                rules.forEach { (pkg, rule) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, 32)
                        Spacer(Modifier.width(10.dp))
                        Text(Apps.label(ctx, pkg), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        FilterChip(rule == OverlayLogic.IGNORE, {
                            saveRules(rules + (pkg to if (rule == OverlayLogic.IGNORE) 0xFF6D4AFF.toInt() else OverlayLogic.IGNORE))
                        }, { Text("Ignore") })
                        if (rule != OverlayLogic.IGNORE) {
                            Spacer(Modifier.width(8.dp))
                            ColorDot(Color(rule), selected = editing == pkg, size = 32) { editing = if (editing == pkg) null else pkg }
                        }
                        IconButton(onClick = { saveRules(rules - pkg); if (editing == pkg) editing = null }) { Icon(Icons.Rounded.Close, "Remove") }
                    }
                    if (editing == pkg && rule != OverlayLogic.IGNORE) ColorPicker(Color(rule), { saveRules(rules + (pkg to it.toArgb())) })
                }
                OutlinedButton(onClick = { picking = true }) { Icon(Icons.Rounded.Add, null); Text(" Apps") }
            }
        }
        item {
            SectionCard("When", Icons.Rounded.Schedule) {
                SwitchRow("Screen on only", null, p[OverlayKeys.edgeScreenOnOnly] == true) { ui.set(OverlayKeys.edgeScreenOnOnly, it) }
                SwitchRow("Quiet hours", null, p[OverlayKeys.edgeQuiet] == true) { ui.set(OverlayKeys.edgeQuiet, it) }
                if (p[OverlayKeys.edgeQuiet] == true) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeButton("From", p[OverlayKeys.edgeQuietStart] ?: (22 * 60)) { ui.set(OverlayKeys.edgeQuietStart, it) }
                    TimeButton("To", p[OverlayKeys.edgeQuietEnd] ?: (7 * 60)) { ui.set(OverlayKeys.edgeQuietEnd, it) }
                }
            }
        }
    }

    if (picking) AppPickerDialog("Add apps", rules.keys.toList(), onDismiss = { picking = false }) { chosen ->
        picking = false
        // Keep existing rules, new apps start with their icon color, unticked apps are dropped.
        saveRules(chosen.associateWith { rules[it] ?: Apps.colorFor(ctx, it, 0).takeIf { c -> c != 0 } ?: 0xFF6D4AFF.toInt() })
    }
}
