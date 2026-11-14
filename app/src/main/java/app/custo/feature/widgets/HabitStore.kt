package app.custo.feature.widgets

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import java.time.LocalDate

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore("widget_data")

/** Keys for the widgets' shared data (water, mood, to-do, steps). Each value is JSON. */
object WidgetKeys {
    val water = stringPreferencesKey("water_json")
    val mood = stringPreferencesKey("mood_json")
    val todo = stringPreferencesKey("todo_json")
    val steps = stringPreferencesKey("steps_json")
}

/**
 * Small local store for tracker widgets. Every widget of a kind shows the same data,
 * and every write refreshes those widgets.
 */
class HabitStore(context: Context) {
    private val app = context.applicationContext
    private val store = app.widgetDataStore

    private fun <T> read(p: Preferences, key: Preferences.Key<String>, ser: KSerializer<T>, default: T): T =
        p[key]?.let { runCatching { HabitLogic.json.decodeFromString(ser, it) }.getOrNull() } ?: default

    private fun <T> flowOf(key: Preferences.Key<String>, ser: KSerializer<T>, default: T): Flow<T> = store.data.map { read(it, key, ser, default) }

    private suspend fun <T> update(key: Preferences.Key<String>, ser: KSerializer<T>, default: T, block: (T) -> T): T {
        var out = default
        store.edit { p ->
            out = block(read(p, key, ser, default))
            p[key] = HabitLogic.json.encodeToString(ser, out)
        }
        return out
    }

    val water: Flow<WaterData> = flowOf(WidgetKeys.water, WaterData.serializer(), WaterData())
    val mood: Flow<MoodData> = flowOf(WidgetKeys.mood, MoodData.serializer(), MoodData())
    val todo: Flow<TodoData> = flowOf(WidgetKeys.todo, TodoData.serializer(), TodoData())

    suspend fun waterNow(): WaterData = water.first()
    suspend fun moodNow(): MoodData = mood.first()
    suspend fun todoNow(): TodoData = todo.first()

    suspend fun updateWater(block: (WaterData) -> WaterData) {
        update(WidgetKeys.water, WaterData.serializer(), WaterData(), block)
        Widgets.refresh(app, WidgetKind.WATER)
    }

    suspend fun addSip(drink: Drink) = updateWater { it.add(LocalDate.now().toEpochDay(), System.currentTimeMillis(), drink) }

    suspend fun setMood(day: LocalDate, mood: Mood?) {
        update(WidgetKeys.mood, MoodData.serializer(), MoodData()) {
            MoodData(if (mood == null) it.days - day.toEpochDay() else it.days + (day.toEpochDay() to mood))
        }
        Widgets.refresh(app, WidgetKind.MOOD)
    }

    suspend fun updateTodo(block: (TodoData) -> TodoData) {
        update(WidgetKeys.todo, TodoData.serializer(), TodoData(), block)
        Widgets.refresh(app, WidgetKind.TODO)
    }

    /** Records a step counter reading and returns today's steps. */
    suspend fun steps(reading: Long): Long {
        var today = 0L
        update(WidgetKeys.steps, StepsData.serializer(), StepsData()) { s ->
            val (next, n) = HabitLogic.steps(s, reading, LocalDate.now().toEpochDay())
            today = n
            next
        }
        return today
    }

    suspend fun lastSteps(): Long {
        val s = read(store.data.first(), WidgetKeys.steps, StepsData.serializer(), StepsData())
        return if (s.day == LocalDate.now().toEpochDay()) (s.last - s.base).coerceAtLeast(0) else 0
    }
}
