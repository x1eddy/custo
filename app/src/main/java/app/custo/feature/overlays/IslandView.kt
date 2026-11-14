package app.custo.feature.overlays

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import app.custo.R

/** What the island shows. */
sealed interface IslandContent {
    data class Media(val m: IslandMedia) : IslandContent
    data class Charging(val level: Int) : IslandContent
    data class Notif(val n: IslandNotif, val icon: Drawable?) : IslandContent
    data class Timer(val t: IslandTimer) : IslandContent
}

/**
 * The black pill. Animates between sizes with a spring; draws compact or expanded content.
 * The pill is centered horizontally and pinned to the top of the view.
 */
@SuppressLint("ViewConstructor")
class IslandView(context: Context, private val onTap: (button: Boolean) -> Unit) : View(context) {

    private val d = resources.displayMetrics.density
    var content: IslandContent? = null
        set(v) { field = v; prepare(); invalidate() }
    var expanded = false
        private set

    private var fromW = 0f; private var fromH = 0f
    private var toW = 0f; private var toH = 0f
    private var f = 1f
    private var anim: ValueAnimator? = null

    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); typeface = Typeface.DEFAULT_BOLD; textSize = 14 * d }
    private val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFAAAAAA.toInt(); textSize = 12.5f * d }
    private val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 22 * d; textAlign = Paint.Align.RIGHT }
    private val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 12 * d; textAlign = Paint.Align.RIGHT }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val tmp = RectF()
    private val m = Matrix()
    private var artShaderFor: Bitmap? = null
    private var line1: CharSequence = ""
    private var line2: CharSequence = ""
    private var timeText = ""

    private val bolt = ContextCompat.getDrawable(context, R.drawable.ic_bolt)?.mutate()
    private val play = ContextCompat.getDrawable(context, R.drawable.ic_play)?.mutate()
    private val pause = ContextCompat.getDrawable(context, R.drawable.ic_pause)?.mutate()
    private val music = ContextCompat.getDrawable(context, R.drawable.ic_music)?.mutate()

    var expandedWidth = 0f

    fun pillWidth() = fromW + (toW - fromW) * f
    fun pillHeight() = fromH + (toH - fromH) * f

    /** Springs the pill to a new size. */
    fun animateTo(w: Float, h: Float, expanded: Boolean, onEnd: (() -> Unit)? = null) {
        val cw = pillWidth(); val ch = pillHeight()
        anim?.removeAllListeners(); anim?.cancel()
        fromW = cw; fromH = ch; toW = w; toH = h; f = 0f
        this.expanded = expanded
        prepare()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 560
            interpolator = android.animation.TimeInterpolator { OverlayLogic.spring(it) }
            addUpdateListener { f = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { f = 1f; onEnd?.invoke() }
            })
            start()
        }
    }

    fun snapTo(w: Float, h: Float, expanded: Boolean) {
        anim?.removeAllListeners(); anim?.cancel()
        fromW = w; toW = w; fromH = h; toH = h; f = 1f; this.expanded = expanded
        prepare(); invalidate()
    }

    /** Recomputes the ellipsized strings for the expanded layout (not per frame). */
    private fun prepare() {
        val c = content
        val textW = (expandedWidth - 130 * d).coerceAtLeast(40 * d)
        when (c) {
            is IslandContent.Media -> {
                line1 = TextUtils.ellipsize(c.m.title, title, textW, TextUtils.TruncateAt.END)
                line2 = TextUtils.ellipsize(c.m.artist, sub, textW, TextUtils.TruncateAt.END)
                val bmp = c.m.art
                if (bmp != artShaderFor) {
                    artShaderFor = bmp
                    art.shader = bmp?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
                }
            }
            is IslandContent.Notif -> {
                line1 = TextUtils.ellipsize(c.n.title, title, textW + 50 * d, TextUtils.TruncateAt.END)
                line2 = TextUtils.ellipsize(c.n.text, sub, textW + 50 * d, TextUtils.TruncateAt.END)
            }
            is IslandContent.Charging -> { line1 = "Charging"; line2 = "" }
            is IslandContent.Timer -> { line1 = if (c.t.countDown) "Timer" else "Stopwatch"; line2 = ""; tickTimer() }
            null -> { line1 = ""; line2 = "" }
        }
    }

    /** Updates the chronometer text; the overlay calls this once a second. */
    fun tickTimer() {
        val t = (content as? IslandContent.Timer)?.t ?: return
        val now = System.currentTimeMillis()
        timeText = OverlayLogic.formatElapsed(if (t.countDown) t.whenMs - now else now - t.whenMs)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = pillWidth().coerceAtLeast(0f); val h = pillHeight().coerceAtLeast(0f)
        if (w <= 0f || h <= 0f) return
        val left = (width - w) / 2
        r.set(left, 0f, left + w, h)
        canvas.drawRoundRect(r, h / 2, h / 2, pill)
        // Content fades in near the end of the size animation.
        val alpha = ((f - 0.45f) / 0.55f).coerceIn(0f, 1f)
        if (alpha == 0f || content == null) return
        val a = (255 * alpha).toInt()
        canvas.save()
        canvas.clipRect(r)
        if (expanded) drawExpanded(canvas, a) else drawCompact(canvas, a)
        canvas.restore()
    }

    private fun drawIcon(canvas: Canvas, dr: Drawable?, cx: Float, cy: Float, size: Float, color: Int, a: Int) {
        dr ?: return
        dr.setTint(color)
        dr.alpha = a
        dr.setBounds((cx - size / 2).toInt(), (cy - size / 2).toInt(), (cx + size / 2).toInt(), (cy + size / 2).toInt())
        dr.draw(canvas)
    }

    private fun drawCompact(canvas: Canvas, a: Int) {
        val h = r.height(); val cy = r.centerY()
        val pad = h * 0.18f
        when (val c = content) {
            is IslandContent.Media -> {
                drawArt(canvas, r.left + pad + (h - 2 * pad) / 2, cy, h - 2 * pad, a)
                drawIcon(canvas, music, r.right - h / 2 - pad / 2, cy, h * 0.5f, ACCENT_GREEN, a)
            }
            is IslandContent.Timer -> {
                small.color = ORANGE; small.alpha = a
                canvas.drawText(timeText, r.right - h / 2, cy + small.textSize / 3, small)
            }
            is IslandContent.Charging -> {
                drawIcon(canvas, bolt, r.left + h / 2 + pad, cy, h * 0.55f, ACCENT_GREEN, a)
                small.color = ACCENT_GREEN; small.alpha = a
                canvas.drawText("${c.level}%", r.right - h / 2, cy + small.textSize / 3, small)
            }
            is IslandContent.Notif -> c.icon?.let {
                it.alpha = a
                val s = h - 2 * pad
                it.setBounds((r.left + pad * 2).toInt(), (cy - s / 2).toInt(), (r.left + pad * 2 + s).toInt(), (cy + s / 2).toInt())
                it.draw(canvas)
            }
            null -> Unit
        }
    }

    private fun drawExpanded(canvas: Canvas, a: Int) {
        val h = r.height(); val pad = 14 * d
        val iconSize = h - 2 * pad
        val cy = r.centerY()
        val textX = r.left + pad + iconSize + 12 * d
        title.alpha = a; sub.alpha = a
        when (val c = content) {
            is IslandContent.Media -> {
                drawArt(canvas, r.left + pad + iconSize / 2, cy, iconSize, a)
                canvas.drawText(line1, 0, line1.length, textX, cy - 2 * d, title)
                canvas.drawText(line2, 0, line2.length, textX, cy + sub.textSize + 2 * d, sub)
                val bx = r.right - pad - iconSize / 2
                fill.color = 0xFF2A2A2A.toInt(); fill.alpha = a
                canvas.drawCircle(bx, cy, iconSize / 2, fill)
                drawIcon(canvas, if (c.m.playing) pause else play, bx, cy, iconSize * 0.5f, 0xFFFFFFFF.toInt(), a)
            }
            is IslandContent.Charging -> {
                drawIcon(canvas, bolt, r.left + pad + iconSize / 2, cy, iconSize * 0.8f, ACCENT_GREEN, a)
                canvas.drawText(line1, 0, line1.length, textX, cy + title.textSize / 3, title)
                big.color = ACCENT_GREEN; big.alpha = a
                canvas.drawText("${c.level}%", r.right - pad, cy + big.textSize / 3, big)
                // Thin level bar along the bottom.
                val y = r.bottom - 7 * d
                fill.color = 0xFF2A2A2A.toInt(); fill.alpha = a
                tmp.set(textX, y - 2 * d, r.right - pad - 70 * d, y)
                canvas.drawRoundRect(tmp, d, d, fill)
                fill.color = ACCENT_GREEN; fill.alpha = a
                tmp.right = tmp.left + (tmp.width() * c.level / 100f)
                canvas.drawRoundRect(tmp, d, d, fill)
            }
            is IslandContent.Notif -> {
                c.icon?.let {
                    it.alpha = a
                    it.setBounds((r.left + pad).toInt(), (cy - iconSize / 2).toInt(), (r.left + pad + iconSize).toInt(), (cy + iconSize / 2).toInt())
                    it.draw(canvas)
                }
                canvas.drawText(line1, 0, line1.length, textX, cy - 2 * d, title)
                canvas.drawText(line2, 0, line2.length, textX, cy + sub.textSize + 2 * d, sub)
            }
            is IslandContent.Timer -> {
                canvas.drawText(line1, 0, line1.length, r.left + pad + 4 * d, cy + title.textSize / 3, title)
                big.color = ORANGE; big.alpha = a
                canvas.drawText(timeText, r.right - pad, cy + big.textSize / 3, big)
            }
            null -> Unit
        }
    }

    private fun drawArt(canvas: Canvas, cx: Float, cy: Float, size: Float, a: Int) {
        val bmp = artShaderFor
        tmp.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        if (bmp == null || art.shader == null) {
            fill.color = 0xFF2A2A2A.toInt(); fill.alpha = a
            canvas.drawRoundRect(tmp, size * 0.25f, size * 0.25f, fill)
            drawIcon(canvas, music, cx, cy, size * 0.55f, 0xFFFFFFFF.toInt(), a)
            return
        }
        val s = size / minOf(bmp.width, bmp.height)
        m.setScale(s, s); m.postTranslate(tmp.left, tmp.top)
        art.shader.setLocalMatrix(m)
        art.alpha = a
        canvas.drawRoundRect(tmp, size * 0.25f, size * 0.25f, art)
    }

    private var downX = 0f
    private var downY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
            MotionEvent.ACTION_UP -> {
                if (!r.contains(e.x, e.y)) return true
                // The round button at the right end of the expanded media card.
                val button = expanded && content is IslandContent.Media && e.x > r.right - r.height()
                onTap(button)
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        anim?.removeAllListeners(); anim?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val ACCENT_GREEN = 0xFF00E676.toInt()
        const val ORANGE = 0xFFFF9100.toInt()
    }
}
