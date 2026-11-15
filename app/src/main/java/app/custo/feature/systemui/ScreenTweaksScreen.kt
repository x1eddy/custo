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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.custo.container
import app.custo.nav.Navigator
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.ui.components.FilterSample
import app.custo.ui.components.IconNote
import app.custo.ui.components.IconSwitchRow
import app.custo.ui.components.InfoButton
import app.custo.ui.components.skeletonItems
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.FilterBAndW
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.HideSource
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val EXTRA_DIM = Toggle(Namespace.SECURE, "reduce_bright_colors_activated", "Extra dim", icon = Icons.Rounded.BrightnessLow,
    probe = Probe.AnyOf(Probe.ResBool("config_reduceBrightColorsAvailable"), Probe.Present))
private val NIGHT_LIGHT = Toggle(Namespace.SECURE, "night_display_activated", "Night light", icon = Icons.Rounded.NightsStay,
    probe = Probe.AnyOf(Probe.ResBool("config_nightDisplayAvailable"), Probe.Present))
private val INVERT = Toggle(Namespace.SECURE, "accessibility_display_inversion_enabled", "Invert", icon = Icons.Rounded.InvertColors)
private val BOLD = Toggle(Namespace.SECURE, "font_weight_adjustment", "Bold text", on = "300", off = "0", icon = Icons.Rounded.FormatBold)
private val SCREEN_TOGGLES = listOf(EXTRA_DIM, NIGHT_LIGHT, INVERT, BOLD)

private val TIMEOUTS = listOf(15_000 to "15 s", 30_000 to "30 s", 60_000 to "1 min", 120_000 to "2 min", 300_000 to "5 min", 600_000 to "10 min", 1_800_000 to "30 min")

private const val DIM_LEVEL = "reduce_bright_colors_level"
private const val NIGHT_TEMP = "night_display_color_temperature"
private const val DALT_ON = "accessibility_display_daltonizer_enabled"
private const val DALT = "accessibility_display_daltonizer"
private const val TIMEOUT = "screen_off_timeout"
private const val PEAK = "peak_refresh_rate"
private const val MIN_RATE = "min_refresh_rate"

private val SCREEN_KEYS = SCREEN_TOGGLES.ids() + listOf(
    Ids.of(Namespace.SECURE, DIM_LEVEL), Ids.of(Namespace.SECURE, NIGHT_TEMP), Ids.of(Namespace.SECURE, DALT_ON),
    Ids.of(Namespace.SECURE, DALT), Ids.of(Namespace.SYSTEM, TIMEOUT), Ids.of(Namespace.SYSTEM, PEAK),
    Ids.of(Namespace.SYSTEM, MIN_RATE), Ids.WM_SIZE, Ids.OVERLAYS,
)
private val SCREEN_PROBES = SCREEN_TOGGLES.probeKeys() + ProbeKeys.NO_CUTOUT + listOf(
    ProbeKeys.resInt("config_nightDisplayColorTemperatureMin"),
    ProbeKeys.resInt("config_nightDisplayColorTemperatureMax"),
    ProbeKeys.resInt("config_nightDisplayColorTemperatureDefault"),
)

