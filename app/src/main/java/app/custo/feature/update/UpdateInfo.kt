package app.custo.feature.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A newer Custo release on GitHub. */
data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val apkBytes: Long,
    val notes: String,
    val pageUrl: String,
)

@Serializable
internal data class GhAsset(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val url: String = "",
)

@Serializable
internal data class GhRelease(
    @SerialName("tag_name") val tag: String = "",
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

/** Pure helpers: parsing GitHub's "latest release" answer and comparing versions. */
object UpdateParsing {
    const val REPO = "x1eddy/custo"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The release in [body] if it is newer than [current] and has an APK from this repo; else null. */
    fun newerRelease(body: String, current: String): UpdateInfo? {
        val r = runCatching { json.decodeFromString(GhRelease.serializer(), body) }.getOrNull() ?: return null
        if (r.draft || r.prerelease) return null
        val version = r.tag.trim().removePrefix("v").removePrefix("V")
        if (version.isEmpty() || compare(version, current) <= 0) return null
        val apk = r.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) && it.url.startsWith(DOWNLOAD_PREFIX) }
            ?: return null
        return UpdateInfo(version, apk.url, apk.size, cleanNotes(r.body.orEmpty()), r.htmlUrl)
    }

    /** Compares dotted versions numerically ("1.10.0" > "1.9.2"); a "-suffix" counts as older. */
    fun compare(a: String, b: String): Int {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val pa = parts(a); val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        val sa = a.contains('-'); val sb = b.contains('-')
        return if (sa == sb) 0 else if (sa) -1 else 1
    }

    /** Release notes without Markdown markup or the install section, for a plain dialog. */
    fun cleanNotes(md: String): String = md
        .substringBefore("### Install")
        .lines()
        .joinToString("\n") { line ->
            line.trim()
                .removePrefix("### ").removePrefix("## ").removePrefix("# ")
                .replace("**", "")
                .replace(Regex("^[-*] "), "• ")
        }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
