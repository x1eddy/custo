package app.custo.feature.widgets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class Drink(val label: String, val emoji: String, val color: Int) {
    WATER("Water", "💧", 0xFF4FC3F7.toInt()),
    TEA("Tea", "🍵", 0xFF9CCC65.toInt()),
    COFFEE("Coffee", "☕", 0xFF8D6E63.toInt()),
    JUICE("Juice", "🧃", 0xFFFFB74D.toInt()),
    MILK("Milk", "🥛", 0xFFECEFF1.toInt()),
    SODA("Soda", "🥤", 0xFFE57373.toInt()),
}

enum class Mood(val label: String, val emoji: String, val color: Int) {
    GREAT("Great", "😄", 0xFF66BB6A.toInt()),
    HAPPY("Happy", "🙂", 0xFF9CCC65.toInt()),
    CALM("Calm", "😌", 0xFF4DD0E1.toInt()),
    OKAY("Okay", "😐", 0xFFFFD54F.toInt()),
    TIRED("Tired", "😴", 0xFF9575CD.toInt()),
    SAD("Sad", "😢", 0xFF64B5F6.toInt()),
    ANXIOUS("Anxious", "😟", 0xFFFFB74D.toInt()),
    ANGRY("Angry", "😠", 0xFFE57373.toInt()),
    SICK("Sick", "🤒", 0xFFA1887F.toInt()),
}

@Serializable data class Sip(val day: Long, val at: Long, val drink: Drink = Drink.WATER)

@Serializable
data class WaterData(
    val goal: Int = 8,
    val sips: List<Sip> = emptyList(),
    val remind: Boolean = false,
    val everyHours: Int = 2,
    val fromHour: Int = 9,
    val toHour: Int = 21,
) {
    fun today(day: Long): List<Sip> = sips.filter { it.day == day }
    fun count(day: Long): Int = sips.count { it.day == day }

    /** Adds a glass and keeps two months of history. */
    fun add(day: Long, at: Long, drink: Drink): WaterData = copy(sips = (sips + Sip(day, at, drink)).filter { it.day > day - 62 })

    fun undo(day: Long): WaterData {
        val i = sips.indexOfLast { it.day == day }
        return if (i < 0) this else copy(sips = sips.toMutableList().apply { removeAt(i) })
    }
}

@Serializable data class MoodData(val days: Map<Long, Mood> = emptyMap())

@Serializable data class Todo(val id: Long, val text: String, val done: Boolean = false)

@Serializable data class TodoData(val items: List<Todo> = emptyList()) {
    fun toggle(id: Long) = copy(items = items.map { if (it.id == id) it.copy(done = !it.done) else it })
    fun add(text: String, id: Long) = copy(items = items + Todo(id, text.trim()))
    fun clearDone() = copy(items = items.filterNot { it.done })
}

/** Step counter baseline: counter value at the start of [day], and the last value seen. */
@Serializable data class StepsData(val day: Long = 0, val base: Long = 0, val last: Long = 0)

/** Pure helpers for the second wave of widgets (unit-tested). */
object HabitLogic {
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

    // ----- Water reminders -----

    /** Next reminder after [now] on the hour grid [from, from+every, ...] up to [to] (inclusive). */
    fun nextReminder(now: LocalDateTime, everyHours: Int, fromHour: Int, toHour: Int): LocalDateTime {
        val step = everyHours.coerceIn(1, 12)
        var day = now.toLocalDate()
        repeat(3) {
            var h = fromHour
            while (h <= toHour.coerceAtMost(23)) {
                val t = day.atTime(h, 0)
                if (t.isAfter(now)) return t
                h += step
            }
            day = day.plusDays(1)
        }
        return now.toLocalDate().plusDays(1).atTime(fromHour, 0)
    }

    // ----- Work countdown -----

    enum class WorkPhase { BEFORE, WORKING, AFTER, DAY_OFF }

    /** Where [now] sits in the work day, and when that changes next. */
    data class WorkState(val phase: WorkPhase, val until: LocalDateTime, val progress: Float)

    fun work(now: LocalDateTime, startMin: Int, endMin: Int, days: Set<Int>): WorkState {
        val today = now.toLocalDate()
        val start = today.atStartOfDay().plusMinutes(startMin.toLong())
        val end = today.atStartOfDay().plusMinutes(endMin.toLong().coerceAtLeast(startMin + 1L))
        val workday = today.dayOfWeek.value in days
        return when {
            workday && now.isBefore(start) -> WorkState(WorkPhase.BEFORE, start, 0f)
            workday && now.isBefore(end) -> WorkState(
                WorkPhase.WORKING, end,
                Duration.between(start, now).toMinutes().toFloat() / Duration.between(start, end).toMinutes().coerceAtLeast(1),
            )
            else -> {
                val next = nextWorkday(today.plusDays(1), days)?.atStartOfDay()?.plusMinutes(startMin.toLong())
                    ?: today.plusDays(1).atStartOfDay()
                WorkState(if (workday) WorkPhase.AFTER else WorkPhase.DAY_OFF, next, if (workday) 1f else 0f)
            }
        }
    }

