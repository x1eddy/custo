package app.custo

import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgetsets.HudMode
import app.custo.feature.widgetsets.SetLayout
import app.custo.feature.widgetsets.ThemePackD
import app.custo.feature.widgetsets.WidgetThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackDTest {

    @Test fun carbonSetsAreRegistered() {
        val ids = ThemePackD.themes.map { it.id }
        assertEquals(listOf("d_carbon", "d_carbon_silver"), ids)
        val all = WidgetThemes.all.map { it.id }
        assertEquals(all.size, all.toSet().size)
        ThemePackD.themes.forEach { assertSame(it, WidgetThemes.byId(it.id)) }
    }

    @Test fun carbonSetsHaveHudFrameAndFitTheGrid() {
        ThemePackD.themes.forEach { t ->
            assertNotNull(t.id, t.framePainter)
            assertNotNull(t.id, t.icons)
            assertNotNull(t.id, t.banner)
            assertEquals(HudMode.REPLACE, t.hudMode(WidgetKind.BANNER))
            assertEquals(HudMode.REPLACE, t.hudMode(WidgetKind.CALENDAR))
            assertEquals(HudMode.REPLACE, t.hudMode(WidgetKind.BATTERY_RING))
            assertEquals(HudMode.SLOTS, t.hudMode(WidgetKind.APPS))
            assertEquals(null, t.hudMode(WidgetKind.CLOCK))
            val rows = SetLayout.rowsUsed(SetLayout.pack(t.widgets.map { it.cols to it.rows }))
            assertTrue("${t.id} uses $rows rows", rows <= 8)
            t.widgets.indices.forEach { assertEquals(t.id, t.styleForSetWidget(it).themeId) }
            // Apps: at least one slot per app, a few round buttons in a wide row.
            assertEquals(5, t.hud!!.appSlots(0, 250f, 50f))
            assertEquals(3, t.hud!!.appSlots(0, 150f, 50f))
            assertEquals(6, t.hud!!.appSlots(6, 250f, 50f))
        }
    }
}
