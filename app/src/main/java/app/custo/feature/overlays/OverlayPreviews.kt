package app.custo.feature.overlays

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.custo.ui.components.MiniScreen
import app.custo.ui.components.drawMiniScreen
import app.custo.ui.components.miniColors
import app.custo.ui.components.rememberLoop
import app.custo.ui.components.smooth
import app.custo.ui.components.tri
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Small live pictures of each overlay, drawn with Canvas from the current settings.
 * They take any size: a tall phone on detail screens, a wide crop in hub tiles.
 * Every animated one reads its phase in the draw block only, and stops when off-screen.
 */

private val Green = Color(0xFF00E676)
private val Rainbow = EdgeLightingOverlay.RAINBOW.map { Color(it) }
/** Stand-in "app colors" for previews of the App color mode. */
private val AppColors = listOf(Color(0xFF25D366), Color(0xFF1DA1F2), Color(0xFFFF5252))

fun edgeColors(mode: EdgeColorMode, custom: List<Int>, accent: Color): List<Color> = when (mode) {
    EdgeColorMode.APP -> AppColors
    EdgeColorMode.ACCENT -> listOf(accent, accent.copy(alpha = 0.7f))
    EdgeColorMode.RAINBOW -> Rainbow
    EdgeColorMode.CUSTOM -> custom.ifEmpty { EdgeLightingOverlay.DEFAULT_GRADIENT }.map { Color(it) }
}

fun edgeBrush(colors: List<Color>, center: Offset): Brush =
    if (colors.size == 1) Brush.sweepGradient(listOf(colors[0], colors[0]), center) else Brush.sweepGradient(colors + colors.first(), center)

// ---------- Edge lighting ----------

@Composable
fun EdgeLightPreview(style: EdgeStyle, colors: List<Color>, widthDp: Float, modifier: Modifier, periodMs: Int = 2500, animate: Boolean = true) {
    val c = miniColors()
    val phase = rememberLoop(periodMs.coerceAtLeast(900), animate)
    val path = remember { Path() }
    val seg = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(modifier) {
        val s = drawMiniScreen(c)
        val w = (widthDp * s.unit * 1.6f).coerceIn(2f, s.w * 0.05f)
        val r = s.rect.deflate(w / 2)
        path.reset(); path.addRoundRect(RoundRect(r, CornerRadius((s.corner - w / 2).coerceAtLeast(0f))))
        measure.setPath(path, true)
        val len = measure.length
        val brush = edgeBrush(colors, s.rect.center)
        val t = if (animate) phase.floatValue else 0.35f
        fun glow(p: Path, alpha: Float) {
            drawPath(p, brush, alpha = alpha * 0.35f, style = Stroke(w * 2.6f, cap = StrokeCap.Round))
            drawPath(p, brush, alpha = alpha, style = Stroke(w, cap = StrokeCap.Round))
        }
        fun segment(from: Float, length: Float, alpha: Float) {
            var a = from % len; if (a < 0) a += len
            val b = a + length
            seg.reset()
            measure.getSegment(a, b.coerceAtMost(len), seg, true)
            if (b > len) measure.getSegment(0f, b - len, seg, true)
            glow(seg, alpha)
        }
        when (style) {
            EdgeStyle.SWEEP -> {
                // Two arms from the top center meet at the bottom, then fade.
                val reach = smooth(t / 0.7f) * len / 2
                val fade = 1f - smooth((t - 0.7f) / 0.3f)
                val start = measureTopCenter(r, len, measure)
                segment(start, reach, fade)
                segment(start - reach, reach, fade)
            }
            EdgeStyle.PULSE -> glow(path, (0.25f + 0.75f * abs(sin(t * 2 * PI.toFloat()))))
            EdgeStyle.BREATHING -> glow(path, 0.15f + 0.85f * (0.5f - 0.5f * cos(t * 2 * PI.toFloat())))
            EdgeStyle.COMET -> {
                val head = t * len + measureTopCenter(r, len, measure)
                for (k in 0 until 6) segment(head - (k + 1) * len * 0.035f, len * 0.035f, (1f - k / 6f))
            }
        }
    }
}

