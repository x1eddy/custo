package app.custo

import app.custo.feature.profiles.AppProfile
import app.custo.feature.profiles.ProfileLogic
import app.custo.feature.profiles.SettingChange
import app.custo.shizuku.ShellCommands.Namespace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLogicTest {
    private val games = AppProfile("1", "Games", listOf("com.game"), mapOf("HEADS_UP" to false), animationScale = 0.5f)
    private val social = AppProfile("2", "Social", listOf("com.social"), mapOf("GRAYSCALE" to true), screenTimeoutSec = 30)
    private val off = AppProfile("3", "Off", listOf("com.off"), mapOf("EXTRA_DIM" to true), enabled = false)

    @Test fun findsProfileByPackage() {
        assertEquals("Games", ProfileLogic.forPackage("com.game", listOf(games, social))?.name)
        assertNull(ProfileLogic.forPackage("com.other", listOf(games, social)))
        assertNull(ProfileLogic.forPackage(null, listOf(games)))
        assertNull(ProfileLogic.forPackage("com.off", listOf(off)))
    }

    @Test fun buildsChanges() {
        val g = ProfileLogic.changes(games)
        assertTrue(SettingChange(Namespace.GLOBAL, "heads_up_notifications_enabled", "0") in g)
        assertEquals(3, g.count { it.value == "0.5" })
        val s = ProfileLogic.changes(social)
        assertTrue(SettingChange(Namespace.SECURE, "accessibility_display_daltonizer_enabled", "1") in s)
        assertTrue(SettingChange(Namespace.SECURE, "accessibility_display_daltonizer", "0") in s)
        assertTrue(SettingChange(Namespace.SYSTEM, "screen_off_timeout", "30000") in s)
    }

    @Test fun ignoresUnknownToggles() {
        assertEquals(emptyList<SettingChange>(), ProfileLogic.changes(AppProfile("x", "x", toggles = mapOf("NOPE" to true))))
    }
}
