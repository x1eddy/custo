package app.custo

import app.custo.data.DeviceCache
import app.custo.data.DeviceCacheLogic
import app.custo.data.DeviceIdentity
import app.custo.feature.systemui.Probe
import app.custo.feature.systemui.Probes
import app.custo.shizuku.ShellBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCacheTest {
    private val id = DeviceIdentity("google/x/y:14/AP1/123:user/release-keys", 34, "10/1000")

    @Test fun roundTripKeepsNullValuesAndProbes() {
        val c = DeviceCache(id, mapOf("res.bool:a" to "1", "overlays:nav_modes" to "GESTURAL,THREE_BUTTON"),
            mapOf("secure:clock_seconds" to "1", "secure:icon_blacklist" to null, "raw:wm_size" to "Physical size: 576x1152"))
        val back = DeviceCacheLogic.decode(DeviceCacheLogic.encode(c))
        assertEquals(c, back)
        assertTrue(back!!.values.containsKey("secure:icon_blacklist"))
        assertNull(back.values["secure:icon_blacklist"])
    }

    @Test fun decodeToleratesGarbageAndUnknownFields() {
        assertNull(DeviceCacheLogic.decode(null))
        assertNull(DeviceCacheLogic.decode("{not json"))
        val c = DeviceCacheLogic.decode("""{"identity":{"fingerprint":"f","sdk":34,"app":"1"},"values":{"a:b":"1"},"future":42}""")
        assertEquals("1", c!!.values["a:b"])
    }

    @Test fun fingerprintChangeDropsProbesKeepsValues() {
        val c = DeviceCache(id, mapOf("p" to "1"), mapOf("secure:x" to "1"))
        assertEquals(c, DeviceCacheLogic.validate(c, id))
        for (other in listOf(id.copy(fingerprint = "new"), id.copy(sdk = 35), id.copy(app = "11/2000"))) {
            val v = DeviceCacheLogic.validate(c, other)
            assertEquals(other, v.identity)
            assertTrue(v.probes.isEmpty())
            assertEquals(c.values, v.values)
        }
        val fresh = DeviceCacheLogic.validate(null, id)
        assertEquals(id, fresh.identity)
        assertTrue(fresh.values.isEmpty())
    }

    @Test fun batchScriptAndSplit() {
        val script = ShellBatch.script(listOf("system" to "settings list system", "wm_size" to "wm size"))
        assertEquals("echo '${ShellBatch.MARK}system'; settings list system 2>&1; echo '${ShellBatch.MARK}wm_size'; wm size 2>&1", script)
        val out = "${ShellBatch.MARK}system\nfont_scale=1.0\nnext=a=b\n${ShellBatch.MARK}wm_size\nPhysical size: 576x1152\n${ShellBatch.MARK}empty\n"
        val parts = ShellBatch.split(out)
        assertEquals(listOf("system", "wm_size", "empty"), parts.keys.toList())
        assertEquals("font_scale=1.0\nnext=a=b", parts["system"])
        assertEquals("Physical size: 576x1152", parts["wm_size"])
        assertEquals("", parts["empty"])
    }

    @Test fun parseBatchMultiKeyOutput() {
        val m = ShellBatch.MARK
        val out = """
            ${m}system
            font_scale=1.15
            status_bar_show_battery_percent=1
            ${m}secure
            clock_seconds=null
            theme_customization_overlay_packages={"a":"b=c"}
            ${m}global
            window_animation_scale=0.5
            ${m}wm_size
            Physical size: 576x1152
            ${m}wm_density
            Physical density: 240
            ${m}overlays
            com.android.internal.systemui.navbar.gestural
            [x] com.android.internal.systemui.navbar.threebutton
            com.example.unrelated
        """.trimIndent()
        val watched = setOf("system:font_scale", "system:status_bar_show_battery_percent", "secure:clock_seconds",
            "secure:theme_customization_overlay_packages", "secure:icon_blacklist", "global:window_animation_scale")
        val b = DeviceCacheLogic.parseBatch(ShellBatch.split(out), watched)
        assertEquals("1.15", b.values["system:font_scale"])
        assertEquals("1", b.values["system:status_bar_show_battery_percent"])
        assertNull(b.values["secure:clock_seconds"])
        assertTrue("absent key is known-unset", b.values.containsKey("secure:icon_blacklist"))
        assertEquals("""{"a":"b=c"}""", b.values["secure:theme_customization_overlay_packages"])
        assertEquals("0.5", b.values["global:window_animation_scale"])
        assertEquals("Physical size: 576x1152", b.values["raw:wm_size"])
        assertFalse(b.values.getValue("raw:overlays")!!.contains("unrelated"))
        assertTrue(b.values.getValue("raw:overlays")!!.contains("[x] com.android.internal.systemui.navbar.threebutton"))
        assertEquals(listOf("font_scale", "status_bar_show_battery_percent"), b.lists["system"]!!.map { it.first })
    }

    @Test fun failedNamespaceNeverOverwritesWithUnset() {
        val m = ShellBatch.MARK
        val out = "${m}system\njava.lang.SecurityException: nope\n${m}secure\nclock_seconds=1"
        val b = DeviceCacheLogic.parseBatch(ShellBatch.split(out), setOf("system:font_scale", "secure:clock_seconds", "global:x"))
        assertFalse(b.values.containsKey("system:font_scale"))
        assertFalse(b.values.containsKey("global:x"))
        assertEquals("1", b.values["secure:clock_seconds"])
    }

    @Test fun diffAndMerge() {
        val old = mapOf("a" to "1", "b" to null, "c" to "x")
        assertNull("identical refresh changes nothing", DeviceCacheLogic.merge(old, mapOf("a" to "1", "b" to null)))
        val merged = DeviceCacheLogic.merge(old, mapOf("a" to "2", "d" to null))!!
        assertEquals(mapOf("a" to "2", "b" to null, "c" to "x", "d" to null), merged)
        assertEquals(setOf("a", "d"), DeviceCacheLogic.diff(old, merged))
        // A value the app just wrote wins over a refresh that started before the write.
        assertNull(DeviceCacheLogic.merge(old, mapOf("a" to "stale"), skip = setOf("a")))
        assertEquals(setOf("b"), DeviceCacheLogic.diff(mapOf("b" to null), mapOf("b" to "1")))
        assertEquals(setOf("b"), DeviceCacheLogic.diff(mapOf("b" to null), emptyMap()))
    }

    @Test fun cachedProbeEvaluation() {
        val p = Probe.AnyOf(Probe.ResBool("cfg"), Probe.Present)
        assertEquals(setOf("res.bool:cfg"), Probes.leaves(p))
        assertTrue(Probes.eval(p, null, mapOf("res.bool:cfg" to "1")))
        assertFalse(Probes.eval(p, null, mapOf("res.bool:cfg" to "0")))
        assertTrue(Probes.eval(p, "0", mapOf("res.bool:cfg" to "0")))
        assertTrue(Probes.eval(Probe.MinSdk(33), null, emptyMap(), sdk = 34))
        assertFalse(Probes.eval(Probe.Prop("ro.x"), null, emptyMap()))
    }
}
