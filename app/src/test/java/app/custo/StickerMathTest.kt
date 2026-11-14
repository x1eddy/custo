package app.custo

import app.custo.feature.overlays.Sticker
import app.custo.feature.overlays.StickerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerMathTest {
    private val w = 1000f
    private val h = 2000f

    @Test fun codecRoundTrip() {
        val list = listOf(Sticker("a", "b:heart", 0.2f, 0.3f, 0.25f, 45f), Sticker("b", "f:x.png", aspect = 1.5f))
        assertEquals(list, StickerMath.decode(StickerMath.encode(list)))
        assertEquals(emptyList<Sticker>(), StickerMath.decode(null))
        assertEquals(emptyList<Sticker>(), StickerMath.decode("not json"))
    }

    @Test fun normalizeAngles() {
        assertEquals(0f, StickerMath.normalize(360f), 1e-4f)
        assertEquals(-90f, StickerMath.normalize(270f), 1e-4f)
        assertEquals(180f, StickerMath.normalize(-180f), 1e-4f)
        assertEquals(10f, StickerMath.normalize(730f), 1e-4f)
    }

    @Test fun transformPansScalesAndClamps() {
        val s = Sticker("a", "b:star", 0.5f, 0.5f, 0.3f, 170f)
        val t = StickerMath.transform(s, 100f, -200f, 2f, 20f, w, h)
        assertEquals(0.6f, t.x, 1e-5f)
        assertEquals(0.4f, t.y, 1e-5f)
        assertEquals(0.6f, t.size, 1e-5f)
        assertEquals(-170f, t.rot, 1e-4f)
        val big = StickerMath.transform(s, 5000f, 5000f, 100f, 0f, w, h)
        assertEquals(1f, big.x, 0f); assertEquals(1f, big.y, 0f)
        assertEquals(StickerMath.MAX_SIZE, big.size, 0f)
        assertEquals(StickerMath.MIN_SIZE, StickerMath.transform(s, 0f, 0f, 0.001f, 0f, w, h).size, 0f)
    }

    @Test fun hitTestFollowsRotation() {
        // 200 px wide, 100 px tall, centered at (500, 1000).
        val s = Sticker("a", "b:x", 0.5f, 0.5f, 0.2f, 0f, aspect = 0.5f)
        assertTrue(StickerMath.contains(s, 590f, 1000f, w, h))
        assertFalse(StickerMath.contains(s, 500f, 1070f, w, h))
        val r = s.copy(rot = 90f)
        assertFalse(StickerMath.contains(r, 590f, 1000f, w, h))
        assertTrue(StickerMath.contains(r, 500f, 1090f, w, h))
    }

    @Test fun topmostWinsAndFrontMoves() {
        val a = Sticker("a", "b:x", 0.5f, 0.5f, 0.2f)
        val b = Sticker("b", "b:y", 0.52f, 0.5f, 0.2f)
        val list = listOf(a, b)
        assertEquals(1, StickerMath.topmostAt(list, 500f, 1000f, w, h))
        assertEquals(-1, StickerMath.topmostAt(list, 10f, 10f, w, h))
        assertEquals(listOf(b, a), StickerMath.bringToFront(list, 0))
        assertEquals(list, StickerMath.bringToFront(list, 1))
    }

    @Test fun boundsGrowWithRotation() {
        val s = Sticker("a", "b:x", 0.5f, 0.5f, 0.2f, 0f, aspect = 0.5f)
        val b0 = StickerMath.bounds(s, w, h)
        assertEquals(200f, b0.w, 1e-3f); assertEquals(100f, b0.h, 1e-3f)
        assertEquals(400f, b0.l, 1e-3f); assertEquals(950f, b0.t, 1e-3f)
        val b90 = StickerMath.bounds(s.copy(rot = 90f), w, h)
        assertEquals(100f, b90.w, 1e-3f); assertEquals(200f, b90.h, 1e-3f)
        val b45 = StickerMath.bounds(s.copy(rot = 45f), w, h)
        assertEquals(b45.w, b45.h, 1e-3f)
        assertEquals(150f * Math.sqrt(2.0).toFloat(), b45.w, 1e-2f)
    }

    @Test fun pinchMeasuresPanZoomAndTwist() {
        // Fingers 100 px apart horizontally, then 200 px apart vertically, centroid moved by (10, 20).
        val g = StickerMath.pinch(0f, 0f, 100f, 0f, 60f, -80f, 60f, 120f)
        assertEquals(2f, g.zoom, 1e-4f)
        assertEquals(90f, g.rotDeg, 1e-3f)
        assertEquals(10f, g.panX, 1e-4f)
        assertEquals(20f, g.panY, 1e-4f)
        assertEquals(1f, StickerMath.pinch(5f, 5f, 5f, 5f, 6f, 6f, 6f, 6f).zoom, 0f)
    }
}
