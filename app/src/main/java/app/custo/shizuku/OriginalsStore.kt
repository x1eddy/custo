package app.custo.shizuku

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Remembers the value a system setting had *before* Custo first changed it,
 * so "Restore defaults" can put the phone back exactly as it was.
 */
class OriginalsStore(private val store: DataStore<Preferences>) {

    private fun key(ns: ShellCommands.Namespace, name: String) = stringPreferencesKey("${ns.id}:$name")

    /** Record [current] only if nothing is recorded yet for this setting. */
    suspend fun rememberIfAbsent(ns: ShellCommands.Namespace, name: String, current: String?) {
        store.edit { prefs ->
            val k = key(ns, name)
            if (!prefs.contains(k)) prefs[k] = current ?: NULL
        }
    }

    /** Snapshot of all recorded originals: (namespace, key) -> original value (null = did not exist). */
    suspend fun all(): Map<Pair<ShellCommands.Namespace, String>, String?> =
        store.data.first().asMap().mapNotNull { (k, v) ->
            val (nsId, name) = k.name.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val ns = ShellCommands.Namespace.entries.firstOrNull { it.id == nsId } ?: return@mapNotNull null
            (ns to name) to (v as String).takeUnless { it == NULL }
        }.toMap()

    suspend fun contains(ns: ShellCommands.Namespace, name: String): Boolean = store.data.first().contains(key(ns, name))

    /** Forgets one recorded original (after it was restored successfully). */
    suspend fun remove(ns: ShellCommands.Namespace, name: String) {
        store.edit { it.remove(key(ns, name)) }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    companion object {
        const val NULL = "\u0000null"
    }
}
