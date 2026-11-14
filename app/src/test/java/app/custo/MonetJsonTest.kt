package app.custo

import app.custo.feature.systemui.MonetJson
import app.custo.feature.systemui.MonetStyle
import app.custo.feature.systemui.Tonal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonetJsonTest {
    private val p = "android.theme.customization."

    @Test fun buildsFreshJson() {
        val s = MonetJson.build(null, 0xFF6D4AFF.toInt(), MonetStyle.VIBRANT, 42)
        val o = Json.parseToJsonElement(s).jsonObject
        assertEquals("6D4AFF", o["${p}system_palette"]!!.jsonPrimitive.content)
        assertEquals("6D4AFF", o["${p}accent_color"]!!.jsonPrimitive.content)
        assertEquals("VIBRANT", o["${p}theme_style"]!!.jsonPrimitive.content)
        assertEquals("preset", o["${p}color_source"]!!.jsonPrimitive.content)
    }

    @Test fun keepsUnrelatedKeysAndDropsColorIndex() {
        val existing = """{"${p}color_index":"2","${p}font":"x"}"""
        val o = Json.parseToJsonElement(MonetJson.build(existing, 0xFF000000.toInt(), null, 1)).jsonObject
        assertFalse(o.containsKey("${p}color_index"))
        assertFalse(o.containsKey("${p}theme_style"))
        assertEquals("x", o["${p}font"]!!.jsonPrimitive.content)
    }

    @Test fun survivesGarbageExisting() {
        assertTrue(MonetJson.build("not json", 0xFFFFFFFF.toInt(), MonetStyle.TONAL_SPOT, 1).contains("FFFFFF"))
    }

    @Test fun roundTripsSeedAndStyle() {
        val s = MonetJson.build(null, 0xFF123456.toInt(), MonetStyle.FRUIT_SALAD, 1)
        assertEquals(0xFF123456.toInt(), MonetJson.seedOf(s))
        assertEquals(MonetStyle.FRUIT_SALAD, MonetJson.styleOf(s))
    }

    @Test fun tonalRampIsOrderedByLightness() {
        val ramp = Tonal.ramp(0xFF6D4AFF.toInt())
        assertEquals(13, ramp.size)
        assertEquals(0xFFFFFFFF.toInt(), ramp[0])
        assertEquals(0xFF000000.toInt(), ramp[1000])
        val lights = Tonal.SHADES.map { Tonal.toHsl(ramp[it]!!).third }
        assertEquals(lights.sortedDescending(), lights)
    }
}
