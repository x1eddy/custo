package app.custo

import app.custo.feature.icons.BgKind
import app.custo.feature.icons.FgMode
import app.custo.feature.icons.ICON_THEMES
import app.custo.feature.icons.IconPalette
import app.custo.feature.icons.IconShape
import app.custo.feature.icons.IconStyle
import app.custo.feature.icons.IconStyleCodec
import app.custo.feature.icons.IconTexture
import app.custo.feature.icons.PuzzleMath
import app.custo.feature.icons.ShapeGeometry
import app.custo.feature.icons.Sticker
import app.custo.feature.icons.StickerCorner
import app.custo.feature.icons.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IconStyleCompatTest {
    /** Exactly what 1.0.x wrote to prefs for a style. */
    private val v10 = """{"shape":"HEXAGON","bg":[-16777216,-1],"angle":45.0,"pattern":"DOTS","patternColor":-1,"patternSize":0.3,
        "patternOpacity":0.2,"glow":0.1,"glowColor":-1,"shadow":0.4,"fg":"TEXT","fgColor":-65536,"fgScale":0.6,"glyph":"Music",
        "text":"Hi","launcherShape":true,"seed":3}"""

    @Test fun oldStylesStillLoadWithNewDefaults() {
        val s = IconStyle.decode(v10)!!
        assertEquals(IconShape.HEXAGON, s.shape)
        assertEquals(FgMode.TEXT, s.fg)
        assertEquals(listOf(-16777216, -1), s.bg)
        assertTrue(s.launcherShape)
        // Everything new keeps the 1.0 look.
        val fresh = IconStyle()
        assertEquals(BgKind.AUTO, s.bgKind)
        assertEquals(fresh.corner, s.corner, 0f)
        assertEquals(null, s.image)
        assertFalse(s.puzzle)
        assertEquals(IconTexture.NONE, s.texture)
        assertEquals(0f, s.border, 0f)
        assertEquals(Sticker.NONE, s.sticker)
        assertFalse(s.rainbow)
        assertEquals(0.3f, s.shadowDistance, 0f)
        assertEquals(0.3f, s.shadowBlur, 0f)
        assertEquals(90f, s.shadowAngle, 0f)
        assertEquals(0f, s.fgX + s.fgY + s.fgRotation + s.bevel + s.emboss + s.gloss + s.neumorph, 0f)
        // Saved-style lists from 1.0 too.
        val list = IconStyleCodec.decodeStyles("""[{"name":"Old","style":$v10}]""")
        assertEquals("Old", list.single().name)
        assertEquals(s, list.single().style)
    }

    @Test fun newFieldsRoundTrip() {
        val s = IconStyle(
            shape = IconShape.BLOB, bg = listOf(1, 2, 3, 4), bgKind = BgKind.SWEEP, corner = 0.1f, image = "/data/x.webp", imageZoom = 2f,
            imageX = -0.5f, imageY = 0.25f, puzzle = true, texture = IconTexture.CARBON, textureOpacity = 0.7f, border = 0.3f,
            borderColor = 5, borderDashed = true, innerStroke = 0.2f, innerStrokeColor = 6, fg = FgMode.DUOTONE, fgColor2 = 7,
            fgX = 0.1f, fgY = -0.1f, fgRotation = 30f, shadowColor = 8, shadowAngle = 200f, shadowDistance = 0.9f, shadowBlur = 0.1f,
            emboss = 0.5f, bevel = 0.6f, gloss = 0.7f, neumorph = 0.8f, sticker = Sticker.CROWN, stickerCorner = StickerCorner.BOTTOM_START,
            stickerColor = 9, rainbow = true, pack = "com.pack",
        )
        assertEquals(s, IconStyle.decode(s.encode()))
    }

    @Test fun newEnumsFallBackWhenUnknown() {
        val s = IconStyle.decode("""{"bgKind":"PLASMA","texture":"VELVET","sticker":"UNICORN","stickerCorner":"MIDDLE","shape":"HEART"}""")!!
        assertEquals(BgKind.AUTO, s.bgKind)
        assertEquals(IconTexture.NONE, s.texture)
        assertEquals(Sticker.NONE, s.sticker)
        assertEquals(StickerCorner.TOP_END, s.stickerCorner)
        assertEquals(IconShape.HEART, s.shape)
    }

    @Test fun picturesNeedAPath() {
        assertFalse(IconStyle(bgKind = BgKind.IMAGE).usesImage)
        assertFalse(IconStyle(image = "/x").usesImage)
        assertTrue(IconStyle(bgKind = BgKind.IMAGE, image = "/x").usesImage)
    }

    @Test fun themesAreManyUniqueAndSerializable() {
        assertTrue(ICON_THEMES.size >= 30)
        assertEquals(ICON_THEMES.size, ICON_THEMES.map { it.id }.toSet().size)
        assertEquals(ICON_THEMES.size, ICON_THEMES.map { it.style }.toSet().size)
        ICON_THEMES.forEach { assertEquals(it.name, it.style, IconStyle.decode(it.style.encode())) }
    }
}

