package app.custo.feature.overlays

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One Control Center tile. [span] is its width in grid columns, [rows] its height. */
enum class CcTile(val label: String, val span: Int = 1, val rows: Int = 1) {
    WIFI("Internet"),
    BLUETOOTH("Bluetooth"),
    FLASHLIGHT("Flashlight"),
    DND("Do not disturb"),
    ROTATION("Auto-rotate"),
    MEDIA("Now playing", span = 4, rows = 2),
    BRIGHTNESS("Brightness", span = 4),
    VOLUME("Media volume", span = 4),
    SCREENSHOT("Screenshot"),
    RECORD("Screen record"),
    CALCULATOR("Calculator"),
    CAMERA("Camera"),
    ALARM("Alarms"),
    APPS("Your apps", span = 4),
}

enum class CcSkin(val label: String) { LIGHT("Frosted light"), DARK("Frosted dark"), ACCENT("Accent") }

/** Which part of the top edge opens the panel. */
enum class CcZone(val label: String) { LEFT("Left"), CENTER("Middle"), RIGHT("Right") }

/** A tile placed on the grid. */
data class CcCell(val tile: CcTile, val col: Int, val row: Int, val cols: Int, val rows: Int)

/** Pure Control Center helpers: tile list codec, grid layout, top-edge zone, slider curves. */
object CcLogic {
    const val COLS = 4
    const val MAX_APPS = 4

    val DEFAULT = listOf(
        CcTile.WIFI, CcTile.BLUETOOTH, CcTile.FLASHLIGHT, CcTile.DND,
        CcTile.MEDIA, CcTile.BRIGHTNESS, CcTile.VOLUME,
        CcTile.ROTATION, CcTile.SCREENSHOT, CcTile.CAMERA, CcTile.CALCULATOR,
    )

    /** Null means never set: use [DEFAULT]. Unknown names are skipped, duplicates dropped. */
    fun decode(s: String?): List<CcTile> =
        if (s == null) DEFAULT
        else s.split(',').mapNotNull { n -> CcTile.entries.firstOrNull { it.name == n.trim() } }.distinct()

    fun encode(tiles: List<CcTile>): String = tiles.distinct().joinToString(",") { it.name }

    /** Moves the tile at [index] by [delta] places, clamped to the list. */
    fun move(tiles: List<CcTile>, index: Int, delta: Int): List<CcTile> {
        if (index !in tiles.indices) return tiles
        val to = (index + delta).coerceIn(0, tiles.lastIndex)
        if (to == index) return tiles
        return tiles.toMutableList().apply { add(to, removeAt(index)) }
    }

    /**
     * Places tiles in order, left to right, top to bottom. A tile goes in the first free spot at or
     * after the previous tile's spot, so order is kept, but small tiles can fill gaps left by tall ones.
     */
    fun layout(tiles: List<CcTile>, cols: Int = COLS): List<CcCell> {
        val taken = HashSet<Long>()
        fun key(c: Int, r: Int) = r.toLong() * 1000 + c
        fun fits(c: Int, r: Int, w: Int, h: Int): Boolean {
            if (c + w > cols) return false
            for (dr in 0 until h) for (dc in 0 until w) if (key(c + dc, r + dr) in taken) return false
            return true
        }
        val out = ArrayList<CcCell>(tiles.size)
        var cursor = 0 // linear index (row * cols + col) where the search starts
        for (t in tiles) {
            val w = t.span.coerceIn(1, cols); val h = t.rows.coerceAtLeast(1)
            var i = cursor
            while (!fits(i % cols, i / cols, w, h)) i++
            val c = i % cols; val r = i / cols
            for (dr in 0 until h) for (dc in 0 until w) taken += key(c + dc, r + dr)
            out += CcCell(t, c, r, w, h)
            cursor = i + w
        }
        return out
    }

    fun rowCount(cells: List<CcCell>): Int = cells.maxOfOrNull { it.row + it.rows } ?: 0

    /** Index of the cell at grid position ([col], [row]), or -1. */
    fun cellAt(cells: List<CcCell>, col: Int, row: Int): Int =
        cells.indexOfFirst { col >= it.col && col < it.col + it.cols && row >= it.row && row < it.row + it.rows }

    // ---- top-edge zone ----

    /** Left and right pixel edges of the trigger zone on a screen [screenW] wide. */
    fun zoneSpan(screenW: Int, zone: CcZone, widthFraction: Float): Pair<Int, Int> {
        val w = (screenW * widthFraction.coerceIn(0.1f, 1f)).roundToInt()
        val left = when (zone) {
            CcZone.LEFT -> 0
            CcZone.CENTER -> (screenW - w) / 2
            CcZone.RIGHT -> screenW - w
        }
        return left to left + w
    }

    /** True if a touch at x [x] on the top edge is inside the zone. */
    fun inZone(x: Float, screenW: Int, zone: CcZone, widthFraction: Float): Boolean {
        val (l, r) = zoneSpan(screenW, zone, widthFraction)
        return x >= l && x <= r
    }

    /** A mostly-downward drag of at least [thresholdPx]. */
    fun isPullDown(dx: Float, dy: Float, thresholdPx: Float): Boolean = dy >= thresholdPx && dy > abs(dx) * 1.2f

    /** A mostly-upward drag of at least [thresholdPx]. */
    fun isPushUp(dx: Float, dy: Float, thresholdPx: Float): Boolean = -dy >= thresholdPx && -dy > abs(dx) * 1.2f

    // ---- sliders ----

    /** Slider position to a brightness value. Squared, so the low end gets more room like the system slider. */
    fun sliderToBrightness(f: Float, max: Int = 255): Int = (max * f.coerceIn(0f, 1f).let { it * it }).roundToInt().coerceIn(1, max)

    fun brightnessToSlider(v: Int, max: Int = 255): Float = sqrt((v.coerceIn(0, max)) / max.toFloat())

    fun sliderToSteps(f: Float, maxSteps: Int): Int = (f.coerceIn(0f, 1f) * maxSteps).roundToInt()
}
