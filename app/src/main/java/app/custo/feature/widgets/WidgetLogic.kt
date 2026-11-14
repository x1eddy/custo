package app.custo.feature.widgets

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Pure, Android-free helpers behind the info widgets (unit-tested). */
object WidgetLogic {

    /** Rows of 7 day-of-month numbers (0 = blank) for [month]'s grid. */
    fun monthGrid(month: LocalDate, mondayFirst: Boolean): List<List<Int>> {
        val first = month.withDayOfMonth(1)
        val firstDow = first.dayOfWeek.value // 1 = Monday .. 7 = Sunday
        val lead = if (mondayFirst) firstDow - 1 else firstDow % 7
        val days = month.lengthOfMonth()
        val cells = List(lead) { 0 } + (1..days).toList()
        val padded = cells + List((7 - cells.size % 7) % 7) { 0 }
        return padded.chunked(7)
    }

    fun weekdayInitials(mondayFirst: Boolean, locale: Locale = Locale.getDefault()): List<String> {
        val order = if (mondayFirst) DayOfWeek.entries else listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.dropLast(1)
        return order.map { it.getDisplayName(TextStyle.NARROW, locale) }
    }

    /** Resolves a countdown target: 0 means the next New Year's Day. */
    fun countdownTarget(epochDay: Long, today: LocalDate): LocalDate =
        if (epochDay == 0L) LocalDate.of(today.year + 1, 1, 1) else LocalDate.ofEpochDay(epochDay)

    /** Days from [today] to [target]; negative once it has passed. */
    fun daysUntil(target: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, target)

    fun countdownText(days: Long): String = when {
        days > 1 -> "$days days"
        days == 1L -> "Tomorrow"
        days == 0L -> "Today"
        days == -1L -> "Yesterday"
        else -> "${-days} days ago"
    }

    /** Fraction of the year elapsed at the start of [day]. */
    fun yearProgress(day: LocalDate): Float = (day.dayOfYear - 1).toFloat() / day.lengthOfYear()

    /** Same quote all day, different every day; widgets offset by their id so two don't match. */
    fun quoteIndex(day: LocalDate, offset: Int, size: Int): Int =
        Math.floorMod(day.toEpochDay() * 31 + offset, size.toLong()).toInt()

    fun bytesText(bytes: Long): String {
        val gb = bytes / 1_000_000_000.0
        return if (gb >= 100) "%.0f GB".format(gb) else if (gb >= 1) "%.1f GB".format(gb) else "%.0f MB".format(bytes / 1_000_000.0)
    }

    val QUOTES: List<Pair<String, String>> = listOf(
        "Well begun is half done." to "Aristotle",
        "The secret of getting ahead is getting started." to "Mark Twain",
        "Simplicity is the ultimate sophistication." to "Leonardo da Vinci",
        "It always seems impossible until it's done." to "Nelson Mandela",
        "What we think, we become." to "Buddha",
        "Act as if what you do makes a difference. It does." to "William James",
        "Nothing will work unless you do." to "Maya Angelou",
        "The best way out is always through." to "Robert Frost",
        "Turn your wounds into wisdom." to "Oprah Winfrey",
        "Little by little, one travels far." to "J. R. R. Tolkien",
        "He who has a why can bear almost any how." to "Friedrich Nietzsche",
        "The journey of a thousand miles begins with one step." to "Lao Tzu",
        "Be yourself; everyone else is already taken." to "Oscar Wilde",
        "In the middle of difficulty lies opportunity." to "Albert Einstein",
        "Do what you can, with what you have, where you are." to "Theodore Roosevelt",
        "Whatever you are, be a good one." to "Abraham Lincoln",
        "The only way to do great work is to love what you do." to "Steve Jobs",
        "Stay hungry, stay foolish." to "Stewart Brand",
        "Make it simple, but significant." to "Don Draper",
        "Happiness depends upon ourselves." to "Aristotle",
        "Dwell on the beauty of life." to "Marcus Aurelius",
        "You have power over your mind, not outside events." to "Marcus Aurelius",
        "Waste no more time arguing what a good man should be. Be one." to "Marcus Aurelius",
        "We suffer more often in imagination than in reality." to "Seneca",
        "Luck is what happens when preparation meets opportunity." to "Seneca",
        "Knowing yourself is the beginning of all wisdom." to "Aristotle",
        "The unexamined life is not worth living." to "Socrates",
        "Fall seven times, stand up eight." to "Japanese proverb",
        "Less is more." to "Ludwig Mies van der Rohe",
        "Done is better than perfect." to "Sheryl Sandberg",
        "Everything you can imagine is real." to "Pablo Picasso",
        "Creativity takes courage." to "Henri Matisse",
        "Not all those who wander are lost." to "J. R. R. Tolkien",
        "To live is the rarest thing in the world." to "Oscar Wilde",
        "Keep your face always toward the sunshine." to "Walt Whitman",
        "If you want to lift yourself up, lift up someone else." to "Booker T. Washington",
        "Quality is not an act, it is a habit." to "Aristotle",
        "Patience is bitter, but its fruit is sweet." to "Jean-Jacques Rousseau",
        "The present moment always will have been." to "Unknown",
        "Slow is smooth, smooth is fast." to "Proverb",
        "Start where you are." to "Arthur Ashe",
        "Be curious, not judgmental." to "Walt Whitman",
    )
}

/** Cities offered by the world clock, with their time zone ids. */
val WORLD_CITIES: List<Pair<String, String>> = listOf(
    "London" to "Europe/London", "Lisbon" to "Europe/Lisbon", "Paris" to "Europe/Paris", "Berlin" to "Europe/Berlin",
    "Madrid" to "Europe/Madrid", "Rome" to "Europe/Rome", "Athens" to "Europe/Athens", "Istanbul" to "Europe/Istanbul",
    "Moscow" to "Europe/Moscow", "Dubai" to "Asia/Dubai", "Delhi" to "Asia/Kolkata", "Bangkok" to "Asia/Bangkok",
    "Singapore" to "Asia/Singapore", "Hong Kong" to "Asia/Hong_Kong", "Shanghai" to "Asia/Shanghai", "Seoul" to "Asia/Seoul",
    "Tokyo" to "Asia/Tokyo", "Sydney" to "Australia/Sydney", "Auckland" to "Pacific/Auckland", "Honolulu" to "Pacific/Honolulu",
    "Los Angeles" to "America/Los_Angeles", "Denver" to "America/Denver", "Chicago" to "America/Chicago",
    "New York" to "America/New_York", "Toronto" to "America/Toronto", "Mexico City" to "America/Mexico_City",
    "São Paulo" to "America/Sao_Paulo", "Buenos Aires" to "America/Argentina/Buenos_Aires", "Lagos" to "Africa/Lagos",
    "Cairo" to "Africa/Cairo", "Johannesburg" to "Africa/Johannesburg", "Nairobi" to "Africa/Nairobi",
)

fun cityName(zone: String): String = WORLD_CITIES.firstOrNull { it.second == zone }?.first ?: zone.substringAfterLast('/').replace('_', ' ')
