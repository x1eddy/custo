package app.custo.feature.systemui

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.custo.container
import app.custo.nav.Navigator
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.rememberActionRunner
import kotlin.math.roundToInt

internal val EXTRA_DIM = Toggle(Namespace.SECURE, "reduce_bright_colors_activated", "Extra dim",
    probe = Probe.AnyOf(Probe.ResBool("config_reduceBrightColorsAvailable"), Probe.Present))
private val NIGHT_LIGHT = Toggle(Namespace.SECURE, "night_display_activated", "Night light",
    probe = Probe.AnyOf(Probe.ResBool("config_nightDisplayAvailable"), Probe.Present))
private val INVERT = Toggle(Namespace.SECURE, "accessibility_display_inversion_enabled", "Invert colors")
private val BOLD = Toggle(Namespace.SECURE, "font_weight_adjustment", "Bold text", on = "300", off = "0")

private val TIMEOUTS = listOf(15_000 to "15 s", 30_000 to "30 s", 60_000 to "1 min", 120_000 to "2 min", 300_000 to "5 min", 600_000 to "10 min", 1_800_000 to "30 min")

private data class ScreenState(
    val toggles: Map<String, ToggleState>,
    val dimLevel: Int,
    val nightTemp: Int,
    val nightRange: IntRange,
    val grayscale: Boolean,
    val physical: ScreenSize?,
    val override: ScreenSize?,
    val cutoutAvailable: Boolean,
    val cutoutHidden: Boolean,
    val timeout: Int?,
    val refresh: RefreshState?,
)

private data class RefreshState(val rates: List<Float>, val peak: Float?, val min: Float?)

