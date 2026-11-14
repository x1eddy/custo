package app.custo.feature.overlays

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.RoundedCorner
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/** Animated glow that travels around the screen's rounded edges. */
class EdgeLightView(context: Context) : View(context) {

    var strokePx = 8f
        set(v) { if (field != v) { field = v; blur = null } }
    private var blur: BlurMaskFilter? = null
    var onFinished: (() -> Unit)? = null

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val matrix = Matrix()
    private var colors = intArrayOf(Color.WHITE)
    private var progress = 0f
    private var animator: ValueAnimator? = null

    fun play(colors: IntArray, durationMs: Long) {
        this.colors = if (colors.size == 1) intArrayOf(colors[0], Color.TRANSPARENT, colors[0], Color.TRANSPARENT, colors[0]) else colors + colors[0]
        buildShader()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { onFinished?.invoke() }
            })
            start()
        }
    }

    private fun cornerRadius(): Float {
        val d = display ?: return 0f
        return d.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f
    }

    private fun buildShader() {
        if (width == 0) { line.shader = null; return }
        val g = SweepGradient(width / 2f, height / 2f, colors, null)
        line.shader = g; glow.shader = g
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = buildShader()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || line.shader == null) return
        matrix.setRotate(progress * 720f, w / 2, h / 2)
        (line.shader as Shader).setLocalMatrix(matrix)
        // Fade in, hold, fade out.
        val envelope = sin(PI * progress).toFloat().coerceIn(0f, 1f)
        val alpha = (255 * (0.25f + 0.75f * envelope)).toInt().coerceIn(0, 255) * (if (progress >= 1f) 0 else 1)

        val inset = strokePx / 2
        rect.set(inset, inset, w - inset, h - inset)
        val r = (cornerRadius() - inset).coerceAtLeast(0f)

        glow.strokeWidth = strokePx * 3
        if (blur == null) blur = BlurMaskFilter(strokePx * 2, BlurMaskFilter.Blur.NORMAL)
        glow.maskFilter = blur
        glow.alpha = alpha / 2
        canvas.drawRoundRect(rect, r, r, glow)

        line.strokeWidth = strokePx
        line.alpha = alpha
        canvas.drawRoundRect(rect, r, r, line)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
