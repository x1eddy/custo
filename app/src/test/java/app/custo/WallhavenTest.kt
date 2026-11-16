package app.custo

import app.custo.feature.wallhaven.CompactMath
import app.custo.feature.wallhaven.PackPlanner
import app.custo.feature.wallhaven.PagerState
import app.custo.feature.wallhaven.RateLimiter
import app.custo.feature.wallhaven.WallFormat
import app.custo.feature.wallhaven.WallPacks
import app.custo.feature.wallhaven.WallhavenApi
import app.custo.feature.wallhaven.WallhavenException
import app.custo.feature.wallhaven.WhError
import app.custo.feature.wallhaven.WhFilters
import app.custo.feature.wallhaven.WhPage
import app.custo.feature.wallhaven.WhSorting
import app.custo.feature.wallhaven.WhWallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WallhavenTest {

    /** A real `/api/v1/search?categories=110&purity=100&sorting=random` answer, captured 2026-11-16. */
    private val fixture: String = javaClass.classLoader!!.getResource("wallhaven_search.json")!!.readText()

    private fun wall(id: String, purity: String = "sfw", category: String = "general") =
        WhWallpaper(id = id, path = "https://w.wallhaven.cc/full/$id.jpg", purity = purity, category = category)

    // ---------- Parsing ----------

    @Test fun parsesRealSearchResponse() {
        val res = WallhavenApi.parseSearch(fixture)
        assertEquals(24, res.data.size)
        assertEquals(1, res.meta.currentPage)
        assertEquals(21009, res.meta.lastPage)
        assertEquals("N6PoKF", res.meta.seed)
        val w = res.data.first()
        assertEquals("0qg87d", w.id)
        assertEquals("https://wallhaven.cc/w/0qg87d", w.url)
        assertEquals("https://whvn.cc/0qg87d", w.shortUrl)
        assertEquals("1600x1200", w.resolution)
        assertEquals(1600, w.width)
        assertEquals(1200, w.height)
        assertEquals(473647L, w.fileSize)
        assertEquals("general", w.category)
        assertEquals("sfw", w.purity)
        assertEquals("#66cccc", w.colors.first())
        assertEquals("https://th.wallhaven.cc/orig/0q/0qg87d.jpg", w.thumbs.original)
        assertTrue(w.thumbs.large.startsWith("https://th.wallhaven.cc/lg/"))
        assertTrue(w.thumbs.small.startsWith("https://th.wallhaven.cc/small/"))
        assertTrue(w.path.startsWith("https://w.wallhaven.cc/full/"))
        // Grid uses the aspect-keeping thumbnail.
        assertEquals(w.thumbs.original, w.gridThumb)
        res.data.forEach { assertTrue(it.id, it.path.isNotBlank() && it.width > 0 && it.height > 0) }
    }

    @Test fun badJsonIsABadResponse() {
        try {
            WallhavenApi.parseSearch("<html>cloudflare</html>")
            fail()
        } catch (e: WallhavenException) {
            assertEquals(WhError.BAD_RESPONSE, e.error)
        }
    }

    @Test fun toleratesMissingAndExtraFields() {
        val res = WallhavenApi.parseSearch("""{"data":[{"id":"abc123","path":"https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg","purity":"sfw","new_field":1}],"meta":{"query":{"id":1,"tag":"x"}}}""")
        assertEquals(1, res.data.size)
        assertEquals("", res.data[0].category)
        assertEquals(1, res.meta.lastPage)
    }

    // ---------- SFW ----------

    @Test fun sfwFilterDropsEverythingElse() {
        val res = WallhavenApi.parseSearch(fixture)
        val tampered = res.data.mapIndexed { i, w ->
            when (i) {
                0 -> w.copy(purity = "sketchy")
                1 -> w.copy(purity = "nsfw")
                2 -> w.copy(category = "people")
                3 -> w.copy(purity = "")
                4 -> w.copy(purity = "SFW ") // not exactly "sfw": dropped
                else -> w
            }
        }
        val kept = WallhavenApi.sfwOnly(tampered)
        assertEquals(19, kept.size)
        assertTrue(kept.all { it.purity == "sfw" && it.category != "people" })
    }

    @Test fun sfwFilterAppliesToRawJsonToo() {
        val body = fixture.replaceFirst("\"purity\":\"sfw\"", "\"purity\":\"nsfw\"")
        val all = WallhavenApi.parseSearch(body).data
        assertEquals(23, WallhavenApi.sfwOnly(all).size)
    }

    @Test fun missingPurityIsNotSafe() {
        val w = WallhavenApi.parseSearch("""{"data":[{"id":"abc123","path":"https://w.wallhaven.cc/x.jpg"}]}""").data
        assertTrue(WallhavenApi.sfwOnly(w).isEmpty())
    }

    @Test fun searchUrlAlwaysAsksForSfwGeneralAnime() {
        for (general in listOf(true, false)) for (anime in listOf(true, false)) for (s in WhSorting.entries) {
            val url = WallhavenApi.buildSearchUrl("one piece", WhFilters(sorting = s, general = general, anime = anime), 3)
            assertTrue(url, url.contains("purity=100&"))
            val cats = Regex("categories=(\\d{3})").find(url)!!.groupValues[1]
            assertEquals(url, '0', cats[2]) // never "people"
            assertTrue(url, cats != "000")
        }
    }

    @Test fun searchUrlEncodesParameters() {
        val url = WallhavenApi.buildSearchUrl(" One Piece ", WhFilters(atLeast = "648x1440", portrait = true), 2)
        assertEquals(
            "https://wallhaven.cc/api/v1/search?q=One%20Piece&categories=110&purity=100&sorting=toplist&order=desc&topRange=1M&atleast=648x1440&ratios=portrait&page=2",
            url,
        )
        val rnd = WallhavenApi.buildSearchUrl(null, WhFilters(sorting = WhSorting.RANDOM, portrait = false, atLeast = "bad&x=1"), 2, seed = "N6PoKF")
        assertEquals("https://wallhaven.cc/api/v1/search?categories=110&purity=100&sorting=random&order=desc&seed=N6PoKF&page=2", rnd)
        // Relevance needs a query; without one it falls back to toplist.
        assertTrue(WallhavenApi.buildSearchUrl("", WhFilters(sorting = WhSorting.RELEVANCE), 1).contains("sorting=toplist"))
        // Injection attempts stay inside the q value.
        assertTrue(WallhavenApi.buildSearchUrl("a&purity=111", WhFilters(), 1).contains("q=a%26purity%3D111&"))
    }

    // ---------- Throttle / retry ----------

    @Test fun throttleAllowsFortyPerMinute() {
        var now = 0L
        val rl = RateLimiter(max = 40, windowMs = 60_000) { now }
        repeat(40) { i ->
            now = i * 100L
            assertEquals(0L, rl.waitMs(now))
            rl.record(now)
        }
        now = 4_000
        // 41st must wait until the first one leaves the window (t=0 + 60 s).
        assertEquals(56_000L, rl.waitMs(now))
        now = 60_000
        assertEquals(0L, rl.waitMs(now))
        rl.record(now)
        // Full again: the next slot frees when the t=100 request leaves the window.
        assertEquals(100L, rl.waitMs(60_000))
    }

    @Test fun throttlePauseAfter429() {
        var now = 1_000L
        val rl = RateLimiter(max = 40, windowMs = 60_000) { now }
        rl.pauseFor(8_000)
        assertEquals(8_000L, rl.waitMs(now))
        now = 9_000
        assertEquals(0L, rl.waitMs(now))
    }

    @Test fun backoffGrowsAndHonoursRetryAfter() {
        assertEquals(1000L, WallhavenApi.backoffMs(0, null))
        assertEquals(2000L, WallhavenApi.backoffMs(1, null))
        assertEquals(4000L, WallhavenApi.backoffMs(2, null))
        assertEquals(30_000L, WallhavenApi.backoffMs(10, null))
        assertEquals(7000L, WallhavenApi.backoffMs(0, "7"))
        assertEquals(60_000L, WallhavenApi.backoffMs(0, "9999"))
        assertTrue(WallhavenApi.retryable(429))
        assertTrue(WallhavenApi.retryable(503))
        assertFalse(WallhavenApi.retryable(404))
        assertFalse(WallhavenApi.retryable(401))
    }

    // ---------- Compactor math ----------

    @Test fun outputIsScreenShapedAndCapped() {
        assertEquals(648 to 1440, CompactMath.outputSize(720, 1600))
        assertEquals(648 to 1440, CompactMath.outputSize(1600, 720)) // landscape-reported screen
        assertEquals(1080 to 1440, CompactMath.outputSize(1080, 1440))
        assertEquals(720 to 1280, CompactMath.outputSize(720, 1280))
        assertEquals("648x1440", CompactMath.minSizeFor(720, 1600))
    }

    @Test fun planCropsPortraitSourceToScreenAspect() {
        // Real One Piece result: 2094x3222 on a 720x1600 phone.
        val p = CompactMath.plan(2094, 3222, 720, 1600)
        assertEquals(648, p.outWidth)
        assertEquals(1440, p.outHeight)
        // The screen (0.45) is narrower than the image (0.65): full height, sides cut.
        assertEquals(3222, p.cropHeight)
        assertEquals(1450, p.cropWidth)
        assertEquals((2094 - 1450) / 2, p.cropLeft)
        assertEquals(0, p.cropTop)
        assertEquals(2, p.sampleSize) // decodes 725x1611, still >= 648x1440
        assertTrue(p.cropWidth / p.sampleSize >= p.outWidth && p.cropHeight / p.sampleSize >= p.outHeight)
    }

    @Test fun planCropsLandscapeSourceCentered() {
        val p = CompactMath.plan(3840, 2160, 1080, 2400)
        assertEquals(2160, p.cropHeight)
        assertEquals(972, p.cropWidth) // 2160 * 1080/2400
        assertEquals((3840 - 972) / 2, p.cropLeft)
        assertEquals(0, p.cropTop)
        assertEquals(1440, p.outHeight)
        assertEquals(648, p.outWidth)
        assertEquals(1, p.sampleSize)
    }

    @Test fun planNeverUpscales() {
        val p = CompactMath.plan(600, 800, 1080, 2400)
        assertTrue(p.outWidth <= p.cropWidth && p.outHeight <= p.cropHeight)
        assertEquals(800, p.outHeight)
        assertEquals(1, p.sampleSize)
    }

    @Test fun hugeSourceGetsBigSampleSize() {
        val p = CompactMath.plan(8000, 12000, 1080, 2400)
        assertEquals(8, p.sampleSize)
        assertTrue(p.cropWidth / p.sampleSize >= p.outWidth)
        assertTrue(p.cropWidth / (p.sampleSize * 2) < p.outWidth || p.cropHeight / (p.sampleSize * 2) < p.outHeight)
    }

    @Test fun storedEstimateIsAFewHundredKb() {
        val b = CompactMath.estimateStoredBytes(1080, 2400)
        assertTrue(b in 100_000..400_000)
    }

    // ---------- Packs ----------

    @Test fun packsAreCuratedAndUnique() {
        assertEquals(20, WallPacks.all.size)
        assertEquals(WallPacks.all.size, WallPacks.all.map { it.id }.toSet().size)
        assertTrue(WallPacks.all.any { it.query == "One Piece" })
        WallPacks.all.forEach { p ->
            val f = p.filters("648x1440", portrait = true)
            assertTrue(p.id, f.categories != "000" && f.categories.endsWith("0"))
        }
        assertEquals(listOf(20, 50, 100), WallPacks.sizes)
    }

    @Test fun resumeSkipsSavedAndDedupes() {
        assertEquals(20, PackPlanner.remaining(20, 0))
        assertEquals(7, PackPlanner.remaining(20, 13))
        assertEquals(0, PackPlanner.remaining(20, 25))
        val page = listOf(wall("a"), wall("b"), wall("b"), wall("c", purity = "sketchy"), wall("d", category = "people"), wall("e"), wall("f"))
        assertEquals(listOf("a", "e"), PackPlanner.pick(page, skipIds = setOf("b"), n = 2).map { it.id })
        assertEquals(listOf("a", "b", "e", "f"), PackPlanner.pick(page, emptySet(), 10).map { it.id })
        assertTrue(PackPlanner.pick(page, emptySet(), 0).isEmpty())
    }

    @Test fun resumeAcrossPagesAndPhases() {
        // Simulates the worker: portrait pages then any-shape pages that repeat some ids.
        val saved = mutableSetOf("p1") // from an earlier, interrupted run
        val pages = listOf(
            listOf(wall("p1"), wall("p2"), wall("p3")),
            listOf(wall("p3"), wall("p4")),
            listOf(wall("p2"), wall("l1"), wall("l2"), wall("l3")),
        )
        var need = PackPlanner.remaining(5, savedForPack = 1)
        val got = mutableListOf<String>()
        for (p in pages) {
            for (w in PackPlanner.pick(p, saved, need)) { saved += w.id; got += w.id; need-- }
            if (need == 0) break
        }
        assertEquals(listOf("p2", "p3", "p4", "l1"), got)
        assertEquals(0, need)
        assertEquals(listOf(true, false), PackPlanner.phases())
    }

    @Test fun estimateScalesWithCount() {
        val (stored, dl) = PackPlanner.estimate(20, 300_000)
        assertEquals(6_000_000L, stored)
        assertEquals(20 * WallPacks.AVG_ORIGINAL_BYTES, dl)
    }

    // ---------- Paging ----------

    @Test fun pagerDropsRepeatsAndStopsAtLastPage() {
        var s = PagerState(filters = WhFilters(), loading = true)
        s = s.plus(WhPage(listOf(wall("a"), wall("b")), page = 1, lastPage = 2, seed = "XY"))
        assertEquals(listOf("a", "b"), s.items.map { it.id })
        assertTrue(s.canLoadMore)
        s = s.copy(loading = true).plus(WhPage(listOf(wall("b"), wall("c")), page = 2, lastPage = 2, seed = null))
        assertEquals(listOf("a", "b", "c"), s.items.map { it.id })
        assertEquals("XY", s.seed)
        assertTrue(s.endReached)
        assertFalse(s.canLoadMore)
    }

    @Test fun formatsSizesAndColors() {
        assertEquals("512 B", WallFormat.bytes(512))
        assertEquals("300 KB", WallFormat.bytes(300 * 1024))
        assertEquals("6.2 MB", WallFormat.bytes((6.2 * 1024 * 1024).toLong()))
        assertEquals(0xFF66CCCC.toInt(), WallFormat.color("#66cccc"))
        assertEquals(null, WallFormat.color("#abc"))
    }
}
