package app.custo.feature.systemui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.BuildConfig
import app.custo.container
import app.custo.data.DeviceCacheLogic
import app.custo.data.DeviceStateRepository
import app.custo.shizuku.ShellBatch
import app.custo.shizuku.ShellCommands.Namespace

/** Ids of cached values that aren't plain settings. */
object Ids {
    const val WM_SIZE = DeviceCacheLogic.RAW + "wm_size"
    const val WM_DENSITY = DeviceCacheLogic.RAW + "wm_density"
    const val OVERLAYS = DeviceCacheLogic.RAW + "overlays"

    fun of(ns: Namespace, key: String) = "${ns.id}:$key"
}

/** Probe keys (see [SysProbes.resolve]). */
object ProbeKeys {
    const val NAV_MODES = "overlays:nav_modes"
    const val NO_CUTOUT = "overlays:no_cutout"
    const val FABRICATE = "help:overlay_fabricate"
    const val DISABLE_FLAGS = "help:statusbar_disable"
    const val DND = "help:notification_dnd"
    fun resInt(name: String) = "res.int:$name"
}

/** Read-only view over cached state, handed to each screen's pure state builder. */
class DeviceView(val values: Map<String, String?>, val probes: Map<String, String>) {
    fun v(ns: Namespace, key: String): String? = values[Ids.of(ns, key)]
    fun raw(id: String): String = values[id].orEmpty()
    fun flag(probe: String): Boolean = probes[probe] == "1"

    fun toggles(list: List<Toggle>): Map<String, ToggleState> =
        list.associate { t ->
            val v = v(t.ns, t.key)
            t.id to ToggleState(Probes.eval(t.probe, v, probes), t.isOn(v))
        }

    fun capabilities(): Capabilities {
        val overlays = raw(Ids.OVERLAYS)
        val names = probes[ProbeKeys.NAV_MODES].orEmpty().split(',').filter { it.isNotBlank() }
        val enabled = SysCmd.enabledNavOverlay(overlays)
        return Capabilities(
            sdk = Build.VERSION.SDK_INT,
            monetStyles = Build.VERSION.SDK_INT >= 33,
            // Android 14+ rejects fabricated overlays from the shell uid, so without root this is 12/13 only.
            fabricatedOverlays = Build.VERSION.SDK_INT < 34 && flag(ProbeKeys.FABRICATE),
            navModes = NavMode.entries.filter { it.name in names },
            currentNav = enabled?.let { id -> NavMode.entries.firstOrNull { id == it.overlay || id.startsWith(it.overlay + "_") } },
            oem = Build.MANUFACTURER.lowercase(),
        )
    }

