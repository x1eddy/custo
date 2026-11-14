package app.custo

import app.custo.feature.widgets.Drink
import app.custo.feature.widgets.HabitLogic
import app.custo.feature.widgets.HabitLogic.UsageEvent
import app.custo.feature.widgets.HabitLogic.WorkPhase
import app.custo.feature.widgets.MoodData
import app.custo.feature.widgets.Mood
import app.custo.feature.widgets.StepsData
import app.custo.feature.widgets.TodoData
import app.custo.feature.widgets.WaterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HabitLogicTest {
    private val weekdays = setOf(1, 2, 3, 4, 5)

    @Test fun workCountdown() {
        // Friday 14 Nov 2025, 9:00–17:00.
        val at = { h: Int, m: Int -> LocalDateTime.of(2025, 11, 14, h, m) }
        val working = HabitLogic.work(at(14, 45), 540, 1020, weekdays)
        assertEquals(WorkPhase.WORKING, working.phase)
        assertEquals(at(17, 0), working.until)
        assertEquals(345f / 480f, working.progress, 0.001f)
        assertEquals("2h 15m", HabitLogic.span(java.time.Duration.between(at(14, 45), working.until).toMinutes()))

        val before = HabitLogic.work(at(7, 30), 540, 1020, weekdays)
        assertEquals(WorkPhase.BEFORE, before.phase)
        assertEquals(at(9, 0), before.until)

        // After Friday's shift the next one is Monday.
        val after = HabitLogic.work(at(18, 0), 540, 1020, weekdays)
        assertEquals(WorkPhase.AFTER, after.phase)
        assertEquals(LocalDateTime.of(2025, 11, 17, 9, 0), after.until)

        val saturday = HabitLogic.work(LocalDateTime.of(2025, 11, 15, 12, 0), 540, 1020, weekdays)
        assertEquals(WorkPhase.DAY_OFF, saturday.phase)
        assertEquals(LocalDateTime.of(2025, 11, 17, 9, 0), saturday.until)
    }

    @Test fun spans() {
        assertEquals("45m", HabitLogic.span(45))
        assertEquals("1h 0m", HabitLogic.span(60))
        assertEquals("3d 2h", HabitLogic.span(3 * 1440 + 125))
        assertEquals("0m", HabitLogic.span(-5))
    }

    @Test fun weekCountdown() {
        val tue = LocalDate.of(2025, 11, 11)
        assertEquals(3, HabitLogic.daysUntilWeekday(tue, 5))
        assertEquals(0, HabitLogic.daysUntilWeekday(tue.plusDays(3), 5))
        assertEquals(6, HabitLogic.daysUntilWeekday(tue.plusDays(4), 5))
        assertEquals("3 days till", HabitLogic.weekText(tue, 5).first)
        assertEquals("the weekend", HabitLogic.weekText(tue, 6).second)
        assertEquals("weekend!", HabitLogic.weekText(LocalDate.of(2025, 11, 16), 6).second)
        assertEquals("1 day till", HabitLogic.weekText(LocalDate.of(2025, 11, 14), 6).first)
    }

    @Test fun anniversary() {
        val since = LocalDate.of(2023, 6, 10)
        assertEquals(1, HabitLogic.daysTogether(since, since))
        assertEquals(523, HabitLogic.daysTogether(since, since.plusDays(522)))
        assertEquals(LocalDate.of(2025, 6, 10), HabitLogic.nextAnniversary(since, LocalDate.of(2025, 6, 10)))
        assertEquals(LocalDate.of(2026, 6, 10), HabitLogic.nextAnniversary(since, LocalDate.of(2025, 6, 11)))
        assertEquals(2, HabitLogic.yearsAt(since, LocalDate.of(2025, 6, 10)))
        // A leap-day start falls back to 28 February in common years.
        assertEquals(LocalDate.of(2025, 2, 28), HabitLogic.nextAnniversary(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 1, 1)))
    }

    @Test fun reminders() {
        val now = LocalDateTime.of(2025, 11, 14, 10, 30)
        assertEquals(LocalDateTime.of(2025, 11, 14, 11, 0), HabitLogic.nextReminder(now, 2, 9, 21))
        assertEquals(LocalDateTime.of(2025, 11, 15, 9, 0), HabitLogic.nextReminder(now.withHour(21), 2, 9, 21))
        assertEquals(LocalDateTime.of(2025, 11, 14, 9, 0), HabitLogic.nextReminder(now.withHour(6), 3, 9, 21))
    }

    @Test fun waterLog() {
        var w = WaterData(goal = 3)
        w = w.add(100, 1, Drink.WATER).add(100, 2, Drink.TEA).add(99, 0, Drink.COFFEE)
        assertEquals(2, w.count(100))
        w = w.undo(100)
        assertEquals(listOf(Drink.WATER), w.today(100).map { it.drink })
        // Old history is trimmed.
        assertEquals(0, w.add(200, 3, Drink.WATER).count(100))
        assertEquals(w, HabitLogic.json.decodeFromString(WaterData.serializer(), HabitLogic.json.encodeToString(WaterData.serializer(), w)))
    }

    @Test fun moodAndTodoJson() {
        val m = MoodData(mapOf(20_000L to Mood.CALM, 20_001L to Mood.SICK))
        assertEquals(m, HabitLogic.json.decodeFromString(MoodData.serializer(), HabitLogic.json.encodeToString(MoodData.serializer(), m)))
        val t = TodoData().add(" milk ", 1).add("bread", 2).toggle(1)
        assertEquals("milk", t.items[0].text)
        assertTrue(t.items[0].done)
        assertEquals(listOf("bread"), t.clearDone().items.map { it.text })
        assertEquals(9, Mood.entries.size)
    }

    @Test fun stepsBaseline() {
        var (s, n) = HabitLogic.steps(StepsData(), 10_000, 100)
        assertEquals(0, n)
        HabitLogic.steps(s, 12_500, 100).let { s = it.first; n = it.second }
        assertEquals(2_500, n)
        // Reboot resets the counter: keep what we had and keep counting.
        HabitLogic.steps(s, 300, 100).let { s = it.first; n = it.second }
        assertEquals(2_800, n)
        // A new day starts from the last reading.
        HabitLogic.steps(s, 1_000, 101).let { s = it.first; n = it.second }
        assertEquals(700, n)
    }

    @Test fun screenTime() {
        val ev = listOf(
            UsageEvent("a", 50, true), // open since before the window
            UsageEvent("a", 200, false),
            UsageEvent("b", 300, true),
            UsageEvent("c", 400, true), // c replaces b without a pause
            UsageEvent("b", 410, false),
            UsageEvent("c", 600, false),
            UsageEvent("home", 600, true),
            UsageEvent("home", 700, false),
            UsageEvent("b", 900, true), // still open at the end
        )
        val r = HabitLogic.foreground(ev, 100, 1000, ignore = setOf("home"))
        assertEquals(mapOf("a" to 100L, "b" to 200L, "c" to 200L), r)
        assertEquals("1h 5m", HabitLogic.durationText(65 * 60_000L))
        // A pause with no resume seen counts from the start.
        assertEquals(mapOf("x" to 50L), HabitLogic.foreground(listOf(UsageEvent("x", 150, false)), 100, 1000))
    }

    @Test fun carouselAndBubbles() {
        assertEquals(0, HabitLogic.carousel(19, 1, 20))
        assertEquals(0, HabitLogic.carousel(5, 1, 0))
        assertEquals(2, HabitLogic.carousel(7, 0, 5))
        assertFalse(HabitLogic.carouselDue(0, 10 * 60_000L, 15))
        assertTrue(HabitLogic.carouselDue(0, 14 * 60_000L, 15)) // inexact alarms may come a bit early
        assertTrue(HabitLogic.carouselDue(0, 15 * 60_000L, 5)) // never faster than 15 min
        assertFalse(HabitLogic.carouselDue(0, 10 * 60_000L, 5))
        var mask = 0L
        (0 until 36).forEach { mask = HabitLogic.pop(mask, it) }
        assertTrue(HabitLogic.allPopped(mask, 36))
        assertFalse(HabitLogic.allPopped(HabitLogic.pop(0, 3), 36))
        assertTrue(HabitLogic.popped(HabitLogic.pop(0, 35), 35))
    }
}
