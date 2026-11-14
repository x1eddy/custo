package app.custo.feature.widgets

import android.app.AlarmManager
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.custo.R
import app.custo.feature.widgetsets.ThemeArt
import app.custo.feature.widgetsets.WidgetTheme
import app.custo.feature.widgetsets.WidgetThemes
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun WidgetFont.compose(): FontFamily = when (this) {
    WidgetFont.SANS -> FontFamily.SansSerif
    WidgetFont.SERIF -> FontFamily.Serif
    WidgetFont.MONO -> FontFamily.Monospace
    WidgetFont.CURSIVE -> FontFamily.Cursive
}

private fun HAlign.compose(): Alignment.Horizontal = when (this) {
    HAlign.START -> Alignment.Start; HAlign.CENTER -> Alignment.CenterHorizontally; HAlign.END -> Alignment.End
}

private fun HAlign.textAlign(): TextAlign = when (this) {
    HAlign.START -> TextAlign.Start; HAlign.CENTER -> TextAlign.Center; HAlign.END -> TextAlign.End
}

private fun HAlign.bias() = when (this) { HAlign.START -> -1f; HAlign.CENTER -> 0f; HAlign.END -> 1f }
private fun VAlign.bias() = when (this) { VAlign.TOP -> -1f; VAlign.CENTER -> 0f; VAlign.BOTTOM -> 1f }

/** Resolved colors and text helpers for one preview. */
internal class P(val s: WidgetStyle, val fg: Color, val bg: Color) {
    val font = s.font.compose()

    /** Shrinks text when the preview is drawn smaller than a real widget (gallery tiles). */
    var fit = 1f

    @Composable
    fun T(text: String, size: Float, modifier: Modifier = Modifier, bold: Boolean = false, alpha: Float = 1f, align: TextAlign = s.hAlign.textAlign(), italic: Boolean = false, color: Color = fg) {
        Text(
            text, modifier, color = color.copy(alpha = color.alpha * alpha), fontFamily = font,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontSize = (size * s.textScale * fit).sp, lineHeight = (size * s.textScale * fit * 1.15f).sp,
            textAlign = align, maxLines = 4, overflow = TextOverflow.Ellipsis,
            style = if (s.textShadow) TextStyle(shadow = Shadow(Color(s.shadowColor), Offset(2f, 2f), 3f)) else TextStyle.Default,
        )
    }
}

/** In-app replica of each widget so edits preview instantly. */
@Composable
fun WidgetPreview(kind: WidgetKind, style: WidgetStyle, modifier: Modifier = Modifier) {
    val theme = WidgetThemes.byId(style.themeId)
    if (theme != null) ThemedPreview(kind, style, theme, modifier) else PlainPreview(kind, style, modifier)
}

/** A set widget: the theme's frame and stickers drawn straight onto the canvas, content on top. */
@Composable
private fun ThemedPreview(kind: WidgetKind, style: WidgetStyle, theme: WidgetTheme, modifier: Modifier) {
    val p = P(style, Color(style.fg), Color(style.bg))
    val f = theme.frame
    val padded = !((kind == WidgetKind.PHOTO && style.photoUri != null) || (kind == WidgetKind.CAROUSEL && style.photos.isNotEmpty()))
    val k = if (padded) 1f else 0f
    val window = if (padded) 0f else 3f
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            drawIntoCanvas { ThemeArt.drawWidget(it.nativeCanvas, theme, kind, style.themeSlot, size.width, size.height, density) }
        }
        Box(
            Modifier.matchParentSize().padding(
                start = (f.outerSide + f.innerSide * k + window).dp, top = (f.outerTop + f.innerTop * k + window).dp,
                end = (f.outerSide + f.innerSide * k + window).dp, bottom = (f.outerBottom + f.innerBottom * k + window).dp,
            ),
            contentAlignment = BiasAlignment(style.hAlign.bias(), style.vAlign.bias()),
        ) { FitBody(p, kind) }
    }
}

/** Measures the content area and scales preview text so it never wraps or overflows. */
@Composable
private fun FitBody(p: P, kind: WidgetKind) {
    androidx.compose.foundation.layout.BoxWithConstraints(contentAlignment = BiasAlignment(p.s.hAlign.bias(), p.s.vAlign.bias())) {
        p.fit = minOf(1f, maxWidth.value / 240f, maxHeight.value / 110f).coerceAtLeast(0.35f)
        p.Body(kind)
    }
}