/** Path distance of the top center, since roundrect paths start at the left of the top edge. */
private fun measureTopCenter(r: Rect, len: Float, m: PathMeasure): Float {
    // Search a few samples for the point nearest the top center. Cheap: 24 lookups.
    var best = 0f; var bestD = Float.MAX_VALUE
    for (i in 0 until 24) {
        val d = len * i / 24f
        val p = m.getPosition(d)
        val dist = abs(p.x - r.center.x) + abs(p.y - r.top) * 4
        if (dist < bestD) { bestD = dist; best = d }
    }
    return best
}

// ---------- Notification light ----------

@Composable
fun NotifLightPreview(dotDp: Float, modifier: Modifier, animate: Boolean = true) {
    val c = miniColors()
    val phase = rememberLoop(2400, animate)
    Canvas(modifier) {
        val s = drawMiniScreen(c)
        val t = if (animate) phase.floatValue else 0.25f
        val color = AppColors[((t * 2).toInt()) % 2]
        val a = abs(sin(t * 2 * PI.toFloat()))
        val ring = s.camR + (dotDp * s.unit * 0.6f).coerceAtLeast(2f)
        drawCircle(color.copy(alpha = 0.35f * a), ring * 1.9f, s.cam)
        drawCircle(color.copy(alpha = a), ring, s.cam, style = Stroke(ring - s.camR + 1.5f))
    }
}

// ---------- Dynamic island ----------

@Composable
fun IslandPreview(modifier: Modifier, idlePill: Boolean = true, animate: Boolean = true) {
    val c = miniColors()
    val phase = rememberLoop(4200, animate)
    Canvas(modifier) {
        val s = drawMiniScreen(c)
        val t = if (animate) phase.floatValue else 0.55f
        val open = smooth((t - 0.15f) / 0.15f) * (1f - smooth((t - 0.8f) / 0.15f))
        val minW = if (idlePill) s.camR * 7f else s.camR * 2.2f
        val maxW = s.w * 0.84f
        val minH = s.camR * 2.6f
        val maxH = s.w * 0.26f
        val pw = minW + (maxW - minW) * open
        val ph = minH + (maxH - minH) * open
        val top = s.cam.y - minH / 2
        drawRoundRect(Color.Black, Offset(s.cam.x - pw / 2, top), Size(pw, ph), CornerRadius(minH / 2 + (ph - minH) * 0.35f))
        if (open > 0.6f) {
            val a = (open - 0.6f) / 0.4f
            val pad = ph * 0.18f
            val art = ph - pad * 2
            val left = s.cam.x - pw / 2 + pad
            drawRoundRect(Brush.linearGradient(listOf(Color(0xFFFF6E40), Color(0xFFD500F9))), Offset(left, top + pad), Size(art, art), CornerRadius(art * 0.25f), alpha = a)
            val tx = left + art + pad
            drawRoundRect(Color.White.copy(alpha = 0.85f * a), Offset(tx, top + pad * 1.4f), Size(pw * 0.38f, art * 0.18f), CornerRadius(art))
            drawRoundRect(Color.White.copy(alpha = 0.45f * a), Offset(tx, top + pad * 1.4f + art * 0.32f), Size(pw * 0.26f, art * 0.15f), CornerRadius(art))
            // Equalizer bars.
            val bx = s.cam.x + pw / 2 - pad - art * 0.6f
            for (i in 0 until 3) {
                val bh = art * (0.25f + 0.5f * abs(sin((t * 9f + i * 0.7f) * PI.toFloat())))
                drawRoundRect(Green.copy(alpha = a), Offset(bx + i * art * 0.22f, top + ph / 2 - bh / 2), Size(art * 0.13f, bh), CornerRadius(art))
            }
        }
    }
}

// ---------- Battery bar ----------

