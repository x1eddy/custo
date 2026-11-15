package app.custo

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.custo.feature.themes.WallEdge
import app.custo.feature.themes.WallSpec
import app.custo.feature.themes.WallStyle
import app.custo.feature.widgets.BgLook
import app.custo.feature.widgets.ClockFaces
import app.custo.feature.widgets.ClockLook
import app.custo.feature.widgets.TextFit
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetShape
import app.custo.feature.widgets.WidgetShapes
import app.custo.feature.widgets.WidgetStyle
import app.custo.feature.widgets.WordClock
import app.custo.ui.theme.AccentSource
import app.custo.ui.theme.AppAppearance
import app.custo.ui.theme.CornerStyle
import app.custo.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomizeMoreTest {

    // ---------- Backward compatibility ----------

    /** A 1.0 widget style (before shapes, backgrounds and the new faces). */
    private val v10 = """{"clock":"STACKED","themed":false,"bg":-14935009,"fg":-1,"corner":12,"font":"HEAVY","borderWidth":2,"tapAnim":true}"""

    @Test fun oldWidgetJsonGetsPlainShapeAndFill() {
        val s = WidgetStyle.decode(v10)
        assertEquals(ClockLook.STACKED, s.clock)
        assertEquals(WidgetFont.HEAVY, s.font)
        assertEquals(WidgetShape.ROUNDED, s.shape)
        assertEquals(BgLook.SOLID, s.bgLook)
        assertNull(s.bgPhoto)
        assertFalse(s.drawnShape)
        assertTrue(s.needsBitmapBg) // only for its border, as before
        assertFalse(WidgetStyle.decode("""{"corner":3}""").needsBitmapBg)
    }

    @Test fun newFieldsRoundTripAndUnknownValuesFallBack() {
        val s = WidgetStyle(clock = ClockLook.WORDS, font = WidgetFont.ELEGANT, shape = WidgetShape.TICKET, bgLook = BgLook.PHOTO,
            bgPhoto = "content://x/1", bgDim = 0.5f, bgBlur = 0.3f)
        assertEquals(s, WidgetStyle.decode(s.encode()))
        assertTrue(s.drawnShape)
        val odd = WidgetStyle.decode("""{"clock":"HOLOGRAM","font":"COMIC","shape":"STAR","bgLook":"NEON","corner":5}""")
        assertEquals(ClockLook.BIG, odd.clock)
        assertEquals(WidgetFont.SANS, odd.font)
        assertEquals(WidgetShape.ROUNDED, odd.shape)
        assertEquals(BgLook.SOLID, odd.bgLook)
        assertEquals(5, odd.corner)
    }

    @Test fun presetsCarryShapesButNeverPhotos() {
        val s = WidgetStyle(shape = WidgetShape.PILL, bgLook = BgLook.PHOTO, bgPhoto = "content://secret/9", bgDim = 0.6f)
        val text = WidgetStyle.exportPreset(s, "CLOCK")
        assertFalse(text.contains("secret"))
        val back = WidgetStyle.importPreset(text)!!
        assertEquals(WidgetShape.PILL, back.shape)
        assertEquals(BgLook.SOLID, back.bgLook)
        assertEquals(0.6f, back.bgDim)
        // A widget that has its own photo keeps it when another look is applied.
        val mine = WidgetStyle(bgPhoto = "content://mine/1").withLookOf(WidgetStyle(bgLook = BgLook.PHOTO))
        assertEquals(BgLook.PHOTO, mine.bgLook)
        assertEquals("content://mine/1", mine.bgPhoto)
        assertEquals(BgLook.GLASS, WidgetStyle().withLookOf(WidgetStyle(bgLook = BgLook.GLASS)).bgLook)
    }

    @Test fun oldWallSpecGetsGoldEdgeAndPremiumRoundTrips() {
        val old = WallSpec.decode("""{"style":"TOPO","colors":[-1],"seed":3,"angle":10.0,"grain":0.2}""")!!
        assertEquals(WallEdge.GOLD, old.edge)
        val p = WallSpec(WallStyle.MARBLE, listOf(1), seed = 5, edge = WallEdge.ROSE)
        assertEquals(p, WallSpec.decode(p.encode()))
        assertEquals(WallEdge.GOLD, WallSpec.decode("""{"style":"CARBON","edge":"PLATINUM"}""")!!.edge)
        val premium = WallStyle.entries.filter { it.premium }
        assertEquals(6, premium.size)
        assertTrue(premium.none { it.animated })
        assertEquals(WallStyle.entries.size, WallStyle.entries.map { it.label }.toSet().size)
    }

    // ---------- Clock faces fit at common widget sizes ----------

    /** Content areas (widget minus frame) for 2×1, 4×1, 2×2 and 4×2 widgets. */
    private val sizes = listOf(DpSize(100.dp, 48.dp), DpSize(250.dp, 48.dp), DpSize(100.dp, 100.dp), DpSize(250.dp, 100.dp))
    private val newFaces = listOf(ClockLook.FLIP, ClockLook.LED, ClockLook.NEON, ClockLook.VERTICAL)

    @Test fun newFacesNeverCropAtCommonSizes() {
        for (look in newFaces) for (font in listOf(WidgetFont.SANS, WidgetFont.TECH, WidgetFont.HAND)) for (raw in sizes) for (date in listOf(true, false)) {
            val area = ClockFaces.forFont(raw, ClockFaces.fontOf(look, font))
            val plan = ClockFaces.plan(look, area, 1f, date)
            val n = ClockFaces.need(plan.look, date)
            val usedW = n.w * plan.fit + n.fixedW
            val usedH = n.h * plan.fit + n.fixedH
            val what = "$look/${plan.look} $font $raw date=$date fit=${plan.fit}"
            assertTrue("$what too wide: $usedW", usedW <= area.width.value + 0.5f)
            assertTrue("$what too tall: $usedH", usedH <= area.height.value + 0.5f)
        }
    }

    @Test fun facesUseFullSizeWhenThereIsRoom() {
        newFaces.forEach { assertEquals(1f, ClockFaces.plan(it, DpSize(400.dp, 400.dp), 1f, true).fit, 0f) }
    }

    @Test fun cramped4x1VerticalFallsBackToStacked() {
        assertEquals(ClockLook.STACKED, ClockFaces.plan(ClockLook.VERTICAL, DpSize(250.dp, 48.dp), 1f, true).look)
        assertEquals(ClockLook.VERTICAL, ClockFaces.plan(ClockLook.VERTICAL, DpSize(60.dp, 220.dp), 1f, true).look)
    }

    @Test fun wideFontsGetLessRoom() {
        val a = DpSize(200.dp, 80.dp)
        assertTrue(ClockFaces.forFont(a, WidgetFont.TECH).width < a.width)
        assertEquals(a, ClockFaces.forFont(a, WidgetFont.SANS))
    }

    // ---------- Word clock ----------

    @Test fun wordClockReadsLikeSpeech() {
        assertEquals(WordClock.Words(null, listOf("half past", "ten")), WordClock.words(10, 30))
        assertEquals(WordClock.Words(null, listOf("ten", "o'clock")), WordClock.words(22, 0))
        assertEquals(WordClock.Words(null, listOf("quarter to", "eleven")), WordClock.words(10, 45))
        assertEquals(WordClock.Words(null, listOf("twenty-five past", "four")), WordClock.words(16, 25))
        assertEquals(WordClock.Words("almost", listOf("eleven", "o'clock")), WordClock.words(10, 58))
        assertEquals(WordClock.Words("just after", listOf("half past", "ten")), WordClock.words(10, 31))
        assertEquals(WordClock.Words("almost", listOf("twenty-five to", "twelve")), WordClock.words(23, 33))
        assertEquals(WordClock.Words("almost", listOf("twelve", "o'clock")), WordClock.words(23, 59))
        assertEquals(WordClock.Words(null, listOf("twelve", "o'clock")), WordClock.words(0, 0))
        assertEquals("just after ten past twelve", WordClock.words(12, 12).text)
    }

    @Test fun wordClockFitsEveryMinuteAtCommonSizes() {
        for (font in listOf(WidgetFont.SANS, WidgetFont.TECH)) for (raw in sizes) for (m in 0 until 24 * 60) {
            val area = ClockFaces.forFont(raw, font)
            val words = WordClock.words(m / 60, m % 60)
            val (lines, fit) = WordClock.arrange(words, area, 1f)
            val lead = TextFit.Line(ClockFaces.LEAD_SP, words.lead?.length ?: 0, words.lead != null)
            val big = lines.map { TextFit.Line(ClockFaces.WORD_SP, it.length) }
            val w = maxOf(TextFit.width(lead), big.maxOf { TextFit.width(it) }) * fit
            val h = (TextFit.height(lead) + big.sumOf { TextFit.height(it).toDouble() }.toFloat()) * fit
            assertTrue("${words.text} in $raw ($font): $w x $h", w <= area.width.value + 0.5f && h <= area.height.value + 0.5f)
        }
    }

    // ---------- Shapes ----------

    @Test fun shapedContentStaysInside() {
        assertEquals(0f to 0f, WidgetShapes.contentInset(WidgetShape.ROUNDED, 200f, 100f))
        val (cx, cy) = WidgetShapes.contentInset(WidgetShape.CIRCLE, 160f, 120f)
        // The text box is the circle's inscribed square, centered.
        assertEquals(160f - 2 * cx, 120f - 2 * cy, 0.01f)
        assertTrue(160f - 2 * cx <= 120f * 0.7072f)
        assertTrue(WidgetShapes.contentInset(WidgetShape.PILL, 300f, 80f).first > 0f)
        assertTrue(WidgetShapes.contentInset(WidgetShape.TICKET, 300f, 80f).first > 0f)
    }

    // ---------- App appearance ----------

    @Test fun settingsDefaults() {
        val d = AppAppearance.read(emptyPreferences())
        assertEquals(AppAppearance(), d)
        assertEquals(ThemeMode.SYSTEM, d.mode)
        assertFalse(d.amoled)
        assertEquals(AccentSource.MATERIAL_YOU, d.accent)
        assertNull(d.seed)
        assertEquals(CornerStyle.ROUNDED, d.corners)
        assertFalse(d.reduceMotion)
        assertTrue(d.isDark(true))
        assertFalse(d.isDark(false))
    }

    @Test fun settingsReadStoredValuesAndIgnoreGarbage() {
        val p = mutablePreferencesOf(
            AppAppearance.MODE to "DARK", AppAppearance.AMOLED to true, AppAppearance.ACCENT to "CUSTOM",
            AppAppearance.CUSTOM_ACCENT to 0xFF00FF00.toInt(), AppAppearance.CORNERS to "SHARP", AppAppearance.REDUCE_MOTION to true,
        )
        val a = AppAppearance.read(p)
        assertTrue(a.isDark(false))
        assertTrue(a.amoled)
        assertEquals(0xFF00FF00.toInt(), a.seed)
        assertEquals(CornerStyle.SHARP, a.corners)
        assertTrue(a.reduceMotion)
        assertEquals(AppAppearance.CUSTO_ACCENT, a.copy(accent = AccentSource.CUSTO).seed)
        val junk = AppAppearance.read(mutablePreferencesOf(AppAppearance.MODE to "PURPLE", AppAppearance.CORNERS to ""))
        assertEquals(ThemeMode.SYSTEM, junk.mode)
        assertEquals(CornerStyle.ROUNDED, junk.corners)
    }
}
