package app.custo.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

enum class WidgetCategory(val label: String) {
    CLOCKS("Clocks"), INFO("Info"), HABITS("Habits"), SKY("Sun, moon & prayer"), FUN("Fun"), MEDIA("Media & photos"), TOOLS("Tools"),
}

enum class WidgetKind(
    val label: String,
    val description: String,
    val category: WidgetCategory,
    val receiver: Class<out GlanceAppWidgetReceiver>,
    val previewHeight: Int,
) {
    CLOCK("Clock", "Big, stacked or minimal digital clock", WidgetCategory.CLOCKS, ClockReceiver::class.java, 170),
    ANALOG("Analog clock", "Hands on a dial, five styles", WidgetCategory.CLOCKS, AnalogReceiver::class.java, 180),
    HOUR("Big hour", "A huge hour with small minutes", WidgetCategory.CLOCKS, HourReceiver::class.java, 150),
    WORLD("World clock", "Time in up to three cities", WidgetCategory.CLOCKS, WorldReceiver::class.java, 180),
    STOPWATCH("Stopwatch", "Tap to start and pause", WidgetCategory.CLOCKS, StopwatchReceiver::class.java, 120),
    DATE("Date", "Today, day of the year and year progress", WidgetCategory.INFO, DateReceiver::class.java, 170),
    CALENDAR("Calendar", "This month at a glance", WidgetCategory.INFO, CalendarReceiver::class.java, 230),
    COUNTDOWN("Countdown", "Days left until a date", WidgetCategory.INFO, CountdownReceiver::class.java, 150),
    BATTERY("Battery", "Charge level with a bar", WidgetCategory.INFO, BatteryReceiver::class.java, 100),
    BATTERY_RING("Battery ring", "Charge level as a ring", WidgetCategory.INFO, BatteryRingReceiver::class.java, 150),
    ALARM("Next alarm", "When your next alarm rings", WidgetCategory.INFO, AlarmReceiver::class.java, 100),
    STORAGE("Storage", "Space used on your phone", WidgetCategory.INFO, StorageReceiver::class.java, 130),
    MEDIA("Now playing", "Album art and playback controls", WidgetCategory.MEDIA, MediaReceiver::class.java, 88),
    PHOTO("Photo", "A photo you pick", WidgetCategory.MEDIA, PhotoReceiver::class.java, 200),
    NOTE("Note", "A sticky note on your home screen", WidgetCategory.TOOLS, NoteReceiver::class.java, 150),
    APPS("App shortcuts", "Up to six apps in a row", WidgetCategory.TOOLS, AppsReceiver::class.java, 100),
    TOGGLES("Quick toggles", "Torch, internet, Bluetooth and sound", WidgetCategory.TOOLS, TogglesReceiver::class.java, 90),
    QUOTE("Daily quote", "A new quote every day. Tap for another", WidgetCategory.TOOLS, QuoteReceiver::class.java, 150),
    WORK("Work countdown", "Time left in your work day", WidgetCategory.CLOCKS, WorkReceiver::class.java, 140),
    WEEK("Days till Friday", "Count down to Friday or the weekend", WidgetCategory.INFO, WeekReceiver::class.java, 140),
    WATER("Water", "Tap +1 for every glass", WidgetCategory.HABITS, WaterReceiver::class.java, 160),
    MOOD("Mood", "Log how you feel. A month in colors", WidgetCategory.HABITS, MoodReceiver::class.java, 230),
    TODO("To-do", "Tick things off right on the widget", WidgetCategory.HABITS, TodoReceiver::class.java, 200),
    STEPS("Steps", "Steps today and your goal", WidgetCategory.HABITS, StepsReceiver::class.java, 140),
    SCREEN_TIME("Screen time", "Today's total and top apps", WidgetCategory.HABITS, ScreenTimeReceiver::class.java, 170),
    MOON("Moon & sun", "Moon phase, sunrise and sunset", WidgetCategory.SKY, MoonReceiver::class.java, 160),
    PRAYER("Prayer times", "Today's times, next one highlighted", WidgetCategory.SKY, PrayerReceiver::class.java, 210),
    FISH("Wooden fish", "Tap for merit. Very calming", WidgetCategory.FUN, FishReceiver::class.java, 170),
    BUBBLES("Bubble wrap", "Pop them all, then reset", WidgetCategory.FUN, BubblesReceiver::class.java, 170),
    SWITCH("Click switch", "A satisfying switch with a counter", WidgetCategory.FUN, SwitchReceiver::class.java, 130),
    COUPLE("Together", "Two photos, two names, days together", WidgetCategory.MEDIA, CoupleReceiver::class.java, 170),
    CAROUSEL("Photo carousel", "Up to 20 photos that take turns", WidgetCategory.MEDIA, CarouselReceiver::class.java, 200);

    /** False when the phone can't support this kind at all (it's hidden from the gallery). */
    fun available(context: Context): Boolean = when (this) {
        STEPS -> StepSensor.present(context)
        else -> true
    }

    fun widget(): GlanceAppWidget = when (this) {
        CLOCK -> ClockWidget()
        ANALOG -> AnalogWidget()
        HOUR -> HourWidget()
        WORLD -> WorldWidget()
        STOPWATCH -> StopwatchWidget()
        DATE -> DateWidget()
        CALENDAR -> CalendarWidget()
        COUNTDOWN -> CountdownWidget()
        BATTERY -> BatteryWidget()
        BATTERY_RING -> BatteryRingWidget()
        ALARM -> AlarmWidget()
        STORAGE -> StorageWidget()
        MEDIA -> MediaWidget()
        PHOTO -> PhotoWidget()
        NOTE -> NoteWidget()
        APPS -> AppsWidget()
        TOGGLES -> TogglesWidget()
        QUOTE -> QuoteWidget()
        WORK -> WorkWidget()
        WEEK -> WeekWidget()
        WATER -> WaterWidget()
        MOOD -> MoodWidget()
        TODO -> TodoWidget()
        STEPS -> StepsWidget()
        SCREEN_TIME -> ScreenTimeWidget()
        MOON -> MoonWidget()
        PRAYER -> PrayerWidget()
        FISH -> FishWidget()
        BUBBLES -> BubblesWidget()
        SWITCH -> SwitchWidget()
        COUPLE -> CoupleWidget()
        CAROUSEL -> CarouselWidget()
    }
}

