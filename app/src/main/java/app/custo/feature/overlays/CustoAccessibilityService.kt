package app.custo.feature.overlays

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.RectF
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.datastore.preferences.core.Preferences
import app.custo.container
import app.custo.data.Keys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Hosts every Custo overlay. Accessibility overlays need no "draw over apps" permission and
 * no foreground notification, and this service is the only thing running in the background.
 */
class CustoAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private lateinit var audio: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var prefs: Preferences? = null

    private var batteryView: BatteryBarView? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var edgeView: EdgeLightView? = null
    private var volumeView: VolumePanelView? = null
    private var volumeStream = AudioManager.STREAM_MUSIC
    private val hideVolume = Runnable { removeVolume() }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun accent() = getColor(android.R.color.system_accent1_400)

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        audio = getSystemService(AudioManager::class.java)
        OverlayBus.serviceRunning.value = true
        scope.launch { container.prefs.data.collectLatest { p -> prefs = p; applyPrefs(p) } }
        scope.launch { OverlayBus.edge.collect { color -> flashEdges(color) } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        OverlayBus.serviceRunning.value = false
        removeBattery(); removeEdge(); removeVolume()
        scope.cancel()
        super.onDestroy()
    }

    private fun baseParams(w: Int, h: Int, touchable: Boolean = false) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        setFitInsetsTypes(0)
    }

    private fun applyPrefs(p: Preferences) {
        // Battery bar
        if (p[Keys.batteryBarEnabled] == true) showBattery(p) else removeBattery()
        // Volume panel: key filtering is checked live in onKeyEvent.
        if (p[Keys.volumePanelEnabled] != true) removeVolume()
    }

    // ---------------- Battery bar ----------------

    private fun showBattery(p: Preferences) {
        val style = runCatching { BatteryBarStyle.valueOf(p[Keys.batteryBarStyle] ?: "TOP") }.getOrDefault(BatteryBarStyle.TOP)
        val thick = dp(p[Keys.batteryBarThicknessDp] ?: 3f)
        val cutoutRect = wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRects?.firstOrNull()
        val useCutout = style == BatteryBarStyle.CUTOUT && cutoutRect != null
        val height = if (useCutout) (cutoutRect!!.bottom + thick * 4).toInt() else thick.toInt().coerceAtLeast(1)

        val view = batteryView ?: BatteryBarView(this).also { v ->
            batteryView = v
            wm.addView(v, baseParams(WindowManager.LayoutParams.MATCH_PARENT, height).apply { gravity = Gravity.TOP })
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    v.level = level
                    v.charging = st == BatteryManager.BATTERY_STATUS_CHARGING
                }
            }
            batteryReceiver = r
            registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        view.style = if (useCutout) BatteryBarStyle.CUTOUT else BatteryBarStyle.TOP
        view.thicknessPx = thick
        view.accent = accent()
        view.cutout = cutoutRect?.let { RectF(it) }
        (view.layoutParams as? WindowManager.LayoutParams)?.let {
            if (it.height != height) { it.height = height; wm.updateViewLayout(view, it) }
        }
        view.invalidate()
    }

    private fun removeBattery() {
        batteryReceiver?.let { runCatching { unregisterReceiver(it) } }
        batteryReceiver = null
        batteryView?.let { runCatching { wm.removeView(it) } }
        batteryView = null
    }

    // ---------------- Edge lighting ----------------

    private fun flashEdges(requested: Int?) {
        val p = prefs ?: return
        val mode = runCatching { EdgeColorMode.valueOf(p[Keys.edgeColorMode] ?: "APP") }.getOrDefault(EdgeColorMode.APP)
        val colors = when (mode) {
            EdgeColorMode.RAINBOW -> intArrayOf(0xFFFF1744.toInt(), 0xFFFFEA00.toInt(), 0xFF00E676.toInt(), 0xFF00B0FF.toInt(), 0xFFD500F9.toInt())
            EdgeColorMode.ACCENT -> intArrayOf(accent())
            EdgeColorMode.APP -> intArrayOf(requested?.takeIf { it != 0 } ?: accent())
        }
        val view = edgeView ?: EdgeLightView(this).also {
            edgeView = it
            wm.addView(it, baseParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT))
        }
        view.strokePx = dp(p[Keys.edgeWidthDp] ?: 4f)
        view.onFinished = { removeEdge() }
        view.play(colors, (p[Keys.edgeDurationMs] ?: 2500).toLong())
    }

    private fun removeEdge() {
        edgeView?.let { runCatching { wm.removeView(it) } }
        edgeView = null
    }

    // ---------------- Volume panel ----------------

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val p = prefs ?: return false
        if (p[Keys.volumePanelEnabled] != true) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        // Never interfere with calls.
        if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            volumeStream = if (audio.isMusicActive) AudioManager.STREAM_MUSIC else AudioManager.STREAM_RING
            val dir = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            try {
                audio.adjustStreamVolume(volumeStream, dir, 0)
            } catch (_: SecurityException) {
                // Changing ringer volume in Do Not Disturb can be refused; fall back to media.
                volumeStream = AudioManager.STREAM_MUSIC
                audio.adjustStreamVolume(volumeStream, dir, 0)
            }
            showVolume(p)
        }
        return true
    }

    private fun currentFraction(): Float {
        val max = audio.getStreamMaxVolume(volumeStream).coerceAtLeast(1)
        return audio.getStreamVolume(volumeStream) / max.toFloat()
    }

    private fun showVolume(p: Preferences) {
        val side = runCatching { PanelSide.valueOf(p[Keys.volumePanelSide] ?: "RIGHT") }.getOrDefault(PanelSide.RIGHT)
        val view = volumeView ?: VolumePanelView(
            this,
            onSet = { f ->
                val max = audio.getStreamMaxVolume(volumeStream)
                runCatching { audio.setStreamVolume(volumeStream, (f * max).toInt(), 0) }
                volumeView?.fraction = currentFraction()
            },
            onTouch = { scheduleHide() },
        ).also { v ->
            volumeView = v
            val params = baseParams(dp(56f).toInt(), dp(240f).toInt(), touchable = true).apply {
                gravity = (if (side == PanelSide.RIGHT) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
                x = dp(14f).toInt()
            }
            v.alpha = 0f
            v.translationX = if (side == PanelSide.RIGHT) dp(40f) else -dp(40f)
            wm.addView(v, params)
            v.animate().alpha(1f).translationX(0f).setDuration(180).start()
        }
        view.accent = accent()
        view.label = if (volumeStream == AudioManager.STREAM_MUSIC) "♪" else "🔔"
        view.fraction = currentFraction()
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideVolume)
        handler.postDelayed(hideVolume, 2200)
    }

    private fun removeVolume() {
        handler.removeCallbacks(hideVolume)
        val v = volumeView ?: return
        volumeView = null
        v.animate().alpha(0f).setDuration(150).withEndAction { runCatching { wm.removeView(v) } }.start()
    }
}
