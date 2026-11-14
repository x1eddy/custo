package app.custo

import app.custo.shizuku.ShellCommands
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.shizuku.ShellResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellCommandsTest {
    @Test fun quoteEscapesSingleQuotes() {
        assertEquals("'it'\\''s'", ShellCommands.quote("it's"))
        assertEquals("'a b;rm -rf /'", ShellCommands.quote("a b;rm -rf /"))
    }

    @Test fun settingsPutQuotesKeyAndValue() {
        assertEquals("settings put secure 'icon_blacklist' 'alarm_clock,vpn'",
            ShellCommands.settingsPut(Namespace.SECURE, "icon_blacklist", "alarm_clock,vpn"))
    }

    @Test fun jsonValueSurvivesQuoting() {
        val cmd = ShellCommands.settingsPut(Namespace.SECURE, "k", """{"a":"b"}""")
        assertEquals("""settings put secure 'k' '{"a":"b"}'""", cmd)
    }

    @Test fun fabricatedIdsAreShellOwnedAndPrefixed() {
        assertEquals("com.android.shell:custo_a1_500", ShellCommands.fabricatedId("a1_500"))
        assertEquals("0xff6d4aff", ShellCommands.colorArg(0xFF6D4AFF.toInt()))
    }

    @Test fun parseSettingValueTreatsNullLiteralAsMissing() {
        assertNull(ShellCommands.parseSettingValue("null\n"))
        assertNull(ShellCommands.parseSettingValue(""))
        assertEquals("1", ShellCommands.parseSettingValue("1\n"))
    }

    @Test fun shellResultParsing() {
        assertEquals(ShellResult(0, "hello\n"), ShellResult.parse("0\nhello\n"))
        assertEquals(ShellResult(255, ""), ShellResult.parse("255"))
        assertEquals(-1, ShellResult.parse("x\nboom").code)
    }

    @Test fun errorPicksTheExceptionMessage() {
        val out = "Exception occurred while executing 'put':\njava.lang.IllegalArgumentException: Invalid value: 5 for setting: x\n\tat a.b(C.java:1)\n"
        assertEquals("Invalid value: 5 for setting: x", ShellResult(255, out).error)
        assertEquals("Command failed (code 1)", ShellResult(1, "").error)
    }
}
