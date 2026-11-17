package app.custo.feature.wallhaven

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.custo.data.LenientList
import app.custo.data.Prefs
import app.custo.data.updateList
import app.custo.feature.themes.WallTarget
import app.custo.feature.themes.WallpaperStudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Kept apart from the main settings so backups don't carry entries whose files stay behind. */
private val Context.wallhavenStore: DataStore<Preferences> by preferencesDataStore("wallhaven")

/** A wallpaper saved on the phone as `filesDir/wallpapers/<id>.webp`. */
@Serializable
data class SavedWall(
    val id: String,
    /** wallhaven page (attribution link). */
    val sourceUrl: String = "",
    val shortUrl: String = "",
    val colors: List<String> = emptyList(),
    val packId: String? = null,
    val query: String? = null,
    val bytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    /** Size of the original on wallhaven, e.g. "2094x3222". */
    val resolution: String = "",
    val category: String = "",
    val savedAt: Long = 0,
    val favorite: Boolean = false,
)

/**
 * Everything wallhaven: search, saving (compacted WebP) and the saved list, favourites, offline
 * packs and applying as wallpaper. One instance in [app.custo.AppContainer.wallhaven].
 *
 * For other features: [topFor] gives a few top results for a search term (cached for the session),
 * [savedFile] the local image of a saved wallpaper, [rememberWallImage] draws either.
 */