@Composable
private fun PlainPreview(kind: WidgetKind, style: WidgetStyle, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val bg = if (style.themed) colorResource(if (dark) android.R.color.system_accent1_800 else android.R.color.system_accent1_100) else Color(style.bg)
    val fg = if (style.themed) colorResource(if (dark) android.R.color.system_accent1_100 else android.R.color.system_accent1_900) else Color(style.fg)
    val p = P(style, fg, bg)
    val shape = RoundedCornerShape(style.corner.dp)
    val background = if (style.gradient && !style.themed) {
        val a = style.alpha
        remember(style.bg, style.bg2, style.gradientAngle, a) { angleBrush(Color(style.bg).copy(alpha = a), Color(style.bg2).copy(alpha = a), style.gradientAngle) }
    } else androidx.compose.ui.graphics.SolidColor(bg.copy(alpha = style.alpha))
    var m = modifier.clip(shape).background(background)
    if (style.borderWidth > 0) m = m.border(style.borderWidth.dp, Color(style.borderColor), shape)
    val padded = !((kind == WidgetKind.PHOTO && style.photoUri != null) || (kind == WidgetKind.CAROUSEL && style.photos.isNotEmpty()))
    Box(
        m.padding(if (padded) style.padding.dp else style.borderWidth.dp),
        contentAlignment = BiasAlignment(style.hAlign.bias(), style.vAlign.bias()),
    ) { FitBody(p, kind) }
}

private fun angleBrush(a: Color, b: Color, angle: Int): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val rad = angle * PI.toFloat() / 180f
        val cx = size.width / 2; val cy = size.height / 2
        val len = (abs(cos(rad)) * size.width + abs(sin(rad)) * size.height) / 2
        return LinearGradientShader(Offset(cx - cos(rad) * len, cy - sin(rad) * len), Offset(cx + cos(rad) * len, cy + sin(rad) * len), listOf(a, b))
    }
}