fun barColor(mode: BarColorMode, custom: Int, accent: Color): Brush = when (mode) {
    BarColorMode.LEVEL -> Brush.linearGradient(listOf(Green, Green))
    BarColorMode.GRADIENT -> Brush.horizontalGradient(listOf(Color(0xFFFF1744), Color(0xFFFFC400), Green))
    BarColorMode.ACCENT -> Brush.linearGradient(listOf(accent, accent))
    BarColorMode.CUSTOM -> Brush.linearGradient(listOf(Color(custom), Color(custom)))
}

@Composable
fun BatteryBarPreview(
    style: BatteryBarStyle, position: BarPosition, thicknessDp: Float, segmented: Boolean,
    colorMode: BarColorMode, custom: Int, modifier: Modifier, animate: Boolean = true,
) {
    val c = miniColors()
    val accent = MaterialTheme.colorScheme.primary
    val phase = rememberLoop(5000, animate)
    Canvas(modifier) {
        val s = drawMiniScreen(c)
        val t = if (animate) phase.floatValue else 0.6f
        val level = 0.35f + 0.5f * smooth(tri(t) * 1.3f)
        val th = (thicknessDp * s.unit * 2.2f).coerceAtLeast(2f)
        val brush = barColor(colorMode, custom, accent)
        if (style == BatteryBarStyle.CUTOUT) {
            val rr = s.camR + th * 1.2f
            drawCircle(Color.White.copy(alpha = 0.15f), rr, s.cam, style = Stroke(th))
            drawArc(brush, -90f, 360f * level, false, Offset(s.cam.x - rr, s.cam.y - rr), Size(rr * 2, rr * 2), style = Stroke(th, cap = StrokeCap.Round))
        } else {
            val y = if (position == BarPosition.TOP) s.rect.top else s.rect.bottom - th
            clipRect(s.rect.left, s.rect.top, s.rect.right, s.rect.bottom) {
                if (segmented) {
                    val gap = th * 0.8f
                    val bw = (s.w - gap * 9) / 10
                    for (i in 0 until 10) {
                        val on = (i + 1) / 10f <= level + 0.05f
                        drawRect(if (on) brush else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.12f))),
                            Offset(s.rect.left + i * (bw + gap), y), Size(bw, th))
                    }
                } else {
                    drawRect(brush, Offset(s.rect.left, y), Size(s.w * level, th))
                }
            }
        }
    }
}

// ---------- Charging ----------

@Composable
fun ChargingPreview(style: ChargeStyle, modifier: Modifier, animate: Boolean = true) {
    val c = miniColors()
    val phase = rememberLoop(2600, animate)
    Canvas(modifier) {
        val s = drawMiniScreen(c)
        val t = if (animate) phase.floatValue else 0.6f
        drawRoundRect(Color.Black.copy(alpha = 0.45f * tri(t).coerceAtMost(0.5f) * 2), s.rect.topLeft, s.rect.size, CornerRadius(s.corner))
        val cx = s.rect.center
        val rr = s.w * 0.22f
        when (style) {
            ChargeStyle.RING -> {
                drawCircle(Green.copy(alpha = 0.18f), rr, cx, style = Stroke(rr * 0.16f))
                drawArc(Green, -90f, 360f * smooth(t / 0.8f) * 0.82f, false, Offset(cx.x - rr, cx.y - rr), Size(rr * 2, rr * 2), style = Stroke(rr * 0.16f, cap = StrokeCap.Round))
            }
            ChargeStyle.EDGES -> {
                val rise = smooth(t / 0.7f) * s.h
                val th = s.w * 0.035f
                clipRect(s.rect.left, s.rect.bottom - rise, s.rect.right, s.rect.bottom) {
                    drawRoundRect(Green, s.rect.topLeft + Offset(th / 2, th / 2), Size(s.w - th, s.h - th), CornerRadius(s.corner), style = Stroke(th))
                    drawRoundRect(Green.copy(alpha = 0.3f), s.rect.topLeft + Offset(th / 2, th / 2), Size(s.w - th, s.h - th), CornerRadius(s.corner), style = Stroke(th * 3))
                }
            }
            ChargeStyle.PULSE -> for (k in 0 until 3) {
                val p = (t + k / 3f) % 1f
                drawCircle(Green.copy(alpha = (1f - p) * 0.8f), rr * (0.3f + p), cx, style = Stroke(rr * 0.08f))
            }
        }
        drawBolt(cx, rr * 0.42f, Green)
    }
}

