package app.custo.feature.overlays

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A sticker on the home screen. Position is the center as a fraction of screen width/height,
 * [size] is its width as a fraction of screen width, [aspect] is height / width.
 * [src] is "b:<name>" for a bundled sticker or "f:<file>" for an imported image.
 */
@Serializable
data class Sticker(
    val id: String,
    val src: String,
    val x: Float = 0.5f,
    val y: Float = 0.4f,
    val size: Float = 0.3f,
    val rot: Float = 0f,
    val aspect: Float = 1f,
)

/** Axis-aligned box in pixels. */
data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t
}

/** Pan, zoom and rotation between two frames of a two-finger gesture. */
data class Pinch(val panX: Float, val panY: Float, val zoom: Float, val rotDeg: Float)

/** Pure sticker math, unit-tested: codec, transforms, hit tests, window bounds. */
object StickerMath {
    const val MIN_SIZE = 0.06f
    const val MAX_SIZE = 0.9f
    const val MAX_STICKERS = 20

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(list: List<Sticker>): String = json.encodeToString(list)
    fun decode(s: String?): List<Sticker> = if (s.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString<List<Sticker>>(s) }.getOrDefault(emptyList())

    /** Angle in (-180, 180]. */
    fun normalize(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /** Applies a gesture measured in pixels on a [w] x [h] canvas. Keeps the center on screen. */
    fun transform(s: Sticker, panX: Float, panY: Float, zoom: Float, rotDeg: Float, w: Float, h: Float): Sticker = s.copy(
        x = (s.x + panX / w).coerceIn(0f, 1f),
        y = (s.y + panY / h).coerceIn(0f, 1f),
        size = (s.size * zoom).coerceIn(MIN_SIZE, MAX_SIZE),
        rot = normalize(s.rot + rotDeg),
    )

    fun halfW(s: Sticker, w: Float) = s.size * w / 2f
    fun halfH(s: Sticker, w: Float) = halfW(s, w) * s.aspect

    /** True if pixel ([px], [py]) lies on the (rotated) sticker. */
    fun contains(s: Sticker, px: Float, py: Float, w: Float, h: Float): Boolean {
        val dx = px - s.x * w; val dy = py - s.y * h
        val a = Math.toRadians(-s.rot.toDouble())
        val lx = dx * cos(a) - dy * sin(a)
        val ly = dx * sin(a) + dy * cos(a)
        return abs(lx) <= halfW(s, w) && abs(ly) <= halfH(s, w)
    }

    /** The topmost sticker under the point (last drawn wins), or -1. */
    fun topmostAt(list: List<Sticker>, px: Float, py: Float, w: Float, h: Float): Int =
        list.indexOfLast { contains(it, px, py, w, h) }

    /** Bounding box of the rotated sticker, used to size its overlay window. */
    fun bounds(s: Sticker, w: Float, h: Float): Box {
        val hw = halfW(s, w); val hh = halfH(s, w)
        val a = Math.toRadians(s.rot.toDouble())
        val ex = (abs(hw * cos(a)) + abs(hh * sin(a))).toFloat()
        val ey = (abs(hw * sin(a)) + abs(hh * cos(a))).toFloat()
        val cx = s.x * w; val cy = s.y * h
        return Box(cx - ex, cy - ey, cx + ex, cy + ey)
    }

    /** Pan of the centroid, zoom by finger distance, rotation by finger angle, from (a0, b0) to (a1, b1). */
    fun pinch(a0x: Float, a0y: Float, b0x: Float, b0y: Float, a1x: Float, a1y: Float, b1x: Float, b1y: Float): Pinch {
        val d0 = hypot(b0x - a0x, b0y - a0y); val d1 = hypot(b1x - a1x, b1y - a1y)
        val zoom = if (d0 < 1f) 1f else d1 / d0
        val r0 = atan2(b0y - a0y, b0x - a0x); val r1 = atan2(b1y - a1y, b1x - a1x)
        val rot = normalize(Math.toDegrees((r1 - r0).toDouble()).toFloat())
        return Pinch((a1x + b1x) / 2 - (a0x + b0x) / 2, (a1y + b1y) / 2 - (a0y + b0y) / 2, zoom, rot)
    }

    /** Moves the sticker at [index] to the top of the stack. */
    fun bringToFront(list: List<Sticker>, index: Int): List<Sticker> =
        if (index !in list.indices || index == list.lastIndex) list
        else list.toMutableList().apply { add(removeAt(index)) }
}
