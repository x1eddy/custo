package app.custo

import app.custo.feature.widgets.Astro
import app.custo.feature.widgets.CITIES
import app.custo.feature.widgets.MoonPhase
import app.custo.feature.widgets.Place
import app.custo.feature.widgets.PrayerMethod
import app.custo.feature.widgets.findCity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.tan

class AstroTest {
    private fun city(n: String) = findCity(n)!!

    private fun assertTime(expected: String, actual: ZonedDateTime?, tolMin: Long = 2) {
        assertNotNull(actual)
        val diff = abs(ChronoUnit.MINUTES.between(LocalTime.parse(expected), actual!!.toLocalTime()))
        assertTrue("expected $expected, got ${actual.toLocalTime()}", diff <= tolMin)
    }

    @Test fun cityListIsComplete() {
        assertTrue(CITIES.size >= 60)
        assertEquals(CITIES.size, CITIES.map { it.name }.toSet().size)
        CITIES.forEach { java.time.ZoneId.of(it.zone) }
    }

    // Reference values: NOAA solar calculator / timeanddate.com.
    @Test fun londonSummerSolstice() {
        val t = Astro.sunTimes(LocalDate.of(2024, 6, 21), city("London"))
        assertTime("04:43", t.sunrise)
        assertTime("21:21", t.sunset)
        assertTime("13:02", t.noon)
    }

    @Test fun tokyoWinterSolstice() {
        val t = Astro.sunTimes(LocalDate.of(2024, 12, 21), city("Tokyo"))
        assertTime("06:47", t.sunrise)
        assertTime("16:32", t.sunset)
    }

    @Test fun aucklandAcrossTheDateLine() {
        val t = Astro.sunTimes(LocalDate.of(2024, 12, 21), city("Auckland"))
        assertTime("05:58", t.sunrise, 3)
        assertTime("20:40", t.sunset, 3)
        assertEquals(LocalDate.of(2024, 12, 21), t.sunrise!!.toLocalDate())
    }

    @Test fun polarNightHasNoSunrise() {
        val t = Astro.sunTimes(LocalDate.of(2024, 12, 21), Place("Tromsø", 69.65, 18.96, "Europe/Oslo"))
        assertNull(t.sunrise)
        assertNull(t.sunset)
    }

    @Test fun sunIsWhereWeSaid() {
        val p = city("Cairo")
        val d = LocalDate.of(2025, 3, 1)
        val t = Astro.sunTimes(d, p)
        assertEquals(-0.833, Astro.sunAltitude(t.sunrise!!.toInstant(), p.lat, p.lon), 0.3)
        assertEquals(-0.833, Astro.sunAltitude(t.sunset!!.toInstant(), p.lat, p.lon), 0.3)
    }

    // Moon phases from published times (UTC).
    @Test fun fullMoon() {
        val m = Astro.moon(Instant.parse("2024-04-23T23:49:00Z"))
        assertEquals(0.5, m.fraction, 0.01)
        assertTrue(m.illumination > 0.99)
        assertEquals(MoonPhase.FULL, m.phase)
        assertEquals(MoonPhase.FULL, Astro.moon(Instant.parse("2025-01-13T22:27:00Z")).phase)
    }

    @Test fun newMoonAndQuarters() {
        val n = Astro.moon(Instant.parse("2024-04-08T18:21:00Z"))
        assertTrue(n.illumination < 0.01)
        assertEquals(MoonPhase.NEW, n.phase)
        val q = Astro.moon(Instant.parse("2024-04-15T19:13:00Z"))
        assertEquals(0.25, q.fraction, 0.01)
        assertEquals(0.5, q.illumination, 0.03)
        assertEquals(MoonPhase.FIRST_QUARTER, q.phase)
        assertEquals(MoonPhase.WAXING_GIBBOUS, Astro.moon(Instant.parse("2024-04-19T12:00:00Z")).phase)
        assertEquals(MoonPhase.WANING_CRESCENT, Astro.moon(Instant.parse("2024-04-05T12:00:00Z")).phase)
    }

    @Test fun daysUntilFull() {
        assertEquals(8, Astro.daysUntilPhase(Instant.parse("2024-04-15T23:49:00Z"), 0.5))
        assertEquals(0, Astro.daysUntilPhase(Instant.parse("2024-04-23T23:49:00Z"), 0.5))
    }

    // Umm al-Qura timetable for Makkah, 1 January 2024.
    @Test fun makkahPrayerTimes() {
        val t = Astro.prayerTimes(LocalDate.of(2024, 1, 1), city("Makkah"), PrayerMethod.MAKKAH, hanafi = false)!!
        assertTime("05:38", t.fajr, 3)
        assertTime("06:58", t.sunrise, 3)
        assertTime("12:23", t.dhuhr, 3)
        assertTime("15:27", t.asr, 3)
        assertTime("17:49", t.maghrib, 3)
        assertTime("19:19", t.isha, 3)
    }

    @Test fun anglesMatchTheMethod() {
        val p = city("Cairo")
        val d = LocalDate.of(2025, 3, 1)
        listOf(PrayerMethod.MWL, PrayerMethod.EGYPT, PrayerMethod.ISNA, PrayerMethod.KARACHI).forEach { m ->
            val t = Astro.prayerTimes(d, p, m, false)!!
            assertEquals(m.label, -m.fajr, Astro.sunAltitude(t.fajr.toInstant(), p.lat, p.lon), 0.3)
            assertEquals(m.label, -m.isha, Astro.sunAltitude(t.isha.toInstant(), p.lat, p.lon), 0.3)
        }
    }

    @Test fun asrShadowRule() {
        val p = city("Karachi")
        val d = LocalDate.of(2025, 6, 1)
        val shafi = Astro.prayerTimes(d, p, PrayerMethod.KARACHI, false)!!
        val hanafi = Astro.prayerTimes(d, p, PrayerMethod.KARACHI, true)!!
        assertTrue(hanafi.asr.isAfter(shafi.asr.plusMinutes(30)))
        // Shadow length = object + noon shadow (Shafi), so altitude = atan(1 / (1 + tan|lat - decl|)).
        val noonAlt = Astro.sunAltitude(shafi.dhuhr.toInstant(), p.lat, p.lon)
        val expected = Math.toDegrees(atan(1 / (1 + tan(Math.toRadians(90 - noonAlt)))))
        assertEquals(expected, Astro.sunAltitude(shafi.asr.toInstant(), p.lat, p.lon), 0.3)
    }

    @Test fun highLatitudeStillHasFajrAndIsha() {
        val t = Astro.prayerTimes(LocalDate.of(2024, 6, 21), city("London"), PrayerMethod.MWL, false)!!
        assertTrue(t.fajr.isBefore(t.sunrise))
        assertTrue(t.isha.isAfter(t.maghrib))
        val order = t.withSunrise().map { it.second }
        assertEquals(order, order.sortedBy { it.toInstant() })
    }

    @Test fun nextPrayerRollsOver() {
        val p = city("Makkah")
        val z = p.zoneId()
        val noonish = ZonedDateTime.of(2024, 1, 1, 13, 0, 0, 0, z)
        assertEquals("Asr", Astro.nextPrayer(noonish, p, PrayerMethod.MAKKAH, false)!!.first)
        val late = ZonedDateTime.of(2024, 1, 1, 23, 0, 0, 0, z)
        val next = Astro.nextPrayer(late, p, PrayerMethod.MAKKAH, false)!!
        assertEquals("Fajr", next.first)
        assertEquals(LocalDate.of(2024, 1, 2), next.second.toLocalDate())
    }
}
