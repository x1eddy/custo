package app.custo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.custoStore: DataStore<Preferences> by preferencesDataStore("custo")
val Context.originalsStore: DataStore<Preferences> by preferencesDataStore("originals")
/** Per-app profile engine state (what to put back); separate so backups and resets never touch it. */
val Context.profileStateStore: DataStore<Preferences> by preferencesDataStore("profile_engine")

/** All of Custo's own settings. Keys are grouped by feature. */
object Keys {
    // Monet
    val monetSeed = intPreferencesKey("monet_seed")
    val monetStyle = stringPreferencesKey("monet_style")

    // Overlays
    val edgeEnabled = booleanPreferencesKey("edge_enabled")
    val edgeColorMode = stringPreferencesKey("edge_color_mode") // app | accent | rainbow
    val edgeWidthDp = floatPreferencesKey("edge_width_dp")
    val edgeDurationMs = intPreferencesKey("edge_duration_ms")
    val batteryBarEnabled = booleanPreferencesKey("battery_bar_enabled")
    val batteryBarStyle = stringPreferencesKey("battery_bar_style") // top | cutout
    val batteryBarThicknessDp = floatPreferencesKey("battery_bar_thickness_dp")
    val volumePanelEnabled = booleanPreferencesKey("volume_panel_enabled")
    val volumePanelSide = stringPreferencesKey("volume_panel_side") // right | left

    // Looks (JSON list)
    val looks = stringPreferencesKey("looks_json")

    // Onboarding
    val onboarded = booleanPreferencesKey("onboarded")
}

class Prefs(private val store: DataStore<Preferences>) {
    val data: Flow<Preferences> = store.data

    fun <T> flow(key: Preferences.Key<T>, default: T): Flow<T> = store.data.map { it[key] ?: default }
    suspend fun <T> get(key: Preferences.Key<T>, default: T): T = store.data.first()[key] ?: default
    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }
    suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }
}
