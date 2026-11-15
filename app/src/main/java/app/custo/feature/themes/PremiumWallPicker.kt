package app.custo.feature.themes

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.custo.ui.components.Hint
import app.custo.ui.components.ThumbPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The "Premium" group of the wallpaper maker: dark material styles and their metal accent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PremiumWallPicker(spec: WallSpec, onChange: (WallSpec) -> Unit) {
    Text("Premium", style = MaterialTheme.typography.titleMedium)
    ThumbPicker(WallStyle.entries.filter { it.premium }, spec.style, { it.label }, { onChange(spec.copy(style = it)) }, thumbWidth = 60, thumbHeight = 96) { st, _ ->
        PremiumThumb(spec.copy(style = st))
    }
    if (spec.style.premium) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WallEdge.entries.forEach { e ->
                FilterChip(spec.edge == e, { onChange(spec.copy(edge = e)) }, { Text(e.label) }, leadingIcon = {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(e.color)))
                })
            }
        }
        Hint("Premium styles use their own dark palette; the accent sets the metal. Detail, angle and Shuffle still change them.")
    }
}

@Composable
private fun PremiumThumb(spec: WallSpec) {
    // Thumbnails ignore colors, grain and softness, so palette edits don't redraw them.
    val key = spec.copy(grain = 0f, blur = 0f, colors = emptyList())
    val bmp by produceState<Bitmap?>(null, key) {
        delay(60)
        value = withContext(Dispatchers.Default) { WallpaperStudio.render(key.copy(colors = listOf(0xFF000000.toInt())), 72, 120) }
    }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A0A0C))) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
