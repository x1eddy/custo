package app.custo

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.custo.feature.widgets.TextFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFitTest {
    @Test fun bigWidgetKeepsFullSize() {
        assertEquals(1f, TextFit.stack(DpSize(300.dp, 200.dp), 1f, TextFit.Line(56f, 5), TextFit.Line(15f, 17)), 0f)
    }

    @Test fun oneRowClockShrinksSoDigitsFit() {
        // The reported bug: a 4×1 clock (about 60 dp of content height) cropped 56 sp digits.
        val s = TextFit.stack(DpSize(280.dp, 56.dp), 1f, TextFit.Line(56f, 5), TextFit.Line(15f, 17, shown = false))
        assertTrue(s < 1f)
        assertTrue(56f * 1.12f * s <= 56f + 0.01f)
    }

    @Test fun hiddenLinesDontTakeSpace() {
        val withDate = TextFit.stack(DpSize(280.dp, 80.dp), 1f, TextFit.Line(56f, 5), TextFit.Line(15f, 17))
        val without = TextFit.stack(DpSize(280.dp, 80.dp), 1f, TextFit.Line(56f, 5), TextFit.Line(15f, 17, shown = false))
        assertTrue(without > withDate)
    }

    @Test fun textScaleCounts() {
        val a = TextFit.stack(DpSize(200.dp, 80.dp), 1f, TextFit.Line(56f, 5))
        val b = TextFit.stack(DpSize(200.dp, 80.dp), 1.5f, TextFit.Line(56f, 5))
        assertTrue(b < a)
    }

    @Test fun rowFitsWidth() {
        val s = TextFit.row(DpSize(120.dp, 200.dp), 1f, TextFit.Line(96f, 2), TextFit.Line(40f, 2))
        assertTrue((96f * 0.62f * 2 + 40f * 0.62f * 2 + 8f) * s <= 120.5f)
    }
}
