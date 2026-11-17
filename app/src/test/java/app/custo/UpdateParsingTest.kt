package app.custo

import app.custo.feature.update.UpdateParsing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateParsingTest {
    private fun release(tag: String, assetUrl: String = "https://github.com/x1eddy/custo/releases/download/$tag/custo.apk", pre: Boolean = false) = """
        {"tag_name":"$tag","html_url":"https://github.com/x1eddy/custo/releases/tag/$tag","prerelease":$pre,"draft":false,
         "body":"**Big news**\n### New\n- Auto updates\n### Install\nDownload it",
         "assets":[{"name":"custo-x.apk","size":6600000,"browser_download_url":"$assetUrl","extra":1}],"other":true}
    """.trimIndent()

    @Test fun compareVersions() {
        assertTrue(UpdateParsing.compare("1.10.0", "1.9.2") > 0)
        assertTrue(UpdateParsing.compare("1.1", "1.1.0") == 0)
        assertTrue(UpdateParsing.compare("1.1.0", "1.1.0-beta") > 0)
        assertTrue(UpdateParsing.compare("1.0.1", "1.1.0") < 0)
    }

    @Test fun newerReleaseFound() {
        val info = UpdateParsing.newerRelease(release("v1.2.0"), "1.1.0")
        assertNotNull(info)
        assertEquals("1.2.0", info!!.version)
        assertEquals(6600000L, info.apkBytes)
        assertTrue(info.notes.contains("• Auto updates"))
        assertTrue(!info.notes.contains("Download it") && !info.notes.contains("**"))
    }

    @Test fun sameOrOlderIgnored() {
        assertNull(UpdateParsing.newerRelease(release("v1.1.0"), "1.1.0"))
        assertNull(UpdateParsing.newerRelease(release("v1.0.1"), "1.1.0"))
    }

    @Test fun prereleaseAndForeignAssetsIgnored() {
        assertNull(UpdateParsing.newerRelease(release("v2.0.0", pre = true), "1.1.0"))
        assertNull(UpdateParsing.newerRelease(release("v2.0.0", assetUrl = "https://evil.example/custo.apk"), "1.1.0"))
        assertNull(UpdateParsing.newerRelease("not json", "1.1.0"))
    }
}