private fun DrawScope.drawBolt(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x + r * 0.15f, c.y - r)
        lineTo(c.x - r * 0.5f, c.y + r * 0.15f)
        lineTo(c.x - r * 0.02f, c.y + r * 0.15f)
        lineTo(c.x - r * 0.15f, c.y + r)
        lineTo(c.x + r * 0.5f, c.y - r * 0.15f)
        lineTo(c.x + r * 0.02f, c.y - r * 0.15f)
        close()
    }
    drawPath(p, color)
}

// ---------- Rounded corners and tint ----------

@Composable
fun CornersPreview(radiusDp: Float, color: Int, top: Boolean, bottom: Boolean, modifier: Modifier) {
    val c = miniColors()
    Canvas(modifier) {
        val s = drawMiniScreen(c, squareCorners = true)
        val r = (radiusDp * s.unit * 2f).coerceAtMost(s.w / 3)
        val col = Color(color)
        val corner = Path().apply {
            moveTo(0f, 0f); lineTo(r, 0f)
            arcTo(Rect(0f, 0f, r * 2, r * 2), 270f, -90f, false)
            close()
        }
        fun at(x: Float, y: Float, sx: Float, sy: Float) {
            drawContext.transform.translate(x, y)
            scale(sx, sy, Offset.Zero) { drawPath(corner, col) }
            drawContext.transform.translate(-x, -y)
        }
        val rc = s.rect
        if (top) { at(rc.left, rc.top, 1f, 1f); at(rc.right, rc.top, -1f, 1f) }
        if (bottom) { at(rc.left, rc.bottom, 1f, -1f); at(rc.right, rc.bottom, -1f, -1f) }
        // A hairline guide so black corners show against the dark bezel.
        if (top || bottom) drawRoundRect(Color.White.copy(alpha = 0.25f), rc.topLeft, rc.size, CornerRadius(r), style = Stroke(1.2f))
    }
}

@Composable
fun TintPreview(color: Int, opacity: Float, on: Boolean, modifier: Modifier) {
    val c = miniColors()
    Canvas(modifier) {
        val s = drawMiniScreen(c)
        // Left half plain, right half tinted: a before/after you can see at a glance.
        clipRect(s.rect.center.x, s.rect.top, s.rect.right, s.rect.bottom) {
            drawRoundRect(Color(color).copy(alpha = if (on) opacity.coerceIn(0.05f, 0.95f) else opacity * 0.5f), s.rect.topLeft, s.rect.size, CornerRadius(s.corner))
        }
        drawLine(Color.White.copy(alpha = 0.6f), Offset(s.rect.center.x, s.rect.top + s.bar), Offset(s.rect.center.x, s.rect.bottom - s.bar), 1.5f)
    }
}

// ---------- Network speed ----------

@Composable
fun NetSpeedPreview(position: NetPosition, color: Int, textSp: Float, nudgeX: Int, nudgeY: Int, modifier: Modifier, animate: Boolean = true) {
    val c = miniColors()
    val measurer = rememberTextMeasurer()
    val phase = rememberLoop(3000, animate)
    Canvas(modifier) {
        val s = drawMiniScreen(c)
        val t = if (animate) phase.floatValue else 0.4f
        val down = listOf("1.2 MB/s", "860 KB/s", "2.4 MB/s", "340 KB/s")[(t * 4).toInt() % 4]
        val fs = (textSp * s.unit * 2.4f).coerceIn(6f, s.bar * 0.75f)
        val layout = measurer.measure("↓ $down", TextStyle(color = Color(color.takeIf { it != 0 } ?: 0xFFFFFFFF.toInt()), fontSize = (fs / density / fontScale).sp, fontWeight = FontWeight.Medium))
        val tw = layout.size.width
        val x = when (position) {
            NetPosition.LEFT -> s.rect.left + s.w * 0.22f
            NetPosition.CENTER -> s.cam.x + s.camR * 2.5f
            NetPosition.RIGHT -> s.rect.right - s.w * 0.24f - tw
        } + nudgeX * s.unit
        val y = s.rect.top + s.bar / 2 - layout.size.height / 2 + nudgeY * s.unit
        drawText(layout, topLeft = Offset(x.coerceIn(s.rect.left, s.rect.right - tw), y))
    }
}

