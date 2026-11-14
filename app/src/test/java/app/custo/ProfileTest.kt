package app.custo

import app.custo.feature.backup.Profile
import app.custo.feature.widgets.ClockLook
import app.custo.feature.widgets.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileTest {
    @Test fun tagsRoundTrip() {
        listOf(true, 3, 9_000_000_000L, 1.5f, "hello:world").forEach { v ->
            assertEquals(v, Profile.untag(Profile.tag(v)!!))
        }
        assertNull(Profile.untag("x:1"))
        assertNull(Profile.untag("i:notanint"))
    }

    @Test fun profileRoundTrip() {
        val p = Profile(createdAt = 5, settings = mapOf("edge_enabled" to "b:true"), originals = mapOf("secure:clock_seconds" to "0"))
        assertEquals(p, Profile.decode(p.encode()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsForeignJson() {
        Profile.decode("""{"app":"other"}""")
    }

    @Test fun widgetStyleDefaultsOnBadJson() {
        assertEquals(WidgetStyle(), WidgetStyle.decode("{broken"))
        assertEquals(ClockLook.STACKED, WidgetStyle.decode(WidgetStyle(clock = ClockLook.STACKED).encode()).clock)
    }
}
