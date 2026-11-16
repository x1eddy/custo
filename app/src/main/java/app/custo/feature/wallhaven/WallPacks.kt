package app.custo.feature.wallhaven

/**
 * A curated offline pack: a saved wallhaven search. Franchise names are only search terms; Custo
 * downloads what wallhaven's community uploaded under them (SFW only) and credits wallhaven.
 */
data class WallPack(
    val id: String,
    val title: String,
    val query: String,
    val general: Boolean = true,
    val anime: Boolean = true,
) {
    fun filters(atLeast: String?, portrait: Boolean) = WhFilters(
        // All-time favourites: toplist only reaches back one year, too few for niche searches.
        sorting = WhSorting.FAVORITES,
        general = general,
        anime = anime,
        portrait = portrait,
        atLeast = atLeast,
    )
}

object WallPacks {
    val all = listOf(
        WallPack("one_piece", "One Piece", "One Piece", general = false),
        WallPack("jjk", "Jujutsu Kaisen", "Jujutsu Kaisen", general = false),
        WallPack("invincible", "Invincible", "Invincible"),
        WallPack("fortnite", "Fortnite", "Fortnite"),
        WallPack("minecraft", "Minecraft", "Minecraft"),
        WallPack("roblox", "Roblox", "Roblox"),
        WallPack("valorant", "Valorant", "Valorant"),
        WallPack("demon_slayer", "Demon Slayer", "Demon Slayer", general = false),
        WallPack("naruto", "Naruto", "Naruto", general = false),
        WallPack("aot", "Attack on Titan", "Attack on Titan", general = false),
        WallPack("genshin", "Genshin Impact", "Genshin Impact"),
        WallPack("anime_landscapes", "Anime landscapes", "landscape", general = false),
        WallPack("amoled", "Dark / AMOLED", "dark"),
        WallPack("minimal", "Minimal", "minimalism", anime = false),
        WallPack("nature", "Nature", "nature", anime = false),
        WallPack("space", "Space", "space"),
        WallPack("cars", "Cars", "car", anime = false),
        WallPack("cyberpunk", "Cyberpunk", "cyberpunk"),
        WallPack("kawaii", "Cute / kawaii", "cute", general = false),
        WallPack("abstract", "Abstract", "abstract", anime = false),
    )

    val sizes = listOf(20, 50, 100)

    fun byId(id: String?): WallPack? = all.firstOrNull { it.id == id }

    /** Typical original download on wallhaven (PNG/JPEG at phone-or-larger size). */
    const val AVG_ORIGINAL_BYTES = 4L * 1024 * 1024
}

/** Pack download bookkeeping that doesn't touch Android, so it can be tested. */
object PackPlanner {
    /** How many more wallpapers a pack of [target] needs when [savedForPack] are already on the phone. */
    fun remaining(target: Int, savedForPack: Int): Int = (target - savedForPack).coerceAtLeast(0)

    /**
     * The next wallpapers to fetch from one page of [candidates]: SFW only, each id once, nothing
     * in [skipIds] (already saved, or tried and failed this run), at most [n].
     */
    fun pick(candidates: List<WhWallpaper>, skipIds: Set<String>, n: Int): List<WhWallpaper> {
        if (n <= 0) return emptyList()
        val seen = HashSet<String>()
        return WallhavenApi.sfwOnly(candidates).filter { it.id !in skipIds && seen.add(it.id) }.take(n)
    }

    /** Pages to try, portrait-first; landscape results are center-cropped, so they come last. */
    fun phases(): List<Boolean> = listOf(true, false)

    /** Phone storage after compaction and network data for [count] wallpapers. */
    fun estimate(count: Int, storedBytesEach: Long): Pair<Long, Long> =
        count * storedBytesEach to count * WallPacks.AVG_ORIGINAL_BYTES
}

/** Download state for one pack, shown on its card. */
data class PackProgress(
    val packId: String,
    val state: State,
    val done: Int = 0,
    val total: Int = 0,
    val message: String? = null,
) {
    enum class State { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

    val active: Boolean get() = state == State.QUEUED || state == State.RUNNING
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}