// ---------- Volume panel ----------

@Composable
fun VolumePreview(skin: VolumeSkin, side: PanelSide, scale: Float, offsetY: Float, modifier: Modifier, animate: Boolean = true) {
    val c = miniColors()
    val accent = MaterialTheme.colorScheme.primary
    val phase = rememberLoop(3200, animate)
    Canvas(modifier) {
        val s = drawMiniScreen(c)
        drawVolume(s, skin, side, scale, offsetY, if (animate) 0.3f + 0.5f * smooth(tri(phase.floatValue) * 1.2f) else 0.6f, accent)
    }
}

/** Draws one volume skin; also used for the thumbnails. */
fun DrawScope.drawVolume(s: MiniScreen, skin: VolumeSkin, side: PanelSide, scale: Float, offsetY: Float, level: Float, accent: Color) {
    val k = scale.coerceIn(0.6f, 1.6f)
    val ph = s.h * 0.36f * k
    val pw = when (skin) { VolumeSkin.SLIM -> s.w * 0.07f; VolumeSkin.BIG -> s.w * 0.2f; else -> s.w * 0.13f } * k
    val margin = s.w * 0.06f
    val x = if (side == PanelSide.RIGHT) s.rect.right - margin - pw else s.rect.left + margin
    val y = s.rect.center.y - ph / 2 + offsetY * s.h
    val panel = Color(0xF21F1F24)
    when (skin) {
        VolumeSkin.PILL, VolumeSkin.BIG -> {
            val rad = if (skin == VolumeSkin.PILL) pw / 2 else pw * 0.3f
            drawRoundRect(panel, Offset(x, y), Size(pw, ph), CornerRadius(rad))
            val fh = ph * level
            clipRect(x, y + ph - fh, x + pw, y + ph) { drawRoundRect(accent, Offset(x, y), Size(pw, ph), CornerRadius(rad)) }
            if (skin == VolumeSkin.BIG) drawCircle(Color.White.copy(alpha = 0.9f), pw * 0.12f, Offset(x + pw / 2, y + ph - pw * 0.35f))
        }
        VolumeSkin.SLIM -> {
            val tx = x + pw / 2
            drawLine(Color.White.copy(alpha = 0.35f), Offset(tx, y), Offset(tx, y + ph), pw * 0.35f, StrokeCap.Round)
            drawLine(accent, Offset(tx, y + ph * (1 - level)), Offset(tx, y + ph), pw * 0.35f, StrokeCap.Round)
            drawCircle(Color.White, pw * 0.5f, Offset(tx, y + ph * (1 - level)))
        }
        VolumeSkin.DOTS -> {
            drawRoundRect(panel, Offset(x, y), Size(pw, ph), CornerRadius(pw / 2))
            val n = 8
            for (i in 0 until n) {
                val on = (n - i) / n.toFloat() <= level + 0.06f
                drawCircle(if (on) accent else Color.White.copy(alpha = 0.25f), pw * 0.17f, Offset(x + pw / 2, y + ph * (i + 0.7f) / (n + 0.4f)))
            }
        }
    }
}

// ---------- Edge gestures ----------

