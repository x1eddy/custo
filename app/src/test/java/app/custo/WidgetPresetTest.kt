package app.custo

import app.custo.feature.widgets.Drink
import app.custo.feature.widgets.HAlign
import app.custo.feature.widgets.PrayerMethod
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPresetTest {
    /** What v0.2 wrote (no 0.3 fields). */
    private val v02 = """{"themed":false,"bg":-16777216,"gradient":true,"bg2":-65536,"borderWidth":2,"padding":8,"hAlign":"START",""" +
        """"textScale":1.2,"zones":["Asia/Tokyo"],"countdownTitle":"Trip","photoUri":"content://p/1","apps":["a.b"]}"""

    @Test fun decodesV02WithNewDefaults() {
        val s = WidgetStyle.decode(v02)
        assertFalse(s.themed)
        assertTrue(s.gradient)
        assertEquals(HAlign.START, s.hAlign)
        assertEquals("Trip", s.countdownTitle)
        val d = WidgetStyle()
        assertFalse(s.textShadow)
        assertEquals(d.shadowColor, s.shadowColor)
        assertEquals(Drink.WATER, s.drink)
        assertEquals(listOf(1, 2, 3, 4, 5), s.workDays)
        assertEquals(5, s.weekDay)
        assertEquals(0L, s.sinceDay)
        assertTrue(s.photos.isEmpty())
        assertEquals("London", s.city)
        assertEquals("London", s.place().name)
        assertEquals(PrayerMethod.MWL, s.prayerMethod)
    }

    @Test fun roundTripsNewFields() {
        val s = WidgetStyle(
            textShadow = true, shadowColor = 5, drink = Drink.TEA, moodFaces = true, workStart = 480, workEnd = 960,
            workDays = listOf(1, 3), weekDay = 6, name1 = "Ana", name2 = "Ben", photoUri2 = "content://p/2", sinceDay = 19_000,
            photos = listOf("content://p/3"), rotateMinutes = 60, city = "", lat = 10.5, lon = -20.25, zone = "UTC",
            prayerMethod = PrayerMethod.KARACHI, hanafi = true, stepGoal = 10_000, showSun = false,
        )
        assertEquals(s, WidgetStyle.decode(s.encode()))
        assertEquals("Custom", s.place().name)
        assertEquals(10.5, s.place().lat, 0.0)
        assertEquals(PrayerMethod.MWL, WidgetStyle.decode("""{"prayerMethod":"MOON"}""").prayerMethod)
    }

    @Test fun presetCarriesOnlyTheLook() {
        val mine = WidgetStyle(
            themed = false, bg = 1, fg = 2, alpha = 0.5f, corner = 3, font = WidgetFont.MONO, gradient = true, bg2 = 4, borderWidth = 2,
            textShadow = true, shadowColor = 9, note = "secret", photoUri = "content://private", photos = listOf("content://x"),
            name1 = "Ana", apps = listOf("a.b"),
        )
        val file = WidgetStyle.exportPreset(mine, "NOTE")
        assertFalse(file.contains("secret"))
        assertFalse(file.contains("content://"))
        assertFalse(file.contains("Ana"))
        assertTrue(file.contains("custo-widget-style"))

        val imported = WidgetStyle.importPreset(file)!!
        val target = WidgetStyle(note = "keep me", countdownTitle = "Trip")
        val applied = target.withLookOf(imported)
        assertEquals("keep me", applied.note)
        assertEquals("Trip", applied.countdownTitle)
        assertEquals(WidgetFont.MONO, applied.font)
        assertEquals(1, applied.bg)
        assertEquals(0.5f, applied.alpha)
        assertTrue(applied.textShadow)
        assertEquals(9, applied.shadowColor)
        assertFalse(applied.themed)
    }

    @Test fun importsBareStyleAndRejectsJunk() {
        assertEquals(7, WidgetStyle.importPreset("""{"corner":7}""")!!.corner)
        assertNull(WidgetStyle.importPreset("hello"))
        assertNull(WidgetStyle.importPreset("[1,2]"))
    }
}
