package app.custo.feature.overlays

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import androidx.datastore.preferences.core.Preferences

/** Download/upload speed in the status bar. Samples every 1.5 s, only while the screen is on. */
class NetSpeedOverlay(host: OverlayHost) : Overlay(host) {

    private var view: NetSpeedView? = null
    private var built: List<Any?>? = null
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L
    private var hideIdle = true

    private val sample = object : Runnable {
        override fun run() {
            val v = view ?: return
            val rx = TrafficStats.getTotalRxBytes(); val tx = TrafficStats.getTotalTxBytes()
            val now = SystemClock.elapsedRealtime()
            if (lastRx >= 0 && now > lastAt && rx != TrafficStats.UNSUPPORTED.toLong()) {
                val secs = (now - lastAt) / 1000.0
                val down = ((rx - lastRx) / secs).toLong().coerceAtLeast(0)
                val up = ((tx - lastTx) / secs).toLong().coerceAtLeast(0)
                val idle = down < 1024 && up < 1024
                v.setText(if (idle && hideIdle) "" else "↓ ${OverlayLogic.formatRate(down)}  ↑ ${OverlayLogic.formatRate(up)}")
            }
            lastRx = rx; lastTx = tx; lastAt = now
            host.handler.postDelayed(this, 1500)
        }
    }

    override val wantsScreen get() = host.prefs?.get(OverlayKeys.netSpeed) == true

    override fun apply(p: Preferences) {
        if (p[OverlayKeys.netSpeed] != true) { remove(); return }
        hideIdle = p[OverlayKeys.netHideIdle] != false
        val key = listOf(p[OverlayKeys.netPosition], p[OverlayKeys.netNudgeX], p[OverlayKeys.netNudgeY], p[OverlayKeys.netTextSp], p[OverlayKeys.netColor])
        if (key != built || view == null) { stop(); drop(view); view = null; built = key; build(p) }
        if (host.isScreenOn) start() else stop()
    }

    private fun build(p: Preferences) {
        val pos = enumOr(p[OverlayKeys.netPosition], NetPosition.CENTER)
        val sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, p[OverlayKeys.netTextSp] ?: 10f, ctx.resources.displayMetrics)
        val v = NetSpeedView(ctx, sizePx, (p[OverlayKeys.netColor] ?: 0).takeIf { it != 0 } ?: 0xFFFFFFFF.toInt(), pos)
        val w = v.maxWidth()
        val h = maxOf(statusBarHeight(), (sizePx * 1.6f).toInt())
        val nudgeX = dpi((p[OverlayKeys.netNudgeX] ?: 0).toFloat())
        val lp = params(w, h).apply {
            gravity = Gravity.TOP or when (pos) {
                NetPosition.LEFT -> Gravity.START
                NetPosition.CENTER -> Gravity.CENTER_HORIZONTAL
                NetPosition.RIGHT -> Gravity.END
            }
            // Gravity END measures x from the right edge, so nudging right means a smaller x.
            x = when (pos) { NetPosition.RIGHT -> -nudgeX; else -> nudgeX } + if (pos == NetPosition.CENTER) 0 else dpi(12f)
            y = dpi((p[OverlayKeys.netNudgeY] ?: 0).toFloat())
        }
        if (add(v, lp)) view = v
    }

    private fun start() {
        host.handler.removeCallbacks(sample)
        lastRx = -1
        host.handler.post(sample)
    }

    private fun stop() = host.handler.removeCallbacks(sample)

    override fun onScreen(on: Boolean) {
        if (view == null) return
        if (on) start() else { stop(); view?.setText("") }
    }

    override fun remove() { stop(); built = null; drop(view); view = null }
}

@android.annotation.SuppressLint("ViewConstructor")
class NetSpeedView(context: Context, sizePx: Float, color: Int, private val pos: NetPosition) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx; this.color = color; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(sizePx / 4, 0f, 0f, 0x88000000.toInt())
        textAlign = when (pos) { NetPosition.LEFT -> Paint.Align.LEFT; NetPosition.CENTER -> Paint.Align.CENTER; NetPosition.RIGHT -> Paint.Align.RIGHT }
    }
    private var text = ""

    fun maxWidth() = (paint.measureText("↓ 999 K  ↑ 999 K") + paint.textSize).toInt()

    fun setText(t: String) { if (t != text) { text = t; invalidate() } }

    override fun onDraw(canvas: Canvas) {
        if (text.isEmpty()) return
        val x = when (pos) { NetPosition.LEFT -> 0f; NetPosition.CENTER -> width / 2f; NetPosition.RIGHT -> width.toFloat() }
        val y = height / 2f - (paint.ascent() + paint.descent()) / 2
        canvas.drawText(text, x, y, paint)
    }
}
