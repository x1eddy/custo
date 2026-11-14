package app.custo.feature.overlays

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import app.custo.data.Keys

/**
 * Volume key filter: custom panel, double/long press actions, long press to skip tracks.
 * Keys are only taken over when a feature needs them, never during calls.
 */
class VolumeKeys(private val host: OverlayHost, private val panel: VolumePanelOverlay) {

    private val audio: AudioManager = host.service.getSystemService(AudioManager::class.java)
    private val up = Slot(isUp = true)
    private val down = Slot(isUp = false)

    private inner class Slot(val isUp: Boolean) {
        val detector = PressPatternDetector()
        var plan = VolumeKeyPolicy.PASS
        val timeout = Runnable { handle(this, detector.onTimeout(SystemClock.uptimeMillis())) }
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val slot = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> up
            KeyEvent.KEYCODE_VOLUME_DOWN -> down
            else -> return false
        }
        val p = host.prefs ?: return false
        val inCall = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION ||
            audio.mode == AudioManager.MODE_RINGTONE
        if (inCall) { slot.detector.reset(); slot.plan = VolumeKeyPolicy.PASS; return false }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && slot.detector.isIdle) {
            val remapOn = p[OverlayKeys.buttons] == true
            slot.plan = VolumeKeyPolicy.plan(
                isUp = slot.isUp,
                inCall = false,
                panelOn = p[Keys.volumePanelEnabled] == true,
                remapOn = remapOn,
                doubleAction = OverlayAction.of(p[if (slot.isUp) OverlayKeys.upDouble else OverlayKeys.downDouble]),
                longAction = OverlayAction.of(p[if (slot.isUp) OverlayKeys.upLong else OverlayKeys.downLong]),
                longSkip = p[OverlayKeys.volumeLongSkip] == true,
                musicActive = audio.isMusicActive,
                screenOn = host.isScreenOn,
            )
        }
        val plan = slot.plan
        if (!plan.consume) return false

        val now = event.eventTime
        val immediate = plan.double == OverlayAction.NONE && plan.long == OverlayAction.NONE
        val result = when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // Holding the key with nothing else assigned keeps stepping the volume.
                if (event.repeatCount > 0) { if (immediate) single(slot); return true }
                slot.detector.onDown(now, plan.double != OverlayAction.NONE, plan.long != OverlayAction.NONE)
            }
            KeyEvent.ACTION_UP -> slot.detector.onUp(now)
            else -> null
        }
        handle(slot, result)
        return true
    }

    private fun handle(slot: Slot, press: Press?) {
        host.handler.removeCallbacks(slot.timeout)
        when (press) {
            Press.SINGLE -> single(slot)
            Press.DOUBLE -> host.actions.perform(slot.plan.double)
            Press.LONG -> host.actions.perform(slot.plan.long)
            null -> Unit
        }
        slot.detector.deadline?.let { at -> host.handler.postAtTime(slot.timeout, at) }
    }

    private fun single(slot: Slot) {
        if (host.prefs?.get(Keys.volumePanelEnabled) == true) {
            panel.step(slot.isUp)
        } else {
            // Feature only remaps double/long: a single press still changes volume with the system panel.
            audio.adjustSuggestedStreamVolume(
                if (slot.isUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI,
            )
        }
    }

    fun release() {
        host.handler.removeCallbacks(up.timeout)
        host.handler.removeCallbacks(down.timeout)
    }
}
