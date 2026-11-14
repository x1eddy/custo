package app.custo.feature.widgetsets

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ---------- Small Pen helpers (file-private so they never clash with other packs) ----------

private const val PC_W = 0xFFFFFFFF.toInt()
private const val PC_INK = 0xFF3A2A2A.toInt()
private const val PC_BLUSH = 0x66FF8FA3

/** One outline around several overlapping shapes. */
private fun pcUnion(vararg ps: Path): Path {
    val out = Path(ps[0])
    for (i in 1 until ps.size) out.op(ps[i], Path.Op.UNION)
    return out
}

/** A thick colored line with an ink outline (handles, stems, gills). */
private fun Pen.pcTube(p: Path, w: Float, col: Int) {
    stroke(p, w + lineW * 2f, ink.line)
    stroke(p, w, if (colorful) col else ink.paper)
}

/** Dashed stitch line. */
private fun Pen.pcDashed(p: Path, col: Int = ink.line, w: Float = lineW * 0.6f) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = w; color = col; strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(r(0.05f), r(0.04f)), 0f)
    }
    c.drawPath(p, paint)
}

private inline fun Pen.pcRot(deg: Float, u: Float = 0f, v: Float = 0f, crossinline block: () -> Unit) {
    c.withRotation(deg, x(u), y(v)) { block() }
}

private inline fun Pen.pcClip(p: Path, crossinline block: () -> Unit) {
    c.withSave { clipPath(p); block() }
}

private fun Pen.pcCheeks(u: Float, v: Float, gap: Float, rr: Float) {
    tint(circleP(u - gap, v, rr), PC_BLUSH)
    tint(circleP(u + gap, v, rr), PC_BLUSH)
}

private fun Pen.pcCuteFace(u: Float, v: Float, k: Float) {
    eyes(u, v, 0.1f * k, 0.035f * k)
    smile(u, v + 0.07f * k, 0.045f * k)
    pcCheeks(u, v + 0.07f * k, 0.17f * k, 0.04f * k)
}

/** Tiny "w" cat mouth. */
private fun Pen.pcCatMouth(u: Float, v: Float, k: Float) =
    stroke(path { m(u - 0.05f * k, v); q(u - 0.025f * k, v + 0.045f * k, u, v); q(u + 0.025f * k, v + 0.045f * k, u + 0.05f * k, v) }, lineW * 0.7f)

private fun Pen.pcFlame(u: Float, v: Float, h: Float, w: Float) = path {
    m(u, v); cu(u - w, v, u - w * 0.9f, v - h * 0.45f, u, v - h); cu(u + w * 0.9f, v - h * 0.45f, u + w, v, u, v); close()
}

private fun Pen.pcSpark(u: Float, v: Float, k: Float) = path {
    m(u, v - k); q(u + k * 0.13f, v - k * 0.13f, u + k, v); q(u + k * 0.13f, v + k * 0.13f, u, v + k)
    q(u - k * 0.13f, v + k * 0.13f, u - k, v); q(u - k * 0.13f, v - k * 0.13f, u, v - k); close()
}

/** Icing / honey that runs down from [top] to [base] with drips of [depths]. */
private fun Pen.pcDripPath(x0: Float, x1: Float, top: Float, base: Float, depths: FloatArray) = path {
    m(x0, top); l(x1, top); l(x1, base)
    val seg = (x1 - x0) / depths.size
    var px = x1
    for (dp in depths) {
        val nx = px - seg
        cu(px, base + dp * 1.35f, nx, base + dp * 1.35f, nx, base)
        px = nx
    }
    close()
}

private fun Pen.pcBow(u: Float, v: Float, k: Float, col: Int) {
    shape(poly(u - 0.04f * k, v + 0.04f * k, u - 0.24f * k, v + 0.44f * k, u - 0.1f * k, v + 0.38f * k, u, v + 0.12f * k), col)
    shape(poly(u + 0.04f * k, v + 0.04f * k, u + 0.24f * k, v + 0.44f * k, u + 0.1f * k, v + 0.38f * k, u, v + 0.12f * k), col)
    shape(path { m(u, v); cu(u - 0.15f * k, v - 0.32f * k, u - 0.52f * k, v - 0.28f * k, u - 0.46f * k, v); cu(u - 0.52f * k, v + 0.26f * k, u - 0.15f * k, v + 0.3f * k, u, v); close() }, col)
    shape(path { m(u, v); cu(u + 0.15f * k, v - 0.32f * k, u + 0.52f * k, v - 0.28f * k, u + 0.46f * k, v); cu(u + 0.52f * k, v + 0.26f * k, u + 0.15f * k, v + 0.3f * k, u, v); close() }, col)
    shape(circleP(u, v, 0.09f * k), col)
    tint(ovalP(u - 0.38f * k, v - 0.12f * k, u - 0.25f * k, v - 0.03f * k), 0x77FFFFFF)
}

private fun Pen.pcLeafH(u: Float, v: Float, len: Float) = path {
    m(u - len / 2, v); q(u, v - len * 0.45f, u + len / 2, v); q(u, v + len * 0.45f, u - len / 2, v); close()
}

/** A notched cherry-blossom petal pointing up from (u, v). */
private fun Pen.pcPetal(u: Float, v: Float, k: Float) = path {
    m(u, v)
    cu(u - 0.22f * k, v - 0.1f * k, u - 0.2f * k, v - 0.42f * k, u - 0.07f * k, v - 0.44f * k)
    l(u, v - 0.37f * k); l(u + 0.07f * k, v - 0.44f * k)
    cu(u + 0.2f * k, v - 0.42f * k, u + 0.22f * k, v - 0.1f * k, u, v)
    close()
}

private fun Pen.pcBlossom(u: Float, v: Float, k: Float, spin: Float = 0f) {
    for (i in 0 until 5) pcRot(spin + i * 72f, u, v) { shape(pcPetal(u, v, k), PackCArt.SAKURA) }
    tint(circleP(u, v, 0.12f * k), PackCArt.SAKURA_DEEP)
    for (i in 0 until 5) {
        val a = -PI / 2 + i * 2 * PI / 5 + 0.6
        val ex = u + (cos(a) * 0.17f * k).toFloat(); val ey = v + (sin(a) * 0.17f * k).toFloat()
        stroke(path { m(u, v); l(ex, ey) }, lineW * 0.5f, if (colorful) PackCArt.SAKURA_DARK else ink.line)
        solid(circleP(ex, ey, 0.025f * k), PackCArt.GOLD)
    }
}

private fun Pen.pcCloudP(u: Float, v: Float, k: Float) = pcUnion(
    circleP(u - 0.22f * k, v + 0.06f * k, 0.18f * k),
    circleP(u, v - 0.06f * k, 0.25f * k),
    circleP(u + 0.22f * k, v + 0.04f * k, 0.19f * k),
    rectP(u - 0.38f * k, v + 0.02f * k, u + 0.38f * k, v + 0.24f * k, 0.11f * k),
)

/** Squishy mochi body: a soft dome with a flat bottom. */
private fun Pen.pcDome() = path {
    m(-0.44f, 0.3f); cu(-0.5f, -0.4f, 0.5f, -0.4f, 0.44f, 0.3f)
    q(0.44f, 0.42f, 0.3f, 0.42f); l(-0.3f, 0.42f); q(-0.44f, 0.42f, -0.44f, 0.3f); close()
}

private fun Pen.pcSquishShade() {
    tint(ovalP(-0.4f, 0.37f, 0.4f, 0.5f), 0x22000000)
}

private fun Pen.pcSquishFace(v: Float, catty: Boolean = false) {
    eyes(0f, v, 0.13f, 0.034f)
    if (catty) pcCatMouth(0f, v + 0.05f, 1f) else smile(0f, v + 0.05f, 0.035f)
    tint(ovalP(-0.3f, v + 0.04f, -0.18f, v + 0.11f), PC_BLUSH)
    tint(ovalP(0.18f, v + 0.04f, 0.3f, v + 0.11f), PC_BLUSH)
}

private fun Pen.pcSquishShine() = tint(ovalP(-0.3f, -0.12f, -0.14f, -0.02f), 0x99FFFFFF.toInt())

// ---------- Big shared pieces ----------

private fun Pen.pcTeddy(wave: Boolean, heart: Boolean) {
    val fur = PackCArt.BEAR; val light = PackCArt.BEAR_LIGHT
    for (sx in floatArrayOf(-1f, 1f)) {
        shape(circleP(0.2f * sx, -0.39f, 0.095f), fur)
        tint(circleP(0.2f * sx, -0.39f, 0.05f), PackCArt.PINK_SOFT)
    }
    shape(ovalP(-0.25f, -0.02f, 0.25f, 0.44f), fur)
    tint(ovalP(-0.14f, 0.1f, 0.14f, 0.38f), light)
    if (!heart) {
        val patch = rectP(0.02f, 0.16f, 0.13f, 0.27f, 0.015f)
        tint(patch, PackCArt.PINK_SOFT)
        pcDashed(patch, ink.line, lineW * 0.45f)
    }
    for (sx in floatArrayOf(-1f, 1f)) {
        val u = 0.16f * sx
        shape(ovalP(u - 0.1f, 0.35f, u + 0.1f, 0.49f), fur)
        tint(circleP(u, 0.42f, 0.04f), PackCArt.PINK_SOFT)
    }
    if (heart) {
        shape(heartPath(0f, 0.16f, 0.5f), ink.accent)
        tint(ovalP(-0.13f, 0.08f, -0.06f, 0.13f), 0x88FFFFFF.toInt())
    }
    pcRot(if (heart) -40f else 20f, -0.24f, 0.08f) { shape(ovalP(-0.31f, -0.02f, -0.17f, 0.26f), fur) }
    if (wave) {
        pcRot(-35f, 0.3f, -0.07f) { shape(ovalP(0.23f, -0.24f, 0.37f, 0.06f), fur) }
    } else {
        pcRot(if (heart) 40f else -20f, 0.24f, 0.08f) { shape(ovalP(0.17f, -0.02f, 0.31f, 0.26f), fur) }
    }
    shape(circleP(0f, -0.19f, 0.24f), fur)
    shape(ovalP(-0.1f, -0.15f, 0.1f, -0.01f), light)
    solid(ovalP(-0.045f, -0.135f, 0.045f, -0.085f), PC_INK)
    stroke(path { m(0f, -0.085f); l(0f, -0.055f); m(-0.045f, -0.04f); q(-0.02f, -0.025f, 0f, -0.055f); q(0.02f, -0.025f, 0.045f, -0.04f) }, lineW * 0.6f)
    eyes(0f, -0.24f, 0.1f, 0.032f)
    pcCheeks(0f, -0.12f, 0.16f, 0.04f)
}

private fun Pen.pcCatHead(u: Float, v: Float, k: Float, fur: Int, stripes: Boolean) {
    val head = pcUnion(
        ovalP(u - 0.44f * k, v - 0.3f * k, u + 0.44f * k, v + 0.36f * k),
        poly(u - 0.42f * k, v - 0.06f * k, u - 0.36f * k, v - 0.5f * k, u - 0.06f * k, v - 0.28f * k),
        poly(u + 0.42f * k, v - 0.06f * k, u + 0.36f * k, v - 0.5f * k, u + 0.06f * k, v - 0.28f * k),
    )
    shape(head, fur)
    tint(poly(u - 0.34f * k, v - 0.18f * k, u - 0.32f * k, v - 0.4f * k, u - 0.16f * k, v - 0.27f * k), PackCArt.PINK_SOFT)
    tint(poly(u + 0.34f * k, v - 0.18f * k, u + 0.32f * k, v - 0.4f * k, u + 0.16f * k, v - 0.27f * k), PackCArt.PINK_SOFT)
    if (stripes && colorful) {
        for (i in -1..1) stroke(path { m(u + i * 0.07f * k, v - 0.29f * k); l(u + i * 0.06f * k, v - 0.19f * k) }, lineW * 0.9f, PackCArt.GINGER_DARK)
    }
    eyes(u, v + 0.02f * k, 0.16f * k, 0.042f * k)
    solid(poly(u - 0.035f * k, v + 0.09f * k, u + 0.035f * k, v + 0.09f * k, u, v + 0.13f * k), PackCArt.PINK_NOSE)
    pcCatMouth(u, v + 0.14f * k, k)
    pcCheeks(u, v + 0.13f * k, 0.25f * k, 0.05f * k)
    for (sx in floatArrayOf(-1f, 1f)) {
        for (j in 0..1) stroke(path { m(u + sx * 0.33f * k, v + (0.1f + j * 0.06f) * k); l(u + sx * 0.5f * k, v + (0.07f + j * 0.1f) * k) }, lineW * 0.5f)
    }
}

