package app.custo.feature.wallhaven

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.custo.ui.components.PreviewRender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny image loader for wallhaven pictures and saved wallpapers: memory [LruCache] + disk cache in
 * `cacheDir/wh_thumbs` (80 MB, least recently used goes first), subsampled decodes on the
 * background-priority [PreviewRender] pool, at most 4 downloads at a time. A load is cancelled when
 * its composable leaves the screen (produceState's coroutine is cancelled with it).
 */
object WallImages {
    const val DISK_CAP = 80L * 1024 * 1024
    private const val DIR = "wh_thumbs"

    private val memory = object : LruCache<String, Bitmap>(memoryBudget()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val downloads = Semaphore(4)
    private val inflight = ConcurrentHashMap<String, Mutex>()
    private val api = WallhavenApi()
    @Volatile private var diskBytes = -1L

    private fun memoryBudget(): Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(64L * 1024 * 1024).toInt()

    private fun key(src: String, targetPx: Int) = "$src@${bucket(targetPx)}"

    /** Round sizes up so nearby requests share one cached bitmap. */
    private fun bucket(px: Int): Int {
        var b = 128
        while (b < px && b < 4096) b *= 2
        return b
    }

    fun peek(src: String, targetPx: Int): Bitmap? = memory.get(key(src, targetPx))

    /** The disk-cached copy of [url] if it was loaded before (lets the compactor skip a download). */
    fun cachedFile(context: Context, url: String): File? = diskFile(context, url).takeIf { it.isFile && it.length() > 0 }

    /**
     * Loads [src] (an https URL on wallhaven or an absolute path to a local file) so its longer side
     * is about [targetPx]. Null on failure.
     */
    suspend fun load(context: Context, src: String, targetPx: Int): Bitmap? {
        val k = key(src, targetPx)
        memory.get(k)?.let { return it }
        val file = if (src.startsWith("/")) File(src) else {
            val cached = diskFile(context, src)
            if (!cached.isFile) {
                val lock = inflight.getOrPut(src) { Mutex() }
                val ok = try {
                    lock.withLock {
                        cached.isFile || downloads.withPermit { runCatching { api.download(src, cached) }.isSuccess }
                    }
                } finally {
                    inflight.remove(src, lock)
                }
                if (!ok) return null
                trim(context, cached.length())
            } else {
                cached.setLastModified(System.currentTimeMillis())
            }
            cached
        }
        val bmp = withContext(PreviewRender) { decode(file, bucket(targetPx)) } ?: return null
        memory.put(k, bmp)
        return bmp
    }

    private fun decode(file: File, targetPx: Int): Bitmap? {
        if (!file.isFile) return null
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, b)
        if (b.outWidth <= 0) { file.delete(); return null }
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= targetPx) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            // Hardware bitmaps keep thumbnails off the Java heap; Compose draws them directly.
            inPreferredConfig = Bitmap.Config.HARDWARE
        }
        return BitmapFactory.decodeFile(file.path, opts)
    }

    private fun dir(context: Context) = File(context.cacheDir, DIR)

    private fun diskFile(context: Context, url: String) = File(dir(context), sha1(url))

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Keeps the disk cache under [DISK_CAP], oldest-used first. */
    private suspend fun trim(context: Context, added: Long) = withContext(Dispatchers.IO) {
        synchronized(this@WallImages) {
            val d = dir(context)
            if (diskBytes < 0) diskBytes = d.listFiles()?.sumOf { it.length() } ?: 0L else diskBytes += added
            if (diskBytes <= DISK_CAP) return@synchronized
            val files = d.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return@synchronized
            var total = files.sumOf { it.length() }
            for (f in files) {
                if (total <= DISK_CAP * 3 / 4) break
                val len = f.length()
                if (f.delete()) total -= len
            }
            diskBytes = total
        }
    }

    fun diskUsage(context: Context): Long = dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    fun clearDisk(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
        diskBytes = 0
        memory.evictAll()
    }

    /** Drops cached bitmaps of a deleted local file. */
    fun forget(src: String) {
        memory.snapshot().keys.filter { it.startsWith("$src@") }.forEach { memory.remove(it) }
    }
}

/**
 * The image at [src] (https URL or local file path), about [targetPx] on its longer side; null
 * while loading or on failure. Cancelled when the caller leaves composition.
 */
@Composable
fun rememberWallImage(src: String?, targetPx: Int): State<ImageBitmap?> {
    val context = LocalContext.current.applicationContext
    return produceState(src?.let { WallImages.peek(it, targetPx)?.asImageBitmap() }, src, targetPx) {
        // produceState keeps the old value when the keys change, so always reset it first.
        val cached = src?.let { WallImages.peek(it, targetPx) }
        value = cached?.asImageBitmap()
        if (src == null || cached != null) return@produceState
        value = WallImages.load(context, src, targetPx)?.asImageBitmap()
    }
}
