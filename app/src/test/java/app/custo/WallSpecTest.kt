package app.custo

import app.custo.feature.systemui.Tonal
import app.custo.feature.themes.BUILT_IN_LOOKS
import app.custo.feature.themes.Look
import app.custo.feature.themes.LookCodec
import app.custo.feature.themes.WallLogic
import app.custo.feature.themes.WallSpec
import app.custo.feature.themes.WallStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WallSpecTest {

    /** A look list as v0.1 saved it (WallSpec had only style, colors, seed, angle). */
    private val v01Looks = """[{"name":"My look 1","wall":{"style":"WAVES","colors":[-14024704,-16747303],"seed":11,"angle":100.0},"seed":-16747303,"style":"TONAL_SPOT","hiddenIcons":null,"builtIn":false}]"""

    @Test fun oldSpecGetsNewDefaults() {
        val s = WallSpec.decode("""{"style":"DOTS","colors":[-1],"seed":3,"angle":10.0}""")!!
        assertEquals(WallStyle.DOTS, s.style)
        assertEquals(listOf(-1), s.colors)
        assertEquals(3, s.seed)
        assertEquals(10f, s.angle)
        assertEquals(0f, s.grain)
        assertEquals(0f, s.blur)
        assertEquals(0.5f, s.complexity)
    }

    @Test fun oldLookListDecodes() {
        val list = LookCodec.decodeList(v01Looks)
        assertEquals(1, list.size)
        assertEquals("My look 1", list[0].name)
        assertEquals(WallStyle.WAVES, list[0].wall.style)
        assertEquals(0.5f, list[0].wall.complexity)
    }

    @Test fun roundTrips() {
        val s = WallSpec(WallStyle.TOPO, listOf(1, 2, 3), seed = -9, angle = 33f, grain = 0.4f, blur = 0.2f, complexity = 0.9f)
        assertEquals(s, WallSpec.decode(s.encode()))
        val look = Look("x", s, 5, "VIBRANT")
        assertEquals(look, LookCodec.decode(LookCodec.encode(look)))
        BUILT_IN_LOOKS.forEach { assertEquals(it, LookCodec.decode(LookCodec.encode(it))) }
    }

    @Test fun unknownStyleFallsBackAndGarbageIsNull() {
        assertEquals(WallStyle.MESH, WallSpec.decode("""{"style":"PLASMA","seed":4}""")!!.style)
        assertNull(WallSpec.decode("{nope"))
        assertNull(WallSpec.decode(null))
        assertTrue(LookCodec.decodeList("garbage").isEmpty())
    }

    @Test fun builtInsAreUniqueAndCoverNewStyles() {
        assertEquals(BUILT_IN_LOOKS.size, BUILT_IN_LOOKS.map { it.name }.toSet().size)
        assertTrue(BUILT_IN_LOOKS.size >= 14)
        assertTrue(BUILT_IN_LOOKS.map { it.wall.style }.toSet().size >= 12)
    }
}

class WallLogicTest {
    @Test fun dayWindow() {
        assertTrue(WallLogic.isDay(8 * 60, 7 * 60, 20 * 60))
        assertFalse(WallLogic.isDay(21 * 60, 7 * 60, 20 * 60))
        assertFalse(WallLogic.isDay(6 * 60 + 59, 7 * 60, 20 * 60))
        // Day slot that wraps midnight (night shift).
        assertTrue(WallLogic.isDay(23 * 60, 22 * 60, 6 * 60))
        assertTrue(WallLogic.isDay(2 * 60, 22 * 60, 6 * 60))
        assertFalse(WallLogic.isDay(12 * 60, 22 * 60, 6 * 60))
        assertTrue(WallLogic.isDay(0, 600, 600))
    }

    @Test fun nextSwitch() {
        assertEquals(60, WallLogic.minutesUntilSwitch(6 * 60, 7 * 60, 20 * 60))
        assertEquals(13 * 60, WallLogic.minutesUntilSwitch(7 * 60, 7 * 60, 20 * 60))
        assertEquals(11 * 60, WallLogic.minutesUntilSwitch(20 * 60, 7 * 60, 20 * 60))
        assertEquals(1440, WallLogic.minutesUntilSwitch(600, 600, 600))
    }

    @Test fun palette() {
        val a = 0xFF000000.toInt(); val b = 0xFFFFFFFF.toInt()
        assertEquals(a, WallLogic.sample(listOf(a, b), 0f))
        assertEquals(b, WallLogic.sample(listOf(a, b), 1f))
        assertEquals(0xFF808080.toInt(), WallLogic.sample(listOf(a, b), 0.5f))
        assertEquals(0x80FF0000.toInt(), WallLogic.withAlpha(0xFFFF0000.toInt(), 0.5f))
    }

    @Test fun timeOfDayIsNeutralAtNoonAndDarkAtNight() {
        val c = 0xFF3366CC.toInt()
        val noon = WallLogic.timeOfDay(c, 13 * 60)
        assertTrue(abs(Tonal.toHsl(noon).third - Tonal.toHsl(c).third) < 0.01f)
        val night = WallLogic.timeOfDay(c, 60)
        assertTrue(Tonal.toHsl(night).third < Tonal.toHsl(c).third * 0.7f)
    }

    @Test fun batteryWashesOut() {
        val c = 0xFFFF3300.toInt()
        assertEquals(Tonal.toHsl(c).second, Tonal.toHsl(WallLogic.battery(c, 90)).second, 0.02f)
        assertTrue(Tonal.toHsl(WallLogic.battery(c, 5)).second < 0.5f)
    }
}
