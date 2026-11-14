package app.custo.feature.overlays

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.datastore.preferences.core.Preferences
import app.custo.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Hosts every Custo overlay. Accessibility overlays need no "draw over apps" permission and
 * no foreground notification, and this service is the only thing running in the background.
 * Each feature adds its windows only while it is enabled, and receivers are registered only
 * while some feature needs them.
 */
class CustoAccessibilityService : AccessibilityService(), OverlayHost {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override val service: AccessibilityService get() = this
    override lateinit var wm: WindowManager
    override val handler = Handler(Looper.getMainLooper())
    override lateinit var actions: ActionPerformer
    override var prefs: Preferences? = null
    private var screenOnCached = true
    /** Cached from screen broadcasts while some feature listens for them, else asked directly. */
    override val isScreenOn: Boolean
        get() = if (screenReceiver != null) screenOnCached else !::power.isInitialized || power.isInteractive

    private lateinit var power: PowerManager
    private lateinit var keys: VolumeKeys
    private lateinit var overlays: List<Overlay>
    private lateinit var edge: EdgeLightingOverlay
    private lateinit var notifLight: NotificationLightOverlay
    private lateinit var island: DynamicIslandOverlay

    private var screenReceiver: BroadcastReceiver? = null
    private var powerReceiver: BroadcastReceiver? = null
    private var lastOrientation = 0

    override fun dp(v: Float) = v * resources.displayMetrics.density
    override fun accent() = getColor(android.R.color.system_accent1_400)

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        power = getSystemService(PowerManager::class.java)
        lastOrientation = resources.configuration.orientation
        actions = ActionPerformer(this)
        edge = EdgeLightingOverlay(this)
        notifLight = NotificationLightOverlay(this)
        island = DynamicIslandOverlay(this)
        val volume = VolumePanelOverlay(this)
        keys = VolumeKeys(this, volume)
        // Order matters a little: later windows draw on top of earlier ones.
        overlays = listOf(
            ScreenTintOverlay(this), RoundedCornersOverlay(this), BatteryBarOverlay(this), NetSpeedOverlay(this),
            notifLight, StatusBarTapOverlay(this), EdgeGesturesOverlay(this), EdgeSidebarOverlay(this),
            island, edge, ChargingAnimationOverlay(this), volume,
        )
        OverlayBus.serviceRunning.value = true
        scope.launch { container.prefs.data.collectLatest { p -> prefs = p; applyPrefs(p) } }
        scope.launch { OverlayBus.edge.collect { edge.play(it) } }
        scope.launch { OverlayBus.preview.collect { k -> overlays.forEach { it.preview(k) } } }
        scope.launch { OverlayBus.unread.collect { notifLight.setUnread(it) } }
        scope.launch { OverlayBus.islandNotif.collect { island.onNotification(it) } }
        scope.launch { OverlayBus.media.collect { island.setMedia(it) } }
        scope.launch { OverlayBus.timer.collect { island.setTimer(it) } }
    }

    private var lastPkg: String? = null
    private val ignoredPkgs by lazy {
        val ims = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .inputMethodList.map { it.packageName }.toSet()
        ims + setOf(packageName, "com.android.systemui", "android")
    }

    /** Foreground-app changes drive per-app profiles. Only window-state events are subscribed. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignoredPkgs || pkg == lastPkg) return
        lastPkg = pkg
        container.profileEngine.onForegroundApp(pkg)
    }
    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::overlays.isInitialized || newConfig.orientation == lastOrientation) return
        lastOrientation = newConfig.orientation
        overlays.forEach { it.onConfigChanged() }
    }

    override fun onDestroy() {
        OverlayBus.serviceRunning.value = false
        if (::overlays.isInitialized) {
            overlays.forEach { it.remove() }
            keys.release()
            actions.release()
        }
        unregister(screenReceiver); screenReceiver = null
        unregister(powerReceiver); powerReceiver = null
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    private fun applyPrefs(p: Preferences) {
        overlays.forEach { runCatching { it.apply(p) } }
        syncReceivers()
    }

    private fun syncReceivers() {
        if (overlays.any { it.wantsScreen }) {
            if (screenReceiver == null) {
                screenOnCached = power.isInteractive
                val r = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, i: Intent) {
                        screenOnCached = i.action != Intent.ACTION_SCREEN_OFF
                        overlays.forEach { it.onScreen(screenOnCached) }
                    }
                }
                screenReceiver = r
                registerReceiver(r, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
            }
        } else {
            unregister(screenReceiver); screenReceiver = null
        }
        if (overlays.any { it.wantsPower }) {
            if (powerReceiver == null) {
                val r = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, i: Intent) {
                        val level = ChargingAnimationOverlay.currentLevel(c)
                        overlays.forEach { it.onPowerConnected(level) }
                    }
                }
                powerReceiver = r
                registerReceiver(r, IntentFilter(Intent.ACTION_POWER_CONNECTED))
            }
        } else {
            unregister(powerReceiver); powerReceiver = null
        }
    }

    private fun unregister(r: BroadcastReceiver?) { r?.let { runCatching { unregisterReceiver(it) } } }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::keys.isInitialized) return false
        return keys.onKeyEvent(event)
    }
}
