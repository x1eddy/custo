package app.custo.feature.overlays

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.BatteryManager
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import app.custo.R
import kotlin.math.min

/** A short animation when the charger is plugged in. The window only exists while it plays. */
class ChargingAnimationOverlay(host: OverlayHost) : Overlay(host) {

    private var view: ChargingView? = null

    override val wantsPower get() = host.prefs?.get(OverlayKeys.chargeAnim) == true

    override fun apply(p: Preferences) { if (p[OverlayKeys.chargeAnim] != true) remove() }
    override fun remove() { drop(view); view = null }
    override fun onConfigChanged() = remove()

    override fun onPowerConnected(level: Int) {
        if (host.prefs?.get(OverlayKeys.chargeAnim) == true && host.isScreenOn) play(level)
    }

    override fun preview(kind: Preview) { if (kind == Preview.CHARGING) play(currentLevel(ctx)) }

    private fun play(level: Int) {
        val style = enumOr(host.prefs?.get(OverlayKeys.chargeAnimStyle), ChargeStyle.RING)
        remove()
        val v = ChargingView(ctx, style, level, host.accent())
        v.onFinished = { if (view === v) remove() }
        if (!add(v, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT))) return
        view = v
        v.start()
    }

    companion object {
        fun currentLevel(c: Context): Int {
            val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
            return i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        }
    }
}

@android.annotation.SuppressLint("ViewConstructor")
class ChargingView(context: Context, private val style: ChargeStyle, private val level: Int, private val accent: Int) : View(context) {
    var onFinished: (() -> Unit)? = null
    private val d = resources.displayMetrics.density
    private var t = 0f
    private val green = 0xFF00E676.toInt()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; maskFilter = BlurMaskFilter(10 * d, BlurMaskFilter.Blur.NORMAL) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val rect = RectF()
    private val bolt = ContextCompat.getDrawable(context, R.drawable.ic_bolt)?.mutate()
    private var edgeShader: Shader? = null
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
        addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { onFinished?.invoke() } })
    }

    fun start() = anim.start()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        edgeShader = LinearGradient(0f, h.toFloat(), 0f, 0f, intArrayOf(green, accent), null, Shader.TileMode.CLAMP)
    }

    /** 0..1 fade: in over the first 12 %, out over the last 20 %. */
    private fun fade() = min((t / 0.12f).coerceAtMost(1f), ((1f - t) / 0.2f).coerceAtMost(1f)).coerceIn(0f, 1f)
    private fun fillProgress() = (t / 0.55f).coerceAtMost(1f).let { 1 - (1 - it) * (1 - it) * (1 - it) }

    override fun onDraw(canvas: Canvas) {
        val a = (255 * fade()).toInt()
        val cx = width / 2f; val cy = height / 2f
        when (style) {
            ChargeStyle.RING -> {
                val r = min(width, height) * 0.22f
                fill.color = 0xCC000000.toInt(); fill.alpha = (0xCC * fade()).toInt()
                canvas.drawCircle(cx, cy, r * 1.35f, fill)
                rect.set(cx - r, cy - r, cx + r, cy + r)
                stroke.strokeWidth = 10 * d
                stroke.color = 0x33FFFFFF; stroke.alpha = a / 5
                canvas.drawOval(rect, stroke)
                val sweep = 360f * level / 100f * fillProgress()
                glow.color = green; glow.alpha = a / 2; glow.strokeWidth = 14 * d
                canvas.drawArc(rect, -90f, sweep, false, glow)
                stroke.color = green; stroke.alpha = a
                canvas.drawArc(rect, -90f, sweep, false, stroke)
                text.textSize = r * 0.42f; text.alpha = a
                canvas.drawText("${(level * fillProgress()).toInt()}%", cx, cy + text.textSize * 0.55f, text)
                drawBolt(canvas, cx, cy - r * 0.42f, r * 0.32f, a)
            }
            ChargeStyle.EDGES -> {
                val top = height * (1f - level / 100f * fillProgress())
                stroke.shader = edgeShader; glow.shader = edgeShader
                stroke.strokeWidth = 5 * d; glow.strokeWidth = 12 * d
                stroke.alpha = a; glow.alpha = a / 2
                val x1 = 2.5f * d; val x2 = width - 2.5f * d
                canvas.drawLine(x1, height.toFloat(), x1, top, glow)
                canvas.drawLine(x2, height.toFloat(), x2, top, glow)
                canvas.drawLine(x1, height.toFloat(), x1, top, stroke)
                canvas.drawLine(x2, height.toFloat(), x2, top, stroke)
                stroke.shader = null; glow.shader = null
                text.textSize = 15 * d; text.alpha = a
                canvas.drawText("$level%", cx, height - 64 * d, text)
            }
            ChargeStyle.PULSE -> {
                val base = min(width, height) * 0.1f
                for (i in 0 until 3) {
                    val p = ((t * 2.2f) - i * 0.25f).let { it - it.toInt() }.coerceIn(0f, 1f)
                    stroke.color = green; stroke.strokeWidth = 3 * d
                    stroke.alpha = (a * (1 - p) * 0.8f).toInt()
                    canvas.drawCircle(cx, cy, base * (1 + p * 2.6f), stroke)
                }
                fill.color = 0xE6000000.toInt(); fill.alpha = (0xE6 * fade()).toInt()
                canvas.drawCircle(cx, cy, base, fill)
                drawBolt(canvas, cx, cy - base * 0.15f, base * 0.9f, a)
                text.textSize = base * 0.38f; text.alpha = a
                canvas.drawText("$level%", cx, cy + base * 0.62f, text)
            }
        }
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, size: Float, a: Int) {
        val b = bolt ?: return
        b.setTint(green); b.alpha = a
        b.setBounds((cx - size / 2).toInt(), (cy - size / 2).toInt(), (cx + size / 2).toInt(), (cy + size / 2).toInt())
        b.draw(canvas)
    }

    override fun onDetachedFromWindow() {
        anim.removeAllListeners(); anim.cancel()
        super.onDetachedFromWindow()
    }
}