    fun density(): Pair<Int, Int?> {
        val out = raw(Ids.WM_DENSITY)
        fun grab(label: String) = Regex("$label density: (\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull()
        return (grab("Physical") ?: 0) to grab("Override")
    }

    fun screenSize(): Pair<ScreenSize?, ScreenSize?> = SysCmd.parseWmSize(raw(Ids.WM_SIZE))

    fun custoOverlayOn(): Boolean = raw(Ids.OVERLAYS).lineSequence().any { it.trim().startsWith("[x]") && it.contains("com.android.shell:custo_") }
}

/** Computes probe values: resources locally, shell probes in one batched call. */
object SysProbes {
    suspend fun resolve(ctx: Context, tweaks: SystemTweaks, keys: Set<String>): Map<String, String> {
        val out = HashMap<String, String>()
        val shellCmds = ArrayList<Pair<String, String>>()
        for (k in keys) {
            val arg = k.substringAfter(':')
            when (k.substringBefore(':')) {
                "res.bool" -> out[k] = if (Probes.resBool(ctx, arg)) "1" else "0"
                "res.string" -> out[k] = if (Probes.resString(ctx, arg) != null) "1" else "0"
                "res.int" -> out[k] = Probes.resInt(ctx, arg)?.toString().orEmpty()
                "prop" -> shellCmds += k to SysCmd.getprop(arg)
                "overlays" -> if (shellCmds.none { it.first == "overlays" }) shellCmds += "overlays" to "cmd overlay list"
                "help" -> {
                    val cmd = when (k) {
                        ProbeKeys.FABRICATE -> "cmd overlay help"
                        ProbeKeys.DISABLE_FLAGS -> SysCmd.STATUSBAR_HELP
                        ProbeKeys.DND -> SysCmd.NOTIFICATION_HELP
                        else -> null
                    }
                    if (cmd != null) shellCmds += k to cmd
                }
            }
        }
        if (shellCmds.isEmpty()) return out
        val parts = ShellBatch.split(tweaks.probe(ShellBatch.script(shellCmds)))
        for (k in keys) {
            when {
                k.startsWith("prop:") -> parts[k]?.let { out[k] = if (it.trim() == "true") "1" else "0" }
                k == ProbeKeys.FABRICATE -> parts[k]?.let { out[k] = if (it.contains("fabricate")) "1" else "0" }
                k == ProbeKeys.DISABLE_FLAGS -> parts[k]?.let { out[k] = if (it.contains("send-disable-flag")) "1" else "0" }
                k == ProbeKeys.DND -> parts[k]?.let { out[k] = if (it.contains("set_dnd")) "1" else "0" }
                k == ProbeKeys.NAV_MODES -> parts["overlays"]?.let { list ->
                    out[k] = NavMode.entries.filter { list.contains(it.overlay) }.joinToString(",") { it.name }
                }
                k == ProbeKeys.NO_CUTOUT -> parts["overlays"]?.let { out[k] = if (SysCmd.hasOverlay(it, SysCmd.NO_CUTOUT)) "1" else "0" }
            }
        }
        // The shell answered: anything it didn't print counts as "not supported", so no screen waits forever.
        if (parts.isNotEmpty()) for (k in keys) out.putIfAbsent(k, "0")
        return out
    }
}

/** Value ids a toggle list needs. */
fun List<Toggle>.ids(): Set<String> = mapTo(LinkedHashSet()) { it.id }

/** Probe keys a toggle list needs. */
fun List<Toggle>.probeKeys(): Set<String> = flatMapTo(LinkedHashSet()) { Probes.leaves(it.probe) }

/**
 * Screen state from the device cache: returns immediately when the cache already holds [keys] and
 * [probes] (no spinner), otherwise null until the first read lands (show skeletons). Every time the
 * screen is entered or resumed it refreshes the values in the background (throttled), and the UI only
 * changes when a value really did.
 */
@Composable
fun <T> rememberDeviceState(
    tag: String,
    keys: Set<String>,
    probes: Set<String> = emptySet(),
    build: (DeviceView) -> T,
): T? {
    val ctx = LocalContext.current
    val c = ctx.container
    val ready = rememberShizukuReady()
    val cache by c.deviceState.cache.collectAsStateWithLifecycle()
    val epoch by c.deviceState.epoch.collectAsStateWithLifecycle()
    val t0 = remember { SystemClock.elapsedRealtime() }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    LaunchedEffect(ready, epoch, resumes) {
        if (!ready || resumes == 0) return@LaunchedEffect
        runCatching {
            c.deviceState.ensureProbes(probes) { SysProbes.resolve(ctx, c.tweaks, it) }
            c.deviceState.refresh(tag, keys, if (resumes > 1) DeviceStateRepository.Mode.RESUME else DeviceStateRepository.Mode.ENTER)
        }.onFailure { if (BuildConfig.DEBUG) Log.w("CustoCache", "refresh[$tag] failed", it) }
    }
    val snap = cache ?: return null
    if (!snap.hasValues(keys) || !snap.hasProbes(probes)) return null
    val result = remember(snap.values, snap.probes) { runCatching { build(DeviceView(snap.values, snap.probes)) }.getOrNull() }
    if (BuildConfig.DEBUG) {
        val logged = remember { booleanArrayOf(false) }
        if (!logged[0] && result != null) {
            logged[0] = true
            Log.d("CustoCache", "screen[$tag] shown in ${SystemClock.elapsedRealtime() - t0} ms")
        }
    }
    return result
}

/** What Home's quick toggles and "Your look" tile show, from the same cache. */
data class QuickSystem(val batteryPct: Boolean, val clockSeconds: Boolean, val snappy: Boolean, val styleName: String?)

private val QUICK_KEYS = setOf(
    Ids.of(Namespace.SYSTEM, "status_bar_show_battery_percent"),
    Ids.of(Namespace.SECURE, "clock_seconds"),
    Ids.of(Namespace.GLOBAL, "window_animation_scale"),
    Ids.of(Namespace.SECURE, MonetJson.SETTING),
)

@Composable
fun rememberQuickSystem(): QuickSystem? = rememberDeviceState("home", QUICK_KEYS) { v ->
    QuickSystem(
        batteryPct = v.v(Namespace.SYSTEM, "status_bar_show_battery_percent") == "1",
        clockSeconds = v.v(Namespace.SECURE, "clock_seconds") == "1",
        snappy = (v.v(Namespace.GLOBAL, "window_animation_scale")?.toFloatOrNull() ?: 1f) <= 0.5f,
        styleName = MonetJson.styleOf(v.v(Namespace.SECURE, MonetJson.SETTING))?.label,
    )
}
