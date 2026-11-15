package app.custo.feature.widgets

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

/** Live previews of the clock faces from [ClockFaces], sized with the same [TextFit] math as the widget. */
@Composable
internal fun P.FacePreview(now: Date, fmt: (String) -> String) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val font = ClockFaces.fontOf(s.clock, s.font)
        val area = ClockFaces.forFont(DpSize(maxWidth, maxHeight), font)
        val ha = when (s.hAlign) { HAlign.START -> Alignment.Start; HAlign.CENTER -> Alignment.CenterHorizontally; HAlign.END -> Alignment.End }
        if (s.clock == ClockLook.WORDS) {
            val cal = Calendar.getInstance().apply { time = now }
            val words = WordClock.words(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            val (lines, f) = WordClock.arrange(words, area, s.textScale)
            fit = f
            Column(Modifier.fillMaxWidth(), horizontalAlignment = ha) {
                words.lead?.let { T(it, ClockFaces.LEAD_SP * maxOf(1f, 0.8f / f), alpha = 0.75f, maxLines = 1, modifier = Modifier.fillMaxWidth()) }
                lines.forEach { T(it, ClockFaces.WORD_SP, bold = true, maxLines = 1, modifier = Modifier.fillMaxWidth()) }
            }
            return@BoxWithConstraints
        }
        val plan = ClockFaces.plan(s.clock, area, s.textScale, s.showDate)
        fit = plan.fit
        val family = font.compose()
        val loc = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        val date = fmt("EEE d MMM").uppercase(loc)
        when (plan.look) {
            ClockLook.FLIP -> Column(horizontalAlignment = ha) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(fmt("HH"), fmt("mm")).forEach { digits ->
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.45f))))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            T(digits, ClockFaces.FLIP_SP, bold = true, maxLines = 1, family = family)
                            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.4f)))
                            }
                        }
                    }
                }
                if (s.showDate) T(date, 13f, alpha = 0.85f, family = family, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
            ClockLook.LED -> Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = ha) {
                    Box(contentAlignment = Alignment.Center) {
                        T("88:88", ClockFaces.LED_SP, alpha = 0.13f, family = family, maxLines = 1)
                        T(fmt("HH:mm"), ClockFaces.LED_SP, family = family, maxLines = 1, glow = Color.White.copy(alpha = 0.5f))
                    }
                    if (s.showDate) T(fmt("EEE dd MMM").uppercase(loc), 13f, alpha = 0.85f, family = family, maxLines = 1)
                }
                Canvas(Modifier.matchParentSize()) {
                    val step = 3.dp.toPx()
                    val line = Color.Black.copy(alpha = 0.28f)
                    var x = step / 2
                    while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 0.8.dp.toPx()); x += step }
                    var y = step / 2
                    while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 0.8.dp.toPx()); y += step }
                }
            }
            ClockLook.NEON -> Box(contentAlignment = Alignment.Center) {
                Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(fg.copy(alpha = 0.35f), Color.Transparent))))
                Column(horizontalAlignment = ha, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) {
                    T(fmt("H:mm"), ClockFaces.NEON_SP, family = family, maxLines = 1, glow = Color.White.copy(alpha = 0.9f))
                    if (s.showDate) T(date, 13f, family = family, maxLines = 1, glow = Color.White.copy(alpha = 0.7f))
                }
            }
            ClockLook.VERTICAL -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val digits = fmt("HHmm")
                digits.forEachIndexed { i, ch ->
                    T("$ch", ClockFaces.VERTICAL_SP, bold = true, alpha = if (i >= 2) 0.7f else 1f, family = family, maxLines = 1, align = TextAlign.Center)
                }
                if (s.showDate) T(fmt("EEE").uppercase(loc), 11f, family = family, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
            else -> Column(horizontalAlignment = ha) {
                // A vertical clock in a short, wide widget falls back to hours over minutes.
                T(fmt("HH"), ClockFaces.STACKED_SP, bold = true, family = family, maxLines = 1)
                T(fmt("mm"), ClockFaces.STACKED_SP, bold = true, alpha = 0.7f, family = family, maxLines = 1)
                if (s.showDate) T(fmt("EEE d"), 14f, family = family, maxLines = 1)
            }
        }
    }
}

/** A plain widget with a drawn shape or fill (pill, circle, glass, outline, photo): the widget's own bitmap behind the content. */
@Composable
internal fun DrawnShapePreview(kind: WidgetKind, style: WidgetStyle, p: P, modifier: Modifier) {
    val ctx = LocalContext.current
    BoxWithConstraints(modifier) {
        val w = maxWidth.value; val h = maxHeight.value
        val bmp by produceState<Bitmap?>(null, style, w, h) {
            delay(30)
            value = withContext(Dispatchers.Default) { WidgetArt.background(ctx, style, w, h) }
        }
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds) }
        val (ix, iy) = WidgetShapes.contentInset(style.shape, w, h)
        val hb = when (style.hAlign) { HAlign.START -> -1f; HAlign.CENTER -> 0f; HAlign.END -> 1f }
        val vb = when (style.vAlign) { VAlign.TOP -> -1f; VAlign.CENTER -> 0f; VAlign.BOTTOM -> 1f }
        Box(
            Modifier.matchParentSize().padding(horizontal = (style.padding + ix).dp, vertical = (style.padding + iy).dp),
            contentAlignment = BiasAlignment(hb, vb),
        ) { PreviewBody(p, kind) }
    }
}