class ShapeGeometryTest {
    private fun check(name: String, pts: FloatArray, left: Float, top: Float, size: Float) {
        assertTrue("$name has points", pts.size >= 6 && pts.size % 2 == 0)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in pts.indices step 2) {
            val x = pts[i]; val y = pts[i + 1]
            assertFalse("$name NaN", x.isNaN() || y.isNaN())
            minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
        }
        val eps = 0.01f
        assertTrue("$name inside: $minX $minY $maxX $maxY", minX >= left - eps && minY >= top - eps && maxX <= left + size + eps && maxY <= top + size + eps)
        // Fitted: the long side fills the box, and it's centered.
        assertEquals("$name fills", size, maxOf(maxX - minX, maxY - minY), 0.02f)
        assertEquals("$name centered x", left + size / 2, (minX + maxX) / 2, 0.02f)
        assertEquals("$name centered y", top + size / 2, (minY + maxY) / 2, 0.02f)
        // Reasonably chunky: no shape is a sliver.
        assertTrue("$name sliver", minOf(maxX - minX, maxY - minY) > size * 0.5f)
    }

    @Test fun everyShapeFitsItsBox() {
        for (shape in IconShape.entries) for (seed in listOf(0, 7, -123, 99999)) for (corner in listOf(0f, 0.22f, 0.5f)) {
            check("$shape/$seed/$corner", ShapeGeometry.outline(shape, 17f, 9f, 400f, corner, seed), 17f, 9f, 400f)
        }
    }

    @Test fun stickersFitTheirBox() {
        Sticker.entries.filter { it != Sticker.NONE }.forEach { k ->
            val parts = ShapeGeometry.sticker(k, 5f, 5f, 100f)
            assertTrue(parts.isNotEmpty())
            check("$k", parts.reduce { a, b -> a + b }, 5f, 5f, 100f)
        }
        assertTrue(ShapeGeometry.sticker(Sticker.NONE, 0f, 0f, 10f).isEmpty())
    }

    @Test fun pebbleIsSeeded() {
        val a = ShapeGeometry.outline(IconShape.BLOB, 0f, 0f, 100f, seed = 1)
        assertTrue(a.contentEquals(ShapeGeometry.outline(IconShape.BLOB, 0f, 0f, 100f, seed = 1)))
        assertFalse(a.contentEquals(ShapeGeometry.outline(IconShape.BLOB, 0f, 0f, 100f, seed = 2)))
    }

    @Test fun cornerRadiusChangesRoundedOnly() {
        val sharp = ShapeGeometry.outline(IconShape.ROUNDED, 0f, 0f, 100f, corner = 0f)
        assertEquals(8, sharp.size) // four plain corners
        val round = ShapeGeometry.outline(IconShape.ROUNDED, 0f, 0f, 100f, corner = 0.5f)
        // Fully rounded square = circle: every point 50 from the center.
        for (i in round.indices step 2) assertEquals(50f, kotlin.math.hypot(round[i] - 50f, round[i + 1] - 50f), 0.5f)
        assertTrue(ShapeGeometry.outline(IconShape.CIRCLE, 0f, 0f, 100f, corner = 0f).contentEquals(ShapeGeometry.outline(IconShape.CIRCLE, 0f, 0f, 100f, corner = 0.4f)))
    }
}

class PuzzleMathTest {
    @Test fun gridShape() {
        assertEquals(1 to 1, PuzzleMath.grid(1))
        assertEquals(3 to 1, PuzzleMath.grid(3))
        assertEquals(4 to 1, PuzzleMath.grid(4))
        assertEquals(4 to 2, PuzzleMath.grid(5))
        assertEquals(4 to 5, PuzzleMath.grid(20))
        assertEquals(1 to 1, PuzzleMath.grid(0))
    }

