package app.custo.feature.widgets

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.rememberActionRunner

@Composable
fun WidgetsScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val runner = rememberActionRunner()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    var placed by remember { mutableStateOf<List<Pair<PlacedWidget, WidgetStyle>>>(emptyList()) }
    val listenerOn = remember(tick) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }

    LaunchedEffect(tick) {
        placed = Widgets.placed(ctx).map { it to Widgets.style(ctx, it.glanceId) }
    }

    ScreenScaffold(title = "Widgets") {
        if (placed.isNotEmpty()) {
            item { SectionLabel("Placed") }
            placed.forEach { (w, style) ->
                item(key = "placed-${w.appWidgetId}") {
                    SectionCard(w.kind.label, Icons.Rounded.Edit, null, onClick = { nav.push(Route.WidgetEditor(w.appWidgetId)) }) {
                        WidgetPreview(w.kind, style, Modifier.fillMaxWidth().height(w.kind.previewHeight().dp))
                    }
                }
            }
        }
        item { SectionLabel("Add") }
        WidgetKind.entries.forEach { kind ->
            item(key = "add-${kind.name}") {
                SectionCard(kind.label, null, kind.description) {
                    WidgetPreview(kind, WidgetStyle(), Modifier.fillMaxWidth().height(kind.previewHeight().dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            if (!Widgets.requestPin(ctx, kind)) runner.message("Long-press your home screen → Widgets → Custo")
                        }) { Icon(Icons.Rounded.Add, null); Text("  Add") }
                    }
                }
            }
        }
        if (!listenerOn) item {
            SectionCard("Notification access", Icons.Rounded.NotificationsActive, "Needed for Now playing. Nothing is stored or sent.") {
                FilledTonalButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open settings") }
            }
        }
        item { Hint("Widgets follow your palette unless you pick colors.") }
    }
}
