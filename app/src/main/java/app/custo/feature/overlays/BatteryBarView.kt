package app.custo.feature.overlays

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

/** Thin battery indicator along the top edge, or a ring around a punch-hole camera. */
class BatteryBarView(context: Context) : View(context) {

    var level = 100
        set(v) { field = v; invalidate() }
    var charging = false
        set(v) { field = v; if (v) shimmer.start() else shimmer.cancel(); invalidate() }
    var style = BatteryBarStyle.TOP
        set(v) { field = v; invalidate() }
    var thicknessPx = 6f
    var accent = 0
    /** Cutout bounds in this view's coordinates (for [BatteryBarStyle.CUTOUT]). */
    var cutout: RectF? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val ring = RectF()
    private var phase = 0f
    private val shimmer = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    private fun levelColor(): Int = when {
        charging -> 0xFF00E676.toInt()
        level <= 15 -> 0xFFFF1744.toInt()
        level <= 35 -> 0xFFFFC400.toInt()
        accent != 0 -> accent
        else -> 0xFF00E5FF.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val color = levelColor()
        paint.color = color
        track.color = (color and 0x00FFFFFF) or 0x33000000
        val frac = level / 100f
        val c = cutout
        if (style == BatteryBarStyle.CUTOUT && c != null) {
            ring.set(c); ring.inset(-thicknessPx * 1.5f, -thicknessPx * 1.5f)
            paint.style = Paint.Style.STROKE; track.style = Paint.Style.STROKE
            paint.strokeWidth = thicknessPx; track.strokeWidth = thicknessPx
            canvas.drawOval(ring, track)
            canvas.drawArc(ring, -90f + if (charging) phase * 360f else 0f, 360f * frac, false, paint)
        } else {
            // Grows from the center outward, so it stays balanced around a centered camera.
            val w = width.toFloat(); val half = w * frac / 2
            paint.style = Paint.Style.FILL
            canvas.drawRect(w / 2 - half, 0f, w / 2 + half, thicknessPx, paint)
            if (charging) {
                paint.color = 0x88FFFFFF.toInt()
                val x = w / 2 - half + (2 * half) * phase
                canvas.drawRect(x - 24f, 0f, x + 24f, thicknessPx, paint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        shimmer.cancel()
        super.onDetachedFromWindow()
    }
}