private fun Pen.pcMapleP() = path {
    val pts = floatArrayOf(
        0f, -0.48f, 0.08f, -0.3f, 0.2f, -0.36f, 0.16f, -0.12f, 0.4f, -0.26f, 0.34f, -0.08f,
        0.48f, -0.02f, 0.3f, 0.1f, 0.34f, 0.2f, 0.12f, 0.16f, 0.04f, 0.26f, 0f, 0.22f,
    )
    m(pts[0], pts[1])
    var i = 2
    while (i < pts.size) { l(pts[i], pts[i + 1]); i += 2 }
    i = pts.size - 4
    while (i >= 2) { l(-pts[i], pts[i + 1]); i -= 2 }
    close()
}

private fun Pen.pcRainbowArcs(u: Float, v: Float, outer: Float, band: Float) {
    val colors = PackCArt.RAINBOW
    val total = band * colors.size
    val mid = outer - total / 2
    val arc = { rad: Float -> path { addArc(RectF(x(u - rad), y(v - rad), x(u + rad), y(v + rad)), 180f, 180f) } }
    stroke(arc(mid), r(total) + lineW * 2f, ink.line)
    colors.forEachIndexed { i, col ->
        val rad = outer - band * (i + 0.5f)
        stroke(arc(rad), r(band) + 0.5f, if (colorful) col else ink.paper)
    }
}

/** Sticker art and frame painters for theme pack C. */
internal object PackCArt {

    // ---------- Colors ----------
    const val RED = 0xFFE53945.toInt()
    const val GOLD = 0xFFFFC94D.toInt()
    const val GREEN = 0xFF2F7D4A.toInt()
    const val GREEN_LIGHT = 0xFF6FBF73.toInt()
    const val GINGER = 0xFFC8834A.toInt()
    const val COCOA_BROWN = 0xFF7B4A2E.toInt()
    const val CREAM = 0xFFFFF4E0.toInt()
    const val BRICK = 0xFFB5523B.toInt()
    const val WOOD = 0xFF9B6237.toInt()
    const val WOOD_DARK = 0xFF6E4122.toInt()
    const val FLAME_O = 0xFFFF8A3D.toInt()
    const val FLAME_Y = 0xFFFFD54F.toInt()
    const val BEAR = 0xFFC99A6E.toInt()
    const val BEAR_LIGHT = 0xFFF1D7B8.toInt()
    const val PINK_SOFT = 0xFFFFB7C5.toInt()
    const val PINK_NOSE = 0xFFFF8FAB.toInt()
    const val HONEY = 0xFFFFB627.toInt()
    const val SPONGE = 0xFFFFE2B3.toInt()
    const val SPONGE_PINK = 0xFFFFC2D1.toInt()
    const val FROST_PINK = 0xFFFF9EBD.toInt()
    const val MINT = 0xFFA6E3D0.toInt()
    const val BERRY = 0xFFF2455C.toInt()
    const val PINK_HOT = 0xFFFF6FA5.toInt()
    const val PINK_LIGHT = 0xFFFFD1E3.toInt()
    const val PEARL = 0xFFFFF5F8.toInt()
    const val ROSE_GOLD = 0xFFF2B8A2.toInt()
    const val SAKURA = 0xFFFFCADB.toInt()
    const val SAKURA_DEEP = 0xFFFF8FB1.toInt()
    const val SAKURA_DARK = 0xFFC2456E.toInt()
    const val BARK = 0xFF6B4436.toInt()
    const val LANTERN = 0xFFE8484A.toInt()
    const val GINGER_CAT = 0xFFFFC27A.toInt()
    const val GINGER_DARK = 0xFFE59A4A.toInt()
    const val GREY_CAT = 0xFFCBCFD9.toInt()
    const val FISH_BLUE = 0xFF8EC9F0.toInt()
    const val C_YARN = 0xFFB9A3F0.toInt()
    const val LEAF_ORANGE = 0xFFF08A3C.toInt()
    const val LEAF_YELLOW = 0xFFF7C04A.toInt()
    const val LEAF_RED = 0xFFD9542F.toInt()
    const val C_ACORN = 0xFFC9874A.toInt()
    const val ACORN_CAP = 0xFF8A5530.toInt()
    const val MUSH_RED = 0xFFE5533D.toInt()
    const val C_PUMPKIN = 0xFFF28C28.toInt()
    const val TENT = 0xFFF2994A.toInt()
    const val SLATE = 0xFF6F7FA6.toInt()
    const val SLATE_LIGHT = 0xFF9AA8C9.toInt()
    const val PINE = 0xFF2E7D5B.toInt()
    const val PINE_LIGHT = 0xFF4FA176.toInt()
    const val SUN = 0xFFFFD54F.toInt()
    const val SUN_RAY = 0xFFFFA94D.toInt()
    const val COFFEE = 0xFF6B3E26.toInt()
    const val LATTE = 0xFFB07848.toInt()
    const val FOAM = 0xFFFFF1DC.toInt()
    const val C_CROISSANT = 0xFFE9A84C.toInt()
    const val CUP = 0xFFFFFAF2.toInt()
    val RAINBOW = intArrayOf(0xFFFF6B6B.toInt(), 0xFFFFA94D.toInt(), 0xFFFFE066.toInt(), 0xFF8CE99A.toInt(), 0xFF74C0FC.toInt(), 0xFFB197FC.toInt())

    // ---------- Cozy Christmas ----------

    val GINGERBREAD: StickerArt = {
        val body = pcUnion(
            circleP(0f, -0.27f, 0.18f),
            rectP(-0.17f, -0.14f, 0.17f, 0.24f, 0.08f),
            rectP(-0.42f, -0.11f, 0.42f, 0.04f, 0.075f),
            rectP(-0.2f, 0.08f, -0.03f, 0.47f, 0.085f),
            rectP(0.03f, 0.08f, 0.2f, 0.47f, 0.085f),
        )
        shape(body, GINGER)
        val icing = lineW * 0.9f
        val ic = if (colorful) PC_W else ink.line
        for (sx in floatArrayOf(-1f, 1f)) {
            stroke(path { m(0.34f * sx, -0.09f); q(0.38f * sx, -0.035f, 0.34f * sx, 0.02f) }, icing, ic)
            stroke(path { m(0.18f * sx, 0.38f); q(0.115f * sx, 0.43f, 0.05f * sx, 0.38f) }, icing, ic)
        }
        solid(circleP(0f, -0.04f, 0.035f), RED)
        solid(circleP(0f, 0.08f, 0.035f), GREEN)
        eyes(0f, -0.3f, 0.065f, 0.03f)
        stroke(path { m(-0.06f, -0.22f); q(0f, -0.17f, 0.06f, -0.22f) }, icing, ic)
        pcCheeks(0f, -0.23f, 0.12f, 0.03f)
    }

    val COCOA: StickerArt = {
        val steam = if (colorful) (ink.line and 0x00FFFFFF) or 0x66000000 else ink.line
        for (i in 0..2) {
            val u = -0.2f + i * 0.12f
            stroke(path { m(u, -0.24f); q(u - 0.05f, -0.31f, u, -0.38f); q(u + 0.05f, -0.44f, u, -0.49f) }, lineW * 0.8f, steam)
        }
        pcTube(path { addArc(RectF(x(0.04f), y(-0.04f), x(0.42f), y(0.32f)), -110f, 220f) }, r(0.065f), CREAM)
        val mug = path { m(-0.36f, -0.16f); l(0.2f, -0.16f); l(0.17f, 0.36f); q(0.165f, 0.44f, 0.08f, 0.44f); l(-0.24f, 0.44f); q(-0.33f, 0.44f, -0.33f, 0.36f); close() }
        shape(mug, CREAM)
        pcClip(path { m(-0.36f, -0.16f); l(0.2f, -0.16f); l(0.17f, 0.36f); l(-0.33f, 0.36f); close() }) {
            tint(rectP(-0.4f, 0.0f, 0.25f, 0.06f), RED)
            tint(rectP(-0.4f, 0.1f, 0.25f, 0.13f), GREEN)
        }
        stroke(mug)
        tint(heartPath(-0.08f, 0.26f, 0.22f), RED)
        shape(ovalP(-0.36f, -0.21f, 0.2f, -0.11f), CREAM)
        tint(ovalP(-0.32f, -0.19f, 0.16f, -0.13f), COCOA_BROWN)
        shape(rectP(-0.22f, -0.25f, -0.11f, -0.15f, 0.025f), PC_W)
        shape(rectP(-0.05f, -0.26f, 0.06f, -0.16f, 0.025f), PINK_SOFT)
    }

    val WREATH: StickerArt = {
        pcTube(circleP(0f, 0f, 0.3f), r(0.13f), GREEN)
        for (i in 0 until 14) {
            pcRot(i * 360f / 14f) { shape(pcLeafH(0f, if (i % 2 == 0) -0.36f else -0.26f, 0.2f), if (i % 2 == 0) GREEN_LIGHT else GREEN) }
        }
        for (i in 0 until 6) {
            val a = -PI / 2 + i * 2 * PI / 6 + 0.3
            shape(circleP((cos(a) * 0.3f).toFloat(), (sin(a) * 0.3f).toFloat(), 0.04f), RED)
        }
        pcBow(0f, 0.3f, 0.45f, RED)
    }

    val ORNAMENT: StickerArt = {
        stroke(circleP(0f, -0.43f, 0.05f), lineW * 0.8f)
        shape(rectP(-0.09f, -0.4f, 0.09f, -0.29f, 0.02f), GOLD)
        val ball = circleP(0f, 0.08f, 0.37f)
        shape(ball, RED)
        pcClip(ball) {
            tint(rectP(-0.5f, 0.0f, 0.5f, 0.15f), GOLD)
            for (i in -3..3) tint(circleP(i * 0.11f, 0.075f, 0.022f), PC_W)
            stroke(path { m(-0.5f, 0f); l(0.5f, 0f); m(-0.5f, 0.15f); l(0.5f, 0.15f) }, lineW * 0.6f)
            for (i in -4..4) stroke(path { m(i * 0.1f, -0.15f); l(i * 0.1f + 0.05f, -0.09f); l(i * 0.1f + 0.1f, -0.15f) }, lineW * 0.5f, if (colorful) GOLD else ink.line)
        }
        stroke(ball)
        tint(ovalP(-0.24f, -0.17f, -0.1f, -0.05f), 0x99FFFFFF.toInt())
    }