@Composable
private fun P.Body(kind: WidgetKind) {
    val ctx = LocalContext.current
    val loc = LocalConfiguration.current.locales[0]
    val now by produceState(Date()) { while (true) { value = Date(); delay(1000) } }
    fun fmt(pattern: String, zone: String? = null) = SimpleDateFormat(pattern, loc).apply { zone?.let { timeZone = TimeZone.getTimeZone(it) } }.format(now)
    val ha = s.hAlign.compose()
    val wide = Modifier.fillMaxWidth()

    when (kind) {
        WidgetKind.CLOCK -> when (s.clock) {
            ClockLook.BIG -> Column(horizontalAlignment = ha) {
                T(fmt("H:mm"), 56f, bold = true)
                if (s.showDate) T(fmt("EEEE, MMM d"), 15f)
            }
            ClockLook.STACKED -> Column(horizontalAlignment = ha) {
                T(fmt("HH"), 60f, bold = true)
                T(fmt("mm"), 60f, bold = true, alpha = 0.7f)
                if (s.showDate) T(fmt("EEE d"), 14f)
            }
            ClockLook.MINIMAL -> Column(horizontalAlignment = ha) {
                if (s.showDate) T(fmt("EEEE"), 14f, alpha = 0.8f)
                T(fmt("h:mm a"), 34f)
            }
        }
        WidgetKind.HOUR -> Row(verticalAlignment = Alignment.CenterVertically) {
            T(fmt("HH"), 96f, bold = true)
            Spacer(Modifier.width(8.dp))
            Column {
                T(fmt("mm"), 40f, bold = true, alpha = 0.75f, align = TextAlign.Start)
                if (s.showDate) T(fmt("EEE d MMM"), 14f, align = TextAlign.Start)
            }
        }
        WidgetKind.ANALOG -> AnalogFace(now)
        WidgetKind.WORLD -> Column(wide, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.zones.take(3).forEach { z ->
                Row(wide, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(cityName(z), 15f, align = TextAlign.Start)
                        T(fmt("EEE", z), 12f, alpha = 0.7f, align = TextAlign.Start)
                    }
                    T(fmt("H:mm", z), 26f, bold = true)
                }
            }
        }
        WidgetKind.STOPWATCH -> Column(horizontalAlignment = ha) {
            T("00:00", 44f, bold = true)
            T("Tap to start", 13f, alpha = 0.75f)
        }
        WidgetKind.DATE -> Column(wide, horizontalAlignment = ha) {
            val today = LocalDate.now()
            T(today.format(DateTimeFormatter.ofPattern("EEEE", loc)), 15f, alpha = 0.8f, modifier = wide)
            T("${today.dayOfMonth}", 54f, bold = true, modifier = wide)
            T(today.format(DateTimeFormatter.ofPattern("MMMM yyyy", loc)), 15f, modifier = wide)
            if (s.yearProgress) {
                Spacer(Modifier.height(8.dp))
                T("Day ${today.dayOfYear} · ${(WidgetLogic.yearProgress(today) * 100).roundToInt()}% of the year", 12f, alpha = 0.75f, modifier = wide)
                Spacer(Modifier.height(4.dp))
                Bar(WidgetLogic.yearProgress(today))
            }
        }
        WidgetKind.CALENDAR -> Column(wide) {
            val today = LocalDate.now()
            T(today.format(DateTimeFormatter.ofPattern("MMMM yyyy", loc)), 16f, bold = true, modifier = wide)
            Spacer(Modifier.height(6.dp))
            Row(wide) { WidgetLogic.weekdayInitials(s.mondayFirst, loc).forEach { T(it, 13f, alpha = 0.6f, align = TextAlign.Center, modifier = Modifier.weight(1f)) } }
            WidgetLogic.monthGrid(today, s.mondayFirst).forEach { week ->
                Row(wide.height((22 * s.textScale).dp), verticalAlignment = Alignment.CenterVertically) {
                    week.forEach { d ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (d == today.dayOfMonth) {
                                Box(Modifier.size((22 * s.textScale).dp).clip(CircleShape).background(fg), contentAlignment = Alignment.Center) {
                                    T("$d", 13f, align = TextAlign.Center, color = bg)
                                }
                            } else T(if (d == 0) "" else "$d", 13f, align = TextAlign.Center)
                        }
                    }
                }
            }
        }
        WidgetKind.COUNTDOWN -> Column(wide, horizontalAlignment = ha) {
            val today = LocalDate.now()
            val target = WidgetLogic.countdownTarget(s.countdownDay, today)
            val days = WidgetLogic.daysUntil(target, today)
            T(s.countdownTitle.ifBlank { "Countdown" }, 15f, alpha = 0.8f, modifier = wide)
            if (days > 1) {
                T("$days", 48f, bold = true, modifier = wide)
                T("days left", 14f, modifier = wide)
            } else T(WidgetLogic.countdownText(days), 32f, bold = true, modifier = wide)
            T(target.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", loc)), 12f, alpha = 0.7f, modifier = wide)
        }
        WidgetKind.BATTERY -> Column(wide, horizontalAlignment = ha) {
            val b = remember { readBattery(ctx) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("${b.level}%", 30f, bold = true)
                if (b.charging) Icon(Icons.Rounded.Bolt, null, tint = fg)
            }
            Spacer(Modifier.height(8.dp))
            Bar(b.level / 100f)
        }
        WidgetKind.BATTERY_RING -> {
            val b = remember { readBattery(ctx) }
            Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = size.minDimension * 0.11f
                    val inset = Offset(sw / 2, sw / 2)
                    val arc = Size(size.minDimension - sw, size.minDimension - sw)
                    drawArc(fg.copy(alpha = 0.25f), 0f, 360f, false, inset, arc, style = Stroke(sw))
                    drawArc(fg, -90f, 360f * b.level / 100f, false, inset, arc, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (b.charging) Icon(Icons.Rounded.Bolt, null, tint = fg, modifier = Modifier.size(18.dp))
                    if (s.showPercent) T("${b.level}%", 24f, bold = true, align = TextAlign.Center)
                }
            }
        }
        WidgetKind.ALARM -> Row(verticalAlignment = Alignment.CenterVertically) {
            val next = remember { ctx.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime }
            Icon(Icons.Rounded.Alarm, null, tint = fg, modifier = Modifier.size((30 * s.textScale).dp))
            Spacer(Modifier.width(12.dp))
            Column {
                if (next == null) T("No alarm set", 18f, bold = true, align = TextAlign.Start)
                else {
                    T(android.text.format.DateFormat.getTimeFormat(ctx).format(Date(next)), 28f, bold = true, align = TextAlign.Start)
                    T(SimpleDateFormat("EEE d MMM", loc).format(Date(next)), 13f, alpha = 0.8f, align = TextAlign.Start)
                }
            }
        }
        WidgetKind.STORAGE -> Column(wide, horizontalAlignment = ha) {
            val st = remember { readStorage() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SdStorage, null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                T("Storage", 14f, alpha = 0.8f)
            }
            T("${WidgetLogic.bytesText(st.used)} used", 22f, bold = true, modifier = wide)
            T("of ${WidgetLogic.bytesText(st.total)}", 12f, alpha = 0.7f, modifier = wide)
            Spacer(Modifier.height(6.dp))
            Bar(st.used.toFloat() / st.total.coerceAtLeast(1))
        }
        WidgetKind.MEDIA -> Row(wide, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(fg.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = fg)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T("Song title", 16f, bold = true, align = TextAlign.Start)
                T("Artist", 13f, align = TextAlign.Start)
            }
            listOf(Icons.Rounded.SkipPrevious, Icons.Rounded.Pause, Icons.Rounded.SkipNext).forEach {
                Icon(it, null, tint = fg, modifier = Modifier.padding(6.dp))
            }
        }
        WidgetKind.PHOTO -> {
            val bmp by produceState<Bitmap?>(null, s.photoUri) { value = withContext(Dispatchers.IO) { WidgetArt.photo(ctx, s.photoUri, 600) } }
            val b = bmp
            if (b != null) {
                Image(b.asImageBitmap(), "Photo", Modifier.fillMaxSize().clip(RoundedCornerShape((s.corner - s.borderWidth).coerceAtLeast(0).dp)), contentScale = ContentScale.Crop)
            } else if (s.themeId != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                T("Tap to add a photo", 11f, alpha = 0.8f, align = TextAlign.Center)
            } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Image, null, tint = fg, modifier = Modifier.size(36.dp))
                T("Tap to pick a photo", 13f, align = TextAlign.Center)
            }
        }
        WidgetKind.NOTE -> T(s.note, 17f, modifier = wide)
        WidgetKind.APPS -> {
            val apps = s.apps.take(6)
            val icons by produceState<List<Pair<Bitmap?, String>>>(emptyList(), apps) {
                value = withContext(Dispatchers.IO) { apps.map { WidgetArt.appIcon(ctx, it) to WidgetArt.appLabel(ctx, it) } }
            }
            if (apps.isEmpty()) T("Tap to pick apps", 14f)
            else Row(wide, verticalAlignment = Alignment.CenterVertically) {
                icons.forEach { (icon, label) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (icon != null) Image(icon.asImageBitmap(), label, Modifier.size((44 * s.textScale.coerceAtMost(1.3f)).dp))
                        if (s.showLabels) T(label, 11f, align = TextAlign.Center)
                    }
                }
            }
        }
        WidgetKind.TOGGLES -> Row(wide, verticalAlignment = Alignment.CenterVertically) {
            listOf<ImageVector>(Icons.Rounded.FlashlightOn, Icons.Rounded.Wifi, Icons.Rounded.Bluetooth, Icons.AutoMirrored.Rounded.VolumeUp).forEach { icon ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size((44 * s.textScale).dp).clip(CircleShape).background(fg.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = fg, modifier = Modifier.size((22 * s.textScale).dp))
                    }
                }
            }
        }
        WidgetKind.WORK, WidgetKind.WEEK, WidgetKind.WATER, WidgetKind.MOOD, WidgetKind.TODO, WidgetKind.STEPS,
        WidgetKind.SCREEN_TIME, WidgetKind.MOON, WidgetKind.PRAYER, WidgetKind.FISH, WidgetKind.BUBBLES,
        WidgetKind.SWITCH, WidgetKind.COUPLE, WidgetKind.CAROUSEL -> NewBody(kind)
        WidgetKind.BANNER -> Column(wide, horizontalAlignment = ha) {
            T(s.bannerText, 28f, bold = true, modifier = wide)
            if (s.bannerSub.isNotBlank()) T(s.bannerSub, 13f, alpha = 0.8f, modifier = wide)
        }
        WidgetKind.QUOTE -> Column(wide, horizontalAlignment = ha) {
            val (q, a) = WidgetLogic.QUOTES[WidgetLogic.quoteIndex(LocalDate.now(), 0, WidgetLogic.QUOTES.size)]
            T("“$q”", 17f, italic = true, modifier = wide)
            if (s.showAuthor) {
                Spacer(Modifier.height(6.dp))
                T("— $a", 13f, alpha = 0.75f, modifier = wide)
            }
        }
    }
}

@Composable
internal fun P.Bar(fraction: Float) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = fg, trackColor = fg.copy(alpha = 0.2f), drawStopIndicator = {},
    )
}

@Composable
private fun P.AnalogFace(now: Date) {
    val cal = Calendar.getInstance().apply { time = now }
    val min = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
    val hour = cal.get(Calendar.HOUR) + min / 60f
    val bold = s.dial == DialStyle.BOLD
    val tint = ColorFilter.tint(fg)
    Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
        val full = Modifier.fillMaxSize()
        Image(painterResource(s.dial.dialRes()), null, full, colorFilter = tint)
        Image(painterResource(if (bold) R.drawable.hand_hour_bold else R.drawable.hand_hour), null, full.rotate(hour * 30f), colorFilter = tint)
        Image(painterResource(if (bold) R.drawable.hand_minute_bold else R.drawable.hand_minute), null, full.rotate(min * 6f), colorFilter = tint)
        if (s.seconds) {
            val accent = if (s.themed) colorResource(R.color.widget_accent) else fg
            Image(painterResource(R.drawable.hand_second), null, full.rotate(cal.get(Calendar.SECOND) * 6f), colorFilter = ColorFilter.tint(accent))
        }
    }
}

