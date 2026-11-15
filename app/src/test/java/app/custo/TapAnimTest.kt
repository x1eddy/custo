package app.custo

import app.custo.feature.widgets.TapFrames
import app.custo.feature.widgets.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapAnimTest {

    private val frames = 1..TapFrames.COUNT

    @Test fun effectIsShortAndFinite() {
        assertTrue(TapFrames.COUNT in 2..4)
        assertTrue(TapFrames.INTERVAL_MS in 50L..100L)
        assertEquals(TapFrames.COUNT * TapFrames.INTERVAL_MS, TapFrames.TOTAL_MS)
        assertTrue("whole effect must stay under 400 ms", TapFrames.TOTAL_MS < 400)
    }

    @Test fun restFrameIsNeutral() {
        for (f in listOf(0, -1, TapFrames.COUNT + 1)) {
            assertEquals(1f, TapFrames.pop(f))
            assertEquals(1f, TapFrames.pop(f, 0.5f))
            assertEquals(0f, TapFrames.rise(f))
            assertEquals(0f, TapFrames.fade(f))
            assertEquals(0f, TapFrames.flash(f))
            assertEquals(1f, TapFrames.reveal(f))
            assertEquals(0f, TapFrames.wobble(f))
            assertEquals(1f, TapFrames.slide(f))
            assertEquals(1f, TapFrames.stretch(f))
            assertEquals(TapFrames.Burst.NONE, TapFrames.burst(f))
        }
    }

    @Test fun popSwellsFirstThenSettlesNearRest() {
        assertTrue(TapFrames.pop(1) > 1.15f)
        assertTrue(TapFrames.pop(2) < 1f)
        assertEquals(1f, TapFrames.pop(TapFrames.COUNT), 0.05f)
        frames.forEach { assertTrue(TapFrames.pop(it) in 0.8f..1.35f) }
        // Toned-down pops move toward 1.
        assertEquals(1f + (TapFrames.pop(1) - 1f) / 2f, TapFrames.pop(1, 0.5f), 1e-6f)
        assertEquals(1f, TapFrames.pop(1, 0f))
    }

    @Test fun floatingLabelRisesAndFades() {
        frames.zipWithNext().forEach { (a, b) ->
            assertTrue(TapFrames.rise(b) > TapFrames.rise(a))
            assertTrue(TapFrames.fade(b) < TapFrames.fade(a))
        }
        frames.forEach { assertTrue(TapFrames.rise(it) in 0f..1f) }
    }

    @Test fun flashFadesAndRevealGrows() {
        frames.zipWithNext().forEach { (a, b) ->
            assertTrue(TapFrames.flash(b) < TapFrames.flash(a))
            assertTrue(TapFrames.reveal(b) > TapFrames.reveal(a))
        }
        assertTrue(TapFrames.flash(1) <= 0.35f)
    }

    @Test fun slideReachesTheNewSideWithoutOvershoot() {
        frames.zipWithNext().forEach { (a, b) -> assertTrue(TapFrames.slide(b) >= TapFrames.slide(a)) }
        frames.forEach { assertTrue(TapFrames.slide(it) in 0f..1f) }
        assertEquals(1f, TapFrames.slide(TapFrames.COUNT))
        assertTrue(TapFrames.stretch(2) > 1f)
    }

    @Test fun wobbleAlternatesAndDies() {
        frames.zipWithNext().forEach { (a, b) -> assertTrue(TapFrames.wobble(a) * TapFrames.wobble(b) < 0) }
        assertTrue(Math.abs(TapFrames.wobble(TapFrames.COUNT)) < Math.abs(TapFrames.wobble(1)))
    }

    @Test fun bubbleBurstSequence() {
        assertEquals(
            listOf(TapFrames.Burst.SWELL, TapFrames.Burst.BURST, TapFrames.Burst.SHARDS, TapFrames.Burst.NONE),
            frames.map { TapFrames.burst(it) },
        )
    }

    @Test fun staleOrFutureFramesAreIgnored() {
        val t = 1_000_000L
        assertEquals(2, TapFrames.live(2, t, t + 100))
        assertEquals(0, TapFrames.live(2, t, t + TapFrames.STALE_MS + 1))
        assertEquals(0, TapFrames.live(2, t, t - 5_000))
        assertEquals(0, TapFrames.live(0, t, t))
        assertEquals(0, TapFrames.live(TapFrames.COUNT + 1, t, t))
        assertTrue(TapFrames.STALE_MS > TapFrames.TOTAL_MS)
    }

    @Test fun tapAnimationIsOnByDefaultAndForOldWidgets() {
        assertTrue(WidgetStyle().tapAnim)
        // JSON from before the option existed.
        assertTrue(WidgetStyle.decode("""{"clock":"BIG","themed":true}""").tapAnim)
        val off = WidgetStyle(tapAnim = false)
        assertFalse(WidgetStyle.decode(off.encode()).tapAnim)
        // Style presets carry it like the rest of the look.
        assertFalse(WidgetStyle().withLookOf(off).tapAnim)
    }
}
