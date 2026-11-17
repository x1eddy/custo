package app.custo.feature.widgetsets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.custo.data.LenientList
import app.custo.data.Prefs
import app.custo.data.updateList
import app.custo.feature.wallhaven.SavedWall
import app.custo.feature.wallhaven.WallFormat
import app.custo.feature.wallhaven.WallhavenApi
import app.custo.feature.wallhaven.WallhavenRepository
import app.custo.feature.wallhaven.WhTags
import app.custo.feature.wallhaven.WhWallpaper
import app.custo.feature.widgets.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Kept apart from the main settings (like the wallhaven store): entries point at files on this phone. */
private val Context.setBackgroundStore: DataStore<Preferences> by preferencesDataStore("set_backgrounds")

/** A set palette as stored with a background choice, so widgets can be drawn without the picture. */
@Serializable
data class StoredPalette(
    val background: Int,
    val text: Int,
    val accent: Int,
    val secondary: Int,
    val line: Int,
    val tint: Int,
) {
    fun toPalette() = ThemePalette(background, text, accent, secondary, line)

    companion object {
        fun of(p: ThemePalette, tint: Int) = StoredPalette(p.background, p.text, p.accent, p.secondary, p.line, tint)
    }
}

/** One set's chosen wallhaven background: theme id → saved wallpaper id, plus the colors derived from it. */
@Serializable
data class SetBackground(
    val themeId: String,
    val wallId: String,
    val query: String? = null,
    /** Picked automatically (the set's first SFW top result), not by the user. */
    val auto: Boolean = false,
    val palette: StoredPalette? = null,
    val chosenAt: Long = 0,
)

/** The decisions behind set backgrounds, kept free of Android so they are unit-tested. */
object SetBackgroundLogic {
    /**
     * Whether to pick a background for [theme] by itself: only for sets with a wallhaven search,
     * only online, only when nothing is chosen and only once ([autoDone] holds sets already tried).
     */
    fun shouldAutoPick(theme: WidgetTheme, current: SetBackground?, autoDone: Set<String>, online: Boolean): Boolean =
        online && !theme.wallhavenQuery.isNullOrBlank() && current == null && theme.id !in autoDone

    /** The automatic pick: the first result that passes the SFW check, or null. */
    fun autoPick(results: List<WhWallpaper>): WhWallpaper? = WallhavenApi.sfwOnly(results).firstOrNull()

    fun decode(json: String?): Map<String, SetBackground> =
        LenientList.decode(SetBackground.serializer(), json).associateBy { it.themeId }

    /** Wallpaper colors to derive from: wallhaven's own color list, else the [fallback] quantizer. */
    fun wallColors(hex: List<String>, fallback: () -> List<Int>): List<Int> =
        hex.mapNotNull { WallFormat.color(it) }.ifEmpty { fallback() }

    /** The palette (and frame tint) for [base] on a wallpaper with these main colors. */
    fun paletteFor(base: ThemePalette, colors: List<Int>): StoredPalette =
        StoredPalette.of(SetPaletteMath.derive(base, colors), SetPaletteMath.tint(base, colors))

    /** [theme] as it should look with [bg]: the derived palette and the picture's file; unchanged without one. */
    fun resolve(theme: WidgetTheme, bg: SetBackground?, dir: String?): WidgetTheme {
        if (bg == null || dir == null || bg.themeId != theme.id) return theme
        val stored = bg.palette ?: return theme.copy(chosenWall = ChosenWall(bg.wallId, "$dir/${bg.wallId}.webp", theme.palette.background))
        return theme.copy(palette = stored.toPalette(), chosenWall = ChosenWall(bg.wallId, "$dir/${bg.wallId}.webp", stored.tint))
    }
}

/**
 * Process-wide view of the chosen set backgrounds. Read by previews, the set screens and the
 * home-screen widgets (via [WidgetThemes.byId]); written by [SetBackgroundRepository].
 */
object SetBackgrounds {
    private val _choices = MutableStateFlow<Map<String, SetBackground>>(emptyMap())
    val choices: StateFlow<Map<String, SetBackground>> = _choices

    @Volatile private var dir: String? = null

    @Volatile var loaded = false
        private set

    fun of(themeId: String): SetBackground? = _choices.value[themeId]

