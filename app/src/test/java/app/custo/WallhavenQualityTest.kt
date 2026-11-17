package app.custo

import app.custo.feature.wallhaven.WallPacks
import app.custo.feature.wallhaven.WallhavenApi
import app.custo.feature.wallhaven.WhFilters
import app.custo.feature.wallhaven.WhQuality
import app.custo.feature.wallhaven.WhSorting
import app.custo.feature.wallhaven.WhTag
import app.custo.feature.wallhaven.WhTags
import app.custo.feature.wallhaven.WhWallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallhavenQualityTest {

    private fun wall(
        id: String, favorites: Int = 100, w: Int = 1080, h: Int = 1920, bytes: Long = 900_000,
    ) = WhWallpaper(id = id, path = "https://w.wallhaven.cc/full/$id.jpg", purity = "sfw", category = "general", favorites = favorites, width = w, height = h, fileSize = bytes)

    // ---------- Exact tags ----------

    @Test fun curatedTermsUseExactTags() {
        assertEquals("id:1216", WhTags.apiQuery("Minecraft"))
        assertEquals("id:1394", WhTags.apiQuery("  one piece "))
        assertEquals("id:67755", WhTags.apiQuery("Fortnite"))
        // Free text stays free text.
        assertEquals("Luffy gear 5", WhTags.apiQuery("Luffy gear 5"))
        assertNull(WhTags.apiQuery("   "))
        assertNull(WhTags.apiQuery(null))
    }

    @Test fun everyPackAndChipTermHasATag() {
        val untagged = WallPacks.all.filter { it.tagId == null }.map { it.query }
        // "cute" has no single wallhaven tag; everything else is exact.
        assertEquals(listOf("cute"), untagged)
        listOf("video games", "dark", "nature", "space", "minimalism").forEach { assertTrue(it, WhTags.idFor(it) != null) }
    }

    @Test fun exactTagGoesIntoTheSearchUrl() {
        val url = WallhavenApi.buildSearchUrl(WhTags.apiQuery("Minecraft"), WhFilters(sorting = WhSorting.FAVORITES, atLeast = "576x1152"), 1)
        assertTrue(url, url.contains("q=id%3A1216"))
        assertTrue(url.contains("sorting=favorites"))
        assertTrue(url.contains("atleast=576x1152"))
        assertTrue(url.contains("purity=100"))
    }

    // ---------- Cheap filters ----------

    @Test fun keepNeedsFavoritesSizeAndPhoneShape() {
        assertTrue(WhQuality.keep(wall("a"), 25))
        assertFalse("too few favourites", WhQuality.keep(wall("b", favorites = 3), 25))
        assertFalse("tiny file", WhQuality.keep(wall("c", bytes = 40_000), 0))
        assertFalse("ultrawide", WhQuality.keep(wall("d", w = 3440, h = 1440), 0))
        assertTrue("16:9 crops fine", WhQuality.keep(wall("e", w = 1920, h = 1080), 0))
        assertTrue("16:10", WhQuality.keep(wall("f", w = 2560, h = 1600), 0))
        assertFalse("long screenshot", WhQuality.keep(wall("g", w = 1080, h = 3400), 0))
        assertTrue("unknown size passes", WhQuality.keep(wall("h", w = 0, h = 0, bytes = 0), 0))
    }

    @Test fun curateRelaxesTheFloorOnlyWhenShort() {
        val items = listOf(wall("a", 300), wall("b", 90), wall("c", 12), wall("d", 4), wall("e", 1))
        assertEquals(listOf("a", "b"), WhQuality.curate(items, 2).map { it.id })
        // Needs 3: the strict floor (25) gives 2, the next (8) gives 3.
        assertEquals(listOf("a", "b", "c"), WhQuality.curate(items, 3).map { it.id })
        // Asking for more than there are returns everything that passes the shape/size rules.
        assertEquals(5, WhQuality.curate(items, 10).size)
    }

    @Test fun floorsGoDownAndEndLow() {
        assertEquals(listOf(25, 8, 3), WhQuality.floors(25))
        assertEquals(listOf(10, 3), WhQuality.floors(10))
    }

    // ---------- Tag check ----------

    @Test fun junkTagsAreRejected() {
        assertTrue(WhQuality.isJunk(listOf(WhTag(1216, "Minecraft"), WhTag(12459, "collage"))))
        assertTrue(WhQuality.isJunk(listOf(WhTag(1, "Memes"))))
        assertTrue(WhQuality.isJunk(listOf(WhTag(2, "dank meme"))))
        assertTrue(WhQuality.isJunk(listOf(WhTag(547, "cleavage"))))
        assertFalse(WhQuality.isJunk(listOf(WhTag(1216, "Minecraft"), WhTag(40, "water"), WhTag(903, "boat"))))
    }

    @Test fun mainTagMustBePresent() {
        val tags = listOf(WhTag(86347, "Hololive"), WhTag(2830, "beach"))
        assertFalse(WhQuality.hasTag(tags, 1394))
        assertTrue(WhQuality.hasTag(tags + WhTag(1394, "One Piece"), 1394))
        assertTrue("no curated tag: nothing to check", WhQuality.hasTag(tags, null))
    }

    @Test fun parsesTagsFromInfo() {
        val body = """{"data":{"id":"y8pmdg","path":"https://w.wallhaven.cc/full/y8/wallhaven-y8pmdg.jpg","purity":"sfw","category":"general",
            "tags":[{"id":1216,"name":"Minecraft","alias":"","category_id":5,"category":"Games","purity":"sfw","created_at":"2014-03-07 18:24:23"}]}}"""
        val w = WallhavenApi.json.decodeFromString(app.custo.feature.wallhaven.WhInfoResponse.serializer(), body).data
        assertEquals(listOf(1216L), w.tags.map { it.id })
        assertEquals("Minecraft", w.tags.single().name)
    }

    // ---------- "Not this" blocklist ----------

    @Test fun blockedPicturesAreDroppedEverywhere() {
        val items = listOf(wall("a"), wall("b"), wall("c"))
        assertEquals(listOf("a", "c"), WhQuality.withoutBlocked(items, setOf("b")).map { it.id })
        assertEquals(items, WhQuality.withoutBlocked(items, emptySet()))
        assertFalse(WhQuality.keep(wall("b"), 0, blocked = setOf("b")))
        assertEquals(listOf("a", "c"), WhQuality.curate(items, 3, blocked = setOf("b")).map { it.id })
    }
}
