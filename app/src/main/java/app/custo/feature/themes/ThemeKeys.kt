package app.custo.feature.themes

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Preference keys owned by the Themes feature (live wallpaper and day/night schedule). */
object ThemeKeys {
    // Live wallpaper
    val liveSpec = stringPreferencesKey("live_spec_json")
    val liveAnimate = booleanPreferencesKey("live_animate")
    val liveFps = intPreferencesKey("live_fps")
    val liveSpeed = floatPreferencesKey("live_speed")
    val liveTimeOfDay = booleanPreferencesKey("live_time_of_day")
    val liveBattery = booleanPreferencesKey("live_battery")
    val liveParallax = booleanPreferencesKey("live_parallax")
    val liveUnlockedOnly = booleanPreferencesKey("live_unlocked_only")

    // Day / night schedule
    val schedOn = booleanPreferencesKey("sched_on")
    val schedDay = stringPreferencesKey("sched_day_look")
    val schedNight = stringPreferencesKey("sched_night_look")
    val schedDayAt = intPreferencesKey("sched_day_at")
    val schedNightAt = intPreferencesKey("sched_night_at")
    val schedColors = booleanPreferencesKey("sched_colors")
    val schedLast = stringPreferencesKey("sched_last_slot")
}
