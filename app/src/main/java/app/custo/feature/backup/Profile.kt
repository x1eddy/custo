package app.custo.feature.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Portable snapshot of Custo's settings. Values carry a one-letter type tag: b/i/l/f/s. */
@Serializable
data class Profile(
    val format: Int = 1,
    val app: String = "custo",
    val createdAt: Long = 0,
    val settings: Map<String, String> = emptyMap(),
    val originals: Map<String, String> = emptyMap(),
) {
    fun encode(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        fun decode(s: String): Profile {
            val p = json.decodeFromString<Profile>(s)
            require(p.app == "custo") { "This file isn't a Custo profile" }
            require(p.format <= 1) { "This profile was made by a newer version of Custo" }
            return p
        }

        fun tag(v: Any): String? = when (v) {
            is Boolean -> "b:$v"
            is Int -> "i:$v"
            is Long -> "l:$v"
            is Float -> "f:$v"
            is String -> "s:$v"
            else -> null
        }

        fun untag(s: String): Any? {
            if (s.length < 2 || s[1] != ':') return null
            val body = s.substring(2)
            return when (s[0]) {
                'b' -> body.toBooleanStrictOrNull()
                'i' -> body.toIntOrNull()
                'l' -> body.toLongOrNull()
                'f' -> body.toFloatOrNull()
                's' -> body
                else -> null
            }
        }
    }
}
