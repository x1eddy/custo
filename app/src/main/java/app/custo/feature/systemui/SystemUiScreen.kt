package app.custo.feature.systemui

import android.app.WallpaperManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.foundation.layout.Box
import app.custo.shizuku.ShellCommands.Namespace
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.ColorStrip
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.SupportBadge
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class SysState(
    val caps: Capabilities,
    val seed: Int?,
    val style: MonetStyle?,
    val hidden: Set<String>,
    val batteryPct: Boolean,
    val clockSecs: Boolean,
    val anim: AnimationScales,
    val physicalDpi: Int,
    val overrideDpi: Int?,
    val fontScale: Float,
    val exactAccentOn: Boolean,
    val colorSource: MonetJson.ColorSource?,
    /** null = not on this phone (Android 14+ only). */
    val contrast: Float?,
)

private data class HubEntry(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: Route)

private val HUB = listOf(
    HubEntry("Status bar", Icons.Rounded.CleaningServices, Route.StatusBarExtras),
    HubEntry("Screen", Icons.Rounded.Smartphone, Route.ScreenTweaks),
    HubEntry("Lock screen", Icons.Rounded.Lock, Route.LockScreen),
    HubEntry("Notifications", Icons.Rounded.Notifications, Route.Notifications),
    HubEntry("Gestures", Icons.Rounded.Gesture, Route.Gestures),
    HubEntry("Sound", Icons.AutoMirrored.Rounded.VolumeUp, Route.SoundHaptics),
    HubEntry("Custo tiles", Icons.Rounded.Apps, Route.TilePack),
    HubEntry("Advanced", Icons.Rounded.Code, Route.AdvancedSettings),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemUiScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val shizuku by c.shizuku.state.collectAsStateWithLifecycle()
    val ready = shizuku == ShizukuState.READY
    val runner = rememberActionRunner()
    var s by remember { mutableStateOf<SysState?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(ready, reload) {
        if (!ready) return@LaunchedEffect
        runCatching {
            val t = c.tweaks
            val json = t.monetJson()
            val (phys, over) = t.density()
            SysState(
                caps = t.capabilities(),
                seed = MonetJson.seedOf(json),
                style = MonetJson.styleOf(json),
                hidden = t.hiddenIcons(),
                batteryPct = t.batteryPercent(),
                clockSecs = t.clockSeconds(),
                anim = t.animationScales(),
                physicalDpi = phys,
                overrideDpi = over,
                fontScale = t.fontScale(),
                exactAccentOn = t.custoOverlays().any { it.second },
                colorSource = MonetJson.sourceOf(json),
                contrast = t.read(Namespace.SECURE, "contrast_level")?.toFloatOrNull()
                    ?: if (android.os.Build.VERSION.SDK_INT >= 34) 0f else null,
            )
        }.onSuccess { s = it }.onFailure { runner.message(it.message ?: "Could not read system settings") }
    }

    fun act(msg: String? = null, block: suspend () -> Unit) = runner.run(msg) { block(); reload++ }

    RevertDialog(c.appScope)
    ScreenScaffold(title = "System UI") {
        if (!ready) item {
            SectionCard("Locked", Icons.Rounded.SignalCellularAlt, "These change the real system UI. Set up Shizuku once to unlock them.", onClick = { nav.push(Route.ShizukuSetup) })
        }
        HUB.chunked(2).forEach { pair ->
            item(key = "hub_" + pair.first().title) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { e ->
                        Box(Modifier.weight(1f)) { SectionCard(e.title, e.icon, onClick = { nav.push(e.route) }) }
                    }
                }
            }
        }
        val st = s
        if (ready && st == null) item { Hint("Reading your system…") }
        if (st == null) return@ScreenScaffold

        if (st.caps.isSkinnedOem) item {
            Hint("${st.caps.oem.replaceFirstChar { it.uppercase() }} skins Android, so a few tweaks may not show. Undo all on Home reverts everything.")
        }

        // ---------------- Colors ----------------
        item { SectionLabel("Color") }
        item {
            var color by remember(st.seed) { mutableStateOf(Color(st.seed ?: 0xFF6D4AFF.toInt())) }
            var style by remember(st.style) { mutableStateOf(st.style ?: MonetStyle.TONAL_SPOT) }
            SectionCard("Palette", Icons.Rounded.ColorLens, "Recolors the shade, Settings and every Material You app.") {
                ColorPicker(color, { color = it })
                if (st.caps.monetStyles) {
                    Text("Style", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MonetStyle.entries.forEach { m -> FilterChip(selected = style == m, onClick = { style = m }, label = { Text(m.label) }) }
                    }
                }
                Text("Take colors from", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonetJson.ColorSource.entries.forEach { src ->
                        FilterChip(
                            selected = (st.colorSource ?: MonetJson.ColorSource.HOME) == src,
                            onClick = {
                                if (src == MonetJson.ColorSource.PRESET) act("System colors applied") { c.tweaks.applyMonet(color.toArgb(), if (st.caps.monetStyles) style else null) }
                                else act("Colors follow your ${src.label.lowercase()}") { c.tweaks.applyWallpaperColors(src) }
                            },
                            label = { Text(src.label) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { act("System colors applied") { c.tweaks.applyMonet(color.toArgb(), if (st.caps.monetStyles) style else null) } }) { Text("Apply") }
                    OutlinedButton(onClick = {
                        val wc = WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                        wc?.primaryColor?.toArgb()?.let { color = Color(it) }
                    }) {
                        androidx.compose.material3.Icon(Icons.Rounded.Wallpaper, null)
                        Text(" From wallpaper")
                    }
                }
            }
        }
        item {
            var contrast by remember(st.contrast) { mutableStateOf(st.contrast ?: 0f) }
            SectionCard("Contrast", Icons.Rounded.Contrast, if (st.contrast == null) null else "%.2f".format(contrast)) {
                if (st.contrast == null) NotOnPhoneRow("Contrast")
                else Slider(contrast, { contrast = (it * 4).roundToInt() / 4f }, valueRange = -1f..1f, steps = 7, onValueChangeFinished = {
                    act { c.tweaks.write(Namespace.SECURE, "contrast_level", contrast.toString()) }
                })
            }
        }
        item {
            var accent by remember { mutableStateOf(Color(st.seed ?: 0xFF6D4AFF.toInt())) }
            SectionCard(
                "Exact accent",
                Icons.Rounded.ColorLens,
                "Use this exact color as the system accent.",
                badge = { SupportBadge(if (st.caps.fabricatedOverlays) "Supported" else "Needs root", st.caps.fabricatedOverlays) },
            ) {
                if (!st.caps.fabricatedOverlays) Hint("Android ${android.os.Build.VERSION.RELEASE} only allows this with root. Palette above still works.")
                if (st.caps.fabricatedOverlays) {
                    ColorPicker(accent, { accent = it })
                    ColorStrip(Tonal.ramp(accent.toArgb()).values.map { Color(it) }, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { act("Exact accent applied") { c.tweaks.applyExactAccent(accent.toArgb()) } }) { Text("Apply") }
                        AnimatedVisibility(st.exactAccentOn) {
                            OutlinedButton(onClick = { act("Exact accent removed") { c.tweaks.disableCustoOverlays() } }) { Text("Remove") }
                        }
                    }
                }
            }
        }

        // ---------------- Status bar ----------------
        item { SectionLabel("Status bar") }
        item {
            SectionCard {
                SwitchRow("Battery percentage", null, st.batteryPct) { on -> act { c.tweaks.setBatteryPercent(on) } }
                SwitchRow("Clock seconds", null, st.clockSecs) { on -> act { c.tweaks.setClockSeconds(on) } }
                var demo by remember { mutableStateOf(false) }
                SwitchRow("Screenshot mode", "12:00, full battery, no notification icons", demo) { on ->
                    demo = on; act { c.tweaks.setDemoMode(on) }
                }
                Text("Hide icons", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    STATUS_BAR_SLOTS.forEach { (slot, label) ->
                        val hidden = slot in st.hidden
                        FilterChip(
                            selected = hidden,
                            onClick = { act { c.tweaks.setHiddenIcons(if (hidden) st.hidden - slot else st.hidden + slot) } },
                            label = { Text(label) },
                        )
                    }
                }
                Hint("Highlighted = hidden.")
            }
        }

        // ---------------- Quick settings ----------------
        item {
            SectionCard("Quick Settings tiles", Icons.Rounded.GridView, "Reorder, add, remove.", onClick = { nav.push(Route.QsEditor) })
        }

        // ---------------- Navigation ----------------
        if (st.caps.navModes.size > 1) {
            item { SectionLabel("Navigation") }
            item {
                SectionCard {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        st.caps.navModes.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = st.caps.currentNav == m,
                                onClick = { act("Navigation: ${m.label}") { c.tweaks.setNavMode(m, st.caps.currentNav) } },
                                shape = SegmentedButtonDefaults.itemShape(i, st.caps.navModes.size),
                            ) { Text(m.label) }
                        }
                    }
                }
            }
        }

        // ---------------- Motion ----------------
        item { SectionLabel("Motion & size") }
        item {
            var anim by remember(st.anim) { mutableStateOf(st.anim) }
            SectionCard("Animation speed", Icons.Rounded.Animation, null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimationPreset.entries.forEach { p ->
                        FilterChip(
                            selected = anim.window == p.scale && anim.transition == p.scale && anim.animator == p.scale,
                            onClick = { anim = AnimationScales(p.scale, p.scale, p.scale); act { c.tweaks.setAnimationScales(anim) } },
                            label = { Text(p.label) },
                        )
                    }
                }
                ScaleSlider("Window", anim.window, { anim = anim.copy(window = it) }) { act { c.tweaks.setAnimationScales(anim) } }
                ScaleSlider("Transition", anim.transition, { anim = anim.copy(transition = it) }) { act { c.tweaks.setAnimationScales(anim) } }
                ScaleSlider("Animator", anim.animator, { anim = anim.copy(animator = it) }) { act { c.tweaks.setAnimationScales(anim) } }
            }
        }
        item { DensityCard(st, onApply = { dpi, revert -> act { c.tweaks.setDensity(dpi) }; revert }, act = ::act) }
        item {
            var scale by remember(st.fontScale) { mutableStateOf(st.fontScale) }
            SectionCard("Font size", Icons.Rounded.FormatSize, "${(scale * 100).roundToInt()}%") {
                Slider(scale, { scale = (it * 20).roundToInt() / 20f }, valueRange = 0.8f..1.5f, onValueChangeFinished = { act { c.tweaks.setFontScale(scale) } })
            }
        }
    }
}

