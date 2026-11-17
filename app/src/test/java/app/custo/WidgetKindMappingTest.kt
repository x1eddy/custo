package app.custo

import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.Widgets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A placed widget's kind comes from its receiver (a manifest component, never renamed by R8), not
 * from the Glance widget class name: minified builds renamed those, so a To-do widget could be
 * taken for a Clock and its tap opened the wrong editor.
 */
class WidgetKindMappingTest {

    @Test fun everyKindHasItsOwnReceiver() {
        val receivers = WidgetKind.entries.map { it.receiver.name }
        assertEquals(receivers.size, receivers.toSet().size)
        WidgetKind.entries.forEach { assertEquals(it, Widgets.kindOfReceiver(it.receiver.name)) }
        assertNull(Widgets.kindOfReceiver("a.b"))
        assertNull(Widgets.kindOfReceiver(null))
    }

    @Test fun everyReceiverIsAWidgetInTheManifest() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        WidgetKind.entries.forEach { k ->
            val short = "." + k.receiver.name.removePrefix("app.custo.")
            val decl = Regex("<receiver android:name=\"" + Regex.escape(short) + "\"[\\s\\S]*?</receiver>").find(manifest)?.value
            assertTrue("${k.name}: $short not declared", decl != null)
            assertTrue("${k.name}: no APPWIDGET_UPDATE", decl!!.contains("android.appwidget.action.APPWIDGET_UPDATE"))
        }
    }

    @Test fun r8KeepsGlanceWidgetNamesStable() {
        val rules = File("proguard-rules.pro").readText()
        assertTrue(rules.contains("-keepnames class * extends androidx.glance.appwidget.GlanceAppWidget"))
        assertTrue(rules.contains("-keep class * implements androidx.glance.appwidget.action.ActionCallback"))
    }
}
