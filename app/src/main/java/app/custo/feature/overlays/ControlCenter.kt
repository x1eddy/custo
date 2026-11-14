package app.custo.feature.overlays

import androidx.core.net.toUri
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.AlarmClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.Preferences
import app.custo.container
import app.custo.feature.systemui.DndMode
import app.custo.feature.widgets.MediaRepo
import app.custo.shizuku.ShellCommands
import app.custo.shizuku.ShizukuState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch

/** Asks the overlay service to open the Control Center (used by the settings screen's preview). */
object ControlCenterBus {
    val open = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}

/**
 * iOS-style Control Center. While enabled only a thin touch strip on the top edge exists; the
 * full-screen panel window is added when it opens and removed as soon as it closes.
 */
class ControlCenterOverlay(host: OverlayHost) : Overlay(host) {

    private var trigger: CcTriggerView? = null
    private var panel: ControlCenterView? = null
    private var built: List<Any?>? = null
    private var enabled = false
    private val sys by lazy { CcSystem(ctx) }
    private val refresh = Runnable { panel?.let { it.state = readState() } }

    override fun apply(p: Preferences) {
        enabled = p[OverlayKeys.cc] == true
        if (!enabled) { remove(); return }
        val zone = enumOr(p[OverlayKeys.ccZone], CcZone.RIGHT)
        val frac = p[OverlayKeys.ccZoneWidth] ?: 0.3f
        val show = p[OverlayKeys.ccShowZone] == true
        val sw = screen().width()
        val key = listOf(zone, frac, show, host.accent(), sw)
        if (key == built && trigger != null) return
        drop(trigger); trigger = null
        built = key
        val (l, r) = CcLogic.zoneSpan(sw, zone, frac)
        val tint = if (show) (host.accent() and 0x00FFFFFF) or 0x80000000.toInt() else 0
        val v = CcTriggerView(ctx, tint, dp(28f)) { open() }
        val lp = params(r - l, statusBarHeight().coerceAtMost(dpi(32f)), touchable = true).apply {
            gravity = Gravity.TOP or Gravity.START
            x = l; y = 0
        }
        if (add(v, lp)) trigger = v
    }

    override val wantsScreen: Boolean get() = enabled
    override fun onScreen(on: Boolean) { if (!on) closeNow() }

