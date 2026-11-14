package app.custo

import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetStyle
import app.custo.feature.widgetsets.Anchor
import app.custo.feature.widgetsets.PinEvent
import app.custo.feature.widgetsets.PinPhase
import app.custo.feature.widgetsets.PinRun
import app.custo.feature.widgetsets.SetLayout
import app.custo.feature.widgetsets.WidgetThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetSetsTest {

    // ---------- Registry ----------

    @Test fun themeIdsAreUniqueAndFindable() {
        val ids = WidgetThemes.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertEquals(it, WidgetThemes.byId(it)?.id) }
        assertNull(WidgetThemes.byId("nope"))
        assertNull(WidgetThemes.byId(null))
    }

    @Test fun starterSetsArePresent() {
        listOf("good_luck", "frosted_christmas", "doodle_red", "doodle_green").forEach { assertNotNull(WidgetThemes.byId(it)) }
        assertTrue(WidgetThemes.categories.isNotEmpty())
    }

    @Test fun everySetFitsTheGridAndHasABanner() {
        WidgetThemes.all.forEach { t ->
            assertTrue(t.name, t.widgets.isNotEmpty())
            t.widgets.forEach { assertTrue(t.name, it.cols in 1..4 && it.rows in 1..4) }
            val rows = SetLayout.rowsUsed(SetLayout.pack(t.widgets.map { it.cols to it.rows }))
            assertTrue("${t.name} uses $rows rows", rows <= 8)
            if (t.widgets.any { it.kind == WidgetKind.BANNER }) assertNotNull(t.banner)
        }
    }

    @Test fun decorationsFallBackAndCycleBySlot() {
        val t = WidgetThemes.GOOD_LUCK
        assertTrue(t.decorationsFor(WidgetKind.PHOTO, 0).any { it.anchor == Anchor.CENTER })
        // A kind with no entry uses the fallback, cycling by slot.
        val a = t.decorationsFor(WidgetKind.MOON, 0)
        val b = t.decorationsFor(WidgetKind.MOON, 1)
        assertEquals(t.fallbackDecorations[0], a)
        assertEquals(t.fallbackDecorations[1], b)
        assertEquals(a, t.decorationsFor(WidgetKind.MOON, 2))
        assertEquals(a, t.decorationsFor(null, 0))
    }

    @Test fun themeStyleKeepsContentAndSetsLook() {
        val t = WidgetThemes.FROSTED_CHRISTMAS
        val mine = WidgetStyle(note = "milk", apps = listOf("a.b"), photoUri = "content://x", themed = true, borderWidth = 3, gradient = true)
        val s = t.styleFor(WidgetKind.NOTE, 2, mine)
        assertEquals("milk", s.note)
        assertEquals(listOf("a.b"), s.apps)
        assertEquals("content://x", s.photoUri)
        assertEquals(t.id, s.themeId)
        assertEquals("NOTE", s.themeKind)
        assertEquals(2, s.themeSlot)
        assertFalse(s.themed)
        assertFalse(s.gradient)
        assertEquals(0, s.borderWidth)
        assertEquals(t.palette.text, s.fg)
        assertEquals(t.font, s.font)
        assertEquals(t.textShadow, s.textShadow)
    }

    @Test fun setWidgetGetsBannerCountdownAndSlots() {
        val t = WidgetThemes.FROSTED_CHRISTMAS
        val today = LocalDate.of(2026, 12, 26)
        val banner = t.widgets.indexOfFirst { it.kind == WidgetKind.BANNER }
        assertEquals("Merry Christmas", t.styleForSetWidget(banner, today).bannerText)
        val cd = t.widgets.indexOfFirst { it.kind == WidgetKind.COUNTDOWN }
        val s = t.styleForSetWidget(cd, today)
        assertEquals("Christmas", s.countdownTitle)
        assertEquals(LocalDate.of(2027, 12, 25).toEpochDay(), s.countdownDay)
        t.widgets.indices.forEach { i ->
            val twinsBefore = t.widgets.take(i).count { it.kind == t.widgets[i].kind }
            assertEquals(twinsBefore, t.slotOf(i))
        }
    }

    // ---------- Style JSON ----------

    @Test fun oldJsonHasNoTheme() {
        val old = """{"clock":"BIG","themed":false,"bg":-1,"fg":-16777216,"note":"hi","textShadow":true}"""
        val s = WidgetStyle.decode(old)
        assertNull(s.themeId)
        assertEquals("", s.themeKind)
        assertEquals(0, s.themeSlot)
        assertEquals("hi", s.note)
        assertEquals(WidgetStyle().bannerText, s.bannerText)
    }

    @Test fun themeFieldsRoundTrip() {
        val s = WidgetThemes.DOODLE_RED.styleFor(WidgetKind.BANNER, 1, WidgetStyle(bannerText = "Ho", bannerSub = "ho"))
        val back = WidgetStyle.decode(s.encode())
        assertEquals(s, back)
        assertEquals("doodle_red", back.themeId)
    }

    @Test fun unknownThemeIdStillDecodes() {
        val s = WidgetStyle.decode("""{"themeId":"from_the_future","themeKind":"NOPE","futureField":1}""")
        assertEquals("from_the_future", s.themeId)
        assertNull(WidgetThemes.byId(s.themeId))
    }

    @Test fun lookCopyIncludesTheme() {
        val themed = WidgetThemes.GOOD_LUCK.styleFor(WidgetKind.CLOCK, 0, WidgetStyle())
        assertEquals("good_luck", WidgetStyle(note = "x").withLookOf(themed).themeId)
    }

    // ---------- Pin queue ----------

    @Test fun pinRunHappyPath() {
        var r = PinRun(2)
        assertEquals(PinPhase.READY, r.phase)
        r = r.on(PinEvent.Asked); assertEquals(PinPhase.WAITING, r.phase)
        r = r.on(PinEvent.Added); assertEquals(1, r.index); assertEquals(PinPhase.READY, r.phase)
        r = r.on(PinEvent.Asked).on(PinEvent.Skipped)
        assertTrue(r.done)
        assertEquals(1, r.added); assertEquals(1, r.skipped)
        assertEquals("1 added, 1 skipped", r.summary())
    }

    @Test fun pinRunIgnoresStrayEvents() {
        var r = PinRun(1)
        r = r.on(PinEvent.Added) // not waiting yet
        assertEquals(0, r.added)
        r = r.on(PinEvent.Asked).on(PinEvent.Asked)
        assertEquals(PinPhase.WAITING, r.phase)
        r = r.on(PinEvent.Added).on(PinEvent.Added)
        assertEquals(1, r.added)
        assertTrue(r.done)
    }

    @Test fun pinRunFailedRequestSkipsFromReady() {
        val r = PinRun(2).on(PinEvent.Skipped)
        assertEquals(1, r.index); assertEquals(1, r.skipped); assertFalse(r.done)
    }

    @Test fun pinRunStopEndsEarly() {
        val r = PinRun(5).on(PinEvent.Asked).on(PinEvent.Added).on(PinEvent.Asked).on(PinEvent.Stop)
        assertTrue(r.done); assertTrue(r.stopped)
        assertEquals(4, r.left)
        assertEquals("1 added, 4 not asked", r.summary())
        // Late events after stopping change nothing.
        assertEquals(r, r.on(PinEvent.Added).on(PinEvent.Skipped).on(PinEvent.Stop))
    }

    @Test fun pinRunUnsupportedAndEmpty() {
        assertTrue(PinRun(0).done)
        val r = PinRun(3).on(PinEvent.Unsupported)
        assertTrue(r.done); assertTrue(r.unsupported)
        assertTrue(r.summary().contains("launcher"))
    }

    // ---------- Layout ----------

    @Test fun packFillsRowsLikeALauncher() {
        val cells = SetLayout.pack(listOf(4 to 1, 2 to 2, 2 to 2, 2 to 2, 2 to 1, 2 to 1))
        assertEquals(SetLayout.Cell(0, 0, 4, 1), cells[0])
        assertEquals(SetLayout.Cell(0, 1, 2, 2), cells[1])
        assertEquals(SetLayout.Cell(2, 1, 2, 2), cells[2])
        assertEquals(SetLayout.Cell(0, 3, 2, 2), cells[3])
        assertEquals(SetLayout.Cell(2, 3, 2, 1), cells[4])
        assertEquals(SetLayout.Cell(2, 4, 2, 1), cells[5])
        assertEquals(5, SetLayout.rowsUsed(cells))
    }

    @Test fun packClampsWideSpansAndFillsGaps() {
        val cells = SetLayout.pack(listOf(9 to 1, 3 to 1, 1 to 1))
        assertEquals(SetLayout.Cell(0, 0, 4, 1), cells[0])
        assertEquals(SetLayout.Cell(0, 1, 3, 1), cells[1])
        assertEquals(SetLayout.Cell(3, 1, 1, 1), cells[2])
        assertEquals(0, SetLayout.rowsUsed(emptyList()))
    }

    @Test fun cellsShrinkForTallSets() {
        val (w, h) = SetLayout.cellSize(400f, 800f, 4, 4)
        assertEquals(100f, w, 0.001f)
        assertEquals(105f, h, 0.001f)
        val (_, h2) = SetLayout.cellSize(400f, 600f, 4, 10)
        assertEquals(60f, h2, 0.001f)
        val boxes = SetLayout.boxes(listOf(SetLayout.Cell(1, 2, 2, 1)), 10f, 20f, 100f, 50f)
        assertEquals(SetLayout.Box(110f, 120f, 310f, 170f), boxes[0])
    }

    @Test fun stickersStayInsideTheBitmap() {
        val card = SetLayout.Box(10f, 10f, 190f, 90f)
        val size = SetLayout.stickerSize(card.width, card.height, 1f, 1000f)
        assertEquals(80f * 0.34f, size, 0.001f)
        for (ax in listOf(0f, 0.5f, 1f)) for (ay in listOf(0f, 0.5f, 1f)) {
            val (x, y) = SetLayout.stickerCenter(card, ax, ay, size, 200f, 100f)
            assertTrue(x - size / 2 >= -0.001f && x + size / 2 <= 200.001f)
            assertTrue(y - size / 2 >= -0.001f && y + size / 2 <= 100.001f)
        }
        assertEquals(card.left + card.width / 2 to card.top + card.height / 2, SetLayout.stickerCenter(card, 0.5f, 0.5f, size, 200f, 100f))
        assertEquals(20f, SetLayout.stickerSize(500f, 500f, 0.5f, 40f), 0.001f)
    }

    @Test fun nextOccurrenceRollsOver() {
        assertEquals(LocalDate.of(2026, 12, 25), SetLayout.nextOccurrence(12, 25, LocalDate.of(2026, 11, 14)))
        assertEquals(LocalDate.of(2026, 12, 25), SetLayout.nextOccurrence(12, 25, LocalDate.of(2026, 12, 25)))
        assertEquals(LocalDate.of(2027, 12, 25), SetLayout.nextOccurrence(12, 25, LocalDate.of(2026, 12, 26)))
        assertEquals(LocalDate.of(2027, 2, 28), SetLayout.nextOccurrence(2, 29, LocalDate.of(2026, 3, 1)))
    }
}
