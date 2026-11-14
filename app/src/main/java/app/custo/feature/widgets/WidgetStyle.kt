package app.custo.feature.widgets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ClockLook(val label: String) { BIG("Big"), STACKED("Stacked"), MINIMAL("Minimal") }
enum class WidgetFont(val label: String) { SANS("Sans"), SERIF("Serif"), MONO("Mono"), CURSIVE("Script") }
enum class HAlign(val label: String) { START("Left"), CENTER("Center"), END("Right") }
enum class VAlign(val label: String) { TOP("Top"), CENTER("Middle"), BOTTOM("Bottom") }
enum class DialStyle(val label: String) { MINIMAL("Minimal"), CLASSIC("Classic"), DOTS("Dots"), BOLD("Bold"), RING("Ring") }

/**
 * Per-widget appearance, stored as JSON inside each widget's Glance state.
 * Every field has a default so JSON saved by older versions still decodes.
 */
@Serializable
data class WidgetStyle(
    val clock: ClockLook = ClockLook.BIG,
    val themed: Boolean = true,
    val bg: Int = 0xFF1C1B1F.toInt(),
    val fg: Int = 0xFFFFFFFF.toInt(),
    val alpha: Float = 1f,
    val corner: Int = 28,
    val font: WidgetFont = WidgetFont.SANS,
    val showDate: Boolean = true,
    val note: String = "Tap to write a note in Custo",
    // Added in 0.2
    val gradient: Boolean = false,
    val bg2: Int = 0xFF6D4AFF.toInt(),
    val gradientAngle: Int = 135,
    val borderWidth: Int = 0,
    val borderColor: Int = 0xFFFFFFFF.toInt(),
    val padding: Int = 16,
    val hAlign: HAlign = HAlign.CENTER,
    val vAlign: VAlign = VAlign.CENTER,
    val textScale: Float = 1f,
    // Kind-specific
    val dial: DialStyle = DialStyle.MINIMAL,
    val seconds: Boolean = false,
    val zones: List<String> = listOf("Europe/London", "America/New_York", "Asia/Tokyo"),
    val yearProgress: Boolean = true,
    val mondayFirst: Boolean = true,
    val countdownTitle: String = "New year",
    /** Target date as days since 1970-01-01 (local). 0 = next New Year's Day. */
    val countdownDay: Long = 0,
    val photoUri: String? = null,
    val apps: List<String> = emptyList(),
    val showLabels: Boolean = true,
    val showAuthor: Boolean = true,
    val showPercent: Boolean = true,
    // Added in 0.3
    val textShadow: Boolean = false,
    val shadowColor: Int = 0x99000000.toInt(),
    val drink: Drink = Drink.WATER,
    val moodFaces: Boolean = false,
    /** Work hours as minutes after midnight, and ISO workdays (1 = Monday). */
    val workStart: Int = 9 * 60,
    val workEnd: Int = 17 * 60,
    val workDays: List<Int> = listOf(1, 2, 3, 4, 5),
    /** ISO weekday to count down to; 6 means the weekend. */
    val weekDay: Int = 5,
    val name1: String = "",
    val name2: String = "",
    val photoUri2: String? = null,
    /** Day the couple got together, as days since 1970-01-01. 0 = not set. */
    val sinceDay: Long = 0,
    val photos: List<String> = emptyList(),
    val rotateMinutes: Int = 30,
    /** A city from [CITIES], or blank for the custom coordinates below. */
    val city: String = "London",
    val lat: Double = 51.5074,
    val lon: Double = -0.1278,
    val zone: String = "",
    val prayerMethod: PrayerMethod = PrayerMethod.MWL,
    val hanafi: Boolean = false,
    val stepGoal: Int = 8000,
    val showSun: Boolean = true,
    // Added in 0.4: widget sets
    /** A [app.custo.feature.widgetsets.WidgetTheme] id. When set, the theme draws the frame, colors and stickers. */
    val themeId: String? = null,
    /** The kind this theme style was made for (picks the stickers). */
    val themeKind: String = "",
    /** Varies the stickers between widgets of the same kind in one set. */
    val themeSlot: Int = 0,
    val bannerText: String = "Have a nice day",
    val bannerSub: String = "",
) {
    fun encode(): String = json.encodeToString(this)

    /** True when the background can't be a flat Glance color and needs a drawn bitmap. */
    val needsBitmapBg: Boolean get() = (gradient && !themed) || borderWidth > 0

    fun place(): Place = findCity(city) ?: Place("Custom", lat.coerceIn(-90.0, 90.0), lon.coerceIn(-180.0, 180.0), zone)

    /** This widget's content with [other]'s look (colors, shape, text, alignment). */
    fun withLookOf(other: WidgetStyle): WidgetStyle = copy(
        themed = other.themed, bg = other.bg, fg = other.fg, alpha = other.alpha, corner = other.corner, font = other.font,
        gradient = other.gradient, bg2 = other.bg2, gradientAngle = other.gradientAngle, borderWidth = other.borderWidth,
        borderColor = other.borderColor, padding = other.padding, hAlign = other.hAlign, vAlign = other.vAlign,
        textScale = other.textScale, textShadow = other.textShadow, shadowColor = other.shadowColor,
        themeId = other.themeId,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        private val presetJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; prettyPrint = true; encodeDefaults = true }

        fun decode(s: String?): WidgetStyle = s?.let { runCatching { json.decodeFromString<WidgetStyle>(it) }.getOrNull() } ?: WidgetStyle()

        /** A shareable preset file: only the look, never notes, photos or app lists. */
        fun exportPreset(style: WidgetStyle, kind: String): String =
            presetJson.encodeToString(WidgetPreset(kind = kind, style = WidgetStyle().withLookOf(style)))

        /** Reads a preset file (or a bare style JSON). Null if it isn't one. */
        fun importPreset(text: String): WidgetStyle? = runCatching {
            val el = presetJson.parseToJsonElement(text)
            val obj = el as? kotlinx.serialization.json.JsonObject ?: return null
            if (obj["format"] != null) presetJson.decodeFromJsonElement(WidgetPreset.serializer(), obj).style
            else presetJson.decodeFromJsonElement(serializer(), obj)
        }.getOrNull()
    }
}

@Serializable
data class WidgetPreset(val format: String = "custo-widget-style", val version: Int = 1, val kind: String = "", val style: WidgetStyle = WidgetStyle())
