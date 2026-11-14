package app.custo

import app.custo.feature.overlays.OverlayAction
import app.custo.feature.overlays.Press
import app.custo.feature.overlays.PressPatternDetector
import app.custo.feature.overlays.VolumeKeyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PressPatternTest {
    private fun det() = PressPatternDetector(longMs = 450, doubleMs = 280)

    @Test fun noExtraActionsFiresSingleOnDownWithoutDelay() {
        val d = det()
        assertEquals(Press.SINGLE, d.onDown(0, canDouble = false, canLong = false))
        assertNull(d.deadline)
        assertNull(d.onUp(80))
        assertTrue(d.isIdle)
    }

    @Test fun repeatEventsAreIgnored() {
        val d = det()
        d.onDown(0, canDouble = false, canLong = false)
        assertNull(d.onDown(500, canDouble = false, canLong = false, repeat = true))
        assertNull(d.onUp(600))
    }

    @Test fun longOnlyShortPressIsSingleOnUp() {
        val d = det()
        assertNull(d.onDown(0, canDouble = false, canLong = true))
        assertEquals(450L, d.deadline)
        assertEquals(Press.SINGLE, d.onUp(120))
        assertTrue(d.isIdle)
    }

    @Test fun longFiresOnTimeoutAndUpIsSwallowed() {
        val d = det()
        d.onDown(0, canDouble = false, canLong = true)
        assertNull(d.onTimeout(300)) // early timeout does nothing
        assertEquals(Press.LONG, d.onTimeout(450))
        assertNull(d.deadline)
        assertNull(d.onUp(900))
        assertTrue(d.isIdle)
    }

    @Test fun longFiresOnLateUpIfTimeoutWasMissed() {
        val d = det()
        d.onDown(0, canDouble = false, canLong = true)
        assertEquals(Press.LONG, d.onUp(700))
    }

    @Test fun doublePress() {
        val d = det()
        assertNull(d.onDown(0, canDouble = true, canLong = false))
        assertNull(d.deadline) // no long action: no timeout while held
        assertNull(d.onUp(90))
        assertEquals(370L, d.deadline)
        assertEquals(Press.DOUBLE, d.onDown(200, canDouble = true, canLong = false))
        assertNull(d.onUp(260))
        assertTrue(d.isIdle)
    }

    @Test fun singleAfterDoubleWindowExpires() {
        val d = det()
        d.onDown(0, canDouble = true, canLong = true)
        d.onUp(100)
        assertNull(d.onTimeout(200))
        assertEquals(Press.SINGLE, d.onTimeout(380))
        assertTrue(d.isIdle)
    }

    @Test fun longWinsWhenBothConfigured() {
        val d = det()
        d.onDown(0, canDouble = true, canLong = true)
        assertEquals(Press.LONG, d.onTimeout(450))
        assertNull(d.onUp(800))
        assertTrue(d.isIdle)
    }

    @Test fun twoSeparatePressesAreTwoSingles() {
        val d = det()
        d.onDown(0, canDouble = true, canLong = false); d.onUp(50)
        assertEquals(Press.SINGLE, d.onTimeout(330))
        assertNull(d.onDown(1000, canDouble = true, canLong = false))
        d.onUp(1050)
        assertEquals(Press.SINGLE, d.onTimeout(1330))
    }

    @Test fun lostUpRecoversOnNextDown() {
        val d = det()
        d.onDown(0, canDouble = false, canLong = true)
        // No up arrived; a fresh down restarts the gesture instead of getting stuck.
        assertNull(d.onDown(2000, canDouble = false, canLong = true))
        assertEquals(Press.SINGLE, d.onUp(2100))
    }

    @Test fun policyPassesThroughWhenNothingIsOn() {
        val plan = VolumeKeyPolicy.plan(true, false, false, false, OverlayAction.NONE, OverlayAction.NONE, false, false, true)
        assertFalse(plan.consume)
    }

    @Test fun policyNeverConsumesInCall() {
        val plan = VolumeKeyPolicy.plan(true, true, true, true, OverlayAction.FLASHLIGHT, OverlayAction.LOCK, true, true, true)
        assertFalse(plan.consume)
    }

    @Test fun policyRemapOffIgnoresActions() {
        val plan = VolumeKeyPolicy.plan(true, false, false, false, OverlayAction.FLASHLIGHT, OverlayAction.LOCK, false, false, true)
        assertFalse(plan.consume)
    }

    @Test fun policyRemapActions() {
        val plan = VolumeKeyPolicy.plan(false, false, false, true, OverlayAction.FLASHLIGHT, OverlayAction.NONE, false, false, true)
        assertTrue(plan.consume)
        assertEquals(OverlayAction.FLASHLIGHT, plan.double)
        assertEquals(OverlayAction.NONE, plan.long)
    }

    @Test fun policyNavActionsAreNotAllowedOnKeys() {
        val plan = VolumeKeyPolicy.plan(false, false, false, true, OverlayAction.BACK, OverlayAction.HOME, false, false, true)
        assertFalse(plan.consume)
    }

    @Test fun policyLongSkipOnlyWithMusicAndScreenOn() {
        val on = VolumeKeyPolicy.plan(true, false, true, false, OverlayAction.NONE, OverlayAction.NONE, true, true, true)
        assertEquals(OverlayAction.NEXT_TRACK, on.long)
        val down = VolumeKeyPolicy.plan(false, false, true, false, OverlayAction.NONE, OverlayAction.NONE, true, true, true)
        assertEquals(OverlayAction.PREV_TRACK, down.long)
        val noMusic = VolumeKeyPolicy.plan(true, false, true, false, OverlayAction.NONE, OverlayAction.NONE, true, false, true)
        assertEquals(OverlayAction.NONE, noMusic.long)
        val screenOff = VolumeKeyPolicy.plan(true, false, true, false, OverlayAction.NONE, OverlayAction.NONE, true, true, false)
        assertEquals(OverlayAction.NONE, screenOff.long)
        assertTrue(screenOff.consume) // panel still handles it
    }

    @Test fun policyRemapLongBeatsSkip() {
        val plan = VolumeKeyPolicy.plan(true, false, true, true, OverlayAction.NONE, OverlayAction.SCREENSHOT, true, true, true)
        assertEquals(OverlayAction.SCREENSHOT, plan.long)
    }
}
