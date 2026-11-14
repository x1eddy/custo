package app.custo.feature.profiles

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LaunchableApp(val pkg: String, val label: String, val icon: Bitmap?)

object AppList {
    @Volatile private var cache: List<LaunchableApp>? = null

    /** Apps with a launcher icon, sorted by name. Icons are downscaled and cached in memory. */
    suspend fun load(context: Context): List<LaunchableApp> = cache ?: withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                LaunchableApp(
                    ri.activityInfo.packageName,
                    ri.loadLabel(pm).toString(),
                    runCatching { ri.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .also { cache = it }
    }
}
