package app.custo.feature.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import app.custo.data.Prefs
import app.custo.data.updateList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/** Saved icon styles, the shortcut history, and imported pictures (files/icons/...). */
class IconRepository(private val context: Context, private val prefs: Prefs) {

    val styles: Flow<List<SavedStyle>> = prefs.flow(IconKeys.styles, "[]").map { IconStyleCodec.decodeStyles(it) }
    val history: Flow<List<IconShortcut>> = prefs.flow(IconKeys.history, "[]").map { IconStyleCodec.decodeHistory(it) }

    suspend fun saveStyle(s: SavedStyle) =
        prefs.updateList(IconKeys.styles, SavedStyle.serializer(), "icon styles") { list -> list.filterNot { it.name == s.name } + s }

    suspend fun deleteStyle(name: String) =
        prefs.updateList(IconKeys.styles, SavedStyle.serializer(), "icon styles") { list -> list.filterNot { it.name == name } }

    suspend fun draft(): IconStyle = IconStyle.decode(prefs.get(IconKeys.draft, "")) ?: IconStyle()
    suspend fun saveDraft(s: IconStyle) = prefs.set(IconKeys.draft, s.encode())

    // --- shortcut history ---

    private val shortcutDir get() = File(context.filesDir, "icons/shortcuts").apply { mkdirs() }
    fun shortcutIcon(id: String) = File(shortcutDir, "$id.png")

    suspend fun record(entry: IconShortcut, icon: Bitmap) = withContext(Dispatchers.IO) {
        shortcutIcon(entry.id).outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        prefs.updateList(IconKeys.history, IconShortcut.serializer(), "shortcut history") { list -> list.filterNot { it.id == entry.id } + entry }
    }

    suspend fun forget(id: String) = withContext(Dispatchers.IO) {
        shortcutIcon(id).delete()
        prefs.updateList(IconKeys.history, IconShortcut.serializer(), "shortcut history") { list -> list.filterNot { it.id == id } }
    }

    suspend fun loadShortcutIcon(id: String): Bitmap? = withContext(Dispatchers.IO) {
        shortcutIcon(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }

    // --- imported pictures ---

    private val importDir get() = File(context.filesDir, "icons/imported").apply { mkdirs() }
    private val _imported = MutableStateFlow<List<File>>(emptyList())
    val imported: StateFlow<List<File>> = _imported

    suspend fun refreshImported() = withContext(Dispatchers.IO) {
        _imported.value = importDir.listFiles { f -> f.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /** Copies pictures in as 432 px PNGs. Returns the files that were added. */
    suspend fun importImages(uris: List<Uri>): List<File> = withContext(Dispatchers.IO) {
        val out = uris.mapNotNull { uri ->
            runCatching {
                val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val big = maxOf(info.size.width, info.size.height)
                    if (big > MAX_PX) d.setTargetSampleSize(big / MAX_PX)
                }
                save(src, uri.lastPathSegment ?: "image")
            }.getOrNull()
        }
        refreshImported()
        out
    }

    /** Unpacks every PNG / JPG / WEBP in a ZIP. Names are flattened, so nothing escapes the folder. */
    suspend fun importZip(uri: Uri): Int = withContext(Dispatchers.IO) {
        var count = 0
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (count < MAX_ZIP_ENTRIES) {
                    val e = zip.nextEntry ?: break
                    val name = e.name.substringAfterLast('/').substringAfterLast('\\')
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (e.isDirectory || name.startsWith(".") || ext !in IMAGE_EXT) continue
                    val bytes = readLimited(zip) ?: continue
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    val big = maxOf(opts.outWidth, opts.outHeight)
                    if (big <= 0) continue
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                        inSampleSize = (big / MAX_PX).coerceAtLeast(1)
                    }) ?: continue
                    save(bmp, name)
                    count++
                }
            }
        } ?: error("Couldn't open that file")
        refreshImported()
        count
    }

    private fun readLimited(zip: ZipInputStream): ByteArray? {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val n = zip.read(chunk)
            if (n < 0) break
            buf.write(chunk, 0, n)
            if (buf.size() > MAX_ENTRY_BYTES) return null
        }
        return buf.toByteArray()
    }

    private fun save(src: Bitmap, name: String): File {
        val icon = IconRenderer.fitBitmap(src)
        val base = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifEmpty { "icon" }
        var f = File(importDir, "$base.png")
        var i = 2
        while (f.exists()) f = File(importDir, "${base}_${i++}.png")
        f.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    suspend fun deleteImported(f: File) = withContext(Dispatchers.IO) {
        if (f.parentFile == importDir) f.delete()
        refreshImported()
    }

    suspend fun loadImported(f: File): Bitmap? = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.path) }

    companion object {
        private const val MAX_PX = 864
        private const val MAX_ZIP_ENTRIES = 2000
        private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
        private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp")
    }
}