@Composable
fun GesturesPreview(left: Boolean, right: Boolean, corners: Boolean, widthDp: Float, height: Float, offsetY: Float, modifier: Modifier, animate: Boolean = true) {
    val c = miniColors()
    val accent = MaterialTheme.colorScheme.primary
    val phase = rememberLoop(2400, animate)
    Canvas(modifier) {
        val s = drawMiniScreen(c)
        val w = (widthDp * s.unit * 2f).coerceIn(3f, s.w * 0.12f)
        val h = s.h * height
        val top = s.rect.center.y - h / 2 + offsetY * s.h
        val strip = accent.copy(alpha = 0.55f)
        if (left) drawRoundRect(strip, Offset(s.rect.left, top), Size(w, h), CornerRadius(w / 2))
        if (right) drawRoundRect(strip, Offset(s.rect.right - w, top), Size(w, h), CornerRadius(w / 2))
        if (corners) {
            val cz = s.w * 0.16f
            drawArc(strip, 180f, 90f, true, Offset(s.rect.left - cz, s.rect.bottom - cz), Size(cz * 2, cz * 2))
            drawArc(strip, 270f, 90f, true, Offset(s.rect.right - cz, s.rect.bottom - cz), Size(cz * 2, cz * 2))
        }
        // A fingertip swiping in from whichever edge is on.
        if (left || right) {
            val t = if (animate) phase.floatValue else 0.4f
            val p = smooth(t / 0.6f)
            val a = 1f - smooth((t - 0.6f) / 0.3f)
            val fromLeft = left
            val fx = if (fromLeft) s.rect.left + w / 2 + p * s.w * 0.3f else s.rect.right - w / 2 - p * s.w * 0.3f
            val fy = top + h / 2
            drawCircle(Color.White.copy(alpha = 0.85f * a), s.w * 0.05f, Offset(fx, fy))
            drawLine(Color.White.copy(alpha = 0.35f * a), Offset(if (fromLeft) s.rect.left + w / 2 else s.rect.right - w / 2, fy), Offset(fx, fy), s.w * 0.04f, StrokeCap.Round)
        }
    }
}

// ---------- Double-tap to lock ----------

@Composable
fun StatusTapPreview(widthDp: Float, modifier: Modifier, animate: Boolean = true) {
    val c = miniColors()
    val accent = MaterialTheme.colorScheme.primary
    val phase = rememberLoop(3000, animate)
    Canvas(modifier) {
        val s = drawMiniScreen(c)
        val t = if (animate) phase.floatValue else 0.2f
        val zw = (widthDp * s.unit * 1.5f).coerceIn(s.w * 0.12f, s.w * 0.9f)
        drawRoundRect(accent.copy(alpha = 0.45f), Offset(s.cam.x - zw / 2, s.rect.top + 2f), Size(zw, s.bar - 4f), CornerRadius(s.bar))
        for (k in 0 until 2) {
            val p = ((t - k * 0.12f) / 0.25f)
            if (p in 0f..1f) drawCircle(Color.White.copy(alpha = 1f - p), s.bar * (0.4f + p), Offset(s.cam.x, s.rect.top + s.bar * 1.2f), style = Stroke(2f))
        }
        val dark = smooth((t - 0.4f) / 0.1f) * (1f - smooth((t - 0.85f) / 0.1f))
        if (dark > 0f) drawRoundRect(Color.Black.copy(alpha = dark), s.rect.topLeft, s.rect.size, CornerRadius(s.corner))
    }
}

// ---------- Sidebar ----------

