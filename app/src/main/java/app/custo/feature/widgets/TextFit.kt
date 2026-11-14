package app.custo.feature.widgets

import androidx.compose.ui.unit.DpSize
import kotlin.math.min

/**
 * Works out how much to shrink widget text so it fits the space it really has.
 * Glyph metrics are approximated (digit width ≈ 0.62 em, line height ≈ 1.22 em), which is close
 * enough for the fonts Custo ships and errs on the side of a little extra room.
 */
object TextFit {
    /** A line of text: its base size in dp/sp and roughly how many characters wide it is. */
    class Line(val size: Float, val chars: Int, val shown: Boolean = true)

    private const val CHAR_W = 0.62f
    private const val LINE_H = 1.12f
    private const val MIN = 0.3f

    /** Lines stacked on top of each other. */
    fun stack(area: DpSize, textScale: Float, vararg lines: Line): Float {
        val ls = lines.filter { it.shown }
        if (ls.isEmpty()) return 1f
        val needH = ls.sumOf { (it.size * LINE_H).toDouble() }.toFloat() * textScale
        val needW = ls.maxOf { it.size * CHAR_W * it.chars } * textScale
        return clamp(area, needW, needH)
    }

    /** Lines side by side in one row. */
    fun row(area: DpSize, textScale: Float, vararg lines: Line): Float {
        val ls = lines.filter { it.shown }
        if (ls.isEmpty()) return 1f
        val needW = (ls.sumOf { (it.size * CHAR_W * it.chars).toDouble() }.toFloat() + 8f * (ls.size - 1)) * textScale
        val needH = ls.maxOf { it.size * LINE_H } * textScale
        return clamp(area, needW, needH)
    }

    private fun clamp(area: DpSize, needW: Float, needH: Float): Float {
        if (needW <= 0f || needH <= 0f) return 1f
        return min(1f, min(area.width.value / needW, area.height.value / needH)).coerceAtLeast(MIN)
    }
}
