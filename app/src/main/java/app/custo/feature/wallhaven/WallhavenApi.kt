package app.custo.feature.wallhaven

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.currentCoroutineContext

/**
 * wallhaven.cc API v1 over plain [HttpURLConnection]. No API key: Custo only reads public SFW
 * wallpapers. Every API call goes through one [RateLimiter] (wallhaven allows 45 per minute; we stay
 * at 40) and 429/5xx answers are retried with backoff.
 */
class WallhavenApi(
    private val isOnline: () -> Boolean = { true },
    val limiter: RateLimiter = RateLimiter(),
) {

    /** One page of search results, already filtered to SFW general/anime. */
    suspend fun search(query: String?, filters: WhFilters, page: Int = 1, seed: String? = null): WhPage {
        val body = getApi(buildSearchUrl(query, filters, page, seed))
        val res = parseSearch(body)
        return WhPage(sfwOnly(res.data), res.meta.currentPage, res.meta.lastPage, res.meta.seed)
    }

    /** Details for one wallpaper; null if it isn't SFW general/anime. */
    suspend fun info(id: String): WhWallpaper? {
        require(ID.matches(id)) { "Bad wallpaper id" }
        val body = getApi("$BASE/w/$id")
        val w = runCatching { json.decodeFromString(WhInfoResponse.serializer(), body).data }
            .getOrElse { throw WallhavenException(WhError.BAD_RESPONSE, it) }
        return sfwOnly(listOf(w)).firstOrNull()
    }

    /**
     * Streams [url] (an image on wallhaven's CDN, not the API) into [dest] via a temp file.
     * Returns the byte count. Gives up above [maxBytes].
     */
    suspend fun download(url: String, dest: File, maxBytes: Long = MAX_IMAGE_BYTES): Long = withContext(Dispatchers.IO) {
        requireWallhavenHost(url)
        val tmp = File(dest.parentFile, dest.name + ".part")
        var lastError: WallhavenException? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            val conn = open(url, accept = "image/*")
            try {
                val code = conn.responseCode
                when {
                    code == 200 -> {
                        if (conn.contentLengthLong > maxBytes) throw WallhavenException(WhError.SERVER)
                        dest.parentFile?.mkdirs()
                        val n = conn.inputStream.use { copyLimited(it, tmp, maxBytes) }
                        if (!tmp.renameTo(dest)) { tmp.delete(); throw IOException("rename failed") }
                        return@withContext n
                    }
                    code == 404 || code == 403 -> throw WallhavenException(WhError.NOT_FOUND)
                    retryable(code) -> lastError = WallhavenException(if (code == 429) WhError.RATE_LIMITED else WhError.SERVER)
                    else -> throw WallhavenException(WhError.SERVER)
                }
                delay(backoffMs(attempt, conn.getHeaderField("Retry-After")))
            } catch (e: IOException) {
                tmp.delete()
                lastError = WallhavenException(if (isOnline()) WhError.SERVER else WhError.OFFLINE, e)
                if (!isOnline()) break
                delay(backoffMs(attempt, null))
            } finally {
                conn.disconnect()
            }
        }
        throw lastError ?: WallhavenException(WhError.SERVER)
    }

    private suspend fun copyLimited(input: InputStream, out: File, maxBytes: Long): Long {
        var total = 0L
        out.outputStream().use { os ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val r = input.read(buf)
                if (r < 0) break
                total += r
                if (total > maxBytes) { out.delete(); throw WallhavenException(WhError.SERVER) }
                os.write(buf, 0, r)
            }
        }
        return total
    }

    /** GET on the API with throttling and retries; returns the body. */
    private suspend fun getApi(url: String): String = withContext(Dispatchers.IO) {
        if (!isOnline()) throw WallhavenException(WhError.OFFLINE)
        var lastError: WallhavenException? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            limiter.acquire()
            val conn = open(url, accept = "application/json")
            try {
                val code = conn.responseCode
                when {
                    code == 200 -> return@withContext conn.inputStream.bufferedReader().use { it.readText() }
                    code == 404 -> throw WallhavenException(WhError.NOT_FOUND)
                    retryable(code) -> {
                        val retryAfter = conn.getHeaderField("Retry-After")
                        lastError = WallhavenException(if (code == 429) WhError.RATE_LIMITED else WhError.SERVER)
                        if (code == 429) limiter.pauseFor(backoffMs(attempt, retryAfter))
                        delay(backoffMs(attempt, retryAfter))
                    }
                    else -> throw WallhavenException(WhError.SERVER)
                }
            } catch (e: IOException) {
                lastError = WallhavenException(if (isOnline()) WhError.SERVER else WhError.OFFLINE, e)
                if (!isOnline()) break
                delay(backoffMs(attempt, null))
            } finally {
                conn.disconnect()
            }
        }
        throw lastError ?: WallhavenException(WhError.SERVER)
    }

    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
        }

    companion object {
        const val BASE = "https://wallhaven.cc/api/v1"
        const val USER_AGENT = "Custo/1.1 (Android wallpaper app)"
        const val MAX_ATTEMPTS = 4
        const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
        private val ID = Regex("^[a-z0-9]{1,16}$")
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

        /**
         * The search URL. purity is always 100 (SFW only) and categories never include "people";
         * neither can be changed by callers.
         */
        fun buildSearchUrl(query: String?, filters: WhFilters, page: Int, seed: String? = null): String {
            val p = linkedMapOf<String, String>()
            query?.trim()?.takeIf { it.isNotEmpty() }?.let { p["q"] = it.take(100) }
            p["categories"] = filters.categories
            p["purity"] = "100"
            val sorting = if (filters.sorting == WhSorting.RELEVANCE && p["q"] == null) WhSorting.TOPLIST else filters.sorting
            p["sorting"] = sorting.api
            p["order"] = "desc"
            if (sorting == WhSorting.TOPLIST) p["topRange"] = filters.topRange.api
            filters.atLeast?.takeIf { SIZE.matches(it) }?.let { p["atleast"] = it }
            if (filters.portrait) p["ratios"] = "portrait"
            if (sorting == WhSorting.RANDOM && seed != null && SEED.matches(seed)) p["seed"] = seed
            p["page"] = page.coerceAtLeast(1).toString()
            return "$BASE/search?" + p.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
        }

        private val SIZE = Regex("^\\d{1,5}x\\d{1,5}$")
        private val SEED = Regex("^[A-Za-z0-9]{1,12}$")

        fun parseSearch(body: String): WhSearchResponse =
            runCatching { json.decodeFromString(WhSearchResponse.serializer(), body) }
                .getOrElse { throw WallhavenException(WhError.BAD_RESPONSE, it) }

        /** Keeps only explicit "sfw" items in the general/anime categories, with a usable image. */
        fun sfwOnly(items: List<WhWallpaper>): List<WhWallpaper> = items.filter {
            it.purity == "sfw" && it.category != "people" && it.path.isNotBlank() && ID.matches(it.id)
        }

        fun retryable(code: Int) = code == 429 || code in 500..599

        /** Wait before retry [attempt] (0-based): Retry-After seconds if given, else 1 s, 2 s, 4 s… up to 30 s. */
        fun backoffMs(attempt: Int, retryAfter: String?): Long {
            retryAfter?.trim()?.toLongOrNull()?.let { return (it * 1000).coerceIn(1000, 60_000) }
            return (1000L shl attempt.coerceIn(0, 5)).coerceAtMost(30_000)
        }

        private fun requireWallhavenHost(url: String) {
            val host = runCatching { URL(url).host }.getOrNull().orEmpty()
            if (!url.startsWith("https://") || !(host == "wallhaven.cc" || host.endsWith(".wallhaven.cc"))) {
                throw WallhavenException(WhError.BAD_RESPONSE)
            }
        }
    }
}

/**
 * Sliding-window throttle: at most [max] starts per [windowMs]. [pauseFor] holds everyone back
 * after a 429. [now] is injectable for tests.
 */
class RateLimiter(
    private val max: Int = 40,
    private val windowMs: Long = 60_000,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val stamps = ArrayDeque<Long>()
    private var pausedUntil = 0L
    private val mutex = Mutex()

    /** How long a request starting at [t] has to wait (0 = go now). */
    @Synchronized
    fun waitMs(t: Long): Long {
        while (stamps.isNotEmpty() && t - stamps.first() >= windowMs) stamps.removeFirst()
        val forWindow = if (stamps.size < max) 0L else stamps.first() + windowMs - t
        return maxOf(forWindow, pausedUntil - t, 0L)
    }

    @Synchronized
    fun record(t: Long) { stamps.addLast(t) }

    @Synchronized
    fun pauseFor(ms: Long) { pausedUntil = maxOf(pausedUntil, now() + ms) }

    /** Waits until a request may start and counts it. */
    suspend fun acquire() = mutex.withLock {
        while (true) {
            val t = now()
            val w = waitMs(t)
            if (w <= 0) { record(t); return@withLock }
            delay(w)
        }
    }
}
