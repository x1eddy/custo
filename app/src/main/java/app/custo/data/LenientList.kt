package app.custo.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * Saved JSON lists that survive bad entries: each element is decoded on its own, so one broken or
 * outdated entry no longer makes the whole list read as empty (and get overwritten on next save).
 */
object LenientList {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** The raw array, or null if [s] isn't a JSON array at all. Blank/absent counts as empty. */
    fun parse(s: String?): JsonArray? {
        if (s.isNullOrBlank()) return JsonArray(emptyList())
        return runCatching { json.parseToJsonElement(s) as? JsonArray }.getOrNull()
    }

    /** Every element that decodes; broken ones are skipped. Empty if [s] can't be parsed. */
    fun <T> decode(ser: KSerializer<T>, s: String?): List<T> =
        parse(s)?.mapNotNull { e -> runCatching { json.decodeFromJsonElement(ser, e) }.getOrNull() } ?: emptyList()

    /**
     * Read-modify-write on the raw string: [transform] gets the decodable elements; elements that
     * don't decode are kept verbatim at the end so nothing is lost. Returns null (refuse to write)
     * when [raw] isn't a JSON array.
     */
    fun <T> update(ser: KSerializer<T>, raw: String?, transform: (List<T>) -> List<T>): String? {
        val arr = parse(raw) ?: return null
        val good = mutableListOf<T>()
        val bad = mutableListOf<JsonElement>()
        arr.forEach { e ->
            runCatching { json.decodeFromJsonElement(ser, e) }.onSuccess { good += it }.onFailure { bad += e }
        }
        return JsonArray(transform(good).map { json.encodeToJsonElement(ser, it) } + bad).toString()
    }

    fun <T> encode(ser: KSerializer<T>, list: List<T>): String = JsonArray(list.map { json.encodeToJsonElement(ser, it) }).toString()

    class UnreadableListException(what: String) :
        IllegalStateException("Couldn't read your saved $what, so nothing was overwritten (a backup copy was kept). Try again to start a fresh list.")
}

/**
 * Updates the JSON list at [key] inside one atomic DataStore edit. If the stored string is not a
 * list at all, it is copied to `<key>_backup` and the save is refused rather than wiping it.
 */
suspend fun <T> Prefs.updateList(
    key: Preferences.Key<String>,
    ser: KSerializer<T>,
    what: String,
    transform: (List<T>) -> List<T>,
) {
    var failed = false
    val backupKey = stringPreferencesKey(key.name + "_backup")
    edit { p ->
        failed = false
        val raw = p[key]
        val out = LenientList.update(ser, raw, transform)
        when {
            out != null -> p[key] = out
            // Already backed up and the user was told: start a fresh list (the old text stays in the backup).
            p[backupKey] == raw -> p[key] = LenientList.update(ser, null, transform)!!
            else -> { p[backupKey] = raw.orEmpty(); failed = true }
        }
    }
    if (failed) throw LenientList.UnreadableListException(what)
}
