package app.custo

import app.custo.feature.widgets.ClockLook
import app.custo.feature.widgets.DialStyle
import app.custo.feature.widgets.HAlign
import app.custo.feature.widgets.Stopwatch
import app.custo.feature.widgets.VAlign
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetLogic
import app.custo.feature.widgets.WidgetStyle
import app.custo.feature.widgets.cityName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class WidgetStyleTest {

    /** Exactly what v0.1 wrote into Glance state. */
    private val v01 = """{"clock":"STACKED","themed":false,"bg":-14935009,"fg":-1,"alpha":0.8,"corner":12,"font":"SERIF","showDate":false,"note":"hi"}"""

    @Test fun decodesOldJsonWithNewDefaults() {
        val s = WidgetStyle.decode(v01)
        assertEquals(ClockLook.STACKED, s.clock)
        assertFalse(s.themed)
        assertEquals(-14935009, s.bg)
        assertEquals(0.8f, s.alpha)
        assertEquals(12, s.corner)
        assertEquals(WidgetFont.SERIF, s.font)
        assertFalse(s.showDate)
        assertEquals("hi", s.note)
        // New fields fall back to defaults.
        val d = WidgetStyle()
        assertEquals(d.gradient, s.gradient)
        assertEquals(d.borderWidth, s.borderWidth)
        assertEquals(d.padding, s.padding)
        assertEquals(HAlign.CENTER, s.hAlign)
        assertEquals(VAlign.CENTER, s.vAlign)
        assertEquals(1f, s.textScale)
        assertEquals(DialStyle.MINIMAL, s.dial)
        assertEquals(d.zones, s.zones)
        assertEquals(null, s.photoUri)
        assertTrue(s.apps.isEmpty())
        assertFalse(s.needsBitmapBg)
    }

    @Test fun roundTripsEveryField() {
        val s = WidgetStyle(
            themed = false, gradient = true, bg2 = 0x12345678, gradientAngle = 45, borderWidth = 3, borderColor = 7,
            padding = 4, hAlign = HAlign.END, vAlign = VAlign.TOP, textScale = 1.4f, dial = DialStyle.RING, seconds = true,
            zones = listOf("Asia/Tokyo"), countdownTitle = "Trip", countdownDay = 20_000, photoUri = "content://x/1",
            apps = listOf("a.b", "c.d"), showLabels = false, showAuthor = false, showPercent = false,
        )
        assertEquals(s, WidgetStyle.decode(s.encode()))
        assertTrue(s.needsBitmapBg)
    }

    @Test fun unknownEnumAndGarbageFallBack() {
        assertEquals(ClockLook.BIG, WidgetStyle.decode("""{"clock":"HOLOGRAM","corner":3}""").clock)
        assertEquals(3, WidgetStyle.decode("""{"clock":"HOLOGRAM","corner":3}""").corner)
        assertEquals(WidgetStyle(), WidgetStyle.decode("not json"))
        assertEquals(WidgetStyle(), WidgetStyle.decode(null))
    }

    @Test fun themedGradientNeedsNoBitmap() {
        assertFalse(WidgetStyle(themed = true, gradient = true).needsBitmapBg)
        assertTrue(WidgetStyle(themed = true, borderWidth = 1).needsBitmapBg)
    }
}

class WidgetLogicTest {
    @Test fun monthGridMondayFirst() {
        // 1 Nov 2026 is a Sunday.
        val rows = WidgetLogic.monthGrid(LocalDate.of(2026, 11, 14), mondayFirst = true)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1), rows.first())
        assertEquals(30, rows.flatten().count { it > 0 })
        assertTrue(rows.all { it.size == 7 })
        assertEquals(6, rows.size)
    }

    @Test fun monthGridSundayFirst() {
        val rows = WidgetLogic.monthGrid(LocalDate.of(2026, 11, 1), mondayFirst = false)
        assertEquals(1, rows.first().first())
        assertEquals(5, rows.size)
        // February 2026 starts on a Sunday and fits exactly 4 rows.
        assertEquals(4, WidgetLogic.monthGrid(LocalDate.of(2026, 2, 1), mondayFirst = false).size)
    }

    @Test fun weekdayOrder() {
        assertEquals("M", WidgetLogic.weekdayInitials(true, Locale.ENGLISH).first())
        assertEquals("S", WidgetLogic.weekdayInitials(false, Locale.ENGLISH).first())
    }

    @Test fun countdown() {
        val today = LocalDate.of(2026, 11, 14)
        assertEquals(LocalDate.of(2027, 1, 1), WidgetLogic.countdownTarget(0, today))
        assertEquals(48, WidgetLogic.daysUntil(LocalDate.of(2027, 1, 1), today))
        assertEquals("Tomorrow", WidgetLogic.countdownText(1))
        assertEquals("Today", WidgetLogic.countdownText(0))
        assertEquals("3 days ago", WidgetLogic.countdownText(-3))
        val epoch = LocalDate.of(2026, 12, 25).toEpochDay()
        assertEquals(LocalDate.of(2026, 12, 25), WidgetLogic.countdownTarget(epoch, today))
    }

    @Test fun yearProgress() {
        assertEquals(0f, WidgetLogic.yearProgress(LocalDate.of(2026, 1, 1)))
        assertTrue(WidgetLogic.yearProgress(LocalDate.of(2026, 12, 31)) > 0.99f)
    }

    @Test fun quoteIsStableAndInRange() {
        val d = LocalDate.of(2026, 11, 14)
        val n = WidgetLogic.QUOTES.size
        val i = WidgetLogic.quoteIndex(d, 5, n)
        assertEquals(i, WidgetLogic.quoteIndex(d, 5, n))
        (-50..50).forEach { assertTrue(WidgetLogic.quoteIndex(d.plusDays(it.toLong()), -it, n) in 0 until n) }
        assertTrue(WidgetLogic.quoteIndex(d, 0, n) != WidgetLogic.quoteIndex(d.plusDays(1), 0, n))
    }

    @Test fun formats() {
        assertEquals("54.2 GB", WidgetLogic.bytesText(54_200_000_000))
        assertEquals("128 GB", WidgetLogic.bytesText(128_000_000_000))
        assertEquals("512 MB", WidgetLogic.bytesText(512_000_000))
        assertEquals("01:05", Stopwatch.format(65_400))
        assertEquals("1:00:01", Stopwatch.format(3_601_000))
        assertEquals("Tokyo", cityName("Asia/Tokyo"))
        assertEquals("Kuala Lumpur", cityName("Asia/Kuala_Lumpur"))
    }
}
