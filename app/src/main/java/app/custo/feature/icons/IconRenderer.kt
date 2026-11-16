package app.custo.feature.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
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
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import app.custo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Draws [IconStyle]s to bitmaps. Pure software canvas, safe to call off the main thread. */
object IconRenderer {
    /** Adaptive icon canvas: 108dp at xxxhdpi. */
    const val SIZE = 432

    /** Rendered icons are shared from this cache: callers must not recycle them. */
    private val cache = object : LruCache<String, Bitmap>(36 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    @Volatile private var font: Typeface? = null

    private fun key(style: IconStyle, pkg: String?, label: String, size: Int, tile: Tile?) =
        "${style.hashCode()}|$pkg|$label|$size|${tile?.index}/${tile?.count}"

    suspend fun render(context: Context, style: IconStyle, pkg: String?, label: String = "", size: Int = SIZE, tile: Tile? = null): Bitmap =
        peek(style, pkg, label, size, tile) ?: withContext(Dispatchers.Default) {
            val key = key(style, pkg, label, size, tile)
            cache.get(key) ?: run {
                val st = style.forApp(pkg)
                val pack = if (pkg != null && st.fg == FgMode.PACK && st.pack.isNotEmpty()) {
                    runCatching { IconPacks.iconFor(context, st.pack, pkg, size) }.getOrNull()
                } else null
                val image = if (st.usesImage) {
                    withContext(Dispatchers.IO) { IconImages.load(st.image!!, if (st.puzzle && tile != null) 1440 else 720) }
                } else null
                draw(context, st, pkg, label, size, tile, pack, image)
            }.also { cache.put(key, it) }
        }

    /** The cached render, if any; cheap enough to call during composition. */
    fun peek(style: IconStyle, pkg: String?, label: String = "", size: Int = SIZE, tile: Tile? = null): Bitmap? =
        cache.get(key(style, pkg, label, size, tile))

    /**
     * Draws [style] (already resolved for the app, see [IconStyle.forApp]). [pack] is the icon-pack
     * picture for [FgMode.PACK], [image] the background picture, [tile] the puzzle piece.
     */
    fun draw(
        context: Context, style: IconStyle, pkg: String?, label: String, size: Int,
        tile: Tile? = null, pack: Bitmap? = null, image: Bitmap? = null,
    ): Bitmap {
        val s = size.toFloat()
        // Full-bleed content: background, pattern, texture, foreground.
        val content = createBitmap(size, size)
        val c = Canvas(content)
        drawBackground(c, style, s, image, tile)
        drawPattern(c, style, s)
        drawTexture(c, style, s)

        val visible = if (style.launcherShape) s * (72f / 108f) else s * 0.92f
        val fgPx = (style.fgScale.coerceIn(0.2f, 0.95f) * visible).toInt().coerceAtLeast(8)
        foreground(context, style, pkg, label, fgPx, pack)?.let { fg -> drawForeground(c, fg, style, s) }

        if (style.launcherShape) {
            // The launcher cuts the shape; keep stickers inside its safe zone.
            drawSticker(c, style, s, inset = s * (18f / 108f))
            return content
        }

        // Cut the shape with anti-aliasing by painting the path with the content as a shader.
        val out = createBitmap(size, size)
        val oc = Canvas(out)
        val borderPx = style.border.coerceIn(0f, 1f) * s * 0.07f
        val margin = s * 0.04f + borderPx / 2f + style.neumorph.coerceIn(0f, 1f) * s * 0.035f +
            if (style.sticker != Sticker.NONE) s * 0.03f else 0f
        val rect = RectF(margin, margin, s - margin, s - margin)
        val path = shapePath(style, rect)

        if (style.neumorph > 0f) {
            val d = s * 0.03f * style.neumorph
            val blur = BlurMaskFilter(s * 0.035f * style.neumorph + 1f, BlurMaskFilter.Blur.NORMAL)
            val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((230 * style.neumorph).toInt().coerceIn(0, 255), 255, 255, 255); maskFilter = blur }
            val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((110 * style.neumorph).toInt().coerceIn(0, 255), 60, 70, 95); maskFilter = blur }
            oc.withTranslation(-d, -d) { drawPath(path, light) }
            oc.withTranslation(d, d) { drawPath(path, dark) }
        }
        if (style.shadow > 0f) {
            val (dx, dy) = shadowOffset(style, s * 0.04f, 0f)
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(style.shadowColor, 90 * style.shadow)
                maskFilter = BlurMaskFilter(max(1f, s * 0.0667f * style.shadowBlur), BlurMaskFilter.Blur.NORMAL)
            }
            oc.withTranslation(dx, dy) { drawPath(path, sp) }
        }
        oc.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) })
        content.recycle()

        // Effects inside the shape.
        if (style.bevel > 0f || style.gloss > 0f || style.innerStroke > 0f) {
            val save = oc.save()
            oc.clipPath(path)
            if (style.bevel > 0f) {
                val w = s * 0.09f * style.bevel
                val a = (200 * style.bevel).toInt().coerceIn(0, 255)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE; strokeWidth = w * 2
                    maskFilter = BlurMaskFilter(w * 0.6f + 1f, BlurMaskFilter.Blur.NORMAL)
                    shader = LinearGradient(0f, 0f, s, s, intArrayOf(Color.argb(a, 255, 255, 255), Color.TRANSPARENT, Color.argb(a, 0, 0, 0)), null, Shader.TileMode.CLAMP)
                }
                oc.drawPath(path, p)
            }
            if (style.gloss > 0f) {
                val a = (170 * style.gloss).toInt().coerceIn(0, 255)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, rect.top, 0f, rect.centerY(), Color.argb(a, 255, 255, 255), Color.argb(a / 6, 255, 255, 255), Shader.TileMode.CLAMP)
                }
                oc.drawOval(rect.left - rect.width() * 0.25f, rect.top - rect.height() * 0.55f, rect.right + rect.width() * 0.25f, rect.centerY() + rect.height() * 0.02f, p)
                val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(rect.left, rect.bottom, rect.centerX(), rect.centerY(), Color.argb(a / 3, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                }
                oc.drawRect(rect, shine)
            }
            if (style.innerStroke > 0f) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE; strokeWidth = s * 0.05f * style.innerStroke + 2f; color = style.innerStrokeColor
                }
                oc.withScale(0.9f, 0.9f, s / 2, s / 2) { drawPath(path, p) }
            }
            oc.restoreToCount(save)
        }
        if (borderPx > 0.5f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE; strokeWidth = borderPx; color = style.borderColor
                strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
                if (style.borderDashed) pathEffect = DashPathEffect(floatArrayOf(borderPx * 1.6f, borderPx * 1.4f), 0f)
            }
            oc.drawPath(path, p)
        }
        drawSticker(oc, style, s, inset = 0f)
        return out
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha * Color.alpha(color) / 255f).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    /**
     * Shadow offset where distance 1 = [full] px, turned back by [rotation] so the light stays put
     * when the foreground is rotated.
     */
    private fun shadowOffset(style: IconStyle, full: Float, rotation: Float): Pair<Float, Float> {
        val d = style.shadowDistance.coerceIn(0f, 1f) * full
        val a = Math.toRadians((style.shadowAngle - rotation).toDouble())
        return (cos(a) * d).toFloat() to (sin(a) * d).toFloat()
    }

    // --- shapes ---

    fun shapePath(style: IconStyle, r: RectF): Path = shapePath(style.shape, r, style.corner, style.seed)

    fun shapePath(shape: IconShape, r: RectF, corner: Float = 0.22f, seed: Int = 7): Path =
        pathOf(ShapeGeometry.outline(shape, r.left, r.top, r.width(), corner, seed))

    private fun pathOf(pts: FloatArray, into: Path = Path()): Path {
        for (i in pts.indices step 2) if (i == 0) into.moveTo(pts[0], pts[1]) else into.lineTo(pts[i], pts[i + 1])
        into.close()
        return into
    }

    // --- background, pattern, texture ---

    private fun drawBackground(c: Canvas, style: IconStyle, s: Float, image: Bitmap?, tile: Tile?) {
        val colors = style.bg.ifEmpty { listOf(Color.GRAY) }.take(4)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val kind = if (style.bgKind == BgKind.IMAGE) BgKind.AUTO else style.bgKind
        val center = s / 2
        when {
            colors.size == 1 || kind == BgKind.SOLID -> paint.color = colors[0]
            kind == BgKind.RADIAL -> paint.shader = RadialGradient(center, center * 0.85f, s * 0.75f, colors.toIntArray(), null, Shader.TileMode.CLAMP)
            kind == BgKind.SWEEP -> paint.shader = SweepGradient(center, center, (colors + colors[0]).toIntArray(), null).apply {
                setLocalMatrix(Matrix().apply { setRotate(style.angle, center, center) })
            }
            else -> {
                val a = Math.toRadians(style.angle.toDouble())
                val dx = (cos(a) * s / 2).toFloat(); val dy = (sin(a) * s / 2).toFloat()
                paint.shader = LinearGradient(center - dx, center - dy, center + dx, center + dy, colors.toIntArray(), null, Shader.TileMode.CLAMP)
            }
        }
        c.drawRect(0f, 0f, s, s, paint)
        if (image != null && style.usesImage) {
            val src = PuzzleMath.sourceRect(
                image.width.toFloat(), image.height.toFloat(), if (style.puzzle) tile else null,
                style.imageZoom, style.imageX, style.imageY,
            )
            val l = src[0].toInt(); val t = src[1].toInt()
            val r = Rect(l, t, src[2].toInt().coerceAtLeast(l + 1), src[3].toInt().coerceAtLeast(t + 1))
            c.drawBitmap(image, r, RectF(0f, 0f, s, s), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
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

    private fun drawTexture(c: Canvas, style: IconStyle, s: Float) {
        if (style.texture == IconTexture.NONE || style.textureOpacity <= 0f) return
        val alpha = (255 * style.textureOpacity.coerceIn(0f, 1f)).toInt()
        val rnd = Random(style.seed * 7 + style.texture.ordinal)
        when (style.texture) {
            IconTexture.NOISE, IconTexture.PAPER -> {
                val n = (s / 2).toInt().coerceIn(48, 256)
                val px = IntArray(n * n)
                val spread = if (style.texture == IconTexture.PAPER) 40 else 95
                for (i in px.indices) {
                    val v = (128 + (rnd.nextFloat() - 0.5f) * 2 * spread).toInt().coerceIn(0, 255)
                    px[i] = Color.rgb(v, v, v)
                }
                val noise = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
                val p = Paint(if (style.texture == IconTexture.PAPER) Paint.FILTER_BITMAP_FLAG else 0).apply { this.alpha = alpha; blendMode = BlendMode.OVERLAY }
                c.drawBitmap(noise, null, RectF(0f, 0f, s, s), p)
                noise.recycle()
                if (style.texture == IconTexture.PAPER) {
                    val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = max(1f, s * 0.004f) }
                    repeat(40) {
                        val x = rnd.nextFloat() * s; val y = rnd.nextFloat() * s
                        val len = s * (0.04f + rnd.nextFloat() * 0.08f); val a = rnd.nextFloat() * PI.toFloat()
                        fp.color = if (rnd.nextBoolean()) Color.argb(alpha / 2, 255, 255, 255) else Color.argb(alpha / 4, 0, 0, 0)
                        val path = Path().apply {
                            moveTo(x, y); quadTo(x + cos(a) * len / 2 + len * 0.2f, y + sin(a) * len / 2, x + cos(a) * len, y + sin(a) * len)
                        }
                        c.drawPath(path, fp)
                    }
                }
            }
            IconTexture.GLITTER -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                val count = (s * 0.9f).toInt().coerceIn(60, 420)
                repeat(count) {
                    val x = rnd.nextFloat() * s; val y = rnd.nextFloat() * s
                    val r = s * (0.003f + rnd.nextFloat() * 0.008f)
                    val light = rnd.nextFloat() < 0.7f
                    p.color = if (light) Color.argb((alpha * (0.4f + rnd.nextFloat() * 0.6f)).toInt(), 255, 255, 255) else Color.argb(alpha / 2, 255, 215, 120)
                    c.drawCircle(x, y, r, p)
                }
                repeat(6) {
                    val x = rnd.nextFloat() * s; val y = rnd.nextFloat() * s; val r = s * (0.025f + rnd.nextFloat() * 0.03f)
                    p.color = Color.argb(alpha, 255, 255, 255)
                    c.drawPath(pathOf(ShapeGeometry.sticker(Sticker.SPARKLE, x - r, y - r, r * 2).first()), p)
                }
            }
            IconTexture.CARBON -> {
                val tileSize = (s / 11f).toInt().coerceAtLeast(8)
                val t = createBitmap(tileSize, tileSize)
                val tc = Canvas(t)
                val h = tileSize / 2f
                val dark = Color.argb(alpha, 0, 0, 0); val light = Color.argb(alpha, 255, 255, 255)
                for (i in 0..1) for (j in 0..1) {
                    val l = i * h; val tp = j * h
                    val g = if ((i + j) % 2 == 0) LinearGradient(l, tp, l + h, tp, intArrayOf(dark, light, dark), null, Shader.TileMode.CLAMP)
                    else LinearGradient(l, tp, l, tp + h, intArrayOf(dark, light, dark), null, Shader.TileMode.CLAMP)
                    tc.drawRect(l, tp, l + h, tp + h, Paint().apply { shader = g })
                }
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = BitmapShader(t, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(Matrix().apply { setRotate(45f) }) }
                    blendMode = BlendMode.OVERLAY
                }
                c.drawRect(0f, 0f, s, s, p)
                t.recycle()
            }
            IconTexture.NONE -> Unit
        }
    }

    // --- stickers ---

    private fun drawSticker(c: Canvas, style: IconStyle, s: Float, inset: Float) {
        if (style.sticker == Sticker.NONE) return
        val size = s * 0.3f
        val pad = inset + s * 0.015f
        val left = if (style.stickerCorner == StickerCorner.TOP_START || style.stickerCorner == StickerCorner.BOTTOM_START) pad else s - pad - size
        val top = if (style.stickerCorner == StickerCorner.TOP_START || style.stickerCorner == StickerCorner.TOP_END) pad else s - pad - size
        val path = Path().apply { fillType = Path.FillType.WINDING }
        ShapeGeometry.sticker(style.sticker, left + size * 0.1f, top + size * 0.1f, size * 0.8f).forEach { pathOf(it, path) }
        // Die-cut sticker: soft shadow, white rim, color, a small shine.
        c.withTranslation(0f, size * 0.04f) {
            drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.FILL_AND_STROKE; strokeWidth = size * 0.14f; strokeJoin = Paint.Join.ROUND
                color = Color.argb(70, 0, 0, 0); maskFilter = BlurMaskFilter(size * 0.05f + 1f, BlurMaskFilter.Blur.NORMAL)
            })
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = size * 0.14f; strokeJoin = Paint.Join.ROUND; color = Color.WHITE })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.stickerColor })
        c.drawCircle(left + size * 0.36f, top + size * 0.36f, size * 0.06f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255) })
    }

    // --- foreground ---

    private fun drawForeground(c: Canvas, fg: Bitmap, style: IconStyle, s: Float) {
        val left = (s - fg.width) / 2f; val top = (s - fg.height) / 2f
        val offset = IntArray(2)
        val rot = style.fgRotation
        c.withTranslation(style.fgX.coerceIn(-0.5f, 0.5f) * s, style.fgY.coerceIn(-0.5f, 0.5f) * s) {
            withRotation(rot, s / 2, s / 2) {
                if (style.glow > 0f) {
                    val blur = BlurMaskFilter(max(1f, s * 0.05f * style.glow), BlurMaskFilter.Blur.NORMAL)
                    val halo = fg.extractAlpha(Paint().apply { maskFilter = blur }, offset)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.glowColor; alpha = (255 * min(1f, 0.5f + style.glow)).toInt() }
                    drawBitmap(halo, left + offset[0], top + offset[1], p)
                    if (style.glow > 0.5f) drawBitmap(halo, left + offset[0], top + offset[1], p)
                    halo.recycle()
                }
                if (style.shadow > 0f) {
                    val blur = BlurMaskFilter(max(1f, s * 0.06f * style.shadowBlur), BlurMaskFilter.Blur.NORMAL)
                    val sh = fg.extractAlpha(Paint().apply { maskFilter = blur }, offset)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(style.shadowColor, 120 * style.shadow) }
                    val (dx, dy) = shadowOffset(style, s * 0.06f, rot)
                    drawBitmap(sh, left + offset[0] + dx, top + offset[1] + dy, p)
                    sh.recycle()
                }
                if (style.emboss > 0f) {
                    val e = s * 0.012f * (0.5f + style.emboss)
                    val mask = fg.extractAlpha()
                    val lightOff = turn(-e, -e, -rot); val darkOff = turn(e, e, -rot)
                    drawBitmap(mask, left + lightOff.first, top + lightOff.second, Paint().apply { color = Color.argb((210 * style.emboss).toInt().coerceIn(0, 255), 255, 255, 255) })
                    drawBitmap(mask, left + darkOff.first, top + darkOff.second, Paint().apply { color = Color.argb((150 * style.emboss).toInt().coerceIn(0, 255), 0, 0, 0) })
                    mask.recycle()
                }
                drawBitmap(fg, left, top, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
        }
        fg.recycle()
    }

    private fun turn(x: Float, y: Float, degrees: Float): Pair<Float, Float> {
        val a = Math.toRadians(degrees.toDouble())
        return (x * cos(a) - y * sin(a)).toFloat() to (x * sin(a) + y * cos(a)).toFloat()
    }

    private fun foreground(context: Context, style: IconStyle, pkg: String?, label: String, px: Int, pack: Bitmap?): Bitmap? = when {
        pkg != null && style.fg == FgMode.TEXT -> textBitmap(context, IconStyle.initials(label.ifBlank { pkg.substringAfterLast('.') }), style.fgColor, px)
        pkg != null && style.fg == FgMode.APP_ORIGINAL -> appIcon(context, pkg)?.let { drawableBitmap(it, px, expandLayers = false, tint = null) }
        pkg != null && style.fg == FgMode.PACK && pack != null -> fitBitmap(pack, px)
        pkg != null && style.fg == FgMode.DUOTONE -> appIcon(context, pkg)?.let { duotone(drawableBitmap(it, px, expandLayers = false, tint = null), style.fgColor, style.fgColor2) }
        pkg != null && style.fg == FgMode.SILHOUETTE -> appIcon(context, pkg)?.let { silhouette(it, px, style.fgColor) }
        pkg != null && style.fg == FgMode.OUTLINE -> appIcon(context, pkg)?.let { outline(appGlyph(it, px, Color.WHITE), style.fgColor) }
        pkg != null -> appIcon(context, pkg)?.let { appGlyph(it, px, style.fgColor) }
        style.fg == FgMode.TEXT -> textBitmap(context, style.text.take(2), style.fgColor, px)
        style.fg == FgMode.OUTLINE -> outline(glyphBitmap(GLYPHS[style.glyph] ?: GLYPHS.values.first(), Color.WHITE, px), style.fgColor)
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
            val mono = if (android.os.Build.VERSION.SDK_INT >= 33) d.monochrome else null
            val layer = mono ?: d.foreground
            if (layer != null) return drawableBitmap(layer, px, expandLayers = true, tint = color)
        }
        return drawableBitmap(d, px, expandLayers = false, tint = color)
    }

    /** The colorful logo's own outline (foreground layer, never the themed one) in one color. */
    private fun silhouette(d: Drawable, px: Int, color: Int): Bitmap {
        val layer = (d as? AdaptiveIconDrawable)?.foreground
        return if (layer != null) drawableBitmap(layer, px, expandLayers = true, tint = color)
        else drawableBitmap(d, px, expandLayers = false, tint = color)
    }

    /** Maps brightness onto [dark]→[light]. */
    private fun duotone(src: Bitmap, dark: Int, light: Int): Bitmap {
        val out = createBitmap(src.width, src.height)
        fun ch(c: Int, shift: Int) = (c shr shift and 0xFF).toFloat()
        val dr = ch(light, 16) - ch(dark, 16); val dg = ch(light, 8) - ch(dark, 8); val db = ch(light, 0) - ch(dark, 0)
        val m = ColorMatrix(floatArrayOf(
            0.299f * dr / 255f, 0.587f * dr / 255f, 0.114f * dr / 255f, 0f, ch(dark, 16),
            0.299f * dg / 255f, 0.587f * dg / 255f, 0.114f * dg / 255f, 0f, ch(dark, 8),
            0.299f * db / 255f, 0.587f * db / 255f, 0.114f * db / 255f, 0f, ch(dark, 0),
            0f, 0f, 0f, 1f, 0f,
        ))
        Canvas(out).drawBitmap(src, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(m) })
        src.recycle()
        return out
    }

    /** A line around the glyph: the glyph grown in a ring of copies, minus the glyph itself. */
    private fun outline(glyph: Bitmap, color: Int): Bitmap {
        val px = glyph.width
        val out = createBitmap(px, px)
        val c = Canvas(out)
        val w = max(1.5f, px * 0.04f)
        val shrink = 0.86f
        val mask = glyph.extractAlpha()
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.color = color }
        c.withScale(shrink, shrink, px / 2f, px / 2f) {
            for (k in 0 until 16) {
                val a = 2 * PI * k / 16
                drawBitmap(mask, (cos(a) * w / shrink).toFloat(), (sin(a) * w / shrink).toFloat(), p)
            }
            drawBitmap(mask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        }
        mask.recycle(); glyph.recycle()
        return out
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
        val bounds = Rect()
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
