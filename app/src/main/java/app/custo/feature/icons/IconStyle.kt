package app.custo.feature.icons

import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Preference keys owned by the Icons feature. */
object IconKeys {
    /** JSON list of [SavedStyle]. */
    val styles = stringPreferencesKey("icon_styles_json")
    /** JSON list of [IconShortcut]: shortcuts Custo asked the launcher to pin. */
    val history = stringPreferencesKey("icon_shortcuts_json")
    /** The style last open in the maker, so it survives leaving the screen. */
    val draft = stringPreferencesKey("icon_style_draft")
}

enum class IconShape(val label: String) {
    CIRCLE("Circle"), SQUIRCLE("Squircle"), ROUNDED("Rounded"), TEARDROP("Teardrop"), HEXAGON("Hexagon"), FLOWER("Flower"),
}

enum class IconPattern(val label: String) {
    NONE("None"), DOTS("Dots"), STRIPES("Stripes"), GRID("Grid"), WAVES("Waves"), CONFETTI("Confetti"),
}

enum class FgMode(val label: String) {
    APP_TINT("App icon, one color"), APP_ORIGINAL("App icon"), GLYPH("Symbol"), TEXT("Letters"),
}

/**
 * A DIY icon look. Every field has a default so styles saved by older versions still decode;
 * unknown enum values fall back to the default (coerceInputValues).
 */
@Serializable
data class IconStyle(
    val shape: IconShape = IconShape.SQUIRCLE,
    /** 1 color = solid, 2-3 = gradient. */
    val bg: List<Int> = listOf(0xFF6D4AFF.toInt(), 0xFFFF6FB5.toInt()),
    val angle: Float = 135f,
    val pattern: IconPattern = IconPattern.NONE,
    val patternColor: Int = 0xFFFFFFFF.toInt(),
    /** 0..1 */
    val patternSize: Float = 0.5f,
    /** 0..1 */
    val patternOpacity: Float = 0.25f,
    /** Soft halo around the foreground, 0..1. */
    val glow: Float = 0f,
    val glowColor: Int = 0xFFFFFFFF.toInt(),
    /** Drop shadow under the foreground (and the shape), 0..1. */
    val shadow: Float = 0.3f,
    val fg: FgMode = FgMode.APP_TINT,
    val fgColor: Int = 0xFFFFFFFF.toInt(),
    /** Foreground size relative to the icon, 0.3..0.9. */
    val fgScale: Float = 0.55f,
    val glyph: String = "Star",
    val text: String = "Aa",
    /** true = full-bleed adaptive icon, the launcher cuts its own shape; false = keep [shape]. */
    val launcherShape: Boolean = false,
    val seed: Int = 7,
) {
    fun encode(): String = IconStyleCodec.json.encodeToString(this)

    /** The same look for many apps: each app keeps its own glyph (letters become the app's initials). */
    fun forBulk(): IconStyle = when (fg) {
        FgMode.GLYPH -> copy(fg = FgMode.APP_TINT)
        else -> this
    }

    companion object {
        fun decode(s: String?): IconStyle? = s?.let { runCatching { IconStyleCodec.json.decodeFromString<IconStyle>(it) }.getOrNull() }

        /** Up to two letters from an app name: "Google Maps" → "GM", "Spotify" → "Sp". */
        fun initials(name: String): String {
            val words = name.split(' ', '-', '_', '.').filter { w -> w.isNotBlank() && w[0].isLetterOrDigit() }
            return when {
                words.isEmpty() -> name.take(1).uppercase()
                words.size == 1 -> words[0].take(2).replaceFirstChar { it.uppercase() }
                else -> (words[0].take(1) + words[1].take(1)).uppercase()
            }
        }
    }
}

@Serializable
data class SavedStyle(val name: String, val style: IconStyle)

/** A shortcut Custo asked the launcher to pin. The icon PNG lives in files/icons/shortcuts/<id>.png. */
@Serializable
data class IconShortcut(
    val id: String,
    val pkg: String,
    val label: String,
    /** "style", "pack:<package>", "image" */
    val source: String = "style",
    val style: IconStyle? = null,
    val createdAt: Long = 0,
)

object IconStyleCodec {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    fun decodeStyles(s: String?): List<SavedStyle> = s?.let { runCatching { json.decodeFromString<List<SavedStyle>>(it) }.getOrNull() } ?: emptyList()
    fun encodeStyles(list: List<SavedStyle>): String = json.encodeToString(list)
    fun decodeHistory(s: String?): List<IconShortcut> = s?.let { runCatching { json.decodeFromString<List<IconShortcut>>(it) }.getOrNull() } ?: emptyList()
    fun encodeHistory(list: List<IconShortcut>): String = json.encodeToString(list)
}

/** Ready-made styles shown before the user saves any. */
val PRESET_STYLES = listOf(
    SavedStyle("Violet", IconStyle()),
    SavedStyle("Mint", IconStyle(IconShape.CIRCLE, listOf(0xFF00BFA5.toInt(), 0xFF64FFDA.toInt()), angle = 90f, pattern = IconPattern.DOTS, patternOpacity = 0.18f)),
    SavedStyle("Ink", IconStyle(IconShape.ROUNDED, listOf(0xFF111111.toInt()), fgColor = 0xFFFFFFFF.toInt(), shadow = 0f)),
    SavedStyle("Paper", IconStyle(IconShape.SQUIRCLE, listOf(0xFFF5F1E8.toInt()), fgColor = 0xFF222222.toInt(), shadow = 0.15f, pattern = IconPattern.GRID, patternColor = 0xFF000000.toInt(), patternOpacity = 0.06f)),
    SavedStyle("Sunset", IconStyle(IconShape.TEARDROP, listOf(0xFFFF6F61.toInt(), 0xFFFFB347.toInt(), 0xFFFF3D7F.toInt()), angle = 45f, pattern = IconPattern.WAVES, patternOpacity = 0.2f)),
    SavedStyle("Neon", IconStyle(IconShape.HEXAGON, listOf(0xFF0B0B1E.toInt(), 0xFF1A1A40.toInt()), fgColor = 0xFF2DFFB3.toInt(), glow = 0.7f, glowColor = 0xFF2DFFB3.toInt(), shadow = 0f)),
    SavedStyle("Bloom", IconStyle(IconShape.FLOWER, listOf(0xFFFFE3F1.toInt(), 0xFFA78BFA.toInt()), fgColor = 0xFF4A2B7A.toInt(), pattern = IconPattern.CONFETTI, patternColor = 0xFFFFFFFF.toInt(), patternOpacity = 0.5f)),
    SavedStyle("Original", IconStyle(IconShape.CIRCLE, listOf(0xFFFFFFFF.toInt()), fg = FgMode.APP_ORIGINAL, fgScale = 0.8f, shadow = 0.2f)),
)