    val FIREPLACE: StickerArt = {
        val wall = rectP(-0.42f, -0.22f, 0.42f, 0.46f, 0.03f)
        shape(wall, BRICK)
        pcClip(wall) {
            val mortar = if (colorful) 0x55FFFFFF else ink.line
            var row = 0
            var yy = -0.12f
            while (yy < 0.46f) {
                stroke(path { m(-0.5f, yy); l(0.5f, yy) }, lineW * 0.5f, mortar)
                var xx = if (row % 2 == 0) -0.36f else -0.28f
                while (xx < 0.45f) { stroke(path { m(xx, yy); l(xx, yy - 0.1f) }, lineW * 0.5f, mortar); xx += 0.16f }
                yy += 0.1f; row++
            }
        }
        val hole = path { m(-0.25f, 0.46f); l(-0.25f, 0.02f); q(-0.25f, -0.08f, -0.15f, -0.08f); l(0.15f, -0.08f); q(0.25f, -0.08f, 0.25f, 0.02f); l(0.25f, 0.46f); close() }
        shape(hole, 0xFF3B2B2B.toInt())
        tint(circleP(0f, 0.3f, 0.2f), 0x44FFB74D)
        shape(pcFlame(0f, 0.38f, 0.38f, 0.17f), FLAME_O)
        shape(pcFlame(-0.11f, 0.38f, 0.22f, 0.08f), FLAME_O)
        shape(pcFlame(0.11f, 0.38f, 0.24f, 0.08f), FLAME_O)
        tint(pcFlame(0f, 0.38f, 0.2f, 0.09f), FLAME_Y)
        pcRot(10f, 0f, 0.4f) { shape(rectP(-0.2f, 0.36f, 0.2f, 0.44f, 0.04f), WOOD) }
        pcRot(-10f, 0f, 0.4f) { shape(rectP(-0.2f, 0.36f, 0.2f, 0.44f, 0.04f), WOOD_DARK) }
        shape(rectP(-0.5f, -0.33f, 0.5f, -0.21f, 0.03f), WOOD)
        stroke(path { m(-0.46f, -0.3f); q(-0.23f, -0.18f, 0f, -0.3f); q(0.23f, -0.18f, 0.46f, -0.3f) }, lineW * 0.9f, if (colorful) GREEN else ink.line)
        for (i in 0..4) solid(circleP(-0.36f + i * 0.18f, -0.25f + (if (i % 2 == 0) 0.02f else 0.05f), 0.025f), if (i % 2 == 0) RED else GOLD)
    }

    val LIGHTS: StickerArt = {
        val p0x = -0.48f; val p0y = -0.2f; val p1x = 0f; val p1y = 0.25f; val p2x = 0.48f; val p2y = -0.2f
        stroke(path { m(p0x, p0y); q(p1x, p1y, p2x, p2y) }, lineW * 0.7f, if (colorful) GREEN else ink.line)
        val cols = intArrayOf(RED, GOLD, 0xFF5DADE2.toInt(), GREEN_LIGHT, 0xFFFF8FC8.toInt())
        for (i in 0 until 5) {
            val t = 0.1f + i * 0.2f
            val bx = (1 - t) * (1 - t) * p0x + 2 * (1 - t) * t * p1x + t * t * p2x
            val by = (1 - t) * (1 - t) * p0y + 2 * (1 - t) * t * p1y + t * t * p2y
            tint(circleP(bx, by + 0.14f, 0.13f), (cols[i] and 0x00FFFFFF) or 0x33000000)
            shape(rectP(bx - 0.035f, by - 0.01f, bx + 0.035f, by + 0.05f, 0.01f), 0xFF5A5A5A.toInt())
            shape(ovalP(bx - 0.06f, by + 0.04f, bx + 0.06f, by + 0.24f), cols[i])
            tint(ovalP(bx - 0.035f, by + 0.07f, bx - 0.005f, by + 0.13f), 0x99FFFFFF.toInt())
        }
    }

    // ---------- Teddy bears ----------

    val TEDDY: StickerArt = { pcTeddy(wave = false, heart = false) }
    val TEDDY_WAVE: StickerArt = { pcTeddy(wave = true, heart = false) }
    val TEDDY_HEART: StickerArt = { pcTeddy(wave = false, heart = true) }

    val TEDDY_FACE: StickerArt = {
        for (sx in floatArrayOf(-1f, 1f)) {
            shape(circleP(0.3f * sx, -0.27f, 0.14f), BEAR)
            tint(circleP(0.3f * sx, -0.27f, 0.075f), PINK_SOFT)
        }
        shape(ovalP(-0.42f, -0.34f, 0.42f, 0.42f), BEAR)
        shape(ovalP(-0.16f, 0.06f, 0.16f, 0.3f), BEAR_LIGHT)
        solid(ovalP(-0.07f, 0.08f, 0.07f, 0.15f), PC_INK)
        stroke(path { m(0f, 0.15f); l(0f, 0.2f); m(-0.07f, 0.22f); q(-0.03f, 0.25f, 0f, 0.2f); q(0.03f, 0.25f, 0.07f, 0.22f) }, lineW * 0.7f)
        eyes(0f, -0.04f, 0.16f, 0.045f)
        pcCheeks(0f, 0.1f, 0.25f, 0.06f)
        pcBow(0.27f, -0.32f, 0.3f, ink.accent)
    }

    val HONEY_POT: StickerArt = {
        pcTube(path { m(0.08f, -0.14f); l(0.3f, -0.46f) }, r(0.04f), WOOD)
        val pot = path { m(-0.3f, -0.12f); cu(-0.52f, 0.0f, -0.46f, 0.45f, -0.2f, 0.45f); l(0.2f, 0.45f); cu(0.46f, 0.45f, 0.52f, 0.0f, 0.3f, -0.12f); close() }
        shape(pot, 0xFFE59A4A.toInt())
        tint(ovalP(-0.32f, 0.02f, -0.22f, 0.2f), 0x55FFFFFF)
        shape(rectP(-0.35f, -0.2f, 0.35f, -0.1f, 0.04f), 0xFFC97B33.toInt())
        shape(pcDripPath(-0.33f, 0.33f, -0.11f, -0.06f, floatArrayOf(0.02f, 0.1f, 0.03f, 0.13f, 0.02f)), HONEY)
        shape(rectP(-0.17f, 0.12f, 0.17f, 0.32f, 0.05f), CREAM)
        tint(heartPath(0f, 0.21f, 0.18f), ink.accent)
        tint(ovalP(-0.25f, -0.19f, -0.05f, -0.15f), 0x66FFFFFF)
    }

    val STITCH_HEART: StickerArt = {
        val h = heartPath(0f, 0.04f, 1.05f)
        shape(h, ink.accent)
        pcDashed(heartPath(0f, 0.04f, 0.82f), if (colorful) PC_W else ink.line, lineW * 0.7f)
        tint(ovalP(-0.28f, -0.2f, -0.14f, -0.08f), 0x88FFFFFF.toInt())
    }

    // ---------- Cake ----------

    val LAYER_CAKE: StickerArt = {
        shape(ovalP(-0.48f, 0.36f, 0.48f, 0.49f), PC_W)
        shape(rectP(-0.4f, 0.04f, 0.4f, 0.42f, 0.05f), SPONGE_PINK)
        tint(rectP(-0.4f, 0.22f, 0.4f, 0.27f), CREAM)
        shape(rectP(-0.28f, -0.25f, 0.28f, 0.08f, 0.05f), SPONGE)
        shape(pcDripPath(-0.41f, 0.41f, 0.03f, 0.09f, floatArrayOf(0.03f, 0.08f, 0.02f, 0.1f, 0.03f, 0.07f)), FROST_PINK)
        shape(pcDripPath(-0.29f, 0.29f, -0.26f, -0.18f, floatArrayOf(0.04f, 0.09f, 0.03f, 0.08f)), PC_W)
        val sp = intArrayOf(MINT, GOLD, 0xFF74C0FC.toInt(), PC_W)
        for (i in 0 until 6) {
            val u = -0.3f + i * 0.12f; val v = 0.32f + (if (i % 2 == 0) 0.02f else -0.03f)
            stroke(path { m(u - 0.025f, v - 0.015f); l(u + 0.025f, v + 0.015f) }, lineW * 0.9f, if (colorful) sp[i % sp.size] else ink.line)
        }
        stroke(path { m(0f, -0.38f); q(0.04f, -0.46f, 0.1f, -0.48f) }, lineW * 0.8f, if (colorful) GREEN else ink.line)
        shape(circleP(0f, -0.32f, 0.075f), BERRY)
        tint(circleP(-0.025f, -0.345f, 0.02f), 0xAAFFFFFF.toInt())
    }

    val CUPCAKE: StickerArt = {
        val wrap = poly(-0.3f, 0.04f, 0.3f, 0.04f, 0.22f, 0.46f, -0.22f, 0.46f)
        shape(wrap, ink.accent)
        for (i in -2..2) stroke(path { m(i * 0.11f, 0.06f); l(i * 0.08f, 0.44f) }, lineW * 0.5f, if (colorful) 0x55FFFFFF else ink.line)
        val frost = pcUnion(ovalP(-0.36f, -0.08f, 0.36f, 0.14f), ovalP(-0.28f, -0.24f, 0.28f, -0.0f), ovalP(-0.17f, -0.37f, 0.17f, -0.15f))
        shape(frost, FROST_PINK)
        stroke(path { m(-0.24f, 0.02f); q(0f, 0.08f, 0.24f, 0.02f); m(-0.16f, -0.13f); q(0f, -0.08f, 0.16f, -0.13f) }, lineW * 0.6f, if (colorful) 0x88FFFFFF.toInt() else ink.line)
        val sp = intArrayOf(MINT, GOLD, 0xFF74C0FC.toInt(), PC_W)
        val pos = floatArrayOf(-0.2f, -0.04f, 0.05f, -0.0f, 0.2f, -0.06f, -0.08f, -0.2f, 0.1f, -0.2f)
        for (i in 0 until 5) {
            val u = pos[i * 2]; val v = pos[i * 2 + 1]
            stroke(path { m(u - 0.025f, v + 0.01f); l(u + 0.025f, v - 0.01f) }, lineW * 0.9f, if (colorful) sp[i % sp.size] else ink.line)
        }
        stroke(path { m(0.02f, -0.44f); q(0.06f, -0.5f, 0.12f, -0.5f) }, lineW * 0.7f, if (colorful) GREEN else ink.line)
        shape(circleP(0.02f, -0.4f, 0.07f), BERRY)
        tint(circleP(-0.005f, -0.415f, 0.02f), 0xAAFFFFFF.toInt())
    }

    val STRAWBERRY: StickerArt = {
        val body = path { m(0f, 0.46f); cu(-0.3f, 0.36f, -0.44f, 0f, -0.34f, -0.14f); q(-0.2f, -0.3f, 0f, -0.22f); q(0.2f, -0.3f, 0.34f, -0.14f); cu(0.44f, 0f, 0.3f, 0.36f, 0f, 0.46f); close() }
        shape(body, BERRY)
        val seeds = floatArrayOf(-0.2f, -0.06f, 0.2f, -0.06f, -0.24f, 0.12f, 0.24f, 0.12f, -0.1f, 0.3f, 0.1f, 0.3f, 0f, 0.38f)
        for (i in 0 until seeds.size / 2) tint(ovalP(seeds[i * 2] - 0.015f, seeds[i * 2 + 1] - 0.025f, seeds[i * 2] + 0.015f, seeds[i * 2 + 1] + 0.025f), 0xFFFFE08A.toInt())
        shape(starPath(0f, -0.24f, 0.22f, 0.08f, 6), GREEN_LIGHT)
        stroke(path { m(0f, -0.28f); q(0.02f, -0.4f, 0.08f, -0.46f) }, lineW * 1.1f, if (colorful) GREEN else ink.line)
        eyes(0f, 0.06f, 0.09f, 0.03f)
        smile(0f, 0.12f, 0.04f)
        tint(ovalP(-0.25f, -0.12f, -0.17f, -0.02f), 0x77FFFFFF)
    }

    val CANDLE: StickerArt = {
        tint(circleP(0f, -0.3f, 0.2f), 0x44FFD54F)
        val body = rectP(-0.08f, -0.18f, 0.08f, 0.46f, 0.03f)
        shape(body, PC_W)
        pcClip(body) {
            var v = -0.3f
            while (v < 0.5f) { tint(poly(-0.1f, v, 0.1f, v - 0.08f, 0.1f, v - 0.02f, -0.1f, v + 0.06f), ink.accent); v += 0.14f }
        }
        stroke(body)
        stroke(path { m(0f, -0.18f); l(0f, -0.24f) }, lineW * 0.7f)
        shape(pcFlame(0f, -0.21f, 0.26f, 0.1f), FLAME_Y)
        tint(pcFlame(0f, -0.22f, 0.13f, 0.05f), FLAME_O)
    }

