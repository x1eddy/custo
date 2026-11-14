package app.custo.feature.overlays

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.RectF
import android.os.BatteryManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.datastore.preferences.core.Preferences
import app.custo.data.Keys

/** Battery bar: listens to battery changes only while it is enabled. */
class BatteryBarOverlay(host: OverlayHost) : Overlay(host) {

    private var view: BatteryBarView? = null
    private var receiver: BroadcastReceiver? = null
    private var below = 100

    override fun apply(p: Preferences) {
        if (p[Keys.batteryBarEnabled] != true) { remove(); return }
        val style = enumOr(p[Keys.batteryBarStyle], BatteryBarStyle.TOP)
        val position = enumOr(p[OverlayKeys.batteryPosition], BarPosition.TOP)
        val thick = dp(p[Keys.batteryBarThicknessDp] ?: 3f)
        val cutoutRect = topCutout()
        val useCutout = style == BatteryBarStyle.CUTOUT && cutoutRect != null
        val height = if (useCutout) (cutoutRect.bottom + thick * 4).toInt() else thick.toInt().coerceAtLeast(1)
        val gravity = if (useCutout || position == BarPosition.TOP) Gravity.TOP else Gravity.BOTTOM
        below = p[OverlayKeys.batteryBelow] ?: 100

        val view = view ?: BatteryBarView(ctx).also { v ->
            if (!add(v, params(WindowManager.LayoutParams.MATCH_PARENT, height).apply { this.gravity = gravity })) return
            view = v
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    v.level = level
                    v.charging = st == BatteryManager.BATTERY_STATUS_CHARGING
                    updateVisibility(v)
                }
            }
            receiver = r
            ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        view.style = if (useCutout) BatteryBarStyle.CUTOUT else BatteryBarStyle.TOP
        view.thicknessPx = thick
        view.accent = host.accent()
        view.colorMode = enumOr(p[OverlayKeys.batteryColorMode], BarColorMode.LEVEL)
        view.customColor = p[OverlayKeys.batteryColor] ?: 0xFF00E5FF.toInt()
        view.segmented = p[OverlayKeys.batterySegmented] == true
        view.cutout = cutoutRect?.let { RectF(it) }
        (view.layoutParams as? WindowManager.LayoutParams)?.let {
            if (it.height != height || it.gravity != gravity) { it.height = height; it.gravity = gravity; update(view, it) }
        }
        updateVisibility(view)
        view.invalidate()
    }

    private fun updateVisibility(v: BatteryBarView) {
        v.visibility = if (below >= 100 || v.level < below) View.VISIBLE else View.GONE
    }

    override fun remove() {
        receiver?.let { runCatching { ctx.unregisterReceiver(it) } }
        receiver = null
        drop(view); view = null
    }
}
