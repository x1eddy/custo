package app.custo.feature.wallhaven

import kotlinx.serialization.Serializable

/** A wallhaven tag as `/w/<id>` returns it. */
@Serializable
data class WhTag(val id: Long = 0, val name: String = "", val alias: String = "", val category: String = "")

/**
 * Curated search terms (packs, set searches, Wallpapers chips) mapped to wallhaven tag ids. A free
 * text search for "Minecraft" or "One Piece" matches titles, uploaders and loosely related tags
 * (the top "One Piece" result was a VTuber at the beach); `id:<tag>` only returns pictures tagged
 * with exactly that tag. Ids were checked against the live API on 2026-11-17.
 */
object WhTags {
    private val ids: Map<String, Long> = mapOf(
        // Franchises
        "one piece" to 1394,
        "jujutsu kaisen" to 108966,
        "invincible" to 127837,
        "fortnite" to 67755,
        "minecraft" to 1216,
        "roblox" to 18265,
        "valorant" to 97807,
        "demon slayer" to DEMON_SLAYER,
        "naruto" to 541,
        "attack on titan" to ATTACK_ON_TITAN,
        "genshin impact" to 95047,
        "matrix" to 325,
        // Topics
        "video games" to 55,
        "landscape" to 711,
        "dark" to 369,
        "minimalism" to 2278,
        "nature" to 37,
        "space" to 32,
        "car" to 314,
        "cyberpunk" to 376,
        "abstract" to 74,
        "cherry blossom" to 2612,
        "forest" to 1265,
        "halloween" to 566,
        "ocean" to 307,
        "synthwave" to SYNTHWAVE,
        "autumn" to AUTUMN,
        "cute" to CUTE,
    ).filterValues { it > 0 }

    /** Anime franchises (and cute art) whose pictures are expected to show characters. */
    private val characterTerms = setOf("one piece", "jujutsu kaisen", "demon slayer", "naruto", "attack on titan", "genshin impact", "cute")

    /**
     * Whether pictures for [term] may be about people/characters. Everything else (games like
     * Minecraft or Fortnite, Nature, Space, Cars…) wants scenery and art of the thing itself, not
     * anime girls or fan art that merely carries the tag.
     */
    fun allowsCharacters(term: String?): Boolean = term?.trim()?.lowercase() in characterTerms

    /** The exact tag id for a curated term (any case), or null for free text. */
    fun idFor(term: String?): Long? = term?.trim()?.lowercase()?.let { ids[it] }

    /** The `q` to send: `id:<tag>` for curated terms, the text itself otherwise. */
    fun apiQuery(term: String?): String? {
        val t = term?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return idFor(t)?.let { "id:$it" } ?: t
    }
}

// Filled in from the live API (0 = no reliable tag yet: falls back to free text).
private const val DEMON_SLAYER = 84088L
private const val ATTACK_ON_TITAN = 140L
private const val SYNTHWAVE = 38424L
private const val AUTUMN = 1463L
private const val CUTE = 0L

/**
 * Quality rules for curated results (packs, set strips, automatic set backgrounds): the picture has
 * to be popular enough, big enough, phone-croppable and not a meme, collage, screenshot or pin-up.
 * Pure, so it is unit tested.
 */
object WhQuality {
    /** Smaller originals are mostly low-detail images, icons or memes. */
    const val MIN_BYTES = 150_000L

    /** Landscape pictures wider than 16:9 lose too much when center-cropped to a phone. */
    const val MAX_LANDSCAPE = 16f / 9f + 0.01f

    /** Taller than this (long scroll screenshots, banners) doesn't fit a phone either. */
    const val MAX_PORTRAIT = 2.6f

    /** Default favourites floor for curated picks; packs relax it step by step when wallhaven runs short. */
    const val MIN_FAVORITES = 25

    /** Tags that mark a picture as not a wallpaper (memes, collages, screenshots…) or as a pin-up. */
    val JUNK_TAGS: Set<String> = setOf(
        "meme", "memes", "humor", "rage comics", "text", "quote", "screen shot", "screenshot", "collage",
        "comic strip", "comics", "infographic", "icons", "map", "demotivational posters", "4chan", "reddit",
        "cleavage", "big boobs", "boobs", "ass", "bikini", "lingerie", "underwear", "panties", "swimwear",
        "seductive pose", "sideboob", "underboob", "thick thigh", "thighs", "bare midriff", "pantyhose",
        "stockings", "cameltoe", "booty", "bent over",
    )

