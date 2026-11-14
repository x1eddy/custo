package app.custo.feature.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import app.custo.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.min

// ---------- Wooden fish ----------

object Fish {
    val DAY = longPreferencesKey("fish_day")
    val TODAY = intPreferencesKey("fish_today")
    val TOTAL = longPreferencesKey("fish_total")
    val HIT = booleanPreferencesKey("fish_hit")
    internal val raised by lazy { WidgetArt.woodenFish(false) }
    internal val struck by lazy { WidgetArt.woodenFish(true) }
}

class FishWidget : ResponsiveWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val todayDay = LocalDate.now().toEpochDay()
        val count = if (currentState(Fish.DAY) == todayDay) currentState(Fish.TODAY) ?: 0 else 0
        val total = currentState(Fish.TOTAL) ?: 0L
        val hit = currentState(Fish.HIT) == true
        val cls = sizeClass()
        val size = LocalSize.current
        val side = (min(size.width.value, size.height.value) - style.padding * 2 - if (cls == SizeClass.WIDE) 0f else 44f).coerceIn(48f, 170f)
        Frame(style, actionRunCallback<FishAction>()) {
            val label = @Composable {
                Column(horizontalAlignment = if (cls == SizeClass.WIDE) Alignment.Start else Alignment.CenterHorizontally) {
                    val align = if (cls == SizeClass.WIDE) TextAlign.Start else TextAlign.Center
                    WText(if (count == 0) "Tap the fish" else "Merit +1", style = style.text(16f, bold = true).copy(textAlign = align), maxLines = 1)
                    WText("Today $count", style = style.text(12f, alpha = 0.75f).copy(textAlign = align), maxLines = 1)
                    if (cls == SizeClass.WIDE || cls == SizeClass.LARGE) WText("All time $total", style = style.text(12f, alpha = 0.6f).copy(textAlign = align), maxLines = 1)
                }
            }
            if (cls == SizeClass.WIDE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(if (hit) Fish.struck else Fish.raised), "Wooden fish", GlanceModifier.size(side.dp))
                    Spacer(GlanceModifier.width(12.dp))
                    label()
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(ImageProvider(if (hit) Fish.struck else Fish.raised), "Wooden fish", GlanceModifier.size(side.dp))
                    label()
                }
            }
        }
    }
}

class FishAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val today = LocalDate.now().toEpochDay()
        updateAppWidgetState(context, glanceId) { p ->
            val n = if (p[Fish.DAY] == today) p[Fish.TODAY] ?: 0 else 0
            p[Fish.DAY] = today
            p[Fish.TODAY] = n + 1
            p[Fish.TOTAL] = (p[Fish.TOTAL] ?: 0L) + 1
            p[Fish.HIT] = p[Fish.HIT] != true
        }
        FishWidget().update(context, glanceId)
    }
}

class FishReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = FishWidget() }

// ---------- Bubble wrap ----------

object Bubbles {
    val MASK = longPreferencesKey("bubble_mask")
    val ROUNDS = intPreferencesKey("bubble_rounds")

    /** Columns × rows for each size. */
    fun grid(cls: SizeClass): Pair<Int, Int> = when (cls) {
        SizeClass.SMALL -> 4 to 4
        SizeClass.WIDE -> 8 to 3
        SizeClass.TALL -> 4 to 8
        SizeClass.LARGE -> 6 to 6
    }
}

