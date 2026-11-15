package app.custo.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the cached probes belong to. Any change (OTA, new SDK, app update) forces a rescan. */
@Serializable
data class DeviceIdentity(val fingerprint: String = "", val sdk: Int = 0, val app: String = "")

/**
 * Remembered device state.
 * - [probes]: capability results (rarely change); key -> value, booleans as "1"/"0".
 * - [values]: last-known value for each watched id. Settings use `ns:key` ("secure:clock_seconds");
 *   raw command output uses `raw:<section>`. A present key with a null value means "unset".
 */
@Serializable
data class DeviceCache(
    val identity: DeviceIdentity = DeviceIdentity(),
    val probes: Map<String, String> = emptyMap(),
    val values: Map<String, String?> = emptyMap(),
) {
    fun hasValues(ids: Collection<String>): Boolean = ids.all { values.containsKey(it) }
    fun hasProbes(keys: Collection<String>): Boolean = keys.all { probes.containsKey(it) }
}

/** Result of parsing one batched refresh. [lists] holds every setting per namespace (memory only). */
data class BatchValues(val values: Map<String, String?>, val lists: Map<String, List<Pair<String, String>>>)

/** Pure logic behind [DeviceStateRepository]; JVM unit-tested. */
object DeviceCacheLogic {
    private val json = Json { ignoreUnknownKeys = true }

    const val RAW = "raw:"
    val NAMESPACES = listOf("system", "secure", "global")

    /** Raw sections kept in [DeviceCache.values] as `raw:<id>`. */
    val RAW_SECTIONS = listOf("wm_size" to "wm size", "wm_density" to "wm density", "overlays" to "cmd overlay list")

    /** Everything one refresh reads: three `settings list` calls are far cheaper than one `settings get` per key. */
    val SECTIONS: List<Pair<String, String>> = NAMESPACES.map { it to "settings list $it" } + RAW_SECTIONS

    /** Only these overlay lines matter to the UI (navigation mode, notch, Custo's own); the rest is noise. */
    private val OVERLAY_KEEP = listOf(".navbar.", "cutout.emulation", "com.android.shell:")

    fun encode(cache: DeviceCache): String = json.encodeToString(DeviceCache.serializer(), cache)

    fun decode(text: String?): DeviceCache? =
        text?.let { runCatching { json.decodeFromString(DeviceCache.serializer(), it) }.getOrNull() }

    /**
     * The cache to use on a device with [current] identity. Same identity: as is. Different (new
     * fingerprint, SDK or app build): probes are dropped so they get recomputed; values are kept as a
     * best guess to render from until the background refresh replaces them.
     */
    fun validate(cache: DeviceCache?, current: DeviceIdentity): DeviceCache = when {
        cache == null -> DeviceCache(identity = current)
        cache.identity == current -> cache
        else -> cache.copy(identity = current, probes = emptyMap())
    }

    /** `settings list` prints `key=value` lines; "null" means unset. */
    fun parseSettingsList(out: String): Map<String, String?> {
        val map = LinkedHashMap<String, String?>()
        out.lineSequence().map { it.trimEnd('\r') }
            .filter { it.contains('=') && !it.startsWith(' ') }
            .forEach { line ->
                val k = line.substringBefore('=')
                if (k.isNotBlank() && k.none { it.isWhitespace() }) {
                    map[k] = line.substringAfter('=').takeUnless { it == "null" }
                }
            }
        return map
    }

    /**
     * Turns the batched output ([sections] from ShellBatch.split) into values for the [watched] ids
     * (+ all raw sections). Ids of a namespace whose section is missing or looks failed are left out,
     * so a broken read never overwrites a good cached value with "unset".
     */
    fun parseBatch(sections: Map<String, String>, watched: Set<String>): BatchValues {
        val lists = HashMap<String, Map<String, String?>>()
        for (ns in NAMESPACES) {
            val out = sections[ns] ?: continue
            if (out.contains("Exception") && !out.contains('=')) continue
            lists[ns] = parseSettingsList(out)
        }
        val values = LinkedHashMap<String, String?>()
        for (id in watched) {
            val ns = id.substringBefore(':', "")
            val list = lists[ns] ?: continue
            values[id] = list[id.substringAfter(':')]
        }
        for ((section, _) in RAW_SECTIONS) {
            var out = sections[section] ?: continue
            if (section == "overlays") out = out.lineSequence().filter { l -> OVERLAY_KEEP.any { l.contains(it) } }.joinToString("\n")
            values[RAW + section] = out
        }
        val sorted = lists.mapValues { (_, m) ->
            m.entries.map { it.key to (it.value ?: "null") }.sortedBy { it.first.lowercase() }
        }
        return BatchValues(values, sorted)
    }

    /** Ids whose value differs between [old] and [new] (including appearing or disappearing). */
    fun diff(old: Map<String, String?>, new: Map<String, String?>): Set<String> =
        (old.keys + new.keys).filterTo(LinkedHashSet()) { k -> old.containsKey(k) != new.containsKey(k) || old[k] != new[k] }

    /**
     * [old] updated with [fresh], except for ids in [skip] (written by the app while the refresh ran:
     * the optimistic value wins). Returns null when nothing changes, so the UI isn't touched.
     */
    fun merge(old: Map<String, String?>, fresh: Map<String, String?>, skip: Set<String> = emptySet()): Map<String, String?>? {
        val apply = fresh.filterKeys { it !in skip }
        val changed = apply.any { (k, v) -> !old.containsKey(k) || old[k] != v }
        return if (changed) old + apply else null
    }
}