    val CAKE_SLICE: StickerArt = {
        shape(ovalP(-0.5f, 0.3f, 0.5f, 0.46f), PC_W)
        val front = poly(-0.46f, 0.04f, 0.42f, -0.2f, 0.42f, 0.38f, -0.46f, 0.38f)
        shape(front, SPONGE)
        pcClip(front) {
            tint(rectP(-0.5f, 0.13f, 0.5f, 0.19f), FROST_PINK)
            tint(rectP(-0.5f, 0.27f, 0.5f, 0.32f), FROST_PINK)
            tint(poly(-0.5f, 0.1f, 0.5f, -0.18f, 0.5f, -0.1f, -0.5f, 0.15f), BERRY)
        }
        stroke(front)
        pcTube(path { m(-0.44f, 0.01f); l(0.4f, -0.23f) }, r(0.05f), PC_W)
        shape(circleP(0.26f, -0.29f, 0.07f), BERRY)
        tint(circleP(0.24f, -0.31f, 0.02f), 0xAAFFFFFF.toInt())
        stroke(path { m(0.26f, -0.36f); l(0.29f, -0.42f) }, lineW * 0.7f, if (colorful) GREEN else ink.line)
    }

    // ---------- Pink ----------

    val GLOSSY_HEART: StickerArt = {
        shape(heartPath(0f, 0.03f, 1.1f), PINK_HOT)
        tint(heartPath(0.04f, 0.08f, 0.85f), 0x33C2185B)
        tint(heartPath(0f, 0.03f, 0.8f), 0x22FFFFFF)
        pcRot(-35f, -0.2f, -0.12f) { tint(ovalP(-0.3f, -0.17f, -0.1f, -0.07f), 0xCCFFFFFF.toInt()) }
        tint(circleP(-0.07f, -0.2f, 0.025f), 0xCCFFFFFF.toInt())
    }

    val PEARLS: StickerArt = {
        val n = 9
        stroke(path { addArc(RectF(x(-0.4f), y(-0.5f), x(0.4f), y(0.3f)), 10f, 160f) }, lineW * 0.4f)
        for (i in 0 until n) {
            val a = (10.0 + i * 160.0 / (n - 1)) * PI / 180
            val u = (cos(a) * 0.4f).toFloat(); val v = -0.1f + (sin(a) * 0.4f).toFloat()
            shape(circleP(u, v, 0.065f), PEARL)
            tint(circleP(u - 0.02f, v - 0.02f, 0.02f), PC_W)
            tint(circleP(u + 0.015f, v + 0.02f, 0.03f), 0x22FF6FA5)
        }
        shape(heartPath(0f, 0.36f, 0.28f), PINK_HOT)
        tint(circleP(-0.04f, 0.33f, 0.018f), PC_W)
    }

    val RIBBON: StickerArt = {
        shape(path { m(-0.05f, -0.05f); cu(-0.2f, 0.15f, -0.05f, 0.3f, -0.25f, 0.48f); l(-0.12f, 0.46f); cu(0.06f, 0.3f, -0.05f, 0.12f, 0.06f, -0.03f); close() }, ink.accent)
        shape(path { m(0.05f, -0.05f); cu(0.2f, 0.15f, 0.08f, 0.3f, 0.3f, 0.46f); l(0.17f, 0.48f); cu(-0.02f, 0.3f, 0.06f, 0.12f, -0.06f, -0.03f); close() }, ink.accent)
        pcBow(0f, -0.12f, 0.9f, ink.accent)
        tint(circleP(0f, -0.12f, 0.035f), 0x88FFFFFF.toInt())
    }

    val SPARKLES: StickerArt = {
        shape(pcSpark(-0.05f, 0.05f, 0.42f), PC_W)
        tint(pcSpark(-0.05f, 0.05f, 0.2f), PINK_LIGHT)
        shape(pcSpark(0.32f, -0.3f, 0.15f), PINK_HOT)
        shape(pcSpark(0.3f, 0.34f, 0.1f), PINK_LIGHT)
    }

    val PERFUME: StickerArt = {
        shape(rectP(-0.06f, -0.3f, 0.06f, -0.16f, 0.01f), ROSE_GOLD)
        shape(rectP(-0.13f, -0.48f, 0.13f, -0.29f, 0.05f), PINK_HOT)
        tint(rectP(-0.09f, -0.45f, -0.04f, -0.33f, 0.02f), 0x66FFFFFF)
        val bottle = rectP(-0.32f, -0.17f, 0.32f, 0.46f, 0.14f)
        shape(bottle, PINK_LIGHT)
        pcClip(bottle) { tint(rectP(-0.4f, 0.12f, 0.4f, 0.5f), 0x55FF6FA5) }
        stroke(bottle)
        tint(heartPath(0.02f, 0.12f, 0.3f), PINK_HOT)
        tint(rectP(-0.24f, -0.09f, -0.15f, 0.28f, 0.04f), 0x99FFFFFF.toInt())
    }

    val TIARA: StickerArt = {
        val crown = path {
            m(-0.44f, 0.22f); l(-0.38f, -0.12f); l(-0.2f, 0.04f); l(0f, -0.3f); l(0.2f, 0.04f); l(0.38f, -0.12f); l(0.44f, 0.22f)
            q(0f, 0.12f, -0.44f, 0.22f); close()
        }
        shape(crown, ROSE_GOLD)
        tint(path { m(-0.42f, 0.18f); q(0f, 0.08f, 0.42f, 0.18f); l(0.43f, 0.22f); q(0f, 0.12f, -0.43f, 0.22f); close() }, 0x33C2185B)
        shape(circleP(0f, -0.33f, 0.07f), PINK_HOT)
        shape(circleP(-0.38f, -0.15f, 0.05f), PEARL)
        shape(circleP(0.38f, -0.15f, 0.05f), PEARL)
        shape(heartPath(0f, 0.06f, 0.3f), PINK_HOT)
        for (i in listOf(-0.26f, -0.13f, 0.13f, 0.26f)) shape(circleP(i, 0.15f, 0.03f), PEARL)
        tint(circleP(-0.02f, -0.35f, 0.02f), PC_W)
    }

    // ---------- Squishy ----------

    val BLOB_CAT: StickerArt = {
        pcSquishShade()
        val body = pcUnion(pcDome(), poly(-0.38f, 0.0f, -0.32f, -0.42f, -0.08f, -0.2f), poly(0.38f, 0.0f, 0.32f, -0.42f, 0.08f, -0.2f))
        shape(body, 0xFFFFF1DC.toInt())
        tint(poly(-0.3f, -0.12f, -0.29f, -0.32f, -0.15f, -0.2f), PINK_SOFT)
        tint(poly(0.3f, -0.12f, 0.29f, -0.32f, 0.15f, -0.2f), PINK_SOFT)
        if (colorful) for (i in -1..1) stroke(path { m(i * 0.07f, -0.21f); l(i * 0.06f, -0.13f) }, lineW * 0.8f, GINGER_DARK)
        pcSquishShine()
        pcSquishFace(0.1f, catty = true)
    }

    val BLOB_FROG: StickerArt = {
        pcSquishShade()
        val body = pcUnion(pcDome(), circleP(-0.22f, -0.2f, 0.13f), circleP(0.22f, -0.2f, 0.13f))
        shape(body, 0xFFAEE6A6.toInt())
        tint(ovalP(-0.26f, 0.18f, 0.26f, 0.4f), 0xFFE4F7D8.toInt())
        tint(circleP(-0.22f, -0.2f, 0.075f), PC_W)
        tint(circleP(0.22f, -0.2f, 0.075f), PC_W)
        eyes(0f, -0.2f, 0.22f, 0.04f)
        smile(0f, 0.06f, 0.09f)
        tint(ovalP(-0.32f, 0.02f, -0.2f, 0.09f), PC_BLUSH)
        tint(ovalP(0.2f, 0.02f, 0.32f, 0.09f), PC_BLUSH)
        tint(ovalP(-0.36f, -0.06f, -0.24f, 0.02f), 0x88FFFFFF.toInt())
    }

    val BLOB_COW: StickerArt = {
        pcSquishShade()
        shape(ovalP(-0.24f, -0.36f, -0.12f, -0.18f), CREAM)
        shape(ovalP(0.12f, -0.36f, 0.24f, -0.18f), CREAM)
        pcRot(-25f, -0.36f, -0.08f) { shape(ovalP(-0.5f, -0.13f, -0.28f, -0.03f), PC_W) }
        pcRot(25f, 0.36f, -0.08f) { shape(ovalP(0.28f, -0.13f, 0.5f, -0.03f), PC_W) }
        val body = pcDome()
        shape(body, PC_W)
        pcClip(body) {
            tint(ovalP(-0.5f, -0.12f, -0.2f, 0.14f), 0xFF8D6E63.toInt())
            tint(ovalP(0.14f, -0.3f, 0.4f, -0.08f), 0xFF8D6E63.toInt())
            tint(ovalP(0.28f, 0.2f, 0.56f, 0.46f), 0xFF8D6E63.toInt())
        }
        stroke(body)
        shape(ovalP(-0.2f, 0.12f, 0.2f, 0.36f), 0xFFFFC1CC.toInt())
        solid(ovalP(-0.11f, 0.2f, -0.06f, 0.26f), 0xFFD9798C.toInt())
        solid(ovalP(0.06f, 0.2f, 0.11f, 0.26f), 0xFFD9798C.toInt())
        eyes(0f, 0.02f, 0.15f, 0.034f)
        tint(ovalP(-0.36f, 0.06f, -0.24f, 0.13f), PC_BLUSH)
        tint(ovalP(0.24f, 0.06f, 0.36f, 0.13f), PC_BLUSH)
    }

    val BLOB_AXOLOTL: StickerArt = {
        pcSquishShade()
        val gill = 0xFFFF8FB8.toInt()
        for (sx in floatArrayOf(-1f, 1f)) {
            for (j in 0..2) {
                val v0 = -0.1f + j * 0.1f
                pcTube(path { m(0.3f * sx, v0); q(0.42f * sx, v0 - 0.08f, 0.48f * sx, v0 - 0.2f + j * 0.06f) }, r(0.05f), gill)
            }
        }
        shape(pcDome(), 0xFFFFC9DF.toInt())
        pcSquishShine()
        tint(circleP(0.2f, -0.14f, 0.025f), 0x66FF6FA5)
        tint(circleP(0.27f, -0.06f, 0.018f), 0x66FF6FA5)
        eyes(0f, 0.06f, 0.17f, 0.034f)
        smile(0f, 0.12f, 0.08f)
        tint(ovalP(-0.32f, 0.12f, -0.2f, 0.19f), PC_BLUSH)
        tint(ovalP(0.2f, 0.12f, 0.32f, 0.19f), PC_BLUSH)
    }

    val BLOB_BUNNY: StickerArt = {
        pcSquishShade()
        val body = pcUnion(pcDome(), rectP(-0.24f, -0.5f, -0.08f, -0.08f, 0.08f), rectP(0.08f, -0.5f, 0.24f, -0.08f, 0.08f))
        shape(body, 0xFFFFFBF7.toInt())
        tint(rectP(-0.2f, -0.44f, -0.12f, -0.2f, 0.04f), PINK_SOFT)
        tint(rectP(0.12f, -0.44f, 0.2f, -0.2f, 0.04f), PINK_SOFT)
        pcSquishShine()
        eyes(0f, 0.08f, 0.13f, 0.034f)
        solid(poly(-0.03f, 0.12f, 0.03f, 0.12f, 0f, 0.15f), PINK_NOSE)
        pcCatMouth(0f, 0.15f, 0.9f)
        tint(ovalP(-0.3f, 0.13f, -0.18f, 0.2f), PC_BLUSH)
        tint(ovalP(0.18f, 0.13f, 0.3f, 0.2f), PC_BLUSH)
    }

    val PUFF_STAR: StickerArt = {
        shape(starPath(0f, 0.04f, 0.46f, 0.26f), 0xFFFFE9A8.toInt())
        eyes(0f, 0.08f, 0.08f, 0.028f)
        smile(0f, 0.13f, 0.035f)
        pcCheeks(0f, 0.14f, 0.14f, 0.03f)
    }

    // ---------- Café ----------

