package app.custo.feature.overlays

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import app.custo.R

/** Every action a button, edge gesture or sidebar can trigger. Shared so they all behave the same. */
enum class OverlayAction(val label: String, val icon: Int, val forKeys: Boolean = true) {
    NONE("Nothing", 0),
    FLASHLIGHT("Flashlight", R.drawable.ov_flashlight),
    SCREENSHOT("Screenshot", R.drawable.ov_screenshot),
    LOCK("Lock screen", R.drawable.ov_lock),
    NOTIFICATIONS("Notifications", R.drawable.ov_notifications),
    QUICK_SETTINGS("Quick settings", R.drawable.ov_tune),
    PLAY_PAUSE("Play / pause", R.drawable.ic_play),
    NEXT_TRACK("Next track", R.drawable.ic_next),
    PREV_TRACK("Previous track", R.drawable.ic_prev),
    CAMERA("Open camera", R.drawable.ov_camera),
    BACK("Back", R.drawable.ov_back, forKeys = false),
    HOME("Home", R.drawable.ov_home, forKeys = false),
    RECENTS("Recents", R.drawable.ov_recents, forKeys = false);

    companion object {
        fun of(name: String?): OverlayAction = enumOr(name, NONE)
        val forButtons get() = entries.filter { it.forKeys }

        /** Quick actions offered in the sidebar. */
        val sidebarChoices = listOf(FLASHLIGHT, SCREENSHOT, LOCK, NOTIFICATIONS, QUICK_SETTINGS, PLAY_PAUSE, CAMERA)
    }
}

/** Runs [OverlayAction]s from inside the accessibility service. */
class ActionPerformer(private val service: AccessibilityService) {

    private val audio by lazy { service.getSystemService(AudioManager::class.java) }
    private val camera by lazy { service.getSystemService(CameraManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private var torchId: String? = null
    private var torchOn = false
    private var torchCallback: CameraManager.TorchCallback? = null

    fun perform(action: OverlayAction): Boolean = when (action) {
        OverlayAction.NONE -> false
        OverlayAction.FLASHLIGHT -> toggleTorch()
        // Let the overlay that triggered a screenshot get out of the picture first.
        OverlayAction.SCREENSHOT -> handler.postDelayed({ service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, 250).let { true }
        OverlayAction.LOCK -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        OverlayAction.NOTIFICATIONS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        OverlayAction.QUICK_SETTINGS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        OverlayAction.PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        OverlayAction.NEXT_TRACK -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        OverlayAction.PREV_TRACK -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        OverlayAction.CAMERA -> runCatching {
            service.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        OverlayAction.BACK -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        OverlayAction.HOME -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        OverlayAction.RECENTS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    fun launchApp(pkg: String): Boolean = runCatching {
        val i = service.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        service.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    private fun mediaKey(code: Int): Boolean {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        return true
    }

    private fun toggleTorch(): Boolean {
        val id = torchId ?: findTorch()?.also { torchId = it } ?: return false
        if (torchCallback == null) {
            // Keeps torchOn right when the flashlight is toggled elsewhere, e.g. from Quick Settings.
            val cb = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == torchId) torchOn = enabled }
            }
            torchCallback = cb
            camera.registerTorchCallback(cb, handler)
        }
        return runCatching { camera.setTorchMode(id, !torchOn); torchOn = !torchOn }.isSuccess
    }

    private fun findTorch(): String? = runCatching {
        camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    fun release() {
        handler.removeCallbacksAndMessages(null)
        torchCallback?.let { runCatching { camera.unregisterTorchCallback(it) } }
        torchCallback = null
    }
}