    /** [theme] with its chosen background and matching colors, if it has one. */
    fun resolve(theme: WidgetTheme): WidgetTheme = SetBackgroundLogic.resolve(theme, of(theme.id), dir)

    internal fun publish(dir: String, map: Map<String, SetBackground>) {
        this.dir = dir
        _choices.value = map
        loaded = true
    }
}

/**
 * Chooses, clears and auto-picks set backgrounds. One instance in [app.custo.AppContainer.setBackgrounds].
 * Choosing saves the picture offline (compact WebP via [WallhavenRepository.save]), derives the set's
 * colors from it and restyles that set's widgets already on the home screen.
 */
class SetBackgroundRepository(
    context: Context,
    private val wallhaven: WallhavenRepository,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val prefs = Prefs(app.setBackgroundStore)
    private val choicesKey = stringPreferencesKey("choices_json")
    private val autoKey = stringSetPreferencesKey("auto_done")
    private val rulesKey = androidx.datastore.preferences.core.intPreferencesKey("auto_rules")
    private val loadLock = Mutex()
    /** Automatic picks run one at a time, so a gallery full of sets doesn't burst downloads. */
    private val autoQueue = Mutex()
    private val autoInFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        // A set whose picture was deleted goes back to its drawn wallpaper; after "Not this" it gets
        // the next good picture instead (on Wi-Fi, like any automatic pick).
        wallhaven.addGoneListener { id, notThis ->
            ensureLoaded()
            val using = SetBackgrounds.choices.value.values.filter { it.wallId == id }.map { it.themeId }
            if (using.isEmpty()) return@addGoneListener
            prefs.updateList(choicesKey, SetBackground.serializer(), "set backgrounds") { l -> l.filterNot { it.wallId == id } }
            if (notThis) prefs.edit { it[autoKey] = (it[autoKey] ?: emptySet()) - using.toSet() }
            reload()
            using.forEach { themeId ->
                restylePlaced(themeId)
                if (notThis) WidgetThemes.base(themeId)?.let { autoDefault(it) }
            }
        }
    }

    /** Loads the choices once per process; cheap after that. Widgets call it before drawing. */
    suspend fun ensureLoaded() {
        if (SetBackgrounds.loaded) return
        loadLock.withLock {
            if (SetBackgrounds.loaded) return@withLock
            migrateAutoPicks()
            reload()
        }
    }

    /**
     * Automatic picks made before the exact-tag search (1.1 test builds) could be off-topic. Drop
     * them – and their downloads, unless favourited or part of a pack – so the sets pick again.
     * Pictures the user chose by hand stay.
     */
    private suspend fun migrateAutoPicks() {
        if (prefs.get(rulesKey, 0) >= AUTO_RULES) return
        val all = SetBackgroundLogic.decode(prefs.get(choicesKey, "[]")).values
        val stale = all.filter { it.auto }
        if (stale.isNotEmpty()) {
            val keepIds = all.filterNot { it.auto }.mapTo(HashSet()) { it.wallId }
            val saved = wallhaven.savedNow().associateBy { it.id }
            prefs.updateList(choicesKey, SetBackground.serializer(), "set backgrounds") { l -> l.filterNot { it.auto } }
            prefs.edit { it[autoKey] = (it[autoKey] ?: emptySet()) - stale.map { b -> b.themeId }.toSet() }
            stale.map { it.wallId }.distinct().forEach { id ->
                val rec = saved[id]
                if (id !in keepIds && rec != null && !rec.favorite && rec.packId == null) runCatching { wallhaven.delete(id, notify = false) }
            }
        }
        prefs.set(rulesKey, AUTO_RULES)
    }

    private suspend fun reload() = withContext(Dispatchers.IO) {
        val dir = wallhaven.dir
        // A wallpaper deleted from "My wallpapers" takes its set background with it.
        val map = SetBackgroundLogic.decode(prefs.get(choicesKey, "[]")).filterValues { File(dir, "${it.wallId}.webp").isFile }
        SetBackgrounds.publish(dir.path, map)
    }

    /** Saves [item] (if needed) and makes it [theme]'s background. */
    suspend fun choose(theme: WidgetTheme, item: WhWallpaper, auto: Boolean = false) {
        val saved = wallhaven.save(item, query = theme.wallhavenQuery)
        chooseSaved(theme, saved, auto)
    }

    /** Makes an already saved wallpaper [theme]'s background. */
    suspend fun chooseSaved(theme: WidgetTheme, saved: SavedWall, auto: Boolean = false) {
        ensureLoaded()
        val base = WidgetThemes.base(theme.id) ?: theme
        val file = wallhaven.savedFile(saved.id) ?: return
        val colors = withContext(Dispatchers.Default) { SetBackgroundLogic.wallColors(saved.colors) { quantize(file) } }
        val bg = SetBackground(
            themeId = base.id, wallId = saved.id, query = base.wallhavenQuery, auto = auto,
            palette = SetBackgroundLogic.paletteFor(base.palette, colors), chosenAt = System.currentTimeMillis(),
        )
        prefs.updateList(choicesKey, SetBackground.serializer(), "set backgrounds") { l -> l.filterNot { it.themeId == base.id } + bg }
        reload()
        restylePlaced(base.id)
    }

    /** Back to the set's drawn wallpaper and original colors. */
    suspend fun clear(themeId: String) {
        ensureLoaded()
        // A cleared set counts as decided: it is never auto-picked again.
        prefs.edit { it[autoKey] = (it[autoKey] ?: emptySet()) + themeId }
        prefs.updateList(choicesKey, SetBackground.serializer(), "set backgrounds") { l -> l.filterNot { it.themeId == themeId } }
        reload()
        restylePlaced(themeId)
    }

    /**
     * Gives [theme] its first SFW wallhaven top result as background, in the background, when it has
     * a search, nothing chosen, the phone is on Wi-Fi (never mobile data – these are unrequested
     * downloads of a few MB each) and it was never auto-picked before.
     */
    fun autoDefault(theme: WidgetTheme) {
        if (theme.wallhavenQuery.isNullOrBlank() || !autoInFlight.add(theme.id)) return
        scope.launch(Dispatchers.IO) {
            try {
                ensureLoaded()
                autoQueue.withLock {
                    val done = prefs.get(autoKey, emptySet())
                    if (!SetBackgroundLogic.shouldAutoPick(theme, SetBackgrounds.of(theme.id), done, wallhaven.isOnlineUnmetered())) return@withLock
                    // Let the screen's own pictures load first.
                    delay(1200)
                    val query = theme.wallhavenQuery!!
                    val results = runCatching { wallhaven.topFor(query, 6) }.getOrNull() ?: return@withLock
                    // The first that passes the tag check (no memes, collages or pin-ups); at most 4 lookups.
                    val tagId = WhTags.idFor(query)
                    val pick = SetBackgroundLogic.autoPick(results.take(4).filter { runCatching { wallhaven.passesTagCheck(it, tagId) }.getOrDefault(true) })
                    if (pick != null) runCatching { choose(theme, pick, auto = true) }.onFailure { return@withLock }
                    prefs.edit { it[autoKey] = (it[autoKey] ?: emptySet()) + theme.id }
                }
            } finally {
                autoInFlight.remove(theme.id)
            }
        }
    }

    private companion object {
        /** Bumped when the automatic pick rules change enough to redo earlier picks. */
        const val AUTO_RULES = 2
    }

    /** The picture to set as wallpaper for [theme], or null to use its drawn one. */
    fun wallFile(theme: WidgetTheme): File? = theme.chosenWall?.let { wallhaven.savedFile(it.id) }

    /** Gives this set's widgets on the home screen the new colors (their content stays). */
    private suspend fun restylePlaced(themeId: String) {
        val theme = WidgetThemes.byId(themeId) ?: return
        runCatching {
            Widgets.placed(app).forEach { w ->
                val style = Widgets.style(app, w.glanceId)
                if (style.themeId == themeId) Widgets.save(app, w, theme.styleFor(w.kind, style.themeSlot, style))
            }
        }
    }

    /** Main colors of a saved picture, from a small decode. */
    private fun quantize(file: File): List<Int> {
        val opts = BitmapFactory.Options().apply { inSampleSize = 16; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = BitmapFactory.decodeFile(file.path, opts) ?: return emptyList()
        return try {
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            SetPaletteMath.dominantColors(px)
        } finally {
            bmp.recycle()
        }
    }
}
