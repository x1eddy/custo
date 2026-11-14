package app.custo

import app.custo.feature.overlays.CcCell
import app.custo.feature.overlays.CcLogic
import app.custo.feature.overlays.CcTile
import app.custo.feature.overlays.CcZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCenterLogicTest {

    @Test fun codecRoundTripAndDefaults() {
        val tiles = listOf(CcTile.MEDIA, CcTile.WIFI, CcTile.APPS)
        assertEquals(tiles, CcLogic.decode(CcLogic.encode(tiles)))
        assertEquals(CcLogic.DEFAULT, CcLogic.decode(null))
        assertEquals(emptyList<CcTile>(), CcLogic.decode(""))
        assertEquals(listOf(CcTile.WIFI, CcTile.DND), CcLogic.decode("WIFI, BOGUS,DND,WIFI"))
    }

    @Test fun moveClamps() {
        val t = listOf(CcTile.WIFI, CcTile.BLUETOOTH, CcTile.DND)
        assertEquals(listOf(CcTile.BLUETOOTH, CcTile.WIFI, CcTile.DND), CcLogic.move(t, 0, 1))
        assertEquals(listOf(CcTile.DND, CcTile.WIFI, CcTile.BLUETOOTH), CcLogic.move(t, 2, -5))
        assertEquals(t, CcLogic.move(t, 0, -1))
        assertEquals(t, CcLogic.move(t, 7, 1))
    }

    @Test fun smallTilesFillRows() {
        val cells = CcLogic.layout(listOf(CcTile.WIFI, CcTile.BLUETOOTH, CcTile.FLASHLIGHT, CcTile.DND, CcTile.ROTATION))
        assertEquals(CcCell(CcTile.DND, 3, 0, 1, 1), cells[3])
        assertEquals(CcCell(CcTile.ROTATION, 0, 1, 1, 1), cells[4])
        assertEquals(2, CcLogic.rowCount(cells))
    }

    @Test fun wideTileStartsNewRow() {
        val cells = CcLogic.layout(listOf(CcTile.WIFI, CcTile.BRIGHTNESS, CcTile.DND))
        assertEquals(CcCell(CcTile.WIFI, 0, 0, 1, 1), cells[0])
        assertEquals(CcCell(CcTile.BRIGHTNESS, 0, 1, 4, 1), cells[1])
        assertEquals(CcCell(CcTile.DND, 0, 2, 1, 1), cells[2])
    }

    @Test fun tallTileReservesTwoRowsAndNoOverlap() {
        val cells = CcLogic.layout(CcLogic.DEFAULT)
        val media = cells.first { it.tile == CcTile.MEDIA }
        assertEquals(2, media.rows)
        val brightness = cells.first { it.tile == CcTile.BRIGHTNESS }
        assertEquals(media.row + 2, brightness.row)
        val seen = HashSet<Pair<Int, Int>>()
        cells.forEach { c ->
            for (r in c.row until c.row + c.rows) for (col in c.col until c.col + c.cols) {
                assertTrue("overlap at $col,$r", seen.add(col to r))
                assertTrue(col in 0 until CcLogic.COLS)
            }
        }
    }

    @Test fun cellAtFindsSpans() {
        val cells = CcLogic.layout(listOf(CcTile.MEDIA, CcTile.WIFI))
        assertEquals(0, CcLogic.cellAt(cells, 3, 1))
        assertEquals(1, CcLogic.cellAt(cells, 0, 2))
        assertEquals(-1, CcLogic.cellAt(cells, 2, 2))
    }

    @Test fun zones() {
        assertEquals(700 to 1000, CcLogic.zoneSpan(1000, CcZone.RIGHT, 0.3f))
        assertEquals(0 to 300, CcLogic.zoneSpan(1000, CcZone.LEFT, 0.3f))
        assertEquals(350 to 650, CcLogic.zoneSpan(1000, CcZone.CENTER, 0.3f))
        assertEquals(0 to 1000, CcLogic.zoneSpan(1000, CcZone.CENTER, 5f))
        assertTrue(CcLogic.inZone(900f, 1000, CcZone.RIGHT, 0.3f))
        assertFalse(CcLogic.inZone(100f, 1000, CcZone.RIGHT, 0.3f))
    }

    @Test fun pullDownNeedsMostlyVerticalMotion() {
        assertTrue(CcLogic.isPullDown(5f, 80f, 60f))
        assertFalse(CcLogic.isPullDown(5f, 40f, 60f))
        assertFalse(CcLogic.isPullDown(90f, 80f, 60f))
        assertFalse(CcLogic.isPullDown(0f, -80f, 60f))
        assertTrue(CcLogic.isPushUp(-10f, -100f, 72f))
        assertFalse(CcLogic.isPushUp(0f, 100f, 72f))
    }

    @Test fun brightnessCurveRoundTrips() {
        assertEquals(1, CcLogic.sliderToBrightness(0f))
        assertEquals(255, CcLogic.sliderToBrightness(1f))
        assertEquals(64, CcLogic.sliderToBrightness(0.5f))
        for (v in listOf(10, 64, 128, 255)) {
            assertEquals(v.toFloat(), CcLogic.sliderToBrightness(CcLogic.brightnessToSlider(v)).toFloat(), 1f)
        }
        assertEquals(15, CcLogic.sliderToSteps(1f, 15))
        assertEquals(8, CcLogic.sliderToSteps(0.5f, 15))
    }
}
