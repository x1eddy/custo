package app.custo.feature.systemui

import android.os.Build
import app.custo.shizuku.OriginalsStore
import app.custo.shizuku.Shell
import app.custo.shizuku.ShellCommands
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.shizuku.ShellResult

data class Capabilities(
    val sdk: Int,
    val monetStyles: Boolean,
    val fabricatedOverlays: Boolean,
    val navModes: List<NavMode>,
    val currentNav: NavMode?,
    val oem: String,
) {
    /** Heavily skinned OEM ROMs ignore some AOSP SystemUI settings. */
    val isSkinnedOem: Boolean get() = oem in setOf("samsung", "xiaomi", "oppo", "vivo", "realme", "huawei", "honor", "oneplus")
}

/**
 * Every privileged system change goes through here. Before the first write to any setting,
 * its original value is saved so [restoreAll] can undo everything Custo ever did.
 */
class SystemTweaks(private val shell: Shell, private val originals: OriginalsStore) {

    private suspend fun run(cmd: String): ShellResult = shell.run(cmd)

    private suspend fun runOk(cmd: String): ShellResult {
        val r = run(cmd)
        if (!r.ok) error(r.error)
        return r
    }

    suspend fun read(ns: Namespace, key: String): String? =
        ShellCommands.parseSettingValue(run(ShellCommands.settingsGet(ns, key)).output)

    suspend fun write(ns: Namespace, key: String, value: String?) {
        originals.rememberIfAbsent(ns, key, read(ns, key))
        if (value == null) runOk(ShellCommands.settingsDelete(ns, key))
        else runOk(ShellCommands.settingsPut(ns, key, value))
    }

    // ---- Capabilities -------------------------------------------------------------------------

    suspend fun capabilities(): Capabilities {
        val overlays = run(ShellCommands.OVERLAY_LIST).output
        val help = run("cmd overlay help").output
        val navs = NavMode.entries.filter { overlays.contains(it.overlay) }
        val current = NavMode.entries.firstOrNull { mode ->
            overlays.lineSequence().any { it.contains("[x]") && it.contains(mode.overlay) }
        }
        return Capabilities(
            sdk = Build.VERSION.SDK_INT,
            monetStyles = Build.VERSION.SDK_INT >= 33,
            // Android 14+ rejects fabricated overlays from the shell uid ("Non-root shell cannot
            // fabricate overlays"), so without root this only works on Android 12 and 13.
            fabricatedOverlays = Build.VERSION.SDK_INT < 34 && help.contains("fabricate"),
            navModes = navs,
            currentNav = current,
            oem = Build.MANUFACTURER.lowercase(),
        )
    }

    // ---- Monet --------------------------------------------------------------------------------

    suspend fun monetJson(): String? = read(Namespace.SECURE, MonetJson.SETTING)

    suspend fun applyMonet(seed: Int, style: MonetStyle?) {
        val json = MonetJson.build(monetJson(), seed, style, System.currentTimeMillis())
        write(Namespace.SECURE, MonetJson.SETTING, json)
    }

    suspend fun applyWallpaperColors(source: MonetJson.ColorSource) {
        write(Namespace.SECURE, MonetJson.SETTING, MonetJson.withWallpaperSource(monetJson(), source, System.currentTimeMillis()))
    }

    // ---- Status bar ---------------------------------------------------------------------------

    suspend fun hiddenIcons(): Set<String> =
        read(Namespace.SECURE, "icon_blacklist")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    suspend fun setHiddenIcons(slots: Set<String>) =
        write(Namespace.SECURE, "icon_blacklist", slots.sorted().joinToString(",").ifEmpty { null })

    suspend fun batteryPercent(): Boolean = read(Namespace.SYSTEM, "status_bar_show_battery_percent") == "1"
    suspend fun setBatteryPercent(on: Boolean) = write(Namespace.SYSTEM, "status_bar_show_battery_percent", if (on) "1" else "0")

    suspend fun clockSeconds(): Boolean = read(Namespace.SECURE, "clock_seconds") == "1"
    suspend fun setClockSeconds(on: Boolean) = write(Namespace.SECURE, "clock_seconds", if (on) "1" else "0")

