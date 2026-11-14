package app.custo.feature.overlays

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
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.withSave
import app.custo.R
import kotlin.math.abs
import kotlin.math.min

/** Everything the panel shows. Re-read from the system each time something changes. */
data class CcState(
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
    val torch: Boolean = false,
    val dnd: Boolean = false,
    val rotation: Boolean = false,
    val brightness: Float = 0.5f,
    val volume: Float = 0.5f,
    val mediaTitle: String? = null,
    val mediaArtist: String = "",
    val mediaArt: Bitmap? = null,
    val mediaPlaying: Boolean = false,
    val mediaAccess: Boolean = true,
    val apps: List<Pair<String, Drawable>> = emptyList(),
)

/** What a tap landed on. */
sealed interface CcHit {
    data class Tile(val tile: CcTile) : CcHit
    data class App(val pkg: String) : CcHit
    data class Media(val cmd: String) : CcHit // open, prev, toggle, next
}

/** Colors for one skin. */
class CcColors(val tile: Int, val tileOn: Int, val icon: Int, val iconOn: Int, val text: Int, val sub: Int, val fill: Int, val onFill: Int) {
    companion object {
        fun of(skin: CcSkin, accent: Int): CcColors = when (skin) {
            CcSkin.LIGHT -> CcColors(0xE6F4F4F6.toInt(), accent, 0xFF1C1B1F.toInt(), 0xFFFFFFFF.toInt(), 0xFF1C1B1F.toInt(), 0xFF5F5E66.toInt(), accent, 0xFFFFFFFF.toInt())
            CcSkin.DARK -> CcColors(0xD92A2A2E.toInt(), accent, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFB9B8C0.toInt(), 0xF2FFFFFF.toInt(), 0xFF1C1B1F.toInt())
            CcSkin.ACCENT -> CcColors(
                (OverlayLogic.lerpColor(accent, 0xFF000000.toInt(), 0.45f) and 0x00FFFFFF) or 0xE0000000.toInt(),
                0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), accent, 0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0xF2FFFFFF.toInt(), accent,
            )
        }
    }
}

/**
 * The whole Control Center in one full-screen view: a dim scrim with a grid of tiles at the top.
 * Only animates while opening or closing. Tap the scrim or swipe up to close.
 */
