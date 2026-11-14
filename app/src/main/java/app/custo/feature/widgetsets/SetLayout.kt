package app.custo.feature.widgetsets

import java.time.LocalDate
import java.time.MonthDay

/** Pure, Android-free layout math for set previews and sticker placement (unit-tested). */
object SetLayout {

    /** A widget's place on the home grid, in cells. */
    data class Cell(val col: Int, val row: Int, val cols: Int, val rows: Int)

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /**
     * Places widgets like a launcher does: each goes in the first free spot, reading left to right,
     * top to bottom. Spans wider than the grid are clamped.
     */
    fun pack(spans: List<Pair<Int, Int>>, columns: Int = 4): List<Cell> {
        val taken = mutableListOf<BooleanArray>()
        fun free(c: Int, r: Int): Boolean = r >= taken.size || !taken[r][c]
        fun fits(c: Int, r: Int, w: Int, h: Int): Boolean {
            if (c + w > columns) return false
            for (y in r until r + h) for (x in c until c + w) if (!free(x, y)) return false
            return true
        }
        return spans.map { (sw, sh) ->
            val w = sw.coerceIn(1, columns)
            val h = sh.coerceAtLeast(1)
            var row = 0
            var placed: Cell? = null
            while (placed == null) {
                for (col in 0..columns - w) {
                    if (fits(col, row, w, h)) { placed = Cell(col, row, w, h); break }
                }
                if (placed == null) row++
            }
            while (taken.size < placed.row + placed.rows) taken.add(BooleanArray(columns))
            for (y in placed.row until placed.row + placed.rows) for (x in placed.col until placed.col + placed.cols) taken[y][x] = true
            placed
        }
    }

    fun rowsUsed(cells: List<Cell>): Int = cells.maxOfOrNull { it.row + it.rows } ?: 0

    /**
     * Cell size for [rows] × [columns] cells in an area: square-ish cells, shrunk to fit tall sets.
     * Returns width to height.
     */
    fun cellSize(areaW: Float, areaH: Float, columns: Int, rows: Int): Pair<Float, Float> {
        val w = areaW / columns
        val h = if (rows <= 0) w else minOf(w * 1.05f, areaH / rows)
        return w to h
    }

    /** Pixel boxes for [cells] inside an area starting at [left], [top]. */
    fun boxes(cells: List<Cell>, left: Float, top: Float, cellW: Float, cellH: Float): List<Box> = cells.map {
        Box(left + it.col * cellW, top + it.row * cellH, left + (it.col + it.cols) * cellW, top + (it.row + it.rows) * cellH)
    }

    /**
     * Center of a sticker of [size] anchored at ([ax], [ay]) (0..1) on a card, pulled inward
     * so the whole sticker stays inside the bitmap of [bmpW] × [bmpH].
     */
    fun stickerCenter(card: Box, ax: Float, ay: Float, size: Float, bmpW: Float, bmpH: Float): Pair<Float, Float> {
        val half = size / 2f
        // Corner stickers overlap the card edge by about a third of their size.
        val cx = card.left + card.width * ax + (0.5f - ax) * size * 0.35f
        val cy = card.top + card.height * ay + (0.5f - ay) * size * 0.35f
        return cx.coerceIn(half, (bmpW - half).coerceAtLeast(half)) to cy.coerceIn(half, (bmpH - half).coerceAtLeast(half))
    }

    /** Sticker side for a card: a third of its short side, capped so big widgets don't get huge art. */
    fun stickerSize(cardW: Float, cardH: Float, scale: Float, maxPx: Float): Float =
        (minOf(cardW, cardH) * 0.34f).coerceAtMost(maxPx) * scale

    /** The next [month]/[day] on or after [today] (Feb 29 falls back to Feb 28 in other years). */
    fun nextOccurrence(month: Int, day: Int, today: LocalDate): LocalDate {
        val md = MonthDay.of(month, day)
        val thisYear = md.atYear(today.year)
        return if (thisYear.isBefore(today)) md.atYear(today.year + 1) else thisYear
    }
}
