package app.custo.feature.widgetsets

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import app.custo.feature.widgets.WidgetFont
import app.custo.feature.widgets.WidgetFonts
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetLogic
import app.custo.feature.widgets.WidgetStyle
import java.time.format.TextStyle
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** A tiny pixel sprite: ARGB cells (0 = empty), already outlined. */
internal class PixSprite(val w: Int, val h: Int, val cells: IntArray)

/**
 * The Blocky set: a pixel-craft look built from 16 x 16 textures drawn nearest-neighbour (grass, dirt,
 * cobblestone, planks, stone), pixel sprites, game-UI widgets (hearts, XP bar, hotbar, inventory grid)
 * and a full pixel-world wallpaper. All art is original and generic to the voxel-sandbox genre.
 */
internal object Blocky {
    const val OUT = 0xFF1C1A1E.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val SPLASH = 0xFFFFFF55.toInt()
    private const val XP = 0xFF80FF20.toInt()
    private const val GUI = 0xFFC6C6C6.toInt()
    private const val GUI_DARK = 0xFF555555.toInt()
    private const val GUI_TEXT = 0xFF3F3F3F.toInt()
    private const val SLOT = 0xFF8B8B8B.toInt()
    private const val SLOT_DARK = 0xFF373737.toInt()

    // ---------- Sprites (ASCII pixel art; '.' is empty, an outline is added around every sprite) ----------

    private val PAL = mapOf(
        'k' to 0xFF1C1A1E.toInt(),
        'C' to 0xFFD8FFFB.toInt(), 'c' to 0xFF4BE3D3.toInt(), 'd' to 0xFF1E9F94.toInt(), 'D' to 0xFF0F625C.toInt(),
        'w' to 0xFFA27A46.toInt(), 'W' to 0xFF66461F.toInt(), 'v' to 0xFFC9A064.toInt(),
        'r' to 0xFFE32B2B.toInt(), 'R' to 0xFF99161B.toInt(), 'p' to 0xFFFF9C9C.toInt(),
        'g' to 0xFF5DB339.toInt(), 'G' to 0xFF367420.toInt(), 'h' to 0xFFA6E86E.toInt(),
        'y' to 0xFFFFE45C.toInt(), 'o' to 0xFFFF951F.toInt(), 'Y' to 0xFFFFFBD6.toInt(), 'O' to 0xFFD9561A.toInt(),
        's' to 0xFF8E8E8E.toInt(), 'S' to 0xFF5E5E5E.toInt(), 'l' to 0xFFC2C2C2.toInt(),
        'i' to 0xFFE2E2E2.toInt(), 'I' to 0xFFA3A3A3.toInt(),
        'b' to 0xFF3F76E4.toInt(), 'B' to 0xFF8FB4FF.toInt(),
        'n' to 0xFF7A5434.toInt(), 'N' to 0xFF54371F.toInt(),
        'x' to 0xFF262626.toInt(), 'u' to 0xFFFFFFFF.toInt(), 'e' to 0xFF3A1214.toInt(),
    )

