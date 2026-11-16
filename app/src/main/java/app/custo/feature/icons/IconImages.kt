package app.custo.feature.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pictures for icon backgrounds: photos the user picks (copied into files/icons/backgrounds so they
 * outlive the picker's permission) and wallpapers saved by the wallpaper browser (files/wallpapers).
 */
object IconImages {
    private const val MAX_PX = 1440

    private val cache = object : LruCache<String, Bitmap>(28 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun bgDir(context: Context) = File(context.filesDir, "icons/backgrounds").apply { mkdirs() }

    /** Saved wallpapers from the wallpaper browser, newest first. Empty if that folder doesn't exist yet. */
    fun savedWallpapers(context: Context): List<File> = listImages(File(context.filesDir, "wallpapers"))

    /** Photos picked for icons before, newest first. */
    fun photos(context: Context): List<File> = listImages(bgDir(context))

    private fun listImages(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("webp", "jpg", "jpeg", "png") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** Copies a picked photo in (max 1440 px, WebP). Returns its absolute path for [IconStyle.image]. */
    suspend fun importPhoto(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val big = maxOf(info.size.width, info.size.height)
            if (big > MAX_PX) d.setTargetSampleSize((big / MAX_PX).coerceAtLeast(1))
        }
        val f = File(bgDir(context), "bg_${System.currentTimeMillis().toString(36)}.webp")
        f.outputStream().use { src.compress(Bitmap.CompressFormat.WEBP_LOSSY, 88, it) }
        src.recycle()
        f.absolutePath
    }

    fun deletePhoto(context: Context, path: String) {
        val f = File(path)
        if (f.parentFile == bgDir(context)) f.delete()
        cache.snapshot().keys.filter { it.startsWith("$path|") }.forEach { cache.remove(it) }
    }

    /** Decodes [path] down to about [maxPx] on its long side. Cached; callers must not recycle. Blocking. */
    fun load(path: String, maxPx: Int = MAX_PX): Bitmap? {
        val key = "$path|$maxPx"
        cache.get(key)?.let { return it }
        val f = File(path)
        if (!f.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val big = maxOf(bounds.outWidth, bounds.outHeight)
        if (big <= 0) return null
        var sample = 1
        while (big / (sample * 2) >= maxPx) sample *= 2
        val b = runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) }.getOrNull() ?: return null
        cache.put(key, b)
        return b
    }

    suspend fun loadAsync(path: String, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) { load(path, maxPx) }
}
