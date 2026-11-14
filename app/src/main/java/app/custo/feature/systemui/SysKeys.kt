package app.custo.feature.systemui

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Preference keys owned by the System UI extras (kept out of data/Prefs.kt's Keys). */
object SysKeys {
    /** Comma-separated [DisableFlag] ids the user wants; re-applied after reboot / SystemUI restart. */
    val statusBarFlags = stringPreferencesKey("sys_statusbar_flags")

    /** screen_off_timeout before Caffeine raised it (absent = Caffeine is off). */
    val caffeinePrevTimeout = intPreferencesKey("sys_caffeine_prev_timeout")

    /** Last state of the Screenshot mode tile (demo mode can't be read back). */
    val demoOn = booleanPreferencesKey("sys_demo_on")
}
