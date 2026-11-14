package app.custo.feature.overlays

import android.media.AudioManager
import android.view.Gravity
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import app.custo.R
import app.custo.data.Keys

/** Custom volume panel. The window only exists while the panel is on screen. */
class VolumePanelOverlay(host: OverlayHost) : Overlay(host) {

    private val audio: AudioManager = host.service.getSystemService(AudioManager::class.java)
    private var view: VolumePanelView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var built: List<Any?>? = null
    private var expanded = false
    private var stream = AudioManager.STREAM_MUSIC
    private var colW = 0f
    private var gap = 0f
    private val hide = Runnable { dismiss() }

    override fun apply(p: Preferences) {
        if (p[Keys.volumePanelEnabled] != true) { remove(); return }
        if (key(p) != built) remove()
    }

    private fun key(p: Preferences) = listOf(p[OverlayKeys.volumeSkin], p[OverlayKeys.volumeScale], p[OverlayKeys.volumeOffsetY], p[Keys.volumePanelSide])

    override fun remove() {
        host.handler.removeCallbacks(hide)
        view?.animate()?.cancel()
        drop(view); view = null; lp = null; built = null; expanded = false
    }

    override fun onConfigChanged() = remove()

    override fun preview(kind: Preview) {
        if (kind != Preview.VOLUME) return
        stream = if (audio.isMusicActive) AudioManager.STREAM_MUSIC else AudioManager.STREAM_RING
        show(force = true)
    }

    /** Volume key single press: step the active stream and show the panel. */
    fun step(raise: Boolean) {
        if (view == null || !expanded) stream = if (audio.isMusicActive) AudioManager.STREAM_MUSIC else AudioManager.STREAM_RING
        val dir = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        try {
            audio.adjustStreamVolume(stream, dir, 0)
        } catch (_: SecurityException) {
            // Changing ringer volume in Do Not Disturb can be refused; fall back to media.
            stream = AudioManager.STREAM_MUSIC
            runCatching { audio.adjustStreamVolume(stream, dir, 0) }
        }
        if (host.isScreenOn) show() else remove()
    }

    private fun fraction(s: Int): Float {
        val max = audio.getStreamMaxVolume(s).coerceAtLeast(1)
        return audio.getStreamVolume(s) / max.toFloat()
    }

    private fun icon(s: Int) = ContextCompat.getDrawable(ctx, when (s) {
        AudioManager.STREAM_MUSIC -> R.drawable.ic_music
        AudioManager.STREAM_ALARM -> R.drawable.ov_alarm
        else -> R.drawable.ov_notifications
    })?.mutate()

    private fun slotsFor(exp: Boolean): List<VolumeSlot> =
        (if (exp) listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_ALARM) else listOf(stream))
            .map { VolumeSlot(it, icon(it)).apply { fraction = fraction(it) } }

    private fun show(force: Boolean = false) {
        val p = host.prefs ?: return
        if (!force && p[Keys.volumePanelEnabled] != true) return
        val v = view ?: create(p) ?: return
        v.accent = host.accent()
        val current = v.slots
        if (current.size != (if (expanded) 3 else 1) || (!expanded && current.firstOrNull()?.stream != stream)) v.slots = slotsFor(expanded)
        else { current.forEach { it.fraction = fraction(it.stream) }; v.changed() }
        scheduleHide()
    }

    private fun create(p: Preferences): VolumePanelView? {
        val skin = enumOr(p[OverlayKeys.volumeSkin], VolumeSkin.PILL)
        val scale = (p[OverlayKeys.volumeScale] ?: 1f).coerceIn(0.6f, 1.6f)
        val side = enumOr(p[Keys.volumePanelSide], PanelSide.RIGHT)
        val (w, h) = when (skin) {
            VolumeSkin.PILL -> 56f to 240f
            VolumeSkin.SLIM -> 40f to 230f
            VolumeSkin.BIG -> 78f to 260f
            VolumeSkin.DOTS -> 28f to 210f
        }
        colW = dp(w * scale); gap = dp(12f)
        expanded = false
        val v = VolumePanelView(
            ctx, skin, colW, gap,
            onSet = { i, f -> setLevel(i, f) },
            onTap = { i, f -> if (host.prefs?.get(OverlayKeys.volumeExpand) != false) toggleExpand() else setLevel(i, f) },
            onTouch = { scheduleHide() },
        )
        val params = params(colW.toInt(), dp(h * scale).toInt(), touchable = true).apply {
            gravity = (if (side == PanelSide.RIGHT) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            x = dpi(14f)
            y = (screen().height() * (p[OverlayKeys.volumeOffsetY] ?: 0f)).toInt()
        }
        v.alpha = 0f
        v.translationX = if (side == PanelSide.RIGHT) dp(40f) else -dp(40f)
        if (!add(v, params)) return null
        view = v; lp = params; built = key(p)
        v.animate().alpha(1f).translationX(0f).setDuration(180).start()
        return v
    }

    private fun setLevel(i: Int, f: Float) {
        val v = view ?: return
        val s = v.slots.getOrNull(i)?.stream ?: return
        val max = audio.getStreamMaxVolume(s)
        runCatching { audio.setStreamVolume(s, (f * max + 0.5f).toInt(), 0) }
        v.slots[i].fraction = fraction(s)
        v.changed()
        scheduleHide()
    }

    private fun toggleExpand() {
        val v = view ?: return
        val params = lp ?: return
        expanded = !expanded
        v.slots = slotsFor(expanded)
        params.width = if (expanded) (3 * colW + 2 * gap).toInt() else colW.toInt()
        update(v, params)
        scheduleHide()
    }

    private fun scheduleHide() {
        host.handler.removeCallbacks(hide)
        host.handler.postDelayed(hide, (host.prefs?.get(OverlayKeys.volumeHideMs) ?: 2200).toLong())
    }

    private fun dismiss() {
        host.handler.removeCallbacks(hide)
        val v = view ?: return
        view = null; lp = null; built = null; expanded = false
        v.animate().alpha(0f).setDuration(150).withEndAction { drop(v) }.start()
    }
}