@Composable
fun SidebarPreview(side: PanelSide, offsetY: Float, apps: Int, modifier: Modifier, animate: Boolean = true) {
    val c = miniColors()
    val dots = remember { listOf(Color(0xFF25D366), Color(0xFF1DA1F2), Color(0xFFFF5252), Color(0xFFFFC400), Color(0xFF7C4DFF), Color(0xFF00BFA5), Color(0xFFFF6E40), Color(0xFF448AFF)) }
    val phase = rememberLoop(4000, animate)
    Canvas(modifier) {
        val s = drawMiniScreen(c)
        val t = if (animate) phase.floatValue else 0.5f
        val open = smooth((t - 0.15f) / 0.15f) * (1f - smooth((t - 0.8f) / 0.15f))
        val right = side == PanelSide.RIGHT
        val hy = s.rect.center.y + offsetY * s.h
        val hw = s.w * 0.025f
        val hh = s.h * 0.1f
        drawRoundRect(Color.White.copy(alpha = 0.8f * (1f - open)), Offset(if (right) s.rect.right - hw * 1.6f else s.rect.left + hw * 0.6f, hy - hh / 2), Size(hw, hh), CornerRadius(hw))
        if (open > 0f) {
            val pw = s.w * 0.24f
            val n = apps.coerceIn(3, 8)
            val ph = s.h * 0.55f
            val px = if (right) s.rect.right - pw * open - s.w * 0.03f * open else s.rect.left - pw + (pw + s.w * 0.03f) * open
            val py = (s.rect.center.y - ph / 2).coerceIn(s.rect.top + s.bar, s.rect.bottom - ph)
            drawRoundRect(Color(0xF21F1F24), Offset(px, py), Size(pw, ph), CornerRadius(pw * 0.3f))
            val d = pw * 0.22f
            for (i in 0 until n) drawCircle(dots[i], d, Offset(px + pw / 2, py + ph * (i + 0.6f) / (n + 0.2f)))
        }
    }
}

// ---------- Stickers (static, for the hub) ----------

@Composable
fun StickersPreview(modifier: Modifier) {
    val c = miniColors()
    Canvas(modifier) {
        val s = drawMiniScreen(c)
        val u = s.w.coerceAtMost(s.h)
        drawHeart(Offset(s.rect.left + s.w * 0.28f, s.rect.top + s.h * 0.62f), u * 0.13f, Color(0xFFFF5C8A))
        drawStar(Offset(s.rect.right - s.w * 0.26f, s.rect.top + s.h * 0.4f), u * 0.14f, Color(0xFFFFD54F))
        drawCircle(Color(0xFF7CE0C3), u * 0.08f, Offset(s.rect.center.x + s.w * 0.08f, s.rect.bottom - s.h * 0.2f))
    }
}

private fun DrawScope.drawHeart(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r)
        cubicTo(c.x - r * 1.6f, c.y - r * 0.1f, c.x - r * 0.8f, c.y - r * 1.3f, c.x, c.y - r * 0.45f)
        cubicTo(c.x + r * 0.8f, c.y - r * 1.3f, c.x + r * 1.6f, c.y - r * 0.1f, c.x, c.y + r)
        close()
    }
    rotate(-12f, c) { drawPath(p, color) }
}

private fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val a = -PI / 2 + i * PI / 5
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + (rr * cos(a)).toFloat(); val y = c.y + (rr * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    rotate(10f, c) { drawPath(p, color) }
}

// ---------- Control Center (composed, uses the real tile icons) ----------

@Composable
fun ControlCenterPreview(skin: CcSkin, tiles: List<CcTile>, radiusDp: Float, modifier: Modifier, maxTiles: Int = 10) {
    val cs = MaterialTheme.colorScheme
    val (panel, tile, on, ink) = when (skin) {
        CcSkin.LIGHT -> listOf(Color(0xE6F2F2F5), Color(0xFFFFFFFF), cs.primary, Color(0xFF1B1B1F))
        CcSkin.DARK -> listOf(Color(0xE61B1B1F), Color(0xFF2E2E34), cs.primary, Color.White)
        CcSkin.ACCENT -> listOf(cs.primaryContainer.copy(alpha = 0.95f), cs.surface.copy(alpha = 0.5f), cs.primary, cs.onPrimaryContainer)
    }
    val c = miniColors()
    Box(modifier.drawBehind { drawMiniScreen(c) }) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(top = 18.dp, bottom = 8.dp)
                .clip(RoundedCornerShape((radiusDp * 0.6f).dp)).background(panel).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Lay tiles out four to a row; wide ones take a full row.
            val rows = mutableListOf<List<CcTile>>()
            var cur = mutableListOf<CcTile>()
            tiles.take(maxTiles).forEach { t ->
                if (t.span >= 4) { if (cur.isNotEmpty()) rows += cur; rows += listOf(t); cur = mutableListOf() }
                else { cur += t; if (cur.size == 4) { rows += cur; cur = mutableListOf() } }
            }
            if (cur.isNotEmpty()) rows += cur
            rows.forEachIndexed { ri, row ->
                Row(Modifier.fillMaxWidth().weight(if (row.first().rows > 1) 1.6f else 1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (row.first().span >= 4) {
                        val t = row.first()
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape((radiusDp * 0.3f).dp)).background(tile), contentAlignment = Alignment.CenterStart) {
                            if (t == CcTile.BRIGHTNESS || t == CcTile.VOLUME) Box(Modifier.fillMaxHeight().fillMaxWidth(0.6f).background(on.copy(alpha = 0.85f)))
                            Icon(painterResource(ControlCenterView.iconFor(t)), null, Modifier.padding(start = 5.dp).size(11.dp), tint = ink)
                        }
                    } else row.forEachIndexed { i, t ->
                        val lit = (ri + i) % 3 == 0
                        Box(
                            Modifier.weight(1f).fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).clip(CircleShape).background(if (lit) on else tile),
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(ControlCenterView.iconFor(t)), null, Modifier.size(11.dp), tint = if (lit) Color.White else ink) }
                    }
                }
            }
        }
    }
}

