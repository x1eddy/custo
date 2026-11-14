package app.custo

import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetStyle
import app.custo.feature.widgetsets.HudMode
import app.custo.feature.widgetsets.SetLayout
import app.custo.feature.widgetsets.ThemePackA
import app.custo.feature.widgetsets.WidgetThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackATest {

    private val expected = listOf(
        "a_blocky", "a_matrix_code", "a_blocky_summer", "a_brick_party", "a_tactical_red",
        "a_battle_royale", "a_gamer_rgb", "a_synthwave", "a_neon_cyberpunk", "a_football",
    )

    @Test fun allTenThemesAreRegisteredWithUniqueIds() {
        val ids = ThemePackA.themes.map { it.id }
        assertEquals(expected, ids)
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(it, it.startsWith("a_")); assertNotNull(it, WidgetThemes.byId(it)) }
        val all = WidgetThemes.all.map { it.id }
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun everyThemeHasWidgetsArtAndABanner() {
        ThemePackA.themes.forEach { t ->
            assertTrue(t.name, t.widgets.isNotEmpty())
            assertTrue(t.name, t.widgets.size in 6..10)
            assertTrue(t.name, t.decorations.isNotEmpty() && t.fallbackDecorations.isNotEmpty())
            assertNotNull(t.name, t.banner)
            assertNotNull(t.name, t.icons)
            // Every widget kind in the set has its own stickers, and every sticker is pack art.
            t.widgets.forEach { w -> assertTrue("${t.name} ${w.kind}", t.decorations.containsKey(w.kind)) }
            (t.decorations.values.flatten().flatten() + t.fallbackDecorations.flatten()).forEach { assertNotNull(t.name, it.art) }
            val rows = SetLayout.rowsUsed(SetLayout.pack(t.widgets.map { it.cols to it.rows }))
            assertTrue("${t.name} uses $rows rows", rows <= 8)
            assertTrue(t.name, t.widgets.any { it.kind == WidgetKind.BANNER })
        }
    }

    @Test fun gameSetsUseBundledFontsAndDrawTheirWidgetsAsGameUi() {
        mapOf(ThemePackA.BLOCKY to WidgetFont.PIXEL, ThemePackA.BATTLE_ROYALE to WidgetFont.HEAVY).forEach { (t, font) ->
            assertEquals(t.name, font, t.font)
            assertNotNull(t.name, t.hud)
            t.widgets.forEach { w -> assertNotNull("${t.name} ${w.kind}", t.hudMode(w.kind)) }
            assertEquals(t.name, HudMode.BEHIND, t.hudMode(WidgetKind.CLOCK))
            assertEquals(t.name, HudMode.SLOTS, t.hudMode(WidgetKind.APPS))
            // App slots always hold every picked app.
            for (apps in 0..6) assertTrue(t.hud!!.appSlots(apps, 330f, 40f) >= apps.coerceAtLeast(1))
            // Saved styles keep the bundled font.
            assertEquals(font, WidgetStyle.decode(t.styleForSetWidget(0).encode()).font)
        }
    }
}