class WallhavenRepository(
    context: Context,
    private val studio: WallpaperStudio,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val prefs = Prefs(app.wallhavenStore)
    private val savedKey = stringPreferencesKey("saved_json")
    private val blockedKey = stringSetPreferencesKey("blocked_ids")
    val api = WallhavenApi(isOnline = ::isOnline)
    private val compactor = WallCompactor { studio.screenSize() }
    val dir: File get() = File(app.filesDir, "wallpapers").apply { mkdirs() }

    private val known = ConcurrentHashMap<String, WhWallpaper>()
    private val topCache = ConcurrentHashMap<String, Pair<Long, List<WhWallpaper>>>()
    private val saveLocks = ConcurrentHashMap<String, Mutex>()
    /** Tag check results per wallpaper id (true = fine), so a picture is looked up once per run. */
    private val tagChecks = ConcurrentHashMap<String, Boolean>()
    private val goneListeners = java.util.concurrent.CopyOnWriteArrayList<suspend (id: String, notThis: Boolean) -> Unit>()

    // ---------- "Not this" ----------

    /** Wallpapers the user said "Not this" to: every search, pack, set strip and automatic pick skips them. */
    val blocked: Flow<Set<String>> = prefs.flow(blockedKey, emptySet())

    @Volatile private var blockedCache: Set<String>? = null

    suspend fun blockedNow(): Set<String> = blockedCache ?: prefs.get(blockedKey, emptySet()).also { blockedCache = it }

    /** Called when a saved wallpaper goes away (deleted, or "Not this"), e.g. so a set using it picks another. */
    fun addGoneListener(l: suspend (id: String, notThis: Boolean) -> Unit) { goneListeners += l }

    /**
     * "Not this": never show [id] again, delete its file, drop it from the open grids and, when it
     * belonged to a pack, fetch the next good picture so the pack keeps its size.
     */
    suspend fun notThis(id: String) {
        val record = savedRaw.first().firstOrNull { it.id == id }
        prefs.edit { it[blockedKey] = (it[blockedKey] ?: emptySet()) + id }
        blockedCache = null
        topCache.replaceAll { _, v -> v.first to v.second.filterNot { it.id == id } }
        browsePager.remove(id)
        queryPager.remove(id)
        if (record != null) {
            withContext(Dispatchers.IO) { savedFileOf(id).delete() }
            WallImages.forget(savedFileOf(id).path)
            prefs.updateList(savedKey, SavedWall.serializer(), "wallpapers") { l -> l.filterNot { it.id == id } }
        }
        goneListeners.forEach { runCatching { it(id, true) } }
        val packId = record?.packId ?: return
        val have = savedRaw.first().count { it.packId == packId && savedFileOf(it.id).isFile }
        val target = maxOf(have + 1, packProgress.value[packId]?.takeIf { it.active }?.total ?: 0)
        // One picture, asked for by the user: any network is fine.
        downloadPack(packId, target, wifiOnly = false)
    }

    /**
     * Looks at the tags of a curated pick (one rate-limited request, cached): false when it is a
     * meme/collage/screenshot/pin-up or isn't about [tagId] (see [WhQuality.isAbout]). Network errors other than being offline count as "fine".
     */
    suspend fun passesTagCheck(item: WhWallpaper, tagId: Long?): Boolean {
        tagChecks[item.id]?.let { return it }
        val info = try {
            api.info(item.id)
        } catch (e: WallhavenException) {
            if (e.error == WhError.OFFLINE || e.error == WhError.RATE_LIMITED) throw e
            return true
        }
        val ok = info != null && !WhQuality.isJunk(info.tags) && WhQuality.isAbout(info.tags, tagId)
        tagChecks[item.id] = ok
        return ok
    }

    // ---------- Network ----------

    fun isOnline(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Online on Wi-Fi/ethernet (not mobile data): used for automatic, unrequested downloads. */
    fun isOnlineUnmetered(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Search filters sized for this phone (minimum resolution = the stored wallpaper size). */
    fun defaultFilters(portrait: Boolean = true, sorting: WhSorting = WhSorting.TOPLIST): WhFilters {
        val (w, h) = studio.screenSize()
        return WhFilters(sorting = sorting, portrait = portrait, atLeast = CompactMath.minSizeFor(w, h))
    }

    /** One page of SFW results. Throws [WallhavenException] (offline, rate limited, …). */
    suspend fun search(query: String?, filters: WhFilters = defaultFilters(), page: Int = 1, seed: String? = null): WhPage {
        // Curated terms ("Minecraft", "One Piece", "space"…) search their exact wallhaven tag.
        val p = api.search(WhTags.apiQuery(query), filters, page, seed)
        p.items.forEach { known[it.id] = it }
        val blocked = blockedNow()
        return if (blocked.isEmpty()) p else p.copy(items = WhQuality.withoutBlocked(p.items, blocked))
    }

    /**
     * Up to [n] top wallpapers for [query] (e.g. "One Piece"), cached for 30 minutes. Falls back to
     * any-shape results when there are too few portrait ones. Throws when offline and not cached.
     */
    suspend fun topFor(query: String, n: Int = 12, portrait: Boolean = true): List<WhWallpaper> {
        val key = "$query|$portrait"
        val blocked = blockedNow()
        topCache[key]?.let { (at, list) ->
            if (System.currentTimeMillis() - at < 30 * 60_000) return WhQuality.withoutBlocked(list, blocked).take(n)
        }
        val first = search(query, defaultFilters(portrait, WhSorting.FAVORITES)).items
        val enough = WhQuality.curate(first, n).size >= n
        val raw = if (enough || !portrait) first
        else (first + search(query, defaultFilters(false, WhSorting.FAVORITES)).items).distinctBy { it.id }
        // Named searches get the quality rules (popular, big enough, phone-shaped); the hero's "" doesn't need them.
        val list = if (query.isBlank()) raw else WhQuality.curate(raw, n)
        topCache[key] = System.currentTimeMillis() to list
        return list.take(n)
    }

    /** A wallpaper seen in results this session, or fetched by id. */
    suspend fun item(id: String): WhWallpaper? = known[id] ?: runCatching { api.info(id) }.getOrNull()?.also { known[id] = it }

    fun cached(id: String): WhWallpaper? = known[id]

    // ---------- Saved ----------

    private val savedRaw: Flow<List<SavedWall>> =
        prefs.flow(savedKey, "[]").map { LenientList.decode(SavedWall.serializer(), it) }

    /** Saved wallpapers whose file is on the phone, newest first. Works offline. */
    val saved: Flow<List<SavedWall>> = savedRaw
        .map { list -> list.filter { savedFileOf(it.id).isFile }.sortedByDescending { it.savedAt } }
        .flowOn(Dispatchers.IO)

    val favorites: Flow<List<SavedWall>> = saved.map { l -> l.filter { it.favorite } }

    suspend fun savedNow(): List<SavedWall> = saved.first()

    private fun savedFileOf(id: String) = File(dir, "$id.webp")

    /** The compact local copy of a saved wallpaper, or null if it isn't saved. */
    fun savedFile(id: String): File? = savedFileOf(id).takeIf { it.isFile }

    /**
     * Downloads [item], compacts it to a screen-sized WebP and records it. Saving one that is already
     * saved only updates its pack/favourite. Returns the record.
     */
    suspend fun save(item: WhWallpaper, packId: String? = null, query: String? = null, favorite: Boolean? = null): SavedWall {
        require(WallhavenApi.sfwOnly(listOf(item)).isNotEmpty()) { "Not available" }
        known[item.id] = item
        val lock = saveLocks.getOrPut(item.id) { Mutex() }
        return lock.withLock {
            val existing = savedRaw.first().firstOrNull { it.id == item.id }
            val file = savedFileOf(item.id)
            val record = if (existing != null && file.isFile) {
                existing.copy(
                    packId = existing.packId ?: packId,
                    favorite = favorite ?: existing.favorite,
                )
            } else {
                val out = fetchCompacted(item, file)
                SavedWall(
                    id = item.id, sourceUrl = item.pageUrl, shortUrl = item.shortUrl, colors = item.colors,
                    packId = packId, query = query, bytes = out.bytes, width = out.width, height = out.height,
                    resolution = item.resolution, category = item.category,
                    savedAt = System.currentTimeMillis(), favorite = favorite ?: false,
                )
            }
            prefs.updateList(savedKey, SavedWall.serializer(), "wallpapers") { l -> l.filterNot { it.id == item.id } + record }
            record
        }
    }

    /** Downloads the original (or reuses the image cache) and writes the compact WebP to [dest]. */
    private suspend fun fetchCompacted(item: WhWallpaper, dest: File): Compacted {
        val cached = WallImages.cachedFile(app, item.path)
        val tmpDir = File(app.cacheDir, "wh_dl").apply { mkdirs() }
        val src = cached ?: File(tmpDir, item.id).also { api.download(item.path, it) }
        try {
            return compactor.compact(src, dest)
        } finally {
            if (cached == null) src.delete()
        }
    }

    suspend fun setFavorite(id: String, favorite: Boolean) =
        prefs.updateList(savedKey, SavedWall.serializer(), "wallpapers") { l -> l.map { if (it.id == id) it.copy(favorite = favorite) else it } }

    /** Favourite toggle from a search result: favouriting saves it offline first. */
    suspend fun toggleFavorite(item: WhWallpaper): Boolean {
        val cur = savedRaw.first().firstOrNull { it.id == item.id }
        return if (cur != null && savedFile(item.id) != null) {
            setFavorite(item.id, !cur.favorite); !cur.favorite
        } else {
            save(item, favorite = true); true
        }
    }

    suspend fun delete(id: String, notify: Boolean = true) {
        withContext(Dispatchers.IO) { savedFileOf(id).delete() }
        WallImages.forget(savedFileOf(id).path)
        prefs.updateList(savedKey, SavedWall.serializer(), "wallpapers") { l -> l.filterNot { it.id == id } }
        if (notify) goneListeners.forEach { runCatching { it(id, false) } }
    }

    /** [deletePack] in the app scope, for callers whose own scope ends right away (a closing sheet). */
    fun deletePackLater(packId: String) { scope.launch { deletePack(packId) } }

    /** Deletes a pack's downloads, except ones the user favourited. */
    suspend fun deletePack(packId: String) {
        cancelPack(packId)
        val gone = savedRaw.first().filter { it.packId == packId && !it.favorite }.map { it.id }.toSet()
        withContext(Dispatchers.IO) { gone.forEach { savedFileOf(it).delete(); WallImages.forget(savedFileOf(it).path) } }
        prefs.updateList(savedKey, SavedWall.serializer(), "wallpapers") { l -> l.filterNot { it.id in gone } }
        _packProgress.update { it - packId }
        gone.forEach { id -> goneListeners.forEach { runCatching { it(id, false) } } }
    }

    /** Bytes of saved wallpapers on the phone. */
    suspend fun storageUsed(): Long = withContext(Dispatchers.IO) { dir.listFiles()?.filter { it.name.endsWith(".webp") }?.sumOf { it.length() } ?: 0L }

    /** Thumbnail/image cache size (can be cleared any time). */
    fun cacheUsed(): Long = WallImages.diskUsage(app)

    fun clearCache() = WallImages.clearDisk(app)

    // ---------- Apply ----------

    /** Sets a saved wallpaper (or compacts a temporary copy of an unsaved one) as Home/Lock/Both. */
    suspend fun apply(item: WhWallpaper, target: WallTarget) {
        val file = savedFile(item.id) ?: File(app.cacheDir, "wh_apply/${item.id}.webp").also {
            if (!it.isFile) fetchCompacted(item, it)
        }
        studio.applyImage(file, target)
    }

    suspend fun applySaved(id: String, target: WallTarget) {
        val f = savedFile(id) ?: throw WallhavenException(WhError.NOT_FOUND)
        studio.applyImage(f, target)
    }

    // ---------- Offline packs ----------

    val packs: List<WallPack> get() = WallPacks.all

    private val _packProgress = MutableStateFlow<Map<String, PackProgress>>(emptyMap())
    /** Live download state per pack id (queued/running/done…). */
    val packProgress: StateFlow<Map<String, PackProgress>> = _packProgress

    internal fun reportProgress(p: PackProgress) = _packProgress.update { it + (p.packId to p) }

    /** Saved count per pack id. */
    val packCounts: Flow<Map<String, Int>> = saved.map { l -> l.mapNotNull { it.packId }.groupingBy { it }.eachCount() }

    /** Bytes on the phone per pack id. */
    val packBytes: Flow<Map<String, Long>> = saved.map { l ->
        l.filter { it.packId != null }.groupBy { it.packId!! }.mapValues { (_, v) -> v.sumOf { it.bytes } }
    }

    /** (stored bytes, download bytes) for [count] wallpapers, shown before starting. */
    fun estimate(count: Int): Pair<Long, Long> {
        val (w, h) = studio.screenSize()
        return PackPlanner.estimate(count, CompactMath.estimateStoredBytes(w, h))
    }

    /**
     * Queues a background download until the pack holds [count] wallpapers. Resumable: already saved
     * ones are skipped, so starting again continues where it stopped.
     */
    fun downloadPack(pack: WallPack, count: Int, wifiOnly: Boolean = true) {
        val req = OneTimeWorkRequestBuilder<PackDownloadWorker>()
            .setInputData(workDataOf(PackDownloadWorker.KEY_PACK to pack.id, PackDownloadWorker.KEY_COUNT to count))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .addTag(PackDownloadWorker.TAG)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(workName(pack.id), ExistingWorkPolicy.REPLACE, req)
        reportProgress(PackProgress(pack.id, PackProgress.State.QUEUED, total = count))
    }

    fun downloadPack(packId: String, count: Int, wifiOnly: Boolean = true) {
        WallPacks.byId(packId)?.let { downloadPack(it, count, wifiOnly) }
    }

    fun cancelPack(packId: String) {
        WorkManager.getInstance(app).cancelUniqueWork(workName(packId))
        _packProgress.update { m -> m[packId]?.takeIf { it.active }?.let { m + (packId to it.copy(state = PackProgress.State.CANCELLED)) } ?: m }
    }

    /** After a restart: marks packs whose work is still queued so their cards show it. */
    fun refreshPackStates() {
        scope.launch(Dispatchers.IO) {
            val wm = WorkManager.getInstance(app)
            WallPacks.all.forEach { p ->
                val infos = runCatching { wm.getWorkInfosForUniqueWork(workName(p.id)).get() }.getOrNull() ?: return@forEach
                val info = infos.firstOrNull { !it.state.isFinished }
                if (info == null) {
                    // Cancelled from the notification, or finished while the app was closed.
                    _packProgress.update { m -> m[p.id]?.takeIf { it.active }?.let { m - p.id } ?: m }
                    return@forEach
                }
                if (_packProgress.value[p.id]?.active == true) return@forEach
                val total = info.progress.getInt(PackDownloadWorker.KEY_TOTAL, 0)
                val done = info.progress.getInt(PackDownloadWorker.KEY_DONE, 0)
                val st = if (info.state == WorkInfo.State.RUNNING) PackProgress.State.RUNNING else PackProgress.State.QUEUED
                reportProgress(PackProgress(p.id, st, done, total))
            }
        }
    }

    // ---------- Paging ----------

    /** The Wallpapers browser's pager; shared so going back from a detail keeps results and position. */
    val browsePager: WallhavenPager by lazy { WallhavenPager(this, scope) }

    /** Pager for searches opened from elsewhere (a set, a pack), so the Wallpapers tab keeps its own. */
    val queryPager: WallhavenPager by lazy { WallhavenPager(this, scope) }

    /** A fresh pager for another screen (e.g. a set's wallpaper strip). */
    fun pager(scope: CoroutineScope): WallhavenPager = WallhavenPager(this, scope)

    private fun workName(packId: String) = "wh_pack_$packId"
}