@Composable
private fun ScaleSlider(label: String, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Text("$label · ${"%.2f".format(value)}×", style = MaterialTheme.typography.labelMedium)
    Slider(value, { onChange((it * 4).roundToInt() / 4f) }, valueRange = 0f..2f, steps = 7, onValueChangeFinished = onDone)
}

@Composable
private fun DensityCard(st: SysState, onApply: (Int?, Boolean) -> Unit, act: (String?, suspend () -> Unit) -> Unit) {
    val c = LocalContext.current.container
    val current = st.overrideDpi ?: st.physicalDpi
    var dpi by remember(current) { mutableStateOf(current.toFloat()) }
    var previous by remember { mutableStateOf<Int?>(null) }
    var countdown by remember { mutableIntStateOf(0) }

    SectionCard("Display size", Icons.Rounded.GridView, "${dpi.roundToInt()} dpi · stock ${st.physicalDpi}") {
        val lo = (st.physicalDpi * 0.7f).coerceAtLeast(240f)
        val hi = (st.physicalDpi * 1.3f)
        Slider(dpi, { dpi = it }, valueRange = lo..hi)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                previous = st.overrideDpi
                countdown = 15
                onApply(dpi.roundToInt(), true)
            }) { Text("Apply") }
            OutlinedButton(onClick = { act("Display size reset") { c.tweaks.setDensity(null) } }) { Text("Reset") }
        }
        Hint("Auto-reverts in 15 s unless you keep it.")
    }

    if (countdown > 0) {
        LaunchedEffect(Unit) {
            while (countdown > 0) { delay(1000); countdown-- }
            act("Display size reverted") { c.tweaks.setDensity(previous) }
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Keep this display size?") },
            text = { Text("Switching back in $countdown s") },
            confirmButton = { TextButton(onClick = { countdown = -1 }) { Text("Keep") } },
            dismissButton = { TextButton(onClick = { countdown = 0; act("Display size reverted") { c.tweaks.setDensity(previous) } }) { Text("Revert") } },
        )
    }
}