// ---------- Button actions (composed, uses the real action icons) ----------

@Composable
fun ButtonsPreview(upDouble: OverlayAction, upLong: OverlayAction, downDouble: OverlayAction, downLong: OverlayAction, modifier: Modifier, compact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        // The phone's right edge with two volume keys.
        Canvas(Modifier.width(if (compact) 26.dp else 40.dp).fillMaxHeight()) {
            val body = Color(0xFF26262B)
            drawRoundRect(body, Offset(0f, 0f), Size(size.width * 0.7f, size.height), CornerRadius(size.width * 0.3f))
            val kx = size.width * 0.7f
            val kw = size.width * 0.16f
            drawRoundRect(cs.primary, Offset(kx - 1f, size.height * 0.12f), Size(kw, size.height * 0.3f), CornerRadius(kw))
            drawRoundRect(cs.tertiary, Offset(kx - 1f, size.height * 0.58f), Size(kw, size.height * 0.3f), CornerRadius(kw))
        }
        Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceAround) {
            KeyActions("+", cs.primary, upDouble, upLong, compact)
            KeyActions("−", cs.tertiary, downDouble, downLong, compact)
        }
    }
}

@Composable
private fun KeyActions(sign: String, color: Color, double: OverlayAction, long: OverlayAction, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
        if (!compact) Box(Modifier.size(26.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(sign, color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.titleMedium)
        }
        ActionBubble("2×", double, compact)
        ActionBubble("hold", long, compact)
    }
}

@Composable
private fun ActionBubble(tag: String, a: OverlayAction, compact: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(if (compact) 32.dp else 44.dp).clip(RoundedCornerShape(14.dp)).background(if (a == OverlayAction.NONE) cs.surfaceContainerHighest else cs.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (a.icon != 0) Icon(painterResource(a.icon), a.label, Modifier.size(if (compact) 18.dp else 22.dp), tint = cs.onSecondaryContainer)
            else Text("–", color = cs.onSurfaceVariant)
        }
        if (!compact) Text(tag, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.height(16.dp))
    }
}

/** A phone-shaped box for detail-screen hero previews. */
fun Modifier.phoneSize(): Modifier = this.fillMaxHeight().aspectRatio(0.5f)

/** Top card of an overlay screen: live preview, the on switch and an optional "Try" button. */
@Composable
fun OverlayHero(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    onTry: (() -> Unit)? = null,
    label: String = "Enabled",
    stageHeight: Int = 240,
    extra: @Composable ColumnScope.() -> Unit = {},
    preview: @Composable BoxScope.() -> Unit,
) {
    app.custo.ui.components.SectionCard {
        app.custo.ui.components.PreviewStage(height = stageHeight, content = preview)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { app.custo.ui.components.SwitchRow(label, null, on, onChange = onChange) }
            if (onTry != null) { Spacer(Modifier.width(10.dp)); PreviewButton(onClick = onTry) }
        }
        extra()
    }
}
