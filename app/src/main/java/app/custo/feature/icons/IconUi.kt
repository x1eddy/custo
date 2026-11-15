package app.custo.feature.icons

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.custo.container
import app.custo.feature.profiles.AppList
import app.custo.feature.profiles.LaunchableApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** One icon to put on the home screen. [render] runs off the main thread. */
class PinJob(
    val pkg: String,
    val label: String,
    val source: String,
    val style: IconStyle?,
    val adaptive: Boolean,
    /** Set to update a shortcut Custo made before instead of adding a new one. */
    val editId: String? = null,
    val render: suspend (Context) -> Bitmap,
)

/** Runs pin requests one after another: Android asks the user once per shortcut. */
class PinQueue {
    var jobs by mutableStateOf<List<PinJob>>(emptyList()); private set
    var index by mutableIntStateOf(0)
    var added by mutableIntStateOf(0)
    var skipped by mutableIntStateOf(0)
    var status by mutableStateOf("")
    var done by mutableStateOf(false)
    var stopped by mutableStateOf(false)
    var runId by mutableIntStateOf(0); private set

    fun start(list: List<PinJob>) {
        if (list.isEmpty()) return
        jobs = list; index = 0; added = 0; skipped = 0; status = ""; done = false; stopped = false; runId++
    }

    fun close() { jobs = emptyList(); done = false }
}

@Composable
fun rememberPinQueue(): PinQueue = remember { PinQueue() }

/** Progress dialog + the worker that walks the queue. Put it once on any screen that pins. */
@Composable
fun PinQueuePanel(queue: PinQueue) {
    val ctx = LocalContext.current
    val c = ctx.container
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(queue.runId) {
        if (queue.jobs.isEmpty()) return@LaunchedEffect
        if (!Shortcuts.supported(ctx)) {
            queue.status = "Your launcher doesn't let apps add shortcuts. Try another launcher."
            queue.done = true
            return@LaunchedEffect
        }
        for ((i, job) in queue.jobs.withIndex()) {
            if (queue.stopped) break
            queue.index = i
            queue.status = "Drawing ${job.label}…"
            val result = runCatching {
                val bmp = job.render(ctx)
                val id = job.editId ?: Shortcuts.newId(job.pkg)
                val entry = IconShortcut(id, job.pkg, job.label, job.source, job.style, System.currentTimeMillis())
                if (job.editId != null && job.editId in Shortcuts.pinnedIds(ctx)) {
                    // Already on the home screen: update in place, no prompt.
                    Shortcuts.update(ctx, id, job.pkg, job.label, bmp, job.adaptive)
                    c.icons.record(entry, bmp)
                    return@runCatching true
                }
                if (!Shortcuts.requestPin(ctx, id, job.pkg, job.label, bmp, job.adaptive)) return@runCatching false
                queue.status = "Tap Add on the prompt"
                // The launcher's prompt covers Custo; wait for it to close before the next one.
                val prompted = withTimeoutOrNull(2500) { lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.RESUMED) } } != null
                if (prompted) lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                delay(450)
                val pinned = id in Shortcuts.pinnedIds(ctx)
                if (pinned || !prompted) c.icons.record(entry, bmp)
                pinned || !prompted
            }
            if (result.getOrDefault(false)) queue.added++ else queue.skipped++
        }
        queue.done = true
        queue.status = ""
    }

    if (queue.jobs.isEmpty()) return
    val total = queue.jobs.size
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (queue.done) "All set" else "Adding icons") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!queue.done) {
                    val job = queue.jobs.getOrNull(queue.index)
                    Text("${queue.index + 1} of $total: ${job?.label.orEmpty()}", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { (queue.index + 0.5f) / total }, modifier = Modifier.fillMaxWidth())
                    Text(queue.status, style = MaterialTheme.typography.bodyMedium)
                    if (total > 1) Text(
                        "Android asks once for every icon. Tap Add each time and Custo moves on to the next.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (queue.status.isNotEmpty()) Text(queue.status)
                    Text("${queue.added} added" + if (queue.skipped > 0) ", ${queue.skipped} skipped" else "")
                    Text(
                        "The original icons stay where they were. Long-press one and pick Remove to tidy up. " +
                            "Some launchers add a small Custo badge to shortcuts. That's the launcher, not the icon.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (queue.done) TextButton(onClick = { queue.close() }) { Text("OK") }
            else TextButton(onClick = { queue.stopped = true }, enabled = !queue.stopped) { Text(if (queue.stopped) "Stopping…" else "Stop") }
        },
    )
}

/** Renders [style] for [pkg] in the background; null while drawing. */
@Composable
fun rememberStyledIcon(style: IconStyle, pkg: String?, label: String = "", size: Int = 192): Bitmap? {
    val ctx = LocalContext.current
    // Start from the cache so icons scrolled back into view don't flash a spinner.
    val b by produceState(IconRenderer.peek(style, pkg, label, size), style, pkg, label, size) {
        value = IconRenderer.peek(style, pkg, label, size) ?: run {
            delay(30) // let slider drags settle
            IconRenderer.render(ctx, style, pkg, label, size)
        }
    }
    return b
}

@Composable
fun IconBitmap(bitmap: Bitmap?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription, Modifier.fillMaxWidth().fillMaxHeight())
        else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

/** Searchable app list. Multi-select shows checkboxes and a Done button. */
@Composable
fun AppPickerDialog(
    title: String,
    multi: Boolean,
    onDismiss: () -> Unit,
    only: Set<String>? = null,
    onDone: (List<LaunchableApp>) -> Unit,
) {
    val ctx = LocalContext.current
    val apps by produceState<List<LaunchableApp>?>(null) { value = AppList.load(ctx) }
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.88f), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search apps") }, singleLine = true)
                val list = apps.orEmpty().filter { (only == null || it.pkg in only) && (query.isBlank() || it.label.contains(query, ignoreCase = true)) }
                if (multi) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${picked.size} picked", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { picked.clear(); picked.addAll(list.map { it.pkg }) }) { Text("All") }
                    TextButton(onClick = { picked.clear() }) { Text("None") }
                }
                Box(Modifier.weight(1f)) {
                    if (apps == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else if (list.isEmpty()) Text("No apps here", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn {
                        items(list, key = { it.pkg }) { app ->
                            val on = app.pkg in picked
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
                                    if (!multi) onDone(listOf(app)) else if (on) picked.remove(app.pkg) else picked.add(app.pkg)
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (app.icon != null) Image(app.icon.asImageBitmap(), null, Modifier.size(36.dp))
                                else Box(Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant))
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (multi) Checkbox(on, { if (it) picked.add(app.pkg) else picked.remove(app.pkg) })
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (multi) TextButton(
                        onClick = { onDone(apps.orEmpty().filter { it.pkg in picked }) },
                        enabled = picked.isNotEmpty(),
                    ) { Text("Done") }
                }
            }
        }
    }
}

/** A pin job that draws [style] for an app. */
fun styleJob(style: IconStyle, app: LaunchableApp, label: String = app.label, editId: String? = null) = PinJob(
    pkg = app.pkg, label = label, source = "style", style = style, adaptive = style.launcherShape, editId = editId,
) { ctx -> IconRenderer.render(ctx, style, app.pkg, app.label) }