    private fun nextWorkday(from: LocalDate, days: Set<Int>): LocalDate? =
        (0L..6L).map { from.plusDays(it) }.firstOrNull { it.dayOfWeek.value in days }

    /** "2h 15m", "45m", "3d 2h". */
    fun span(minutes: Long): String {
        val m = minutes.coerceAtLeast(0)
        return when {
            m >= 1440 -> "${m / 1440}d ${m % 1440 / 60}h"
            m >= 60 -> "${m / 60}h ${m % 60}m"
            else -> "${m}m"
        }
    }

    // ----- Week countdown -----

    /** Days from [today] until the next [target] weekday (ISO 1..7); 0 when it's today. */
    fun daysUntilWeekday(today: LocalDate, target: Int): Int = Math.floorMod(target - today.dayOfWeek.value, 7)

    /** Text for the week countdown. [target] 6 means the weekend (Saturday and Sunday). */
    fun weekText(today: LocalDate, target: Int): Pair<String, String> {
        val weekend = target == 6
        if (weekend && today.dayOfWeek.value >= 6) return "It's the" to "weekend!"
        val n = daysUntilWeekday(today, target)
        val name = if (weekend) "the weekend" else DayOfWeek.of(target).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        return when (n) {
            0 -> "It's" to "$name!"
            1 -> "1 day till" to name
            else -> "$n days till" to name
        }
    }

    // ----- Couple -----

    fun daysTogether(since: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(since, today) + 1

    /** Next anniversary on or after [today] (29 Feb falls back to 28 Feb in common years). */
    fun nextAnniversary(since: LocalDate, today: LocalDate): LocalDate {
        fun inYear(y: Int) = since.withYear(y)
        val thisYear = inYear(today.year)
        return if (!thisYear.isBefore(today)) thisYear else inYear(today.year + 1)
    }

    fun yearsAt(since: LocalDate, on: LocalDate): Int = ChronoUnit.YEARS.between(since, on).toInt()

    // ----- Steps -----

    /** Feeds a raw counter reading; returns the new baseline state and steps today. */
    fun steps(state: StepsData, reading: Long, today: Long): Pair<StepsData, Long> {
        var s = state
        if (s.day == 0L) s = StepsData(today, reading, reading)
        if (reading < s.last) s = s.copy(base = s.base - s.last, last = 0) // counter reset by a reboot
        if (s.day != today) s = StepsData(today, s.last, s.last)
        s = s.copy(last = reading)
        return s to (reading - s.base).coerceAtLeast(0)
    }

    // ----- Screen time -----

    /** One usage event: [resumed] true when an app came to the front, false when it left. */
    data class UsageEvent(val pkg: String, val time: Long, val resumed: Boolean)

    /**
     * Foreground time per app between [start] and [end] from resume/pause events. An app already
     * open at [start] counts from [start]; one still open at [end] counts up to [end].
     */
    fun foreground(events: List<UsageEvent>, start: Long, end: Long, ignore: Set<String> = emptySet()): Map<String, Long> {
        val total = HashMap<String, Long>()
        var current: String? = null
        var since = start
        var seenAny = false
        fun close(at: Long) {
            current?.let { p -> if (p !in ignore) total[p] = (total[p] ?: 0) + (at.coerceIn(start, end) - since.coerceIn(start, end)).coerceAtLeast(0) }
        }
        for (e in events.sortedBy { it.time }) {
            val first = !seenAny
            seenAny = true
            if (e.resumed) {
                if (current != e.pkg) { close(e.time); current = e.pkg; since = e.time }
            } else if (e.pkg == current) {
                close(e.time); current = null
            } else if (current == null && first) {
                // Paused without a seen resume: it was open since the start of the window.
                if (e.pkg !in ignore) total[e.pkg] = (total[e.pkg] ?: 0) + (e.time.coerceIn(start, end) - start)
            }
        }
        close(end)
        return total.filterValues { it > 0 }
    }

    fun durationText(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    // ----- Carousel -----

    /** Index after [steps] advances through [count] photos. */
    fun carousel(index: Int, steps: Int, count: Int): Int = if (count <= 0) 0 else Math.floorMod(index + steps, count)

    /** True when a widget rotating every [minutes] is due (with a little slack for inexact alarms). */
    fun carouselDue(lastAt: Long, now: Long, minutes: Int): Boolean = now - lastAt >= minutes.coerceAtLeast(15) * 60_000L * 9 / 10

    // ----- Bubble wrap -----

    fun pop(mask: Long, index: Int): Long = mask or (1L shl index)
    fun popped(mask: Long, index: Int): Boolean = mask and (1L shl index) != 0L
    fun allPopped(mask: Long, count: Int): Boolean = (0 until count).all { popped(mask, it) }
}
