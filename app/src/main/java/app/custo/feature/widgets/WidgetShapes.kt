package app.custo.feature.widgets

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Outlines for [WidgetShape]s, shared by the home-screen bitmap and the in-app preview. */
object WidgetShapes {

    /**
     * Extra room (dp per side: horizontal, vertical) the content needs inside [shape] at [w] × [h] dp,
     * on top of the widget's padding, so text stays inside circles, pills and notches.
     */
    fun contentInset(shape: WidgetShape, w: Float, h: Float): Pair<Float, Float> {
        val side = min(w, h)
        return when (shape) {
            WidgetShape.ROUNDED, WidgetShape.SQUIRCLE -> 0f to 0f
            // The inscribed square of the circle, centered.
            WidgetShape.CIRCLE -> ((w - side * 0.7f) / 2f).coerceAtLeast(0f) to ((h - side * 0.7f) / 2f).coerceAtLeast(0f)
            WidgetShape.PILL -> if (w >= h) h * 0.18f to 0f else 0f to w * 0.18f
            WidgetShape.TICKET -> side * 0.12f to 0f
            WidgetShape.BLOB -> w * 0.07f to h * 0.07f
        }
    }

    /** The outline in [r] (pixels). [corner] is the rounded-corner radius in pixels. */
    fun path(shape: WidgetShape, r: RectF, corner: Float): Path {
        val w = r.width(); val h = r.height()
        val side = min(w, h)
        return when (shape) {
            WidgetShape.ROUNDED -> Path().apply { addRoundRect(r, corner, corner, Path.Direction.CW) }
            WidgetShape.PILL -> Path().apply { addRoundRect(r, side / 2f, side / 2f, Path.Direction.CW) }
            WidgetShape.CIRCLE -> Path().apply { addCircle(r.centerX(), r.centerY(), side / 2f, Path.Direction.CW) }
            WidgetShape.SQUIRCLE -> squircle(r, side * 0.42f)
            WidgetShape.TICKET -> ticket(r, corner.coerceAtMost(side * 0.25f), side * 0.11f)
            WidgetShape.BLOB -> blob(r)
        }
    }

    /** A rectangle with superellipse corners (continuous curvature, like app icons). */
    private fun squircle(r: RectF, rad: Float): Path {
        val p = Path()
        val n = 4.0
        val steps = 12
        // Corner centers, clockwise from top-right; each corner sweeps a quarter turn.
        val corners = listOf(
            Triple(r.right - rad, r.top + rad, -PI / 2), Triple(r.right - rad, r.bottom - rad, 0.0),
            Triple(r.left + rad, r.bottom - rad, PI / 2), Triple(r.left + rad, r.top + rad, PI),
        )
        var first = true
        corners.forEach { (cx, cy, start) ->
            for (i in 0..steps) {
                val t = start + (PI / 2) * i / steps
                val c = cos(t); val s = sin(t)
                val x = cx + rad * (abs(c).pow(2.0 / n) * sign(c)).toFloat()
                val y = cy + rad * (abs(s).pow(2.0 / n) * sign(s)).toFloat()
                if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y)
            }
        }
        p.close()
        return p
    }

    /** A rounded ticket with a half-circle notch cut into each side. */
    private fun ticket(r: RectF, corner: Float, notch: Float): Path {
        val body = Path().apply { addRoundRect(r, corner, corner, Path.Direction.CW) }
        val cuts = Path().apply {
            addCircle(r.left, r.centerY(), notch, Path.Direction.CW)
            addCircle(r.right, r.centerY(), notch, Path.Direction.CW)
        }
        body.op(cuts, Path.Op.DIFFERENCE)
        return body
    }

    /** A soft, slightly uneven blob that fills [r] (deterministic). */
    private fun blob(r: RectF): Path {
        val bumps = floatArrayOf(0.97f, 0.9f, 1f, 0.93f, 0.98f, 0.88f, 0.99f, 0.92f)
        val n = bumps.size
        val cx = r.centerX(); val cy = r.centerY()
        val pts = Array(n) { i ->
            val a = 2 * PI * i / n - PI / 2
            floatArrayOf(cx + (r.width() / 2f) * bumps[i] * cos(a).toFloat(), cy + (r.height() / 2f) * bumps[i] * sin(a).toFloat())
        }
        // Closed Catmull-Rom spline through the points, as cubic Béziers.
        val p = Path()
        p.moveTo(pts[0][0], pts[0][1])
        for (i in 0 until n) {
            val p0 = pts[(i - 1 + n) % n]; val p1 = pts[i]; val p2 = pts[(i + 1) % n]; val p3 = pts[(i + 2) % n]
            p.cubicTo(
                p1[0] + (p2[0] - p0[0]) / 6f, p1[1] + (p2[1] - p0[1]) / 6f,
                p2[0] - (p3[0] - p1[0]) / 6f, p2[1] - (p3[1] - p1[1]) / 6f,
                p2[0], p2[1],
            )
        }
        p.close()
        return p
    }
}