    /** Opens the panel. Works even while the top-edge trigger is off, e.g. from an edge gesture. */
    fun open() {
        if (panel != null) return
        val p = host.prefs ?: return
        val tiles = CcLogic.decode(p[OverlayKeys.ccTiles]).ifEmpty { CcLogic.DEFAULT }
        val skin = enumOr(p[OverlayKeys.ccSkin], CcSkin.DARK)
        val radius = dp(p[OverlayKeys.ccRadiusDp] ?: 22f)
        if (CcTile.FLASHLIGHT in tiles) host.actions.watchTorch()
        val v = ControlCenterView(
            ctx, tiles, CcColors.of(skin, host.accent()), radius, statusBarHeight() + dp(12f),
            onHit = ::onHit, onSlide = ::onSlide, onClose = { close() },
        )
        val lp = params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, touchable = true).apply {
            if (wm.isCrossWindowBlurEnabled) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = dpi(24f)
            }
        }
        v.state = readState(p)
        if (!add(v, lp)) return
        panel = v
        v.show()
    }

    private fun close(then: (() -> Unit)? = null) {
        val v = panel ?: return
        panel = null
        host.handler.removeCallbacks(refresh)
        v.hide { drop(v); then?.invoke() }
    }

    /** Removes the panel at once so actions like screenshots don't capture it. */
    private fun closeNow() {
        host.handler.removeCallbacks(refresh)
        drop(panel); panel = null
    }

    override fun remove() {
        closeNow()
        drop(trigger); trigger = null
        built = null
    }

    private fun readState(p: Preferences? = host.prefs): CcState {
        val apps = OverlayLogic.csv(p?.get(OverlayKeys.ccApps)).take(CcLogic.MAX_APPS)
            .mapNotNull { pkg -> Apps.icon(ctx, pkg)?.let { pkg to it } }
        val media = MediaRepo.nowPlaying(ctx)
        return CcState(
            wifi = sys.wifiOn(), bluetooth = sys.bluetoothOn(), torch = host.actions.isTorchOn,
            dnd = sys.dndOn(), rotation = sys.rotationOn(),
            brightness = CcLogic.brightnessToSlider(sys.brightness()), volume = sys.volume(),
            mediaTitle = media?.title?.ifBlank { null } ?: media?.let { Apps.label(ctx, it.pkg) },
            mediaArtist = media?.artist ?: "", mediaArt = media?.art, mediaPlaying = media?.playing == true,
            mediaAccess = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName),
            apps = apps,
        )
    }

    /** Re-reads state shortly after an action, once the system has caught up. */
    private fun refreshSoon() {
        host.handler.removeCallbacks(refresh)
        host.handler.postDelayed(refresh, 350)
    }

    private fun onHit(hit: CcHit) {
        when (hit) {
            is CcHit.App -> close { host.actions.launchApp(hit.pkg) }
            is CcHit.Media -> when (hit.cmd) {
                "open" -> if (panel?.state?.mediaAccess == false) close { sys.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    else MediaRepo.controller(ctx)?.packageName?.let { pkg -> close { host.actions.launchApp(pkg) } }
                else -> { MediaRepo.command(ctx, hit.cmd); refreshSoon() }
            }
            is CcHit.Tile -> onTile(hit.tile)
        }
    }

    private fun onTile(t: CcTile) {
        when (t) {
            CcTile.WIFI -> close { sys.start(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }
            CcTile.BLUETOOTH -> close { sys.start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            CcTile.FLASHLIGHT -> { host.actions.perform(OverlayAction.FLASHLIGHT); refreshSoon() }
            CcTile.DND -> when (sys.toggleDnd()) {
                CcSystem.Result.DONE -> refreshSoon()
                CcSystem.Result.NEEDS_ACCESS -> close {
                    toast("Allow Do Not Disturb access for Custo")
                    sys.start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
            CcTile.ROTATION -> when (sys.setRotation(!sys.rotationOn())) {
                CcSystem.Result.DONE -> refreshSoon()
                CcSystem.Result.NEEDS_ACCESS -> close { askWriteSettings() }
            }
            CcTile.SCREENSHOT -> { closeNow(); host.actions.perform(OverlayAction.SCREENSHOT) }
            CcTile.RECORD -> close {
                host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                toast("Tap Screen record")
            }
            CcTile.CALCULATOR -> close { sys.start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)) }
            CcTile.CAMERA -> close { host.actions.perform(OverlayAction.CAMERA) }
            CcTile.ALARM -> close { sys.start(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
            CcTile.BRIGHTNESS, CcTile.VOLUME, CcTile.MEDIA, CcTile.APPS -> Unit
        }
    }

    private fun onSlide(t: CcTile, v: Float, final: Boolean) {
        when (t) {
            CcTile.VOLUME -> sys.setVolume(v)
            CcTile.BRIGHTNESS -> if (sys.setBrightness(CcLogic.sliderToBrightness(v), final) == CcSystem.Result.NEEDS_ACCESS && final) close { askWriteSettings() }
            else -> Unit
        }
    }

    private fun askWriteSettings() {
        toast("Allow Custo to change system settings")
        sys.start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${ctx.packageName}".toUri()))
    }

    private fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
}

/** Reads and changes the system state behind the tiles, using the gentlest route available. */
class CcSystem(private val ctx: Context) {
    enum class Result { DONE, NEEDS_ACCESS }

    private val cr get() = ctx.contentResolver
    private val audio by lazy { ctx.getSystemService(AudioManager::class.java) }
    private val nm by lazy { ctx.getSystemService(NotificationManager::class.java) }
    private val c get() = ctx.container
    private val shizukuReady get() = c.shizuku.state.value == ShizukuState.READY

    fun start(i: Intent) = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    fun wifiOn(): Boolean = runCatching { ctx.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false)
    fun bluetoothOn(): Boolean = Settings.Global.getInt(cr, Settings.Global.BLUETOOTH_ON, 0) == 1
    fun dndOn(): Boolean = nm.currentInterruptionFilter.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }
    fun rotationOn(): Boolean = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    fun brightness(): Int = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)

    fun volume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    fun setVolume(f: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, CcLogic.sliderToSteps(f, max), 0) }
    }

    /** Uses Do Not Disturb access if granted, else Shizuku. */
    fun toggleDnd(): Result {
        val on = dndOn()
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            return Result.DONE
        }
        if (!shizukuReady) return Result.NEEDS_ACCESS
        c.appScope.launch { runCatching { c.tweaks.setDnd(if (on) DndMode.OFF else DndMode.PRIORITY) } }
        return Result.DONE
    }

    fun setRotation(on: Boolean): Result = writeSystem(Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0, final = true)

    /** Writes live while dragging with "modify system settings"; through Shizuku only when the finger lifts. */
    fun setBrightness(v: Int, final: Boolean): Result {
        // Auto brightness would ignore the new level, so switch it off like the system slider does.
        val auto = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        if (auto && final) writeSystem(Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, true)
        return writeSystem(Settings.System.SCREEN_BRIGHTNESS, v, final)
    }

    private fun writeSystem(key: String, value: Int, final: Boolean): Result {
        if (Settings.System.canWrite(ctx)) {
            runCatching { Settings.System.putInt(cr, key, value) }
            return Result.DONE
        }
        if (!shizukuReady) return Result.NEEDS_ACCESS
        if (final) c.appScope.launch {
            runCatching { c.shizuku.run(ShellCommands.settingsPut(ShellCommands.Namespace.SYSTEM, key, value.toString())) }
        }
        return Result.DONE
    }
}
