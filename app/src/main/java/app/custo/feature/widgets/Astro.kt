package app.custo.feature.widgets

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/** A place for sun, moon and prayer times. [zone] is an IANA id; blank means the phone's zone. */
data class Place(val name: String, val lat: Double, val lon: Double, val zone: String) {
    fun zoneId(): ZoneId = runCatching { if (zone.isBlank()) ZoneId.systemDefault() else ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault())
}

/** Pure, offline astronomy: moon phase, sunrise/sunset (NOAA) and Islamic prayer times. */
object Astro {
    private fun rad(d: Double) = d * PI / 180
    private fun deg(r: Double) = r * 180 / PI
    private fun norm360(x: Double) = ((x % 360) + 360) % 360

    fun julianDay(instant: Instant): Double = instant.toEpochMilli() / 86_400_000.0 + 2440587.5

    private data class Sun(val declination: Double, val eqTimeMin: Double, val appLong: Double)

    /** NOAA solar position (declination in degrees, equation of time in minutes). */
    private fun sun(jd: Double): Sun {
        val t = (jd - 2451545.0) / 36525.0
        val l0 = norm360(280.46646 + t * (36000.76983 + t * 0.0003032))
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
        val omega = 125.04 - 1934.136 * t
        val appLong = l0 + c - 0.00569 - 0.00478 * sin(rad(omega))
        val meanObliq = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val obliq = meanObliq + 0.00256 * cos(rad(omega))
        val decl = deg(asin(sin(rad(obliq)) * sin(rad(appLong))))
        val y = tan(rad(obliq / 2)).let { it * it }
        val eq = 4 * deg(
            y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) -
                0.5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m)),
        )
        return Sun(decl, eq, norm360(appLong))
    }

    private fun jdAt(date: LocalDate, utcMinutes: Double) = date.toEpochDay() + 2440587.5 + utcMinutes / 1440.0

    /** Solar noon in UTC minutes after [date]'s UTC midnight. */
    fun solarNoonUtc(date: LocalDate, lon: Double): Double {
        var noon = 720 - 4 * lon
        repeat(2) { noon = 720 - 4 * lon - sun(jdAt(date, noon)).eqTimeMin }
        return noon
    }

    /**
     * UTC minutes when the sun's center is at [altitude] degrees, before ([morning]) or after solar noon.
     * Null when the sun never gets there that day (polar day/night, or twilight that never ends).
     */
    fun timeAtAltitude(date: LocalDate, lat: Double, lon: Double, altitude: Double, morning: Boolean): Double? {
        val noon = solarNoonUtc(date, lon)
        var t = noon + if (morning) -360.0 else 360.0
        repeat(3) {
            val s = sun(jdAt(date, t))
            val cosH = (sin(rad(altitude)) - sin(rad(lat)) * sin(rad(s.declination))) / (cos(rad(lat)) * cos(rad(s.declination)))
            if (cosH < -1 || cosH > 1) return null
            val h = deg(acos(cosH))
            val n = 720 - 4 * lon - s.eqTimeMin
            t = n + if (morning) -4 * h else 4 * h
        }
        return t
    }

    /** Sun's altitude in degrees at an instant (used to check results). */
    fun sunAltitude(instant: Instant, lat: Double, lon: Double): Double {
        val jd = julianDay(instant)
        val s = sun(jd)
        val utcMin = (instant.epochSecond % 86_400 + 86_400) % 86_400 / 60.0
        val trueSolar = utcMin + s.eqTimeMin + 4 * lon
        val ha = trueSolar / 4 - 180
        val z = acos(sin(rad(lat)) * sin(rad(s.declination)) + cos(rad(lat)) * cos(rad(s.declination)) * cos(rad(ha)))
        return 90 - deg(z)
    }

    fun toZoned(date: LocalDate, utcMinutes: Double, zone: ZoneId): ZonedDateTime =
        date.atStartOfDay(ZoneOffset.UTC).plusSeconds((utcMinutes * 60).toLong()).withZoneSameInstant(zone)

    data class SunTimes(val sunrise: ZonedDateTime?, val noon: ZonedDateTime, val sunset: ZonedDateTime?) {
        /** Daylight length in minutes; 0 or 1440 at the poles. */
        val dayMinutes: Long get() = if (sunrise != null && sunset != null) java.time.Duration.between(sunrise, sunset).toMinutes() else 0
    }

    /** Sunrise, solar noon and sunset for [date] at [place], in the place's zone. */
    fun sunTimes(date: LocalDate, place: Place): SunTimes {
        val z = place.zoneId()
        val d = date
        return SunTimes(
            timeAtAltitude(d, place.lat, place.lon, -0.833, true)?.let { toZoned(d, it, z) },
            toZoned(d, solarNoonUtc(d, place.lon), z),
            timeAtAltitude(d, place.lat, place.lon, -0.833, false)?.let { toZoned(d, it, z) },
        )
    }

    // ---------- Moon ----------

    data class Moon(val fraction: Double, val illumination: Double, val ageDays: Double) {
        val waxing: Boolean get() = fraction < 0.5
        val phase: MoonPhase get() = MoonPhase.of(fraction)
    }

    const val SYNODIC = 29.530588853

    /** Moon phase from the Sun–Moon elongation (low-precision Meeus terms, about ±30 min). */
    fun moon(instant: Instant): Moon {
        val jd = julianDay(instant)
        val d = jd - 2451545.0
        val lMoon = 218.316 + 13.176396 * d
        val mMoon = 134.963 + 13.064993 * d
        val mSun = 357.529 + 0.98560028 * d
        val dd = 297.850 + 12.190749 * d
        val f = 93.272 + 13.229350 * d
        val lambda = lMoon + 6.289 * sin(rad(mMoon)) + 1.274 * sin(rad(2 * dd - mMoon)) + 0.658 * sin(rad(2 * dd)) +
            0.214 * sin(rad(2 * mMoon)) - 0.186 * sin(rad(mSun)) - 0.114 * sin(rad(2 * f))
        val elong = norm360(lambda - sun(jd).appLong)
        val frac = elong / 360
        return Moon(frac, (1 - cos(rad(elong))) / 2, frac * SYNODIC)
    }

    /** Whole days until the moon next reaches [targetFraction] (0 = new, 0.5 = full). */
    fun daysUntilPhase(instant: Instant, targetFraction: Double): Int {
        // The moon's speed varies, so refine the mean-rate guess a few times.
        var days = 0.0
        repeat(4) {
            val f = moon(instant.plusSeconds((days * 86_400).toLong())).fraction
            var diff = ((targetFraction - f) % 1 + 1) % 1
            if ((days > 0 && diff > 0.5) || diff > 1 - 0.5 / SYNODIC) diff -= 1 // overshot, or only just passed
            days += diff * SYNODIC
        }
        return floor(days + 0.5).toInt()
    }

    // ---------- Prayer times ----------

    data class PrayerTimes(
        val fajr: ZonedDateTime, val sunrise: ZonedDateTime, val dhuhr: ZonedDateTime,
        val asr: ZonedDateTime, val maghrib: ZonedDateTime, val isha: ZonedDateTime,
    ) {
        /** The five prayers (sunrise is shown but isn't one). */
        fun list(): List<Pair<String, ZonedDateTime>> = listOf("Fajr" to fajr, "Dhuhr" to dhuhr, "Asr" to asr, "Maghrib" to maghrib, "Isha" to isha)
        fun withSunrise(): List<Pair<String, ZonedDateTime>> = listOf("Fajr" to fajr, "Sunrise" to sunrise, "Dhuhr" to dhuhr, "Asr" to asr, "Maghrib" to maghrib, "Isha" to isha)
    }

    /**
     * Prayer times (praytimes.org approach). High latitudes use the angle-based rule
     * so Fajr and Isha always exist.
     */
    fun prayerTimes(date: LocalDate, place: Place, method: PrayerMethod, hanafi: Boolean): PrayerTimes? {
        val z = place.zoneId()
        val lat = place.lat; val lon = place.lon
        val sunrise = timeAtAltitude(date, lat, lon, -0.833, true) ?: return null
        val sunset = timeAtAltitude(date, lat, lon, -0.833, false) ?: return null
        val noon = solarNoonUtc(date, lon)
        val decl = sun(jdAt(date, noon)).declination
        val factor = if (hanafi) 2.0 else 1.0
        val asrAlt = deg(atan(1 / (factor + tan(rad(abs(lat - decl))))))
        val asr = timeAtAltitude(date, lat, lon, asrAlt, false) ?: (noon + sunset) / 2
        val night = 1440 - (sunset - sunrise)

        var fajr = timeAtAltitude(date, lat, lon, -method.fajr, true)
        val fajrMax = night * method.fajr / 60
        if (fajr == null || sunrise - fajr > fajrMax) fajr = sunrise - fajrMax

        var isha = method.ishaMinutes?.let { sunset + it } ?: timeAtAltitude(date, lat, lon, -method.isha, false)
        if (method.ishaMinutes == null) {
            val ishaMax = night * method.isha / 60
            if (isha == null || isha - sunset > ishaMax) isha = sunset + ishaMax
        }
        return PrayerTimes(
            toZoned(date, fajr, z), toZoned(date, sunrise, z), toZoned(date, noon, z),
            toZoned(date, asr, z), toZoned(date, sunset, z), toZoned(date, isha!!, z),
        )
    }

    /** Next prayer at or after [now]; rolls to tomorrow's Fajr after Isha. */
    fun nextPrayer(now: ZonedDateTime, place: Place, method: PrayerMethod, hanafi: Boolean): Pair<String, ZonedDateTime>? {
        val local = now.withZoneSameInstant(place.zoneId())
        val today = prayerTimes(local.toLocalDate(), place, method, hanafi) ?: return null
        today.list().firstOrNull { it.second.isAfter(local) }?.let { return it }
        return prayerTimes(local.toLocalDate().plusDays(1), place, method, hanafi)?.list()?.first()
    }

    fun minutesBetween(a: ZonedDateTime, b: ZonedDateTime): Long = max(0, java.time.Duration.between(a, b).toMinutes())
}

