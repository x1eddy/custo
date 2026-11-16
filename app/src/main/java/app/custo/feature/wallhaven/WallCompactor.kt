package app.custo.feature.wallhaven

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where to cut the source and how big the stored wallpaper ends up. Pure, so it can be unit tested. */
data class CompactPlan(
    val cropLeft: Int, val cropTop: Int, val cropWidth: Int, val cropHeight: Int,
    val outWidth: Int, val outHeight: Int,
    /** Power of two for the region decode, so it never holds more than ~2× the output pixels. */
    val sampleSize: Int,
)

object CompactMath {
    const val MAX_HEIGHT = 1440

    /** Portrait output size for a screen: the screen's shape, at most [maxHeight] tall. */
    fun outputSize(screenW: Int, screenH: Int, maxHeight: Int = MAX_HEIGHT): Pair<Int, Int> {
        val w = min(screenW, screenH).coerceAtLeast(1)
        val h = max(screenW, screenH).coerceAtLeast(1)
        val outH = min(h, maxHeight)
        val outW = (w.toLong() * outH / h).toInt().coerceAtLeast(1)
        return outW to outH
    }

    /** The "atleast" search filter: images smaller than the stored size would only get blurry. */
    fun minSizeFor(screenW: Int, screenH: Int): String {
        val (w, h) = outputSize(screenW, screenH)
        return "${w}x$h"
    }

    /**
     * Center-crop [srcW]×[srcH] to the screen's aspect and scale it to the output size, never
     * upscaling (a small source keeps its cropped size).
     */
    fun plan(srcW: Int, srcH: Int, screenW: Int, screenH: Int, maxHeight: Int = MAX_HEIGHT): CompactPlan {
        require(srcW > 0 && srcH > 0) { "empty image" }
        val (tw, th) = outputSize(screenW, screenH, maxHeight)
        val targetAspect = tw.toDouble() / th
        val cropW: Int
        val cropH: Int
        if (srcW.toDouble() / srcH > targetAspect) {
            cropH = srcH
            cropW = (srcH * targetAspect).roundToInt().coerceIn(1, srcW)
        } else {
            cropW = srcW
            cropH = (srcW / targetAspect).roundToInt().coerceIn(1, srcH)
        }
        val left = (srcW - cropW) / 2
        val top = (srcH - cropH) / 2
        val scale = min(1.0, th.toDouble() / cropH)
        val outH = (cropH * scale).roundToInt().coerceAtLeast(1)
        val outW = (cropW * scale).roundToInt().coerceAtLeast(1)
        var sample = 1
        while (cropW / (sample * 2) >= outW && cropH / (sample * 2) >= outH) sample *= 2
        return CompactPlan(left, top, cropW, cropH, outW, outH, sample)
    }

    /** Rough size of a stored WebP for planning (~0.17 bytes per pixel at quality 82). */
    fun estimateStoredBytes(screenW: Int, screenH: Int): Long {
        val (w, h) = outputSize(screenW, screenH)
        return (w.toLong() * h * 0.17).toLong().coerceAtLeast(60_000)
    }
}

data class Compacted(val file: File, val bytes: Long, val width: Int, val height: Int)

/**
 * Turns a downloaded original (often 5–10 MB PNG/JPEG) into a screen-sized WebP of a few hundred KB.
 * Decodes only the crop region, subsampled, on a background thread.
 */
class WallCompactor(private val screenSize: () -> Pair<Int, Int>) {

    suspend fun compact(source: File, dest: File, quality: Int = QUALITY): Compacted = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw WallhavenException(WhError.BAD_RESPONSE)
        val (sw, sh) = screenSize()
        val plan = CompactMath.plan(bounds.outWidth, bounds.outHeight, sw, sh)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = plan.sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rect = Rect(plan.cropLeft, plan.cropTop, plan.cropLeft + plan.cropWidth, plan.cropTop + plan.cropHeight)
        val region = runCatching {
            BitmapRegionDecoder.newInstance(source.path).let { d -> try { d.decodeRegion(rect, opts) } finally { d.recycle() } }
        }.getOrNull() ?: decodeCropFallback(source, opts, rect, plan.sampleSize)
            ?: throw WallhavenException(WhError.BAD_RESPONSE)
        val out = if (region.width == plan.outWidth && region.height == plan.outHeight) region
        else region.scale(plan.outWidth, plan.outHeight).also { if (it !== region) region.recycle() }
        try {
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            tmp.outputStream().buffered().use { os ->
                if (!out.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, os)) throw WallhavenException(WhError.BAD_RESPONSE)
            }
            if (!tmp.renameTo(dest)) { tmp.delete(); throw WallhavenException(WhError.BAD_RESPONSE) }
            Compacted(dest, dest.length(), out.width, out.height)
        } finally {
            out.recycle()
        }
    }

    /** For formats the region decoder can't open: whole-image subsampled decode, then crop. */
    private fun decodeCropFallback(source: File, opts: BitmapFactory.Options, rect: Rect, sample: Int): Bitmap? {
        val full = BitmapFactory.decodeFile(source.path, opts) ?: return null
        val l = (rect.left / sample).coerceIn(0, full.width - 1)
        val t = (rect.top / sample).coerceIn(0, full.height - 1)
        val w = (rect.width() / sample).coerceIn(1, full.width - l)
        val h = (rect.height() / sample).coerceIn(1, full.height - t)
        return Bitmap.createBitmap(full, l, t, w, h).also { if (it !== full) full.recycle() }
    }

    companion object {
        const val QUALITY = 82
    }
}
