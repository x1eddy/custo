package app.custo.feature.profiles

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.custo.feature.systemui.SystemTweaks
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.shizuku.ShizukuManager
import app.custo.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Applies a per-app profile when its app comes to the front and puts the previous values back
 * when the user leaves. Fed by the overlay (accessibility) service's window-change events.
 *
 * What to put back is persisted, so it survives process death and a partly failed apply: the next
 * time the engine runs with no profile app in front, the saved values are restored.
 */
class ProfileEngine(
    private val repo: ProfilesRepository,
    private val tweaks: SystemTweaks,
    private val shizuku: ShizukuManager,
    private val store: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var loaded = false
    private var activeId: String? = null
    /** Values that were in place before [activeId] was applied. */
    private var saved: Map<Pair<Namespace, String>, String?> = emptyMap()
    private var pending: Job? = null
    @Volatile private var lastPkg: String? = null
    @Volatile private var seenForeground = false

    init {
        // Shizuku often becomes ready after the first window events: re-run once it does.
        scope.launch {
            shizuku.state.collect { if (it == ShizukuState.READY && seenForeground) onForegroundApp(lastPkg) }
        }
    }

    /** Call on every foreground-app change. Debounced so app-switch animations don't thrash settings. */
    fun onForegroundApp(pkg: String?) {
        lastPkg = pkg
        seenForeground = true
        pending?.cancel()
        pending = scope.launch {
            delay(400)
            mutex.withLock { switchTo(pkg) }
        }
    }

    /** Forgets everything (Undo all restores the real originals itself). */
    suspend fun reset() {
        pending?.cancel()
        mutex.withLock {
            activeId = null
            saved = emptyMap()
            loaded = true
            store.edit { it.clear() }
        }
    }

    private suspend fun load() {
        if (loaded) return
        val p = store.data.first()
        activeId = p[ACTIVE]
        saved = p[SAVED]?.let { decodeSaved(it) } ?: emptyMap()
        loaded = true
    }

    private suspend fun persist() {
        store.edit { p ->
            val id = activeId
            if (id == null) p.remove(ACTIVE) else p[ACTIVE] = id
            if (saved.isEmpty()) p.remove(SAVED) else p[SAVED] = encodeSaved(saved)
        }
    }

    private suspend fun switchTo(pkg: String?) {
        if (shizuku.state.value != ShizukuState.READY) return
        runCatching { load() }
        val target = ProfileLogic.forPackage(pkg, repo.all())
        if (target?.id == activeId && (target != null || saved.isEmpty())) return
        runCatching {
            // Put back what the previous profile changed, forgetting each value once it is back.
            for ((k, v) in saved) {
                tweaks.write(k.first, k.second, v)
                saved = saved - k
                persist()
            }
            activeId = null
            persist()
            if (target != null) {
                val changes = ProfileLogic.changes(target)
                // Saved (and marked active) before the first write, so a partial failure still restores.
                saved = changes.associate { (it.ns to it.key) to tweaks.readStrict(it.ns, it.key) }
                activeId = target.id
                persist()
                changes.forEach { tweaks.write(it.ns, it.key, it.value) }
            }
        }
    }

    private companion object {
        val ACTIVE = stringPreferencesKey("active_profile")
        val SAVED = stringPreferencesKey("saved_values")
        val json = Json

        fun encodeSaved(m: Map<Pair<Namespace, String>, String?>): String =
            json.encodeToString(m.mapKeys { (k, _) -> "${k.first.id}:${k.second}" })

        fun decodeSaved(s: String): Map<Pair<Namespace, String>, String?> =
            runCatching { json.decodeFromString<Map<String, String?>>(s) }.getOrDefault(emptyMap()).mapNotNull { (k, v) ->
                val (nsId, key) = k.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                val ns = Namespace.entries.firstOrNull { it.id == nsId } ?: return@mapNotNull null
                (ns to key) to v
            }.toMap()
    }
}