enum class MoonPhase(val label: String, val emoji: String) {
    NEW("New moon", "🌑"), WAXING_CRESCENT("Waxing crescent", "🌒"), FIRST_QUARTER("First quarter", "🌓"),
    WAXING_GIBBOUS("Waxing gibbous", "🌔"), FULL("Full moon", "🌕"), WANING_GIBBOUS("Waning gibbous", "🌖"),
    LAST_QUARTER("Last quarter", "🌗"), WANING_CRESCENT("Waning crescent", "🌘");

    companion object {
        /** Named phases get about a day either side; the rest fill the gaps. */
        fun of(fraction: Double): MoonPhase {
            val f = ((fraction % 1) + 1) % 1
            val w = 1.0 / SYN_DAYS // ~1 day as a fraction
            return when {
                f < w || f > 1 - w -> NEW
                abs(f - 0.25) < w -> FIRST_QUARTER
                abs(f - 0.5) < w -> FULL
                abs(f - 0.75) < w -> LAST_QUARTER
                f < 0.25 -> WAXING_CRESCENT
                f < 0.5 -> WAXING_GIBBOUS
                f < 0.75 -> WANING_GIBBOUS
                else -> WANING_CRESCENT
            }
        }
        private const val SYN_DAYS = 29.530588853
    }
}

