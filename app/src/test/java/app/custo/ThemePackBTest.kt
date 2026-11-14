package app.custo

import app.custo.feature.widgetsets.SetLayout
import app.custo.feature.widgetsets.ThemePackB
import app.custo.feature.widgetsets.WidgetThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackBTest {

    private val expected = listOf(
        "b_comic_hero", "b_pirate_crew", "b_cursed_energy", "b_rock", "b_music",
        "b_galaxy", "b_halloween", "b_dinosaurs", "b_ocean", "b_mono",
    )

    @Test fun allTenThemesAreRegisteredWithUniqueIds() {
        val ids = ThemePackB.themes.map { it.id }
        assertEquals(expected, ids)
        assertEquals(ids.size, ids.toSet().size)
        val all = WidgetThemes.all.map { it.id }
        assertEquals(all.size, all.toSet().size)
        ThemePackB.themes.forEach { assertSame(it, WidgetThemes.byId(it.id)) }
    }

    @Test fun everyThemeHasWidgetsStickersAndFitsTheGrid() {
        ThemePackB.themes.forEach { t ->
            assertTrue(t.id, t.widgets.isNotEmpty())
            assertTrue(t.id, t.decorations.isNotEmpty())
            assertTrue(t.id, t.decorations.values.flatten().flatten().all { it.scale > 0f })
            assertNotNull(t.id, t.banner)
            assertNotNull(t.id, t.icons)
            val rows = SetLayout.rowsUsed(SetLayout.pack(t.widgets.map { it.cols to it.rows }))
            assertTrue("${t.id} uses $rows rows", rows <= 8)
            t.widgets.indices.forEach { t.styleForSetWidget(it) }
        }
    }
}
