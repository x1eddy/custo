package app.custo.feature.overlays

import android.view.WindowManager
import androidx.datastore.preferences.core.Preferences
import app.custo.data.Keys
import java.util.Calendar

/** Edge lighting: the window exists only while an animation plays. */
class EdgeLightingOverlay(host: OverlayHost) : Overlay(host) {

    private var view: EdgeLightView? = null

    override fun apply(p: Preferences) { if (p[Keys.edgeEnabled] != true) remove() }

    override fun remove() { drop(view); view = null }

    override fun onConfigChanged() = remove()

    override fun preview(kind: Preview) { if (kind == Preview.EDGE) play(EdgeEvent(null, 0), force = true) }

    fun play(e: EdgeEvent, force: Boolean = false) {
        val p = host.prefs ?: return
        val rules = OverlayLogic.decodeRules(p[OverlayKeys.edgeAppRules])
        val rule = e.pkg?.let { rules[it] }
        if (!force) {
            if (p[Keys.edgeEnabled] != true) return
            if (rule == OverlayLogic.IGNORE) return
            if (p[OverlayKeys.edgeScreenOnOnly] == true && !host.isScreenOn) return
            if (p[OverlayKeys.edgeQuiet] == true) {
                val c = Calendar.getInstance()
                val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
                if (OverlayLogic.inQuietHours(now, p[OverlayKeys.edgeQuietStart] ?: 22 * 60, p[OverlayKeys.edgeQuietEnd] ?: 7 * 60)) return
            }
        }
        val colors = colors(p, e.color, rule)
        val v = view ?: EdgeLightView(ctx).also {
            if (!add(it, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT))) return
            view = it
        }
        v.strokePx = dp(p[Keys.edgeWidthDp] ?: 4f)
        v.onFinished = { remove() }
        val style = enumOr(p[OverlayKeys.edgeStyle], EdgeStyle.SWEEP)
        val start = { v.play(colors, style, (p[Keys.edgeDurationMs] ?: 2500).toLong(), p[OverlayKeys.edgeRepeat] ?: 1) }
        // A fresh view has no size yet; wait for layout so the geometry is right.
        if (v.width == 0) v.post { start() } else start()
    }

    private fun colors(p: Preferences, appColor: Int, rule: Int?): IntArray {
        if (rule != null && rule != OverlayLogic.IGNORE) return intArrayOf(rule)
        return when (enumOr(p[Keys.edgeColorMode], EdgeColorMode.APP)) {
            EdgeColorMode.RAINBOW -> RAINBOW
            EdgeColorMode.ACCENT -> intArrayOf(host.accent())
            EdgeColorMode.APP -> intArrayOf(appColor.takeIf { it != 0 } ?: host.accent())
            EdgeColorMode.CUSTOM -> OverlayLogic.colorsCsv(p[OverlayKeys.edgeGradient]).takeIf { it.isNotEmpty() }?.toIntArray()
                ?: intArrayOf(host.accent())
        }
    }

    companion object {
        val RAINBOW = intArrayOf(0xFFFF1744.toInt(), 0xFFFFEA00.toInt(), 0xFF00E676.toInt(), 0xFF00B0FF.toInt(), 0xFFD500F9.toInt())
        val DEFAULT_GRADIENT = listOf(0xFF6D4AFF.toInt(), 0xFF00B8D4.toInt())
    }
}
