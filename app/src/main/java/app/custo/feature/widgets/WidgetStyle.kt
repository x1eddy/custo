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
) {
    fun encode(): String = json.encodeToString(this)

    /** True when the background can't be a flat Glance color and needs a drawn bitmap. */
    val needsBitmapBg: Boolean get() = (gradient && !themed) || borderWidth > 0

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        fun decode(s: String?): WidgetStyle = s?.let { runCatching { json.decodeFromString<WidgetStyle>(it) }.getOrNull() } ?: WidgetStyle()
    }
}
