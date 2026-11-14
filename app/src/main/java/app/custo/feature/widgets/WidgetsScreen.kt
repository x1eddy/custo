package app.custo.feature.widgets

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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

    fun add(kind: WidgetKind) {
        if (!Widgets.requestPin(ctx, kind)) runner.message("Long-press your home screen → Widgets → Custo")
    }

    ScreenScaffold(title = "Widgets") {
        if (placed.isNotEmpty()) {
            item { SectionLabel("On your home screen") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(placed, key = { it.first.appWidgetId }) { (w, style) ->
                        GalleryCard(w.kind, style, Icons.Rounded.Edit, "Edit") { nav.push(Route.WidgetEditor(w.appWidgetId)) }
                    }
                }
            }
        }
        WidgetCategory.entries.forEach { cat ->
            val kinds = WidgetKind.entries.filter { it.category == cat }
            item(key = "cat-${cat.name}") { SectionLabel(cat.label) }
            item(key = "row-${cat.name}") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(kinds, key = { it.name }) { kind ->
                        GalleryCard(kind, WidgetStyle(), Icons.Rounded.Add, "Add") { add(kind) }
                    }
                }
            }
            if (cat == WidgetCategory.MEDIA && !listenerOn) item(key = "listener") { app.custo.feature.overlays.NotificationAccessCard("Needed for the Now playing widget. Nothing is stored or sent.") }
        }
        item { Hint("Widgets follow your palette unless you pick colors. Tap a placed widget to style it.") }
    }
}

@Composable
private fun GalleryCard(kind: WidgetKind, style: WidgetStyle, icon: androidx.compose.ui.graphics.vector.ImageVector, action: String, onClick: () -> Unit) {
    val wide = kind.previewHeight < 110 || kind == WidgetKind.MEDIA
    val width = if (wide) 280.dp else 200.dp
    Surface(
        Modifier.width(width).clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(12.dp)) {
            WidgetPreview(kind, style, Modifier.fillMaxWidth().height(kind.previewHeight.dp))
            Spacer(Modifier.height(10.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text(kind.label, style = MaterialTheme.typography.titleSmall)
                    Text(kind.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(icon, action, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
