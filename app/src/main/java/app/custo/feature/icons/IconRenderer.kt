package app.custo.feature.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import app.custo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Draws [IconStyle]s to bitmaps. Pure software canvas, safe to call off the main thread. */
object IconRenderer {
    /** Adaptive icon canvas: 108dp at xxxhdpi. */
    const val SIZE = 432

    /** Rendered icons are shared from this cache: callers must not recycle them. */
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    @Volatile private var font: Typeface? = null

    suspend fun render(context: Context, style: IconStyle, pkg: String?, label: String = "", size: Int = SIZE): Bitmap =
        withContext(Dispatchers.Default) {
            val key = "${style.hashCode()}|$pkg|$label|$size"
            cache.get(key) ?: draw(context, style, pkg, label, size).also { cache.put(key, it) }
        }

    fun draw(context: Context, style: IconStyle, pkg: String?, label: String, size: Int): Bitmap {
        val s = size.toFloat()
        // Full-bleed content: background, pattern, foreground.
        val content = createBitmap(size, size)
        val c = Canvas(content)
        drawBackground(c, style, s)
        drawPattern(c, style, s)

        val visible = if (style.launcherShape) s * (72f / 108f) else s * 0.92f
        val fgPx = (style.fgScale.coerceIn(0.2f, 0.95f) * visible).toInt().coerceAtLeast(8)
        foreground(context, style, pkg, label, fgPx)?.let { fg -> drawForeground(c, fg, style, s) }

        if (style.launcherShape) return content

        // Cut the shape with anti-aliasing by painting the path with the content as a shader.
        val out = createBitmap(size, size)
        val oc = Canvas(out)
        val margin = s * 0.04f
        val path = shapePath(style.shape, RectF(margin, margin, s - margin, s - margin))
        if (style.shadow > 0f) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((90 * style.shadow).toInt().coerceIn(0, 255), 0, 0, 0)
                maskFilter = BlurMaskFilter(s * 0.02f, BlurMaskFilter.Blur.NORMAL)
            }
            oc.withTranslation(0f, s * 0.012f) { drawPath(path, sp) }
        }
        oc.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) })
        content.recycle()
        return out
    }

    // --- shapes ---

    fun shapePath(shape: IconShape, r: RectF): Path {
        val p = Path()
        val w = r.width(); val cx = r.centerX(); val cy = r.centerY(); val rad = w / 2f
        when (shape) {
            IconShape.CIRCLE -> p.addOval(r, Path.Direction.CW)
            IconShape.ROUNDED -> p.addRoundRect(r, w * 0.22f, w * 0.22f, Path.Direction.CW)
            IconShape.SQUIRCLE -> polar(p, cx, cy, 160) { t ->
                // Superellipse |x|^4 + |y|^4 = 1
                val ct = cos(t); val st = sin(t)
                rad / (ct.pow(4) + st.pow(4)).pow(0.25)
            }
            IconShape.TEARDROP -> {
                val big = w / 2f; val small = w * 0.12f
                p.addRoundRect(r, floatArrayOf(big, big, big, big, small, small, big, big), Path.Direction.CW)
            }
            IconShape.HEXAGON -> {
                for (i in 0 until 6) {
                    val a = PI / 3 * i + PI / 6
                    val x = cx + rad * cos(a).toFloat(); val y = cy + rad * sin(a).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            IconShape.FLOWER -> polar(p, cx, cy, 240) { t -> rad * (0.9 + 0.1 * cos(8 * t)) }
        }
        return p
    }

    private inline fun polar(p: Path, cx: Float, cy: Float, steps: Int, r: (Double) -> Double) {
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val rr = r(t)
            val x = cx + (rr * cos(t)).toFloat(); val y = cy + (rr * sin(t)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    // --- background & pattern ---

    private fun drawBackground(c: Canvas, style: IconStyle, s: Float) {
        val colors = style.bg.ifEmpty { listOf(Color.GRAY) }.take(3)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (colors.size == 1) {
            paint.color = colors[0]
        } else {
            val a = Math.toRadians(style.angle.toDouble())
            val dx = (cos(a) * s / 2).toFloat(); val dy = (sin(a) * s / 2).toFloat()
            paint.shader = LinearGradient(s / 2 - dx, s / 2 - dy, s / 2 + dx, s / 2 + dy, colors.toIntArray(), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, s, s, paint)
    }

    private fun drawPattern(c: Canvas, style: IconStyle, s: Float) {
        if (style.pattern == IconPattern.NONE || style.patternOpacity <= 0f) return
        val alpha = (255 * style.patternOpacity.coerceIn(0f, 1f)).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.patternColor; this.alpha = alpha }
        val step = s * (0.06f + 0.14f * style.patternSize.coerceIn(0f, 1f))
        when (style.pattern) {
            IconPattern.DOTS -> {
                var row = 0; var y = step / 2
                while (y < s + step) {
                    var x = if (row % 2 == 0) step / 2 else step
                    while (x < s + step) { c.drawCircle(x, y, step * 0.2f, paint); x += step }
                    y += step * 0.87f; row++
                }
            }
            IconPattern.STRIPES -> c.withRotation(style.angle + 90f, s / 2, s / 2) {
                var x = -s
                while (x < s * 2) { drawRect(x, -s, x + step * 0.4f, s * 2, paint); x += step }
            }
            IconPattern.GRID -> {
                paint.strokeWidth = max(1.5f, step * 0.08f)
                var v = step / 2
                while (v < s) { c.drawLine(v, 0f, v, s, paint); c.drawLine(0f, v, s, v, paint); v += step }
            }
            IconPattern.WAVES -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = step * 0.16f
                paint.strokeCap = Paint.Cap.ROUND
                var y = step / 2
                while (y < s + step) {
                    val path = Path()
                    var x = 0f
                    path.moveTo(0f, y)
                    while (x <= s) {
                        path.lineTo(x, y + sin(x / step * PI).toFloat() * step * 0.22f)
                        x += 4f
                    }
                    c.drawPath(path, paint)
                    y += step * 0.75f
                }
            }
            IconPattern.CONFETTI -> {
                val rnd = Random(style.seed)
                val n = (14 + 40 * (1f - style.patternSize)).toInt()
                repeat(n) {
                    val x = rnd.nextFloat() * s; val y = rnd.nextFloat() * s
                    val w = step * (0.18f + rnd.nextFloat() * 0.25f)
                    c.withRotation(rnd.nextFloat() * 180f, x, y) {
                        if (rnd.nextBoolean()) drawCircle(x, y, w * 0.45f, paint)
                        else drawRoundRect(x - w, y - w * 0.3f, x + w, y + w * 0.3f, w * 0.3f, w * 0.3f, paint)
                    }
                }
            }
            IconPattern.NONE -> Unit
        }
    }

    // --- foreground ---

    private fun drawForeground(c: Canvas, fg: Bitmap, style: IconStyle, s: Float) {
        val left = (s - fg.width) / 2f; val top = (s - fg.height) / 2f
        val offset = IntArray(2)
        if (style.glow > 0f) {
            val blur = BlurMaskFilter(max(1f, s * 0.05f * style.glow), BlurMaskFilter.Blur.NORMAL)
            val halo = fg.extractAlpha(Paint().apply { maskFilter = blur }, offset)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.glowColor; alpha = (255 * min(1f, 0.5f + style.glow)).toInt() }
            c.drawBitmap(halo, left + offset[0], top + offset[1], p)
            if (style.glow > 0.5f) c.drawBitmap(halo, left + offset[0], top + offset[1], p)
            halo.recycle()
        }
        if (style.shadow > 0f) {
            val blur = BlurMaskFilter(s * 0.018f, BlurMaskFilter.Blur.NORMAL)
            val sh = fg.extractAlpha(Paint().apply { maskFilter = blur }, offset)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((120 * style.shadow).toInt().coerceIn(0, 255), 0, 0, 0) }
            c.drawBitmap(sh, left + offset[0], top + offset[1] + s * 0.018f, p)
            sh.recycle()
        }
        c.drawBitmap(fg, left, top, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        fg.recycle()
    }

    private fun foreground(context: Context, style: IconStyle, pkg: String?, label: String, px: Int): Bitmap? = when {
        pkg != null && style.fg == FgMode.TEXT -> textBitmap(context, IconStyle.initials(label.ifBlank { pkg.substringAfterLast('.') }), style.fgColor, px)
        pkg != null && style.fg == FgMode.APP_ORIGINAL -> appIcon(context, pkg)?.let { drawableBitmap(it, px, expandLayers = false, tint = null) }
        pkg != null -> appIcon(context, pkg)?.let { appGlyph(it, px, style.fgColor) }
        style.fg == FgMode.TEXT -> textBitmap(context, style.text.take(2), style.fgColor, px)
        else -> glyphBitmap(GLYPHS[style.glyph] ?: GLYPHS.values.first(), style.fgColor, px)
    }

    fun appIcon(context: Context, pkg: String): Drawable? = runCatching {
        val pm = context.packageManager
        val launch = pm.getLaunchIntentForPackage(pkg)
        if (launch != null) pm.getActivityIcon(launch) else pm.getApplicationIcon(pkg)
    }.getOrNull()

    /** The app's one-color glyph: themed-icon layer when the app has one, else its foreground layer. */
    private fun appGlyph(d: Drawable, px: Int, color: Int): Bitmap {
        if (d is AdaptiveIconDrawable) {
            val mono = if (Build.VERSION.SDK_INT >= 33) d.monochrome else null
            val layer = mono ?: d.foreground
            if (layer != null) return drawableBitmap(layer, px, expandLayers = true, tint = color)
        }
        return drawableBitmap(d, px, expandLayers = false, tint = color)
    }

    /** Adaptive layers are 108dp with the art in the middle 72dp, so they're drawn 1.5× bigger. */
    private fun drawableBitmap(d: Drawable, px: Int, expandLayers: Boolean, tint: Int?): Bitmap {
        val b = createBitmap(px, px)
        val c = Canvas(b)
        val old = d.bounds
        if (expandLayers) { val e = px / 4; d.setBounds(-e, -e, px + e, px + e) } else d.setBounds(0, 0, px, px)
        if (tint != null) {
            c.saveLayer(null, Paint().apply { colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN) })
            d.draw(c)
            c.restore()
        } else d.draw(c)
        d.bounds = old
        return b
    }

    fun glyphBitmap(v: ImageVector, color: Int, px: Int): Bitmap {
        val b = createBitmap(px, px)
        val c = Canvas(b)
        val m = Matrix().apply { setScale(px / v.viewportWidth, px / v.viewportHeight) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        fun walk(g: VectorGroup) {
            for (i in 0 until g.size) {
                when (val n = g[i]) {
                    is VectorGroup -> walk(n)
                    is VectorPath -> {
                        val path = PathParser().addPathNodes(n.pathData).toPath().also {
                            it.fillType = if (n.pathFillType == PathFillType.EvenOdd) PathFillType.EvenOdd else PathFillType.NonZero
                        }.asAndroidPath()
                        path.transform(m)
                        paint.alpha = (255 * n.fillAlpha).toInt()
                        c.drawPath(path, paint)
                    }
                }
            }
        }
        walk(v.root)
        return b
    }

    private fun typeface(context: Context): Typeface =
        font ?: (runCatching { ResourcesCompat.getFont(context, R.font.bricolage) }.getOrNull() ?: Typeface.DEFAULT_BOLD).also { font = it }

    fun textBitmap(context: Context, text: String, color: Int, px: Int): Bitmap {
        val b = createBitmap(px, px)
        val t = text.ifBlank { "?" }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = typeface(context)
            fontVariationSettings = "'wght' 800, 'opsz' 96, 'wdth' 88"
            textAlign = Paint.Align.CENTER
            textSize = px * 0.9f
        }
        val w = paint.measureText(t)
        if (w > px * 0.95f) paint.textSize *= px * 0.95f / w
        val bounds = android.graphics.Rect()
        paint.getTextBounds(t, 0, t.length, bounds)
        Canvas(b).drawText(t, px / 2f, px / 2f - bounds.exactCenterY(), paint)
        return b
    }

    /** Fits any picture (pack icon, imported image) into a square icon bitmap. */
    fun fitBitmap(src: Bitmap, size: Int = SIZE): Bitmap {
        val b = createBitmap(size, size)
        val scale = min(size.toFloat() / src.width, size.toFloat() / src.height)
        val w = src.width * scale; val h = src.height * scale
        Canvas(b).drawBitmap(src, null, RectF((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return b
    }

    fun drawableToBitmap(d: Drawable, size: Int = SIZE): Bitmap = drawableBitmap(d, size, expandLayers = false, tint = null)
}
