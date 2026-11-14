package app.custo.feature.overlays

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.datastore.preferences.core.Preferences

/** Thin invisible touch strips on the screen edges (and optional bottom corners). */
class EdgeGesturesOverlay(host: OverlayHost) : Overlay(host) {

    private val views = mutableListOf<View>()
    private var built: List<Any?>? = null

    override fun apply(p: Preferences) {
        if (p[OverlayKeys.gestures] != true) { remove(); return }
        val key = listOf(
            p[OverlayKeys.gestLeft], p[OverlayKeys.gestRight], p[OverlayKeys.gestCorners], p[OverlayKeys.gestHeight],
            p[OverlayKeys.gestOffsetY], p[OverlayKeys.gestWidthDp], p[OverlayKeys.gestSwipeDp], p[OverlayKeys.gestShow],
        )
        if (key == built) return
        remove()
        built = key
        val screenH = screen().height()
        val width = dpi(p[OverlayKeys.gestWidthDp] ?: 14f).coerceAtLeast(dpi(4f))
        val height = (screenH * (p[OverlayKeys.gestHeight] ?: 0.4f)).toInt()
        val offset = (screenH * (p[OverlayKeys.gestOffsetY] ?: 0f)).toInt()
        val swipePx = dp(p[OverlayKeys.gestSwipeDp] ?: 40f)
        val show = p[OverlayKeys.gestShow] == true
        val tint = (host.accent() and 0x00FFFFFF) or 0x66000000

        fun strip(zone: Zone, w: Int, h: Int, gravity: Int, y: Int) {
            val v = GestureStripView(ctx, zone, swipePx, if (show) tint else 0) { z, g -> onGesture(z, g) }
            if (add(v, params(w, h, touchable = true).apply { this.gravity = gravity; this.y = y })) views += v
        }
        if (p[OverlayKeys.gestLeft] != false) strip(Zone.LEFT, width, height, Gravity.START or Gravity.CENTER_VERTICAL, offset)
        if (p[OverlayKeys.gestRight] != false) strip(Zone.RIGHT, width, height, Gravity.END or Gravity.CENTER_VERTICAL, offset)
        if (p[OverlayKeys.gestCorners] == true) {
            val c = dpi(44f)
            strip(Zone.CORNER_LEFT, c, c, Gravity.START or Gravity.BOTTOM, 0)
            strip(Zone.CORNER_RIGHT, c, c, Gravity.END or Gravity.BOTTOM, 0)
        }
    }

    private fun onGesture(zone: Zone, g: EdgeGesture) {
        val p = host.prefs ?: return
        val key = when (zone) {
            Zone.LEFT -> if (g == EdgeGesture.TAP) OverlayKeys.gestLeftTap else OverlayKeys.gestLeftSwipe
            Zone.RIGHT -> if (g == EdgeGesture.TAP) OverlayKeys.gestRightTap else OverlayKeys.gestRightSwipe
            Zone.CORNER_LEFT -> OverlayKeys.gestCornerLeft
            Zone.CORNER_RIGHT -> OverlayKeys.gestCornerRight
        }
        val default = when (key) {
            OverlayKeys.gestLeftSwipe, OverlayKeys.gestRightSwipe -> OverlayAction.BACK
            else -> OverlayAction.NONE
        }
        val action = p[key]?.let { OverlayAction.of(it) } ?: default
        host.actions.perform(action)
    }

    override fun remove() {
        built = null
        views.forEach { drop(it) }
        views.clear()
    }

    enum class Zone(val inward: Int, val corner: Boolean) { LEFT(1, false), RIGHT(-1, false), CORNER_LEFT(1, true), CORNER_RIGHT(-1, true) }
}

@SuppressLint("ViewConstructor")
class GestureStripView(
    context: Context,
    private val zone: EdgeGesturesOverlay.Zone,
    private val swipePx: Float,
    private val tint: Int,
    private val onGesture: (EdgeGesturesOverlay.Zone, EdgeGesture) -> Unit,
) : View(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var fired = false

    override fun onDraw(canvas: Canvas) { if (tint != 0) canvas.drawColor(tint) }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; downAt = e.eventTime; fired = false }
            MotionEvent.ACTION_MOVE -> if (!fired) {
                // Fire a swipe as soon as it crosses the threshold, like system back.
                val g = classify(e)
                if (g == EdgeGesture.SWIPE) { fired = true; fire(g) }
            }
            MotionEvent.ACTION_UP -> if (!fired) {
                val g = classify(e)
                if (g != EdgeGesture.NONE) fire(g)
            }
        }
        return true
    }

    private fun classify(e: MotionEvent): EdgeGesture {
        val dx = e.rawX - downX; val dy = e.rawY - downY; val dt = e.eventTime - downAt
        return if (zone.corner) EdgeGestureClassifier.classifyCorner(dx, dy, dt, zone.inward, swipePx, slop)
        else EdgeGestureClassifier.classify(dx, dy, dt, zone.inward, swipePx, slop)
    }

    private fun fire(g: EdgeGesture) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onGesture(zone, g)
    }
}
