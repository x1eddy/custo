package app.custo.feature.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun WidgetFont.compose(): FontFamily = when (this) {
    WidgetFont.SANS -> FontFamily.SansSerif
    WidgetFont.SERIF -> FontFamily.Serif
    WidgetFont.MONO -> FontFamily.Monospace
    WidgetFont.CURSIVE -> FontFamily.Cursive
}

/** In-app replica of each widget so edits preview instantly. */
@Composable
fun WidgetPreview(kind: WidgetKind, style: WidgetStyle, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val bg = if (style.themed) colorResource(if (dark) android.R.color.system_accent1_800 else android.R.color.system_accent1_100) else Color(style.bg)
    val fg = if (style.themed) colorResource(if (dark) android.R.color.system_accent1_100 else android.R.color.system_accent1_900) else Color(style.fg)
    val font = style.font.compose()
    val now by produceState(Date()) { while (true) { value = Date(); delay(1000) } }
    fun fmt(p: String) = SimpleDateFormat(p, Locale.getDefault()).format(now)

    Box(
        modifier.clip(RoundedCornerShape(style.corner.dp)).background(bg.copy(alpha = style.alpha)),
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            WidgetKind.CLOCK -> when (style.clock) {
                ClockLook.BIG -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt("H:mm"), color = fg, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 52.sp, style = TextStyle(lineHeight = 52.sp))
                    if (style.showDate) Text(fmt("EEEE, MMM d"), color = fg, fontFamily = font, fontSize = 15.sp)
                }
                ClockLook.STACKED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt("HH"), color = fg, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 56.sp, style = TextStyle(lineHeight = 50.sp))
                    Text(fmt("mm"), color = fg.copy(alpha = 0.7f), fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 56.sp, style = TextStyle(lineHeight = 50.sp))
                    if (style.showDate) Text(fmt("EEE d"), color = fg, fontFamily = font, fontSize = 14.sp)
                }
                ClockLook.MINIMAL -> Column(Modifier.fillMaxWidth().padding(start = 20.dp)) {
                    if (style.showDate) Text(fmt("EEEE"), color = fg.copy(alpha = 0.8f), fontFamily = font, fontSize = 14.sp)
                    Text(fmt("h:mm a"), color = fg, fontFamily = font, fontSize = 34.sp)
                }
            }
            WidgetKind.BATTERY -> {
                val b = readBattery(ctx)
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                    Text("${b.level}%", color = fg, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { b.level / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = fg, trackColor = fg.copy(alpha = 0.2f), drawStopIndicator = {},
                    )
                }
            }
            WidgetKind.MEDIA -> Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(fg.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, tint = fg)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Song title", color = fg, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                    Text("Artist", color = fg, fontFamily = font, fontSize = 13.sp, maxLines = 1)
                }
                listOf(Icons.Rounded.SkipPrevious, Icons.Rounded.Pause, Icons.Rounded.SkipNext).forEach {
                    Icon(it, null, tint = fg, modifier = Modifier.padding(6.dp))
                }
            }
            WidgetKind.NOTE -> Box(Modifier.fillMaxSize().padding(18.dp)) {
                Text(style.note, color = fg, fontFamily = font, fontSize = 17.sp)
            }
        }
    }
}

fun WidgetKind.previewHeight() = when (this) {
    WidgetKind.CLOCK -> 170
    WidgetKind.BATTERY -> 96
    WidgetKind.MEDIA -> 88
    WidgetKind.NOTE -> 150
}