    val COFFEE_CUP: StickerArt = {
        val steam = if (colorful) (ink.line and 0x00FFFFFF) or 0x66000000 else ink.line
        for (i in 0..1) {
            val u = -0.12f + i * 0.14f
            stroke(path { m(u, -0.2f); q(u - 0.05f, -0.28f, u, -0.35f); q(u + 0.05f, -0.42f, u, -0.48f) }, lineW * 0.8f, steam)
        }
        shape(ovalP(-0.48f, 0.3f, 0.4f, 0.46f), CUP)
        pcTube(path { addArc(RectF(x(0.12f), y(-0.08f), x(0.44f), y(0.22f)), -100f, 200f) }, r(0.055f), CUP)
        val cup = path { m(-0.38f, -0.12f); l(0.3f, -0.12f); cu(0.3f, 0.26f, 0.16f, 0.38f, -0.04f, 0.38f); cu(-0.24f, 0.38f, -0.38f, 0.26f, -0.38f, -0.12f); close() }
        shape(cup, CUP)
        pcClip(cup) { tint(rectP(-0.5f, 0.02f, 0.5f, 0.08f), ink.secondary) }
        stroke(cup)
        shape(ovalP(-0.38f, -0.18f, 0.3f, -0.06f), CUP)
        tint(ovalP(-0.33f, -0.16f, 0.25f, -0.08f), LATTE)
        tint(heartPath(-0.04f, -0.12f, 0.12f), FOAM)
    }

    val LATTE_ART: StickerArt = {
        shape(circleP(0f, 0f, 0.47f), CUP)
        shape(rectP(0.3f, -0.07f, 0.49f, 0.07f, 0.06f), CUP)
        shape(circleP(0f, 0f, 0.36f), CUP)
        shape(circleP(0f, 0f, 0.29f), LATTE)
        tint(heartPath(0f, 0.0f, 0.5f), FOAM)
        tint(heartPath(0f, 0.02f, 0.32f), LATTE)
        tint(heartPath(0f, 0.035f, 0.18f), FOAM)
        tint(ovalP(-0.3f, -0.36f, -0.1f, -0.3f), 0x66FFFFFF)
    }

    val CROISSANT: StickerArt = {
        val segs = listOf(-62f to 0.62f, 62f to 0.62f, -32f to 0.82f, 32f to 0.82f, 0f to 1f)
        for ((a, k) in segs) {
            val rad = a * PI.toFloat() / 180f
            val u = sin(rad) * 0.32f; val v = 0.28f - cos(rad) * 0.32f
            pcRot(a, u, v) {
                shape(ovalP(u - 0.13f * k, v - 0.19f * k, u + 0.13f * k, v + 0.19f * k), C_CROISSANT)
                tint(ovalP(u - 0.07f * k, v - 0.15f * k, u + 0.0f, v - 0.04f * k), 0x55FFFFFF)
            }
        }
        pcCheeks(0f, 0.04f, 0.1f, 0.03f)
        eyes(0f, -0.04f, 0.06f, 0.026f)
    }

    val BEANS: StickerArt = {
        for ((u, v, a) in listOf(Triple(-0.17f, 0.06f, -25f), Triple(0.18f, -0.06f, 20f))) {
            pcRot(a, u, v) {
                shape(ovalP(u - 0.17f, v - 0.26f, u + 0.17f, v + 0.26f), COFFEE)
                stroke(path { m(u, v - 0.21f); cu(u - 0.09f, v - 0.05f, u + 0.09f, v + 0.05f, u, v + 0.21f) }, lineW * 0.9f, if (colorful) 0xFF3E2216.toInt() else ink.line)
                tint(ovalP(u - 0.11f, v - 0.17f, u - 0.05f, v - 0.05f), 0x44FFFFFF)
            }
        }
    }

    val TAKEAWAY: StickerArt = {
        val cup = poly(-0.26f, -0.26f, 0.26f, -0.26f, 0.2f, 0.47f, -0.2f, 0.47f)
        shape(cup, CUP)
        shape(poly(-0.25f, -0.05f, 0.25f, -0.05f, 0.22f, 0.26f, -0.22f, 0.26f), 0xFFC98E5A.toInt())
        tint(heartPath(0f, 0.11f, 0.22f), CUP)
        shape(rectP(-0.32f, -0.36f, 0.32f, -0.25f, 0.04f), 0xFF8A5A3B.toInt())
        shape(rectP(-0.24f, -0.44f, 0.24f, -0.35f, 0.04f), 0xFF8A5A3B.toInt())
        tint(rectP(-0.2f, -0.2f, -0.14f, 0.4f, 0.03f), 0x66FFFFFF)
    }

    // ---------- Sakura ----------

    val BLOSSOM: StickerArt = { pcBlossom(0f, 0f, 1f) }

    val BRANCH: StickerArt = {
        pcTube(path { m(-0.5f, 0.32f); q(-0.1f, 0.12f, 0.15f, -0.05f); q(0.3f, -0.15f, 0.48f, -0.34f) }, r(0.035f), BARK)
        pcTube(path { m(0.0f, 0.06f); q(0.05f, 0.2f, 0.2f, 0.28f) }, r(0.022f), BARK)
        pcBlossom(-0.14f, 0.12f, 0.5f, 10f)
        pcBlossom(0.24f, -0.16f, 0.42f, 30f)
        pcBlossom(0.22f, 0.27f, 0.32f)
        shape(ovalP(0.4f, -0.44f, 0.48f, -0.34f), SAKURA_DEEP)
        shape(ovalP(-0.42f, 0.16f, -0.34f, 0.26f), SAKURA_DEEP)
    }

    val PAPER_LANTERN: StickerArt = {
        stroke(path { m(0f, -0.5f); l(0f, -0.38f) }, lineW * 0.8f)
        for (i in -1..1) stroke(path { m(i * 0.04f, 0.36f); l(i * 0.05f, 0.5f) }, lineW * 0.9f, if (colorful) GOLD else ink.line)
        shape(rectP(-0.14f, -0.4f, 0.14f, -0.3f, 0.02f), 0xFF3B2B2B.toInt())
        shape(rectP(-0.14f, 0.28f, 0.14f, 0.38f, 0.02f), 0xFF3B2B2B.toInt())
        val body = ovalP(-0.33f, -0.34f, 0.33f, 0.32f)
        shape(body, LANTERN)
        pcClip(body) {
            tint(circleP(0f, 0f, 0.22f), 0x55FFD54F)
            for (i in -2..2) stroke(path { addArc(RectF(x(-0.33f), y(i * 0.11f - 0.2f), x(0.33f), y(i * 0.11f + 0.06f)), 20f, 140f) }, lineW * 0.5f, if (colorful) 0x55000000 else ink.line)
        }
        stroke(body)
        tint(ovalP(-0.24f, -0.22f, -0.14f, 0.02f), 0x55FFFFFF)
    }

    val PETALS: StickerArt = {
        pcRot(-30f, -0.15f, 0.1f) { shape(pcPetal(-0.15f, 0.3f, 0.75f), SAKURA) }
        pcRot(40f, 0.22f, -0.12f) { shape(pcPetal(0.22f, 0.12f, 0.55f), SAKURA) }
        tint(pcSpark(0.3f, 0.3f, 0.08f), SAKURA_DEEP)
    }

    val PETAL_ONE: StickerArt = { shape(pcPetal(0f, 0.36f, 1.4f), SAKURA) }

    val FAN: StickerArt = {
        shape(rectP(-0.03f, 0.18f, 0.03f, 0.48f, 0.02f), BARK)
        val fan = path {
            moveTo(x(0f), y(0.32f))
            arcTo(RectF(x(-0.5f), y(-0.24f), x(0.5f), y(0.88f)), 205f, 130f)
            close()
        }
        shape(fan, PC_W)
        pcClip(fan) {
            tint(ovalP(-0.6f, 0.0f, 0.6f, 0.9f), SAKURA)
            for (i in 0..8) {
                val a = (205.0 + i * 130.0 / 8) * PI / 180
                stroke(path { m(0f, 0.32f); l((cos(a) * 0.56f).toFloat(), 0.32f + (sin(a) * 0.56f).toFloat()) }, lineW * 0.45f, if (colorful) 0x55C2456E else ink.line)
            }
        }
        stroke(fan)
        pcBlossom(0.12f, -0.08f, 0.3f)
        shape(circleP(0f, 0.32f, 0.04f), GOLD)
    }

    // ---------- Kitty ----------

    val CAT_FACE: StickerArt = { pcCatHead(0f, 0.04f, 1f, GINGER_CAT, stripes = true) }

    val CAT_SIT: StickerArt = {
        pcTube(path { m(0.18f, 0.42f); q(0.5f, 0.44f, 0.42f, 0.08f) }, r(0.07f), GREY_CAT)
        shape(ovalP(-0.24f, -0.04f, 0.24f, 0.47f), GREY_CAT)
        tint(ovalP(-0.12f, 0.08f, 0.12f, 0.38f), PC_W)
        shape(ovalP(-0.18f, 0.38f, -0.04f, 0.48f), PC_W)
        shape(ovalP(0.04f, 0.38f, 0.18f, 0.48f), PC_W)
        pcCatHead(0f, -0.18f, 0.62f, GREY_CAT, stripes = false)
    }

    val PAW: StickerArt = {
        val pad = path { m(0f, 0.04f); cu(0.22f, 0.02f, 0.32f, 0.3f, 0.16f, 0.36f); q(0f, 0.3f, -0.16f, 0.36f); cu(-0.32f, 0.3f, -0.22f, 0.02f, 0f, 0.04f); close() }
        shape(pad, ink.accent)
        shape(ovalP(-0.4f, -0.16f, -0.24f, 0.06f), ink.accent)
        shape(ovalP(-0.22f, -0.38f, -0.06f, -0.14f), ink.accent)
        shape(ovalP(0.06f, -0.38f, 0.22f, -0.14f), ink.accent)
        shape(ovalP(0.24f, -0.16f, 0.4f, 0.06f), ink.accent)
        tint(ovalP(-0.12f, 0.1f, -0.03f, 0.18f), 0x77FFFFFF)
    }

    val YARN: StickerArt = {
        stroke(path { m(0.2f, 0.32f); cu(0.4f, 0.5f, 0.5f, 0.3f, 0.36f, 0.24f); q(0.26f, 0.2f, 0.36f, 0.44f) }, lineW * 0.9f, if (colorful) 0xFF8E77D9.toInt() else ink.line)
        val ball = circleP(-0.04f, 0.0f, 0.38f)
        shape(ball, C_YARN)
        pcClip(ball) {
            val ln = if (colorful) 0xFF8E77D9.toInt() else ink.line
            for (i in 0..3) stroke(path { addArc(RectF(x(-0.7f + i * 0.12f), y(-0.5f), x(0.2f + i * 0.12f), y(0.5f)), -60f, 120f) }, lineW * 0.6f, ln)
            for (i in 0..2) stroke(path { addArc(RectF(x(-0.5f), y(-0.62f + i * 0.14f), x(0.4f), y(0.18f + i * 0.14f)), 20f, 140f) }, lineW * 0.6f, ln)
        }
        stroke(ball)
        tint(ovalP(-0.3f, -0.26f, -0.16f, -0.14f), 0x66FFFFFF)
    }

    val FISH: StickerArt = {
        val body = pcUnion(path { m(-0.42f, 0f); q(-0.1f, -0.36f, 0.22f, 0f); q(-0.1f, 0.36f, -0.42f, 0f); close() }, poly(0.16f, 0f, 0.46f, -0.2f, 0.4f, 0f, 0.46f, 0.2f))
        shape(body, FISH_BLUE)
        for (i in 0..1) stroke(path { addArc(RectF(x(-0.1f + i * 0.1f), y(-0.1f), x(0.06f + i * 0.1f), y(0.1f)), -60f, 120f) }, lineW * 0.5f)
        eyes(-0.24f, -0.03f, 0f, 0.035f)
        tint(circleP(-0.2f, 0.06f, 0.03f), PC_BLUSH)
        stroke(circleP(-0.36f, -0.32f, 0.04f), lineW * 0.6f)
        stroke(circleP(-0.26f, -0.42f, 0.03f), lineW * 0.6f)
    }