    /** Parses ASCII art with [PAL], pads it by one and outlines it. */
    private fun sprite(src: String, outline: Boolean = true): PixSprite {
        val rows = src.trimIndent().lines().filter { it.isNotBlank() }.map { it.trim() }
        val pad = if (outline) 1 else 0
        val w = rows.maxOf { it.length } + pad * 2
        val h = rows.size + pad * 2
        val cells = IntArray(w * h)
        rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> if (ch != '.') cells[(j + pad) * w + i + pad] = PAL[ch] ?: OUT } }
        if (outline) {
            val src2 = cells.copyOf()
            fun filled(i: Int, j: Int) = i in 0 until w && j in 0 until h && src2[j * w + i] != 0
            for (j in 0 until h) for (i in 0 until w) {
                if (src2[j * w + i] == 0 && (filled(i - 1, j) || filled(i + 1, j) || filled(i, j - 1) || filled(i, j + 1))) cells[j * w + i] = OUT
            }
        }
        return PixSprite(w, h, cells)
    }

    val PICKAXE = sprite("""
        ................
        ....CCCCCc......
        ..CCcccccccc....
        .Cddd.....dccC..
        ..........wWdcC.
        .........wW..dcC
        ........wW...dcC
        .......wW.....dc
        ......wW......dc
        .....wW........d
        ....wW..........
        ...wW...........
        ..wW............
        .vW.............
        .W..............
    """)

    val SWORD = sprite("""
        .............CC.
        ............CcC.
        ...........Ccd..
        ..........Ccd...
        .........Ccd....
        ........Ccd.....
        .......Ccd......
        ...W..Ccd.......
        ...WWCcd........
        ....WWd.........
        ....wWWW........
        ...wv..WW.......
        ..wv............
        .WW.............
    """)

    val TORCH = sprite("""
        .......y........
        ......yoy.......
        .....yYYoy......
        ......yYo.......
        ......oYYo......
        .......ww.......
        .......wW.......
        .......wW.......
        .......wW.......
        .......wW.......
        .......wW.......
        .......wW.......
        .......wW.......
        .......WW.......
    """)

    val APPLE = sprite("""
        ........W.......
        .......Wgg......
        .......Wggg.....
        ...rrr.W.rrr....
        ..rrrrrrrrrrr...
        .rppRrrrrrrrrr..
        .rprrrrrrrrrrr..
        .rrrrrrrrrrrrr..
        .rrrrrrrrrrrrR..
        .rrrrrrrrrrrRR..
        ..rrrrrrrrrRR...
        ...rrrrrrRRR....
        ....rrR.RRR.....
    """)

    val SAPLING = sprite("""
        ......hg........
        .....hgg.gg.....
        ....hgGg.ggh....
        .....gGggGgg....
        ..gg..gGGg......
        .hggg..gG.ggg...
        .hgGgg.gGggGgh..
        ..ggGgggGgGgg...
        ....gGGgGgg.....
        ......gGG.......
        .......Wg.......
        .......wW.......
        ......wWWw......
        ....nnNNNNnn....
    """)

    val HEART = sprite("""
        ..rrrr...rrrr...
        .rpprrr.rrrrrr..
        .rpurrrrrrrrrR..
        .rprrrrrrrrrrR..
        .rrrrrrrrrrrrR..
        ..rrrrrrrrrrR...
        ...rrrrrrrrR....
        ....rrrrrrR.....
        .....rrrrR......
        ......rrR.......
        .......R........
    """)

    val GEM = sprite("""
        .....CCCCCC.....
        ....CcccccdD....
        ...CccCcccddD...
        ..CccCccccdddD..
        ..dddddddddDDD..
        ...ddcccccdDD...
        ....dcccccdD....
        .....dcccdD.....
        ......dccD......
        .......dD.......
    """)

    val ORE = sprite("""
        ssssssssSsssssss
        sSssCcssssslssss
        ssscCcdsssssssSs
        ssssddsssSsssccs
        slssssssssssCcds
        ssssSsslsssssdss
        ssssssssssssssss
        sssccsssSsssssls
        ssCcdssssssssSss
        sssdsssssCcsssss
        sSsssslsscCdssss
        ssssssssssddssSs
        ssslssSsssssssss
        ssssssssssssssss
        sSsssssssslsssss
        ssssssssssssssss
    """)

    val BUCKET = sprite("""
        .....IIIIII.....
        ....I......I....
        ...I........I...
        ...iiiiiiiiiI...
        ...iBbbbbbbbI...
        ...ibbbbbbbbI...
        ....ibbbbbbI....
        ....iiiiiiII....
        ....iiiiiiII....
        .....iiiiII.....
        .....iiiiII.....
        ......IIII......
    """)

    val BENCH = sprite("""
        WWWWWWWWWWWWWWWW
        WvvvWvvvvWvvvvvW
        WvwwWwwwwWwwwwvW
        WWWWWWWWWWWWWWWW
        WvwwWwwwwWwwwwvW
        WWWWWWWWWWWWWWWW
        nwwwwwwwwwwwwwwn
        nwIIIwwwwwwwwwwn
        nwIIIwwwwwWWWwwn
        nwwSwwwwwwwWwwwn
        nwwSwwwwwwwWwwwn
        nwwSwwwwwwwWwwwn
        nwwSwwwwwwwWwwwn
        nwwwwwwwwwwwwwwn
        nNwwwwwwwwwwwwNn
        nnnnnnnnnnnnnnnn
    """)

    val CRATE = sprite("""
        WWWWWWWWWWWWWWWW
        WrrrrrrrrrrrrrrW
        WrprrrrrrrrrrrRW
        WrrrrrrrrrrrrrRW
        WyxxyyxxyyxxyyxW
        WuuuuuuuuuuuuuuW
        WuuuuuuuuuuuuuuW
        WuuuuuuuuuuuuuuW
        WuuuuuuuuuuuuuuW
        WuuuuuuuuuuuuuuW
        WuuuuuuuuuuuuuuW
        WxyyxxyyxxyyxxyW
        WrrrrrrrrrrrrrRW
        WrrrrrrrrrrrrRRW
        WrRRRRRRRRRRRRRW
        WWWWWWWWWWWWWWWW
    """)

    val CLOCK_ITEM = sprite("""
        .....oyyyyo.....
        ....yBBBBBBy....
        ...yBBBBYBBBy...
        ..yBBBBYYYBBBy..
        .yBBBBBBYBBBBBy.
        .yBBBBBBBBBBBBy.
        .ygggggggggggGy.
        .yxxxxxxxxxxxxy.
        .yxxuxxxxxxxxxy.
        .yxxxxxxxIxxxxy.
        ..yxxxxxxxxuxy..
        ...yxxxxxxxxy...
        ....oxxxxxxo....
        .....oyyyyo.....
    """)

    val SUN = sprite("""
        .......yy.......
        .y.....yy.....y.
        ..y..........y..
        ....oooooooo....
        ....oyyyyyyo....
        yy..oyYYYyyo..yy
        yy..oyYYyyyo..yy
        ....oyyyyyyo....
        ....oooooooo....
        ..y..........y..
        .y.....yy.....y.
        .......yy.......
    """)

    private val HEART_FULL = sprite("""
        .kkk.kkk.
        kpprkrrrk
        kpurrrrRk
        krrrrrrRk
        .krrrrRk.
        ..krrRk..
        ...kRk...
        ....k....
    """, outline = false)

    private val HEART_EMPTY = sprite("""
        .kkk.kkk.
        keeekeeek
        keeeeeeek
        keeeeeeek
        .keeeeek.
        ..keeek..
        ...kek...
        ....k....
    """, outline = false)

    private val BOLT = sprite("""
        ...yy
        ..yy.
        .yyyy
        ..yy.
        .yy..
        yy...
    """)

    /** Draws a sprite centered on ([cx], [cy]), [size] px for its longer side, crisp (no smoothing). */
    fun drawSprite(c: Canvas, sp: PixSprite, cx: Float, cy: Float, size: Float, clipRight: Float = Float.MAX_VALUE) {
        val px = size / max(sp.w, sp.h)
        val ox = cx - sp.w * px / 2; val oy = cy - sp.h * px / 2
        val p = Paint()
        for (j in 0 until sp.h) for (i in 0 until sp.w) {
            val col = sp.cells[j * sp.w + i]
            if (col == 0) continue
            val l = (ox + i * px).roundToInt().toFloat()
            if (l >= clipRight) continue
            p.color = col
            c.drawRect(l, (oy + j * px).roundToInt().toFloat(), (ox + (i + 1) * px).roundToInt().toFloat(), (oy + (j + 1) * px).roundToInt().toFloat(), p)
        }
    }

    private fun art(sp: PixSprite): StickerArt = { drawSprite(c, sp, cx, cy, s * 0.96f) }

    val pickaxe: StickerArt = art(PICKAXE)
    val sword: StickerArt = art(SWORD)
    val apple: StickerArt = art(APPLE)
    val sapling: StickerArt = art(SAPLING)
    val heart: StickerArt = art(HEART)
    val gem: StickerArt = art(GEM)
    val ore: StickerArt = art(ORE)
    val bucket: StickerArt = art(BUCKET)
    val bench: StickerArt = art(BENCH)
    val clockItem: StickerArt = art(CLOCK_ITEM)

    /** Torch with a warm flicker of loose sparks around the flame. */
    val torch: StickerArt = {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy - s * 0.3f, s * 0.42f, intArrayOf(0x66FFD25A, 0x00FFB020), null, Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy - s * 0.3f, s * 0.42f, glow)
        drawSprite(c, TORCH, cx, cy, s * 0.96f)
        val px = s * 0.96f / 18f
        val spark = Paint().apply { color = 0xFFFFE45C.toInt() }
        listOf(-4.5f to -8.5f, 3.5f to -9.5f, 4.5f to -6f, -3.5f to -5.5f).forEach { (u, v) ->
            c.drawRect(cx + u * px, cy + v * px, cx + (u + 1) * px, cy + (v + 1) * px, spark)
        }
    }

    /** A red blast crate with hazard tape and BOOM stencilled on the band (not a copy of any game's block). */
    val crate: StickerArt = {
        val size = s * 0.96f
        drawSprite(c, CRATE, cx, cy, size)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = WidgetFonts.typeface(WidgetFont.PIXEL); textAlign = Paint.Align.CENTER; color = 0xFFC81E1E.toInt(); textSize = size * 0.27f
        }
        val wNow = tp.measureText("BOOM")
        if (wNow > size * 0.72f) tp.textSize *= size * 0.72f / wNow
        c.drawText("BOOM", cx, cy - (tp.fontMetrics.ascent + tp.fontMetrics.descent) / 2, tp)
    }

    // ---------- Textures (16 x 16, generated once) ----------

    private fun bmp(px: IntArray): Bitmap = Bitmap.createBitmap(px, 16, 16, Bitmap.Config.ARGB_8888)

    private fun pick(r: Random, vararg weighted: Pair<Float, Int>): Int {
        var f = r.nextFloat()
        weighted.forEach { (w, col) -> if (f < w) return col; f -= w }
        return weighted.last().second
    }

    private fun dirtPx(r: Random) = pick(
        r, 0.36f to 0xFF866043.toInt(), 0.28f to 0xFF79553A.toInt(), 0.16f to 0xFF6C4A31.toInt(),
        0.1f to 0xFF96693F.toInt(), 0.06f to 0xFF593D29.toInt(), 0.04f to 0xFFB9855C.toInt(),
    )

    private fun greenPx(r: Random) = pick(
        r, 0.4f to 0xFF62A83A.toInt(), 0.25f to 0xFF579A31.toInt(), 0.2f to 0xFF72BA45.toInt(), 0.15f to 0xFF4C8B2B.toInt(),
    )

    private fun stonePx(r: Random) = pick(
        r, 0.42f to 0xFF7E7E7E.toInt(), 0.24f to 0xFF747474.toInt(), 0.18f to 0xFF888888.toInt(), 0.1f to 0xFF6B6B6B.toInt(), 0.06f to 0xFF959595.toInt(),
    )

    val DIRT: Bitmap by lazy { val r = Random(3); bmp(IntArray(256) { dirtPx(r) }) }

    val GRASS_SIDE: Bitmap by lazy {
        val r = Random(11)
        val depth = IntArray(16) { 3 + r.nextInt(3) + if (r.nextFloat() < 0.2f) 2 else 0 }
        bmp(IntArray(256) { k ->
            val i = k % 16; val j = k / 16
            when {
                j < depth[i] - 1 -> greenPx(r)
                j == depth[i] - 1 -> if (r.nextFloat() < 0.6f) 0xFF3F7A26.toInt() else greenPx(r)
                else -> dirtPx(r)
            }
        })
    }

    val STONE: Bitmap by lazy { val r = Random(5); bmp(IntArray(256) { stonePx(r) }) }

    /** Pale stone for the big title letters. */
    private val LIGHT_STONE: Bitmap by lazy {
        val r = Random(7)
        bmp(IntArray(256) { pick(r, 0.4f to 0xFFC4C4C4.toInt(), 0.25f to 0xFFB4B4B4.toInt(), 0.2f to 0xFFD2D2D2.toInt(), 0.15f to 0xFFA2A2A2.toInt()) })
    }

    val COBBLE: Bitmap by lazy {
        val r = Random(9)
        val pts = List(8) { (r.nextInt(16) to r.nextInt(16)) }
        val shades = intArrayOf(0xFF8A8A8A.toInt(), 0xFF7B7B7B.toInt(), 0xFF9A9A9A.toInt(), 0xFF707070.toInt(), 0xFF858585.toInt(), 0xFF949494.toInt(), 0xFF7F7F7F.toInt(), 0xFF8E8E8E.toInt())
        fun cell(i: Int, j: Int): Int {
            var best = 0; var bd = Int.MAX_VALUE
            pts.forEachIndexed { n, (x, y) ->
                val dx = min(Math.floorMod(i - x, 16), Math.floorMod(x - i, 16)); val dy = min(Math.floorMod(j - y, 16), Math.floorMod(y - j, 16))
                val dd = dx * dx + dy * dy * 2
                if (dd < bd) { bd = dd; best = n }
            }
            return best
        }
        val ids = IntArray(256) { cell(it % 16, it / 16) }
        bmp(IntArray(256) { k ->
            val i = k % 16; val j = k / 16
            val id = ids[k]
            val right = ids[j * 16 + (i + 1) % 16]; val down = ids[((j + 1) % 16) * 16 + i]
            val up = ids[((j + 15) % 16) * 16 + i]; val left = ids[j * 16 + (i + 15) % 16]
            when {
                id != right || id != down -> if (r.nextFloat() < 0.7f) 0xFF4A4A4A.toInt() else 0xFF3A3A3A.toInt()
                up != id || left != id -> PackAKit.shade(shades[id], 1.18f)
                r.nextFloat() < 0.15f -> PackAKit.shade(shades[id], 0.88f)
                else -> shades[id]
            }
        })
    }

    val PLANKS: Bitmap by lazy {
        val r = Random(13)
        bmp(IntArray(256) { k ->
            val i = k % 16; val j = k / 16
            val board = j / 4; val row = j % 4
            val seam = (board * 7 + 3) % 16
            when {
                row == 3 -> 0xFF6B4F2C.toInt()
                i == seam -> 0xFF7A5C34.toInt()
                row == 0 && r.nextFloat() < 0.5f -> 0xFFB5935C.toInt()
                else -> pick(r, 0.5f to 0xFFA2824E.toInt(), 0.25f to 0xFF987848.toInt(), 0.25f to 0xFFAB8A54.toInt())
            }
        })
    }

    private val DEEP: Bitmap by lazy {
        val r = Random(17)
        bmp(IntArray(256) { k ->
            val j = k / 16
            if (j % 4 == 0 && r.nextFloat() < 0.6f) 0xFF3B3B42.toInt()
            else pick(r, 0.5f to 0xFF4D4D55.toInt(), 0.3f to 0xFF45454C.toInt(), 0.2f to 0xFF5A5A62.toInt())
        })
    }

    private val BEDROCK: Bitmap by lazy {
        val r = Random(19)
        bmp(IntArray(256) { pick(r, 0.35f to 0xFF565656.toInt(), 0.3f to 0xFF2F2F2F.toInt(), 0.2f to 0xFF7D7D7D.toInt(), 0.15f to 0xFF1F1F1F.toInt()) })
    }

    private val LEAVES: Bitmap by lazy {
        val r = Random(23)
        bmp(IntArray(256) { pick(r, 0.34f to 0xFF3F8A2B.toInt(), 0.26f to 0xFF4FA235.toInt(), 0.18f to 0xFF2F6B1F.toInt(), 0.12f to 0xFF1F4A16.toInt(), 0.1f to 0xFF68BE47.toInt()) })
    }

    private val LOG: Bitmap by lazy {
        val r = Random(29)
        bmp(IntArray(256) { k ->
            val i = k % 16
            val base = when (i % 4) { 0 -> 0xFF5B4428.toInt(); 1 -> 0xFF6B5233.toInt(); 2 -> 0xFF7A5E3B.toInt(); else -> 0xFF64492C.toInt() }
            if (r.nextFloat() < 0.18f) PackAKit.shade(base, 0.8f) else base
        })
    }

    private val LAVA: Bitmap by lazy {
        val r = Random(31)
        bmp(IntArray(256) { k ->
            val i = k % 16; val j = k / 16
            val wave = sin(i * 0.8 + j * 0.5) + sin(j * 1.1 - i * 0.3)
            when {
                wave > 1.2 -> 0xFFFFE066.toInt()
                wave > 0.4 -> 0xFFFFA51F.toInt()
                wave > -0.6 -> if (r.nextFloat() < 0.2f) 0xFFFF8A14.toInt() else 0xFFEE6A0E.toInt()
                else -> 0xFFC4440A.toInt()
            }
        })
    }

    private fun oreTex(seed: Int, light: Int, mid: Int, dark: Int): Bitmap {
        val r = Random(seed)
        val px = IntArray(256) { stonePx(r) }
        val blobs = listOf(3 to 3, 11 to 5, 5 to 11, 12 to 12)
        blobs.forEach { (x, y) ->
            listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1, -1 to 1, 2 to 1, 1 to 2).forEachIndexed { n, (dx, dy) ->
                if (n > 3 && r.nextFloat() < 0.4f) return@forEachIndexed
                val col = when (n) { 0 -> light; 3, 6 -> dark; else -> mid }
                px[((y + dy) and 15) * 16 + ((x + dx) and 15)] = col
            }
        }
        return bmp(px)
    }

    private val COAL by lazy { oreTex(41, 0xFF4A4A4A.toInt(), 0xFF2A2A2A.toInt(), 0xFF141414.toInt()) }
    private val IRON by lazy { oreTex(43, 0xFFF2D2B8.toInt(), 0xFFD8AF93.toInt(), 0xFFAF8E77.toInt()) }
    private val GOLD by lazy { oreTex(47, 0xFFFFFBA0.toInt(), 0xFFFCEE4B.toInt(), 0xFFDBA213.toInt()) }
    private val DIAMOND by lazy { oreTex(53, 0xFFD8FFFB.toInt(), 0xFF5DECF5.toInt(), 0xFF1AA6A6.toInt()) }
    private val RUBY by lazy { oreTex(59, 0xFFFFB0B0.toInt(), 0xFFE0242E.toInt(), 0xFF8E0E16.toInt()) }

    /** Fills [r] with [tex] tiled at [t] px per texel, the tiles anchored at ([ox], [oy]). Crisp, no smoothing. */
    fun fill(c: Canvas, r: RectF, tex: Bitmap, t: Float, ox: Float = r.left, oy: Float = r.top) {
        val p = Paint().apply {
            isFilterBitmap = false
            shader = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                setLocalMatrix(Matrix().apply { setScale(t, t); postTranslate(ox, oy) })
            }
        }
        c.drawRect(r, p)
    }

    /** Texel size in px for a widget drawn at [d] px per dp: whole pixels so every texel is the same size. */
    fun texel(d: Float) = max(1f, (1.5f * d).roundToInt().toFloat())

    private fun snap(r: RectF, t: Float) = RectF(
        (r.left / t).roundToInt() * t, (r.top / t).roundToInt() * t, (r.right / t).roundToInt() * t, (r.bottom / t).roundToInt() * t,
    )

    private fun solid(c: Canvas, l: Float, t: Float, r: Float, b: Float, col: Int) = c.drawRect(l, t, r, b, Paint().apply { color = col })

    /** Rectangle with one-texel notches at the corners, the pixel-art way to round a box. */
    private fun notched(c: Canvas, r: RectF, t: Float, col: Int) {
        val p = Paint().apply { color = col }
        c.drawRect(r.left + t, r.top, r.right - t, r.bottom, p)
        c.drawRect(r.left, r.top + t, r.left + t, r.bottom - t, p)
        c.drawRect(r.right - t, r.top + t, r.right, r.bottom - t, p)
    }

    /** Light top-left / dark bottom-right edges, [n] texels wide. */
    private fun bevel(c: Canvas, r: RectF, t: Float, light: Int, dark: Int, n: Int = 1) {
        val e = t * n
        solid(c, r.left, r.top, r.right - e, r.top + e, light)
        solid(c, r.left, r.top, r.left + e, r.bottom - e, light)
        solid(c, r.left + e, r.bottom - e, r.right, r.bottom, dark)
        solid(c, r.right - e, r.top + e, r.right, r.bottom, dark)
    }

    // ---------- Frames ----------

    private enum class Mat { GRASS, PLANKS, COBBLE, GUI, NONE }

    private fun matFor(kind: WidgetKind?, slot: Int) = when (kind) {
        WidgetKind.APPS -> Mat.NONE
        WidgetKind.CALENDAR -> Mat.GUI
        WidgetKind.PHOTO, WidgetKind.COUNTDOWN, WidgetKind.CAROUSEL -> Mat.PLANKS
        WidgetKind.BATTERY, WidgetKind.BATTERY_RING -> Mat.COBBLE
        WidgetKind.BANNER, WidgetKind.CLOCK, WidgetKind.DATE, null -> Mat.GRASS
        else -> if (slot % 2 == 1) Mat.PLANKS else Mat.GRASS
    }

    /** A block: dark outline, textured face (grass-topped dirt, planks or cobble), bevel and a hard drop shadow. */
    private fun block(c: Canvas, b: RectF, t: Float, mat: Mat) {
        notched(c, RectF(b.left + t * 2, b.top + t * 2, b.right + t * 2, b.bottom + t * 2), t, 0x59000000)
        notched(c, b, t, OUT)
        val inner = RectF(b.left + t, b.top + t, b.right - t, b.bottom - t)
        when (mat) {
            Mat.GRASS -> {
                fill(c, inner, DIRT, t, b.left, b.top)
                c.withClip(RectF(inner.left, inner.top, inner.right, min(inner.bottom, b.top + 16 * t))) { fill(this, inner, GRASS_SIDE, t, b.left, b.top + t) }
            }
            Mat.PLANKS -> fill(c, inner, PLANKS, t, b.left, b.top)
            else -> fill(c, inner, COBBLE, t, b.left, b.top)
        }
        bevel(c, inner, t, 0x40FFFFFF, 0x4D000000)
        listOf(inner.left to inner.top, inner.right - t to inner.top, inner.left to inner.bottom - t, inner.right - t to inner.bottom - t).forEach { (x, y) ->
            solid(c, x, y, x + t, y + t, OUT)
        }
    }

    /** The inventory-screen panel: light grey with a white top-left and grey bottom-right bevel. */
    private fun guiPanel(c: Canvas, b: RectF, t: Float) {
        notched(c, RectF(b.left + t * 2, b.top + t * 2, b.right + t * 2, b.bottom + t * 2), t, 0x59000000)
        notched(c, b, t, OUT)
        val inner = RectF(b.left + t, b.top + t, b.right - t, b.bottom - t)
        solid(c, inner.left, inner.top, inner.right, inner.bottom, GUI)
        bevel(c, inner, t, WHITE, GUI_DARK, 2)
        listOf(inner.left to inner.top, inner.right - t to inner.bottom - t).forEach { (x, y) -> solid(c, x, y, x + t, y + t, GUI) }
        solid(c, inner.left, inner.top, inner.left + t, inner.top + t, OUT)
        solid(c, inner.right - t, inner.bottom - t, inner.right, inner.bottom, OUT)
    }

    val frame = FramePainter { c, _, card, _, d, seed ->
        val kind = WidgetKind.entries.getOrNull(seed / 31)
        val t = texel(d)
        val b = snap(card, t)
        when (val mat = matFor(kind, seed % 31)) {
            Mat.NONE -> Unit
            Mat.GUI -> guiPanel(c, b, t)
            else -> block(c, b, t, mat)
        }
    }

    // ---------- Pixel text ----------

    private fun tp(size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = WidgetFonts.typeface(WidgetFont.PIXEL); textSize = size; this.color = color; textAlign = align
    }

    private fun Paint.fitTo(s: String, maxW: Float): Paint {
        val w = measureText(s)
        if (w > maxW && w > 0) textSize *= maxW / w
        return this
    }

    /** Baseline that centers the paint's text on [cy]. */
    private fun Paint.mid(cy: Float) = cy - (fontMetrics.ascent + fontMetrics.descent) / 2

    /** Text with the game's hard drop shadow: the same text a "font pixel" down-right, at a quarter of the color. */
    private fun say(c: Canvas, s: String, x: Float, y: Float, p: Paint, shadow: Int? = PackAKit.shade(p.color, 0.25f)) {
        if (shadow != null) {
            val o = max(1f, (p.textSize / 11f).roundToInt().toFloat())
            c.drawText(s, x + o, y + o, Paint(p).apply { color = shadow })
        }
        c.drawText(s, x, y, p)
    }

    /** Text with a one-pixel black edge all round (the XP-number look). */
    private fun edged(c: Canvas, s: String, x: Float, y: Float, p: Paint) {
        val o = max(1f, (p.textSize / 12f).roundToInt().toFloat())
        val e = Paint(p).apply { color = 0xFF000000.toInt() }
        listOf(-o to 0f, o to 0f, 0f to -o, 0f to o).forEach { (dx, dy) -> c.drawText(s, x + dx, y + dy, e) }
        c.drawText(s, x, y, p)
    }

    /** Translucent dark panel (the chat-box look) that keeps text readable on textures. */
    private fun shade(c: Canvas, r: RectF, t: Float, alpha: Int = 0x80) = notched(c, r, t, alpha shl 24)

    // ---------- HUD ----------

    val hud = object : ThemeHud {
        override fun mode(kind: WidgetKind): HudMode? = when (kind) {
            WidgetKind.CLOCK -> HudMode.BEHIND
            WidgetKind.BANNER, WidgetKind.BATTERY, WidgetKind.DATE, WidgetKind.COUNTDOWN, WidgetKind.CALENDAR, WidgetKind.PHOTO -> HudMode.REPLACE
            WidgetKind.APPS -> HudMode.SLOTS
            else -> null
        }

        override fun appSlots(apps: Int, w: Float, h: Float): Int =
            max(apps, min(9, floor(w / (h * 0.8f).coerceAtLeast(1f)).toInt()).coerceAtLeast(1))

        override fun draw(c: Canvas, theme: WidgetTheme, kind: WidgetKind, style: WidgetStyle, data: HudData, card: RectF, content: RectF, d: Float) {
            val t = texel(d)
            val b = snap(card, t)
            when (kind) {
                WidgetKind.BANNER -> banner(c, style, b, t)
                WidgetKind.CLOCK -> shade(c, snap(RectF(content.left - 2 * t, content.top - 2 * t, content.right + 2 * t, content.bottom + 2 * t), t), t, 0x66)
                WidgetKind.BATTERY -> hearts(c, data, b, t)
                WidgetKind.DATE -> xp(c, data, b, t)
                WidgetKind.COUNTDOWN -> countdown(c, style, data, b, t)
                WidgetKind.CALENDAR -> inventory(c, style, data, b, t)
                WidgetKind.PHOTO -> painting(c, b, t)
                WidgetKind.APPS -> hotbar(c, theme, style, content, t, d)
                else -> Unit
            }
        }
    }

    /** Big stone letters with a dark 3-D extrusion, and a tilted yellow splash line. */
    private fun banner(c: Canvas, style: WidgetStyle, b: RectF, t: Float) {
        val area = RectF(b.left + 4 * t, b.top + 5 * t, b.right - 4 * t, b.bottom - 3 * t)
        shade(c, snap(area, t), t, 0x59)
        val sub = style.bannerSub.trim()
        val titleArea = if (sub.isEmpty()) area else RectF(area.left, area.top, area.right, area.top + area.height() * 0.66f)
        val title = style.bannerText.ifBlank { " " }
        val p = tp(titleArea.height() * 1.15f, WHITE).fitTo(title, area.width() * 0.74f)
        val path = Path()
        p.getTextPath(title, 0, title.length, titleArea.centerX(), p.mid(titleArea.centerY() + p.textSize * 0.04f), path)
        val e = max(1f, p.textSize / 16f)
        val texel = max(1f, (p.textSize / 14f).roundToInt().toFloat())
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = max(2f, p.textSize / 9f); color = OUT; strokeJoin = Paint.Join.MITER })
        for (k in 4 downTo 1) {
            c.withTranslation(k * e * 0.5f, k * e) { drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (k == 4) 0xFF1E1E22.toInt() else 0xFF3C3C42.toInt() }) }
        }
        c.withClip(path) {
            fill(this, RectF(titleArea.left, titleArea.top - p.textSize, titleArea.right, titleArea.bottom + p.textSize), LIGHT_STONE, texel, titleArea.left, titleArea.top)
            val bounds = RectF(); path.computeBounds(bounds, true)
            drawRect(bounds, Paint().apply {
                shader = LinearGradient(0f, bounds.top, 0f, bounds.bottom, intArrayOf(0x66FFFFFF, 0x00FFFFFF, 0x59000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            })
        }
        if (sub.isNotEmpty()) {
            val sp = tp(area.height() * 0.24f, SPLASH).fitTo(sub, area.width() * 0.7f)
            val cx = area.centerX() + area.width() * 0.12f
            val cy = area.bottom - area.height() * 0.15f
            c.withRotation(-5f, cx, cy) { say(this, sub, cx, sp.mid(cy), sp) }
        }
    }

    /** Ten hearts: battery level in half-heart steps, the percent above. */
    private fun hearts(c: Canvas, data: HudData, b: RectF, t: Float) {
        val panel = snap(RectF(b.left + 3 * t, b.top + 3 * t, b.right - 3 * t, b.bottom - 3 * t), t)
        shade(c, panel, t, 0x8C)
        val inner = RectF(panel.left + 3 * t, panel.top + 2 * t, panel.right - 3 * t, panel.bottom - 2 * t)
        val pitch = min(inner.width() / 10f, inner.height() * 0.5f)
        val heart = pitch * 0.92f
        val rowW = pitch * 10
        val hx = inner.centerX() - rowW / 2
        val hy = inner.bottom - heart * 0.55f
        val halves = ((data.battery + 4) / 5).coerceIn(0, 20)
        for (i in 0 until 10) {
            val cx = hx + pitch * (i + 0.5f)
            drawSprite(c, HEART_EMPTY, cx, hy, heart)
            val filled = halves - i * 2
            if (filled >= 2) drawSprite(c, HEART_FULL, cx, hy, heart)
            else if (filled == 1) drawSprite(c, HEART_FULL, cx, hy, heart, clipRight = cx)
        }
        val textBand = RectF(hx, inner.top, hx + rowW, hy - heart * 0.6f)
        val p = tp(min(textBand.height() * 0.95f, 20f * t), WHITE, Paint.Align.RIGHT)
        val y = p.mid(textBand.centerY())
        say(c, "${data.battery}%", textBand.right, y, p)
        val lp = tp(p.textSize * 0.62f, 0xFFDDDDDD.toInt(), Paint.Align.LEFT)
        say(c, "Battery", textBand.left, lp.mid(textBand.centerY()), lp)
        if (data.charging) {
            val bw = p.measureText("${data.battery}%")
            val lw = lp.measureText("Battery")
            val bx = textBand.right - bw - p.textSize * 0.5f
            if (bx - p.textSize * 0.4f > textBand.left + lw) drawSprite(c, BOLT, bx, textBand.centerY(), p.textSize * 0.8f)
        }
    }

    /** Year progress as an experience bar with a green level (the day of the year) above it. */
    private fun xp(c: Canvas, data: HudData, b: RectF, t: Float) {
        val panel = snap(RectF(b.left + 3 * t, b.top + 5 * t, b.right - 3 * t, b.bottom - 3 * t), t)
        shade(c, panel, t, 0x80)
        val inner = RectF(panel.left + 3 * t, panel.top + t, panel.right - 3 * t, panel.bottom - 2 * t)
        val frac = WidgetLogic.yearProgress(data.today)
        val barH = max(3 * t, (inner.height() * 0.16f / t).roundToInt() * t)
        val barTop = snap(RectF(0f, inner.top + inner.height() * 0.56f, 0f, 0f), t).top
        val bar = RectF(inner.left, barTop, inner.right, barTop + barH)
        solid(c, bar.left - t, bar.top - t, bar.right + t, bar.bottom + t, 0xFF000000.toInt())
        solid(c, bar.left, bar.top, bar.right, bar.bottom, 0xFF1F2A12.toInt())
        val fillR = bar.left + ((bar.width() * frac / t).roundToInt() * t)
        solid(c, bar.left, bar.top, fillR, bar.bottom, 0xFF5CC21E.toInt())
        solid(c, bar.left, bar.top, fillR, bar.top + t, 0xFFA6FF5A.toInt())
        solid(c, bar.left, bar.bottom - t, fillR, bar.bottom, 0xFF3C8A12.toInt())
        for (n in 1 until 18) {
            val x = (bar.left + bar.width() * n / 18f).let { (it / t).roundToInt() * t }
            solid(c, x, bar.top, x + t, bar.bottom, 0x66000000)
        }
        val lv = tp(min(bar.top - inner.top - t, 22f * t), XP)
        edged(c, "${data.today.dayOfYear}", inner.centerX(), lv.mid((inner.top + bar.top - t) / 2), lv)
        val cap = tp(min(inner.bottom - bar.bottom - t, 9f * t), 0xFFE8E8E8.toInt())
        val pct = (frac * 100).roundToInt()
        val line = "${data.today.year} · $pct%"
        cap.fitTo(line, inner.width())
        say(c, line, inner.centerX(), cap.mid((bar.bottom + t + inner.bottom) / 2 + t), cap)
    }

    private fun countdown(c: Canvas, style: WidgetStyle, data: HudData, b: RectF, t: Float) {
        val panel = snap(RectF(b.left + 3 * t, b.top + 3 * t, b.right - 3 * t, b.bottom - 3 * t), t)
        shade(c, panel, t, 0x8C)
        val inner = RectF(panel.left + 2 * t, panel.top + 2 * t, panel.right - 2 * t, panel.bottom - 2 * t)
        val icon = min(inner.height() * 0.9f, inner.width() * 0.3f)
        drawSprite(c, SUN, inner.left + icon / 2 + t, inner.centerY(), icon)
        val text = RectF(inner.left + icon + 3 * t, inner.top, inner.right, inner.bottom)
        val target = WidgetLogic.countdownTarget(style.countdownDay, data.today)
        val days = WidgetLogic.daysUntil(target, data.today)
        val title = style.countdownTitle.ifBlank { "Countdown" }
        val tpP = tp(text.height() * 0.26f, SPLASH, Paint.Align.LEFT).fitTo(title, text.width())
        say(c, title, text.left, tpP.mid(text.top + text.height() * 0.22f), tpP)
        val big = if (days > 1) "$days" else WidgetLogic.countdownText(days)
        val bp = tp(text.height() * 0.5f, WHITE, Paint.Align.LEFT).fitTo(big + if (days > 1) " days" else "", text.width())
        val y = bp.mid(text.top + text.height() * 0.66f)
        say(c, big, text.left, y, bp)
        if (days > 1) {
            val dp = tp(bp.textSize * 0.55f, 0xFFCFCFCF.toInt(), Paint.Align.LEFT)
            say(c, " days", text.left + bp.measureText(big), y, dp)
        }
    }

    /** The month as an inventory: a grid of sunken slots, day numbers like stack counts, today selected. */
    private fun inventory(c: Canvas, style: WidgetStyle, data: HudData, b: RectF, t: Float) {
        val inner = RectF(b.left + 4 * t, b.top + 3 * t, b.right - 4 * t, b.bottom - 4 * t)
        val loc = data.locale
        val grid = WidgetLogic.monthGrid(data.today, style.mondayFirst)
        val heads = WidgetLogic.weekdayInitials(style.mondayFirst, loc)
        val rows = grid.size
        val titleH = min(inner.height() * 0.13f, 13f * t)
        val month = data.today.month.getDisplayName(TextStyle.FULL, loc).replaceFirstChar { it.titlecase(loc) }
        val title = tp(titleH * 0.95f, GUI_TEXT, Paint.Align.LEFT).fitTo(month, inner.width() * 0.7f)
        c.drawText(month, inner.left + t, title.mid(inner.top + titleH / 2), title)
        val yr = tp(title.textSize * 0.8f, 0xFF707070.toInt(), Paint.Align.RIGHT)
        c.drawText("${data.today.year}", inner.right - t, yr.mid(inner.top + titleH / 2), yr)
        val gridTop = inner.top + titleH + t
        val headH = min(titleH * 0.75f, (inner.bottom - gridTop) * 0.12f)
        val slotsTop = gridTop + headH
        val pitchX = inner.width() / 7f
        val pitchY = (inner.bottom - slotsTop) / rows
        // Slots may be a little wider than tall so two-digit counts fit.
        val pitchH = (pitchY / t).toInt().coerceAtLeast(3) * t
        val pitch = (min(pitchX, pitchY * 1.3f) / t).toInt().coerceAtLeast(3) * t
        val gw = pitch * 7
        val gx = (inner.centerX() - gw / 2).let { (it / t).roundToInt() * t }
        val hp = tp(headH * 0.9f, 0xFF555555.toInt())
        heads.forEachIndexed { i, s -> c.drawText(s, gx + pitch * (i + 0.5f), hp.mid(gridTop + headH / 2), hp) }
        val num = tp(pitchH * 0.62f, WHITE, Paint.Align.RIGHT).fitTo("28", pitch - 3.5f * t)
        grid.forEachIndexed { ri, week ->
            week.forEachIndexed { ci, day ->
                val l = gx + ci * pitch; val top = ((slotsTop + ri * pitchH) / t).roundToInt() * t
                val s = RectF(l, top, l + pitch - t, top + pitchH - t)
                val today = day == data.today.dayOfMonth && day != 0
                solid(c, s.left, s.top, s.right, s.bottom, if (today) 0xFFD9F7C8.toInt() else SLOT)
                solid(c, s.left, s.top, s.right - t, s.top + t, SLOT_DARK)
                solid(c, s.left, s.top, s.left + t, s.bottom - t, SLOT_DARK)
                solid(c, s.left + t, s.bottom - t, s.right, s.bottom, WHITE)
                solid(c, s.right - t, s.top + t, s.right, s.bottom, WHITE)
                if (day != 0) {
                    if (today) drawSprite(c, GEM, s.centerX() - pitch * 0.14f, s.centerY() - pitchH * 0.08f, pitchH * 0.66f)
                    say(c, "$day", s.right - t * 1.5f, s.bottom - t * 2f, num.apply { color = if (today) SPLASH else WHITE })
                }
            }
        }
    }

    /** No photo yet: a little pixel painting of a sunny hillside in the plank frame. */
    private fun painting(c: Canvas, b: RectF, t: Float) {
        val canvasR = snap(RectF(b.left + 5 * t, b.top + 5 * t, b.right - 5 * t, b.bottom - 5 * t), t)
        notched(c, RectF(canvasR.left - t, canvasR.top - t, canvasR.right + t, canvasR.bottom + t), t, OUT)
        val cols = 32
        val px = max(t, (canvasR.width() / cols).roundToInt().toFloat())
        val nx = (canvasR.width() / px).toInt() + 1
        val ny = (canvasR.height() / px).toInt() + 1
        c.withClip(canvasR) {
            val sky = intArrayOf(0xFF6F98FF.toInt(), 0xFF7CA3FF.toInt(), 0xFF8AAEFF.toInt(), 0xFF99BAFF.toInt(), 0xFFA8C6FF.toInt())
            for (j in 0 until ny) solid(this, canvasR.left, canvasR.top + j * px, canvasR.right, canvasR.top + (j + 1) * px, sky[(j * sky.size / ny).coerceAtMost(sky.size - 1)])
            val sunX = canvasR.left + px * (nx * 0.72f).toInt(); val sunY = canvasR.top + px * 3
            solid(this, sunX - px, sunY - px, sunX + px * 5, sunY + px * 5, 0xFFFFE15A.toInt())
            solid(this, sunX, sunY, sunX + px * 4, sunY + px * 4, 0xFFFFF7B8.toInt())
            listOf(Triple(0.08f, 0.2f, 7), Triple(0.4f, 0.12f, 5)).forEach { (fx, fy, len) ->
                val x = canvasR.left + px * (nx * fx).toInt(); val y = canvasR.top + px * (ny * fy).toInt()
                solid(this, x, y, x + px * len, y + px * 2, 0xF2FFFFFF.toInt())
                solid(this, x + px * 2, y - px, x + px * (len - 2), y, 0xF2FFFFFF.toInt())
            }
            val r = Random(7)
            var hgt = (ny * 0.38f).toInt()
            val tops = IntArray(nx) { i ->
                if (i % 3 == 0) hgt = (hgt + r.nextInt(3) - 1).coerceIn((ny * 0.25f).toInt(), (ny * 0.5f).toInt())
                ny - hgt
            }
            for (i in 0 until nx) {
                val x = canvasR.left + i * px
                val top = canvasR.top + tops[i] * px
                solid(this, x, top, x + px, top + px * 2, if ((i + tops[i]) % 3 == 0) 0xFF4E9A2E.toInt() else 0xFF62B33F.toInt())
                solid(this, x, top + px * 2, x + px, canvasR.bottom, if ((i * 7 + tops[i]) % 5 == 0) 0xFF6C4A31.toInt() else 0xFF866043.toInt())
            }
            val tx = (nx * 0.22f).toInt()
            val ground = canvasR.top + tops[tx] * px
            solid(this, canvasR.left + tx * px, ground - px * 4, canvasR.left + (tx + 1) * px, ground, 0xFF6B5233.toInt())
            solid(this, canvasR.left + (tx - 2) * px, ground - px * 8, canvasR.left + (tx + 3) * px, ground - px * 4, 0xFF3F8A2B.toInt())
            solid(this, canvasR.left + (tx - 1) * px, ground - px * 9, canvasR.left + (tx + 2) * px, ground - px * 8, 0xFF4FA235.toInt())
        }
        val capH = min(canvasR.height() * 0.2f, 16f * t)
        val cap = RectF(canvasR.left, canvasR.bottom - capH, canvasR.right, canvasR.bottom)
        solid(c, cap.left, cap.top, cap.right, cap.bottom, 0x99000000.toInt())
        val p = tp(capH * 0.6f, WHITE).fitTo("Tap to add a photo", cap.width() * 0.9f)
        say(c, "Tap to add a photo", cap.centerX(), p.mid(cap.centerY()), p)
    }

    private val SAMPLE = listOf(SWORD, PICKAXE, APPLE, TORCH, BUCKET, SAPLING, GEM, BENCH, CRATE)

    /** Nine-slot hotbar; slot one is selected. Sample items show until apps are picked. */
    private fun hotbar(c: Canvas, theme: WidgetTheme, style: WidgetStyle, content: RectF, t: Float, d: Float) {
        val apps = style.apps.take(6)
        val n = theme.hud!!.appSlots(apps.size, content.width() / d, content.height() / d)
        val pitch = content.width() / n
        val size = min(pitch, content.height() + 8 * t)
        val top = content.centerY() - size / 2
        val bar = RectF(content.left, top, content.right, top + size)
        solid(c, bar.left - t, bar.top - t, bar.right + t, bar.bottom + t, 0xFF000000.toInt())
        solid(c, bar.left, bar.top, bar.right, bar.bottom, 0xFF8B8B8B.toInt())
        for (i in 0 until n) {
            val l = bar.left + i * pitch
            val s = RectF(l + t, bar.top + t, l + pitch - t, bar.bottom - t)
            solid(c, s.left, s.top, s.right, s.bottom, 0xD9353535.toInt())
            solid(c, s.left, s.top, s.right, s.top + t, 0xFF1F1F1F.toInt())
            solid(c, s.left, s.top, s.left + t, s.bottom, 0xFF1F1F1F.toInt())
            if (apps.isEmpty()) {
                drawSprite(c, SAMPLE[i % SAMPLE.size], s.centerX(), s.centerY(), min(s.width(), s.height()) * 0.78f)
                if (i == 2 || i == 3) {
                    val np = tp(s.height() * 0.42f, WHITE, Paint.Align.RIGHT)
                    say(c, if (i == 2) "12" else "64", s.right, s.bottom - t, np)
                }
            }
        }
        // Selected slot: a bright frame a little bigger than the slot.
        val sel = RectF(bar.left - t, bar.top - t, bar.left + pitch + t, bar.bottom + t)
        val e = 2 * t
        val p = Paint().apply { color = 0xFFF2F2F2.toInt() }
        c.drawRect(sel.left, sel.top, sel.right, sel.top + e, p)
        c.drawRect(sel.left, sel.bottom - e, sel.right, sel.bottom, p)
        c.drawRect(sel.left, sel.top, sel.left + e, sel.bottom, p)
        c.drawRect(sel.right - e, sel.top, sel.right, sel.bottom, p)
        val o = Paint().apply { color = 0xFF000000.toInt(); this.style = Paint.Style.STROKE; strokeWidth = t }
        c.drawRect(sel.left - t / 2, sel.top - t / 2, sel.right + t / 2, sel.bottom + t / 2, o)
    }

    // ---------- Wallpaper ----------

    /**
     * A world cross-section on a block grid: banded sky, square sun and flat clouds, grass hills with trees,
     * dirt and stone with ore veins, a torch-lit cave with a lava pool, deep slate and a bedrock floor.
     */
    val wall = WallPainter { c, w, h ->
        val cols = 36
        val bs = w / cols
        val t = bs / 16f
        val rows = (h / bs).toInt() + 1
        val r = Random(2024)
        // Sky bands.
        val skyRows = (rows * 0.52f).toInt()
        val sky = intArrayOf(0xFF5E8DF5.toInt(), 0xFF6A98F8.toInt(), 0xFF78A3FA.toInt(), 0xFF86AEFC.toInt(), 0xFF94B9FD.toInt(), 0xFFA3C4FE.toInt(), 0xFFB2CEFF.toInt())
        for (j in 0..skyRows) solid(c, 0f, j * bs, w, (j + 1) * bs, sky[(j * sky.size / (skyRows * 0.82f)).toInt().coerceAtMost(sky.size - 1)])
        // Square sun with a soft halo.
        val sunX = bs * 26; val sunY = bs * 4
        c.drawRect(sunX - bs, sunY - bs, sunX + bs * 5, sunY + bs * 5, Paint().apply { color = 0x33FFF6B0 })
        solid(c, sunX - bs * 0.5f, sunY - bs * 0.5f, sunX + bs * 4.5f, sunY + bs * 4.5f, 0xFFFFE15A.toInt())
        solid(c, sunX, sunY, sunX + bs * 4, sunY + bs * 4, 0xFFFFF8C2.toInt())
        // Flat blocky clouds.
        listOf(Triple(2, 7, 9), Triple(15, 10, 7), Triple(27, 13, 8), Triple(6, 16, 6)).forEach { (cx, cy, len) ->
            val cp = Paint().apply { color = 0xF2FFFFFF.toInt() }
            c.drawRect(cx * bs, cy * bs, (cx + len) * bs, (cy + 1) * bs, cp)
            c.drawRect((cx + 2) * bs, (cy - 1) * bs, (cx + len - 2) * bs, cy * bs, cp)
            c.drawRect(cx * bs, (cy + 1) * bs, (cx + len) * bs, (cy + 1.35f) * bs, Paint().apply { color = 0x4D8FA9D9 })
        }
        // Terrain height per column: gentle stepped hills.
        val surf = IntArray(cols) { i ->
            val v = sin(i * 0.33) * 2.2 + sin(i * 0.11 + 1.3) * 3.0
            (rows * 0.46f).toInt() - v.roundToInt()
        }
        val deep = (rows * 0.8f).toInt()
        // Ground: dirt band then stone to the bottom, deep slate, bedrock.
        for (i in 0 until cols) {
            val x = i * bs
            val top = surf[i] * bs
            fill(c, RectF(x, top, x + bs, top + bs), GRASS_SIDE, t, x, top)
            fill(c, RectF(x, top + bs, x + bs, top + bs * 4), DIRT, t, 0f, 0f)
            fill(c, RectF(x, top + bs * 4, x + bs, deep * bs), STONE, t, 0f, 0f)
        }
        fill(c, RectF(0f, deep * bs, w, (rows - 1) * bs), DEEP, t, 0f, 0f)
        fill(c, RectF(0f, (rows - 1) * bs, w, h + bs), BEDROCK, t, 0f, 0f)
        // Ores in the stone.
        val ores = listOf(COAL to 0.36f, IRON to 0.26f, GOLD to 0.14f, RUBY to 0.1f, DIAMOND to 0.14f)
        repeat(70) {
            val i = r.nextInt(cols)
            val j = surf[i] + 5 + r.nextInt((rows - 2 - surf[i] - 5).coerceAtLeast(1))
            var f = r.nextFloat()
            var tex = COAL
            for ((o, wgt) in ores) { if (f < wgt) { tex = o; break }; f -= wgt }
            // Only gems hide in the deep slate.
            if (j >= deep - 1) { if (r.nextFloat() < 0.7f) return@repeat; tex = DIAMOND }
            val size = 1 + r.nextInt(3)
            for (dx in 0 until size) for (dy in 0 until size) {
                if (dx + dy > 1 && r.nextBoolean()) continue
                val x = (i + dx).coerceAtMost(cols - 1) * bs; val y = (j + dy) * bs
                fill(c, RectF(x, y, x + bs, y + bs), tex, t, x, y)
            }
        }
        // A cave pocket with a lava pool, lit by torches.
        val caveMid = (rows * 0.66f).toInt()
        val cave = HashSet<Pair<Int, Int>>()
        for (i in 2 until 34) {
            // A wide pocket in the middle that narrows to tunnels at both ends.
            val wide = 1.0 - kotlin.math.abs(i - 18) / 16.0
            val hgt = (2 + wide * 5 + sin(i * 0.9) * 0.8).roundToInt().coerceIn(2, 8)
            val off = (sin(i * 0.31) * 1.5).roundToInt()
            val floorRow = caveMid + 3 + off
            for (j in 0 until hgt) cave += i to floorRow - j
        }
        val dark = Paint().apply { color = 0xB0000000.toInt() }
        cave.forEach { (i, j) ->
            fill(c, RectF(i * bs, j * bs, (i + 1) * bs, (j + 1) * bs), STONE, t, 0f, 0f)
            c.drawRect(i * bs, j * bs, (i + 1) * bs, (j + 1) * bs, dark)
        }
        // Lava fills the lowest open cells in the middle of the cave.
        val lavaCols = 14..21
        lavaCols.forEach { i ->
            val floor = cave.filter { it.first == i }.maxOfOrNull { it.second } ?: return@forEach
            val x = i * bs; val y = floor * bs
            fill(c, RectF(x, y + bs * 0.25f, x + bs, y + bs), LAVA, t, 0f, 0f)
            c.drawRect(x, y + bs * 0.25f, x + bs, y + bs * 0.25f + t, Paint().apply { color = 0xFFFFE066.toInt() })
        }
        val lavaY = (cave.filter { it.first == 18 }.maxOfOrNull { it.second } ?: caveMid) * bs
        c.drawCircle(18.5f * bs, lavaY, bs * 6, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(18.5f * bs, lavaY, bs * 6, intArrayOf(0x66FF7A14, 0x00FF7A14), null, Shader.TileMode.CLAMP)
        })
        // Torches on the cave floor.
        listOf(6, 10, 25, 29).forEach { i ->
            val floor = cave.filter { it.first == i }.maxOfOrNull { it.second } ?: return@forEach
            val cx = (i + 0.5f) * bs; val cy = floor * bs + bs * 0.5f
            c.drawCircle(cx, cy - bs * 0.3f, bs * 3.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy - bs * 0.3f, bs * 3.2f, intArrayOf(0x70FFC24A, 0x00FFC24A), null, Shader.TileMode.CLAMP)
            })
            drawSprite(c, TORCH, cx, cy, bs * 1.1f)
        }
        // Trees: log trunks with leaf cubes.
        listOf(4, 13, 30).forEach { i ->
            val ground = surf[i]
            val trunkTop = ground - 4
            for (j in trunkTop until ground) fill(c, RectF(i * bs, j * bs, (i + 1) * bs, (j + 1) * bs), LOG, t, i * bs, j * bs)
            val leaf = RectF((i - 2) * bs, (trunkTop - 2) * bs, (i + 3) * bs, (trunkTop + 1) * bs)
            fill(c, leaf, LEAVES, t, 0f, 0f)
            fill(c, RectF((i - 1) * bs, (trunkTop - 3) * bs, (i + 2) * bs, (trunkTop - 2) * bs), LEAVES, t, 0f, 0f)
            c.drawRect(leaf.left, leaf.bottom - t * 2, leaf.right, leaf.bottom, Paint().apply { color = 0x40000000 })
        }
        // Flowers and tufts on the grass.
        listOf(1, 8, 10, 19, 23, 26, 34).forEachIndexed { n, i ->
            val x = i * bs + bs * 0.4f; val y = surf[i] * bs
            val stem = Paint().apply { color = 0xFF3F7A26.toInt() }
            c.drawRect(x + t * 2, y - t * 6, x + t * 4, y, stem)
            val petal = Paint().apply { color = if (n % 2 == 0) 0xFFE8312F.toInt() else 0xFFFFE04A.toInt() }
            c.drawRect(x, y - t * 9, x + t * 6, y - t * 6, petal)
            c.drawRect(x + t * 2, y - t * 9, x + t * 4, y - t * 7, Paint().apply { color = 0xFFFFF4B0.toInt() })
        }
    }
}
