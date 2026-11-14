package app.custo.feature.widgets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ClockLook(val label: String) { BIG("Big"), STACKED("Stacked"), MINIMAL("Minimal") }
enum class WidgetFont(val label: String) { SANS("Sans"), SERIF("Serif"), MONO("Mono"), CURSIVE("Script") }

/** Per-widget appearance, stored as JSON inside each widget's Glance state. */
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
) {
    fun encode(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(s: String?): WidgetStyle = s?.let { runCatching { json.decodeFromString<WidgetStyle>(it) }.getOrNull() } ?: WidgetStyle()
    }
}
