package app.custo

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.custo.feature.widgets.RESPONSIVE_SIZES
import app.custo.feature.widgets.SizeClass
import app.custo.feature.widgets.sizeClassOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizeTest {

    /** Glance's Responsive pick: the largest declared size that fits, else the smallest. */
    private fun pick(w: Float, h: Float): DpSize =
        RESPONSIVE_SIZES.filter { it.width.value <= w && it.height.value <= h }.maxByOrNull { it.width.value * it.height.value }
            ?: RESPONSIVE_SIZES.minBy { it.width.value * it.height.value }

    @Test fun oneRowWidgetsGetAShortLayout() {
        // OUKITEL WP32 (260 dpi) cells: 2×1 = 153×82 dp, 4×1 = 321×82 dp, 2×2 = 153×180 dp, 4×2 = 321×180 dp.
        assertEquals(DpSize(110.dp, 56.dp), pick(153f, 82f))
        assertEquals(DpSize(250.dp, 56.dp), pick(321f, 82f))
        assertEquals(DpSize(110.dp, 110.dp), pick(153f, 180f))
        assertEquals(DpSize(250.dp, 110.dp), pick(321f, 180f))
        // The layout never claims more height than the widget has.
        listOf(153f to 82f, 321f to 82f, 153f to 180f, 321f to 180f, 321f to 400f).forEach { (w, h) ->
            assertTrue("$w×$h", pick(w, h).height.value <= h)
        }
    }

    @Test fun shortHeightsUseTheCompactClass() {
        assertEquals(SizeClass.SMALL, sizeClassOf(110f, 56f))
        assertEquals(SizeClass.SMALL, sizeClassOf(250f, 56f))
        assertEquals(SizeClass.SMALL, sizeClassOf(110f, 110f))
        assertEquals(SizeClass.WIDE, sizeClassOf(250f, 110f))
        assertEquals(SizeClass.TALL, sizeClassOf(110f, 250f))
        assertEquals(SizeClass.LARGE, sizeClassOf(250f, 250f))
    }
}