/** Standard calculation methods: Fajr and Isha sun angles (or Isha a fixed time after Maghrib). */
enum class PrayerMethod(val label: String, val fajr: Double, val isha: Double, val ishaMinutes: Int? = null) {
    MWL("Muslim World League", 18.0, 17.0),
    ISNA("ISNA (North America)", 15.0, 15.0),
    EGYPT("Egyptian Authority", 19.5, 17.5),
    MAKKAH("Umm al-Qura, Makkah", 18.5, 0.0, 90),
    KARACHI("Karachi", 18.0, 18.0),
}

/** About 60 major cities, so sun and prayer times work without location access. */
val CITIES: List<Place> = listOf(
    Place("London", 51.5074, -0.1278, "Europe/London"),
    Place("Paris", 48.8566, 2.3522, "Europe/Paris"),
    Place("Berlin", 52.52, 13.405, "Europe/Berlin"),
    Place("Madrid", 40.4168, -3.7038, "Europe/Madrid"),
    Place("Lisbon", 38.7223, -9.1393, "Europe/Lisbon"),
    Place("Rome", 41.9028, 12.4964, "Europe/Rome"),
    Place("Amsterdam", 52.3676, 4.9041, "Europe/Amsterdam"),
    Place("Brussels", 50.8503, 4.3517, "Europe/Brussels"),
    Place("Vienna", 48.2082, 16.3738, "Europe/Vienna"),
    Place("Stockholm", 59.3293, 18.0686, "Europe/Stockholm"),
    Place("Oslo", 59.9139, 10.7522, "Europe/Oslo"),
    Place("Warsaw", 52.2297, 21.0122, "Europe/Warsaw"),
    Place("Athens", 37.9838, 23.7275, "Europe/Athens"),
    Place("Istanbul", 41.0082, 28.9784, "Europe/Istanbul"),
    Place("Moscow", 55.7558, 37.6173, "Europe/Moscow"),
    Place("Kyiv", 50.4501, 30.5234, "Europe/Kyiv"),
    Place("Cairo", 30.0444, 31.2357, "Africa/Cairo"),
    Place("Casablanca", 33.5731, -7.5898, "Africa/Casablanca"),
    Place("Lagos", 6.5244, 3.3792, "Africa/Lagos"),
    Place("Nairobi", -1.2921, 36.8219, "Africa/Nairobi"),
    Place("Johannesburg", -26.2041, 28.0473, "Africa/Johannesburg"),
    Place("Addis Ababa", 9.03, 38.74, "Africa/Addis_Ababa"),
    Place("Makkah", 21.4225, 39.8262, "Asia/Riyadh"),
    Place("Medina", 24.4672, 39.6111, "Asia/Riyadh"),
    Place("Riyadh", 24.7136, 46.6753, "Asia/Riyadh"),
    Place("Dubai", 25.2048, 55.2708, "Asia/Dubai"),
    Place("Doha", 25.2854, 51.531, "Asia/Qatar"),
    Place("Kuwait City", 29.3759, 47.9774, "Asia/Kuwait"),
    Place("Amman", 31.9454, 35.9284, "Asia/Amman"),
    Place("Jerusalem", 31.7683, 35.2137, "Asia/Jerusalem"),
    Place("Baghdad", 33.3152, 44.3661, "Asia/Baghdad"),
    Place("Tehran", 35.6892, 51.389, "Asia/Tehran"),
    Place("Karachi", 24.8607, 67.0011, "Asia/Karachi"),
    Place("Lahore", 31.5204, 74.3587, "Asia/Karachi"),
    Place("Delhi", 28.6139, 77.209, "Asia/Kolkata"),
    Place("Mumbai", 19.076, 72.8777, "Asia/Kolkata"),
    Place("Dhaka", 23.8103, 90.4125, "Asia/Dhaka"),
    Place("Bangkok", 13.7563, 100.5018, "Asia/Bangkok"),
    Place("Kuala Lumpur", 3.139, 101.6869, "Asia/Kuala_Lumpur"),
    Place("Singapore", 1.3521, 103.8198, "Asia/Singapore"),
    Place("Jakarta", -6.2088, 106.8456, "Asia/Jakarta"),
    Place("Manila", 14.5995, 120.9842, "Asia/Manila"),
    Place("Hong Kong", 22.3193, 114.1694, "Asia/Hong_Kong"),
    Place("Shanghai", 31.2304, 121.4737, "Asia/Shanghai"),
    Place("Beijing", 39.9042, 116.4074, "Asia/Shanghai"),
    Place("Seoul", 37.5665, 126.978, "Asia/Seoul"),
    Place("Tokyo", 35.6762, 139.6503, "Asia/Tokyo"),
    Place("Sydney", -33.8688, 151.2093, "Australia/Sydney"),
    Place("Melbourne", -37.8136, 144.9631, "Australia/Melbourne"),
    Place("Auckland", -36.8485, 174.7633, "Pacific/Auckland"),
    Place("Honolulu", 21.3069, -157.8583, "Pacific/Honolulu"),
    Place("Los Angeles", 34.0522, -118.2437, "America/Los_Angeles"),
    Place("San Francisco", 37.7749, -122.4194, "America/Los_Angeles"),
    Place("Vancouver", 49.2827, -123.1207, "America/Vancouver"),
    Place("Denver", 39.7392, -104.9903, "America/Denver"),
    Place("Chicago", 41.8781, -87.6298, "America/Chicago"),
    Place("Toronto", 43.6532, -79.3832, "America/Toronto"),
    Place("New York", 40.7128, -74.006, "America/New_York"),
    Place("Mexico City", 19.4326, -99.1332, "America/Mexico_City"),
    Place("Bogotá", 4.711, -74.0721, "America/Bogota"),
    Place("Lima", -12.0464, -77.0428, "America/Lima"),
    Place("São Paulo", -23.5505, -46.6333, "America/Sao_Paulo"),
    Place("Rio de Janeiro", -22.9068, -43.1729, "America/Sao_Paulo"),
    Place("Buenos Aires", -34.6037, -58.3816, "America/Argentina/Buenos_Aires"),
    Place("Santiago", -33.4489, -70.6693, "America/Santiago"),
)

fun findCity(name: String): Place? = CITIES.firstOrNull { it.name == name }