@SuppressLint("ViewConstructor")
class ControlCenterView(
    context: Context,
    private val tiles: List<CcTile>,
    private val colors: CcColors,
    private val radius: Float,
    private val topInset: Float,
    private val onHit: (CcHit) -> Unit,
    private val onSlide: (CcTile, Float, Boolean) -> Unit,
    private val onClose: () -> Unit,
) : View(context) {

    var state = CcState()
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val cells = CcLogic.layout(tiles)
    private val rows = CcLogic.rowCount(cells)
    private val gap = 10 * d
    private val pad = 14 * d
    private var cell = 0f
    private val panel = RectF()
    private val r = RectF()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val scrim = Paint().apply { color = 0x73000000 }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11 * d; textAlign = Paint.Align.CENTER }
    private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15 * d; isFakeBoldText = true }
    private val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13 * d }
    private val icons = HashMap<Int, Drawable?>()
    private val clip = android.graphics.Path()
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artMatrix = Matrix()
    private val artRect = RectF()
    private var artShader: BitmapShader? = null
    private var artFor: Bitmap? = null

    private var progress = 0f
    private var anim: ValueAnimator? = null

    private enum class Mode { NONE, OUTSIDE, TAP, SLIDE }
    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var active = -1
    private var slideValue = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pw = min(w - 32 * d, 420 * d)
        cell = (pw - 2 * pad - (CcLogic.COLS - 1) * gap) / CcLogic.COLS
        val ph = 2 * pad + rows * cell + (rows - 1).coerceAtLeast(0) * gap
        val left = (w - pw) / 2
        panel.set(left, topInset, left + pw, topInset + ph)
    }

    fun show() = animateTo(1f, 260, null)

    fun hide(end: () -> Unit) = animateTo(0f, 160, end)

    private fun animateTo(target: Float, ms: Long, end: (() -> Unit)?) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(progress, target).apply {
            duration = ms
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            if (end != null) addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = end()
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.removeAllListeners(); anim?.cancel(); anim = null
        super.onDetachedFromWindow()
    }

    private fun cellRect(c: CcCell, out: RectF) {
        val l = panel.left + pad + c.col * (cell + gap)
        val t = panel.top + pad + c.row * (cell + gap)
        out.set(l, t, l + c.cols * cell + (c.cols - 1) * gap, t + c.rows * cell + (c.rows - 1) * gap)
    }

    private fun icon(res: Int): Drawable? = icons.getOrPut(res) { ContextCompat.getDrawable(context, res)?.mutate() }

    override fun onDraw(canvas: Canvas) {
        scrim.alpha = (0x73 * progress).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        canvas.withSave {
            translate(0f, -(1f - progress) * 48 * d)
            val a = (progress * 255).toInt()
            cells.forEachIndexed { i, c ->
                cellRect(c, r)
                if (i == active && mode == Mode.TAP && !moved) { val s = 0.04f * r.width().coerceAtMost(r.height()); r.inset(s, s) }
                drawTile(this, c.tile, r, a)
            }
        }
    }

    private fun on(t: CcTile) = when (t) {
        CcTile.WIFI -> state.wifi
        CcTile.BLUETOOTH -> state.bluetooth
        CcTile.FLASHLIGHT -> state.torch
        CcTile.DND -> state.dnd
        CcTile.ROTATION -> state.rotation
        else -> false
    }

    private fun drawTile(canvas: Canvas, t: CcTile, rect: RectF, alpha: Int) {
        val isOn = on(t)
        fill.color = if (isOn) colors.tileOn else colors.tile
        fill.alpha = fill.alpha * alpha / 255
        canvas.drawRoundRect(rect, radius, radius, fill)
        when (t) {
            CcTile.BRIGHTNESS, CcTile.VOLUME -> drawSlider(canvas, t, rect, alpha)
            CcTile.MEDIA -> drawMedia(canvas, rect, alpha)
            CcTile.APPS -> drawApps(canvas, rect, alpha)
            else -> {
                val s = 26 * d
                val cy = rect.centerY() - 7 * d
                drawIcon(canvas, iconFor(t), rect.centerX(), cy, s, if (isOn) colors.iconOn else colors.icon, alpha)
                label.color = if (isOn) colors.iconOn else colors.sub
                label.alpha = label.alpha * alpha / 255
                val text = TextUtils.ellipsize(t.label, label, rect.width() - 8 * d, TextUtils.TruncateAt.END).toString()
                canvas.drawText(text, rect.centerX(), rect.bottom - 10 * d, label)
            }
        }
    }

    private fun drawIcon(canvas: Canvas, res: Int, cx: Float, cy: Float, size: Float, tint: Int, alpha: Int) {
        val dr = icon(res) ?: return
        dr.setTint(tint)
        dr.alpha = alpha
        dr.setBounds((cx - size / 2).toInt(), (cy - size / 2).toInt(), (cx + size / 2).toInt(), (cy + size / 2).toInt())
        dr.draw(canvas)
    }

    private fun drawSlider(canvas: Canvas, t: CcTile, rect: RectF, alpha: Int) {
        val v = if (mode == Mode.SLIDE && active >= 0 && cells[active].tile == t) slideValue
        else if (t == CcTile.BRIGHTNESS) state.brightness else state.volume
        fill.color = colors.fill
        fill.alpha = fill.alpha * alpha / 255
        canvas.withSave {
            clip.rewind()
            clip.addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW)
            clipPath(clip)
            drawRect(rect.left, rect.top, rect.left + rect.width() * v.coerceIn(0f, 1f), rect.bottom, fill)
        }
        val s = 22 * d
        val ix = rect.left + rect.height() / 2
        val covered = rect.width() * v > ix - rect.left + s / 2
        drawIcon(canvas, iconFor(t), ix, rect.centerY(), s, if (covered) colors.onFill else colors.icon, alpha)
    }

    private fun drawMedia(canvas: Canvas, rect: RectF, alpha: Int) {
        val p = 12 * d
        val art = rect.height() - 2 * p
        val ar = artRect.apply { set(rect.left + p, rect.top + p, rect.left + p + art, rect.top + p + art) }
        val bmp = state.mediaArt
        if (bmp != null) {
            if (artFor !== bmp) { artShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); artFor = bmp }
            val sc = maxOf(art / bmp.width, art / bmp.height)
            artMatrix.setScale(sc, sc)
            artMatrix.postTranslate(ar.left + (art - bmp.width * sc) / 2, ar.top + (art - bmp.height * sc) / 2)
            artShader?.setLocalMatrix(artMatrix)
            artPaint.shader = artShader
            artPaint.alpha = alpha
            canvas.drawRoundRect(ar, radius * 0.6f, radius * 0.6f, artPaint)
        } else {
            fill.color = colors.sub; fill.alpha = 0x33 * alpha / 255
            canvas.drawRoundRect(ar, radius * 0.6f, radius * 0.6f, fill)
            drawIcon(canvas, R.drawable.ic_music, ar.centerX(), ar.centerY(), art * 0.4f, colors.sub, alpha)
        }
        val tx = ar.right + p
        val tw = rect.right - p - tx
        title.color = colors.text; title.alpha = alpha
        sub.color = colors.sub; sub.alpha = sub.alpha * alpha / 255
        val (t1, t2) = when {
            !state.mediaAccess -> "Music controls" to "Tap to allow notification access"
            state.mediaTitle == null -> "Nothing playing" to "Start music in any app"
            else -> state.mediaTitle!! to state.mediaArtist
        }
        canvas.drawText(TextUtils.ellipsize(t1, title, tw, TextUtils.TruncateAt.END).toString(), tx, rect.top + p + title.textSize, title)
        canvas.drawText(TextUtils.ellipsize(t2, sub, tw, TextUtils.TruncateAt.END).toString(), tx, rect.top + p + title.textSize + sub.textSize + 6 * d, sub)
        if (state.mediaAccess && state.mediaTitle != null) {
            val cy = rect.bottom - p - 16 * d
            val step = tw / 3
            val ics = listOf(R.drawable.ic_prev, if (state.mediaPlaying) R.drawable.ic_pause else R.drawable.ic_play, R.drawable.ic_next)
            ics.forEachIndexed { i, res -> drawIcon(canvas, res, tx + step * i + step / 2, cy, if (i == 1) 32 * d else 26 * d, colors.text, alpha) }
        }
    }

    private fun drawApps(canvas: Canvas, rect: RectF, alpha: Int) {
        val apps = state.apps
        if (apps.isEmpty()) {
            sub.color = colors.sub; sub.alpha = sub.alpha * alpha / 255
            sub.textAlign = Paint.Align.CENTER
            canvas.drawText("Pick apps in Custo", rect.centerX(), rect.centerY() + sub.textSize / 3, sub)
            sub.textAlign = Paint.Align.LEFT
            return
        }
        val slot = rect.width() / CcLogic.MAX_APPS
        val s = min(rect.height() * 0.62f, 44 * d)
        apps.take(CcLogic.MAX_APPS).forEachIndexed { i, (_, dr) ->
            val cx = rect.left + slot * i + slot / 2
            dr.alpha = alpha
            dr.setBounds((cx - s / 2).toInt(), (rect.centerY() - s / 2).toInt(), (cx + s / 2).toInt(), (rect.centerY() + s / 2).toInt())
            dr.draw(canvas)
        }
    }

    // ---- touch ----

    private fun hitCell(x: Float, y: Float): Int {
        cells.forEachIndexed { i, c -> cellRect(c, r); if (r.contains(x, y)) return i }
        return -1
    }

    private fun sliderValue(x: Float): Float {
        cellRect(cells[active], r)
        return ((x - r.left) / r.width()).coerceIn(0f, 1f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (progress < 0.5f && mode == Mode.NONE && e.actionMasked != MotionEvent.ACTION_DOWN) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; moved = false
                active = if (panel.contains(e.x, e.y)) hitCell(e.x, e.y) else -1
                mode = when {
                    !panel.contains(e.x, e.y) -> Mode.OUTSIDE
                    active < 0 -> Mode.NONE
                    cells[active].tile == CcTile.BRIGHTNESS || cells[active].tile == CcTile.VOLUME -> {
                        slideValue = if (cells[active].tile == CcTile.BRIGHTNESS) state.brightness else state.volume
                        Mode.SLIDE
                    }
                    else -> Mode.TAP
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX; val dy = e.y - downY
                if (!moved && (abs(dx) > slop || abs(dy) > slop)) { moved = true; invalidate() }
                if (mode == Mode.SLIDE) {
                    if (moved) { slideValue = sliderValue(e.x); onSlide(cells[active].tile, slideValue, false); invalidate() }
                } else if (CcLogic.isPushUp(dx, dy, 72 * d)) {
                    mode = Mode.NONE; onClose()
                }
            }
            MotionEvent.ACTION_UP -> {
                when (mode) {
                    Mode.SLIDE -> {
                        if (!moved) slideValue = sliderValue(e.x)
                        val t = cells[active].tile
                        state = if (t == CcTile.BRIGHTNESS) state.copy(brightness = slideValue) else state.copy(volume = slideValue)
                        onSlide(t, slideValue, true)
                    }
                    Mode.OUTSIDE -> if (!moved) onClose()
                    Mode.TAP -> if (!moved) tap(cells[active], e.x, e.y)
                    Mode.NONE -> Unit
                }
                mode = Mode.NONE; active = -1; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { mode = Mode.NONE; active = -1; invalidate() }
        }
        return true
    }

    private fun tap(c: CcCell, x: Float, y: Float) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        cellRect(c, r)
        when (c.tile) {
            CcTile.APPS -> {
                val i = ((x - r.left) / (r.width() / CcLogic.MAX_APPS)).toInt()
                state.apps.getOrNull(i)?.let { onHit(CcHit.App(it.first)) }
            }
            CcTile.MEDIA -> {
                val p = 12 * d
                val tx = r.left + p + (r.height() - 2 * p) + p
                val cmd = if (state.mediaAccess && state.mediaTitle != null && x >= tx && y >= r.bottom - p - 40 * d) {
                    when (((x - tx) / ((r.right - p - tx) / 3)).toInt()) { 0 -> "prev"; 1 -> "toggle"; else -> "next" }
                } else "open"
                onHit(CcHit.Media(cmd))
            }
            else -> onHit(CcHit.Tile(c.tile))
        }
    }

    companion object {
        fun iconFor(t: CcTile): Int = when (t) {
            CcTile.WIFI -> R.drawable.ic_wifi
            CcTile.BLUETOOTH -> R.drawable.ic_bluetooth
            CcTile.FLASHLIGHT -> R.drawable.ov_flashlight
            CcTile.DND -> R.drawable.cc_dnd
            CcTile.ROTATION -> R.drawable.cc_rotate
            CcTile.MEDIA -> R.drawable.ic_music
            CcTile.BRIGHTNESS -> R.drawable.cc_brightness
            CcTile.VOLUME -> R.drawable.ic_volume
            CcTile.SCREENSHOT -> R.drawable.ov_screenshot
            CcTile.RECORD -> R.drawable.cc_record
            CcTile.CALCULATOR -> R.drawable.cc_calc
            CcTile.CAMERA -> R.drawable.ov_camera
            CcTile.ALARM -> R.drawable.ov_alarm
            CcTile.APPS -> R.drawable.cc_apps
        }
    }
}

/** Invisible (or tinted) strip on the top edge. A swipe down from it opens the panel. */
@SuppressLint("ViewConstructor")
class CcTriggerView(context: Context, private val tint: Int, private val thresholdPx: Float, private val onOpen: () -> Unit) : View(context) {
    private var downX = 0f
    private var downY = 0f
    private var fired = false

    override fun onDraw(canvas: Canvas) { if (tint != 0) canvas.drawColor(tint) }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; fired = false }
            MotionEvent.ACTION_MOVE -> if (!fired && CcLogic.isPullDown(e.rawX - downX, e.rawY - downY, thresholdPx)) {
                fired = true
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onOpen()
            }
        }
        return true
    }
}
