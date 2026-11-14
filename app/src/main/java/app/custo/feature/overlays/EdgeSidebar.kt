package app.custo.feature.overlays

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import kotlin.math.abs
import kotlin.math.ceil

/** A small handle on a screen edge that opens a panel of favorite apps and quick actions. */
class EdgeSidebarOverlay(host: OverlayHost) : Overlay(host) {

    private var handle: SidebarHandleView? = null
    private var panel: SidebarPanelView? = null
    private var built: List<Any?>? = null
    private var closedAt = 0L

    override fun apply(p: Preferences) {
        if (p[OverlayKeys.sidebar] != true) { remove(); return }
        val key = listOf(p[OverlayKeys.sidebarSide], p[OverlayKeys.sidebarOffsetY], host.accent())
        if (key == built && handle != null) return
        remove()
        built = key
        val side = enumOr(p[OverlayKeys.sidebarSide], PanelSide.RIGHT)
        val v = SidebarHandleView(ctx, side, host.accent()) { open() }
        val lp = params(dpi(20f), dpi(72f), touchable = true).apply {
            gravity = (if (side == PanelSide.RIGHT) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            y = (screen().height() * (p[OverlayKeys.sidebarOffsetY] ?: -0.1f)).toInt()
        }
        if (add(v, lp)) handle = v
    }

    override fun preview(kind: Preview) { if (kind == Preview.SIDEBAR) open(force = true) }

    private fun open(force: Boolean = false) {
        val p = host.prefs ?: return
        if (panel != null) { close(); return }
        // The touch that closed the panel (outside it) may be the handle's own tap.
        if (System.currentTimeMillis() - closedAt < 400) return
        if (!force && p[OverlayKeys.sidebar] != true) return
        val side = enumOr(p[OverlayKeys.sidebarSide], PanelSide.RIGHT)
        val apps = OverlayLogic.csv(p[OverlayKeys.sidebarApps]).take(MAX_APPS)
            .mapNotNull { pkg -> Apps.icon(ctx, pkg)?.let { SidebarItem.App(pkg, it) } }
        val actions = (p[OverlayKeys.sidebarActions]?.let { OverlayLogic.csv(it) } ?: DEFAULT_ACTIONS)
            .map { OverlayAction.of(it) }.filter { it != OverlayAction.NONE }
            .map { SidebarItem.Action(it, ContextCompat.getDrawable(ctx, it.icon)?.mutate()) }
        val items = apps + actions
        if (items.isEmpty()) return
        val cols = if (items.size > 6) 2 else 1
        val rows = ceil(items.size / cols.toFloat()).toInt()
        val cell = dp(56f); val pad = dp(10f)
        val v = SidebarPanelView(ctx, items, cols, cell, pad, host.accent()) { item ->
            close()
            when (item) {
                is SidebarItem.App -> host.actions.launchApp(item.pkg)
                is SidebarItem.Action -> host.actions.perform(item.action)
            }
        }
        val lp = params((cols * cell + 2 * pad).toInt(), (rows * cell + 2 * pad).toInt(), touchable = true).apply {
            gravity = (if (side == PanelSide.RIGHT) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            x = dpi(8f)
            y = (screen().height() * (p[OverlayKeys.sidebarOffsetY] ?: -0.1f)).toInt()
                .coerceIn(-screen().height() / 2 + height / 2, screen().height() / 2 - height / 2)
        }
        v.onOutside = { close() }
        v.alpha = 0f
        v.translationX = if (side == PanelSide.RIGHT) dp(40f) else -dp(40f)
        if (!add(v, lp)) return
        panel = v
        v.animate().alpha(1f).translationX(0f).setDuration(180).start()
    }

    private fun close() {
        val v = panel ?: return
        panel = null
        closedAt = System.currentTimeMillis()
        v.animate().alpha(0f).setDuration(120).withEndAction { drop(v) }.start()
    }

    override fun remove() {
        built = null
        panel?.animate()?.cancel(); drop(panel); panel = null
        drop(handle); handle = null
    }

    companion object {
        const val MAX_APPS = 8
        val DEFAULT_ACTIONS = listOf(OverlayAction.FLASHLIGHT, OverlayAction.SCREENSHOT, OverlayAction.LOCK, OverlayAction.NOTIFICATIONS).map { it.name }
    }
}

sealed interface SidebarItem {
    data class App(val pkg: String, val icon: Drawable) : SidebarItem
    data class Action(val action: OverlayAction, val icon: Drawable?) : SidebarItem
}

@SuppressLint("ViewConstructor")
class SidebarHandleView(context: Context, private val side: PanelSide, accent: Int, private val onOpen: () -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (accent and 0x00FFFFFF) or 0xAA000000.toInt() }
    private val r = RectF()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f

    override fun onDraw(canvas: Canvas) {
        val w = resources.displayMetrics.density * 5
        val x = if (side == PanelSide.RIGHT) width - w - 2 else 2f
        r.set(x, height * 0.1f, x + w, height * 0.9f)
        canvas.drawRoundRect(r, w / 2, w / 2, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> downX = e.rawX
            MotionEvent.ACTION_UP -> {
                val dx = e.rawX - downX
                val inward = if (side == PanelSide.RIGHT) -dx else dx
                if (abs(dx) < slop || inward > slop) { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onOpen() }
            }
        }
        return true
    }
}

@SuppressLint("ViewConstructor")
class SidebarPanelView(
    context: Context,
    private val items: List<SidebarItem>,
    private val cols: Int,
    private val cell: Float,
    private val pad: Float,
    private val accent: Int,
    private val onPick: (SidebarItem) -> Unit,
) : View(context) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF01C1B1F.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = resources.displayMetrics.density * 1.5f
        color = (accent and 0x00FFFFFF) or 0x99000000.toInt()
    }
    private val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (accent and 0x00FFFFFF) or 0x40000000 }
    private val r = RectF()

    override fun onDraw(canvas: Canvas) {
        r.set(0f, 0f, width.toFloat(), height.toFloat())
        r.inset(border.strokeWidth, border.strokeWidth)
        val radius = cell * 0.45f
        canvas.drawRoundRect(r, radius, radius, bg)
        canvas.drawRoundRect(r, radius, radius, border)
        val icon = cell * 0.62f
        items.forEachIndexed { i, item ->
            val cx = pad + (i % cols) * cell + cell / 2
            val cy = pad + (i / cols) * cell + cell / 2
            when (item) {
                is SidebarItem.App -> {
                    item.icon.setBounds((cx - icon / 2).toInt(), (cy - icon / 2).toInt(), (cx + icon / 2).toInt(), (cy + icon / 2).toInt())
                    item.icon.draw(canvas)
                }
                is SidebarItem.Action -> {
                    canvas.drawCircle(cx, cy, icon / 2, chip)
                    item.icon?.let {
                        val s = icon * 0.55f
                        it.setTint(accent)
                        it.setBounds((cx - s / 2).toInt(), (cy - s / 2).toInt(), (cx + s / 2).toInt(), (cy + s / 2).toInt())
                        it.draw(canvas)
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> outside()
            MotionEvent.ACTION_UP -> {
                val col = ((e.x - pad) / cell).toInt(); val row = ((e.y - pad) / cell).toInt()
                val i = row * cols + col
                if (e.x >= pad && e.y >= pad && col in 0 until cols && i in items.indices) onPick(items[i])
            }
        }
        return true
    }

    /** Called when the user touches anywhere outside the panel. */
    var onOutside: (() -> Unit)? = null

    private fun outside() { onOutside?.invoke() }
}