    @Test fun singleIconIsCenterSquare() {
        val r = PuzzleMath.sourceRect(1000f, 2000f, null)
        assertEquals(listOf(0f, 500f, 1000f, 1500f), r.toList())
        assertEquals(r.toList(), PuzzleMath.sourceRect(1000f, 2000f, Tile(0, 1)).toList())
        // Zoom 2 = the middle half; pan right edge.
        val z = PuzzleMath.sourceRect(1000f, 1000f, null, zoom = 2f, panX = 1f)
        assertEquals(listOf(500f, 250f, 1000f, 750f), z.toList())
    }

    @Test fun tilesAreSquareInsideAndEvenlySpaced() {
        for (count in listOf(2, 4, 7, 12, 20)) for ((w, h) in listOf(1080f to 2400f, 3000f to 1000f, 800f to 800f)) {
            val rects = (0 until count).map { PuzzleMath.sourceRect(w, h, Tile(it, count), zoom = 1.3f, panX = 0.4f, panY = -0.7f) }
            val unit = rects[0][2] - rects[0][0]
            rects.forEach { r ->
                assertEquals(unit, r[2] - r[0], 0.01f)
                assertEquals(unit, r[3] - r[1], 0.01f)
                assertTrue("in bounds $count ${r.toList()}", r[0] >= -0.01f && r[1] >= -0.01f && r[2] <= w + 0.01f && r[3] <= h + 0.01f)
            }
            val (cols, _) = PuzzleMath.grid(count)
            for (i in 1 until count) {
                val a = rects[i - 1]; val b = rects[i]
                if (i % cols != 0) {
                    assertEquals(unit * (1 + PuzzleMath.GAP), b[0] - a[0], 0.01f)
                    assertEquals(a[1], b[1], 0.01f)
                }
            }
            if (count > cols) assertEquals(unit * (1 + PuzzleMath.GAP), rects[cols][1] - rects[0][1], 0.01f)
        }
    }

    @Test fun tilesCoverTheWholeCrop() {
        val count = 8
        val crop = PuzzleMath.crop(2000f, 2000f, (4 + 3 * PuzzleMath.GAP) / (2 + PuzzleMath.GAP))
        val first = PuzzleMath.sourceRect(2000f, 2000f, Tile(0, count)); val last = PuzzleMath.sourceRect(2000f, 2000f, Tile(count - 1, count))
        assertEquals(crop[0], first[0], 0.01f); assertEquals(crop[1], first[1], 0.01f)
        assertEquals(crop[2], last[2], 0.01f); assertEquals(crop[3], last[3], 0.01f)
    }
}

class IconPaletteTest {
    @Test fun seededPerApp() {
        assertEquals(IconPalette.hueFor("com.a", 3), IconPalette.hueFor("com.a", 3), 0f)
        assertNotEquals(IconPalette.hueFor("com.a", 3), IconPalette.hueFor("com.a", 4), 0f)
        val hues = (0 until 30).map { IconPalette.hueFor("com.app$it", 7) }
        assertTrue(hues.all { it in 0f..360f })
        assertTrue("spread", hues.toSet().size > 25 && hues.max() - hues.min() > 180f)
    }

    @Test fun hueRotationKeepsAlphaAndLightness() {
        val c = 0x80FF6B6B.toInt()
        val r = IconPalette.rotateHue(c, 120f)
        assertEquals(0x80, r ushr 24)
        assertEquals(IconPalette.toHsl(c)[2], IconPalette.toHsl(r)[2], 0.02f)
        assertEquals((IconPalette.toHsl(c)[0] + 120f) % 360f, IconPalette.toHsl(r)[0], 2f)
        val full = IconPalette.rotateHue(c, 360f)
        for (shift in listOf(0, 8, 16, 24)) assertTrue(abs((full shr shift and 0xFF) - (c shr shift and 0xFF)) <= 2)
    }

    @Test fun rainbowStylesDifferPerAppOnlyWhenOn() {
        val base = IconStyle(bg = listOf(0xFF6D4AFF.toInt()))
        assertEquals(base, base.forApp("com.x"))
        val on = base.copy(rainbow = true)
        val a = on.forApp("com.a"); val b = on.forApp("com.b")
        assertNotEquals(a.bg, b.bg)
        assertEquals(a, on.forApp("com.a"))
        assertFalse(a.rainbow)
        assertEquals(on, on.forApp(null))
    }
}