    // ---------- Autumn ----------

    val MAPLE: StickerArt = {
        stroke(path { m(0f, 0.2f); q(0.02f, 0.36f, -0.04f, 0.48f) }, lineW * 1.3f, if (colorful) WOOD else ink.line)
        shape(pcMapleP(), LEAF_ORANGE)
        val vein = if (colorful) 0x66FFFFFF else ink.line
        stroke(path { m(0f, 0.2f); l(0f, -0.38f); m(0f, 0.15f); l(0.36f, -0.2f); m(0f, 0.15f); l(-0.36f, -0.2f); m(0f, 0.18f); l(0.26f, 0.12f); m(0f, 0.18f); l(-0.26f, 0.12f) }, lineW * 0.5f, vein)
    }

    val MAPLE_RED: StickerArt = {
        stroke(path { m(0f, 0.2f); q(0.02f, 0.36f, -0.04f, 0.48f) }, lineW * 1.3f, if (colorful) WOOD else ink.line)
        shape(pcMapleP(), LEAF_RED)
        stroke(path { m(0f, 0.2f); l(0f, -0.38f); m(0f, 0.15f); l(0.36f, -0.2f); m(0f, 0.15f); l(-0.36f, -0.2f) }, lineW * 0.5f, if (colorful) 0x66FFFFFF else ink.line)
    }

    val LEAF: StickerArt = {
        pcRot(-30f) {
            stroke(path { m(0f, 0.3f); l(0f, 0.48f) }, lineW * 1.2f, if (colorful) WOOD else ink.line)
            shape(path { m(0f, 0.32f); cu(-0.34f, 0.2f, -0.3f, -0.26f, 0f, -0.46f); cu(0.3f, -0.26f, 0.34f, 0.2f, 0f, 0.32f); close() }, LEAF_YELLOW)
            val vein = if (colorful) 0x88C9871A.toInt() else ink.line
            stroke(path { m(0f, 0.3f); l(0f, -0.38f); for (i in 0..2) { val v = 0.14f - i * 0.15f; m(0f, v); l(-0.15f, v - 0.1f); m(0f, v); l(0.15f, v - 0.1f) } }, lineW * 0.5f, vein)
        }
    }

    val ACORN: StickerArt = {
        shape(path { m(-0.26f, -0.05f); cu(-0.28f, 0.3f, -0.08f, 0.44f, 0f, 0.48f); cu(0.08f, 0.44f, 0.28f, 0.3f, 0.26f, -0.05f); close() }, C_ACORN)
        tint(ovalP(-0.18f, 0.02f, -0.1f, 0.2f), 0x55FFFFFF)
        stroke(path { m(0f, -0.3f); q(0.04f, -0.42f, 0.12f, -0.46f) }, lineW * 1.2f, if (colorful) ACORN_CAP else ink.line)
        val cap = path { m(-0.34f, -0.02f); cu(-0.36f, -0.38f, 0.36f, -0.38f, 0.34f, -0.02f); q(0f, 0.06f, -0.34f, -0.02f); close() }
        shape(cap, ACORN_CAP)
        pcClip(cap) {
            val ln = if (colorful) 0x44000000 else ink.line
            for (i in -4..4) {
                stroke(path { m(i * 0.09f, -0.4f); l(i * 0.09f + 0.2f, 0.05f) }, lineW * 0.45f, ln)
                stroke(path { m(i * 0.09f, -0.4f); l(i * 0.09f - 0.2f, 0.05f) }, lineW * 0.45f, ln)
            }
        }
        stroke(cap)
        eyes(0f, 0.17f, 0.08f, 0.028f)
        smile(0f, 0.23f, 0.035f)
    }

    val MUSHROOM: StickerArt = {
        shape(path { m(-0.14f, 0.0f); q(-0.18f, 0.36f, -0.2f, 0.44f); q(0f, 0.5f, 0.2f, 0.44f); q(0.18f, 0.36f, 0.14f, 0.0f); close() }, CREAM)
        val cap = path { m(-0.46f, 0.06f); cu(-0.48f, -0.5f, 0.48f, -0.5f, 0.46f, 0.06f); q(0f, 0.14f, -0.46f, 0.06f); close() }
        shape(cap, MUSH_RED)
        for ((u, v, rr) in listOf(Triple(-0.22f, -0.12f, 0.06f), Triple(0.04f, -0.24f, 0.07f), Triple(0.26f, -0.08f, 0.05f), Triple(-0.02f, -0.02f, 0.04f))) {
            shape(circleP(u, v, rr), PC_W)
        }
        eyes(0f, 0.22f, 0.07f, 0.026f)
        smile(0f, 0.28f, 0.03f)
        pcCheeks(0f, 0.28f, 0.11f, 0.025f)
    }

    val PUMPKIN: StickerArt = {
        stroke(path { m(0.04f, -0.24f); q(0.0f, -0.38f, 0.08f, -0.46f) }, lineW * 2f, if (colorful) 0xFF6B8E3D.toInt() else ink.line)
        stroke(path { m(0.06f, -0.3f); q(0.24f, -0.44f, 0.3f, -0.32f); q(0.24f, -0.24f, 0.18f, -0.34f) }, lineW * 0.8f, if (colorful) 0xFF6B8E3D.toInt() else ink.line)
        shape(ovalP(-0.48f, -0.24f, -0.02f, 0.42f), C_PUMPKIN)
        shape(ovalP(0.02f, -0.24f, 0.48f, 0.42f), C_PUMPKIN)
        shape(ovalP(-0.24f, -0.28f, 0.24f, 0.44f), 0xFFF79E3E.toInt())
        tint(ovalP(-0.15f, -0.18f, -0.06f, 0.05f), 0x55FFFFFF)
        eyes(0f, 0.08f, 0.09f, 0.03f)
        smile(0f, 0.15f, 0.045f)
    }

    // ---------- Camping ----------

    val TENT_ART: StickerArt = {
        stroke(path { m(0f, -0.38f); l(-0.06f, -0.48f); m(0f, -0.38f); l(0.07f, -0.47f) }, lineW)
        stroke(path { m(-0.46f, 0.4f); l(0.48f, 0.4f) }, lineW)
        shape(poly(-0.44f, 0.4f, 0f, -0.38f, 0.44f, 0.4f), TENT)
        tint(poly(0f, -0.38f, 0.44f, 0.4f, 0.2f, 0.4f), 0x33000000)
        shape(poly(-0.14f, 0.4f, 0f, 0.02f, 0.14f, 0.4f), 0xFF5A3A2A.toInt())
        shape(poly(0f, 0.02f, 0.14f, 0.4f, 0.26f, 0.4f), 0xFFFFC08A.toInt())
        for (i in 0..2) shape(poly(-0.3f + i * 0.1f, 0.12f - i * 0.18f + 0.02f, -0.24f + i * 0.1f, 0.04f - i * 0.18f, -0.2f + i * 0.1f, 0.16f - i * 0.18f), if (i % 2 == 0) GOLD else 0xFF74C0FC.toInt())
    }

    val CAMPFIRE: StickerArt = {
        tint(circleP(0f, 0.1f, 0.4f), 0x33FFB74D)
        shape(pcFlame(0f, 0.32f, 0.66f, 0.27f), FLAME_O)
        shape(pcFlame(-0.13f, 0.32f, 0.36f, 0.12f), FLAME_Y)
        shape(pcFlame(0.13f, 0.32f, 0.3f, 0.11f), FLAME_Y)
        tint(pcFlame(0f, 0.3f, 0.32f, 0.12f), FLAME_Y)
        pcRot(16f, 0f, 0.36f) { shape(rectP(-0.4f, 0.3f, 0.4f, 0.42f, 0.06f), WOOD) }
        pcRot(-16f, 0f, 0.36f) { shape(rectP(-0.4f, 0.3f, 0.4f, 0.42f, 0.06f), WOOD_DARK) }
        for ((u, v) in listOf(-0.3f to -0.12f, 0.28f to -0.22f, 0.12f to -0.42f)) solid(circleP(u, v, 0.022f), FLAME_Y)
    }

    val MOUNTAINS: StickerArt = {
        shape(poly(-0.1f, 0.4f, 0.2f, -0.28f, 0.5f, 0.4f), SLATE_LIGHT)
        shape(poly(0.2f, -0.28f, 0.29f, -0.08f, 0.24f, -0.11f, 0.19f, -0.05f, 0.13f, -0.12f), PC_W)
        shape(poly(-0.5f, 0.4f, -0.12f, -0.42f, 0.3f, 0.4f), SLATE)
        shape(poly(-0.12f, -0.42f, 0.034f, -0.12f, -0.02f, -0.16f, -0.07f, -0.1f, -0.13f, -0.17f, -0.19f, -0.1f, -0.259f, -0.12f), PC_W)
        shape(rectP(-0.5f, 0.36f, 0.5f, 0.46f, 0.04f), PINE_LIGHT)
    }

    val PINE_TREE: StickerArt = {
        shape(rectP(-0.06f, 0.3f, 0.06f, 0.48f, 0.02f), WOOD)
        shape(poly(0f, -0.08f, 0.38f, 0.34f, -0.38f, 0.34f), PINE)
        shape(poly(0f, -0.3f, 0.3f, 0.08f, -0.3f, 0.08f), PINE)
        shape(poly(0f, -0.49f, 0.21f, -0.16f, -0.21f, -0.16f), PINE_LIGHT)
        tint(poly(0f, -0.08f, -0.38f, 0.34f, -0.2f, 0.34f), 0x22FFFFFF)
    }

    val MARSHMALLOW: StickerArt = {
        pcTube(path { m(-0.46f, 0.46f); l(0.16f, -0.16f) }, r(0.035f), 0xFFC9925E.toInt())
        pcRot(-45f, 0.24f, -0.24f) {
            shape(rectP(0.08f, -0.38f, 0.4f, -0.1f, 0.08f), PC_W)
            tint(rectP(0.08f, -0.38f, 0.4f, -0.27f, 0.08f), 0xFFE9B872.toInt())
        }
        eyes(0.24f, -0.22f, 0.06f, 0.025f)
        smile(0.24f, -0.18f, 0.03f)
        pcCheeks(0.24f, -0.18f, 0.1f, 0.025f)
    }

    // ---------- Rainbow ----------

    val RAINBOW_ART: StickerArt = {
        pcRainbowArcs(0f, 0.24f, 0.46f, 0.055f)
        shape(pcCloudP(-0.3f, 0.26f, 0.5f), PC_W)
        shape(pcCloudP(0.3f, 0.26f, 0.5f), PC_W)
        eyes(0.3f, 0.29f, 0.06f, 0.022f)
        pcCheeks(0.3f, 0.33f, 0.1f, 0.025f)
    }

    val CLOUD: StickerArt = {
        shape(pcCloudP(0f, 0f, 1.2f), PC_W)
        tint(ovalP(-0.24f, -0.16f, -0.08f, -0.06f), 0xCCFFFFFF.toInt())
        eyes(0f, 0.08f, 0.1f, 0.033f)
        smile(0f, 0.14f, 0.04f)
        pcCheeks(0f, 0.14f, 0.18f, 0.035f)
    }

    val SUNSHINE: StickerArt = {
        for (i in 0 until 10) pcRot(i * 36f) { shape(rectP(-0.05f, -0.49f, 0.05f, -0.33f, 0.05f), SUN_RAY) }
        shape(circleP(0f, 0f, 0.3f), SUN)
        pcCuteFace(0f, 0f, 1.1f)
    }

    val RAINBOW_HEART: StickerArt = {
        val h = heartPath(0f, 0.04f, 1.08f)
        shape(h, RAINBOW[0])
        pcClip(h) {
            RAINBOW.forEachIndexed { i, col -> tint(rectP(-0.5f, -0.4f + i * 0.14f, 0.5f, -0.26f + i * 0.14f), col) }
        }
        stroke(h)
        tint(ovalP(-0.3f, -0.2f, -0.16f, -0.08f), 0xAAFFFFFF.toInt())
    }

