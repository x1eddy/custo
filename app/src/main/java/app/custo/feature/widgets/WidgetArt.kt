package app.custo.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Small bitmaps for things RemoteViews can't draw: gradient/border backgrounds, the battery ring,
 * picked photos and app icons. Everything is kept small to stay under the RemoteViews memory cap.
 */
object WidgetArt {

    /** Gradient fill (custom colors only) and/or border, sized to the widget at a capped density. */
    fun background(ctx: Context, style: WidgetStyle, wDp: Float, hDp: Float): Bitmap {
        val d = min(ctx.resources.displayMetrics.density, 2f)
        val w = (wDp.takeIf { it > 0 } ?: 200f).times(d).toInt().coerceIn(8, 1000)
        val h = (hDp.takeIf { it > 0 } ?: 200f).times(d).toInt().coerceIn(8, 1000)
        if (style.drawnShape) return shaped(ctx, style, w, h, d)
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val r = style.corner * d
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        if (style.gradient && !style.themed) {
            val a = (style.alpha * 255).toInt().coerceIn(0, 255)
            fun withAlpha(col: Int) = (col and 0x00FFFFFF) or (a shl 24)
            val rad = style.gradientAngle * PI.toFloat() / 180f
            val cx = w / 2f; val cy = h / 2f
            val len = (kotlin.math.abs(cos(rad)) * w + kotlin.math.abs(sin(rad)) * h) / 2f
            p.shader = LinearGradient(
                cx - cos(rad) * len, cy - sin(rad) * len, cx + cos(rad) * len, cy + sin(rad) * len,
                withAlpha(style.bg), withAlpha(style.bg2), Shader.TileMode.CLAMP,
            )
            c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, p)
            p.shader = null
        }
        if (style.borderWidth > 0) {
            val sw = style.borderWidth * d
            p.style = Paint.Style.STROKE
            p.strokeWidth = sw
            p.color = style.borderColor
            val rect = RectF(sw / 2, sw / 2, w - sw / 2, h - sw / 2)
            c.drawRoundRect(rect, max(0f, r - sw / 2), max(0f, r - sw / 2), p)
        }
        return bmp
    }

    private fun isNight(ctx: Context) =
        (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** The widget's background color (Material You resolved for the current day/night). */
    fun bgColor(ctx: Context, style: WidgetStyle): Int =
        if (style.themed) ctx.getColor(if (isNight(ctx)) android.R.color.system_accent1_800 else android.R.color.system_accent1_100) else style.bg

    /** The widget's text color (Material You resolved for the current day/night). */
    fun fgColor(ctx: Context, style: WidgetStyle): Int =
        if (style.themed) ctx.getColor(if (isNight(ctx)) android.R.color.system_accent1_100 else android.R.color.system_accent1_900) else style.fg

    private fun withAlpha(col: Int, a: Float) = (col and 0x00FFFFFF) or ((((col ushr 24) * a.coerceIn(0f, 1f))).toInt() shl 24)

    /**
     * A background with a [WidgetShape] outline and/or a [BgLook] fill (glass, outline, photo),
     * [w] × [h] px at [d] px per dp. Everything outside the shape stays transparent.
     */
    private fun shaped(ctx: Context, style: WidgetStyle, w: Int, h: Int, d: Float): Bitmap {
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val r = style.corner * d
        val path = WidgetShapes.path(style.shape, rect, r)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
        val bg = bgColor(ctx, style)
        val fg = fgColor(ctx, style)
        val a = style.alpha.coerceIn(0f, 1f)
        when (style.bgLook) {
            BgLook.PHOTO -> {
                val photo = style.bgPhoto?.let { photoCached(ctx, it, max(w, h).coerceAtMost(900)) }
                if (photo != null) {
                    val src = blurred(photo, style.bgBlur)
                    val scale = max(w / src.width.toFloat(), h / src.height.toFloat())
                    val m = android.graphics.Matrix().apply {
                        setScale(scale, scale)
                        postTranslate((w - src.width * scale) / 2f, (h - src.height * scale) / 2f)
                    }
                    p.shader = android.graphics.BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
                    p.alpha = (a * 255).toInt().coerceAtLeast(40)
                    c.drawPath(path, p)
                    p.shader = null; p.alpha = 255
                    p.color = withAlpha(0xFF000000.toInt(), style.bgDim.coerceIn(0f, 1f) * 0.85f)
                    c.drawPath(path, p)
                } else {
                    p.color = withAlpha(bg, a)
                    c.drawPath(path, p)
                }
            }
            BgLook.GLASS -> {
                // Translucent tint, a light sheen from the top, a thin bright rim and a darker lower edge.
                p.color = withAlpha(bg, a * 0.38f)
                c.drawPath(path, p)
                p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0x40FFFFFF, 0x0DFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                c.drawPath(path, p)
                p.shader = null
                val sw = max(1f, d)
                p.style = Paint.Style.STROKE; p.strokeWidth = sw
                p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x80FFFFFF.toInt(), 0x26FFFFFF, Shader.TileMode.CLAMP)
                c.drawPath(WidgetShapes.path(style.shape, RectF(sw / 2, sw / 2, w - sw / 2, h - sw / 2), max(0f, r - sw / 2)), p)
                p.shader = null; p.style = Paint.Style.FILL
            }
            BgLook.OUTLINE -> Unit
            BgLook.SOLID -> {
                if (style.gradient && !style.themed) {
                    val rad = style.gradientAngle * PI.toFloat() / 180f
                    val cx = w / 2f; val cy = h / 2f
                    val len = (kotlin.math.abs(cos(rad)) * w + kotlin.math.abs(sin(rad)) * h) / 2f
                    p.shader = LinearGradient(cx - cos(rad) * len, cy - sin(rad) * len, cx + cos(rad) * len, cy + sin(rad) * len,
                        withAlpha(style.bg, a), withAlpha(style.bg2, a), Shader.TileMode.CLAMP)
                } else p.color = withAlpha(bg, a)
                c.drawPath(path, p)
                p.shader = null
            }
        }
        // Outline style: a line in the text color; otherwise the optional border.
        val lineW = when {
            style.bgLook == BgLook.OUTLINE -> max(style.borderWidth, 2) * d
            style.borderWidth > 0 -> style.borderWidth * d
            else -> 0f
        }
        if (lineW > 0f) {
            p.style = Paint.Style.STROKE; p.strokeWidth = lineW
            p.color = if (style.bgLook == BgLook.OUTLINE && style.borderWidth == 0) withAlpha(fg, 0.9f) else style.borderColor
            c.drawPath(WidgetShapes.path(style.shape, RectF(lineW / 2, lineW / 2, w - lineW / 2, h - lineW / 2), max(0f, r - lineW / 2)), p)
        }
        return bmp
    }

    private var photoCache: Triple<String, Int, Bitmap>? = null

    /** [photo], remembering the last one so dim/blur changes don't decode it again. */
    private fun photoCached(ctx: Context, uri: String, maxSide: Int): Bitmap? = synchronized(this) {
        photoCache?.let { (u, s, b) -> if (u == uri && s >= maxSide) return b }
        photo(ctx, uri, maxSide)?.also { photoCache = Triple(uri, maxSide, it) }
    }

    /** A cheap blur: shrink a lot, box-blur the tiny copy, let the shader scale it back up smoothly. */
    private fun blurred(src: Bitmap, amount: Float): Bitmap {
        if (amount < 0.02f) return src
        val f = 1f / (1f + amount * 14f)
        val small = Bitmap.createScaledBitmap(src, (src.width * f).toInt().coerceAtLeast(2), (src.height * f).toInt().coerceAtLeast(2), true)
        boxBlur(small, 1 + (amount * 2).toInt())
        return small
    }

    private fun boxBlur(b: Bitmap, r: Int) {
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        val out = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
            for (y in 0 until h) for (x in 0 until w) {
                var ra = 0; var rr = 0; var rg = 0; var rb = 0; var n = 0
                for (k in -r..r) {
                    val xx = if (horizontal) (x + k).coerceIn(0, w - 1) else x
                    val yy = if (horizontal) y else (y + k).coerceIn(0, h - 1)
                    val c = src[yy * w + xx]
                    ra += c ushr 24; rr += c shr 16 and 0xFF; rg += c shr 8 and 0xFF; rb += c and 0xFF; n++
                }
                dst[y * w + x] = ((ra / n) shl 24) or ((rr / n) shl 16) or ((rg / n) shl 8) or (rb / n)
            }
        }
        pass(px, out, true)
        pass(out, px, false)
        b.setPixels(px, 0, w, 0, 0, w, h)
    }

    /** White ring (track at low alpha + progress arc); tint it with the widget's text color. */
    fun ring(fraction: Float, sizePx: Int = 280, strokeFrac: Float = 0.11f): Bitmap {
        val bmp = createBitmap(sizePx, sizePx)
        val c = Canvas(bmp)
        val sw = sizePx * strokeFrac
        val rect = RectF(sw / 2, sw / 2, sizePx - sw / 2, sizePx - sw / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND }
        p.color = 0x40FFFFFF
        c.drawArc(rect, 0f, 360f, false, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawArc(rect, -90f, 360f * fraction.coerceIn(0f, 1f), false, p)
        return bmp
    }

    /** Decodes a picked photo, downscaled so its longest side is at most [maxSide] px. */
    fun photo(ctx: Context, uri: String?, maxSide: Int = 720): Bitmap? {
        if (uri == null) return null
        return runCatching {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri.toUri())
            ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                val s = info.size
                val scale = min(1f, maxSide.toFloat() / max(s.width, s.height))
                dec.setTargetSize((s.width * scale).toInt().coerceAtLeast(1), (s.height * scale).toInt().coerceAtLeast(1))
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
    }

    fun appIcon(ctx: Context, pkg: String, sizePx: Int = 112): Bitmap? = runCatching {
        ctx.packageManager.getApplicationIcon(pkg).toBitmap(sizePx, sizePx)
    }.getOrNull()

    fun appLabel(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** The moon at phase [fraction] (0 new, 0.5 full), lit from the right while waxing. */
    fun moon(fraction: Double, sizePx: Int = 240): Bitmap {
        val bmp = createBitmap(sizePx, sizePx)
        val c = Canvas(bmp)
        val r = sizePx / 2f - 2
        val cx = sizePx / 2f; val cy = sizePx / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF2E3440.toInt()
        c.drawCircle(cx, cy, r, p)
        val f = ((fraction % 1) + 1) % 1
        val waxing = f < 0.5
        val q = if (waxing) f * 2 else (1 - f) * 2 // 0 new .. 1 full
        if (q > 0.01) {
            if (!waxing) c.scale(-1f, 1f, cx, cy)
            val k = r * kotlin.math.abs(cos(q * PI)).toFloat()
            val path = android.graphics.Path().apply {
                moveTo(cx, cy - r)
                arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 180f)
                arcTo(RectF(cx - k, cy - r, cx + k, cy + r), 90f, if (q < 0.5) -180f else 180f)
                close()
            }
            p.color = 0xFFF4F1DE.toInt()
            c.drawPath(path, p)
            if (!waxing) c.scale(-1f, 1f, cx, cy)
        }
        // A few soft craters over the whole disk.
        p.color = 0x18000000
        listOf(Triple(-0.3f, -0.25f, 0.16f), Triple(0.25f, 0.1f, 0.12f), Triple(-0.05f, 0.4f, 0.09f), Triple(0.35f, -0.4f, 0.07f)).forEach { (x, y, rr) ->
            c.drawCircle(cx + x * r, cy + y * r, rr * r, p)
        }
        return bmp
    }

    /** A wooden fish with its mallet, raised or striking. */
    fun woodenFish(struck: Boolean, w: Int = 260, h: Int = 220): Bitmap {
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val body = RectF(w * 0.08f, h * 0.30f, w * 0.80f, h * 0.95f)
        p.shader = android.graphics.RadialGradient(body.centerX() - body.width() * 0.2f, body.top + body.height() * 0.3f, body.width() * 0.7f,
            0xFFC98A4B.toInt(), 0xFF7A4520.toInt(), Shader.TileMode.CLAMP)
        c.drawOval(body, p)
        p.shader = null
        // Mouth slit and a little eye.
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = h * 0.045f; p.color = 0xFF3E2210.toInt()
        c.drawArc(RectF(body.left + body.width() * 0.12f, body.top + body.height() * 0.35f, body.right - body.width() * 0.12f, body.bottom - body.height() * 0.12f), 20f, 140f, false, p)
        p.style = Paint.Style.FILL
        c.drawCircle(body.left + body.width() * 0.28f, body.top + body.height() * 0.38f, h * 0.035f, p)
        // Highlight.
        p.color = 0x33FFFFFF
        c.drawOval(RectF(body.left + body.width() * 0.25f, body.top + body.height() * 0.08f, body.left + body.width() * 0.6f, body.top + body.height() * 0.25f), p)
        // Mallet: stick and head, pivoting at the right edge.
        c.withRotation(if (struck) -8f else -38f, w * 0.95f, h * 0.55f) {
            p.color = 0xFF8D5A2B.toInt()
            p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.05f
            drawLine(w * 0.95f, h * 0.55f, w * 0.62f, h * 0.20f, p)
            p.style = Paint.Style.FILL; p.color = 0xFFB07A45.toInt()
            drawOval(RectF(w * 0.50f, h * 0.06f, w * 0.70f, h * 0.30f), p)
        }
        if (struck) {
            p.color = 0xCCFFD54F.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.025f
            listOf(-60f, -30f, 0f).forEach { a ->
                val rad = a * PI.toFloat() / 180f
                c.drawLine(w * 0.55f + cos(rad) * h * 0.12f, h * 0.28f + sin(rad) * h * 0.12f, w * 0.55f + cos(rad) * h * 0.2f, h * 0.28f + sin(rad) * h * 0.2f, p)
            }
        }
        return bmp
    }

    /** A drawn face for [mood]: a colored disk with simple eyes and mouth. */
    fun moodFace(mood: Mood, sizePx: Int = 96): Bitmap {
        val bmp = createBitmap(sizePx, sizePx)
        val c = Canvas(bmp)
        val s = sizePx.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = mood.color
        c.drawCircle(s / 2, s / 2, s / 2 - 1, p)
        p.color = 0xFF2B2B2B.toInt()
        val eyeY = s * 0.40f
        val closed = mood == Mood.CALM || mood == Mood.TIRED
        if (closed) {
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.05f; p.strokeCap = Paint.Cap.ROUND
            c.drawArc(RectF(s * 0.27f, eyeY - s * 0.05f, s * 0.41f, eyeY + s * 0.07f), 0f, 180f, false, p)
            c.drawArc(RectF(s * 0.59f, eyeY - s * 0.05f, s * 0.73f, eyeY + s * 0.07f), 0f, 180f, false, p)
            p.style = Paint.Style.FILL
        } else {
            c.drawCircle(s * 0.34f, eyeY, s * 0.055f, p)
            c.drawCircle(s * 0.66f, eyeY, s * 0.055f, p)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.055f; p.strokeCap = Paint.Cap.ROUND
        if (mood == Mood.ANGRY) {
            c.drawLine(s * 0.24f, s * 0.26f, s * 0.42f, s * 0.32f, p)
            c.drawLine(s * 0.76f, s * 0.26f, s * 0.58f, s * 0.32f, p)
        }
        if (mood == Mood.ANXIOUS) {
            c.drawLine(s * 0.26f, s * 0.30f, s * 0.42f, s * 0.25f, p)
            c.drawLine(s * 0.74f, s * 0.30f, s * 0.58f, s * 0.25f, p)
        }
        val curve = when (mood) {
            Mood.GREAT -> 0.16f; Mood.HAPPY -> 0.10f; Mood.CALM -> 0.06f; Mood.OKAY, Mood.SICK, Mood.TIRED -> 0f
            Mood.SAD -> -0.10f; Mood.ANXIOUS -> -0.05f; Mood.ANGRY -> -0.08f
        }
        val my = s * 0.66f
        val path = android.graphics.Path().apply {
            moveTo(s * 0.32f, my)
            quadTo(s * 0.5f, my + curve * s * 2, s * 0.68f, my)
        }
        if (mood == Mood.GREAT) { p.style = Paint.Style.FILL_AND_STROKE; path.close() }
        c.drawPath(path, p)
        if (mood == Mood.SICK) {
            p.style = Paint.Style.FILL; p.color = 0x55FFFFFF
            c.drawCircle(s * 0.5f, s * 0.82f, s * 0.07f, p)
        }
        return bmp
    }
}