data class PlacedWidget(val kind: WidgetKind, val glanceId: GlanceId, val appWidgetId: Int)

object Widgets {
    val STYLE_KEY = stringPreferencesKey("style")

    /** Bumped on refresh so a running Glance session recomposes and re-reads live data. */
    internal val TICK_KEY = longPreferencesKey("tick")

    suspend fun placed(context: Context): List<PlacedWidget> {
        val gm = GlanceAppWidgetManager(context)
        return WidgetKind.entries.flatMap { kind ->
            gm.getGlanceIds(kind.widget().javaClass).map { PlacedWidget(kind, it, gm.getAppWidgetId(it)) }
        }
    }

    suspend fun find(context: Context, appWidgetId: Int): PlacedWidget? = placed(context).firstOrNull { it.appWidgetId == appWidgetId }

    suspend fun style(context: Context, id: GlanceId): WidgetStyle =
        WidgetStyle.decode(getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)[STYLE_KEY])

    suspend fun save(context: Context, w: PlacedWidget, style: WidgetStyle) {
        updateAppWidgetState(context, w.glanceId) { it[STYLE_KEY] = style.encode() }
        w.kind.widget().update(context, w.glanceId)
    }

    fun requestPin(context: Context, kind: WidgetKind): Boolean {
        val awm = AppWidgetManager.getInstance(context)
        if (!awm.isRequestPinAppWidgetSupported) return false
        return awm.requestPinAppWidget(ComponentName(context, kind.receiver), null, null)
    }

    suspend fun bump(context: Context, widget: GlanceAppWidget, id: GlanceId) {
        updateAppWidgetState(context, id) { it[TICK_KEY] = System.currentTimeMillis() }
        widget.update(context, id)
    }

    suspend fun refreshWidget(context: Context, widget: GlanceAppWidget) {
        GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass).forEach { runCatching { bump(context, widget, it) } }
    }

    suspend fun refresh(context: Context, kind: WidgetKind) = runCatching { refreshWidget(context, kind.widget()) }

    suspend fun refreshAll(context: Context) {
        WidgetKind.entries.forEach { refresh(context, it) }
    }
}
