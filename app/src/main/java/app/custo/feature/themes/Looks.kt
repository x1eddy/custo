package app.custo.feature.themes

import app.custo.data.Keys
import app.custo.data.Prefs
import app.custo.feature.systemui.MonetStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A complete "look": wallpaper + system palette + status-bar icon set, applied in one tap. */
@Serializable
data class Look(
    val name: String,
    val wall: WallSpec,
    val seed: Int,
    val style: String = MonetStyle.TONAL_SPOT.id,
    val hiddenIcons: List<String>? = null,
    val builtIn: Boolean = false,
)

val BUILT_IN_LOOKS = listOf(
    Look("Aurora", WallSpec(WallStyle.MESH, listOf(0xFF071A2B, 0xFF00E0A4, 0xFF5B5BFF, 0xFF00B3FF).map { it.toInt() }, seed = 3), 0xFF00E0A4.toInt(), "VIBRANT", builtIn = true),
    Look("Sunset", WallSpec(WallStyle.WAVES, listOf(0xFF2A0F3D, 0xFFFF6F61, 0xFFFFB347, 0xFFFF3D7F).map { it.toInt() }, seed = 11), 0xFFFF6F61.toInt(), "EXPRESSIVE", builtIn = true),
    Look("Ocean", WallSpec(WallStyle.GRADIENT, listOf(0xFF001F3F, 0xFF0074D9, 0xFF7FDBFF).map { it.toInt() }, angle = 100f), 0xFF0074D9.toInt(), "TONAL_SPOT", builtIn = true),
    Look("Forest", WallSpec(WallStyle.MESH, listOf(0xFF0B1F14, 0xFF2E7D32, 0xFF8BC34A, 0xFF1B5E20).map { it.toInt() }, seed = 21), 0xFF2E7D32.toInt(), "TONAL_SPOT", builtIn = true),
    Look("Mono", WallSpec(WallStyle.DOTS, listOf(0xFF111111, 0xFF777777, 0xFFDDDDDD).map { it.toInt() }), 0xFF888888.toInt(), "MONOCHROMATIC", builtIn = true),
    Look("Candy", WallSpec(WallStyle.MESH, listOf(0xFFFFE3F1, 0xFFFF7EB6, 0xFFA78BFA, 0xFF7DD3FC).map { it.toInt() }, seed = 5), 0xFFFF7EB6.toInt(), "FRUIT_SALAD", builtIn = true),
)

class LooksRepository(private val prefs: Prefs) {
    private val json = Json { ignoreUnknownKeys = true }

    val saved: Flow<List<Look>> = prefs.flow(Keys.looks, "[]").map { decode(it) }

    private fun decode(s: String): List<Look> = runCatching { json.decodeFromString<List<Look>>(s) }.getOrDefault(emptyList())

    suspend fun save(look: Look) {
        val list = decode(prefs.get(Keys.looks, "[]")).filterNot { it.name == look.name } + look
        prefs.set(Keys.looks, json.encodeToString(list))
    }

    suspend fun delete(name: String) {
        val list = decode(prefs.get(Keys.looks, "[]")).filterNot { it.name == name }
        prefs.set(Keys.looks, json.encodeToString(list))
    }
}
