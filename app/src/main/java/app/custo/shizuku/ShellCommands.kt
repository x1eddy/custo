package app.custo.shizuku

/** Pure builders for the privileged shell commands Custo uses. Kept free of Android deps for unit tests. */
object ShellCommands {

    /** CUSTO is a pseudo namespace for non-`settings` state (e.g. the navigation overlay) we must restore. */
    enum class Namespace(val id: String) { SYSTEM("system"), SECURE("secure"), GLOBAL("global"), CUSTO("custo") }

    /** Single-quote a value for `sh -c`, escaping embedded single quotes. */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun settingsGet(ns: Namespace, key: String) = "settings get ${ns.id} ${quote(key)}"
    fun settingsPut(ns: Namespace, key: String, value: String) = "settings put ${ns.id} ${quote(key)} ${quote(value)}"
    fun settingsDelete(ns: Namespace, key: String) = "settings delete ${ns.id} ${quote(key)}"

    fun wmDensity(dpi: Int?) = if (dpi == null) "wm density reset" else "wm density $dpi"
    const val WM_DENSITY_GET = "wm density"

    const val OVERLAY_LIST = "cmd overlay list"
    fun overlayEnable(id: String) = "cmd overlay enable --user current ${quote(id)}"
    fun overlayDisable(id: String) = "cmd overlay disable --user current ${quote(id)}"
    fun overlayEnableExclusiveInCategory(id: String) = "cmd overlay enable-exclusive --user current --category ${quote(id)}"

    /** Fabricated overlays are owned by the shell package, so their full id is `com.android.shell:<name>`. */
    const val FABRICATED_PREFIX = "custo_"
    fun fabricatedId(name: String) = "com.android.shell:$FABRICATED_PREFIX$name"

    fun colorArg(argb: Int): String = "0x" + argb.toUInt().toString(16).padStart(8, '0')

    fun demoBroadcast(command: String, extras: Map<String, String> = emptyMap()): String {
        val e = extras.entries.joinToString("") { " -e ${quote(it.key)} ${quote(it.value)}" }
        return "am broadcast -a com.android.systemui.demo -e command ${quote(command)}$e"
    }

    /** `settings get` prints the literal "null" for missing keys. */
    fun parseSettingValue(output: String): String? = output.trim().takeUnless { it == "null" || it.isEmpty() }
}
