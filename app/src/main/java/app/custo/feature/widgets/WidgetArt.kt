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
}