    suspend fun setDemoMode(on: Boolean) {
        if (on) {
            write(Namespace.GLOBAL, "sysui_demo_allowed", "1")
            runOk(ShellCommands.demoBroadcast("enter"))
            runOk(ShellCommands.demoBroadcast("clock", mapOf("hhmm" to "1200")))
            runOk(ShellCommands.demoBroadcast("battery", mapOf("level" to "100", "plugged" to "false")))
            runOk(ShellCommands.demoBroadcast("network", mapOf("wifi" to "show", "level" to "4")))
            runOk(ShellCommands.demoBroadcast("network", mapOf("mobile" to "show", "datatype" to "none", "level" to "4")))
            runOk(ShellCommands.demoBroadcast("notifications", mapOf("visible" to "false")))
        } else {
            run(ShellCommands.demoBroadcast("exit"))
        }
    }

    // ---- Quick Settings -----------------------------------------------------------------------

    suspend fun qsTiles(): List<String> =
        read(Namespace.SECURE, "sysui_qs_tiles")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    suspend fun setQsTiles(tiles: List<String>) =
        write(Namespace.SECURE, "sysui_qs_tiles", tiles.joinToString(",").ifEmpty { null })

    // ---- Navigation ---------------------------------------------------------------------------

    suspend fun setNavMode(mode: NavMode, current: NavMode?) {
        current?.let { originals.rememberIfAbsent(Namespace.CUSTO, NAV_KEY, it.overlay) }
        runOk(ShellCommands.overlayEnableExclusiveInCategory(mode.overlay))
    }

    // ---- Motion & display ---------------------------------------------------------------------

    suspend fun animationScales(): AnimationScales {
        suspend fun f(k: String) = read(Namespace.GLOBAL, k)?.toFloatOrNull() ?: 1f
        return AnimationScales(f("window_animation_scale"), f("transition_animation_scale"), f("animator_duration_scale"))
    }

    suspend fun setAnimationScales(s: AnimationScales) {
        write(Namespace.GLOBAL, "window_animation_scale", s.window.toString())
        write(Namespace.GLOBAL, "transition_animation_scale", s.transition.toString())
        write(Namespace.GLOBAL, "animator_duration_scale", s.animator.toString())
    }

