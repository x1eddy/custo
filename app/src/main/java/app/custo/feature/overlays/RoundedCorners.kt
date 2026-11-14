package app.custo.feature.overlays

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import androidx.datastore.preferences.core.Preferences

/** Four small windows that paint quarter masks in the screen corners. */
class RoundedCornersOverlay(host: OverlayHost) : Overlay(host) {

    private val views = arrayOfNulls<CornerView>(4)
    private var built: List<Any?>? = null

    override fun apply(p: Preferences) {
        if (p[OverlayKeys.corners] != true) { remove(); return }
        val radius = dpi(p[OverlayKeys.cornersRadiusDp] ?: 16f).coerceAtLeast(1)
        val color = p[OverlayKeys.cornersColor] ?: 0xFF000000.toInt()
        val top = p[OverlayKeys.cornersTop] != false
        val bottom = p[OverlayKeys.cornersBottom] != false
        val key = listOf(radius, color, top, bottom)
        if (key == built) return
        remove()
        built = key
        val gravities = intArrayOf(
            Gravity.TOP or Gravity.START, Gravity.TOP or Gravity.END,
            Gravity.BOTTOM or Gravity.START, Gravity.BOTTOM or Gravity.END,
        )
        for (i in 0 until 4) {
            if (i < 2 && !top || i >= 2 && !bottom) continue
            val v = CornerView(ctx, i, color)
            if (add(v, params(radius, radius).apply { gravity = gravities[i] })) views[i] = v
        }
    }

    override fun remove() {
        built = null
        for (i in 0 until 4) { drop(views[i]); views[i] = null }
    }
}

/** corner: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
@SuppressLint("ViewConstructor")
class CornerView(context: Context, private val corner: Int, color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val path = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val r = w.toFloat()
        path.reset()
        path.addRect(0f, 0f, r, r, Path.Direction.CW)
        val circle = Path().apply { addCircle(r, r, r, Path.Direction.CW) }
        path.op(circle, Path.Op.DIFFERENCE)
    }

    override fun onDraw(canvas: Canvas) {
        val c = width / 2f
        canvas.save()
        when (corner) {
            1 -> canvas.scale(-1f, 1f, c, c)
            2 -> canvas.scale(1f, -1f, c, c)
            3 -> canvas.scale(-1f, -1f, c, c)
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
