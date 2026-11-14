package app.custo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.custo.feature.systemui.Tonal

val PRESET_COLORS = listOf(
    0xFF6D4AFF, 0xFF3D5AFE, 0xFF0091EA, 0xFF00B8D4, 0xFF00BFA5, 0xFF2E7D32, 0xFF64DD17,
    0xFFFFD600, 0xFFFF9100, 0xFFFF3D00, 0xFFD50000, 0xFFC51162, 0xFFAA00FF, 0xFF795548, 0xFF607D8B,
).map { Color(it) }

/** Preset swatches plus hue / saturation / lightness sliders. */
@Composable
fun ColorPicker(color: Color, onChange: (Color) -> Unit, showSliders: Boolean = true) {
    val (h, s, l) = Tonal.toHsl(color.toArgb())
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(PRESET_COLORS) { c -> ColorDot(c, selected = c.toArgb() == color.toArgb()) { onChange(c) } }
        }
        if (showSliders) {
            GradientSlider("Hue", h / 360f, Brush.horizontalGradient((0..6).map { Color(Tonal.fromHsl(it * 60f % 360f, 1f, 0.5f)) })) {
                onChange(Color(Tonal.fromHsl(it * 359.9f, s, l)))
            }
            GradientSlider("Saturation", s, Brush.horizontalGradient(listOf(Color(Tonal.fromHsl(h, 0f, l)), Color(Tonal.fromHsl(h, 1f, l))))) {
                onChange(Color(Tonal.fromHsl(h, it, l)))
            }
            GradientSlider("Lightness", l, Brush.horizontalGradient(listOf(Color.Black, Color(Tonal.fromHsl(h, s, 0.5f)), Color.White))) {
                onChange(Color(Tonal.fromHsl(h, s, it)))
            }
        }
    }
}

@Composable
private fun GradientSlider(label: String, value: Float, brush: Brush, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Box(Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.extraSmall).background(brush).align(androidx.compose.ui.Alignment.Center))
            Slider(
                value = value.coerceIn(0f, 1f),
                onValueChange = onChange,
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
            )
        }
    }
}

@Composable
fun ColorStrip(colors: List<Color>, modifier: Modifier = Modifier) {
    Row(modifier.clip(MaterialTheme.shapes.small)) {
        colors.forEach { Box(Modifier.weight(1f).height(28.dp).background(it)) }
    }
}