    // ---------- Frame helpers ----------

    fun pp(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        this.color = color; this.style = style; strokeWidth = width; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    fun fade(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    fun rr(card: RectF, r: Float) = Path().apply { addRoundRect(card, r, r, Path.Direction.CW) }

    private fun inset(card: RectF, i: Float) = RectF(card.left + i, card.top + i, card.right - i, card.bottom - i)

    private fun shadow(c: Canvas, body: Path, dx: Float, dy: Float, color: Int) =
        c.drawPath(Path(body).apply { offset(dx, dy) }, pp(color))

    private fun dash(color: Int, w: Float, on: Float, off: Float) = pp(color, Paint.Style.STROKE, w).apply { pathEffect = DashPathEffect(floatArrayOf(on, off), 0f) }

    private fun gleam(c: Canvas, card: RectF, r: Float, d: Float) {
        val inset = 5f * d
        val rad = max(r, 8f * d)
        c.drawArc(RectF(card.left + inset, card.top + inset, card.left + inset + rad * 1.4f, card.top + inset + rad * 1.4f), 190f, 50f, false, pp(0xCCFFFFFF.toInt(), Paint.Style.STROKE, 2.6f * d))
    }

    // ---------- Frames ----------

    /** Red sweater knit with a fair-isle band on top and ribbing at the bottom. */
    val knitFrame = FramePainter { c, t, card, r, d, _ ->
        val p = t.palette
        val body = rr(card, r)
        shadow(c, body, 0f, 3f * d, fade(p.line, 0.35f))
        c.drawPath(body, pp(p.background))
        c.withSave {
            clipPath(body)
            val k = 4f * d
            val knit = Path()
            var y = card.top
            while (y < card.bottom) {
                var x = card.left
                while (x < card.right) { knit.moveTo(x, y); knit.lineTo(x + k / 2, y + k * 0.8f); knit.lineTo(x + k, y); x += k }
                y += k
            }
            drawPath(knit, pp(fade(p.line, 0.16f), Paint.Style.STROKE, 0.9f * d))
            val bandH = min(13f * d, card.height() * 0.28f)
            drawRect(card.left, card.top, card.right, card.top + bandH, pp(p.secondary))
            val zz = Path()
            val step = 5f * d
            var x = card.left
            var up = false
            zz.moveTo(x, card.top + bandH * 0.68f)
            while (x < card.right) { x += step; up = !up; zz.lineTo(x, card.top + bandH * (if (up) 0.32f else 0.68f)) }
            drawPath(zz, pp(p.text, Paint.Style.STROKE, 1.4f * d))
            var dx = card.left + step
            while (dx < card.right) { drawCircle(dx, card.top + bandH * 0.86f, 0.8f * d, pp(p.accent)); dx += step * 2 }
            drawLine(card.left, card.top + bandH, card.right, card.top + bandH, pp(p.text, Paint.Style.STROKE, 1.2f * d))
            val ribH = min(6f * d, card.height() * 0.12f)
            drawRect(card.left, card.bottom - ribH, card.right, card.bottom, pp(p.secondary))
            var rx = card.left
            val rib = pp(fade(p.line, 0.35f), Paint.Style.STROKE, 0.8f * d)
            while (rx < card.right) { drawLine(rx, card.bottom - ribH, rx, card.bottom, rib); rx += 3f * d }
            drawLine(card.left, card.bottom - ribH, card.right, card.bottom - ribH, pp(p.text, Paint.Style.STROKE, 1f * d))
        }
        c.drawPath(body, pp(p.line, Paint.Style.STROKE, 2f * d))
    }

    /** Cream card with two fabric patches and a dashed stitch all around. */
    val patchFrame = FramePainter { c, t, card, r, d, _ ->
        val p = t.palette
        val body = rr(card, r)
        shadow(c, body, 0f, 2.5f * d, fade(p.line, 0.25f))
        c.drawPath(body, pp(p.background))
        val ps = min(20f * d, min(card.width(), card.height()) * 0.32f)
        val stitch = dash(fade(p.line, 0.75f), 1.3f * d, 3.5f * d, 2.5f * d)
        c.withSave {
            clipPath(body)
            drawRect(card.left, card.top, card.left + ps, card.top + ps, pp(p.accent))
            val dot = pp(fade(0xFFFFFFFF.toInt(), 0.75f))
            var yy = card.top + 3f * d
            while (yy < card.top + ps) { var xx = card.left + 3f * d; while (xx < card.left + ps) { drawCircle(xx, yy, 1f * d, dot); xx += 5f * d }; yy += 5f * d }
            drawPath(Path().apply { moveTo(card.left + ps, card.top); lineTo(card.left + ps, card.top + ps); lineTo(card.left, card.top + ps) }, stitch)
            drawRect(card.right - ps, card.bottom - ps, card.right, card.bottom, pp(p.secondary))
            val gs = pp(fade(0xFFFFFFFF.toInt(), 0.4f))
            var gx = card.right - ps
            while (gx < card.right) { drawRect(gx, card.bottom - ps, gx + 2.5f * d, card.bottom, gs); gx += 5f * d }
            var gy = card.bottom - ps
            while (gy < card.bottom) { drawRect(card.right - ps, gy, card.right, gy + 2.5f * d, gs); gy += 5f * d }
            drawPath(Path().apply { moveTo(card.right - ps, card.bottom); lineTo(card.right - ps, card.bottom - ps); lineTo(card.right, card.bottom - ps) }, stitch)
        }
        val i = 4.5f * d
        c.drawRoundRect(inset(card, i), max(0f, r - i), max(0f, r - i), stitch)
        c.drawPath(body, pp(p.line, Paint.Style.STROKE, 2f * d))
    }

    /** A row of piped frosting dollops on top, sprinkles and faint polka dots. */
    val frostingFrame = FramePainter { c, t, card, r, d, seed ->
        val p = t.palette
        val body = rr(card, r)
        shadow(c, body, 0f, 3f * d, fade(p.line, 0.22f))
        c.drawPath(body, pp(p.background))
        c.withSave {
            clipPath(body)
            val dot = pp(fade(p.secondary, 0.5f))
            val g = 14f * d
            var yy = card.top + g * 0.8f
            var row = 0
            while (yy < card.bottom) {
                var xx = card.left + g * 0.5f + (if (row % 2 == 1) g / 2 else 0f)
                while (xx < card.right) { drawCircle(xx, yy, 1.6f * d, dot); xx += g }
                yy += g; row++
            }
        }
        val rnd = Random(seed + 5)
        val rad = min(8.5f * d, card.height() * 0.16f)
        val n = max(3, (card.width() / (rad * 1.5f)).toInt())
        val step = card.width() / n
        val cyy = card.top + rad * 0.45f
        val frost = Path(body)
        frost.op(Path().apply { addRect(card.left - 1f, card.top - 1f, card.right + 1f, cyy, Path.Direction.CW) }, Path.Op.INTERSECT)
        for (i in 0 until n) frost.op(Path().apply { addCircle(card.left + step * (i + 0.5f), cyy, rad * (0.92f + rnd.nextFloat() * 0.16f), Path.Direction.CW) }, Path.Op.UNION)
        frost.op(Path().apply { addRect(card.left, card.top - rad * 2f, card.right, card.bottom, Path.Direction.CW) }, Path.Op.INTERSECT)
        c.drawPath(frost, pp(p.accent))
        val swirl = pp(fade(0xFFFFFFFF.toInt(), 0.65f), Paint.Style.STROKE, 1.2f * d)
        for (i in 0 until n) {
            val cx = card.left + step * (i + 0.5f)
            c.drawArc(RectF(cx - rad * 0.55f, cyy - rad * 0.55f, cx + rad * 0.55f, cyy + rad * 0.55f), 200f, 150f, false, swirl)
        }
        val colors = intArrayOf(p.secondary, 0xFFFFFFFF.toInt(), 0xFFFFE066.toInt(), 0xFF74C0FC.toInt())
        val sp = pp(0, Paint.Style.STROKE, 1.8f * d)
        repeat(n * 2) { i ->
            sp.color = colors[i % colors.size]
            val x = card.left + rad + rnd.nextFloat() * (card.width() - rad * 2)
            val y = cyy - rad * 0.4f + rnd.nextFloat() * rad * 0.8f
            val a = rnd.nextFloat() * PI.toFloat()
            val l = 2f * d
            c.drawLine(x - cos(a) * l, y - sin(a) * l, x + cos(a) * l, y + sin(a) * l, sp)
        }
        c.drawPath(frost, pp(p.line, Paint.Style.STROKE, 1.8f * d))
        c.drawPath(body, pp(p.line, Paint.Style.STROKE, 2.2f * d))
    }

    /** Glossy pink candy card: gradient, a shiny top band, a white rim and a string of pearls. */
    val glossyFrame = FramePainter { c, t, card, r, d, _ ->
        val p = t.palette
        val body = rr(card, r)
        shadow(c, body, 0f, 2.5f * d, fade(p.line, 0.22f))
        c.drawPath(body, pp(0).apply { shader = LinearGradient(0f, card.top, 0f, card.bottom, p.background, p.secondary, Shader.TileMode.CLAMP) })
        val gl = RectF(card.left + 3f * d, card.top + 3f * d, card.right - 3f * d, card.top + max(8f * d, card.height() * 0.42f))
        val gr = max(0f, r - 3f * d)
        c.drawRoundRect(gl, gr, gr, pp(0).apply { shader = LinearGradient(0f, gl.top, 0f, gl.bottom, 0x77FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP) })
        val i = 1.6f * d
        c.drawRoundRect(inset(card, i), max(0f, r - i), max(0f, r - i), pp(0xE6FFFFFF.toInt(), Paint.Style.STROKE, 1.8f * d))
        c.drawPath(body, pp(p.accent, Paint.Style.STROKE, 1.4f * d))
        val pr = 1.7f * d
        val pearl = pp(0xFFFFFAFC.toInt())
        val rim = pp(fade(p.line, 0.5f), Paint.Style.STROKE, 0.6f * d)
        var x = card.left + r + pr
        while (x < card.right - r - pr) {
            c.drawCircle(x, card.bottom, pr, pearl); c.drawCircle(x, card.bottom, pr, rim)
            x += pr * 2.6f
        }
        gleam(c, card, r, d)
    }

    /** Puffy pillow: bulging sides, soft inner shading and a highlight. */
    val pillowFrame = FramePainter { c, t, card, r, d, _ ->
        val p = t.palette
        val b = 2.5f * d
        val cx = card.centerX(); val cy = card.centerY()
        val l = card.left; val tp = card.top; val rt = card.right; val bt = card.bottom
        val body = Path().apply {
            moveTo(l + r, tp); quadTo(cx, tp - b, rt - r, tp); quadTo(rt, tp, rt, tp + r)
            quadTo(rt + b, cy, rt, bt - r); quadTo(rt, bt, rt - r, bt)
            quadTo(cx, bt + b, l + r, bt); quadTo(l, bt, l, bt - r)
            quadTo(l - b, cy, l, tp + r); quadTo(l, tp, l + r, tp); close()
        }
        shadow(c, body, 0f, 3f * d, fade(p.line, 0.16f))
        val rad = max(card.width(), card.height()) * 0.62f
        c.drawPath(body, pp(0).apply { shader = RadialGradient(cx, cy - card.height() * 0.1f, rad, intArrayOf(p.background, p.background, p.secondary), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) })
        c.withSave {
            clipPath(body)
            for (i in 0..4) drawPath(body, pp(fade(p.secondary, 0.14f), Paint.Style.STROKE, (12f - i * 2.4f) * d))
        }
        val hl = RectF(l + card.width() * 0.07f, tp + card.height() * 0.08f, l + card.width() * 0.34f, tp + card.height() * 0.08f + min(9f * d, card.height() * 0.16f))
        c.drawOval(hl, pp(0x8CFFFFFF.toInt()))
        c.drawPath(body, pp(fade(p.line, 0.5f), Paint.Style.STROKE, 1.5f * d))
    }

    /** Chalkboard menu in a wooden frame. */
    val boardFrame = FramePainter { c, t, card, r, d, seed ->
        val p = t.palette
        val body = rr(card, r)
        shadow(c, body, 0f, 3.5f * d, fade(0xFF000000.toInt(), 0.3f))
        c.drawPath(body, pp(p.secondary))
        val rnd = Random(seed + 11)
        c.withSave {
            clipPath(body)
            val grain = pp(fade(p.line, 0.25f), Paint.Style.STROKE, 0.8f * d)
            repeat(8) {
                val y = card.top + rnd.nextFloat() * card.height()
                drawLine(card.left, y, card.right, y + (rnd.nextFloat() - 0.5f) * 3f * d, grain)
            }
        }
        val bw = min(5f * d, min(card.width(), card.height()) * 0.1f)
        val board = inset(card, bw)
        val br = max(0f, r - bw)
        c.drawRoundRect(board, br, br, pp(p.background))
        c.withSave {
            clipRect(board)
            repeat(4) {
                val x = board.left + rnd.nextFloat() * board.width(); val y = board.top + rnd.nextFloat() * board.height()
                val w = board.width() * (0.2f + rnd.nextFloat() * 0.3f)
                drawOval(RectF(x - w / 2, y - w / 5, x + w / 2, y + w / 5), pp(fade(0xFFFFFFFF.toInt(), 0.045f)))
            }
            val chalk = pp(fade(p.text, 0.5f), Paint.Style.STROKE, 1.1f * d)
            val y = board.bottom - 4f * d
            drawLine(board.centerX() - board.width() * 0.18f, y, board.centerX() + board.width() * 0.18f, y, chalk)
        }
        c.drawRoundRect(board, br, br, pp(fade(0xFF000000.toInt(), 0.35f), Paint.Style.STROKE, 1.4f * d))
        c.drawRoundRect(inset(card, 1f * d), max(0f, r - d), max(0f, r - d), pp(fade(0xFFFFFFFF.toInt(), 0.22f), Paint.Style.STROKE, 1f * d))
        c.drawPath(body, pp(p.line, Paint.Style.STROKE, 1.6f * d))
    }

    /** Washi paper with a pink wash, ink brush strokes and a little red seal. */
    val washiFrame = FramePainter { c, t, card, r, d, seed ->
        val p = t.palette
        val body = rr(card, r)
        shadow(c, body, 1.5f * d, 2.5f * d, fade(p.line, 0.16f))
        c.drawPath(body, pp(p.background))
        c.withSave {
            clipPath(body)
            drawRect(card, pp(0).apply { shader = LinearGradient(0f, card.top + card.height() * 0.4f, 0f, card.bottom, fade(p.accent, 0f), fade(p.accent, 0.32f), Shader.TileMode.CLAMP) })
        }
        val brush = pp(fade(p.line, 0.88f))
        val x0 = card.left - 1f * d; val x1 = card.left + card.width() * 0.42f; val y0 = card.top + 1f * d
        val mid = (x0 + x1) / 2
        c.drawPath(Path().apply { moveTo(x0, y0 - 2f * d); quadTo(mid, y0 - 3.5f * d, x1, y0); quadTo(mid, y0 + 2f * d, x0, y0 + 2.2f * d); close() }, brush)
        val rnd = Random(seed + 2)
        repeat(4) { c.drawCircle(x1 - rnd.nextFloat() * 10f * d, y0 + (rnd.nextFloat() - 0.5f) * 4f * d, 0.6f * d, brush) }
        val bx0 = card.right + 1f * d; val bx1 = card.right - card.width() * 0.3f; val by = card.bottom - 0.5f * d
        val bmid = (bx0 + bx1) / 2
        c.drawPath(Path().apply { moveTo(bx0, by - 2f * d); quadTo(bmid, by - 3f * d, bx1, by); quadTo(bmid, by + 2f * d, bx0, by + 1.8f * d); close() }, brush)
        val s = 6f * d
        val seal = RectF(card.right - 9f * d - s, card.top + 4f * d, card.right - 9f * d, card.top + 4f * d + s)
        c.drawRoundRect(seal, 1f * d, 1f * d, pp(0xE6D9455F.toInt()))
        c.drawRoundRect(inset(seal, 1.3f * d), 0.5f * d, 0.5f * d, pp(0xCCFFFFFF.toInt(), Paint.Style.STROKE, 0.6f * d))
        c.drawPath(body, pp(fade(p.line, 0.3f), Paint.Style.STROKE, 1.2f * d))
    }

    /** A card with cat ears on its top corners and whiskers on the sides. */
    val catEarFrame = FramePainter { c, t, card, r, d, _ ->
        val p = t.palette
        val earW = min(card.width() * 0.2f, 26f * d)
        val earH = min(9.5f * d, earW * 0.7f)
        val top = card.top
        fun ear(mirror: Boolean, w: Float, h: Float, base: Float, lift: Float): Path {
            val xL = card.left + max(r * 0.3f, 2f * d)
            fun fx(x: Float) = if (mirror) card.right - (x - card.left) else x
            return Path().apply {
                moveTo(fx(xL), top + base)
                lineTo(fx(xL + w * 0.2f), top - h * 0.8f + lift)
                quadTo(fx(xL + w * 0.3f), top - h * 1.15f + lift, fx(xL + w * 0.44f), top - h * 0.78f + lift)
                lineTo(fx(xL + w), top + base * 0.4f)
                close()
            }
        }
        val body = union2(rr(card, r), ear(false, earW, earH, earH * 0.8f, 0f), ear(true, earW, earH, earH * 0.8f, 0f))
        shadow(c, body, 0f, 3f * d, fade(p.line, 0.2f))
        c.drawPath(body, pp(p.background))
        val inner = pp(p.accent)
        c.drawPath(ear(false, earW * 0.62f, earH * 0.62f, 0f, 0f).apply { offset(earW * 0.14f, 0.5f * d) }, inner)
        c.drawPath(ear(true, earW * 0.62f, earH * 0.62f, 0f, 0f).apply { offset(-earW * 0.14f, 0.5f * d) }, inner)
        c.drawPath(body, pp(p.line, Paint.Style.STROKE, 2.2f * d))
        val wh = pp(fade(p.line, 0.6f), Paint.Style.STROKE, 1.2f * d)
        val cy = card.centerY()
        for (k in -1..1) {
            c.drawLine(card.left + 2f * d, cy + k * 3.5f * d, card.left - 4.5f * d, cy + k * 5.5f * d, wh)
            c.drawLine(card.right - 2f * d, cy + k * 3.5f * d, card.right + 4.5f * d, cy + k * 5.5f * d, wh)
        }
    }

    private fun union2(vararg ps: Path): Path {
        val out = Path(ps[0])
        for (i in 1 until ps.size) out.op(ps[i], Path.Op.UNION)
        return out
    }

    /** Cream card with a plaid flannel band on top and a running stitch under it. */
    val plaidBandFrame = FramePainter { c, t, card, r, d, _ ->
        val p = t.palette
        val body = rr(card, r)
        shadow(c, body, 0f, 3f * d, fade(p.line, 0.25f))
        c.drawPath(body, pp(p.background))
        val bandH = min(13f * d, card.height() * 0.26f)
        c.withSave {
            clipPath(body)
            clipRect(card.left, card.top, card.right, card.top + bandH)
            drawRect(card, pp(p.accent))
            plaid(this, card, 8f * d, fade(p.secondary, 0.45f), fade(0xFFFFF5E6.toInt(), 0.55f), d)
        }
        c.drawLine(card.left, card.top + bandH, card.right, card.top + bandH, pp(p.line, Paint.Style.STROKE, 1.4f * d))
        val sy = card.top + bandH + 3f * d
        c.drawLine(card.left + r * 0.5f + 3f * d, sy, card.right - r * 0.5f - 3f * d, sy, dash(fade(p.accent, 0.85f), 1.2f * d, 3f * d, 2.5f * d))
        c.drawPath(body, pp(p.line, Paint.Style.STROKE, 2f * d))
    }

    /** Tartan pattern over [area]: [band] wide stripes both ways plus thin [thin] lines. */
    fun plaid(c: Canvas, area: RectF, band: Float, stripe: Int, thin: Int, d: Float) {
        val sp = pp(stripe)
        var x = area.left
        while (x < area.right) { c.drawRect(x, area.top, x + band * 0.8f, area.bottom, sp); x += band * 2 }
        var y = area.top + band * 0.4f
        while (y < area.bottom) { c.drawRect(area.left, y, area.right, y + band * 0.8f, sp); y += band * 2 }
        val tp = pp(thin, Paint.Style.STROKE, 0.8f * d)
        x = area.left + band * 1.4f
        while (x < area.right) { c.drawLine(x, area.top, x, area.bottom, tp); x += band * 2 }
        y = area.top + band * 1.8f
        while (y < area.bottom) { c.drawLine(area.left, y, area.right, y, tp); y += band * 2 }
    }

    /** Wooden planks with grain lines and nails. */
    val woodFrame = FramePainter { c, t, card, r, d, seed ->
        val p = t.palette
        val body = rr(card, r)
        shadow(c, body, 0f, 4f * d, fade(p.line, 0.45f))
        c.drawPath(body, pp(p.background))
        val rnd = Random(seed + 9)
        c.withSave {
            clipPath(body)
            val n = max(2, (card.height() / (22f * d)).toInt())
            val ph = card.height() / n
            val grain = pp(fade(p.line, 0.13f), Paint.Style.STROKE, 0.9f * d)
            for (i in 0 until n) {
                val top = card.top + ph * i
                if (i % 2 == 1) drawRect(card.left, top, card.right, top + ph, pp(fade(p.secondary, 0.45f)))
                repeat(2) {
                    val gy = top + ph * (0.25f + rnd.nextFloat() * 0.5f)
                    val gp = Path().apply {
                        moveTo(card.left, gy)
                        cubicTo(card.left + card.width() * 0.3f, gy - 2f * d, card.left + card.width() * 0.6f, gy + 2.5f * d, card.right, gy - 1f * d)
                    }
                    drawPath(gp, grain)
                }
                if (rnd.nextFloat() < 0.5f) {
                    val kx = card.left + card.width() * (0.2f + rnd.nextFloat() * 0.6f)
                    drawOval(RectF(kx - 3f * d, top + ph * 0.5f - 1.5f * d, kx + 3f * d, top + ph * 0.5f + 1.5f * d), grain)
                }
                if (i > 0) drawLine(card.left, top, card.right, top, pp(fade(p.line, 0.5f), Paint.Style.STROKE, 1.2f * d))
                val nail = pp(fade(p.line, 0.6f))
                drawCircle(card.left + 5f * d, top + ph / 2, 1.2f * d, nail)
                drawCircle(card.right - 5f * d, top + ph / 2, 1.2f * d, nail)
            }
            drawLine(card.left, card.top + 1.5f * d, card.right, card.top + 1.5f * d, pp(0x40FFFFFF, Paint.Style.STROKE, 1.5f * d))
        }
        c.drawPath(body, pp(p.line, Paint.Style.STROKE, 2.4f * d))
    }

    /** White bubbly card wrapped in rainbow piping. */
    val pipingFrame = FramePainter { c, t, card, r, d, _ ->
        val p = t.palette
        val body = rr(card, r)
        shadow(c, body, 0f, 4f * d, fade(p.line, 0.3f))
        val sw = 1.6f * d
        val cols = intArrayOf(0xFFFF6B6B.toInt(), 0xFFFFA94D.toInt(), 0xFFFFE066.toInt(), 0xFF8CE99A.toInt(), 0xFF74C0FC.toInt())
        cols.forEachIndexed { i, col ->
            val inn = i * sw
            c.drawRoundRect(inset(card, inn), max(0f, r - inn), max(0f, r - inn), pp(col))
        }
        val inn = cols.size * sw
        c.drawRoundRect(inset(card, inn), max(0f, r - inn), max(0f, r - inn), pp(p.background))
        c.drawPath(body, pp(p.line, Paint.Style.STROKE, 2f * d))
        gleam(c, inset(card, inn), max(0f, r - inn), d)
    }
}
