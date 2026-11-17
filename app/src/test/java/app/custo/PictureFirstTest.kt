package app.custo

import app.custo.feature.wallhaven.WhWallpaper
import app.custo.feature.widgetsets.SetBackground
import app.custo.feature.widgetsets.SetBackgroundLogic
import app.custo.feature.widgetsets.SetPaletteMath
import app.custo.feature.widgetsets.StoredPalette
import app.custo.feature.widgetsets.ThemePalette
import app.custo.feature.widgetsets.WidgetThemes
import app.custo.nav.Route
import app.custo.nav.Tab
import app.custo.nav.homeTab
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureFirstTest {

    // ---------- Tabs and routes ----------

    @Test fun fiveTabsInOrder() {
        assertEquals(listOf(Tab.HOME, Tab.WALLPAPERS, Tab.WIDGETS, Tab.ICONS, Tab.TWEAKS), Tab.entries)
        assertEquals(listOf("Home", "Wallpapers", "Widgets", "Icons", "Tweaks"), Tab.entries.map { it.label })
    }

    @Test fun routesBelongToTheirTabs() {
        assertEquals(Tab.WALLPAPERS, Route.WallpaperMaker.homeTab())
        assertEquals(Tab.WALLPAPERS, Route.WallhavenBrowse("One Piece").homeTab())
        assertEquals(Tab.WALLPAPERS, Route.WallpaperDetail("abc").homeTab())
        assertEquals(Tab.WALLPAPERS, Route.Collage.homeTab())
        assertEquals(Tab.WALLPAPERS, Route.LiveWallpaper.homeTab())
        assertEquals(Tab.WIDGETS, Route.WidgetSet("b_pirate_crew").homeTab())
        assertEquals(Tab.WIDGETS, Route.WidgetEditor(3).homeTab())
        assertEquals(Tab.ICONS, Route.IconThemes.homeTab())
        assertEquals(Tab.ICONS, Route.IconMaker().homeTab())
        assertEquals(Tab.TWEAKS, Route.SystemUiHub.homeTab())
        assertEquals(Tab.TWEAKS, Route.OverlaysHub.homeTab())
        assertEquals(Tab.TWEAKS, Route.OverlayEdge.homeTab())
        assertEquals(Tab.TWEAKS, Route.QsEditor.homeTab())
        assertEquals(Tab.TWEAKS, Route.Profiles.homeTab())
        assertEquals(Tab.TWEAKS, Route.Backup.homeTab())
        assertEquals(Tab.HOME, Route.ShizukuSetup.homeTab())
        assertEquals(Tab.HOME, Route.AppSettings.homeTab())
    }

    // ---------- WidgetTheme compatibility ----------

    @Test fun setsWithoutQueryAreUnchanged() {
        val t = WidgetThemes.base("good_luck")!!
        assertNull(t.wallhavenQuery)
        assertNull(t.chosenWall)
        // No background chosen: byId gives the built-in set itself.
        assertEquals(t, WidgetThemes.byId("good_luck"))
    }

    @Test fun franchiseSetsSearchByRealNameButKeepTheirTitles() {
        val expected = mapOf(
            "b_pirate_crew" to "One Piece", "b_cursed_energy" to "Jujutsu Kaisen", "b_comic_hero" to "Invincible",
            "a_battle_royale" to "Fortnite", "a_blocky" to "Minecraft", "a_blocky_summer" to "Roblox", "a_brick_party" to "Roblox",
            "a_tactical_red" to "Valorant", "a_matrix_code" to "Matrix", "a_synthwave" to "synthwave",
        )
        expected.forEach { (id, q) ->
            val t = WidgetThemes.base(id)!!
            assertEquals(id, q, t.wallhavenQuery)
        }
        assertEquals("Pirate Crew", WidgetThemes.base("b_pirate_crew")!!.name)
    }

    @Test fun backgroundJsonIsBackwardCompatible() {
        // Minimal entry (older/smaller writer): defaults fill in.
        val old = SetBackgroundLogic.decode("""[{"themeId":"b_pirate_crew","wallId":"abc123"}]""")
        val bg = old["b_pirate_crew"]!!
        assertEquals("abc123", bg.wallId)
        assertNull(bg.palette)
        assertFalse(bg.auto)
        // Unknown fields from a newer version and broken entries are skipped, not fatal.
        val mixed = SetBackgroundLogic.decode(
            """[{"themeId":"a","wallId":"x","future":1},{"nope":true},{"themeId":"b","wallId":"y","palette":{"background":1,"text":2,"accent":3,"secondary":4,"line":5,"tint":6}}]""",
        )
        assertEquals(setOf("a", "b"), mixed.keys)
        assertEquals(StoredPalette(1, 2, 3, 4, 5, 6), mixed["b"]!!.palette)
        assertTrue(SetBackgroundLogic.decode("not json").isEmpty())
        assertTrue(SetBackgroundLogic.decode(null).isEmpty())
        // Round trip.
        val full = SetBackground("t", "w", "One Piece", true, StoredPalette(1, 2, 3, 4, 5, 6), 99)
        val json = Json.encodeToString(SetBackground.serializer(), full)
        assertEquals(full, SetBackgroundLogic.decode("[$json]")["t"])
    }

    @Test fun resolveAppliesStoredPaletteAndFile() {
        val base = WidgetThemes.base("b_pirate_crew")!!
        val stored = StoredPalette(0xFF101010.toInt(), -1, 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), -1, 0xFF203040.toInt())
        val bg = SetBackground(base.id, "abc123", palette = stored)
        val r = SetBackgroundLogic.resolve(base, bg, "/files/wallpapers")
        assertEquals(stored.toPalette(), r.palette)
        assertEquals("/files/wallpapers/abc123.webp", r.chosenWall!!.path)
        assertEquals(stored.tint, r.chosenWall!!.tint)
        assertEquals(base.name, r.name)
        // Someone else's background or no files dir: unchanged.
        assertSame(base, SetBackgroundLogic.resolve(base, bg.copy(themeId = "other"), "/d"))
        assertSame(base, SetBackgroundLogic.resolve(base, bg, null))
        assertSame(base, SetBackgroundLogic.resolve(base, null, "/d"))
    }

    // ---------- Auto default ----------

    private fun wall(id: String, purity: String = "sfw", category: String = "anime") =
        WhWallpaper(id = id, path = "https://w.wallhaven.cc/full/$id.jpg", purity = purity, category = category)

    @Test fun autoPickOnlyOnceOnlineAndOnlyForSetsWithASearch() {
        val pirate = WidgetThemes.base("b_pirate_crew")!!
        val plain = WidgetThemes.base("good_luck")!!
        assertTrue(SetBackgroundLogic.shouldAutoPick(pirate, null, emptySet(), online = true))
        assertFalse("offline", SetBackgroundLogic.shouldAutoPick(pirate, null, emptySet(), online = false))
        assertFalse("no search", SetBackgroundLogic.shouldAutoPick(plain, null, emptySet(), online = true))
        assertFalse("already chosen", SetBackgroundLogic.shouldAutoPick(pirate, SetBackground(pirate.id, "x"), emptySet(), online = true))
        assertFalse("only once", SetBackgroundLogic.shouldAutoPick(pirate, null, setOf(pirate.id), online = true))
    }

    @Test fun autoPickTakesFirstSfwResult() {
        val list = listOf(wall("aaa", purity = "sketchy"), wall("bbb", purity = "nsfw"), wall("ccc", category = "people"), wall("ddd"), wall("eee"))
        assertEquals("ddd", SetBackgroundLogic.autoPick(list)?.id)
        assertNull(SetBackgroundLogic.autoPick(listOf(wall("aaa", purity = "sketchy"), wall("bbb", purity = ""))))
        assertNull(SetBackgroundLogic.autoPick(emptyList()))
    }

    @Test fun wallColorsPreferWallhavenListThenQuantizer() {
        assertEquals(listOf(0xFF112233.toInt()), SetBackgroundLogic.wallColors(listOf("#112233", "bad")) { error("not needed") })
        assertEquals(listOf(7), SetBackgroundLogic.wallColors(emptyList()) { listOf(7) })
    }

    // ---------- Palette ----------

    @Test fun contrastMath() {
        assertEquals(21.0, SetPaletteMath.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, SetPaletteMath.contrast(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
        val fixed = SetPaletteMath.ensureContrast(0xFFDDDDDD.toInt(), 0xFFFFFFFF.toInt(), 4.5)
        assertTrue(SetPaletteMath.contrast(fixed, 0xFFFFFFFF.toInt()) >= 4.5)
        val onDark = SetPaletteMath.ensureContrast(0xFF222244.toInt(), 0xFF101018.toInt(), 4.5)
        assertTrue(SetPaletteMath.contrast(onDark, 0xFF101018.toInt()) >= 4.5)
    }

    @Test fun hslRoundTrip() {
        listOf(0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF336699, 0xFFFAFAFA, 0xFF101010).map { it.toInt() }.forEach { c ->
            val h = SetPaletteMath.toHsl(c)
            val back = SetPaletteMath.fromHsl(h.h, h.s, h.l)
            listOf(16, 8, 0).forEach { sh -> assertTrue(Integer.toHexString(c), kotlin.math.abs(((c shr sh) and 0xFF) - ((back shr sh) and 0xFF)) <= 1) }
        }
    }

    private val walls = listOf(
        listOf(0xFF1B2A49, 0xFFE94F37, 0xFFF6F7EB, 0xFF393E41), // dark blue + red (pirate-ish)
        listOf(0xFFF2E8CF, 0xFFA7C957, 0xFF6A994E, 0xFF386641), // pale + greens
        listOf(0xFF7B2CBF, 0xFF240046, 0xFFE0AAFF, 0xFF10002B), // violet
        listOf(0xFF808080), // grey only
        listOf(0xFFFFFF00, 0xFFFFFFFF), // glaring
    ).map { l -> l.map { it.toInt() } }

    @Test fun derivedPaletteStaysReadableForEverySet() {
        WidgetThemes.all.forEach { t ->
            walls.forEach { w ->
                val p = SetPaletteMath.derive(t.palette, w)
                assertTrue("${t.id} text", SetPaletteMath.contrast(p.text, p.background) >= SetPaletteMath.TEXT_CONTRAST)
                assertTrue("${t.id} accent", SetPaletteMath.contrast(p.accent, p.background) >= SetPaletteMath.ACCENT_CONTRAST)
                assertTrue("${t.id} line", SetPaletteMath.contrast(p.line, p.background) >= SetPaletteMath.ACCENT_CONTRAST)
            }
        }
    }

    @Test fun derivedPaletteKeepsCardCharacterAndTakesWallpaperHue() {
        val light = ThemePalette(0xFFFFF6CC.toInt(), 0xFF15131A.toInt(), 0xFFE8282B.toInt(), 0xFF1E6FD9.toInt(), 0xFF15131A.toInt())
        val dark = ThemePalette(0xFF101014.toInt(), 0xFFFFFFFF.toInt(), 0xFF00FF66.toInt(), 0xFF333333.toInt(), 0xFF00FF66.toInt())
        val violet = walls[2]
        val pl = SetPaletteMath.derive(light, violet)
        val pd = SetPaletteMath.derive(dark, violet)
        assertTrue(SetPaletteMath.luminance(pl.background) > 0.5)
        assertTrue(SetPaletteMath.luminance(pd.background) < 0.1)
        // Takes the wallpaper's hue (violet ≈ 270°).
        val hue = SetPaletteMath.toHsl(pl.background).h
        assertTrue("hue $hue", hue in 250f..300f)
        assertNotEquals(light.accent, pl.accent)
        // No colors: unchanged.
        assertEquals(light, SetPaletteMath.derive(light, emptyList()))
    }

    @Test fun quantizerFindsMainColorsMostCommonFirst() {
        val red = 0xFFE01010.toInt(); val blue = 0xFF1030E0.toInt(); val clear = 0x00FFFFFF
        val px = IntArray(1000) { i -> when { i < 600 -> red; i < 950 -> blue; else -> clear } }
        val cols = SetPaletteMath.dominantColors(px, 4)
        assertEquals(2, cols.size)
        assertEquals(red, cols[0])
        assertEquals(blue, cols[1])
        // Near-identical shades collapse into one.
        val near = IntArray(100) { if (it % 2 == 0) 0xFF808080.toInt() else 0xFF8A8A8A.toInt() }
        assertEquals(1, SetPaletteMath.dominantColors(near).size)
    }
}
