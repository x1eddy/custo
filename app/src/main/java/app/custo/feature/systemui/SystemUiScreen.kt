package app.custo.feature.systemui

import android.app.WallpaperManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.ui.components.AnimationDemo
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.ColorStrip
import app.custo.ui.components.HubTile
import app.custo.ui.components.IconNote
import app.custo.ui.components.IconSwitchRow
import app.custo.ui.components.InfoButton
import app.custo.ui.components.NavMockKind
import app.custo.ui.components.NavModeCard
import app.custo.ui.components.PalettePhone
import app.custo.ui.components.QsTileChip
import app.custo.ui.components.ScaledPhoneMock
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.SupportBadge
import app.custo.ui.components.SwatchRow
import app.custo.ui.components.rememberActionRunner
import app.custo.ui.components.skeletonItems
import kotlinx.coroutines.launch
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
    val qsTiles: List<String>,
)

private data class HubEntry(val title: String, val icon: ImageVector, val route: Route)

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

private val ANIM_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

/** Every value the hub (and Home's quick toggles) show. */
internal val HUB_KEYS: Set<String> = setOf(
    Ids.of(Namespace.SECURE, MonetJson.SETTING),
    Ids.of(Namespace.SECURE, "icon_blacklist"),
    Ids.of(Namespace.SYSTEM, "status_bar_show_battery_percent"),
    Ids.of(Namespace.SECURE, "clock_seconds"),
    Ids.of(Namespace.SYSTEM, "font_scale"),
    Ids.of(Namespace.SECURE, "contrast_level"),
    Ids.of(Namespace.SECURE, "sysui_qs_tiles"),
    Ids.WM_DENSITY,
    Ids.OVERLAYS,
) + ANIM_KEYS.map { Ids.of(Namespace.GLOBAL, it) }

private val HUB_PROBES = setOf(ProbeKeys.NAV_MODES, ProbeKeys.FABRICATE)

private fun NavMode.kind() = when (this) {
    NavMode.GESTURAL -> NavMockKind.GESTURE
    NavMode.THREE_BUTTON -> NavMockKind.THREE_BUTTON
    NavMode.TWO_BUTTON -> NavMockKind.TWO_BUTTON
}

