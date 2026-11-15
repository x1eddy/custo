package app.custo.feature.widgets

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.unit.ColorProvider
import app.custo.R
import app.custo.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.math.min

private val trackColor = androidx.glance.color.ColorProvider(Color(0x33808080), Color(0x33FFFFFF))

@Composable
internal fun GBar(style: WidgetStyle, fraction: Float, color: ColorProvider = accentProvider(style)) {
    LinearProgressIndicator(
        progress = fraction.coerceIn(0f, 1f),
        modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
        color = color,
        backgroundColor = trackColor,
    )
}

/** A progress ring with something in the middle. */
@Composable
internal fun Ring(style: WidgetStyle, fraction: Float, side: Float, content: @Composable () -> Unit) {
    val ring = remember((fraction * 100).toInt()) { WidgetArt.ring(fraction) }
    Box(GlanceModifier.size(side.dp), contentAlignment = Alignment.Center) {
        Image(ImageProvider(ring), null, GlanceModifier.size(side.dp), colorFilter = ColorFilter.tint(accentProvider(style)))
        content()
    }
}

@Composable
internal fun ringSide(style: WidgetStyle, max: Float): Float {
    val size = androidx.glance.LocalSize.current
    return (min(size.width.value, size.height.value) - style.padding * 2).coerceIn(48f, max)
}

/** A rounded pill button; [pop] scales its label (a tap effect). */
@Composable
internal fun Pill(style: WidgetStyle, label: String, tap: Tap, modifier: GlanceModifier = GlanceModifier, pop: Float = 1f) {
    PressButton(
        style, tap, modifier,
        ButtonLook(
            label = label, labelSp = 14f * style.textScale * pop, labelColor = bgArgb(style), bold = true,
            shape = R.drawable.tap_shape_round, shapeColor = fgArgb(style), padH = 14f, padV = 7f,
        ),
    )
}

// ---------- Water ----------

class WaterWidget : ResponsiveWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val habits = context.container.habits
        val first = habits.waterNow()
        provideContent {
            val data by habits.water.collectAsState(first)
            Body(context, data)
        }
    }

    @Composable
    private fun Body(context: Context, data: WaterData) {
        val style = currentStyle()
        val today = LocalDate.now().toEpochDay()
        val n = data.count(today)
        val frac = n.toFloat() / data.goal.coerceAtLeast(1)
        val add = tapRun<AddSipAction>()
        val cls = sizeClass()
        // A sip pops the count and floats a "+1" up out of the ring; undo just pops the count.
        val sip = tapFrame("sip")
        val pop = TapFrames.pop(maxOf(sip, tapFrame("unsip")), 0.6f)
        Frame(style, if (cls == SizeClass.SMALL) add else null) {
            val ring = @Composable { side: Float ->
                Ring(style, frac, side) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WText(style.drink.emoji, style = style.text(side / 5f).copy(textAlign = TextAlign.Center))
                        WText("$n/${data.goal}", style = style.text(side / 6.5f * pop, bold = true).copy(textAlign = TextAlign.Center))
                    }
                    Box(GlanceModifier.size(side.dp), contentAlignment = Alignment.TopCenter) {
                        WText(
                            "+1", GlanceModifier.padding(top = (side * 0.28f * (1f - TapFrames.rise(sip))).dp),
                            style = style.text(side / 7f / style.textScale, bold = true, alpha = TapFrames.fade(sip)).copy(textAlign = TextAlign.Center), maxLines = 1,
                        )
                    }
                }
            }
            when (cls) {
                SizeClass.SMALL -> ring(ringSide(style, 150f))
                SizeClass.WIDE -> Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ring(ringSide(style, 110f))
                    Spacer(GlanceModifier.width(14.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        WText(if (n >= data.goal) "Goal reached!" else "${data.goal - n} to go", style = style.text(17f, bold = true).copy(textAlign = TextAlign.Start), maxLines = 1)
                        WText(data.today(today).takeLast(8).joinToString("") { it.drink.emoji }.ifEmpty { "Nothing yet today" }, style = style.text(13f, alpha = 0.8f).copy(textAlign = TextAlign.Start), maxLines = 1)
                        Spacer(GlanceModifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(style, "+1 ${style.drink.emoji}", add, pop = TapFrames.pop(sip, 0.4f))
                            if (n > 0) UndoButton(style)
                        }
                    }
                }
                else -> Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ring(ringSide(style, 170f) * 0.7f)
                    Spacer(GlanceModifier.height(8.dp))
                    WText(
                        data.today(today).takeLast(10).joinToString("") { it.drink.emoji }.ifEmpty { "Nothing yet today" },
                        style = style.text(14f).copy(textAlign = TextAlign.Center), maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(style, "+1 ${style.drink.label.lowercase()}", add, pop = TapFrames.pop(sip, 0.4f))
                        if (n > 0) UndoButton(style)
                    }
                }
            }
        }
    }

    @Composable
    private fun UndoButton(style: WidgetStyle) {
        PressButton(style, tapRun<UndoSipAction>(), GlanceModifier.size(36.dp), ButtonLook(icon = R.drawable.ic_undo, iconDp = 20f, tint = fgArgb(style), desc = "Undo"))
    }
}

class AddSipAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container.habits.addSip(Widgets.style(context, glanceId).drink)
        TapFx.play(context, glanceId, WaterWidget(), "sip")
    }
}

class UndoSipAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container.habits.updateWater { it.undo(LocalDate.now().toEpochDay()) }
        TapFx.play(context, glanceId, WaterWidget(), "unsip")
    }
}

class WaterReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = WaterWidget() }

// ---------- Mood ----------

class MoodWidget : ResponsiveWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val habits = context.container.habits
        val first = habits.moodNow()
        provideContent {
            val data by habits.mood.collectAsState(first)
            Body(context, id, data)
        }
    }

    @Composable
    private fun Face(style: WidgetStyle, mood: Mood?, side: Float) {
        if (mood != null && style.moodFaces) {
            val bmp = remember(mood) { WidgetArt.moodFace(mood) }
            Image(ImageProvider(bmp), mood.label, GlanceModifier.size(side.dp))
        } else {
            WText(mood?.emoji ?: "＋", style = style.text(side * 0.62f / style.textScale).copy(textAlign = TextAlign.Center))
        }
    }

    @Composable
    private fun Body(context: Context, id: GlanceId, data: MoodData) {
        val style = currentStyle()
        val today = LocalDate.now()
        val mood = data.days[today.toEpochDay()]
        val cls = sizeClass()
        val loc = context.resources.configuration.locales[0]
        Frame(style, tapStart(editIntent(context, id))) {
            when (cls) {
                SizeClass.SMALL -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Face(style, mood, 56f)
                    WText(mood?.label ?: "How are you?", style = style.text(14f, bold = true).copy(textAlign = TextAlign.Center), maxLines = 1)
                }
                SizeClass.WIDE -> Column(GlanceModifier.fillMaxWidth()) {
                    WText(if (mood == null) "How are you today?" else "Today: ${mood.label}", style = style.text(15f, bold = true), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
                    Spacer(GlanceModifier.height(8.dp))
                    MoodPicker(style, mood)
                }
                else -> Column(GlanceModifier.fillMaxWidth()) {
                    WText(today.format(DateTimeFormatter.ofPattern("MMMM", loc)) + (mood?.let { " · ${it.emoji}" } ?: ""), style = style.text(15f, bold = true), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
                    Spacer(GlanceModifier.height(4.dp))
                    MonthGrid(style, today, data)
                    if (cls == SizeClass.LARGE) {
                        Spacer(GlanceModifier.height(6.dp))
                        MoodPicker(style, mood)
                    }
                }
            }
        }
    }

    @Composable
    private fun MoodPicker(style: WidgetStyle, current: Mood?) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mood.entries.forEach { m ->
                val pick = tapRun<LogMoodAction>("mood" to m.name)
                // The face you pick pops.
                val pop = TapFrames.pop(tapFrame("mood:${m.name}"))
                val bmp = if (style.moodFaces) remember(m) { WidgetArt.moodFace(m, 64) } else null
                PressButton(
                    style, pick, GlanceModifier.defaultWeight().height(30.dp),
                    ButtonLook(
                        bitmap = bmp, iconDp = 22f * pop, label = if (bmp == null) m.emoji else null, labelSp = 16f * style.textScale * pop,
                        labelColor = fgArgb(style), fill = true, desc = m.label,
                        shape = if (m == current) R.drawable.tap_shape_round else 0, shapeColor = fgArgb(style, 0.18f),
                    ),
                )
            }
        }
    }

    @Composable
    private fun MonthGrid(style: WidgetStyle, today: LocalDate, data: MoodData) {
        val cell = style.text(11f).copy(textAlign = TextAlign.Center)
        val h = (20 * style.textScale).dp
        Column(GlanceModifier.fillMaxWidth()) { WidgetLogic.monthGrid(today, style.mondayFirst).forEach { week ->
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
                week.forEach { d ->
                    val m = if (d == 0) null else data.days[today.withDayOfMonth(d).toEpochDay()]
                    Box(GlanceModifier.defaultWeight().height(h).padding(horizontal = 1.dp), contentAlignment = Alignment.Center) {
                        var inner = GlanceModifier.fillMaxSize().cornerRadius(6.dp)
                        inner = when {
                            m != null -> inner.background(ColorProvider(Color(m.color)))
                            d != 0 -> inner.background(fgProvider(style, if (d == today.dayOfMonth) 0.3f else 0.08f))
                            else -> inner
                        }
                        Box(inner, contentAlignment = Alignment.Center) {
                            if (d != 0) WText("$d", style = if (m != null) cell.copy(color = ColorProvider(Color(0xFF1B1B1B))) else cell)
                        }
                    }
                }
            }
        } }
    }
}

class LogMoodAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val mood = parameters[MOOD]?.let { runCatching { Mood.valueOf(it) }.getOrNull() } ?: return
        context.container.habits.setMood(LocalDate.now(), mood)
        TapFx.play(context, glanceId, MoodWidget(), "mood:${mood.name}")
    }

    companion object { val MOOD = ActionParameters.Key<String>("mood") }
}

class MoodReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = MoodWidget() }

// ---------- To-do ----------

class TodoWidget : CustoWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val habits = context.container.habits
        val first = habits.todoNow()
        provideContent {
            val data by habits.todo.collectAsState(first)
            Body(context, id, data)
        }
    }

    @Composable
    private fun Body(context: Context, id: GlanceId, data: TodoData) {
        val style = currentStyle()
        val edit = tapStart(editIntent(context, id))
        Frame(style, null) {
            Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val left = data.items.count { !it.done }
                    WText("To-do", style = style.text(17f, bold = true).copy(textAlign = TextAlign.Start), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                    if (data.items.isNotEmpty()) WText("$left left", style = style.text(12f, alpha = 0.7f), maxLines = 1)
                    PressButton(style, edit, GlanceModifier.size(36.dp), ButtonLook(icon = R.drawable.ic_add, iconDp = 24f, tint = fgArgb(style), desc = "Add"))
                }
                if (data.items.isEmpty()) {
                    Box(GlanceModifier.fillMaxSize().clickable(edit.glance()), contentAlignment = Alignment.Center) {
                        WText("All clear. Tap + to add something", style = style.text(13f, alpha = 0.75f).copy(textAlign = TextAlign.Center))
                    }
                } else {
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(data.items.sortedBy { it.done }, itemId = { it.id }) { item ->
                            TodoRow(style, item)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TodoRow(style: WidgetStyle, item: Todo) {
        val toggle = tapRun<ToggleTodoAction>("id" to item.id)
        // The tick pops when you tap a row (sized inside a fixed box so the row doesn't jump).
        val pop = TapFrames.pop(tapFrame("todo:${item.id}"))
        val box = 26 * style.textScale
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(toggle.glance()), verticalAlignment = Alignment.CenterVertically) {
            Image(
                ImageProvider(if (item.done) R.drawable.ic_check_circle else R.drawable.ic_circle_outline), if (item.done) "Done" else "Not done",
                GlanceModifier.size(box.dp).padding((box * (1.3f - pop) / 2f).coerceAtLeast(0f).dp),
                colorFilter = ColorFilter.tint(fgProvider(style, if (item.done) 0.5f else 1f)),
            )
            Spacer(GlanceModifier.width(10.dp))
            val ts = style.text(15f, alpha = if (item.done) 0.5f else 1f).copy(textAlign = TextAlign.Start)
            WText(item.text, style = if (item.done) ts.copy(textDecoration = TextDecoration.LineThrough) else ts, maxLines = 2)
        }
    }
}

class ToggleTodoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ID] ?: return
        context.container.habits.updateTodo { it.toggle(id) }
        TapFx.play(context, glanceId, TodoWidget(), "todo:$id")
    }

    companion object { val ID = ActionParameters.Key<Long>("id") }
}

class TodoReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = TodoWidget() }

// ---------- Steps ----------

object StepSensor {
    fun present(ctx: Context): Boolean = ctx.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun granted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** One reading of the step counter (steps since boot), or null within a few seconds. */
    private suspend fun read(ctx: Context): Long? {
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine { cont ->
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0].toLong())
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
                }
                sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(l) }
            }
        }
    }

    sealed interface Result {
        data object NoSensor : Result
        data object NoPermission : Result
        data class Steps(val today: Long) : Result
    }

    suspend fun today(ctx: Context): Result {
        if (!present(ctx)) return Result.NoSensor
        if (!granted(ctx)) return Result.NoPermission
        val habits = ctx.container.habits
        val r = read(ctx)
        return Result.Steps(if (r != null) habits.steps(r) else habits.lastSteps())
    }
}

class StepsWidget : ResponsiveWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = StepSensor.today(context)
        provideContent {
            val tick = currentState(Widgets.TICK_KEY)
            var result by remember { mutableStateOf(first) }
            LaunchedEffect(tick) { if (tick != null) result = StepSensor.today(context) }
            Body(context, id, result)
        }
    }

    @Composable
    private fun Body(context: Context, id: GlanceId, r: StepSensor.Result) {
        val style = currentStyle()
        val refresh = tapRun<BumpAction>()
        when (r) {
            StepSensor.Result.NoSensor -> Frame(style, null) {
                WText("This phone has no step counter", style = style.text(14f).copy(textAlign = TextAlign.Center))
            }
            StepSensor.Result.NoPermission -> Frame(style, tapStart(editIntent(context, id))) {
                WText("Tap to allow step counting", style = style.text(14f).copy(textAlign = TextAlign.Center))
            }
            is StepSensor.Result.Steps -> Frame(style, refresh) {
                val frac = r.today.toFloat() / style.stepGoal.coerceAtLeast(1)
                val number = "%,d".format(r.today)
                when (sizeClass()) {
                    SizeClass.WIDE -> Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = style.hAlign.glance()) {
                        WText("Steps today", style = style.text(14f, alpha = 0.8f), modifier = GlanceModifier.fillMaxWidth())
                        WText(number, style = style.text(36f, bold = true), modifier = GlanceModifier.fillMaxWidth())
                        Spacer(GlanceModifier.height(6.dp))
                        GBar(style, frac)
                        Spacer(GlanceModifier.height(4.dp))
                        WText("Goal %,d".format(style.stepGoal), style = style.text(12f, alpha = 0.7f), modifier = GlanceModifier.fillMaxWidth())
                    }
                    else -> Ring(style, frac, ringSide(style, 170f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            WText("👟", style = style.text(16f).copy(textAlign = TextAlign.Center))
                            WText(number, style = style.text(22f, bold = true).copy(textAlign = TextAlign.Center), maxLines = 1)
                            WText("of %,d".format(style.stepGoal), style = style.text(11f, alpha = 0.7f).copy(textAlign = TextAlign.Center), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Generic "refresh me" tap. */
class BumpAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val w = Widgets.placed(context).firstOrNull { it.glanceId == glanceId } ?: return
        // A flash says "refreshing"; the new tick makes the widget reload its numbers.
        TapFx.play(context, glanceId, w.kind.widget(), TapFx.WHOLE) { it[Widgets.TICK_KEY] = System.currentTimeMillis() }
    }
}

class StepsReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = StepsWidget() }

// ---------- Screen time ----------

data class ScreenTimeInfo(val totalMs: Long, val top: List<Pair<String, Long>>)

object ScreenTime {
    fun granted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    /** Shell command that grants usage access, run through Shizuku. */
    fun grantCommand(pkg: String) = "appops set $pkg GET_USAGE_STATS allow"

    @Suppress("DEPRECATION")
    suspend fun today(ctx: Context): ScreenTimeInfo? = withContext(Dispatchers.IO) {
        if (!granted(ctx)) return@withContext null
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        // Look back a little so an app already open at midnight is counted from midnight.
        val events = usm.queryEvents(start - 3 * 3_600_000L, end) ?: return@withContext ScreenTimeInfo(0, emptyList())
        val list = ArrayList<HabitLogic.UsageEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> list += HabitLogic.UsageEvent(e.packageName, e.timeStamp, true)
                UsageEvents.Event.ACTIVITY_PAUSED -> list += HabitLogic.UsageEvent(e.packageName, e.timeStamp, false)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> list += HabitLogic.UsageEvent("__screen_off", e.timeStamp, true)
            }
        }
        val home = ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        val ignore = setOfNotNull(home, "__screen_off", "com.android.systemui")
        val per = HabitLogic.foreground(list, start, end, ignore)
        ScreenTimeInfo(per.values.sum(), per.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value })
    }
}

