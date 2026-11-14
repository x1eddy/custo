package app.custo.feature.overlays

import android.app.ActivityOptions
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.datastore.preferences.core.Preferences

/**
 * A black pill at the top center. Shows now playing or a running timer in compact form, and
 * expands (spring) for charging and new notifications. Tap to expand or collapse.
 */
class DynamicIslandOverlay(host: OverlayHost) : Overlay(host) {

    private var view: IslandView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var media: IslandMedia? = null
    private var timer: IslandTimer? = null
    private var transient: IslandContent? = null
    private var userExpanded = false
    private var target: Any? = null

    private val collapse = Runnable { transient = null; userExpanded = false; render() }
    private val tick = object : Runnable {
        override fun run() {
            val v = view ?: return
            if (v.content is IslandContent.Timer) { v.tickTimer(); host.handler.postDelayed(this, 1000) }
        }
    }

    override val wantsScreen get() = host.prefs?.get(OverlayKeys.island) == true
    override val wantsPower get() = host.prefs?.get(OverlayKeys.island) == true && host.prefs?.get(OverlayKeys.islandCharging) != false

    override fun apply(p: Preferences) {
        if (p[OverlayKeys.island] != true) { remove(); return }
        target = null
        render()
    }

    override fun remove() {
        host.handler.removeCallbacks(collapse)
        host.handler.removeCallbacks(tick)
        transient = null; userExpanded = false; target = null
        drop(view); view = null; lp = null
    }

    override fun onScreen(on: Boolean) {
        if (!on) { transient = null; userExpanded = false }
        target = null
        render()
    }

    override fun onPowerConnected(level: Int) {
        val p = host.prefs ?: return
        if (p[OverlayKeys.island] == true && p[OverlayKeys.islandCharging] != false && host.isScreenOn) show(IslandContent.Charging(level))
    }

    override fun preview(kind: Preview) {
        if (kind != Preview.ISLAND) return
        val m = media
        show(
            if (m != null) IslandContent.Media(m)
            else IslandContent.Notif(IslandNotif(ctx.packageName, "Custo", "This is your dynamic island", null, null), Apps.icon(ctx, ctx.packageName)),
            force = true,
        )
    }

    fun setMedia(m: IslandMedia?) { media = m; if (view != null || m?.playing == true) render() }
    fun setTimer(t: IslandTimer?) { timer = t; render() }

    fun onNotification(n: IslandNotif) {
        val p = host.prefs ?: return
        if (p[OverlayKeys.island] != true || p[OverlayKeys.islandNotifs] == false || !host.isScreenOn) return
        val icon = n.icon?.loadDrawable(ctx) ?: Apps.icon(ctx, n.pkg)
        show(IslandContent.Notif(n, icon))
    }

    private fun show(c: IslandContent, force: Boolean = false) {
        val p = host.prefs ?: return
        if (!force && p[OverlayKeys.island] != true) return
        transient = c
        host.handler.removeCallbacks(collapse)
        host.handler.postDelayed(collapse, (p[OverlayKeys.islandCollapseSec] ?: 4) * 1000L)
        render(force)
    }

    private fun persistent(p: Preferences): IslandContent? {
        timer?.takeIf { p[OverlayKeys.islandTimer] != false }?.let { return IslandContent.Timer(it) }
        media?.takeIf { it.playing && p[OverlayKeys.islandMedia] != false }?.let { return IslandContent.Media(it) }
        return null
    }

    private fun render(force: Boolean = false) {
        val p = host.prefs ?: return
        if (!force && p[OverlayKeys.island] != true) { remove(); return }
        if (!host.isScreenOn) { host.handler.removeCallbacks(tick); drop(view); view = null; lp = null; target = null; return }
        val content = transient ?: persistent(p)
        val exp = content != null && (transient != null || userExpanded)
        if (content == null) userExpanded = false

        val cut = topCutout()
        val h0 = (cut?.height()?.plus(dpi(8f)) ?: dpi(28f)).toFloat()
        val w0 = (cut?.width()?.plus(dpi(26f)) ?: dpi(72f)).toFloat().coerceAtLeast(h0)
        val wc = dp(190f)
        val we = minOf(screen().width() - dp(20f), dp(380f))
        val he = dp(74f)
        val top = cut?.let { (it.top - dpi(4f)).coerceAtLeast(0) } ?: dpi(4f)

        val idlePill = p[OverlayKeys.islandIdlePill] == true
        val (tw, th) = when {
            exp -> we to he
            content != null -> wc to h0
            idlePill -> w0 to h0
            else -> 0f to h0
        }
        val key = listOf(exp, tw, th)
        if (key == target && view != null) {
            // Same size: just swap what is drawn (e.g. new track metadata).
            if (content != null && view?.content != content) view?.content = content
            syncTick(content)
            return
        }
        target = key

        var v = view
        if (v == null) {
            if (tw == 0f) return
            v = IslandView(ctx) { button -> onTap(button) }
            v.expandedWidth = we
            v.snapTo(h0, h0, false)
            val params = params(0, 0, touchable = true).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = top }
            lp = params
            sizeWindow(v, maxOf(tw, h0), th)
            if (!add(v, params)) { lp = null; return }
            view = v
        }
        v.expandedWidth = we
        if (v.content != content && content != null) v.content = content
        if (exp) sizeWindow(v, we, he)
        val finalV = v
        v.animateTo(tw, th, exp) {
            if (tw == 0f) { if (view === finalV) { drop(finalV); view = null; lp = null; target = null } }
            else if (!exp) sizeWindow(finalV, tw, th)
        }
        syncTick(content)
    }

    private fun syncTick(content: IslandContent?) {
        host.handler.removeCallbacks(tick)
        if (content is IslandContent.Timer) host.handler.post(tick)
    }

    private fun sizeWindow(v: IslandView, w: Float, h: Float) {
        val params = lp ?: return
        val nw = (w * 1.1f + dp(8f)).toInt(); val nh = (h * 1.12f + dp(4f)).toInt()
        if (params.width == nw && params.height == nh) return
        params.width = nw; params.height = nh
        if (v.isAttachedToWindow) update(v, params)
    }

    private fun onTap(button: Boolean) {
        val v = view ?: return
        val c = v.content
        when {
            button -> host.actions.perform(OverlayAction.PLAY_PAUSE)
            v.expanded && c is IslandContent.Notif -> {
                c.n.intent?.let { pi ->
                    runCatching {
                        val opts = if (Build.VERSION.SDK_INT >= 34) {
                            @Suppress("DEPRECATION")
                            ActivityOptions.makeBasic()
                                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
                        } else null
                        pi.send(ctx, 0, null, null, null, null, opts)
                    }
                }
                transient = null; userExpanded = false; render()
            }
            v.expanded -> { transient = null; userExpanded = false; host.handler.removeCallbacks(collapse); render() }
            c != null -> {
                userExpanded = true
                host.handler.removeCallbacks(collapse)
                host.handler.postDelayed(collapse, ((host.prefs?.get(OverlayKeys.islandCollapseSec) ?: 4) + 2) * 1000L)
                render()
            }
        }
    }
}