private fun displayRates(ctx: Context): List<Float> {
    val d = ctx.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList()
    return d.supportedModes.map { it.refreshRate.roundToInt().toFloat() }.distinct().sorted()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenTweaksScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()
    var reload by remember { mutableIntStateOf(0) }
    val st = rememberSysLoad(ready, reload, runner) {
        val t = c.tweaks
        val toggles = Probes.load(ctx, t, listOf(EXTRA_DIM, NIGHT_LIGHT, INVERT, BOLD))
        val lo = Probes.resInt(ctx, "config_nightDisplayColorTemperatureMin") ?: 2596
        val hi = Probes.resInt(ctx, "config_nightDisplayColorTemperatureMax") ?: 4082
        val def = Probes.resInt(ctx, "config_nightDisplayColorTemperatureDefault") ?: 2850
        val (phys, over) = t.screenSize()
        val (cutAvail, cutHidden) = t.cutoutState()
        val peak = t.read(Namespace.SYSTEM, "peak_refresh_rate")
        val min = t.read(Namespace.SYSTEM, "min_refresh_rate")
        val rates = displayRates(ctx)
        ScreenState(
            toggles = toggles,
            dimLevel = t.read(Namespace.SECURE, "reduce_bright_colors_level")?.toFloatOrNull()?.roundToInt() ?: 50,
            nightTemp = t.read(Namespace.SECURE, "night_display_color_temperature")?.toIntOrNull() ?: def,
            nightRange = minOf(lo, hi)..maxOf(lo, hi),
            grayscale = t.read(Namespace.SECURE, "accessibility_display_daltonizer_enabled") == "1" &&
                t.read(Namespace.SECURE, "accessibility_display_daltonizer") == "0",
            physical = phys,
            override = over,
            cutoutAvailable = cutAvail,
            cutoutHidden = cutHidden,
            timeout = t.read(Namespace.SYSTEM, "screen_off_timeout")?.toIntOrNull(),
            refresh = if ((peak != null || min != null) && rates.size > 1) RefreshState(rates, peak?.toFloatOrNull(), min?.toFloatOrNull()) else null,
        )
    }

    fun act(msg: String? = null, block: suspend () -> Unit) = runner.run(msg) { block(); reload++ }

    RevertDialog(c.appScope)
    ScreenScaffold(title = "Screen", onBack = { nav.pop() }) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        if (st == null) { item { Hint("Reading your system…") }; return@ScreenScaffold }

        item {
            var level by remember(st.dimLevel) { mutableStateOf(st.dimLevel.toFloat()) }
            SectionCard("Extra dim", Icons.Rounded.BrightnessLow) {
                val ts = st.toggles[EXTRA_DIM.id]
                if (ts == null || !ts.supported) NotOnPhoneRow("Extra dim") else {
                    SwitchRow("Extra dim", null, ts.on) { on -> act { c.tweaks.setToggle(EXTRA_DIM, on) } }
                    Text("Intensity · ${level.roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(level, { level = it }, valueRange = 0f..100f, onValueChangeFinished = {
                        act { c.tweaks.write(Namespace.SECURE, "reduce_bright_colors_level", level.roundToInt().toString()) }
                    })
                }
            }
        }
        item {
            var temp by remember(st.nightTemp) { mutableStateOf(st.nightTemp.toFloat()) }
            SectionCard("Night light", Icons.Rounded.NightsStay) {
                val ts = st.toggles[NIGHT_LIGHT.id]
                if (ts == null || !ts.supported) NotOnPhoneRow("Night light") else {
                    SwitchRow("Night light", null, ts.on) { on -> act { c.tweaks.setToggle(NIGHT_LIGHT, on) } }
                    Text("Warmth · ${temp.roundToInt()} K", style = MaterialTheme.typography.labelMedium)
                    Slider(temp, { temp = it }, valueRange = st.nightRange.first.toFloat()..st.nightRange.last.toFloat(), onValueChangeFinished = {
                        act { c.tweaks.write(Namespace.SECURE, "night_display_color_temperature", temp.roundToInt().toString()) }
                    })
                }
            }
        }
        item {
            SectionCard("Colors & text", Icons.Rounded.Contrast) {
                SwitchRow("Grayscale", null, st.grayscale) { on ->
                    act {
                        if (on) c.tweaks.write(Namespace.SECURE, "accessibility_display_daltonizer", "0")
                        c.tweaks.write(Namespace.SECURE, "accessibility_display_daltonizer_enabled", if (on) "1" else "0")
                    }
                }
                ToggleRows(listOf(INVERT, BOLD), st.toggles) { t, on -> act { c.tweaks.setToggle(t, on) } }
            }
        }
        item {
            ResolutionCard(st.physical, st.override) { size ->
                val previous = st.override
                act {
                    c.tweaks.setScreenSize(size)
                    RevertTimer.start(c.appScope, "Keep this resolution?") { c.tweaks.setScreenSize(previous) }
                }
            }
        }
        item {
            SectionCard("Hide camera notch", Icons.Rounded.Fullscreen) {
                if (!st.cutoutAvailable) NotOnPhoneRow("Hide camera notch")
                else SwitchRow("Hide camera notch", "Blacks out the top edge", st.cutoutHidden) { on -> act { c.tweaks.setHideCutout(on) } }
            }
        }
        item {
            SectionCard("Screen timeout", Icons.Rounded.Timer) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIMEOUTS.forEach { (ms, label) ->
                        FilterChip(selected = st.timeout == ms, onClick = { act { c.tweaks.write(Namespace.SYSTEM, "screen_off_timeout", ms.toString()) } }, label = { Text(label) })
                    }
                }
            }
        }
        item {
            SectionCard("Refresh rate", Icons.Rounded.Speed) {
                val r = st.refresh
                if (r == null) NotOnPhoneRow("Refresh rate") else {
                    val max = r.rates.last()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.rates.forEach { hz ->
                            FilterChip(
                                selected = r.peak == hz && (r.min ?: 0f) < hz,
                                onClick = { act { c.tweaks.write(Namespace.SYSTEM, "peak_refresh_rate", hz.toString()); c.tweaks.write(Namespace.SYSTEM, "min_refresh_rate", "0.0") } },
                                label = { Text("Up to ${hz.roundToInt()} Hz") },
                            )
                        }
                        FilterChip(
                            selected = r.min == max,
                            onClick = { act { c.tweaks.write(Namespace.SYSTEM, "peak_refresh_rate", max.toString()); c.tweaks.write(Namespace.SYSTEM, "min_refresh_rate", max.toString()) } },
                            label = { Text("Always ${max.roundToInt()} Hz") },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResolutionCard(physical: ScreenSize?, override: ScreenSize?, apply: (ScreenSize?) -> Unit) {
    SectionCard("Screen resolution", Icons.Rounded.HighQuality, (override ?: physical)?.toString()) {
        if (physical == null) { NotOnPhoneRow("Screen resolution"); return@SectionCard }
        val current = override ?: physical
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysCmd.resolutionChoices(physical).forEach { size ->
                FilterChip(
                    selected = current == size,
                    onClick = { if (size != current) apply(size.takeUnless { it == physical }) },
                    label = { Text(if (size == physical) "Full · ${size.width}p" else "${size.width}p") },
                )
            }
        }
        Hint("Lower saves battery. Auto-reverts in 15 s unless you keep it.")
    }
}