class ScreenTimeWidget : ResponsiveWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = ScreenTime.today(context)
        provideContent {
            val tick = currentState(Widgets.TICK_KEY)
            var info by remember { mutableStateOf(first) }
            LaunchedEffect(tick) { if (tick != null) info = ScreenTime.today(context) }
            Body(context, id, info)
        }
    }

    @Composable
    private fun Body(context: Context, id: GlanceId, info: ScreenTimeInfo?) {
        val style = currentStyle()
        if (info == null) {
            Frame(style, tapStart(editIntent(context, id))) {
                WText("Tap to allow usage access", style = style.text(14f).copy(textAlign = TextAlign.Center))
            }
            return
        }
        val cls = sizeClass()
        val icons = remember(info.top.map { it.first }) { info.top.map { WidgetArt.appIcon(context, it.first, 72) to WidgetArt.appLabel(context, it.first) } }
        Frame(style, tapRun<BumpAction>()) {
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = style.hAlign.glance()) {
                WText("Screen time today", style = style.text(13f, alpha = 0.8f), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
                WText(HabitLogic.durationText(info.totalMs), style = style.text(30f, bold = true), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
                if (cls == SizeClass.SMALL) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        icons.forEach { (icon, label) -> if (icon != null) Image(ImageProvider(icon), label, GlanceModifier.size(22.dp).padding(end = 4.dp)) }
                    }
                } else {
                    Spacer(GlanceModifier.height(6.dp))
                    val max = info.top.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
                    info.top.forEachIndexed { i, (_, ms) ->
                        val (icon, label) = icons[i]
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (icon != null) Image(ImageProvider(icon), null, GlanceModifier.size(22.dp)) else Spacer(GlanceModifier.size(22.dp))
                            Spacer(GlanceModifier.width(8.dp))
                            Column(GlanceModifier.defaultWeight()) {
                                Row(GlanceModifier.fillMaxWidth()) {
                                    WText(label, style = style.text(13f).copy(textAlign = TextAlign.Start), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                                    WText(HabitLogic.durationText(ms), style = style.text(12f, alpha = 0.75f), maxLines = 1)
                                }
                                if (cls != SizeClass.WIDE) GBar(style, ms.toFloat() / max)
                            }
                        }
                    }
                    if (info.top.isEmpty()) WText("No apps used yet today", style = style.text(13f, alpha = 0.75f))
                }
            }
        }
    }
}

class ScreenTimeReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = ScreenTimeWidget() }

/** Fills a row slot evenly. */
@Composable
internal fun RowScope.Cell(content: @Composable () -> Unit) {
    Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { content() }
}