class BubblesWidget : ResponsiveWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val mask = currentState(Bubbles.MASK) ?: 0L
        val cls = sizeClass()
        val (cols, rows) = Bubbles.grid(cls)
        val count = cols * rows
        val done = HabitLogic.allPopped(mask, count)
        val left = (0 until count).count { !HabitLogic.popped(mask, it) }
        val reset = actionRunCallback<BubbleAction>(actionParametersOf(BubbleAction.INDEX to -1))
        val size = LocalSize.current
        val header = cls != SizeClass.SMALL
        val cell = min(
            (size.width.value - style.padding * 2) / cols,
            (size.height.value - style.padding * 2 - if (header) 30f else 0f) / rows,
        ).coerceIn(14f, 56f)
        Frame(style, if (done) reset else null) {
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (header) {
                    Row(GlanceModifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                        WText(if (done) "All popped! Tap to reset" else "$left left", style = style.text(14f, bold = true).copy(textAlign = TextAlign.Start), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                        Image(ImageProvider(R.drawable.ic_reset), "Reset", GlanceModifier.size(30.dp).padding(5.dp).clickable(reset), colorFilter = ColorFilter.tint(fgProvider(style)))
                    }
                }
                if (done && !header) {
                    WText("All popped!\nTap to reset", style = style.text(15f, bold = true).copy(textAlign = TextAlign.Center))
                } else {
                    for (r in 0 until rows) {
                        Row {
                            for (c in 0 until cols) {
                                val i = r * cols + c
                                val popped = HabitLogic.popped(mask, i)
                                var m = GlanceModifier.size(cell.dp).padding((cell * 0.06f).dp)
                                if (!popped) m = m.clickable(actionRunCallback<BubbleAction>(actionParametersOf(BubbleAction.INDEX to i)))
                                Image(
                                    ImageProvider(if (popped) R.drawable.bubble_popped else R.drawable.bubble_full), null, m,
                                    colorFilter = ColorFilter.tint(fgProvider(style)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class BubbleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val i = parameters[INDEX] ?: return
        updateAppWidgetState(context, glanceId) { p ->
            if (i < 0) {
                p[Bubbles.MASK] = 0L
                p[Bubbles.ROUNDS] = (p[Bubbles.ROUNDS] ?: 0) + 1
            } else p[Bubbles.MASK] = HabitLogic.pop(p[Bubbles.MASK] ?: 0L, i)
        }
        BubblesWidget().update(context, glanceId)
    }

    companion object { val INDEX = ActionParameters.Key<Int>("i") }
}

class BubblesReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = BubblesWidget() }

// ---------- Click switch ----------

object ClickSwitch {
    val ON = booleanPreferencesKey("switch_on")
    val COUNT = longPreferencesKey("switch_count")
}

class SwitchWidget : ResponsiveWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val on = currentState(ClickSwitch.ON) == true
        val count = currentState(ClickSwitch.COUNT) ?: 0L
        val size = LocalSize.current
        val w = (size.width.value - style.padding * 2).coerceIn(80f, 160f)
        val h = w * 0.48f
        val toggle = actionRunCallback<SwitchAction>()
        Frame(style, toggle) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    GlanceModifier.width(w.dp).height(h.dp).cornerRadius((h / 2).dp)
                        .background(fgProvider(style, if (on) 1f else 0.2f)).padding((h * 0.1f).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (on) Spacer(GlanceModifier.defaultWeight())
                    Box(
                        GlanceModifier.size((h * 0.8f).dp).cornerRadius((h * 0.4f).dp).background(if (on) bgProvider(style, 1f) else fgProvider(style)),
                        contentAlignment = Alignment.Center,
                    ) {
                        WText(if (on) "I" else "O", style = style.text(h * 0.22f / style.textScale, bold = true).copy(textAlign = TextAlign.Center, color = if (on) fgProvider(style) else bgProvider(style, 1f)))
                    }
                    if (!on) Spacer(GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.height(8.dp))
                WText(if (count == 0L) "Go on, click it" else "Clicked $count ${if (count == 1L) "time" else "times"}", style = style.text(13f, alpha = 0.8f).copy(textAlign = TextAlign.Center), maxLines = 1)
            }
        }
    }
}

class SwitchAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { p ->
            p[ClickSwitch.ON] = p[ClickSwitch.ON] != true
            p[ClickSwitch.COUNT] = (p[ClickSwitch.COUNT] ?: 0L) + 1
        }
        SwitchWidget().update(context, glanceId)
    }
}

class SwitchReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = SwitchWidget() }

// ---------- Couple ----------

