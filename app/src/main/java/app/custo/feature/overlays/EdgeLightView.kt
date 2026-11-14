package app.custo.feature.overlays

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.RoundedCorner
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/** Animated light around the screen's rounded edges, in one of four [EdgeStyle]s. */
class EdgeLightView(context: Context) : View(context) {

    var strokePx = 8f
        set(v) { if (field != v) { field = v; blur = null; headBlur = null } }
    var onFinished: (() -> Unit)? = null

    private var style = EdgeStyle.SWEEP
    private var repeat = 1
    private var blur: BlurMaskFilter? = null
    private var headBlur: BlurMaskFilter? = null
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val seg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val matrix = Matrix()
    private val border = Path()
    private val measure = PathMeasure()
    private val segPath = Path()
    private val pos = FloatArray(2)
    private var borderLen = 0f
    private var colors = intArrayOf(Color.WHITE)
    private var sweep: SweepGradient? = null
    private var progress = 0f
    private var animator: ValueAnimator? = null

    fun play(colors: IntArray, style: EdgeStyle, durationMs: Long, repeat: Int) {
        this.colors = colors
        this.style = style
        this.repeat = repeat.coerceIn(1, 10)
        buildGeometry()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs * this@EdgeLightView.repeat
            interpolator = LinearInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { onFinished?.invoke() }
            })
            start()
        }
    }

    private fun cornerRadius(): Float {
        val d = display ?: return 0f
        return d.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f
    }

    private fun buildGeometry() {
        if (width == 0) return
        val sweepColors = when {
            style == EdgeStyle.SWEEP && colors.size == 1 -> intArrayOf(colors[0], Color.TRANSPARENT, colors[0], Color.TRANSPARENT, colors[0])
            colors.size == 1 -> intArrayOf(colors[0], colors[0])
            else -> colors + colors[0]
        }
        sweep = SweepGradient(width / 2f, height / 2f, sweepColors, null)
        line.shader = sweep; glow.shader = sweep
        val inset = strokePx / 2
        rect.set(inset, inset, width - inset, height - inset)
        val r = (cornerRadius() - inset).coerceAtLeast(0f)
        border.reset()
        border.addRoundRect(rect, r, r, Path.Direction.CW)
        measure.setPath(border, true)
        borderLen = measure.length
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = buildGeometry()

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || sweep == null) return
        if (blur == null) blur = BlurMaskFilter(strokePx * 2, BlurMaskFilter.Blur.NORMAL)
        val cycle = (progress * repeat) % 1f
        when (style) {
            EdgeStyle.SWEEP -> {
                matrix.setRotate(progress * 720f * repeat, width / 2f, height / 2f)
                sweep!!.setLocalMatrix(matrix)
                drawBorder(canvas, envelope(progress) * (0.25f + 0.75f * sin(PI * cycle).toFloat().coerceAtLeast(0f)))
            }
            EdgeStyle.PULSE -> {
                // Two quick beats per cycle.
                val beat = (cycle * 2) % 1f
                drawBorder(canvas, sin(PI * beat).toFloat().let { it * it })
            }
            EdgeStyle.BREATHING -> drawBorder(canvas, sin(PI * cycle).toFloat().coerceAtLeast(0f))
            EdgeStyle.COMET -> drawComet(canvas, cycle)
        }
    }

    private fun envelope(p: Float) = if (p >= 1f) 0f else 1f

    private fun drawBorder(canvas: Canvas, strength: Float) {
        val alpha = (255 * strength).toInt().coerceIn(0, 255)
        if (alpha == 0) return
        val inset = strokePx / 2
        val r = (cornerRadius() - inset).coerceAtLeast(0f)
        glow.strokeWidth = strokePx * 3
        glow.maskFilter = blur
        glow.alpha = alpha / 2
        canvas.drawRoundRect(rect, r, r, glow)
        line.strokeWidth = strokePx
        line.alpha = alpha
        canvas.drawRoundRect(rect, r, r, line)
    }

    /** A bright head with a fading tail running once around the screen per cycle. */
    private fun drawComet(canvas: Canvas, cycle: Float) {
        if (borderLen == 0f) return
        val headAt = cycle * borderLen
        val tail = borderLen * 0.28f
        val n = SEGMENTS
        val step = tail / n
        for (i in 0 until n) {
            val end = headAt - i * step
            val start = end - step - 1f
            val f = 1f - i / n.toFloat()
            seg.color = colorAt(i / n.toFloat())
            seg.alpha = (255 * f * f).toInt()
            seg.strokeWidth = strokePx * (0.35f + 0.65f * f)
            segPath.reset()
            appendSegment(start, end)
            canvas.drawPath(segPath, seg)
        }
        if (headBlur == null) headBlur = BlurMaskFilter(strokePx * 3, BlurMaskFilter.Blur.NORMAL)
        measure.getPosTan(headAt, pos, null)
        head.color = colorAt(0f)
        head.maskFilter = headBlur
        canvas.drawCircle(pos[0], pos[1], strokePx * 2.2f, head)
        head.maskFilter = null
        head.color = Color.WHITE
        canvas.drawCircle(pos[0], pos[1], strokePx * 0.7f, head)
    }

    private fun appendSegment(s: Float, e: Float) {
        var start = s; var end = e
        if (end < 0) { start += borderLen; end += borderLen }
        if (start < 0) {
            measure.getSegment(start + borderLen, borderLen, segPath, true)
            measure.getSegment(0f, end, segPath, true)
        } else {
            measure.getSegment(start, end, segPath, true)
        }
    }

    private fun colorAt(f: Float): Int {
        if (colors.size == 1) return colors[0]
        val x = f * (colors.size - 1)
        val i = x.toInt().coerceAtMost(colors.size - 2)
        return OverlayLogic.lerpColor(colors[i], colors[i + 1], x - i)
    }

    override fun onDetachedFromWindow() {
        animator?.removeAllListeners()
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object { const val SEGMENTS = 24 }
}
