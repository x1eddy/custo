package app.custo.feature.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.custo.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Exports / imports Custo's own settings. Recorded system originals are deliberately left out:
 * they describe the phone they were recorded on, and "undoing" to another phone's values would
 * leave this one in the wrong state.
 */
class BackupManager(private val context: Context, private val prefs: Prefs) {

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val settings = prefs.data.first().asMap().mapNotNull { (k, v) -> Profile.tag(v)?.let { k.name to it } }.toMap()
        val profile = Profile(createdAt = System.currentTimeMillis(), settings = settings)
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(profile.encode()) }
    }

    /** Replaces Custo's settings. Originals found in older export files are ignored. */
    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val profile = Profile.decode(text)
        prefs.edit { p ->
            p.clear()
            profile.settings.forEach { (k, tagged) ->
                when (val v = Profile.untag(tagged)) {
                    is Boolean -> p[booleanPreferencesKey(k)] = v
                    is Int -> p[intPreferencesKey(k)] = v
                    is Long -> p[longPreferencesKey(k)] = v
                    is Float -> p[floatPreferencesKey(k)] = v
                    is String -> p[stringPreferencesKey(k)] = v
                }
            }
        }
        profile.settings.size
    }
}
