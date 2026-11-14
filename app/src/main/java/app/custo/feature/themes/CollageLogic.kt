package app.custo.feature.themes

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A cell as fractions (0..1) of the collage area. */
data class Frac(val l: Float, val t: Float, val r: Float, val b: Float)

/** A cell in pixels. */
data class CellBox(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t
}

data class CollageTemplate(val id: String, val label: String, val cells: List<Frac>) {
    val count get() = cells.size
}

enum class CollageBg(val label: String) { COLOR("Color"), BLUR("Blurred photo") }

/** Pure collage geometry. */
object CollageLogic {
    const val MIN_PHOTOS = 2
    const val MAX_PHOTOS = 6
    private const val T = 1f / 3f
    private const val TT = 2f / 3f

    private fun cols(n: Int) = List(n) { Frac(it / n.toFloat(), 0f, (it + 1) / n.toFloat(), 1f) }
    private fun rows(n: Int) = List(n) { Frac(0f, it / n.toFloat(), 1f, (it + 1) / n.toFloat()) }
    private fun grid(c: Int, r: Int) = List(c * r) { i ->
        val x = i % c; val y = i / c
        Frac(x / c.toFloat(), y / r.toFloat(), (x + 1) / c.toFloat(), (y + 1) / r.toFloat())
    }

    val templates: List<CollageTemplate> = listOf(
        CollageTemplate("2-cols", "Side by side", cols(2)),
        CollageTemplate("2-rows", "Stacked", rows(2)),
        CollageTemplate("3-rows", "Three rows", rows(3)),
        CollageTemplate("3-top", "Big top", listOf(Frac(0f, 0f, 1f, 0.55f), Frac(0f, 0.55f, 0.5f, 1f), Frac(0.5f, 0.55f, 1f, 1f))),
        CollageTemplate("3-left", "Big left", listOf(Frac(0f, 0f, 0.6f, 1f), Frac(0.6f, 0f, 1f, 0.5f), Frac(0.6f, 0.5f, 1f, 1f))),
        CollageTemplate("4-grid", "Grid", grid(2, 2)),
        CollageTemplate("4-top", "Big top", listOf(Frac(0f, 0f, 1f, 0.6f), Frac(0f, 0.6f, T, 1f), Frac(T, 0.6f, TT, 1f), Frac(TT, 0.6f, 1f, 1f))),
        CollageTemplate("4-rows", "Four rows", rows(4)),
        CollageTemplate("5-mosaic", "Mosaic", listOf(
            Frac(0f, 0f, 0.5f, 0.5f), Frac(0.5f, 0f, 1f, 0.5f),
            Frac(0f, 0.5f, T, 1f), Frac(T, 0.5f, TT, 1f), Frac(TT, 0.5f, 1f, 1f),
        )),
        CollageTemplate("5-center", "Center stage", listOf(
            Frac(0f, 0f, 0.5f, 0.25f), Frac(0.5f, 0f, 1f, 0.25f), Frac(0f, 0.25f, 1f, 0.75f),
            Frac(0f, 0.75f, 0.5f, 1f), Frac(0.5f, 0.75f, 1f, 1f),
        )),
        CollageTemplate("6-grid", "Grid", grid(2, 3)),
        CollageTemplate("6-tiles", "Tiles", grid(3, 2)),
    )

    fun forCount(n: Int): List<CollageTemplate> = templates.filter { it.count == n.coerceIn(MIN_PHOTOS, MAX_PHOTOS) }

    fun byId(id: String?): CollageTemplate? = templates.firstOrNull { it.id == id }

    private fun near(a: Float, b: Float) = abs(a - b) < 1e-4f

    /**
     * Cells in pixels on a [w] x [h] canvas. [margin] is kept around the outside and [spacing]
     * between neighbors, so every gap is the same width whatever the template.
     */
    fun cells(t: CollageTemplate, w: Float, h: Float, spacing: Float, margin: Float): List<CellBox> {
        val m = margin.coerceIn(0f, min(w, h) / 3f)
        val iw = w - 2 * m; val ih = h - 2 * m
        val half = max(0f, spacing) / 2f
        fun x(f: Float, inner: Boolean) = m + iw * f + if (near(f, 0f) || near(f, 1f)) 0f else if (inner) half else -half
        fun y(f: Float, inner: Boolean) = m + ih * f + if (near(f, 0f) || near(f, 1f)) 0f else if (inner) half else -half
        return t.cells.map { c ->
            val l = x(c.l, true); val r = x(c.r, false)
            val tp = y(c.t, true); val b = y(c.b, false)
            CellBox(l, tp, max(l, r), max(tp, b))
        }
    }

    /** Source rect (l, t, r, b) that center-crops an [sw] x [sh] image to the aspect of [dw] x [dh]. */
    fun centerCrop(sw: Int, sh: Int, dw: Float, dh: Float): CellBox {
        if (sw <= 0 || sh <= 0 || dw <= 0f || dh <= 0f) return CellBox(0f, 0f, sw.toFloat(), sh.toFloat())
        val src = sw / sh.toFloat(); val dst = dw / dh
        return if (src > dst) {
            val cw = sh * dst; val l = (sw - cw) / 2f
            CellBox(l, 0f, l + cw, sh.toFloat())
        } else {
            val ch = sw / dst; val t = (sh - ch) / 2f
            CellBox(0f, t, sw.toFloat(), t + ch)
        }
    }

    /** Destination (x, y, w, h) for a video of [vw] x [vh] on a [sw] x [sh] surface: crop fills, fit letterboxes. */
    fun videoRect(vw: Int, vh: Int, sw: Int, sh: Int, crop: Boolean): IntArray {
        if (vw <= 0 || vh <= 0 || sw <= 0 || sh <= 0) return intArrayOf(0, 0, sw, sh)
        val scale = if (crop) max(sw / vw.toFloat(), sh / vh.toFloat()) else min(sw / vw.toFloat(), sh / vh.toFloat())
        val w = Math.round(vw * scale); val h = Math.round(vh * scale)
        return intArrayOf((sw - w) / 2, (sh - h) / 2, w, h)
    }
}
