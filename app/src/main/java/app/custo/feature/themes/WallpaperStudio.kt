package app.custo.feature.themes

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class WallStyle(val label: String) { MESH("Mesh"), GRADIENT("Gradient"), WAVES("Waves"), DOTS("Dots"), SOLID("Solid") }

@Serializable
data class WallSpec(
    val style: WallStyle = WallStyle.MESH,
    val colors: List<Int> = listOf(0xFF2B1A6B.toInt(), 0xFF6D4AFF.toInt(), 0xFFFF6FB5.toInt(), 0xFF00C2D1.toInt()),
    val seed: Int = 7,
    val angle: Float = 135f,
)

enum class WallTarget(val label: String, val flags: Int) {
    HOME("Home", WallpaperManager.FLAG_SYSTEM),
    LOCK("Lock", WallpaperManager.FLAG_LOCK),
    BOTH("Both", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
}

/** Procedurally renders wallpapers at full screen resolution. Nothing is downloaded. */
class WallpaperStudio(private val context: Context) {

    fun screenSize(): Pair<Int, Int> {
        val b = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return min(b.width(), b.height()) to max(b.width(), b.height())
    }

    suspend fun apply(spec: WallSpec, target: WallTarget) = withContext(Dispatchers.Default) {
        val (w, h) = screenSize()
        val bmp = render(spec, w, h)
        withContext(Dispatchers.IO) { WallpaperManager.getInstance(context).setBitmap(bmp, null, true, target.flags) }
        bmp.recycle()
    }

    companion object {
        fun render(spec: WallSpec, w: Int, h: Int): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            draw(Canvas(bmp), spec, w.toFloat(), h.toFloat())
            return bmp
        }

        fun draw(canvas: Canvas, spec: WallSpec, w: Float, h: Float) {
            val colors = spec.colors.ifEmpty { listOf(0xFF000000.toInt()) }
            val rnd = Random(spec.seed)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            when (spec.style) {
                WallStyle.SOLID -> canvas.drawColor(colors[0])

                WallStyle.GRADIENT -> {
                    val rad = spec.angle * PI.toFloat() / 180f
                    val cx = w / 2; val cy = h / 2; val r = max(w, h) * 0.6f
                    p.shader = LinearGradient(
                        cx - cos(rad) * r, cy - sin(rad) * r, cx + cos(rad) * r, cy + sin(rad) * r,
                        colors.toIntArray(), null, Shader.TileMode.CLAMP,
                    )
                    canvas.drawRect(0f, 0f, w, h, p)
                }

                WallStyle.MESH -> {
                    canvas.drawColor(colors[0])
                    val blobs = colors.drop(1).ifEmpty { colors }
                    repeat(blobs.size * 2) { i ->
                        val c = blobs[i % blobs.size]
                        val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
                        val r = max(w, h) * (0.35f + rnd.nextFloat() * 0.35f)
                        p.shader = RadialGradient(x, y, r, intArrayOf(c, c and 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        canvas.drawRect(0f, 0f, w, h, p)
                    }
                }

                WallStyle.WAVES -> {
                    p.shader = LinearGradient(0f, 0f, 0f, h, colors[0], colors.last(), Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h, p)
                    p.shader = null
                    val layers = 5
                    for (i in 0 until layers) {
                        val base = h * (0.45f + i * 0.11f)
                        val amp = h * (0.03f + rnd.nextFloat() * 0.04f)
                        val freq = 1.2f + rnd.nextFloat() * 1.5f
                        val phase = rnd.nextFloat() * 2f * PI.toFloat()
                        val path = Path().apply {
                            moveTo(0f, h)
                            var x = 0f
                            while (x <= w) {
                                lineTo(x, base + amp * sin(freq * 2f * PI.toFloat() * x / w + phase))
                                x += w / 60f
                            }
                            lineTo(w, h); close()
                        }
                        val c = colors[(i + 1) % colors.size]
                        p.color = (c and 0x00FFFFFF) or (((0x55 + i * 0x22).coerceAtMost(0xFF)) shl 24)
                        canvas.drawPath(path, p)
                    }
                }

                WallStyle.DOTS -> {
                    canvas.drawColor(colors[0])
                    val accents = colors.drop(1).ifEmpty { listOf(0xFFFFFFFF.toInt()) }
                    val step = w / 14f
                    var y = step / 2
                    var row = 0
                    while (y < h + step) {
                        var x = if (row % 2 == 0) step / 2 else step
                        while (x < w + step) {
                            val t = (y / h)
                            p.color = accents[((x / step).toInt() + row) % accents.size]
                            p.alpha = (90 + 140 * t).toInt().coerceAtMost(255)
                            canvas.drawCircle(x, y, step * (0.12f + 0.22f * t), p)
                            x += step
                        }
                        y += step * 0.87f; row++
                    }
                }
            }
        }

        /** Random but pleasant palette: analogous hues around a random base, one dark anchor. */
        fun randomPalette(rnd: Random = Random.Default): List<Int> {
            val base = rnd.nextFloat() * 360f
            val spread = 25f + rnd.nextFloat() * 50f
            fun hsl(h: Float, s: Float, l: Float) = app.custo.feature.systemui.Tonal.fromHsl(((h % 360f) + 360f) % 360f, s, l)
            return listOf(
                hsl(base, 0.55f, 0.14f),
                hsl(base + spread, 0.85f, 0.55f),
                hsl(base + spread * 2, 0.9f, 0.65f),
                hsl(base - spread, 0.8f, 0.5f),
            )
        }
    }
}
