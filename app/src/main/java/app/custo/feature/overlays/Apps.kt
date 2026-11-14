package app.custo.feature.overlays

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LauncherApp(val pkg: String, val label: String)

/** Installed apps with a launcher icon, and a small per-app color cache. */
object Apps {

    suspend fun launcherApps(context: Context): List<LauncherApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { LauncherApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg }
            .filter { it.pkg != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun icon(context: Context, pkg: String): Drawable? = runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()

    private val colorCache = HashMap<String, Int>()

    /** The notification's own color if it set one, else the app icon's dominant color. */
    fun colorFor(context: Context, pkg: String, notificationColor: Int): Int {
        if (notificationColor != 0 && notificationColor != android.graphics.Color.BLACK) return notificationColor or 0xFF000000.toInt()
        synchronized(colorCache) { colorCache[pkg]?.let { return it } }
        val c = icon(context, pkg)?.let { iconColor(it) } ?: 0
        synchronized(colorCache) { colorCache[pkg] = c }
        return c
    }

    private fun iconColor(d: Drawable): Int {
        val size = 24
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val old = d.bounds
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        d.bounds = old
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        bmp.recycle()
        return OverlayLogic.dominantColor(px, 0)
    }
}
