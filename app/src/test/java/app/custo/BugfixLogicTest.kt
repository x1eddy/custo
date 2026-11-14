package app.custo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.custo.data.LenientList
import app.custo.feature.overlays.NotifFilter
import app.custo.feature.overlays.OverlayAction
import app.custo.feature.overlays.VolumeKeyPolicy
import app.custo.feature.profiles.AppProfile
import app.custo.feature.systemui.SysCmd
import app.custo.feature.systemui.SystemTweaks
import app.custo.shizuku.OriginalsStore
import app.custo.shizuku.Shell
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.shizuku.ShellResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BugfixLogicTest {

    // ---- Lenient list decoding ----

    @Test fun lenientDecodeSkipsOnlyBrokenElements() {
        val raw = """[{"id":"a","name":"A"},{"id":5},{"id":"b","name":"B","future":true}]"""
        val list = LenientList.decode(AppProfile.serializer(), raw)
        assertEquals(listOf("a", "b"), list.map { it.id })
    }

    @Test fun lenientDecodeOfGarbageIsEmpty() {
        assertTrue(LenientList.decode(AppProfile.serializer(), "{not json").isEmpty())
        assertTrue(LenientList.decode(AppProfile.serializer(), null).isEmpty())
    }

    @Test fun lenientUpdateKeepsBrokenElementsVerbatim() {
        val raw = """[{"id":"a","name":"A"},{"id":5}]"""
        val out = LenientList.update(AppProfile.serializer(), raw) { it + AppProfile("c", "C") }!!
        assertEquals(listOf("a", "c"), LenientList.decode(AppProfile.serializer(), out).map { it.id })
        assertTrue(out.contains("""{"id":5}"""))
    }

    @Test fun lenientUpdateRefusesUnparseableString() {
        assertNull(LenientList.update(AppProfile.serializer(), "{broken") { it + AppProfile("c", "C") })
        assertEquals(1, LenientList.decode(AppProfile.serializer(), LenientList.update(AppProfile.serializer(), "") { it + AppProfile("c", "C") }).size)
    }

    // ---- Volume keys ----

    @Test fun policyPassesKeysWhileAlarmRings() {
        val plan = VolumeKeyPolicy.plan(true, false, true, true, OverlayAction.FLASHLIGHT, OverlayAction.LOCK, true, true, true, alarmActive = true)
        assertFalse(plan.consume)
        val noAlarm = VolumeKeyPolicy.plan(true, false, true, true, OverlayAction.FLASHLIGHT, OverlayAction.LOCK, true, true, true, alarmActive = false)
        assertTrue(noAlarm.consume)
    }

    // ---- Notification filter ----

    @Test fun notifFilter() {
        assertTrue(NotifFilter.shouldAlert(isUpdate = false, onlyAlertOnce = false, importance = 3, channelImportance = 3))
        assertTrue(NotifFilter.shouldAlert(isUpdate = false, onlyAlertOnce = true, importance = 4, channelImportance = null))
        assertTrue(NotifFilter.shouldAlert(isUpdate = true, onlyAlertOnce = false, importance = 4, channelImportance = 4))
        assertFalse(NotifFilter.shouldAlert(isUpdate = true, onlyAlertOnce = true, importance = 4, channelImportance = 4))
        assertFalse(NotifFilter.shouldAlert(isUpdate = false, onlyAlertOnce = false, importance = 2, channelImportance = 4))
        assertFalse(NotifFilter.shouldAlert(isUpdate = false, onlyAlertOnce = false, importance = null, channelImportance = 2))
    }

    // ---- Nav overlay detection ----

    @Test fun enabledNavOverlay() {
        val list = """
            com.android.internal.systemui.navbar.gestural
            [ ] com.android.internal.systemui.navbar.gestural
            [x] com.android.internal.systemui.navbar.gestural_wide_back
            [ ] com.android.internal.systemui.navbar.threebutton
        """.trimIndent()
        assertEquals("com.android.internal.systemui.navbar.gestural_wide_back", SysCmd.enabledNavOverlay(list))
        assertNull(SysCmd.enabledNavOverlay("[ ] com.android.internal.systemui.navbar.threebutton\n[x] com.foo.other"))
    }

    // ---- Originals: restored ones are forgotten, failed ones kept ----

    private class FakeShell(private val failOn: String) : Shell {
        val commands = mutableListOf<String>()
        override suspend fun run(command: String): ShellResult {
            commands += command
            return if (command.contains(failOn)) ShellResult(1, "boom") else ShellResult(0, "")
        }
        override suspend fun fabricate(target: String, name: String, resource: String, type: Int, value: Int) = Unit
        override suspend fun unfabricate(name: String) = Unit
    }

    private fun tempOriginals(): OriginalsStore {
        val dir = Files.createTempDirectory("orig").toFile()
        return OriginalsStore(PreferenceDataStoreFactory.create { File(dir, "o.preferences_pb") })
    }

    @Test fun restoreAllRemovesEachRestoredOriginal() = runBlocking {
        val originals = tempOriginals()
        originals.rememberIfAbsent(Namespace.SECURE, "clock_seconds", "0")
        originals.rememberIfAbsent(Namespace.GLOBAL, "bad_key", "1")
        originals.rememberIfAbsent(Namespace.SYSTEM, "font_scale", null)
        val tweaks = SystemTweaks(FakeShell(failOn = "bad_key"), originals)
        val problems = tweaks.restoreAll()
        assertEquals(1, problems.size)
        assertEquals(setOf(Namespace.GLOBAL to "bad_key"), originals.all().keys)
    }

    @Test fun failedReadIsNotRecordedAsOriginal() = runBlocking {
        val originals = tempOriginals()
        val tweaks = SystemTweaks(FakeShell(failOn = "settings get"), originals)
        val r = runCatching { tweaks.write(Namespace.SECURE, "clock_seconds", "1") }
        assertTrue(r.isFailure)
        assertTrue(originals.all().isEmpty())
    }
}
