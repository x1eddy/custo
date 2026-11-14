package app.custo.feature.icons

/**
 * appfilter.xml of an ADW / Nova / Lawnchair icon pack:
 * `<item component="ComponentInfo{com.app/com.app.Main}" drawable="app_icon" />`.
 * Pure Kotlin so it can be unit tested; the Android side feeds it tag events.
 */
data class AppFilter(
    /** "pkg/cls" → drawable name */
    val byComponent: Map<String, String>,
    /** pkg → first drawable seen for that package */
    val byPackage: Map<String, String>,
) {
    /** Drawable for an app: exact activity first, then any entry of the same package. */
    fun drawableFor(pkg: String, cls: String?): String? =
        cls?.let { byComponent["$pkg/${normalizeClass(pkg, it)}"] } ?: byPackage[pkg]

    /** All distinct drawable names, in file order. */
    val drawables: List<String> get() = (byComponent.values + byPackage.values).distinct()

    companion object {
        val EMPTY = AppFilter(emptyMap(), emptyMap())

        /** "ComponentInfo{pkg/.Main}" → ("pkg", "pkg.Main"); null for anything malformed. */
        fun parseComponent(raw: String): Pair<String, String>? {
            val s = raw.trim()
            val start = s.indexOf('{')
            val end = s.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val inner = s.substring(start + 1, end).trim()
            val slash = inner.indexOf('/')
            if (slash <= 0 || slash == inner.lastIndex) return null
            val pkg = inner.substring(0, slash).trim()
            val cls = inner.substring(slash + 1).trim()
            if (pkg.isEmpty() || cls.isEmpty() || pkg.any { it.isWhitespace() }) return null
            return pkg to normalizeClass(pkg, cls)
        }

        fun normalizeClass(pkg: String, cls: String): String = if (cls.startsWith(".")) pkg + cls else cls

        /** Builds the map from (tag, attributes) events. Only `<item>` tags matter. */
        fun fromEvents(events: Sequence<Pair<String, Map<String, String>>>): AppFilter {
            val byComp = LinkedHashMap<String, String>()
            val byPkg = LinkedHashMap<String, String>()
            for ((tag, attrs) in events) {
                if (tag != "item") continue
                val drawable = attrs["drawable"]?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val (pkg, cls) = parseComponent(attrs["component"] ?: continue) ?: continue
                byComp.putIfAbsent("$pkg/$cls", drawable)
                byPkg.putIfAbsent(pkg, drawable)
            }
            return AppFilter(byComp, byPkg)
        }

        private val TAG = Regex("""<\s*([A-Za-z_][\w:.-]*)((?:\s+[\w:.-]+\s*=\s*(?:"[^"]*"|'[^']*'))*)\s*/?>""")
        private val ATTR = Regex("""([\w:.-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
        private val COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

        /** Tiny tolerant tokenizer for plain-text appfilter.xml (assets/). */
        fun tokenize(xml: String): Sequence<Pair<String, Map<String, String>>> {
            val clean = COMMENT.replace(xml, "")
            return TAG.findAll(clean).map { m ->
                val attrs = ATTR.findAll(m.groupValues[2]).associate { a ->
                    a.groupValues[1] to unescape(a.groupValues[2].ifEmpty { a.groupValues[3] })
                }
                m.groupValues[1] to attrs
            }
        }

        fun parse(xml: String): AppFilter = fromEvents(tokenize(xml))

        private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&amp;", "&")
    }
}
