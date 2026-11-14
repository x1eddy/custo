package app.custo.feature.overlays

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** One slider column: a stream's level and icon. */
class VolumeSlot(val stream: Int, val icon: Drawable?) { var fraction = 0f }

/**
 * Vertical volume slider(s) drawn in one of four [VolumeSkin]s. Drag to set the level, tap to
 * expand or collapse. Draws [slots] side by side.
 */
@SuppressLint("ViewConstructor")
class VolumePanelView(
    context: Context,
    private val skin: VolumeSkin,
    private val colW: Float,
    private val gap: Float,
    private val onSet: (slot: Int, fraction: Float) -> Unit,
    private val onTap: (slot: Int, fraction: Float) -> Unit,
    private val onTouch: () -> Unit,
) : View(context) {

    var slots: List<VolumeSlot> = emptyList()
        set(v) { field = v; invalidate() }
    var accent = 0xFF6D4AFF.toInt()
    var showPercent = true

    private val d = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val r = RectF()
    private val clip = Path()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    fun changed() = invalidate()

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        slots.forEachIndexed { i, s ->
            val left = i * (colW + gap)
            r.set(left, 0f, left + colW, h)
            when (skin) {
                VolumeSkin.PILL -> drawFilled(canvas, s, colW / 2, 0xEE1C1B1F.toInt(), accent, true)
                VolumeSkin.BIG -> drawFilled(canvas, s, 22 * d, 0xE62C2C2E.toInt(), 0xFFF2F2F2.toInt(), false)
                VolumeSkin.SLIM -> drawSlim(canvas, s)
                VolumeSkin.DOTS -> drawDots(canvas, s)
            }
        }
    }

    private fun drawFilled(canvas: Canvas, s: VolumeSlot, radius: Float, surface: Int, fillColor: Int, numbers: Boolean) {
        val w = r.width(); val h = r.height()
        bg.color = surface
        canvas.drawRoundRect(r, radius, radius, bg)
        clip.reset(); clip.addRoundRect(r, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        fill.color = fillColor
        canvas.drawRect(r.left, r.top + h * (1 - s.fraction), r.right, r.bottom, fill)
        canvas.restore()
        val iconColor = if (numbers) 0xFFDDDDDD.toInt() else if (s.fraction > 0.15f) 0xFF3A3A3C.toInt() else 0xFFDDDDDD.toInt()
        if (numbers) {
            text.textSize = w * 0.3f
            text.color = if (s.fraction > 0.12f) 0xFFFFFFFF.toInt() else 0xFFBBBBBB.toInt()
            canvas.drawText("${(s.fraction * 100).toInt()}", r.centerX(), r.bottom - w * 0.4f, text)
            drawIcon(canvas, s.icon, r.centerX(), r.top + w * 0.5f, w * 0.38f, iconColor)
        } else {
            drawIcon(canvas, s.icon, r.centerX(), r.bottom - w * 0.45f, w * 0.36f, iconColor)
        }
    }

    private fun drawSlim(canvas: Canvas, s: VolumeSlot) {
        val cx = r.centerX(); val track = 4 * d
        val top = r.top + colW * 0.9f; val bottom = r.bottom - colW * 0.5f
        bg.color = 0xCC1C1B1F.toInt()
        canvas.drawRoundRect(r, colW / 2, colW / 2, bg)
        fill.color = 0x55FFFFFF
        canvas.drawRoundRect(cx - track / 2, top, cx + track / 2, bottom, track, track, fill)
        val y = bottom - (bottom - top) * s.fraction
        fill.color = accent
        canvas.drawRoundRect(cx - track / 2, y, cx + track / 2, bottom, track, track, fill)
        canvas.drawCircle(cx, y, track * 2.2f, fill)
        drawIcon(canvas, s.icon, cx, r.top + colW * 0.45f, colW * 0.42f, 0xFFDDDDDD.toInt())
    }

    private fun drawDots(canvas: Canvas, s: VolumeSlot) {
        val n = 15
        val cx = r.centerX()
        val step = r.height() / n
        val radius = minOf(step * 0.3f, colW * 0.3f)
        val lit = (s.fraction * n + 0.01f).toInt()
        for (k in 0 until n) {
            val cy = r.bottom - step * (k + 0.5f)
            fill.color = if (k < lit) accent else 0x88FFFFFF.toInt()
            canvas.drawCircle(cx, cy, if (k < lit) radius * 1.15f else radius, fill)
        }
    }

    private fun drawIcon(canvas: Canvas, icon: Drawable?, cx: Float, cy: Float, size: Float, color: Int) {
        icon ?: return
        icon.setTint(color)
        icon.setBounds((cx - size / 2).toInt(), (cy - size / 2).toInt(), (cx + size / 2).toInt(), (cy + size / 2).toInt())
        icon.draw(canvas)
    }

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var slot = 0

    private fun fractionAt(y: Float) = (1f - y / height).coerceIn(0f, 1f)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; dragging = false
                slot = (e.x / (colW + gap)).toInt().coerceIn(0, (slots.size - 1).coerceAtLeast(0))
                onTouch()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(e.y - downY) > slop || abs(e.x - downX) > slop)) dragging = true
                if (dragging && slot < slots.size) { slots[slot].fraction = fractionAt(e.y); onSet(slot, slots[slot].fraction); invalidate() }
                onTouch()
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) onTap(slot, fractionAt(e.y))
                onTouch()
            }
            MotionEvent.ACTION_OUTSIDE -> Unit
        }
        return true
    }
}
