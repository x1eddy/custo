package app.custo.feature.overlays

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** A tall pill slider. Drag to set volume; draws its own fill and level label. */
class VolumePanelView(context: Context, private val onSet: (Float) -> Unit, private val onTouch: () -> Unit) : View(context) {

    var fraction = 0.5f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var accent = 0xFF6D4AFF.toInt()
    var surface = 0xEE1C1B1F.toInt()
    var label = ""

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val r = RectF()
    private val clip = android.graphics.Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val radius = w / 2
        r.set(0f, 0f, w, h)
        bg.color = surface
        canvas.drawRoundRect(r, radius, radius, bg)
        clip.reset(); clip.addRoundRect(r, radius, radius, android.graphics.Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        fill.color = accent
        canvas.drawRect(0f, h * (1 - fraction), w, h, fill)
        canvas.restore()
        text.textSize = w * 0.3f
        text.color = if (fraction > 0.12f) 0xFFFFFFFF.toInt() else 0xFFBBBBBB.toInt()
        canvas.drawText("${(fraction * 100).toInt()}", w / 2, h - w * 0.4f, text)
        text.textSize = w * 0.2f
        text.color = 0xFFDDDDDD.toInt()
        canvas.drawText(label, w / 2, w * 0.55f, text)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                fraction = 1f - e.y / height
                onSet(fraction)
                onTouch()
            }
        }
        return true
    }
}
