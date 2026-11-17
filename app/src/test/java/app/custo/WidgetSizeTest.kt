package app.custo

import app.custo.feature.widgets.SizeClass
import app.custo.feature.widgets.sizeClassOf
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeTest {

    @Test fun shortHeightsUseTheCompactClass() {
        // OUKITEL WP32 (260 dpi) cells: 2×1 = 153×82 dp, 4×1 = 321×82 dp, 2×2 = 153×180 dp, 4×2 = 321×180 dp.
        assertEquals(SizeClass.SMALL, sizeClassOf(153f, 82f))
        assertEquals(SizeClass.SMALL, sizeClassOf(321f, 82f))
        assertEquals(SizeClass.SMALL, sizeClassOf(153f, 180f))
        assertEquals(SizeClass.WIDE, sizeClassOf(321f, 180f))
        assertEquals(SizeClass.SMALL, sizeClassOf(110f, 56f))
        assertEquals(SizeClass.SMALL, sizeClassOf(250f, 56f))
        assertEquals(SizeClass.SMALL, sizeClassOf(110f, 110f))
        assertEquals(SizeClass.WIDE, sizeClassOf(250f, 110f))
        assertEquals(SizeClass.TALL, sizeClassOf(110f, 250f))
        assertEquals(SizeClass.LARGE, sizeClassOf(250f, 250f))
    }
}
