package app.custo.feature.overlays

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.datastore.preferences.core.Preferences
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** A pulsing ring around the camera (or a dot at top center) while notifications are unread. */
class NotificationLightOverlay(host: OverlayHost) : Overlay(host) {

    private var view: NotifLightView? = null
    private var unread: Map<String, Int> = emptyMap()
    private var previewUntil = 0L
    private val endPreview = Runnable { previewUntil = 0; refresh() }

    override val wantsScreen get() = host.prefs?.get(OverlayKeys.notifLight) == true

    private var builtSize: Float? = null

    override fun apply(p: Preferences) {
        if (p[OverlayKeys.notifLight] != true) { remove(); return }
        if (builtSize != p[OverlayKeys.notifLightSizeDp]) { view?.stop(); drop(view); view = null }
        refresh()
    }

    override fun remove() {
        host.handler.removeCallbacks(endPreview)
        view?.stop(); drop(view); view = null
    }

    override fun onScreen(on: Boolean) = refresh()

    override fun preview(kind: Preview) {
        if (kind != Preview.NOTIF_LIGHT) return
        previewUntil = System.currentTimeMillis() + 6000
        host.handler.removeCallbacks(endPreview)
        host.handler.postDelayed(endPreview, 6000)
        refresh()
    }

    fun setUnread(map: Map<String, Int>) { unread = map; refresh() }

    private fun refresh() {
        val p = host.prefs ?: return
        val previewing = previewUntil > System.currentTimeMillis()
        if (p[OverlayKeys.notifLight] != true && !previewing) { remove(); return }
        val allowed = OverlayLogic.csv(p[OverlayKeys.notifLightApps])
        val colors = unread.filterKeys { allowed.isEmpty() || it in allowed }.values.map { it.takeIf { c -> c != 0 } ?: host.accent() }
        val shown = if (previewing) listOf(host.accent(), 0xFF00E676.toInt()) else colors
        if (shown.isEmpty() || !host.isScreenOn) { view?.stop(); drop(view); view = null; return }
        val v = view ?: create(p) ?: return
        v.colors = shown.toIntArray()
        v.start()
    }

    private fun create(p: Preferences): NotifLightView? {
        builtSize = p[OverlayKeys.notifLightSizeDp]
        val size = dp(builtSize ?: 8f)
        val cut = topCutout()
        val stroke = dp(2.5f)
        // Ring around the camera hole, or a dot near the top center.
        val radius = if (cut != null) max(cut.width(), cut.height()) / 2f + stroke * 1.5f else size / 2
        val pad = dp(10f)
        val box = (2 * (radius + pad)).toInt()
        val cx = cut?.centerX() ?: (screen().width() / 2)
        val cy = cut?.centerY() ?: (statusBarHeight() / 2)
        val v = NotifLightView(ctx, ring = cut != null, radius = radius, stroke = stroke, glow = pad * 0.6f)
        val lp = params(box, box).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cx - box / 2; y = cy - box / 2
        }
        if (!add(v, lp)) return null
        view = v
        return v
    }
}

@android.annotation.SuppressLint("ViewConstructor")
class NotifLightView(context: Context, private val ring: Boolean, private val radius: Float, stroke: Float, glow: Float) : View(context) {
    var colors = intArrayOf(0xFFFFFFFF.toInt())
    private var t = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = if (ring) Paint.Style.STROKE else Paint.Style.FILL
        strokeWidth = stroke
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = paint.style; strokeWidth = stroke * 2.5f
        maskFilter = BlurMaskFilter(glow, BlurMaskFilter.Blur.NORMAL)
    }
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationRepeat(animation: android.animation.Animator) { cycle++ }
        })
    }
    private var cycle = 0

    fun start() { if (!anim.isStarted) anim.start() }
    fun stop() = anim.cancel()

    override fun onDraw(canvas: Canvas) {
        val c = colors[cycle % colors.size]
        val s = sin(PI * t).toFloat()
        val a = (40 + 215 * s * s).toInt()
        halo.color = c; halo.alpha = a / 2
        paint.color = c; paint.alpha = a
        val cx = width / 2f; val cy = height / 2f
        canvas.drawCircle(cx, cy, radius, halo)
        canvas.drawCircle(cx, cy, radius, paint)
    }

    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
}
