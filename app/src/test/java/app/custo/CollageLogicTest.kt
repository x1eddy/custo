package app.custo

import app.custo.feature.themes.CollageLogic
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageLogicTest {

    @Test fun everyCountHasTemplatesAndIdsAreUnique() {
        for (n in CollageLogic.MIN_PHOTOS..CollageLogic.MAX_PHOTOS) {
            assertTrue("no template for $n", CollageLogic.forCount(n).isNotEmpty())
            assertTrue(CollageLogic.forCount(n).all { it.count == n })
        }
        assertTrue(CollageLogic.templates.size >= 10)
        assertEquals(CollageLogic.templates.size, CollageLogic.templates.map { it.id }.toSet().size)
        assertEquals(CollageLogic.forCount(2), CollageLogic.forCount(1))
        assertEquals(CollageLogic.forCount(6), CollageLogic.forCount(9))
    }

    @Test fun templatesTileTheWholeArea() {
        CollageLogic.templates.forEach { t ->
            val area = t.cells.sumOf { ((it.r - it.l) * (it.b - it.t)).toDouble() }
            assertEquals("${t.id} area", 1.0, area, 1e-4)
            t.cells.forEach { c -> assertTrue(c.l >= 0f && c.t >= 0f && c.r <= 1f && c.b <= 1f && c.r > c.l && c.b > c.t) }
        }
    }

    @Test fun cellsKeepMarginAndEqualGaps() {
        val t = CollageLogic.byId("2-cols")!!
        val cells = CollageLogic.cells(t, 1000f, 2000f, 20f, 50f)
        assertEquals(50f, cells[0].l, 1e-3f)
        assertEquals(50f, cells[0].t, 1e-3f)
        assertEquals(1950f, cells[0].b, 1e-3f)
        assertEquals(950f, cells[1].r, 1e-3f)
        assertEquals(20f, cells[1].l - cells[0].r, 1e-3f)
        assertEquals(cells[0].w, cells[1].w, 1e-3f)
    }

    @Test fun gapsAreUniformInMixedTemplate() {
        val t = CollageLogic.byId("5-mosaic")!!
        val c = CollageLogic.cells(t, 900f, 1800f, 30f, 0f)
        assertEquals(30f, c[1].l - c[0].r, 1e-3f) // top row
        assertEquals(30f, c[3].l - c[2].r, 1e-3f) // bottom row
        assertEquals(30f, c[2].t - c[0].b, 1e-3f) // between rows
        assertEquals(0f, c[0].l, 1e-3f)
        assertEquals(900f, c[4].r, 1e-3f)
    }

    @Test fun hugeMarginIsClamped() {
        val cells = CollageLogic.cells(CollageLogic.byId("4-grid")!!, 300f, 600f, 10f, 10_000f)
        cells.forEach { assertTrue(it.w >= 0f && it.h >= 0f) }
        assertEquals(100f, cells[0].l, 1e-3f)
    }

    @Test fun centerCrop() {
        val wide = CollageLogic.centerCrop(400, 100, 100f, 100f)
        assertEquals(150f, wide.l, 1e-3f); assertEquals(250f, wide.r, 1e-3f)
        assertEquals(0f, wide.t, 0f); assertEquals(100f, wide.b, 0f)
        val tall = CollageLogic.centerCrop(100, 400, 200f, 100f)
        assertEquals(0f, tall.l, 0f); assertEquals(100f, tall.r, 0f)
        assertEquals(50f, tall.h, 1e-3f); assertEquals(175f, tall.t, 1e-3f)
    }

    @Test fun videoRectCropAndFit() {
        // 16:9 video on a 1080x2400 portrait screen.
        val crop = CollageLogic.videoRect(1920, 1080, 1080, 2400, crop = true)
        assertEquals(2400, crop[3])
        assertTrue(crop[2] > 1080 && crop[0] < 0)
        assertEquals((1080 - crop[2]) / 2, crop[0])
        assertArrayEquals(intArrayOf(0, 896, 1080, 608), CollageLogic.videoRect(1920, 1080, 1080, 2400, crop = false))
        assertArrayEquals(intArrayOf(0, 0, 50, 60), CollageLogic.videoRect(0, 0, 50, 60, crop = true))
    }
}
