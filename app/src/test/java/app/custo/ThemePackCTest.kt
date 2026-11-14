package app.custo

import app.custo.feature.widgetsets.ThemePackC
import app.custo.feature.widgetsets.WidgetThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackCTest {

    private val expected = listOf(
        "c_cozy_christmas", "c_teddy_bears", "c_cake", "c_pink", "c_squishy", "c_cafe",
        "c_sakura", "c_kitty", "c_autumn", "c_camping", "c_rainbow",
    )

    @Test fun allElevenThemesAreRegisteredWithUniqueIds() {
        val ids = ThemePackC.themes.map { it.id }
        assertEquals(expected, ids)
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(it, it.startsWith("c_")) }
        val all = WidgetThemes.all.map { it.id }
        assertEquals(all.size, all.toSet().size)
        ids.forEach { assertNotNull(it, WidgetThemes.byId(it)) }
    }

    @Test fun everyThemeHasWidgetsStickersAndAFrame() {
        ThemePackC.themes.forEach { t ->
            assertTrue(t.name, t.widgets.isNotEmpty())
            assertTrue(t.name, t.decorations.isNotEmpty())
            assertTrue(t.name, t.fallbackDecorations.isNotEmpty())
            assertNotNull(t.name, t.framePainter)
            assertNotNull(t.name, t.banner)
            assertNotNull(t.name, t.icons)
            t.decorations.values.flatten().flatten().forEach { assertNotNull(t.name, it.art) }
        }
    }
}
