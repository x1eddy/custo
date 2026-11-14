package app.custo.feature.themes

import app.custo.data.Keys
import app.custo.data.Prefs
import app.custo.feature.icons.IconStyle
import app.custo.feature.systemui.MonetStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A complete "look": wallpaper + system palette + status-bar icon set, applied in one tap.
 * [icons] is an optional app-icon style (added later; null in older saved looks).
 */
@Serializable
data class Look(
    val name: String,
    val wall: WallSpec,
    val seed: Int,
    val style: String = MonetStyle.TONAL_SPOT.id,
    val hiddenIcons: List<String>? = null,
    val builtIn: Boolean = false,
    val icons: IconStyle? = null,
)

val BUILT_IN_LOOKS = listOf(
    Look("Aurora", WallSpec(WallStyle.MESH, listOf(0xFF071A2B, 0xFF00E0A4, 0xFF5B5BFF, 0xFF00B3FF).map { it.toInt() }, seed = 3), 0xFF00E0A4.toInt(), "VIBRANT", builtIn = true),
    Look("Sunset", WallSpec(WallStyle.WAVES, listOf(0xFF2A0F3D, 0xFFFF6F61, 0xFFFFB347, 0xFFFF3D7F).map { it.toInt() }, seed = 11), 0xFFFF6F61.toInt(), "EXPRESSIVE", builtIn = true),
    Look("Ocean", WallSpec(WallStyle.GRADIENT, listOf(0xFF001F3F, 0xFF0074D9, 0xFF7FDBFF).map { it.toInt() }, angle = 100f), 0xFF0074D9.toInt(), "TONAL_SPOT", builtIn = true),
    Look("Forest", WallSpec(WallStyle.MESH, listOf(0xFF0B1F14, 0xFF2E7D32, 0xFF8BC34A, 0xFF1B5E20).map { it.toInt() }, seed = 21), 0xFF2E7D32.toInt(), "TONAL_SPOT", builtIn = true),
    Look("Mono", WallSpec(WallStyle.DOTS, listOf(0xFF111111, 0xFF777777, 0xFFDDDDDD).map { it.toInt() }), 0xFF888888.toInt(), "MONOCHROMATIC", builtIn = true),
    Look("Candy", WallSpec(WallStyle.MESH, listOf(0xFFFFE3F1, 0xFFFF7EB6, 0xFFA78BFA, 0xFF7DD3FC).map { it.toInt() }, seed = 5), 0xFFFF7EB6.toInt(), "FRUIT_SALAD", builtIn = true),
    Look("Northern", WallSpec(WallStyle.AURORA, listOf(0xFF050B1A, 0xFF2DFFB3, 0xFF7A5CFF, 0xFF00C8FF).map { it.toInt() }, seed = 8, grain = 0.25f), 0xFF2DFFB3.toInt(), "VIBRANT", builtIn = true),
    Look("Lava", WallSpec(WallStyle.BLOBS, listOf(0xFF1A0505, 0xFFFF4D1F, 0xFFFFA62B, 0xFFD7263D).map { it.toInt() }, seed = 4, blur = 0.35f), 0xFFFF4D1F.toInt(), "EXPRESSIVE", builtIn = true),
    Look("Facets", WallSpec(WallStyle.LOWPOLY, listOf(0xFF0F2027, 0xFF203A43, 0xFF2C5364, 0xFF5FA8D3).map { it.toInt() }, seed = 12, angle = 70f), 0xFF2C5364.toInt(), "TONAL_SPOT", builtIn = true),
    Look("Retro", WallSpec(WallStyle.STRIPES, listOf(0xFFF4E1C1, 0xFFE76F51, 0xFFF4A261, 0xFF2A9D8F, 0xFF264653).map { it.toInt() }, seed = 2, angle = 30f, grain = 0.35f), 0xFFE76F51.toInt(), "EXPRESSIVE", builtIn = true),
    Look("Terrain", WallSpec(WallStyle.TOPO, listOf(0xFF10140F, 0xFF9BC53D, 0xFFE8D7A9, 0xFF5B8C5A).map { it.toInt() }, seed = 17), 0xFF9BC53D.toInt(), "TONAL_SPOT", builtIn = true),
    Look("City lights", WallSpec(WallStyle.BOKEH, listOf(0xFF0B0B1E, 0xFFFFC857, 0xFFFF6B6B, 0xFF4ECDC4).map { it.toInt() }, seed = 9, blur = 0.15f), 0xFFFFC857.toInt(), "VIBRANT", builtIn = true),
    Look("Rays", WallSpec(WallStyle.SUNBURST, listOf(0xFFFF7A00, 0xFFFFD166, 0xFFFFF3B0).map { it.toInt() }, seed = 6, angle = 10f, grain = 0.2f), 0xFFFF9F1C.toInt(), "EXPRESSIVE", builtIn = true),
    Look("Tiles", WallSpec(WallStyle.MOSAIC, listOf(0xFF14213D, 0xFF3A86FF, 0xFF8338EC, 0xFFFF006E).map { it.toInt() }, seed = 14, angle = 120f), 0xFF3A86FF.toInt(), "VIBRANT", builtIn = true),
    Look("Ripple", WallSpec(WallStyle.RINGS, listOf(0xFF1B1B3A, 0xFF693668, 0xFFA74482, 0xFFF84AA7).map { it.toInt() }, seed = 3, complexity = 0.7f), 0xFFA74482.toInt(), "FRUIT_SALAD", builtIn = true),
)

/** JSON for looks; tolerant of fields and styles added or removed between versions. */
object LookCodec {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    fun decodeList(s: String): List<Look> = runCatching { json.decodeFromString<List<Look>>(s) }.getOrDefault(emptyList())
    fun encode(look: Look): String = json.encodeToString(look)
    fun decode(s: String?): Look? = s?.let { runCatching { json.decodeFromString<Look>(it) }.getOrNull() }
}

class LooksRepository(private val prefs: Prefs) {
    private val json = LookCodec.json

    val saved: Flow<List<Look>> = prefs.flow(Keys.looks, "[]").map { decode(it) }

    private fun decode(s: String): List<Look> = LookCodec.decodeList(s)

    suspend fun save(look: Look) {
        val list = decode(prefs.get(Keys.looks, "[]")).filterNot { it.name == look.name } + look
        prefs.set(Keys.looks, json.encodeToString(list))
    }

    suspend fun delete(name: String) {
        val list = decode(prefs.get(Keys.looks, "[]")).filterNot { it.name == name }
        prefs.set(Keys.looks, json.encodeToString(list))
    }
}
