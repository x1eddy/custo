package app.custo.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.custo.BuildConfig
import app.custo.shizuku.Shell
import app.custo.shizuku.ShellBatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

val Context.deviceStateStore: DataStore<Preferences> by preferencesDataStore("device_state")

/** Told about every system change the app makes, so cached state never lags behind its own writes. */
interface DeviceStateSink {
    /** A setting (`ns:key`) was just written successfully; [value] null = deleted. */
    fun settingWritten(id: String, value: String?)

    /** Something not expressible as one setting changed (overlay, wm size, undo all…): re-read soon. */
    fun stateChanged()
}

/**
 * Remembers this phone between visits: capability probes (per device fingerprint + app build) and
 * the last-known value of every setting the UI shows. Screens render from it instantly and ask for a
 * cheap batched background refresh, which only touches the UI when a value actually changed.
 */
class DeviceStateRepository(
    private val store: DataStore<Preferences>,
    private val shell: Shell,
    private val identity: () -> DeviceIdentity,
    private val scope: CoroutineScope,
) : DeviceStateSink {

    enum class Mode { ENTER, RESUME, FORCE }

    private val key = stringPreferencesKey("cache_json")
    private val _cache = MutableStateFlow<DeviceCache?>(null)
    /** null until the stored copy has been read from disk (a few ms after process start). */
    val cache: StateFlow<DeviceCache?> = _cache

    private val _lists = MutableStateFlow<Map<String, List<Pair<String, String>>>>(emptyMap())
    /** Every setting per namespace from the last refresh, for the raw editor. Memory only. */
    val lists: StateFlow<Map<String, List<Pair<String, String>>>> = _lists

    private val _epoch = MutableStateFlow(0)
    /** Bumped by [rescan]: screens re-run their probes. */
    val epoch: StateFlow<Int> = _epoch

    private val loaded = CompletableDeferred<Unit>()
    private val refreshLock = Mutex()
    private val persistLock = Mutex()
    private val lastByTag = ConcurrentHashMap<String, Long>()
    private val writtenAt = ConcurrentHashMap<String, Long>()
    @Volatile private var lastRefresh = 0L

    init {
        scope.launch(Dispatchers.IO) {
            val stored = runCatching { store.data.first()[key] }.getOrNull()
            val c = DeviceCacheLogic.validate(DeviceCacheLogic.decode(stored), identity())
            _cache.update { it ?: c }
            loaded.complete(Unit)
        }
    }

    suspend fun awaitLoaded(): DeviceCache {
        loaded.await()
        return _cache.value!!
    }

    /** Computes and stores probes that aren't cached yet. [resolve] gets only the missing keys. */
    suspend fun ensureProbes(keys: Set<String>, resolve: suspend (Set<String>) -> Map<String, String>) {
        if (keys.isEmpty()) return
        val missing = keys - awaitLoaded().probes.keys
        if (missing.isEmpty()) return
        val got = resolve(missing)
        if (got.isEmpty()) return
        _cache.update { it!!.copy(probes = it.probes + got) }
        persist()
    }

    /**
     * Re-reads all watched values in one shell call. [Mode.ENTER] runs at most every 30 s per [tag];
     * [Mode.RESUME] skips only if any refresh finished in the last few seconds; [Mode.FORCE] always runs.
     * Returns the ids that changed.
     */
    suspend fun refresh(tag: String, watch: Set<String>, mode: Mode = Mode.ENTER): Set<String> {
        val have = awaitLoaded()
        val now = SystemClock.elapsedRealtime()
        val covered = have.hasValues(watch)
        if (covered && mode == Mode.ENTER && now - (lastByTag[tag] ?: -THROTTLE_MS) < THROTTLE_MS) return emptySet()
        if (covered && mode != Mode.FORCE && now - lastRefresh < MIN_GAP_MS) return emptySet()
        return refreshLock.withLock {
            val started = SystemClock.elapsedRealtime()
            // Another caller may have refreshed while this one waited for the lock.
            if (mode != Mode.FORCE && _cache.value!!.hasValues(watch) && started - lastRefresh < MIN_GAP_MS) return@withLock emptySet()
            val r = shell.run(ShellBatch.script(DeviceCacheLogic.SECTIONS))
            val sections = ShellBatch.split(r.output)
            if (sections.isEmpty()) error(r.error)
            val batch = DeviceCacheLogic.parseBatch(sections, watch + _cache.value!!.values.keys)
            _lists.value = batch.lists
            var changed = emptySet<String>()
            _cache.update { c ->
                val skip = writtenAt.filterValues { it > started }.keys
                val merged = DeviceCacheLogic.merge(c!!.values, batch.values, skip)
                if (merged == null) c else {
                    changed = DeviceCacheLogic.diff(c.values, merged)
                    c.copy(values = merged)
                }
            }
            if (changed.isNotEmpty()) persist()
            lastRefresh = SystemClock.elapsedRealtime()
            lastByTag[tag] = lastRefresh
            if (BuildConfig.DEBUG) Log.d(TAG, "refresh[$tag/$mode] ${lastRefresh - started} ms, changed=${changed.size}")
            changed
        }
    }

    override fun settingWritten(id: String, value: String?) {
        writtenAt[id] = SystemClock.elapsedRealtime()
        var did = false
        _cache.update { c -> c?.copy(values = c.values + (id to value))?.also { did = true } }
        if (did) persist()
    }

    override fun stateChanged() {
        scope.launch { runCatching { refresh("", emptySet(), Mode.FORCE) } }
    }

    /** "Rescan phone": forget every probe and re-read everything. */
    suspend fun rescan() {
        awaitLoaded()
        _cache.update { it!!.copy(identity = identity(), probes = emptyMap()) }
        lastByTag.clear()
        _epoch.update { it + 1 }
        persist()
        refresh("", emptySet(), Mode.FORCE)
    }

    private fun persist() {
        scope.launch(Dispatchers.IO) {
            persistLock.withLock {
                val snapshot = _cache.value ?: return@withLock
                runCatching { store.edit { it[key] = DeviceCacheLogic.encode(snapshot) } }
            }
        }
    }

    companion object {
        private const val TAG = "CustoCache"
        const val THROTTLE_MS = 30_000L
        const val MIN_GAP_MS = 3_000L

        /** Fingerprint + SDK + this app's build (version and install time, so any update rescans). */
        fun identityOf(ctx: Context): DeviceIdentity {
            val updated = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime }.getOrDefault(0L)
            return DeviceIdentity(Build.FINGERPRINT.orEmpty(), Build.VERSION.SDK_INT, "${BuildConfig.VERSION_CODE}/$updated")
        }
    }
}
