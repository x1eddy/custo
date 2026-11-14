package app.custo

import app.custo.feature.icons.AppFilter
import app.custo.feature.icons.FgMode
import app.custo.feature.icons.IconPattern
import app.custo.feature.icons.IconShape
import app.custo.feature.icons.IconShortcut
import app.custo.feature.icons.IconStyle
import app.custo.feature.icons.IconStyleCodec
import app.custo.feature.icons.PRESET_STYLES
import app.custo.feature.icons.SavedStyle
import app.custo.feature.themes.Look
import app.custo.feature.themes.LookCodec
import app.custo.feature.themes.WallSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterTest {
    private val xml = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <iconback img1="iconback" />
            <scale factor="0.8" />
            <!-- <item component="ComponentInfo{com.commented/com.commented.Main}" drawable="nope" /> -->
            <item component="ComponentInfo{com.google.android.gm/com.google.android.gm.ConversationListActivityGmail}" drawable="gmail" />
            <item component="ComponentInfo{com.google.android.gm/.Other}" drawable="gmail_alt" />
            <item
                component="ComponentInfo{org.telegram.messenger/org.telegram.ui.LaunchActivity}"
                drawable='telegram' />
            <item component="ComponentInfo{broken}" drawable="x" />
            <item component=":LAUNCHER_ACTION_APP_DRAWER" drawable="drawer" />
            <item component="ComponentInfo{com.a/com.a.B}" drawable="" />
            <item component="ComponentInfo{com.amp/com.amp.Main}" drawable="a&amp;b" />
        </resources>
    """.trimIndent()

    @Test fun parsesItems() {
        val f = AppFilter.parse(xml)
        assertEquals("gmail", f.drawableFor("com.google.android.gm", "com.google.android.gm.ConversationListActivityGmail"))
        assertEquals("gmail_alt", f.drawableFor("com.google.android.gm", "com.google.android.gm.Other"))
        assertEquals("gmail_alt", f.drawableFor("com.google.android.gm", ".Other"))
        assertEquals("telegram", f.drawableFor("org.telegram.messenger", "org.telegram.ui.LaunchActivity"))
        assertEquals("a&b", f.drawableFor("com.amp", null))
        assertNull(f.drawableFor("com.commented", null))
        assertNull(f.drawableFor("com.a", "com.a.B"))
        assertEquals(3, f.byPackage.size)
    }

    @Test fun fallsBackToPackage() {
        val f = AppFilter.parse(xml)
        assertEquals("gmail", f.drawableFor("com.google.android.gm", "com.google.android.gm.NewLauncher"))
        assertNull(f.drawableFor("com.unknown", "com.unknown.Main"))
    }

    @Test fun componentParsing() {
        assertEquals("a.b" to "a.b.Main", AppFilter.parseComponent("ComponentInfo{a.b/.Main}"))
        assertEquals("a.b" to "x.Y", AppFilter.parseComponent(" ComponentInfo{ a.b / x.Y } "))
        assertNull(AppFilter.parseComponent("ComponentInfo{a.b/}"))
        assertNull(AppFilter.parseComponent("ComponentInfo{/x}"))
        assertNull(AppFilter.parseComponent("nothing"))
    }

    @Test fun eventsAndGarbage() {
        val f = AppFilter.fromEvents(sequenceOf("item" to mapOf("component" to "ComponentInfo{p/p.A}", "drawable" to "d"), "other" to emptyMap()))
        assertEquals("d", f.drawableFor("p", "p.A"))
        assertTrue(AppFilter.parse("not xml at all <<<").byPackage.isEmpty())
        assertEquals(listOf("gmail", "gmail_alt", "telegram", "a&b"), AppFilter.parse(xml).drawables)
    }
}

class IconStyleTest {
    @Test fun roundTrips() {
        val s = IconStyle(IconShape.FLOWER, listOf(1, 2, 3), 45f, IconPattern.CONFETTI, 4, 0.2f, 0.7f, 0.5f, 5, 0.1f, FgMode.TEXT, 6, 0.7f, "Music", "Hi", true, -3)
        assertEquals(s, IconStyle.decode(s.encode()))
        PRESET_STYLES.forEach { assertEquals(it.style, IconStyle.decode(it.style.encode())) }
        val list = listOf(SavedStyle("a", s), SavedStyle("b", IconStyle()))
        assertEquals(list, IconStyleCodec.decodeStyles(IconStyleCodec.encodeStyles(list)))
    }

    @Test fun toleratesOldAndUnknown() {
        val s = IconStyle.decode("""{"shape":"STAR","fg":"HOLOGRAM","bg":[-1],"future":42}""")!!
        assertEquals(IconShape.SQUIRCLE, s.shape)
        assertEquals(FgMode.APP_TINT, s.fg)
        assertEquals(listOf(-1), s.bg)
        assertEquals(IconStyle(), IconStyle.decode("{}"))
        assertNull(IconStyle.decode("{oops"))
        assertNull(IconStyle.decode(null))
        assertTrue(IconStyleCodec.decodeStyles("garbage").isEmpty())
        assertTrue(IconStyleCodec.decodeHistory(null).isEmpty())
    }

    @Test fun history() {
        val h = listOf(IconShortcut("id1", "com.x", "X", "pack:com.pack", null, 5), IconShortcut("id2", "com.y", "Y", style = IconStyle()))
        assertEquals(h, IconStyleCodec.decodeHistory(IconStyleCodec.encodeHistory(h)))
        assertEquals("style", IconStyleCodec.decodeHistory("""[{"id":"a","pkg":"p","label":"L"}]""")[0].source)
    }

    @Test fun bulkKeepsEachAppsGlyph() {
        assertEquals(FgMode.APP_TINT, IconStyle(fg = FgMode.GLYPH).forBulk().fg)
        assertEquals(FgMode.TEXT, IconStyle(fg = FgMode.TEXT).forBulk().fg)
        assertEquals(FgMode.APP_ORIGINAL, IconStyle(fg = FgMode.APP_ORIGINAL).forBulk().fg)
    }

    @Test fun initials() {
        assertEquals("GM", IconStyle.initials("Google Maps"))
        assertEquals("Sp", IconStyle.initials("spotify"))
        assertEquals("X", IconStyle.initials("x"))
        assertEquals("?", IconStyle.initials("?"))
    }
}

class LookIconsCompatTest {
    @Test fun oldLookHasNoIcons() {
        val old = """{"name":"Old","wall":{"style":"DOTS","colors":[-1],"seed":3,"angle":10.0},"seed":5,"style":"TONAL_SPOT","hiddenIcons":null,"builtIn":false}"""
        val look = LookCodec.decode(old)!!
        assertEquals("Old", look.name)
        assertNull(look.icons)
        assertEquals(1, LookCodec.decodeList("[$old]").size)
    }

    @Test fun lookWithIconsRoundTrips() {
        val look = Look("Mine", WallSpec(), 7, icons = IconStyle(IconShape.HEXAGON, listOf(1, 2)))
        val back = LookCodec.decode(LookCodec.encode(look))!!
        assertEquals(look, back)
        assertEquals(IconShape.HEXAGON, back.icons!!.shape)
    }

    @Test fun unknownIconFieldsStillLoadTheLook() {
        val s = """{"name":"Odd","wall":{},"seed":1,"icons":{"shape":"BLOB","pattern":"PLAID"}}"""
        val look = LookCodec.decode(s)!!
        assertEquals(IconShape.SQUIRCLE, look.icons!!.shape)
        assertEquals(IconPattern.NONE, look.icons!!.pattern)
    }
}