    fun shapeOk(w: WhWallpaper): Boolean {
        if (w.width <= 0 || w.height <= 0) return true
        val ratio = w.height.toFloat() / w.width
        return ratio <= MAX_PORTRAIT && 1f / ratio <= MAX_LANDSCAPE
    }

    /** Cheap checks on a search result (no extra request). */
    fun keep(w: WhWallpaper, minFavorites: Int, blocked: Set<String> = emptySet()): Boolean =
        w.id !in blocked && w.favorites >= minFavorites && (w.fileSize <= 0 || w.fileSize >= MIN_BYTES) && shapeOk(w)

    /** True when one of [tags] marks the picture as junk. */
    fun isJunk(tags: List<WhTag>): Boolean = tags.any { t ->
        val n = t.name.trim().lowercase()
        n in JUNK_TAGS || n.contains("meme")
    }

    /** True when [tags] has the curated tag [tagId] (always true without one). */
    fun hasTag(tags: List<WhTag>, tagId: Long?): Boolean = tagId == null || tags.isEmpty() || tags.any { it.id == tagId }

    /** People and character tags: not wanted in packs and sets that aren't about characters. */
    val CHARACTER_TAGS: Set<String> = setOf(
        "anime", "anime girls", "anime boys", "women", "girls", "men", "boys", "cat girl", "fox girl", "fan art",
        "video game girls", "original characters", "women outdoors", "model", "schoolgirl", "maid", "Virtual Youtuber",
        "cats", "black cats", "cat", "portrait", "face", "looking at viewer",
    ).mapTo(HashSet()) { it.lowercase() }

    fun hasCharacters(tags: List<WhTag>): Boolean = tags.any { it.name.trim().lowercase() in CHARACTER_TAGS }

    /** More tags than this is a crossover crowd or collage, not a picture *of* the franchise. */
    const val MAX_TAGS = 30

    /** The main tag must be among the first few: uploaders tag what the picture is about first. */
    const val MAIN_TAG_WITHIN = 4

    /**
     * Whether the picture is about [tagId]: it carries it among its first [MAIN_TAG_WITHIN] tags and
     * isn't a crowd of [MAX_TAGS]+ tags. Without a curated tag (or tags) there's nothing to check.
     */
    fun isAbout(tags: List<WhTag>, tagId: Long?, strict: Boolean = false): Boolean {
        if (tagId == null || tags.isEmpty()) return true
        val at = tags.indexOfFirst { it.id == tagId }
        return at in 0 until (if (strict) STRICT_MAIN_TAG_WITHIN else MAIN_TAG_WITHIN) && tags.size <= MAX_TAGS
    }

    /** For scenery packs and sets the main tag has to be the first or second one. */
    const val STRICT_MAIN_TAG_WITHIN = 2

    /**
     * The whole per-picture tag verdict: no junk, about [tagId], and – unless [allowCharacters] –
     * no people/character tags with the main tag first or second.
     */
    fun tagsOk(tags: List<WhTag>, tagId: Long?, allowCharacters: Boolean): Boolean =
        !isJunk(tags) && isAbout(tags, tagId, strict = !allowCharacters) && (allowCharacters || !hasCharacters(tags))

    /** Results minus the ones the user said "Not this" to. */
    fun withoutBlocked(items: List<WhWallpaper>, blocked: Set<String>): List<WhWallpaper> =
        if (blocked.isEmpty()) items else items.filter { it.id !in blocked }

    /**
     * The best of [items] (kept in order, so portrait-first/most-favourited-first) that pass [keep]:
     * strict favourites floor first, looser ones until there are [n] (or as many as there are).
     */
    fun curate(items: List<WhWallpaper>, n: Int, blocked: Set<String> = emptySet(), minFavorites: Int = MIN_FAVORITES): List<WhWallpaper> {
        var best = emptyList<WhWallpaper>()
        for (floor in floors(minFavorites) + 0) {
            best = items.filter { keep(it, floor, blocked) }
            if (best.size >= n) break
        }
        return best
    }

    /** Favourite floors a pack tries in turn: strict first, then looser so 50/100 packs still fill up. */
    fun floors(start: Int): List<Int> = listOf(start, start / 3, 3).filter { it >= 0 }.distinct().sortedDescending()
}