private data class ScreenState(
    val toggles: Map<String, ToggleState>,
    val dimLevel: Int,
    val nightTemp: Int,
    val nightRange: IntRange,
    val nightKelvin: Boolean = true,
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
    val rates = remember { displayRates(ctx) }
    val st = rememberDeviceState("screen", SCREEN_KEYS, SCREEN_PROBES) { v ->
        fun res(name: String) = v.probes[ProbeKeys.resInt(name)]?.toIntOrNull()
        val lo = res("config_nightDisplayColorTemperatureMin") ?: 2596
        val hi = res("config_nightDisplayColorTemperatureMax") ?: 4082
        val def = res("config_nightDisplayColorTemperatureDefault") ?: 2850
        val (phys, over) = v.screenSize()
        val peak = v.v(Namespace.SYSTEM, PEAK)
        val min = v.v(Namespace.SYSTEM, MIN_RATE)
        val nightRaw = v.v(Namespace.SECURE, NIGHT_TEMP)?.toIntOrNull()
        val nightOem = nightRaw != null && nightRaw < 1000
        ScreenState(
            toggles = v.toggles(SCREEN_TOGGLES),
            dimLevel = v.v(Namespace.SECURE, DIM_LEVEL)?.toFloatOrNull()?.roundToInt() ?: 50,
            nightTemp = nightRaw ?: def,
            // Some OEM builds (e.g. Unisoc) store a 0–255 level here instead of Kelvin.
            nightRange = if (nightOem) 0..255 else minOf(lo, hi)..maxOf(lo, hi),
            nightKelvin = !nightOem,
            grayscale = v.v(Namespace.SECURE, DALT_ON) == "1" && v.v(Namespace.SECURE, DALT) == "0",
            physical = phys,
            override = over,
            cutoutAvailable = v.flag(ProbeKeys.NO_CUTOUT),
            cutoutHidden = SysCmd.enabledCutout(v.raw(Ids.OVERLAYS)) == SysCmd.NO_CUTOUT,
            timeout = v.v(Namespace.SYSTEM, TIMEOUT)?.toIntOrNull(),
            refresh = if ((peak != null || min != null) && rates.size > 1) RefreshState(rates, peak?.toFloatOrNull(), min?.toFloatOrNull()) else null,
        )
    }

    fun act(msg: String? = null, block: suspend () -> Unit) = runner.run(msg) { block() }

    RevertDialog(c.appScope)
    ScreenScaffold(title = "Screen", onBack = { nav.pop() }) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        if (st == null) { skeletonItems(3); return@ScreenScaffold }

        item {
            var level by remember(st.dimLevel) { mutableFloatStateOf(st.dimLevel.toFloat()) }
            var temp by remember(st.nightTemp) { mutableFloatStateOf(st.nightTemp.toFloat()) }
            val dim = st.toggles[EXTRA_DIM.id]
            val night = st.toggles[NIGHT_LIGHT.id]
            val invert = st.toggles[INVERT.id]?.on == true
            val span = (st.nightRange.last - st.nightRange.first).coerceAtLeast(1)
            // Lower Kelvin = warmer; OEM 0–255 levels grow warmer upwards.
            val warm = ((temp - st.nightRange.first) / span).let { if (st.nightKelvin) 1f - it else it }.coerceIn(0f, 1f)
            SectionCard("Filters", Icons.Rounded.Contrast) {
                FilterSample(
                    dim = if (dim?.on == true) 0.15f + 0.6f * level / 100f else 0f,
                    warmth = if (night?.on == true) 0.3f + 0.7f * warm else 0f,
                    grayscale = st.grayscale,
                    invert = invert,
                )
                if (dim != null && dim.supported) {
                    IconSwitchRow(EXTRA_DIM.icon, EXTRA_DIM.title, dim.on) { on -> act { c.tweaks.setToggle(EXTRA_DIM, on) } }
                    if (dim.on) ValueSlider(level, 0f..100f, "${level.roundToInt()}%", { level = it }) {
                        act { c.tweaks.write(Namespace.SECURE, DIM_LEVEL, level.roundToInt().toString()) }
                    }
                }
                if (night != null && night.supported) {
                    IconSwitchRow(NIGHT_LIGHT.icon, NIGHT_LIGHT.title, night.on) { on -> act { c.tweaks.setToggle(NIGHT_LIGHT, on) } }
                    if (night.on) ValueSlider(temp, st.nightRange.first.toFloat()..st.nightRange.last.toFloat(),
                        if (st.nightKelvin) "${temp.roundToInt()} K" else "${temp.roundToInt()}", { temp = it }) {
                        act { c.tweaks.write(Namespace.SECURE, NIGHT_TEMP, temp.roundToInt().toString()) }
                    }
                }
                IconSwitchRow(Icons.Rounded.FilterBAndW, "Grayscale", st.grayscale) { on ->
                    act {
                        if (on) c.tweaks.write(Namespace.SECURE, DALT, "0")
                        c.tweaks.write(Namespace.SECURE, DALT_ON, if (on) "1" else "0")
                    }
                }
                ToggleRows(listOf(INVERT, BOLD), st.toggles) { t, on -> act { c.tweaks.setToggle(t, on) } }
                val missing = listOf(EXTRA_DIM, NIGHT_LIGHT).filter { st.toggles[it.id]?.supported == false }
                if (missing.isNotEmpty()) Text("Not here: " + missing.joinToString(", ") { it.title.lowercase() },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            ResolutionCard(st.physical, st.override) { size ->
                val previous = st.override
                // The resolution change recreates this activity, so it runs in the app scope, with the
                // revert armed before the first command: a half-applied change still switches back.
                RevertTimer.start(c.appScope, "Keep this resolution?") { c.tweaks.setScreenSize(previous) }
                c.appScope.launch {
                    try {
                        c.tweaks.setScreenSize(size)
                    } catch (e: Throwable) {
                        RevertTimer.revertNow(c.appScope)
                        runner.message(errorText(e))
                    }
                }
            }
        }
        if (st.cutoutAvailable) item {
            SectionCard("Camera notch", Icons.Rounded.Fullscreen) {
                IconSwitchRow(Icons.Rounded.HideSource, "Hide notch", st.cutoutHidden) { on -> act { c.tweaks.setHideCutout(on) } }
            }
        }
        item {
            SectionCard("Timeout", Icons.Rounded.Timer) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIMEOUTS.forEach { (ms, label) ->
                        FilterChip(selected = st.timeout == ms, onClick = { act { c.tweaks.write(Namespace.SYSTEM, TIMEOUT, ms.toString()) } }, label = { Text(label) })
                    }
                }
            }
        }
        if (st.refresh != null) item {
            SectionCard("Refresh rate", Icons.Rounded.Speed) {
                val r = st.refresh
                val max = r.rates.last()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.rates.forEach { hz ->
                        FilterChip(
                            selected = r.peak == hz && (r.min ?: 0f) < hz,
                            onClick = { act { c.tweaks.write(Namespace.SYSTEM, PEAK, hz.toString()); c.tweaks.write(Namespace.SYSTEM, MIN_RATE, "0.0") } },
                            label = { Text("≤ ${hz.roundToInt()} Hz") },
                        )
                    }
                    FilterChip(
                        selected = r.min == max,
                        onClick = { act { c.tweaks.write(Namespace.SYSTEM, PEAK, max.toString()); c.tweaks.write(Namespace.SYSTEM, MIN_RATE, max.toString()) } },
                        label = { Text("Always ${max.roundToInt()} Hz") },
                    )
                }
            }
        }
    }
}

/** Slider with its value at the end, no label line. */
@Composable
internal fun ValueSlider(value: Float, range: ClosedFloatingPointRange<Float>, text: String, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(value, onChange, valueRange = range, onValueChangeFinished = onDone, modifier = Modifier.weight(1f))
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 10.dp).width(56.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResolutionCard(physical: ScreenSize?, override: ScreenSize?, apply: (ScreenSize?) -> Unit) {
    SectionCard("Resolution", Icons.Rounded.HighQuality, badge = {
        InfoButton("Resolution", "Fewer pixels can save battery. Text and icons keep their size. If the screen looks wrong, wait 15 s and it switches back.")
    }) {
        if (physical == null) { NotOnPhoneRow("Screen resolution"); return@SectionCard }
        val current = override ?: physical
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysCmd.resolutionChoices(physical).forEach { size ->
                FilterChip(
                    selected = current == size,
                    onClick = { if (size != current) apply(size.takeUnless { it == physical }) },
                    label = { Text(if (size == physical) "${size.width}p · full" else "${size.width}p") },
                )
            }
        }
        IconNote(Icons.Rounded.Timer, "Auto-reverts in 15 s")
    }
}
