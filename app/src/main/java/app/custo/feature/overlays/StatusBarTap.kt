package app.custo.feature.overlays

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import androidx.datastore.preferences.core.Preferences

/**
 * A narrow touch strip over the middle of the status bar. Double-tap it to lock the screen.
 * One window can't both catch taps and pass swipes through, so the strip is kept narrow.
 */
class StatusBarTapOverlay(host: OverlayHost) : Overlay(host) {

    private var view: View? = null
    private var builtWidth = -1

    override fun apply(p: Preferences) {
        if (p[OverlayKeys.statusTap] != true) { remove(); return }
        val w = dpi(p[OverlayKeys.statusTapWidthDp] ?: 120f)
        if (view != null && w == builtWidth) return
        remove()
        builtWidth = w
        val v = TapStrip(ctx) { host.actions.perform(OverlayAction.LOCK) }
        // Leave the camera cutout area alone vertically: the strip is the status bar height.
        if (add(v, params(w, statusBarHeight(), touchable = true).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL })) view = v
    }

    override fun remove() { builtWidth = -1; drop(view); view = null }
}

@SuppressLint("ViewConstructor")
private class TapStrip(context: Context, private val onDouble: () -> Unit) : View(context) {
    private val detector = DoubleTapDetector(slopPx = 24 * resources.displayMetrics.density)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> detector.onDown(e.eventTime, e.x, e.y)
            MotionEvent.ACTION_UP -> if (detector.onUp(e.eventTime, e.x, e.y)) onDouble()
        }
        return true
    }
}
