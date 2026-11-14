package app.custo.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

enum class WidgetKind(val label: String, val description: String, val receiver: Class<out GlanceAppWidgetReceiver>) {
    CLOCK("Clock", "Big, stacked or minimal clock with date", ClockReceiver::class.java),
    BATTERY("Battery", "Charge level with a progress bar", BatteryReceiver::class.java),
    MEDIA("Now playing", "Album art and playback controls", MediaReceiver::class.java),
    NOTE("Note", "A sticky note on your home screen", NoteReceiver::class.java);

    fun widget(): GlanceAppWidget = when (this) {
        CLOCK -> ClockWidget()
        BATTERY -> BatteryWidget()
        MEDIA -> MediaWidget()
        NOTE -> NoteWidget()
    }
}

data class PlacedWidget(val kind: WidgetKind, val glanceId: GlanceId, val appWidgetId: Int)

object Widgets {
    val STYLE_KEY = stringPreferencesKey("style")

    suspend fun placed(context: Context): List<PlacedWidget> {
        val gm = GlanceAppWidgetManager(context)
        return WidgetKind.entries.flatMap { kind ->
            gm.getGlanceIds(kind.widget().javaClass).map { PlacedWidget(kind, it, gm.getAppWidgetId(it)) }
        }
    }

    suspend fun find(context: Context, appWidgetId: Int): PlacedWidget? = placed(context).firstOrNull { it.appWidgetId == appWidgetId }

    suspend fun style(context: Context, id: GlanceId): WidgetStyle =
        WidgetStyle.decode(getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)[STYLE_KEY])

    suspend fun save(context: Context, w: PlacedWidget, style: WidgetStyle) {
        updateAppWidgetState(context, w.glanceId) { it[STYLE_KEY] = style.encode() }
        w.kind.widget().update(context, w.glanceId)
    }

    fun requestPin(context: Context, kind: WidgetKind): Boolean {
        val awm = AppWidgetManager.getInstance(context)
        if (!awm.isRequestPinAppWidgetSupported) return false
        return awm.requestPinAppWidget(ComponentName(context, kind.receiver), null, null)
    }

    suspend fun refresh(context: Context, kind: WidgetKind) = runCatching { kind.widget().updateAll(context) }

    suspend fun refreshAll(context: Context) {
        WidgetKind.entries.forEach { refresh(context, it) }
    }
}
