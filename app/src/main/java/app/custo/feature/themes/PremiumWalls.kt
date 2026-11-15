package app.custo.feature.themes

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import app.custo.feature.themes.WallLogic.lerpColor
import app.custo.feature.themes.WallLogic.withAlpha
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The premium dark wallpapers: carbon bands, hex mesh, brushed metal, carbon-fiber weave, dark marble
 * and obsidian facets. Their palette is fixed near-black; [WallSpec.edge] sets the metal accent.
 * Deterministic for a spec, and drawn with a few hundred shapes at most, so a full-screen render is quick.
 */
internal object PremiumWalls {
    private const val INK = 0xFF0A0A0C.toInt()
    private const val COAL = 0xFF141417.toInt()
    private const val SLATE = 0xFF1E1F23.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    fun draw(c: Canvas, spec: WallSpec, w: Float, h: Float) {
        val rnd = Random(spec.seed)
        val edge = spec.edge.color
        val k = 0.5f + spec.complexity.coerceIn(0f, 1f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        when (spec.style) {
            WallStyle.CARBON -> carbon(c, w, h, rnd, edge, k, spec.angle, p)
            WallStyle.HEX -> hexMesh(c, w, h, rnd, edge, k, p)
            WallStyle.BRUSHED -> brushed(c, w, h, rnd, edge, k, p)
            WallStyle.WEAVE -> weave(c, w, h, edge, k, p)
            WallStyle.MARBLE -> marble(c, w, h, rnd, edge, k, p)
            WallStyle.OBSIDIAN -> obsidian(c, w, h, rnd, edge, k, p)
            else -> c.drawColor(INK)
        }
    }

    private fun vertical(c: Canvas, w: Float, h: Float, top: Int, bottom: Int, p: Paint) {
        p.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }

    private fun vignette(c: Canvas, w: Float, h: Float, strength: Float, p: Paint) {
        val r = hypot(w, h) * 0.62f
        p.shader = RadialGradient(w / 2, h * 0.45f, r, intArrayOf(0x00000000, withAlpha(BLACK, strength)), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }

    /** A hexagon outline (pointy top) centered at [x], [y] with circumradius [r], added to [path]. */
    private fun hex(path: Path, x: Float, y: Float, r: Float) {
        for (i in 0..5) {
            val a = (PI / 3 * i - PI / 2).toFloat()
            val px = x + r * cos(a); val py = y + r * sin(a)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    /** A honeycomb of hexagon outlines covering [l]..[r] × [t]..[b]. */
    private fun honeycomb(l: Float, t: Float, r: Float, b: Float, size: Float): Path {
        val path = Path()
        val dx = sqrt(3f) * size
        val dy = 1.5f * size
        var row = 0
        var y = t - size
        while (y < b + size) {
            var x = l - dx + if (row % 2 == 1) dx / 2 else 0f
            while (x < r + dx) { hex(path, x, y, size * 0.94f); x += dx }
            y += dy; row++
        }
        return path
    }

    // ---------- Carbon bands ----------

    /**
     * Near-black crossing diagonal bands, some filled with a fine hex mesh, each with soft shadows
     * and thin metal highlights along its edges.
     */
    private fun carbon(c: Canvas, w: Float, h: Float, rnd: Random, edge: Int, k: Float, angle: Float, p: Paint) {
        vertical(c, w, h, 0xFF101013.toInt(), 0xFF060607.toInt(), p)
        val diag = hypot(w, h)
        val unit = min(w, h)
        val n = (4 * k).roundToInt().coerceIn(3, 7)
        // Two families of parallel bands that cross each other.
        val a1 = 28f + (angle % 30f)
        val a2 = -a1 - 18f
        val mesh = honeycombTile(unit * 0.018f)
        repeat(n) { i ->
            val ang = if (i % 2 == 0) a1 + rnd.nextFloat() * 6f else a2 - rnd.nextFloat() * 6f
            val bw = unit * (0.14f + 0.16f * rnd.nextFloat())
            val off = (rnd.nextFloat() - 0.5f) * diag * 0.7f
            val withMesh = i % 3 != 1
            c.withRotation(ang, w / 2, h / 2) {
                val l = w / 2 - diag; val r = w / 2 + diag
                val top = h / 2 + off - bw / 2; val bot = top + bw
                // Soft shadows on both sides (drawn as fading strips; cheaper than blurred layers).
                val sh = bw * 0.45f
                p.shader = LinearGradient(0f, top - sh, 0f, top, 0x00000000, 0xA6000000.toInt(), Shader.TileMode.CLAMP)
                drawRect(l, top - sh, r, top, p)
                p.shader = LinearGradient(0f, bot, 0f, bot + sh * 1.4f, 0xCC000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
                drawRect(l, bot, r, bot + sh * 1.4f, p)
                // The band: a dark satin gradient across its width.
                p.shader = LinearGradient(0f, top, 0f, bot, intArrayOf(0xFF26272C.toInt(), 0xFF16171A.toInt(), 0xFF0D0D10.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                drawRect(l, top, r, bot, p)
                p.shader = null
                if (withMesh) withClip(l, top, r, bot) {
                    p.shader = mesh
                    p.alpha = 210
                    drawRect(l, top, r, bot, p)
                    p.shader = null; p.alpha = 255
                    // Light falls off toward the lower edge.
                    p.shader = LinearGradient(0f, top, 0f, bot, 0x00000000, 0x80000000.toInt(), Shader.TileMode.CLAMP)
                    drawRect(l, top, r, bot, p)
                    p.shader = null
                }
                // Hairline highlights that glint in the middle and fade out at the ends.
                val glint = rnd.nextFloat() * 0.6f + 0.2f
                val gx = l + (r - l) * glint
                fun hair(y: Float, strength: Float, width: Float) {
                    p.shader = LinearGradient(gx - diag * 0.7f, 0f, gx + diag * 0.7f, 0f,
                        intArrayOf(withAlpha(edge, 0f), withAlpha(edge, strength), withAlpha(lerpColor(edge, WHITE, 0.4f), strength), withAlpha(edge, strength * 0.6f), withAlpha(edge, 0f)),
                        floatArrayOf(0f, 0.35f, 0.5f, 0.65f, 1f), Shader.TileMode.CLAMP)
                    p.style = Paint.Style.STROKE; p.strokeWidth = width
                    drawLine(l, y, r, y, p)
                    p.style = Paint.Style.FILL; p.shader = null
                }
                hair(top + 0.5f, 0.95f, max(1.2f, unit * 0.0022f))
                hair(bot - 0.5f, 0.35f, max(1f, unit * 0.0014f))
            }
        }
        vignette(c, w, h, 0.55f, p)
    }

    /** A repeating fine honeycomb (dark cells, slightly lighter walls) as a shader. */
    private fun honeycombTile(size: Float): BitmapShader {
        val s = size.coerceAtLeast(3f)
        val tw = (sqrt(3f) * s).roundToInt().coerceAtLeast(4)
        val th = (3f * s).roundToInt().coerceAtLeast(4)
        val bmp = createBitmap(tw, th)
        val c = Canvas(bmp)
        c.drawColor(0x00000000)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1f, s * 0.22f); color = 0x66000000 }
        val path = Path()
        val dx = tw.toFloat(); val dy = th / 2f
        for (row in -1..2) for (col in -1..1) {
            val x = col * dx + if (row % 2 != 0) dx / 2 else 0f
            hex(path, x, row * dy, s)
        }
        c.drawPath(path, p)
        p.color = 0x1FFFFFFF; p.strokeWidth = max(0.6f, s * 0.08f)
        c.save(); c.translate(0f, -max(0.6f, s * 0.12f)); c.drawPath(path, p); c.restore()
        return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    // ---------- Hex mesh ----------

    /** A full honeycomb with light falling across it and a few cells rimmed in the accent. */
    private fun hexMesh(c: Canvas, w: Float, h: Float, rnd: Random, edge: Int, k: Float, p: Paint) {
        vertical(c, w, h, 0xFF0F1013.toInt(), 0xFF050506.toInt(), p)
        val size = w / (9f * k + 4f)
        val dx = sqrt(3f) * size
        val dy = 1.5f * size
        val lx = w * (0.25f + 0.5f * rnd.nextFloat()); val ly = h * (0.2f + 0.3f * rnd.nextFloat())
        val far = hypot(w, h)
        val cell = Path()
        val wall = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1.5f, size * 0.07f); color = 0xFF050506.toInt() }
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1f, size * 0.035f) }
        var row = 0
        var y = -size
        while (y < h + size) {
            var x = -dx + if (row % 2 == 1) dx / 2 else 0f
            while (x < w + dx) {
                val light = (1f - hypot(x - lx, y - ly) / far).coerceIn(0f, 1f)
                val v = light * light * 0.9f + rnd.nextFloat() * 0.08f
                cell.reset(); hex(cell, x, y, size * 0.9f)
                p.shader = LinearGradient(x, y - size, x, y + size, lerpColor(COAL, 0xFF34353B.toInt(), v), lerpColor(INK, COAL, v), Shader.TileMode.CLAMP)
                c.drawPath(cell, p)
                p.shader = null
                c.drawPath(cell, wall)
                if (rnd.nextFloat() < 0.06f * k) {
                    rim.color = withAlpha(edge, 0.35f + 0.5f * light)
                    c.drawPath(cell, rim)
                }
                x += dx
            }
            y += dy; row++
        }
        vignette(c, w, h, 0.6f, p)
    }

    // ---------- Brushed metal ----------

    /** Dark anodized metal: fine horizontal grain, a broad sheen and one thin accent pinstripe. */
    private fun brushed(c: Canvas, w: Float, h: Float, rnd: Random, edge: Int, k: Float, p: Paint) {
        vertical(c, w, h, 0xFF2A2D32.toInt(), 0xFF111215.toInt(), p)
        p.style = Paint.Style.STROKE
        val lines = (900 * k).roundToInt()
        repeat(lines) {
            val y = rnd.nextFloat() * h
            val x0 = rnd.nextFloat() * w * 1.2f - w * 0.2f
            val len = w * (0.15f + 0.7f * rnd.nextFloat())
            p.strokeWidth = 0.6f + rnd.nextFloat() * 1.4f
            p.color = if (rnd.nextBoolean()) withAlpha(WHITE, 0.025f + 0.05f * rnd.nextFloat()) else withAlpha(BLACK, 0.06f + 0.1f * rnd.nextFloat())
            c.drawLine(x0, y, x0 + len, y, p)
        }
        p.style = Paint.Style.FILL
        // Broad diagonal sheen.
        val t = rnd.nextFloat()
        p.shader = LinearGradient(0f, h * (0.15f + 0.3f * t), w, h * (0.45f + 0.3f * t),
            intArrayOf(0x00FFFFFF, 0x1FFFFFFF, 0x00FFFFFF), floatArrayOf(0.3f, 0.5f, 0.7f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        // A machined groove with an accent pinstripe.
        val gy = h * (0.62f + 0.2f * rnd.nextFloat())
        p.color = 0x99000000.toInt(); c.drawRect(0f, gy - 3f, w, gy + 2f, p)
        p.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(withAlpha(edge, 0.2f), withAlpha(edge, 0.95f), withAlpha(edge, 0.2f)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, gy - 0.8f, w, gy + 0.8f, p)
        p.shader = null
        p.color = 0x14FFFFFF; c.drawRect(0f, gy + 2f, w, gy + 3f, p)
        vignette(c, w, h, 0.5f, p)
    }

    // ---------- Carbon fiber ----------

    /** Twill-woven carbon fiber (a repeating 2×2 twill tile) under a glossy clear coat. */
    private fun weave(c: Canvas, w: Float, h: Float, edge: Int, k: Float, p: Paint) {
        val cell = (min(w, h) / (26f * k)).coerceAtLeast(4f)
        val n = 4
        val ts = (cell * n).roundToInt().coerceAtLeast(8)
        val cs = ts / n.toFloat()
        val tile = createBitmap(ts, ts)
        val tc = Canvas(tile)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        for (row in 0 until n) for (col in 0 until n) {
            val x = col * cs; val y = row * cs
            // 2×2 twill: the over/under direction shifts by one each row.
            val horizontal = ((col + row) / 2) % 2 == 0
            tp.shader = if (horizontal) LinearGradient(0f, y, 0f, y + cs, intArrayOf(0xFF1A1B1F.toInt(), 0xFF3A3C42.toInt(), 0xFF101114.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            else LinearGradient(x, 0f, x + cs, 0f, intArrayOf(0xFF0C0D0F.toInt(), 0xFF24252A.toInt(), 0xFF0A0A0C.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            tc.drawRect(x, y, x + cs, y + cs, tp)
        }
        tp.shader = null; tp.style = Paint.Style.STROKE; tp.strokeWidth = max(1f, cs * 0.06f); tp.color = 0x8C000000.toInt()
        for (i in 0..n) { tc.drawLine(i * cs, 0f, i * cs, ts.toFloat(), tp); tc.drawLine(0f, i * cs, ts.toFloat(), i * cs, tp) }
        p.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setRotate(45f) })
        }
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        // Clear-coat reflections and a faint accent glow at the bottom edge.
        p.shader = RadialGradient(w * 0.3f, h * 0.22f, max(w, h) * 0.55f, intArrayOf(0x2EFFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = LinearGradient(0f, h * 0.75f, 0f, h, withAlpha(edge, 0f), withAlpha(edge, 0.16f), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.75f, w, h, p)
        p.shader = null
        vignette(c, w, h, 0.6f, p)
    }

    // ---------- Dark marble ----------

    /** Black marble: cloudy depths, faint grey veins and a few branching metal veins with a soft glow. */
    private fun marble(c: Canvas, w: Float, h: Float, rnd: Random, edge: Int, k: Float, p: Paint) {
        c.drawColor(0xFF0B0B0D.toInt())
        repeat(7) {
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
            val r = max(w, h) * (0.25f + 0.35f * rnd.nextFloat())
            val col = if (rnd.nextBoolean()) 0xFF1D1D21.toInt() else 0xFF050506.toInt()
            p.shader = RadialGradient(x, y, r, intArrayOf(withAlpha(col, 0.8f), withAlpha(col, 0f)), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
        }
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        val unit = min(w, h)
        fun vein(x0: Float, y0: Float, dir: Float, len: Float, width: Float, color: Int, depth: Int) {
            val path = Path()
            var x = x0; var y = y0; var a = dir
            path.moveTo(x, y)
            val steps = 40
            val step = len / steps
            for (i in 0 until steps) {
                a += (rnd.nextFloat() - 0.5f) * 0.5f
                x += cos(a) * step; y += sin(a) * step
                path.lineTo(x, y)
                if (depth > 0 && rnd.nextFloat() < 0.05f) vein(x, y, a + (if (rnd.nextBoolean()) 0.7f else -0.7f), len * 0.4f, width * 0.55f, color, depth - 1)
            }
            // Soft glow, then the crisp core.
            p.color = withAlpha(color, 0.12f); p.strokeWidth = width * 5f; c.drawPath(path, p)
            p.color = withAlpha(color, 0.28f); p.strokeWidth = width * 2.2f; c.drawPath(path, p)
            p.color = withAlpha(lerpColor(color, WHITE, 0.25f), 0.9f); p.strokeWidth = width; c.drawPath(path, p)
        }
        repeat((5 * k).roundToInt()) {
            vein(rnd.nextFloat() * w, rnd.nextFloat() * h, rnd.nextFloat() * 2 * PI.toFloat(), unit * 0.7f, unit * 0.0015f, 0xFF8E8E96.toInt(), 1)
        }
        repeat((2 * k).roundToInt().coerceAtLeast(2)) {
            val fromLeft = rnd.nextBoolean()
            vein(if (fromLeft) -unit * 0.05f else w * rnd.nextFloat(), if (fromLeft) h * rnd.nextFloat() else -unit * 0.05f,
                if (fromLeft) 0.3f + rnd.nextFloat() * 0.6f else 1.1f + rnd.nextFloat() * 0.6f, hypot(w, h) * 0.9f, unit * 0.0032f, edge, 2)
        }
        p.style = Paint.Style.FILL
        // Polished surface: a broad reflection.
        p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0x00FFFFFF, 0x12FFFFFF, 0x00FFFFFF), floatArrayOf(0.2f, 0.4f, 0.6f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        vignette(c, w, h, 0.45f, p)
    }

    // ---------- Obsidian ----------

    /** Volcanic glass: large dark facets, each with its own glossy gradient, a few edges catching the accent. */
    private fun obsidian(c: Canvas, w: Float, h: Float, rnd: Random, edge: Int, k: Float, p: Paint) {
        c.drawColor(INK)
        val cols = (3 + 3 * k).roundToInt()
        val cell = w / cols
        val rows = (h / cell).toInt() + 1
        val px = Array(rows + 1) { r -> FloatArray(cols + 1) { q -> q * cell + if (q in 1 until cols) (rnd.nextFloat() - 0.5f) * cell * 0.9f else 0f } }
        val py = Array(rows + 1) { r -> FloatArray(cols + 1) { q -> r * cell + if (r in 1 until rows) (rnd.nextFloat() - 0.5f) * cell * 0.9f else 0f } }
        val tri = Path()
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
        fun facet(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            tri.reset(); tri.moveTo(x1, y1); tri.lineTo(x2, y2); tri.lineTo(x3, y3); tri.close()
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val cx = (x1 + x2 + x3) / 3; val cy = (y1 + y2 + y3) / 3
            val gloss = rnd.nextFloat()
            val hi = lerpColor(SLATE, 0xFF3C3D45.toInt(), gloss * gloss)
            p.shader = LinearGradient(cx - cos(a) * cell, cy - sin(a) * cell, cx + cos(a) * cell, cy + sin(a) * cell, INK, hi, Shader.TileMode.CLAMP)
            c.drawPath(tri, p)
            p.shader = null
            line.strokeWidth = 1.2f; line.color = 0x99000000.toInt()
            c.drawPath(tri, line)
            if (rnd.nextFloat() < 0.18f) {
                line.strokeWidth = max(1f, cell * 0.008f); line.color = withAlpha(edge, 0.35f + 0.45f * rnd.nextFloat())
                c.drawLine(x1, y1, x2, y2, line)
            }
        }
        for (r in 0 until rows) for (q in 0 until cols) {
            val ax = px[r][q]; val ay = py[r][q]; val bx = px[r][q + 1]; val by = py[r][q + 1]
            val cx = px[r + 1][q + 1]; val cy = py[r + 1][q + 1]; val dx = px[r + 1][q]; val dy = py[r + 1][q]
            if (rnd.nextBoolean()) { facet(ax, ay, bx, by, cx, cy); facet(cx, cy, dx, dy, ax, ay) }
            else { facet(bx, by, cx, cy, dx, dy); facet(dx, dy, ax, ay, bx, by) }
        }
        // A cold specular streak across the glass.
        p.shader = LinearGradient(0f, h * 0.1f, w, h * 0.5f, intArrayOf(0x00FFFFFF, 0x1AFFFFFF, 0x00FFFFFF), floatArrayOf(0.35f, 0.5f, 0.65f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        vignette(c, w, h, 0.5f, p)
    }
}
