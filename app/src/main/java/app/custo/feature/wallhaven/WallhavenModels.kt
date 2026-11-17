package app.custo.feature.wallhaven

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One wallpaper as wallhaven's API returns it (search results and `/w/<id>`). */
@Serializable
data class WhWallpaper(
    val id: String,
    val url: String = "",
    @SerialName("short_url") val shortUrl: String = "",
    val path: String = "",
    val thumbs: WhThumbs = WhThumbs(),
    val resolution: String = "",
    @SerialName("dimension_x") val width: Int = 0,
    @SerialName("dimension_y") val height: Int = 0,
    val colors: List<String> = emptyList(),
    @SerialName("file_size") val fileSize: Long = 0,
    @SerialName("file_type") val fileType: String = "",
    val category: String = "",
    // Unknown purity counts as not safe: the SFW filter only keeps an explicit "sfw".
    val purity: String = "",
    val views: Int = 0,
    val favorites: Int = 0,
    /** Only filled by `/w/<id>` (search results carry no tags). */
    val tags: List<WhTag> = emptyList(),
) {
    /** Thumbnail that keeps the image's own aspect ratio (wallhaven's "large" is a 16:9 crop). */
    val gridThumb: String get() = thumbs.original.ifBlank { thumbs.large.ifBlank { thumbs.small } }
    val pageUrl: String get() = url.ifBlank { "https://wallhaven.cc/w/$id" }
}

@Serializable
data class WhThumbs(val large: String = "", val original: String = "", val small: String = "")

@Serializable
data class WhMeta(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1,
    @SerialName("per_page") val perPage: Int = 24,
    val total: Int = 0,
    // Only set for random sorting; pass it back for the next pages so they don't repeat.
    val seed: String? = null,
)

@Serializable
data class WhSearchResponse(val data: List<WhWallpaper> = emptyList(), val meta: WhMeta = WhMeta())

@Serializable
data class WhInfoResponse(val data: WhWallpaper)

enum class WhSorting(val api: String) {
    RELEVANCE("relevance"), TOPLIST("toplist"), DATE_ADDED("date_added"), FAVORITES("favorites"), VIEWS("views"), RANDOM("random"),
}

enum class WhTopRange(val api: String) { DAY("1d"), THREE_DAYS("3d"), WEEK("1w"), MONTH("1M"), THREE_MONTHS("3M"), SIX_MONTHS("6M"), YEAR("1y") }

/**
 * Search options. There is deliberately no purity or "people" option: Custo only ever asks for
 * SFW general/anime wallpapers (see [WallhavenApi.buildSearchUrl] and [WallhavenApi.sfwOnly]).
 */
@Serializable
data class WhFilters(
    val sorting: WhSorting = WhSorting.TOPLIST,
    val topRange: WhTopRange = WhTopRange.MONTH,
    val general: Boolean = true,
    val anime: Boolean = true,
    /** Only portrait images (phone-shaped). */
    val portrait: Boolean = true,
    /** Minimum size "WxH", usually the phone's screen ([WallCompactor.minSizeFor]). */
    val atLeast: String? = null,
) {
    /** wallhaven's categories bit string general/anime/people; people is always 0. */
    val categories: String get() = when {
        general && !anime -> "100"
        anime && !general -> "010"
        else -> "110"
    }
}

/** Why a request failed, in terms the UI can show. */
enum class WhError(val message: String) {
    OFFLINE("You're offline"),
    RATE_LIMITED("wallhaven is busy. Try again in a minute"),
    SERVER("wallhaven isn't answering right now"),
    BAD_RESPONSE("Couldn't read wallhaven's answer"),
    NOT_FOUND("That wallpaper is gone"),
}

class WallhavenException(val error: WhError, cause: Throwable? = null) : Exception(error.message, cause)

/** A page of SFW results, ready for the UI. */
data class WhPage(val items: List<WhWallpaper>, val page: Int, val lastPage: Int, val seed: String?) {
    val hasMore: Boolean get() = page < lastPage
}
