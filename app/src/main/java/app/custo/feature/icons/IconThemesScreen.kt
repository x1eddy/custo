package app.custo.feature.icons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.custo.feature.profiles.AppList
import app.custo.feature.profiles.LaunchableApp
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.ui.components.ScreenScaffold

/** Picture-first gallery: every theme shown on the user's own apps. */
@Composable
fun IconThemesScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val apps by produceState<List<LaunchableApp>>(emptyList()) { value = AppList.load(ctx) }
    val queue = rememberPinQueue()
    var open by remember { mutableStateOf<IconTheme?>(null) }
    var picking by remember { mutableStateOf<IconTheme?>(null) }

    PinQueuePanel(queue)
    picking?.let { t ->
        AppPickerDialog("${t.name}: which apps?", multi = true, onDismiss = { picking = null }) { picked ->
            picking = null
            queue.start(styleJobs(t.style, picked))
        }
    }
    open?.let { t ->
        ThemePreviewDialog(
            t.name, t.style, apps, onDismiss = { open = null },
            onUse = { open = null; picking = t },
            onEdit = { open = null; nav.push(Route.IconMaker(t.style.encode())) },
        )
    }

    ScreenScaffold(title = "Icon themes", onBack = { nav.pop() }) {
        items(ICON_THEMES.chunked(2), key = { row -> row.first().id }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t -> ThemeCard(t.name, t.style, apps, Modifier.weight(1f)) { open = t } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A theme as a 4×4 grid of the user's apps, with its name under it. */
@Composable
fun ThemeCard(name: String, style: IconStyle, apps: List<LaunchableApp>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        MockHomeGrid(apps, style, Modifier.fillMaxWidth(), cols = 4, rows = 4, renderPx = 96)
        Text(
            name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
    }
}

/** Full-screen look at a style on a pretend home screen, with the two things to do next. */
@Composable
fun ThemePreviewDialog(
    name: String,
    style: IconStyle,
    apps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onUse: () -> Unit,
    onEdit: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                }
                MockHome(apps, { style }, Modifier.fillMaxWidth().weight(1f, fill = false), rows = 5, renderPx = 160)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onUse, Modifier.weight(1f)) { Icon(Icons.Rounded.SelectAll, null); Text("  Apply", maxLines = 1) }
                    OutlinedButton(onClick = onEdit, Modifier.weight(1f)) { Icon(Icons.Rounded.Tune, null); Text("  Customize", maxLines = 1) }
                }
            }
        }
    }
}
