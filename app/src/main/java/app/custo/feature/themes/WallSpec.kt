package app.custo.feature.themes

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wallpaper styles. [premium] ones are dark material looks ([PremiumWalls]) that use their own palette
 * plus the spec's [WallSpec.edge] accent instead of [WallSpec.colors]. Never rename: specs store the name.
 */
enum class WallStyle(val label: String, val animated: Boolean = true, val premium: Boolean = false) {
    MESH("Mesh"), GRADIENT("Gradient"), WAVES("Waves"), DOTS("Dots"), SOLID("Solid", animated = false),
    AURORA("Aurora"), BLOBS("Lava"), LOWPOLY("Low poly"), STRIPES("Stripes"), TOPO("Contours"),
    BOKEH("Bokeh"), SUNBURST("Sunburst"), MOSAIC("Mosaic"), RINGS("Rings"),
    CARBON("Carbon bands", animated = false, premium = true),
    HEX("Hex mesh", animated = false, premium = true),
    BRUSHED("Brushed metal", animated = false, premium = true),
    WEAVE("Carbon fiber", animated = false, premium = true),
    MARBLE("Dark marble", animated = false, premium = true),
    OBSIDIAN("Obsidian", animated = false, premium = true),
}

/** Accent for the premium styles: edge highlights, veins and rims. */
enum class WallEdge(val label: String, val color: Int) {
    GOLD("Gold", 0xFFD9B26A.toInt()), SILVER("Silver", 0xFFD6DAE0.toInt()),
    ROSE("Rose gold", 0xFFE7A792.toInt()), BLUE("Steel blue", 0xFF7FA9E8.toInt()),
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
    /** Accent of the premium styles (added in 1.1). */
    val edge: WallEdge = WallEdge.GOLD,
) {
    fun encode(): String = json.encodeToString(this)

    companion object {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        fun decode(s: String?): WallSpec? = s?.let { runCatching { json.decodeFromString<WallSpec>(it) }.getOrNull() }
    }
}