class CoupleWidget : ResponsiveWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val p1 = remember(style.photoUri) { WidgetArt.photo(context, style.photoUri, 300) }
        val p2 = remember(style.photoUri2) { WidgetArt.photo(context, style.photoUri2, 300) }
        val cls = sizeClass()
        val today = LocalDate.now()
        val loc = context.resources.configuration.locales[0]
        Frame(style, actionStartActivity(editIntent(context, id))) {
            if (style.sinceDay == 0L) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WText("❤️", style = style.text(30f).copy(textAlign = TextAlign.Center))
                    WText("Tap to add your names, photos and date", style = style.text(13f).copy(textAlign = TextAlign.Center))
                }
                return@Frame
            }
            val since = LocalDate.ofEpochDay(style.sinceDay)
            val days = HabitLogic.daysTogether(since, today)
            val avatar = if (cls == SizeClass.SMALL) 44f else 60f
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(style, p1, style.name1, avatar)
                    WText(" ❤️ ", style = style.text(avatar / 3f / style.textScale).copy(textAlign = TextAlign.Center))
                    Avatar(style, p2, style.name2, avatar)
                }
                Spacer(GlanceModifier.height(6.dp))
                val names = listOf(style.name1, style.name2).filter { it.isNotBlank() }.joinToString(" & ")
                if (names.isNotEmpty() && cls != SizeClass.SMALL) WText(names, style = style.text(14f, alpha = 0.85f).copy(textAlign = TextAlign.Center), maxLines = 1)
                WText("Together $days ${if (days == 1L) "day" else "days"}", style = style.text(if (cls == SizeClass.SMALL) 15f else 19f, bold = true).copy(textAlign = TextAlign.Center), maxLines = 1)
                if (cls == SizeClass.LARGE || cls == SizeClass.TALL || cls == SizeClass.WIDE) {
                    val next = HabitLogic.nextAnniversary(since, today)
                    val years = HabitLogic.yearsAt(since, next)
                    val inDays = WidgetLogic.daysUntil(next, today)
                    val text = when {
                        years <= 0 -> "Since ${since.format(DateTimeFormatter.ofPattern("d MMM yyyy", loc))}"
                        inDays == 0L -> "Happy $years-year anniversary! 🎉"
                        else -> "$years ${if (years == 1) "year" else "years"} in $inDays ${if (inDays == 1L) "day" else "days"}"
                    }
                    WText(text, style = style.text(12f, alpha = 0.7f).copy(textAlign = TextAlign.Center), maxLines = 1)
                }
            }
        }
    }

    @Composable
    private fun Avatar(style: WidgetStyle, bmp: android.graphics.Bitmap?, name: String, side: Float) {
        if (bmp != null) {
            Image(ImageProvider(bmp), name, GlanceModifier.size(side.dp).cornerRadius((side / 2).dp), contentScale = ContentScale.Crop)
        } else {
            Box(GlanceModifier.size(side.dp).cornerRadius((side / 2).dp).background(fgProvider(style, 0.15f)), contentAlignment = Alignment.Center) {
                WText(name.take(1).uppercase().ifEmpty { "?" }, style = style.text(side / 2.6f / style.textScale, bold = true).copy(textAlign = TextAlign.Center))
            }
        }
    }
}

class CoupleReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = CoupleWidget() }

// ---------- Photo carousel ----------

class CarouselWidget : ResponsiveWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val photos = style.photos
        val index = HabitLogic.carousel(currentState(Carousel.INDEX) ?: 0, 0, photos.size)
        val uri = photos.getOrNull(index)
        val bmp = remember(uri) { WidgetArt.photo(context, uri, 640) }
        LaunchedEffect(photos.size > 1) { if (photos.size > 1) WidgetAlarms.startCarousel(context) }
        val action = if (photos.size > 1) actionRunCallback<NextPhotoAction>() else actionStartActivity(editIntent(context, id))
        Frame(style, action, padded = bmp == null) {
            if (bmp != null) {
                Box(GlanceModifier.fillMaxSize()) {
                    Image(
                        ImageProvider(bmp), "Photo ${index + 1} of ${photos.size}",
                        GlanceModifier.fillMaxSize().padding(style.borderWidth.dp).cornerRadius((style.corner - style.borderWidth).coerceAtLeast(0).dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(ImageProvider(R.drawable.ic_photo), null, GlanceModifier.size(36.dp), colorFilter = ColorFilter.tint(fgProvider(style)))
                    Spacer(GlanceModifier.height(6.dp))
                    WText("Tap to pick up to 20 photos", style = style.text(13f).copy(textAlign = TextAlign.Center))
                }
            }
        }
    }
}

class NextPhotoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val n = Widgets.style(context, glanceId).photos.size
        updateAppWidgetState(context, glanceId) { p ->
            p[Carousel.INDEX] = HabitLogic.carousel(p[Carousel.INDEX] ?: 0, 1, n)
            p[Carousel.AT] = System.currentTimeMillis()
        }
        CarouselWidget().update(context, glanceId)
    }
}

class CarouselReceiver : CustoReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CarouselWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetAlarms.startCarousel(context)
    }
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetAlarms.stopCarousel(context)
    }
}
