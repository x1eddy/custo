package app.custo.feature.themes

import android.graphics.Bitmap
import android.util.LruCache
import app.custo.ui.components.PreviewRender
import kotlinx.coroutines.withContext

/**
 * Small wallpaper renders for previews (Home, Looks, the style picker), cached by spec and size.
 * Without it every visit to Home/Themes and every scroll of the Looks row re-rendered each preview
 * in software, which on slow phones meant blank tiles and a busy CPU while scrolling.
 */
object WallThumbs {
    private data class Key(val spec: WallSpec, val w: Int, val h: Int)

    private val cache = object : LruCache<Key, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: Key, value: Bitmap) = value.byteCount
    }

    /** The cached render, if there is one; cheap enough to call during composition. */
    fun peek(spec: WallSpec, w: Int, h: Int): Bitmap? = cache.get(Key(spec, w, h))

    suspend fun get(spec: WallSpec, w: Int, h: Int): Bitmap {
        val key = Key(spec, w, h)
        cache.get(key)?.let { return it }
        // Cached inside the render block so a render that outlives its (scrolled-away) caller still counts.
        return withContext(PreviewRender) {
            cache.get(key) ?: WallpaperStudio.render(spec, w, h).also {
                it.prepareToDraw() // upload to the GPU ahead of the first frame that shows it
                cache.put(key, it)
            }
        }
    }
}
