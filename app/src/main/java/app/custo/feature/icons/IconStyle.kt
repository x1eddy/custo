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
    HEART("Heart"), STAR("Star"), CLOUD("Cloud"), SHIELD("Shield"), OCTAGON("Octagon"), LEAF("Leaf"), DROP("Drop"), BLOB("Pebble"),
}

/** How the background colors are laid out. AUTO keeps the 1.0 rule: one color solid, more = linear. */
enum class BgKind(val label: String) {
    AUTO("Auto"), SOLID("Solid"), LINEAR("Linear"), RADIAL("Radial"), SWEEP("Sweep"), IMAGE("Image"),
}

enum class IconTexture(val label: String) {
    NONE("None"), PAPER("Paper"), GLITTER("Glitter"), NOISE("Grain"), CARBON("Carbon"),
}

enum class Sticker(val label: String) {
    NONE("None"), HEART("Heart"), STAR("Star"), BOW("Bow"), SPARKLE("Sparkle"), CROWN("Crown"),
}

enum class StickerCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

enum class IconPattern(val label: String) {
    NONE("None"), DOTS("Dots"), STRIPES("Stripes"), GRID("Grid"), WAVES("Waves"), CONFETTI("Confetti"),
}

enum class FgMode(val label: String) {
    APP_TINT("App icon, one color"), APP_ORIGINAL("App icon"), GLYPH("Symbol"), TEXT("Letters"),
    SILHOUETTE("Silhouette"), OUTLINE("Outline"), DUOTONE("Duotone"), PACK("Icon pack glyph"),
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
    // --- 1.1: everything below is new, appended so old JSON and positional calls keep working ---
    val bgKind: BgKind = BgKind.AUTO,
    /** Corner radius of [IconShape.ROUNDED], relative to the width, 0..0.5. */
    val corner: Float = 0.22f,
    /** Absolute path of a picture (saved wallpaper or imported photo) used when [bgKind] is IMAGE. */
    val image: String? = null,
    /** 1..4 */
    val imageZoom: Float = 1f,
    /** -1..1, pans within the room the zoom leaves. */
    val imageX: Float = 0f,
    val imageY: Float = 0f,
    /** One picture spread across all the icons it's applied to, in grid order. */
    val puzzle: Boolean = false,
    val texture: IconTexture = IconTexture.NONE,
    /** 0..1 */
    val textureOpacity: Float = 0.45f,
    /** Border width 0..1 (0 = none). */
    val border: Float = 0f,
    val borderColor: Int = 0xFFFFFFFF.toInt(),
    val borderDashed: Boolean = false,
    /** Inner stroke width 0..1 (0 = none). */
    val innerStroke: Float = 0f,
    val innerStrokeColor: Int = 0x66FFFFFF,
    /** Second color for [FgMode.DUOTONE] (highlights; [fgColor] takes the shadows). */
    val fgColor2: Int = 0xFFFFD166.toInt(),
    /** Foreground offset, -0.5..0.5 of the icon. */
    val fgX: Float = 0f,
    val fgY: Float = 0f,
    /** Degrees. */
    val fgRotation: Float = 0f,
    val shadowColor: Int = 0xFF000000.toInt(),
    /** Direction the shadow falls, degrees (90 = down). */
    val shadowAngle: Float = 90f,
    /** 0..1; the 1.0 look is 0.3 / 0.3. */
    val shadowDistance: Float = 0.3f,
    val shadowBlur: Float = 0.3f,
    /** 3-D emboss on the foreground, 0..1. */
    val emboss: Float = 0f,
    /** 3-D bevel on the shape's rim, 0..1. */
    val bevel: Float = 0f,
    /** Glassy highlight over the top, 0..1. */
    val gloss: Float = 0f,
    /** Soft-UI double shadow (light top-left, dark bottom-right), 0..1. */
    val neumorph: Float = 0f,
    val sticker: Sticker = Sticker.NONE,
    val stickerCorner: StickerCorner = StickerCorner.TOP_END,
    val stickerColor: Int = 0xFFFF4D8D.toInt(),
    /** Every app gets its own hue (seeded by [seed] + package), keeping lightness and saturation. */
    val rainbow: Boolean = false,
    /** Icon pack package used by [FgMode.PACK]. */
    val pack: String = "",
) {
    fun encode(): String = IconStyleCodec.json.encodeToString(this)

    /** The same look for many apps: each app keeps its own glyph (letters become the app's initials). */
    fun forBulk(): IconStyle = when (fg) {
        FgMode.GLYPH -> copy(fg = FgMode.APP_TINT)
        else -> this
    }

    /** The look for one app: applies the per-app palette when [rainbow] is on. */
    fun forApp(pkg: String?): IconStyle {
        if (!rainbow || pkg == null) return this
        val hue = IconPalette.hueFor(pkg, seed)
        return copy(
            bg = bg.map { IconPalette.rotateHue(it, hue) },
            glowColor = IconPalette.rotateHue(glowColor, hue),
            patternColor = IconPalette.rotateHue(patternColor, hue),
            rainbow = false,
        )
    }

    /** Uses a picture background that can actually be drawn. */
    val usesImage: Boolean get() = bgKind == BgKind.IMAGE && !image.isNullOrEmpty()

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
    /** Per element: one broken entry is skipped, the rest still load. */
    fun decodeStyles(s: String?): List<SavedStyle> = app.custo.data.LenientList.decode(SavedStyle.serializer(), s)
    fun encodeStyles(list: List<SavedStyle>): String = json.encodeToString(list)
    fun decodeHistory(s: String?): List<IconShortcut> = app.custo.data.LenientList.decode(IconShortcut.serializer(), s)
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