private fun MonetJson.ColorSource.icon() = when (this) {
    MonetJson.ColorSource.HOME -> Icons.Rounded.Wallpaper
    MonetJson.ColorSource.LOCK -> Icons.Rounded.Lock
    MonetJson.ColorSource.PRESET -> Icons.Rounded.Palette
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemUiScreen(nav: Navigator, onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val c = ctx.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()
    val st = rememberDeviceState("hub", HUB_KEYS, HUB_PROBES) { v ->
        val json = v.v(Namespace.SECURE, MonetJson.SETTING)
        val (phys, over) = v.density()
        fun anim(k: String) = v.v(Namespace.GLOBAL, k)?.toFloatOrNull() ?: 1f
        SysState(
            caps = v.capabilities(),
            seed = MonetJson.seedOf(json),
            style = MonetJson.styleOf(json),
            hidden = v.v(Namespace.SECURE, "icon_blacklist")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
            batteryPct = v.v(Namespace.SYSTEM, "status_bar_show_battery_percent") == "1",
            clockSecs = v.v(Namespace.SECURE, "clock_seconds") == "1",
            anim = AnimationScales(anim(ANIM_KEYS[0]), anim(ANIM_KEYS[1]), anim(ANIM_KEYS[2])),
            physicalDpi = phys,
            overrideDpi = over,
            fontScale = v.v(Namespace.SYSTEM, "font_scale")?.toFloatOrNull() ?: 1f,
            exactAccentOn = v.custoOverlayOn(),
            colorSource = MonetJson.sourceOf(json),
            contrast = v.v(Namespace.SECURE, "contrast_level")?.toFloatOrNull() ?: if (android.os.Build.VERSION.SDK_INT >= 34) 0f else null,
            qsTiles = v.v(Namespace.SECURE, "sysui_qs_tiles")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        )
    }
    val demoOn by c.prefs.flow(SysKeys.demoOn, false).collectAsStateWithLifecycle(false)

    fun act(msg: String? = null, block: suspend () -> Unit) = runner.run(msg) { block() }

    RevertDialog(c.appScope)
    ScreenScaffold(title = "System UI", onBack = onBack) {
        if (!ready) item {
            SectionCard("Set up Shizuku", Icons.Rounded.SignalCellularAlt, onClick = { nav.push(Route.ShizukuSetup) })
        }
        HUB.chunked(2).forEach { pair ->
            item(key = "hub_" + pair.first().title) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { e -> HubTile(e.title, e.icon, Modifier.weight(1f)) { nav.push(e.route) } }
                }
            }
        }
        if (!ready) return@ScreenScaffold
        if (st == null) { skeletonItems(3); return@ScreenScaffold }

        // ---------------- Colors ----------------
        item { SectionLabel("Color") }
        item {
            var color by remember(st.seed) { mutableStateOf(Color(st.seed ?: 0xFF6D4AFF.toInt())) }
            var style by remember(st.style) { mutableStateOf(st.style ?: MonetStyle.TONAL_SPOT) }
            val dark = isSystemInDarkTheme()
            val preview = stylePreviewColors(color.toArgb(), if (st.caps.monetStyles) style else null, dark)
            SectionCard("Palette", Icons.Rounded.ColorLens, badge = {
                InfoButton("Palette", "Recolors the shade, Settings and every Material You app. Preview is approximate.")
            }) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PalettePhone(preview[0], preview[1], preview[2], preview[3], preview[4], Modifier.width(84.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SwatchRow(preview.take(4))
                        if (st.caps.monetStyles) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MonetStyle.entries.forEach { m -> FilterChip(selected = style == m, onClick = { style = m }, label = { Text(m.label) }) }
                        }
                    }
                }
                ColorPicker(color, { color = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonetJson.ColorSource.entries.forEach { src ->
                        FilterChip(
                            selected = (st.colorSource ?: MonetJson.ColorSource.HOME) == src,
                            onClick = {
                                if (src == MonetJson.ColorSource.PRESET) act("Applied") { c.tweaks.applyMonet(color.toArgb(), if (st.caps.monetStyles) style else null) }
                                else act("Applied") { c.tweaks.applyWallpaperColors(src) }
                            },
                            leadingIcon = { Icon(src.icon(), null, Modifier.size(FilterChipDefaults.IconSize)) },
                            label = { Text(src.label) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { act("Applied") { c.tweaks.applyMonet(color.toArgb(), if (st.caps.monetStyles) style else null) } }) { Text("Apply") }
                    OutlinedButton(onClick = {
                        val wc = WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                        wc?.primaryColor?.toArgb()?.let { color = Color(it) }
                    }) {
                        Icon(Icons.Rounded.Wallpaper, "Pick from wallpaper")
                    }
                }
            }
        }
        item {
            var contrast by remember(st.contrast) { mutableStateOf(st.contrast ?: 0f) }
            SectionCard("Contrast", Icons.Rounded.Contrast, badge = { if (st.contrast != null) Text(contrastLabel(contrast), style = MaterialTheme.typography.labelLarge) }) {
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
                badge = { SupportBadge(if (st.caps.fabricatedOverlays) "Supported" else "Needs root", st.caps.fabricatedOverlays) },
            ) {
                if (st.caps.fabricatedOverlays) {
                    ColorPicker(accent, { accent = it })
                    ColorStrip(Tonal.ramp(accent.toArgb()).values.map { Color(it) }, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { act("Applied") { c.tweaks.applyExactAccent(accent.toArgb()) } }) { Text("Apply") }
                        AnimatedVisibility(st.exactAccentOn) {
                            OutlinedButton(onClick = { act("Removed") { c.tweaks.disableCustoOverlays() } }) { Text("Remove") }
                        }
                    }
                }
            }
        }

        // ---------------- Status bar ----------------
        item { SectionLabel("Status bar") }
        item {
            SectionCard {
                LiveStatusBar(st.hidden, st.batteryPct, st.clockSecs)
                IconSwitchRow(Icons.Rounded.Percent, "Battery %", st.batteryPct) { on -> act { c.tweaks.setBatteryPercent(on) } }
                IconSwitchRow(Icons.Rounded.Timer, "Seconds", st.clockSecs) { on -> act { c.tweaks.setClockSeconds(on) } }
                IconSwitchRow(Icons.Rounded.PhotoCamera, "Screenshot mode", demoOn) { on ->
                    act {
                        c.tweaks.setDemoMode(on)
                        c.prefs.set(SysKeys.demoOn, on)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    InfoButton("Hide icons", "Highlighted icons are hidden from the status bar. Some phones ignore a few of them.")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    STATUS_BAR_SLOTS.forEach { (slot, label) ->
                        val hidden = slot in st.hidden
                        FilterChip(
                            selected = hidden,
                            onClick = { act { c.tweaks.setHiddenIcons(if (hidden) st.hidden - slot else st.hidden + slot) } },
                            leadingIcon = { SLOT_ICONS[slot]?.let { Icon(it, null, Modifier.size(FilterChipDefaults.IconSize)) } },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        // ---------------- Quick settings ----------------
        item {
            SectionCard("Quick Settings", Icons.Rounded.GridView, onClick = { nav.push(Route.QsEditor) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    st.qsTiles.take(4).forEach { spec ->
                        QsTileChip(qsLabel(spec), qsIcon(spec), active = true, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ---------------- Navigation ----------------
        if (st.caps.navModes.size > 1) {
            item { SectionLabel("Navigation") }
            item {
                SectionCard(null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        st.caps.navModes.forEach { m ->
                            NavModeCard(m.kind(), m.label, st.caps.currentNav == m, Modifier.weight(1f)) {
                                if (st.caps.currentNav != m) act { c.tweaks.setNavMode(m) }
                            }
                        }
                    }
                }
            }
        }

        // ---------------- Motion & size ----------------
        item { SectionLabel("Motion & size") }
        item {
            var anim by remember(st.anim) { mutableStateOf(st.anim) }
            var custom by remember { mutableStateOf(false) }
            SectionCard("Animation speed", Icons.Rounded.Animation) {
                AnimationDemo(anim.transition)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimationPreset.entries.forEach { p ->
                        FilterChip(
                            selected = anim.window == p.scale && anim.transition == p.scale && anim.animator == p.scale,
                            onClick = { anim = AnimationScales(p.scale, p.scale, p.scale); act { c.tweaks.setAnimationScales(anim) } },
                            label = { Text(p.label) },
                        )
                    }
                }
                TextButton(onClick = { custom = !custom }) {
                    Icon(Icons.Rounded.Tune, null)
                    Text("  Custom")
                }
                if (custom) {
                    ScaleSlider("Window", anim.window, { anim = anim.copy(window = it) }) { act { c.tweaks.setAnimationScales(anim) } }
                    ScaleSlider("Transition", anim.transition, { anim = anim.copy(transition = it) }) { act { c.tweaks.setAnimationScales(anim) } }
                    ScaleSlider("Animator", anim.animator, { anim = anim.copy(animator = it) }) { act { c.tweaks.setAnimationScales(anim) } }
                }
            }
        }
        item {
            SizeCard(
                st,
                onApplyDensity = { dpi ->
                    // Density changes recreate this activity: run in the app scope with the revert armed first.
                    val previous = st.overrideDpi
                    RevertTimer.start(c.appScope, "Keep this display size?") { c.tweaks.setDensity(previous) }
                    c.appScope.launch {
                        try {
                            c.tweaks.setDensity(dpi)
                        } catch (e: Throwable) {
                            RevertTimer.revertNow(c.appScope)
                            runner.message(errorText(e))
                        }
                    }
                },
                onResetDensity = { act { c.tweaks.setDensity(null) } },
                onFont = { scale -> act { c.tweaks.setFontScale(scale) } },
            )
        }
        if (st.caps.isSkinnedOem) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.caps.oem.replaceFirstChar { it.uppercase() } + " skin", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                InfoButton("Skinned Android", "Your phone's maker changes Android, so a few tweaks may not show. Undo all on Home reverts everything.")
            }
        }
    }
}

@Composable
private fun ScaleSlider(label: String, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Text("$label · ${"%.2f".format(value)}×", style = MaterialTheme.typography.labelMedium)
    Slider(value, { onChange((it * 4).roundToInt() / 4f) }, valueRange = 0f..2f, steps = 7, onValueChangeFinished = onDone)
}

/** Display size + font size with one mini phone that scales live with both sliders. */
@Composable
private fun SizeCard(st: SysState, onApplyDensity: (Int) -> Unit, onResetDensity: () -> Unit, onFont: (Float) -> Unit) {
    val current = st.overrideDpi ?: st.physicalDpi
    var dpi by remember(current) { mutableStateOf(current.toFloat()) }
    var font by remember(st.fontScale) { mutableStateOf(st.fontScale) }
    val stock = st.physicalDpi.coerceAtLeast(1)
    SectionCard("Size", Icons.Rounded.ZoomIn) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ScaledPhoneMock(dpi / stock, font, Modifier.width(96.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Smartphone, "Display size", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("  ${dpi.roundToInt()} dpi", style = MaterialTheme.typography.labelLarge)
                }
                if (st.physicalDpi > 0) {
                    val lo = (st.physicalDpi * 0.7f).coerceAtLeast(240f)
                    val hi = st.physicalDpi * 1.3f
                    Slider(dpi, { dpi = it }, valueRange = lo..hi)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onApplyDensity(dpi.roundToInt()) }, enabled = dpi.roundToInt() != current) { Text("Apply") }
                        if (st.overrideDpi != null) OutlinedButton(onClick = onResetDensity) { Text("Reset") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FormatSize, "Font size", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("  ${(font * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                }
                Slider(font, { font = (it * 20).roundToInt() / 20f }, valueRange = 0.8f..1.5f, onValueChangeFinished = { onFont(font) })
            }
        }
        IconNote(Icons.Rounded.Timer, "Display size auto-reverts in 15 s")
    }
}

/** Android 14's contrast steps are 0 (standard), 0.5 (medium) and 1 (high). */
private fun contrastLabel(v: Float): String = when {
    v < -0.25f -> "Low"
    v < 0.25f -> "Standard"
    v < 0.75f -> "Medium"
    else -> "High"
}
