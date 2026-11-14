package app.custo.feature.themes

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class WallStyle(val label: String, val animated: Boolean = true) {
    MESH("Mesh"), GRADIENT("Gradient"), WAVES("Waves"), DOTS("Dots"), SOLID("Solid", animated = false),
    AURORA("Aurora"), BLOBS("Lava"), LOWPOLY("Low poly"), STRIPES("Stripes"), TOPO("Contours"),
    BOKEH("Bokeh"), SUNBURST("Sunburst"), MOSAIC("Mosaic"), RINGS("Rings"),
}

/**
 * A procedural wallpaper: everything is derived from these fields, so the same spec always
 * renders the same image. New fields must have defaults so older saved looks still decode.
 */
@Serializable
data class WallSpec(
    val style: WallStyle = WallStyle.MESH,
    val colors: List<Int> = listOf(0xFF2B1A6B.toInt(), 0xFF6D4AFF.toInt(), 0xFFFF6FB5.toInt(), 0xFF00C2D1.toInt()),
    val seed: Int = 7,
    /** Direction/rotation in degrees (gradient direction, stripe angle, ray rotation, ribbon tilt). */
    val angle: Float = 135f,
    /** Film grain overlay, 0..1. */
    val grain: Float = 0f,
    /** Softness, 0..1. */
    val blur: Float = 0f,
    /** How many shapes, 0..1. */
    val complexity: Float = 0.5f,
) {
    fun encode(): String = json.encodeToString(this)

    companion object {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        fun decode(s: String?): WallSpec? = s?.let { runCatching { json.decodeFromString<WallSpec>(it) }.getOrNull() }
    }
}
