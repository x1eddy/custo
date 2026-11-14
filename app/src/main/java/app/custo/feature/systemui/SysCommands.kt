package app.custo.feature.systemui

import app.custo.shizuku.ShellCommands.quote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.math.roundToInt

/** `cmd statusbar send-disable-flag` flags Custo exposes (AOSP StatusBarShellCommand names). */
enum class DisableFlag(val id: String, val label: String) {
    CLOCK("clock", "Hide clock"),
    NOTIFICATION_ICONS("notification-icons", "Hide notification icons"),
    SYSTEM_ICONS("system-icons", "Hide system icons"),
    NOTIFICATION_ALERTS("notification-alerts", "Block pop-up notifications"),
    EXPANSION("statusbar-expansion", "Lock the shade");

    companion object {
        fun parse(stored: String?): Set<DisableFlag> =
            stored.orEmpty().split(',').mapNotNull { s -> entries.firstOrNull { it.id == s.trim() } }.toSet()

        fun store(flags: Set<DisableFlag>): String = entries.filter { it in flags }.joinToString(",") { it.id }
    }
}

/** Do Not Disturb modes understood by `cmd notification set_dnd`, keyed by `global zen_mode`. */
enum class DndMode(val arg: String, val zen: Int, val label: String) {
    OFF("off", 0, "Off"),
    PRIORITY("priority", 1, "Priority"),
    ALARMS("alarms", 3, "Alarms only"),
    SILENCE("on", 2, "Total silence");

    companion object {
        fun ofZen(value: String?): DndMode = entries.firstOrNull { it.zen == value?.trim()?.toIntOrNull() } ?: OFF
    }
}

enum class PrivateDnsMode(val id: String, val label: String) {
    OFF("off", "Off"),
    AUTO("opportunistic", "Automatic"),
    HOSTNAME("hostname", "Custom");

    companion object {
        fun of(id: String?): PrivateDnsMode = entries.firstOrNull { it.id == id } ?: AUTO

        /** Off → Automatic → Custom (only if a hostname is set) → Off. */
        fun next(current: PrivateDnsMode, hasHostname: Boolean): PrivateDnsMode = when (current) {
            OFF -> AUTO
            AUTO -> if (hasHostname) HOSTNAME else OFF
            HOSTNAME -> OFF
        }
    }
}

data class ScreenSize(val width: Int, val height: Int) {
    override fun toString() = "${width}x$height"

    companion object {
        fun parse(s: String?): ScreenSize? {
            val m = Regex("(\\d+)\\s*x\\s*(\\d+)").find(s ?: return null) ?: return null
            return ScreenSize(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
    }
}

/** Pure builders and parsers for the extra system tweaks. No Android deps, so they unit-test on the JVM. */
object SysCmd {
    const val STATUSBAR_HELP = "cmd statusbar help"
    const val NOTIFICATION_HELP = "cmd notification help"
    const val WM_SIZE_GET = "wm size"
    const val NO_CUTOUT = "com.android.internal.display.cutout.emulation.noCutout"
    private const val CUTOUT_PREFIX = "com.android.internal.display.cutout.emulation"

    /** No flags clears every flag Custo set. */
    fun sendDisableFlag(flags: Set<DisableFlag>): String =
        (listOf("cmd statusbar send-disable-flag") + DisableFlag.entries.filter { it in flags }.map { it.id }).joinToString(" ")

    fun setDnd(mode: DndMode) = "cmd notification set_dnd ${mode.arg}"

    fun wmSize(size: ScreenSize?) = if (size == null) "wm size reset" else "wm size $size"

    fun addTile(component: String) = "cmd statusbar add-tile ${quote(component)}"

    fun grantWriteSettings(pkg: String) = "appops set ${quote(pkg)} WRITE_SETTINGS allow"

    fun settingsList(ns: String) = "settings list $ns"

    fun getprop(name: String) = "getprop ${quote(name)}"

    fun overlayEnableExclusive(id: String) = "cmd overlay enable-exclusive --user current --category ${quote(id)}"
    fun overlayDisable(id: String) = "cmd overlay disable --user current ${quote(id)}"

    /** `wm size` → (physical, override-or-null). */
    fun parseWmSize(out: String): Pair<ScreenSize?, ScreenSize?> {
        fun grab(label: String) = Regex("$label size: (\\d+x\\d+)").find(out)?.groupValues?.get(1)?.let { ScreenSize.parse(it) }
        return grab("Physical") to grab("Override")
    }

    /** `settings list` prints `key=value` lines; values may themselves contain '='. */
    fun parseSettingsList(out: String): List<Pair<String, String>> =
        out.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.contains('=') && !it.startsWith(' ') }
            .map { it.substringBefore('=') to it.substringAfter('=') }
            .filter { it.first.isNotBlank() }
            .sortedBy { it.first.lowercase() }
            .toList()

    /** Overlay package currently enabled in the display-cutout category, if any. */
    fun enabledCutout(overlayList: String): String? =
        overlayList.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("[x]") && it.contains(CUTOUT_PREFIX) }
            ?.substringAfter("]")?.trim()

    /** The enabled navigation-mode overlay (`com.android.internal.systemui.navbar.*`), if any. */
    fun enabledNavOverlay(overlayList: String): String? =
        overlayList.lineSequence().map { it.trim() }
            .filter { it.startsWith("[x]") }
            .map { it.substringAfter("]").trim() }
            .firstOrNull { id -> NavMode.entries.any { id == it.overlay || id.startsWith(it.overlay + "_") } }

    fun hasOverlay(overlayList: String, id: String): Boolean =
        overlayList.lineSequence().any { line -> line.trim().substringAfter("]").trim() == id }

    /** Lower resolutions with the same aspect ratio (Pixel-style "screen resolution"). Widths rounded to even. */
    fun resolutionChoices(physical: ScreenSize): List<ScreenSize> {
        val widths = listOf(1440, 1080, 720).filter { it < physical.width }
        return listOf(physical) + widths.map { w ->
            val h = (physical.height.toDouble() * w / physical.width).roundToInt()
            ScreenSize(w, h - h % 2)
        }
    }

    /** Keeps things the same physical size when the pixel count changes. */
    fun scaledDensity(currentDpi: Int, from: ScreenSize, to: ScreenSize): Int =
        (currentDpi.toDouble() * to.width / from.width).roundToInt()

    /** Lock-screen clock setting is JSON like `{"clockId":"DEFAULT"}`; keep any other fields. */
    fun clockFaceJson(existing: String?, clockId: String): String {
        val base = existing?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: emptyMap()
        val out = base.toMutableMap()
        out["clockId"] = JsonPrimitive(clockId)
        return JsonObject(out).toString()
    }

    fun clockIdOf(json: String?): String? =
        json?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?.get("clockId")?.let { (it as? JsonPrimitive)?.content }
}
