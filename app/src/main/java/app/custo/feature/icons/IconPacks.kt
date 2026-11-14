package app.custo.feature.icons

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class IconPack(val pkg: String, val label: String, val icon: Bitmap?)

/** Third-party icon packs in the ADW / Nova / Lawnchair format. */
object IconPacks {
    /** Intent actions icon packs register so launchers can find them. Also declared in <queries>. */
    val ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME",
    )

    private val filters = HashMap<String, AppFilter>()
    private val icons = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun installed(context: Context): List<IconPack> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        ACTIONS.flatMap { action -> pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .map { ai ->
                IconPack(
                    ai.packageName,
                    ai.loadLabel(pm).toString(),
                    runCatching { IconRenderer.drawableToBitmap(ai.loadIcon(pm), 96) }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun filter(context: Context, packPkg: String): AppFilter = withContext(Dispatchers.IO) {
        synchronized(filters) { filters[packPkg] } ?: load(context, packPkg).also { synchronized(filters) { filters[packPkg] = it } }
    }

    @SuppressLint("DiscouragedApi") // icon packs are looked up by name: that's the format
    private fun load(context: Context, packPkg: String): AppFilter {
        val res = runCatching { context.packageManager.getResourcesForApplication(packPkg) }.getOrNull() ?: return AppFilter.EMPTY
        // 1) Compiled res/xml/appfilter.xml
        val id = res.getIdentifier("appfilter", "xml", packPkg)
        if (id != 0) {
            val parsed = runCatching {
                res.getXml(id).use { xml ->
                    val events = ArrayList<Pair<String, Map<String, String>>>()
                    while (xml.next() != XmlPullParser.END_DOCUMENT) {
                        if (xml.eventType == XmlPullParser.START_TAG) {
                            events += xml.name to (0 until xml.attributeCount).associate { xml.getAttributeName(it) to xml.getAttributeValue(it) }
                        }
                    }
                    AppFilter.fromEvents(events.asSequence())
                }
            }.getOrNull()
            if (parsed != null && parsed.byPackage.isNotEmpty()) return parsed
        }
        // 2) Plain-text assets/appfilter.xml
        return runCatching {
            res.assets.open("appfilter.xml").bufferedReader().use { AppFilter.parse(it.readText()) }
        }.getOrDefault(AppFilter.EMPTY)
    }

    /** A pack drawable by name, as a bitmap (cached). */
    @SuppressLint("DiscouragedApi")
    suspend fun icon(context: Context, packPkg: String, drawable: String, size: Int = IconRenderer.SIZE): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$packPkg/$drawable/$size"
        icons.get(key) ?: runCatching {
            val res = context.packageManager.getResourcesForApplication(packPkg)
            var id = res.getIdentifier(drawable, "drawable", packPkg)
            if (id == 0) id = res.getIdentifier(drawable, "mipmap", packPkg)
            if (id == 0) return@runCatching null
            val d = ResourcesCompat.getDrawable(res, id, null) ?: return@runCatching null
            IconRenderer.drawableToBitmap(d, size)
        }.getOrNull()?.also { icons.put(key, it) }
    }

    /** The pack's icon for an app, if the pack covers it. */
    suspend fun iconFor(context: Context, packPkg: String, appPkg: String, size: Int = IconRenderer.SIZE): Bitmap? {
        val cls = context.packageManager.getLaunchIntentForPackage(appPkg)?.component?.className
        val name = filter(context, packPkg).drawableFor(appPkg, cls) ?: return null
        return icon(context, packPkg, name, size)
    }
}
