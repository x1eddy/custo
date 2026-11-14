package app.custo

import app.custo.feature.overlays.DoubleTapDetector
import app.custo.feature.overlays.EdgeGesture
import app.custo.feature.overlays.EdgeGestureClassifier
import app.custo.feature.overlays.OverlayLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OverlayLogicTest {

    @Test fun quietHoursSameDay() {
        assertTrue(OverlayLogic.inQuietHours(13 * 60, 12 * 60, 14 * 60))
        assertFalse(OverlayLogic.inQuietHours(14 * 60, 12 * 60, 14 * 60))
        assertFalse(OverlayLogic.inQuietHours(11 * 60, 12 * 60, 14 * 60))
    }

    @Test fun quietHoursWrapMidnight() {
        assertTrue(OverlayLogic.inQuietHours(23 * 60, 22 * 60, 7 * 60))
        assertTrue(OverlayLogic.inQuietHours(3 * 60, 22 * 60, 7 * 60))
        assertFalse(OverlayLogic.inQuietHours(12 * 60, 22 * 60, 7 * 60))
        assertFalse(OverlayLogic.inQuietHours(10, 600, 600))
    }

    @Test fun rulesRoundTrip() {
        val rules = mapOf("com.a" to OverlayLogic.IGNORE, "com.b" to 0xFF112233.toInt())
        val s = OverlayLogic.encodeRules(rules)
        assertEquals("com.a=ignore;com.b=#FF112233", s)
        assertEquals(rules, OverlayLogic.decodeRules(s))
        assertEquals(emptyMap<String, Int>(), OverlayLogic.decodeRules(null))
        assertEquals(mapOf("x" to OverlayLogic.IGNORE), OverlayLogic.decodeRules("junk;x=ignore;=1;y=#zz"))
    }

    @Test fun csvRoundTrip() {
        assertEquals(listOf("a", "b"), OverlayLogic.csv("a, b,,a"))
        assertEquals("a,b", OverlayLogic.csv(listOf("a", "b", "a")))
        val colors = listOf(0xFF6D4AFF.toInt(), 0x00000001)
        assertEquals(colors, OverlayLogic.colorsCsv(OverlayLogic.colorsCsv(colors)))
    }

    @Test fun batteryGradientEnds() {
        assertEquals(0xFFFF1744.toInt(), OverlayLogic.batteryGradient(0))
        assertEquals(0xFFFFC400.toInt(), OverlayLogic.batteryGradient(50))
        assertEquals(0xFF00E676.toInt(), OverlayLogic.batteryGradient(100))
    }

    @Test fun dominantColorPrefersColorfulPixels() {
        val red = 0xFFFF0000.toInt(); val white = 0xFFFFFFFF.toInt(); val clear = 0x00000000
        val px = IntArray(100) { i -> when { i < 20 -> red; i < 70 -> white; else -> clear } }
        val c = OverlayLogic.dominantColor(px, 0)
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF
        assertTrue("expected reddish, got ${Integer.toHexString(c)}", r > 200 && g < 120)
        assertEquals(7, OverlayLogic.dominantColor(IntArray(4), 7))
    }

    @Test fun formatters() {
        assertEquals("0 K", OverlayLogic.formatRate(500))
        assertEquals("12 K", OverlayLogic.formatRate(12 * 1024))
        assertEquals("1.5 M", OverlayLogic.formatRate(1536 * 1024))
        assertEquals("25 M", OverlayLogic.formatRate(25L * 1024 * 1024))
        assertEquals("0:00", OverlayLogic.formatElapsed(-5))
        assertEquals("1:05", OverlayLogic.formatElapsed(65_000))
        assertEquals("1:00:01", OverlayLogic.formatElapsed(3_601_000))
        assertEquals("07:05", OverlayLogic.formatMinute(7 * 60 + 5))
    }

    @Test fun springSettlesAtOne() {
        assertEquals(0f, OverlayLogic.spring(0f), 0f)
        assertEquals(1f, OverlayLogic.spring(1f), 0f)
        assertTrue(abs(OverlayLogic.spring(0.95f) - 1f) < 0.05f)
        // It overshoots a little somewhere in between.
        assertTrue((1..99).any { OverlayLogic.spring(it / 100f) > 1f })
    }

    @Test fun edgeSwipeClassifier() {
        // Left edge: inward is +x.
        assertEquals(EdgeGesture.SWIPE, EdgeGestureClassifier.classify(60f, 10f, 150, 1, 40f, 8f))
        assertEquals(EdgeGesture.NONE, EdgeGestureClassifier.classify(-60f, 0f, 150, 1, 40f, 8f))
        assertEquals(EdgeGesture.NONE, EdgeGestureClassifier.classify(45f, 120f, 150, 1, 40f, 8f)) // mostly vertical
        assertEquals(EdgeGesture.TAP, EdgeGestureClassifier.classify(2f, 3f, 120, 1, 40f, 8f))
        assertEquals(EdgeGesture.NONE, EdgeGestureClassifier.classify(2f, 3f, 900, 1, 40f, 8f)) // held, not a tap
        // Right edge: inward is -x.
        assertEquals(EdgeGesture.SWIPE, EdgeGestureClassifier.classify(-60f, 0f, 150, -1, 40f, 8f))
        // Bottom corner: up counts.
        assertEquals(EdgeGesture.SWIPE, EdgeGestureClassifier.classifyCorner(0f, -50f, 150, 1, 40f, 8f))
    }

    @Test fun doubleTap() {
        val d = DoubleTapDetector(maxGapMs = 300, maxTapMs = 250, slopPx = 20f)
        d.onDown(0, 10f, 10f); assertFalse(d.onUp(80, 10f, 10f))
        d.onDown(200, 12f, 10f); assertTrue(d.onUp(260, 12f, 10f))
        // Too slow between taps.
        d.onDown(1000, 10f, 10f); assertFalse(d.onUp(1050, 10f, 10f))
        d.onDown(1500, 10f, 10f); assertFalse(d.onUp(1550, 10f, 10f))
        // A drag in between resets.
        d.onDown(1600, 10f, 10f); assertFalse(d.onUp(1650, 100f, 10f))
        d.onDown(1700, 10f, 10f); assertFalse(d.onUp(1750, 10f, 10f))
    }
}
