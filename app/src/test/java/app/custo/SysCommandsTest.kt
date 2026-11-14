package app.custo

import app.custo.feature.systemui.DisableFlag
import app.custo.feature.systemui.DndMode
import app.custo.feature.systemui.PrivateDnsMode
import app.custo.feature.systemui.ScreenSize
import app.custo.feature.systemui.SettingValues
import app.custo.feature.systemui.SysCmd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SysCommandsTest {
    @Test fun disableFlagsInStableOrderAndEmptyClears() {
        assertEquals("cmd statusbar send-disable-flag clock notification-icons statusbar-expansion",
            SysCmd.sendDisableFlag(setOf(DisableFlag.EXPANSION, DisableFlag.CLOCK, DisableFlag.NOTIFICATION_ICONS)))
        assertEquals("cmd statusbar send-disable-flag", SysCmd.sendDisableFlag(emptySet()))
    }

    @Test fun disableFlagsStoreRoundTrip() {
        val f = setOf(DisableFlag.SYSTEM_ICONS, DisableFlag.NOTIFICATION_ALERTS)
        assertEquals(f, DisableFlag.parse(DisableFlag.store(f)))
        assertEquals(emptySet<DisableFlag>(), DisableFlag.parse(null))
        assertEquals(setOf(DisableFlag.CLOCK), DisableFlag.parse("clock,bogus,"))
    }

    @Test fun dndModes() {
        assertEquals("cmd notification set_dnd on", SysCmd.setDnd(DndMode.SILENCE))
        assertEquals(DndMode.ALARMS, DndMode.ofZen("3"))
        assertEquals(DndMode.PRIORITY, DndMode.ofZen("1\n"))
        assertEquals(DndMode.OFF, DndMode.ofZen(null))
    }

    @Test fun privateDnsCycle() {
        assertEquals(PrivateDnsMode.AUTO, PrivateDnsMode.next(PrivateDnsMode.OFF, false))
        assertEquals(PrivateDnsMode.OFF, PrivateDnsMode.next(PrivateDnsMode.AUTO, false))
        assertEquals(PrivateDnsMode.HOSTNAME, PrivateDnsMode.next(PrivateDnsMode.AUTO, true))
        assertEquals(PrivateDnsMode.OFF, PrivateDnsMode.next(PrivateDnsMode.HOSTNAME, true))
        assertEquals(PrivateDnsMode.AUTO, PrivateDnsMode.of(null))
    }

    @Test fun wmSizeParsing() {
        val (phys, over) = SysCmd.parseWmSize("Physical size: 1080x2400\nOverride size: 720x1600\n")
        assertEquals(ScreenSize(1080, 2400), phys)
        assertEquals(ScreenSize(720, 1600), over)
        assertNull(SysCmd.parseWmSize("Physical size: 1080x2400\n").second)
        assertEquals("wm size 720x1600", SysCmd.wmSize(ScreenSize(720, 1600)))
        assertEquals("wm size reset", SysCmd.wmSize(null))
        assertNull(ScreenSize.parse(null))
    }

    @Test fun resolutionChoicesKeepAspect() {
        val choices = SysCmd.resolutionChoices(ScreenSize(1080, 2400))
        assertEquals(listOf(ScreenSize(1080, 2400), ScreenSize(720, 1600)), choices)
        assertEquals(3, SysCmd.resolutionChoices(ScreenSize(1440, 3120)).size)
        assertEquals(320, SysCmd.scaledDensity(480, ScreenSize(1080, 2400), ScreenSize(720, 1600)))
    }

    @Test fun settingsListParsing() {
        val out = "b_key=2\na_key=x=y\n\ngarbage line\nc=\n"
        assertEquals(listOf("a_key" to "x=y", "b_key" to "2", "c" to ""), SysCmd.parseSettingsList(out))
    }

    @Test fun cutoutOverlayDetection() {
        val list = """
            android
            [ ] com.android.internal.display.cutout.emulation.corner
            [x] com.android.internal.display.cutout.emulation.noCutout
            [ ] com.android.internal.systemui.navbar.gestural
        """.trimIndent()
        assertEquals(SysCmd.NO_CUTOUT, SysCmd.enabledCutout(list))
        assertTrue(SysCmd.hasOverlay(list, SysCmd.NO_CUTOUT))
        assertNull(SysCmd.enabledCutout(list.replace("[x]", "[ ]")))
        assertFalse(SysCmd.hasOverlay("[ ] other", SysCmd.NO_CUTOUT))
        assertEquals("cmd overlay enable-exclusive --user current --category 'com.android.internal.display.cutout.emulation.noCutout'",
            SysCmd.overlayEnableExclusive(SysCmd.NO_CUTOUT))
    }

    @Test fun clockFaceJsonKeepsOtherFields() {
        val j = SysCmd.clockFaceJson("""{"clockId":"DEFAULT","seedColor":null}""", "DIGITAL_CLOCK_METRO")
        assertEquals("DIGITAL_CLOCK_METRO", SysCmd.clockIdOf(j))
        assertTrue(j.contains("seedColor"))
        assertEquals("X", SysCmd.clockIdOf(SysCmd.clockFaceJson("junk", "X")))
        assertNull(SysCmd.clockIdOf(null))
    }

    @Test fun miscCommandsQuote() {
        assertEquals("cmd statusbar add-tile 'app.custo/app.custo.feature.systemui.CaffeineTile'",
            SysCmd.addTile("app.custo/app.custo.feature.systemui.CaffeineTile"))
        assertEquals("appops set 'app.custo' WRITE_SETTINGS allow", SysCmd.grantWriteSettings("app.custo"))
    }

    @Test fun settingValueMatching() {
        assertTrue(SettingValues.matches("1.0", "1", false))
        assertTrue(SettingValues.matches(null, "1", true))
        assertFalse(SettingValues.matches("0", "1", true))
        assertTrue(SettingValues.matches("300", "300", false))
        assertTrue(SettingValues.matches("on", "on", false))
    }
}
