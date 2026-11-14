package app.custo.feature.widgetsets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import app.custo.feature.widgets.PlacedWidget
import app.custo.feature.widgets.WidgetKind
import app.custo.feature.widgets.WidgetStyle
import app.custo.feature.widgets.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One widget to pin, with the style it gets once it lands on the home screen. */
data class SetPinJob(val kind: WidgetKind, val style: WidgetStyle) {
    val label get() = kind.label
}

/** Tokens of pin requests the launcher confirmed, mapped to the new appWidgetId. */
object SetPinBus {
    private val _added = MutableStateFlow<Map<String, Int>>(emptyMap())
    val added: StateFlow<Map<String, Int>> = _added
    fun report(token: String, appWidgetId: Int) = _added.update { it + (token to appWidgetId) }
}

object SetPins {
    private const val ACTION = "app.custo.action.SET_WIDGET_PINNED"
    internal const val EXTRA_TOKEN = "token"
    internal const val EXTRA_KIND = "kind"
    internal const val EXTRA_STYLE = "style"

    fun newToken(): String = "pin_" + System.nanoTime().toString(36)

    /**
     * Asks the launcher to add [job]. When the user accepts, the launcher fires our callback with the
     * new widget's id and [SetPinReceiver] writes the themed style into it.
     */
    fun request(ctx: Context, job: SetPinJob, token: String): Boolean {
        val intent = Intent(ctx, SetPinReceiver::class.java)
            .setAction(ACTION)
            .putExtra(EXTRA_TOKEN, token)
            .putExtra(EXTRA_KIND, job.kind.name)
            .putExtra(EXTRA_STYLE, job.style.encode())
        // Mutable so the launcher can add EXTRA_APPWIDGET_ID; explicit, so nobody else can receive it.
        val pi = PendingIntent.getBroadcast(ctx, token.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        return Widgets.requestPin(ctx, job.kind, pi)
    }

    /** Writes [style] into a widget's Glance state and redraws it. */
    suspend fun style(ctx: Context, kind: WidgetKind, appWidgetId: Int, style: WidgetStyle) {
        val glanceId = GlanceAppWidgetManager(ctx).getGlanceIdBy(appWidgetId)
        Widgets.save(ctx, PlacedWidget(kind, glanceId, appWidgetId), style)
    }
}

/** Success callback of [SetPins.request]: style the widget the launcher just added. */
class SetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val token = intent.getStringExtra(SetPins.EXTRA_TOKEN) ?: return
        val kind = WidgetKind.entries.firstOrNull { it.name == intent.getStringExtra(SetPins.EXTRA_KIND) } ?: return
        val style = WidgetStyle.decode(intent.getStringExtra(SetPins.EXTRA_STYLE))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching { SetPins.style(context.applicationContext, kind, id, style) }
                SetPinBus.report(token, id)
            } finally {
                pending.finish()
            }
        }
    }
}
