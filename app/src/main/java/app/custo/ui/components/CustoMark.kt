package app.custo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val MarkTeal = Color(0xFF00C2A8)
val MarkIndigo = Color(0xFF5B6CFF)

/**
 * The Custo mark (same geometry as the launcher icon): a 2×2 grid of squircles, one tilted,
 * the fourth a knob. With [animate] the pieces pop in one after another.
 */
@Composable
fun CustoMark(modifier: Modifier = Modifier, animate: Boolean = false) {
    @Suppress("NAME_SHADOWING") val animate = animate && !app.custo.ui.theme.LocalReduceMotion.current
    val parts = remember { List(4) { Animatable(if (animate) 0f else 1f) } }
    val tilt = remember { Animatable(if (animate) -40f else 18f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        parts.forEachIndexed { i, a ->
            launch { delay(90L * i); a.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        }
        delay(200)
        tilt.animateTo(18f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow))
    }
    Canvas(modifier) {
        // Artwork lives in a 48×48 box (icon coordinates 30..78).
        val u = size.minDimension / 48f
        val brush = Brush.linearGradient(listOf(MarkTeal, MarkIndigo), Offset.Zero, Offset(size.width, size.height))
        val sq = Size(21f * u, 21f * u)
        val r = CornerRadius(7f * u)
        fun square(x: Float, y: Float, p: Float, angle: Float = 0f) {
            val center = Offset((x + 10.5f) * u, (y + 10.5f) * u)
            scale(p, center) { rotate(angle, center) { drawRoundRect(brush, Offset(x * u, y * u), sq, r) } }
        }
        square(0f, 0f, parts[0].value)
        square(27f, 0f, parts[1].value, tilt.value)
        square(0f, 27f, parts[2].value)
        val knob = Offset(37.5f * u, 37.5f * u)
        scale(parts[3].value, knob) {
            drawCircle(brush, 11f * u, knob)
            drawCircle(Color.White, 7f * u, knob)
        }
    }
}

/** Fade + rise in, staggered by [index], the first time an item appears. */
fun Modifier.enterAnimation(index: Int): Modifier = composed {
    // Reduce motion: content is simply there.
    if (app.custo.ui.theme.LocalReduceMotion.current) return@composed Modifier
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(45L * index.coerceAtMost(8))
        p.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow))
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 36f
    }
}
