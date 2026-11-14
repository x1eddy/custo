package app.custo.feature.themes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import app.custo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything that shapes a collage. Sizes are fractions of the image width so previews match exports. */
data class CollageSpec(
    val template: CollageTemplate,
    val spacing: Float = 0.02f,
    val margin: Float = 0.04f,
    val radius: Float = 0.03f,
    val bg: CollageBg = CollageBg.COLOR,
    val color: Int = 0xFF1C1B1F.toInt(),
    val text: String = "",
    val textSize: Float = 0.07f,
    val textColor: Int = 0xFFFFFFFF.toInt(),
)

/** Loads photos at just the size they need and draws collages on a software canvas. */
object CollageRenderer {

    /** Decodes [uri] so it just covers [cw] x [ch]. Software bitmap, so it can be drawn anywhere. */
    suspend fun load(ctx: Context, uri: Uri, cw: Int, ch: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = info.size
                val k = max(cw / s.width.toFloat(), ch / s.height.toFloat())
                if (k < 1f) d.setTargetSize((s.width * k).roundToInt().coerceAtLeast(1), (s.height * k).roundToInt().coerceAtLeast(1))
            }
        }.getOrNull()
    }

    /** Loads every photo sized for its cell on a [w] x [h] collage. */
    suspend fun loadFor(ctx: Context, spec: CollageSpec, uris: List<Uri>, w: Int, h: Int): List<Bitmap?> {
        val boxes = layout(spec, w.toFloat(), h.toFloat()).first
        return uris.mapIndexed { i, u ->
            val b = boxes.getOrNull(i)
            val side = if (b == null) 256 else max(b.w, b.h).roundToInt().coerceAtLeast(64)
            load(ctx, u, side, side)
        }
    }

    fun typeface(ctx: Context): Typeface =
        runCatching { Typeface.create(ResourcesCompat.getFont(ctx, R.font.bricolage), 700, false) }.getOrDefault(Typeface.DEFAULT_BOLD)

    /** Cells plus the text band (top, bottom) if there is a caption. */
    private fun layout(spec: CollageSpec, w: Float, h: Float): Pair<List<CellBox>, Pair<Float, Float>?> {
        val margin = spec.margin * w
        val band = if (spec.text.isBlank()) 0f else spec.textSize * w * 2f
        val cells = CollageLogic.cells(spec.template, w, h - band, spec.spacing * w, margin)
        return cells to if (band > 0f) (h - band - margin * 0.5f) to (h - margin * 0.5f) else null
    }

    fun render(spec: CollageSpec, photos: List<Bitmap?>, w: Int, h: Int, typeface: Typeface): Bitmap {
        val out = createBitmap(w, h)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val first = photos.firstOrNull { it != null }
        if (spec.bg == CollageBg.BLUR && first != null) {
            drawBlurred(c, first, w, h, paint)
            c.drawColor(0x40000000)
        } else c.drawColor(spec.color)

        val (cells, band) = layout(spec, w.toFloat(), h.toFloat())
        val radius = spec.radius * w
        val clip = Path()
        val dst = RectF()
        val src = Rect()
        cells.forEachIndexed { i, cell ->
            dst.set(cell.l, cell.t, cell.r, cell.b)
            clip.rewind(); clip.addRoundRect(dst, radius, radius, Path.Direction.CW)
            val bmp = photos.getOrNull(i)
            c.withClip(clip) {
                if (bmp == null) drawColor(0x33FFFFFF)
                else {
                    val crop = CollageLogic.centerCrop(bmp.width, bmp.height, cell.w, cell.h)
                    src.set(crop.l.roundToInt(), crop.t.roundToInt(), crop.r.roundToInt(), crop.b.roundToInt())
                    drawBitmap(bmp, src, dst, paint)
                }
            }
        }
        if (band != null) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = spec.textSize * w
                color = spec.textColor
                textAlign = Paint.Align.CENTER
                setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, 0x55000000)
            }
            val line = TextUtils.ellipsize(spec.text, tp, w * 0.9f, TextUtils.TruncateAt.END).toString()
            val mid = (band.first + band.second) / 2
            c.drawText(line, w / 2f, mid - (tp.descent() + tp.ascent()) / 2, tp)
        }
        return out
    }

    /** A cheap, strong blur: shrink a lot, then stretch back up with filtering. */
    private fun drawBlurred(c: Canvas, src: Bitmap, w: Int, h: Int, paint: Paint) {
        val crop = CollageLogic.centerCrop(src.width, src.height, w.toFloat(), h.toFloat())
        val sw = (w / 24).coerceAtLeast(4); val sh = (h / 24).coerceAtLeast(4)
        val mid = createBitmap(sw * 3, sh * 3)
        Canvas(mid).drawBitmap(src, Rect(crop.l.roundToInt(), crop.t.roundToInt(), crop.r.roundToInt(), crop.b.roundToInt()), Rect(0, 0, sw * 3, sh * 3), paint)
        val tiny = mid.scale(sw, sh)
        c.drawBitmap(tiny, null, Rect(0, 0, w, h), paint)
        if (tiny !== mid) tiny.recycle()
        mid.recycle()
    }
}
