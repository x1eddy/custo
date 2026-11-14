package app.custo.feature.systemui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** System-wide Material You palette styles understood by SystemUI's ThemeOverlayController (Android 13+). */
enum class MonetStyle(val id: String, val label: String) {
    TONAL_SPOT("TONAL_SPOT", "Tonal"),
    VIBRANT("VIBRANT", "Vibrant"),
    EXPRESSIVE("EXPRESSIVE", "Expressive"),
    SPRITZ("SPRITZ", "Spritz"),
    RAINBOW("RAINBOW", "Rainbow"),
    FRUIT_SALAD("FRUIT_SALAD", "Fruit salad"),
    CONTENT("CONTENT", "Content"),
    MONOCHROMATIC("MONOCHROMATIC", "Mono");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: TONAL_SPOT
    }
}

/** Builds the JSON stored in `Settings.Secure.theme_customization_overlay_packages`. */
object MonetJson {
    const val SETTING = "theme_customization_overlay_packages"
    private const val P = "android.theme.customization."

    fun hex(color: Int): String = (color and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

    fun build(existing: String?, seed: Int, style: MonetStyle?, timestamp: Long): String {
        val base: Map<String, kotlinx.serialization.json.JsonElement> = existing
            ?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: emptyMap()
        val out = base.toMutableMap()
        out.remove("${P}color_index")
        out["${P}color_source"] = JsonPrimitive("preset")
        out["${P}system_palette"] = JsonPrimitive(hex(seed))
        out["${P}accent_color"] = JsonPrimitive(hex(seed))
        out["${P}color_both"] = JsonPrimitive("1")
        if (style != null) out["${P}theme_style"] = JsonPrimitive(style.id) else out.remove("${P}theme_style")
        out["_applied_timestamp"] = JsonPrimitive(timestamp)
        return JsonObject(out).toString()
    }

    /** Reads the seed color back out of the setting, if Custo (or the system picker) set one. */
    fun seedOf(json: String?): Int? {
        val obj = json?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return null
        val hex = (obj["${P}system_palette"] as? JsonPrimitive)?.content ?: return null
        return hex.removePrefix("#").takeLast(6).toLongOrNull(16)?.toInt()?.or(0xFF000000.toInt())
    }

    fun styleOf(json: String?): MonetStyle? {
        val obj = json?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return null
        return (obj["${P}theme_style"] as? JsonPrimitive)?.content?.let { MonetStyle.of(it) }
    }
}
