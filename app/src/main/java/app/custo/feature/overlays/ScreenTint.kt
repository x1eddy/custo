package app.custo.feature.overlays

import android.view.View
import android.view.WindowManager
import androidx.datastore.preferences.core.Preferences

/** A full-screen, non-touchable color layer. */
class ScreenTintOverlay(host: OverlayHost) : Overlay(host) {

    private var view: View? = null

    override fun apply(p: Preferences) {
        if (p[OverlayKeys.tint] != true) { remove(); return }
        val opacity = (p[OverlayKeys.tintOpacity] ?: 0.3f).coerceIn(0f, MAX_OPACITY)
        val color = ((p[OverlayKeys.tintColor] ?: 0xFF000000.toInt()) and 0x00FFFFFF) or ((opacity * 255).toInt() shl 24)
        val v = view ?: View(ctx).also {
            if (!add(it, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT))) return
            view = it
        }
        v.setBackgroundColor(color)
    }

    override fun remove() { drop(view); view = null }

    // Only for tinting; keeps text readable and stays below what blocks touches on some ROMs.
    companion object { const val MAX_OPACITY = 0.8f }
}
