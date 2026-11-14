package app.custo.feature.overlays

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.datastore.preferences.core.Preferences

/** What an overlay feature needs from the accessibility service hosting it. */
interface OverlayHost {
    val service: AccessibilityService
    val wm: WindowManager
    val handler: Handler
    val actions: ActionPerformer
    val prefs: Preferences?
    val isScreenOn: Boolean
    fun dp(v: Float): Float
    fun accent(): Int
}

/** Base for each feature: [apply] adds/updates/removes its windows from prefs, [remove] tears down. */
abstract class Overlay(protected val host: OverlayHost) {
    protected val ctx get() = host.service
    protected val wm get() = host.wm
    protected fun dp(v: Float) = host.dp(v)
    protected fun dpi(v: Float) = host.dp(v).toInt()

    abstract fun apply(p: Preferences)
    abstract fun remove()

    /** Whether this feature currently wants screen on/off callbacks. */
    open val wantsScreen: Boolean get() = false
    /** Whether this feature currently wants power-connected callbacks. */
    open val wantsPower: Boolean get() = false

    open fun onScreen(on: Boolean) = Unit
    open fun onPowerConnected(level: Int) = Unit
    open fun preview(kind: Preview) = Unit
    /** A different app (or the launcher) came to the front. */
    open fun onForegroundApp(pkg: String) = Unit

    /** Rotation or size change: rebuild from current prefs. */
    open fun onConfigChanged() { host.prefs?.let { remove(); apply(it) } }

    protected fun add(v: View, lp: WindowManager.LayoutParams) = runCatching { wm.addView(v, lp) }.isSuccess
    protected fun drop(v: View?) { v?.let { runCatching { wm.removeViewImmediate(it) } } }
    protected fun update(v: View, lp: WindowManager.LayoutParams) { runCatching { wm.updateViewLayout(v, lp) } }

    protected fun params(w: Int, h: Int, touchable: Boolean = false) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        setFitInsetsTypes(0)
    }

    protected fun screen(): Rect = wm.currentWindowMetrics.bounds

    /** The top camera cutout, in screen coordinates, or null if the phone has none. */
    protected fun topCutout(): Rect? = wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRects
        ?.filter { it.top <= 0 || it.top < screen().height() / 4 }?.minByOrNull { it.top }

    protected fun statusBarHeight(): Int = wm.currentWindowMetrics.windowInsets
        .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top.takeIf { it > 0 } ?: dpi(24f)
}