    /** Returns (physical, override-or-null). */
    suspend fun density(): Pair<Int, Int?> {
        val out = run(ShellCommands.WM_DENSITY_GET).output
        fun grab(label: String) = Regex("$label density: (\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull()
        return (grab("Physical") ?: 0) to grab("Override")
    }

    suspend fun setDensity(dpi: Int?) {
        val (_, override) = density()
        originals.rememberIfAbsent(Namespace.SECURE, DENSITY_KEY, override?.toString())
        runOk(ShellCommands.wmDensity(dpi))
    }

    suspend fun fontScale(): Float = read(Namespace.SYSTEM, "font_scale")?.toFloatOrNull() ?: 1f
    suspend fun setFontScale(scale: Float) = write(Namespace.SYSTEM, "font_scale", scale.toString())

    // ---- Fabricated overlays ------------------------------------------------------------------

    /** Overrides the framework's accent ramp with exact shades derived from [argb]. */
    suspend fun applyExactAccent(argb: Int) {
        Tonal.ramp(argb).forEach { (shade, color) ->
            shell.fabricate("android", ShellCommands.FABRICATED_PREFIX + "a1_$shade", "android:color/system_accent1_$shade", 0x1c, color)
        }
    }

    suspend fun custoOverlays(): List<Pair<String, Boolean>> =
        run(ShellCommands.OVERLAY_LIST).output.lineSequence()
            .map { it.trim() }
            .filter { it.contains("com.android.shell:${ShellCommands.FABRICATED_PREFIX}") }
            .map { line -> line.substringAfter("] ").trim() to line.startsWith("[x]") }
            .toList()

    /** Removes every fabricated overlay Custo created (falls back to disabling if removal fails). */
    suspend fun disableCustoOverlays() {
        custoOverlays().forEach { (id, _) ->
            val name = id.substringAfter(':')
            runCatching { shell.unfabricate(name) }.onFailure { run(ShellCommands.overlayDisable(id)) }
        }
    }

    // ---- Custo's own services (not recorded as tweaks: Restore defaults keeps them on) ----------

    /** Turns on an accessibility service without the Settings round-trip (and past "restricted settings"). */
    suspend fun enableAccessibilityService(component: String) {
        val current = read(Namespace.SECURE, "enabled_accessibility_services").orEmpty()
        val list = current.split(':').filter { it.isNotBlank() }
        if (component !in list) runOk(ShellCommands.settingsPut(Namespace.SECURE, "enabled_accessibility_services", (list + component).joinToString(":")))
        runOk(ShellCommands.settingsPut(Namespace.SECURE, "accessibility_enabled", "1"))
    }

    suspend fun allowNotificationListener(component: String) {
        runOk("cmd notification allow_listener ${ShellCommands.quote(component)}")
    }

    // ---- Extras: generic helpers -------------------------------------------------------------

    /** Raw command output, for capability probes only. Never change state with it (no undo). */
    suspend fun probe(cmd: String): String = run(cmd).output

    /** Records a setting's current value for undo when Custo changes it through some other API. */
    suspend fun rememberOriginal(ns: Namespace, key: String) = originals.rememberIfAbsent(ns, key, read(ns, key))

    suspend fun isRecorded(ns: Namespace, key: String): Boolean = originals.all().containsKey(ns to key)

    suspend fun settingsList(ns: Namespace): List<Pair<String, String>> =
        SysCmd.parseSettingsList(runOk(SysCmd.settingsList(ns.id)).output)

    // ---- Extras: status bar disable flags (lost on reboot; re-applied by StatusBarKeeper) ------

    suspend fun supportsDisableFlags(): Boolean = run(SysCmd.STATUSBAR_HELP).output.contains("send-disable-flag")

    suspend fun setDisableFlags(flags: Set<DisableFlag>) {
        originals.rememberIfAbsent(Namespace.CUSTO, STATUSBAR_FLAGS_KEY, "")
        runOk(SysCmd.sendDisableFlag(flags))
    }

    // ---- Extras: screen resolution ------------------------------------------------------------

    suspend fun screenSize(): Pair<ScreenSize?, ScreenSize?> = SysCmd.parseWmSize(run(SysCmd.WM_SIZE_GET).output)

    /** Changes resolution and scales density with it so things keep their size. null = stock. */
    suspend fun setScreenSize(size: ScreenSize?) {
        val (physical, override) = screenSize()
        val from = override ?: physical ?: error("Could not read screen size")
        val to = size ?: physical ?: from
        originals.rememberIfAbsent(Namespace.CUSTO, WM_SIZE_KEY, override?.toString())
        val (physDpi, overDpi) = density()
        val dpi = overDpi ?: physDpi
        runOk(SysCmd.wmSize(size))
        if (dpi > 0 && from != to) {
            val scaled = SysCmd.scaledDensity(dpi, from, to)
            // Back at stock size and the scaled dpi is (about) stock too: drop the override entirely.
            setDensity(if (size == null && kotlin.math.abs(scaled - physDpi) <= 2) null else scaled)
        }
    }

    // ---- Extras: display cutout ("Hide camera notch") -----------------------------------------

    /** (available, hidden) */
    suspend fun cutoutState(): Pair<Boolean, Boolean> {
        val list = run(ShellCommands.OVERLAY_LIST).output
        return SysCmd.hasOverlay(list, SysCmd.NO_CUTOUT) to (SysCmd.enabledCutout(list) == SysCmd.NO_CUTOUT)
    }

    suspend fun setHideCutout(hide: Boolean) {
        val current = SysCmd.enabledCutout(run(ShellCommands.OVERLAY_LIST).output)
        originals.rememberIfAbsent(Namespace.CUSTO, CUTOUT_KEY, current)
        if (hide) {
            runOk(SysCmd.overlayEnableExclusive(SysCmd.NO_CUTOUT))
        } else {
            val original = originals.all()[Namespace.CUSTO to CUTOUT_KEY]?.takeUnless { it == SysCmd.NO_CUTOUT }
            val r = restoreCutout(original)
            if (!r.ok) error(r.error)
        }
    }

    private suspend fun restoreCutout(original: String?): ShellResult =
        if (original == null) run(SysCmd.overlayDisable(SysCmd.NO_CUTOUT)) else run(SysCmd.overlayEnableExclusive(original))

    // ---- Extras: Do Not Disturb -----------------------------------------------------------------

    suspend fun supportsDnd(): Boolean = run(SysCmd.NOTIFICATION_HELP).output.contains("set_dnd")

    suspend fun dndMode(): DndMode = DndMode.ofZen(read(Namespace.GLOBAL, "zen_mode"))

    suspend fun setDnd(mode: DndMode) {
        originals.rememberIfAbsent(Namespace.CUSTO, ZEN_KEY, dndMode().zen.toString())
        runOk(SysCmd.setDnd(mode))
    }

    // ---- Extras: Quick Settings tile add, write-settings grant -----------------------------------

    suspend fun addTile(component: String) {
        rememberOriginal(Namespace.SECURE, "sysui_qs_tiles")
        runOk(SysCmd.addTile(component))
    }

    suspend fun grantWriteSettings(pkg: String) {
        runOk(SysCmd.grantWriteSettings(pkg))
    }

    // ---- Undo everything ----------------------------------------------------------------------

    /**
     * Puts back every recorded original. Returns human-readable notes for settings that could not be
     * restored exactly (e.g. OEM ROMs that stored a value Android's own validator now rejects).
     */
    suspend fun restoreAll(): List<String> {
        val problems = mutableListOf<String>()
        val notes = mutableListOf<String>()
        for ((nsKey, value) in originals.all()) {
            val (ns, key) = nsKey
            val r = when {
                ns == Namespace.SECURE && key == DENSITY_KEY -> run(ShellCommands.wmDensity(value?.toIntOrNull()))
                ns == Namespace.CUSTO && key == NAV_KEY ->
                    value?.let { run(ShellCommands.overlayEnableExclusiveInCategory(it)) } ?: ShellResult(0, "")
                ns == Namespace.CUSTO && key == STATUSBAR_FLAGS_KEY -> run(SysCmd.sendDisableFlag(emptySet()))
                ns == Namespace.CUSTO && key == WM_SIZE_KEY -> run(SysCmd.wmSize(ScreenSize.parse(value)))
                ns == Namespace.CUSTO && key == CUTOUT_KEY -> restoreCutout(value)
                ns == Namespace.CUSTO && key == ZEN_KEY -> run(SysCmd.setDnd(DndMode.ofZen(value)))
                ns == Namespace.CUSTO -> ShellResult(0, "")
                value == null -> run(ShellCommands.settingsDelete(ns, key))
                else -> run(ShellCommands.settingsPut(ns, key, value))
            }
            when {
                r.ok -> Unit
                // The ROM stored a value Android won't accept back; leave Custo's value in place.
                r.output.contains("Invalid value") -> notes += "$key kept as is (your phone uses a non-standard value)"
                else -> problems += "$key: ${r.error}"
            }
        }
        disableCustoOverlays()
        run(ShellCommands.demoBroadcast("exit"))
        if (problems.isEmpty()) originals.clear()
        return problems + notes
    }

    companion object {
        /** Pseudo key; not written via `settings`, restored with `wm density`. */
        const val DENSITY_KEY = "custo_density_override"
        const val NAV_KEY = "nav_overlay"

        /** Pseudo keys for non-`settings` state, restored specially in [restoreAll]. */
        const val STATUSBAR_FLAGS_KEY = "statusbar_disable_flags"
        const val WM_SIZE_KEY = "wm_size_override"
        const val CUTOUT_KEY = "cutout_overlay"
        const val ZEN_KEY = "zen_mode"
    }
}
