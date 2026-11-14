package app.custo.feature.widgetsets

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.custo.container
import app.custo.feature.themes.WallTarget
import app.custo.feature.widgets.PlacedWidget
import app.custo.feature.widgets.WidgetPreview
import app.custo.feature.widgets.WidgetStyle
import app.custo.feature.widgets.Widgets
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull

// ---------- Shared pieces ----------

/** The set's phone-shaped preview, rendered in the background at this size and cached. */
@Composable
fun SetPreviewImage(theme: WidgetTheme, modifier: Modifier = Modifier, corner: Int = 18) {
    val shape = RoundedCornerShape(corner.dp)
    BoxWithConstraints(
        modifier.aspectRatio(1f / SetPreviews.ASPECT).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        val px = with(LocalDensity.current) { maxWidth.roundToPx() }
        val bmp by produceState<Bitmap?>(null, theme.id, px) { value = SetPreviews.get(theme, px) }
        Crossfade(bmp, label = "set") { b ->
            if (b != null) Image(b.asImageBitmap(), theme.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** Big card that leads to the gallery; used on the Widgets and Themes tabs. */
@Composable
fun WidgetSetsEntryCard(onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Widget sets", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "A wallpaper and matching widgets, in one tap.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)); Text(" Browse", maxLines = 1, softWrap = false)
                }
            }
            Box(Modifier.width(132.dp).height(130.dp)) {
                WidgetThemes.all.take(3).forEachIndexed { i, t ->
                    SetPreviewImage(t, Modifier.width(60.dp).offset(x = (i * 36).dp, y = (if (i == 1) 0 else 8).dp), corner = 10)
                }
            }
        }
    }
}

// ---------- Gallery ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetSetsScreen(nav: Navigator) {
    var filter by remember { mutableStateOf<ThemeCategory?>(null) }
    ScreenScaffold(title = "Widget sets", onBack = { nav.pop() }) {
        item { Hint("Each set is a wallpaper plus widgets drawn in one style. Tap one to see it up close.") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == null, { filter = null }, { Text("All") })
                WidgetThemes.categories.forEach { c -> FilterChip(filter == c, { filter = c }, { Text(c.label) }) }
            }
        }
        val groups = WidgetThemes.categories.filter { filter == null || it == filter }
        groups.forEach { cat ->
            if (filter == null) item(key = "cat-${cat.name}") { SectionLabel(cat.label) }
            WidgetThemes.all.filter { it.category == cat }.chunked(2).forEach { pair ->
                item(key = "row-" + pair.joinToString { it.id }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { t -> SetCard(t, Modifier.weight(1f)) { nav.push(Route.WidgetSet(t.id)) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { Hint("More sets are on the way.") }
    }
}

@Composable
private fun SetCard(theme: WidgetTheme, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick).padding(4.dp)) {
        SetPreviewImage(theme, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(theme.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${theme.category.label} · ${theme.widgets.size} widgets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Detail ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetSetScreen(id: String, nav: Navigator) {
    val theme = WidgetThemes.byId(id)
    if (theme == null) {
        ScreenScaffold(title = "Widget set", onBack = { nav.pop() }) { item { Hint("This set isn't here anymore.") } }
        return
    }
    val ctx = LocalContext.current
    val c = ctx.container
    val runner = rememberActionRunner()
    val pinner = remember { SetPinner() }
    var applyOpen by remember { mutableStateOf(false) }
    var wallOpen by remember { mutableStateOf(false) }
    var restyleOpen by remember { mutableStateOf(false) }

    SetPinPanel(pinner, theme) { nav.push(Route.IconMaker(theme.icons!!.encode(), pickApps = true)) }

    if (applyOpen) ApplySetDialog(theme, onDismiss = { applyOpen = false }) { wall, target, picked ->
        applyOpen = false
        runner.run {
            if (wall) c.wallpapers.applyPainted(target) { cv, w, h -> theme.wallpaper.paint(cv, w, h) }
            val today = java.time.LocalDate.now()
            val jobs = picked.map { i -> SetPinJob(theme.widgets[i].kind, theme.styleForSetWidget(i, today)) }
            if (jobs.isEmpty()) runner.message("Wallpaper set") else pinner.start(jobs)
        }
    }
    if (wallOpen) WallTargetDialog(onDismiss = { wallOpen = false }) { target ->
        wallOpen = false
        runner.run("Wallpaper set") { c.wallpapers.applyPainted(target) { cv, w, h -> theme.wallpaper.paint(cv, w, h) } }
    }
    if (restyleOpen) RestyleDialog(theme, onDismiss = { restyleOpen = false }) { chosen ->
        restyleOpen = false
        runner.run(if (chosen.size == 1) "1 widget restyled" else "${chosen.size} widgets restyled") {
            chosen.forEachIndexed { i, (w, style) ->
                val slot = chosen.take(i).count { it.first.kind == w.kind }
                Widgets.save(ctx, w, theme.styleFor(w.kind, slot, style))
            }
        }
    }

    ScreenScaffold(title = theme.name, onBack = { nav.pop() }) {
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SetPreviewImage(theme, Modifier.fillMaxWidth(0.64f), corner = 26)
            }
        }
        item { Text(theme.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp)) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { applyOpen = true }) { Icon(Icons.Rounded.AutoAwesome, null); Text("  Apply set") }
                OutlinedButton(onClick = { wallOpen = true }) { Icon(Icons.Rounded.Wallpaper, null); Text("  Wallpaper only") }
                OutlinedButton(onClick = { restyleOpen = true }) { Icon(Icons.Rounded.Brush, null); Text("  Restyle my widgets") }
            }
        }
        item { SectionLabel("In this set") }
        item {
            val today = remember { java.time.LocalDate.now() }
            // Every widget at its true proportions on a 4-column grid, so the whole set is visible.
            androidx.compose.foundation.layout.BoxWithConstraints {
                val gap = 10.dp
                val cell = (maxWidth - gap * 3) / 4
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    theme.widgets.forEachIndexed { i, sw ->
                        val style = remember(theme.id, i) { theme.styleForSetWidget(i, today) }
                        val cols = sw.cols.coerceIn(1, 4)
                        Column(Modifier.width(cell * cols + gap * (cols - 1))) {
                            Box(
                                Modifier.fillMaxWidth().height(cell * sw.rows.coerceAtLeast(1) * 0.9f)
                                    .clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            ) { WidgetPreview(sw.kind, style, Modifier.fillMaxSize()) }
                            Text(sw.kind.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                            if (!sw.kind.available(ctx)) Text("Not on this phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (theme.icons != null) item {
            SectionCard("Matching icons", Icons.Rounded.Apps, "This set has its own icon style. Pick the apps to give it.") {
                FilledTonalButton(onClick = { nav.push(Route.IconMaker(theme.icons.encode(), pickApps = true)) }) { Text("Pick apps") }
            }
        }
        item {
            Hint(
                "Apply set asks your launcher to add each widget, one at a time. Drag and resize them however you like. " +
                    "Restyle gives widgets already on your home screen this look.",
            )
        }
    }
}

@Composable
private fun ApplySetDialog(theme: WidgetTheme, onDismiss: () -> Unit, onApply: (wall: Boolean, target: WallTarget, picked: List<Int>) -> Unit) {
    val ctx = LocalContext.current
    var wall by remember { mutableStateOf(true) }
    var target by remember { mutableStateOf(WallTarget.HOME) }
    val picked = remember { mutableStateListOf<Int>().apply { addAll(theme.widgets.indices.filter { theme.widgets[it].kind.available(ctx) }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply ${theme.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SwitchRow("Wallpaper", null, wall) { wall = it }
                if (wall) app.custo.feature.widgets.Segments(listOf(WallTarget.HOME, WallTarget.BOTH), target, { if (it == WallTarget.HOME) "Home" else "Home & lock" }) { target = it }
                Text("Widgets", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                theme.widgets.forEachIndexed { i, sw ->
                    val ok = sw.kind.available(ctx)
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .clickable(enabled = ok) { if (i in picked) picked.remove(i) else picked.add(i) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(i in picked && ok, null, enabled = ok)
                        Text(sw.kind.label, Modifier.weight(1f))
                        Text(if (ok) "${sw.cols}×${sw.rows}" else "Not on this phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Hint("Android asks once for every widget. Tap Add each time.")
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(wall, target, theme.widgets.indices.filter { it in picked }) }, enabled = wall || picked.isNotEmpty()) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WallTargetDialog(onDismiss: () -> Unit, onPick: (WallTarget) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set wallpaper") },
        text = { Text("Where should it go?") },
        confirmButton = { TextButton(onClick = { onPick(WallTarget.BOTH) }) { Text("Home & lock") } },
        dismissButton = { TextButton(onClick = { onPick(WallTarget.HOME) }) { Text("Home") } },
    )
}

@Composable
private fun RestyleDialog(theme: WidgetTheme, onDismiss: () -> Unit, onRestyle: (List<Pair<PlacedWidget, WidgetStyle>>) -> Unit) {
    val ctx = LocalContext.current
    val placed by produceState<List<Pair<PlacedWidget, WidgetStyle>>?>(null) {
        value = Widgets.placed(ctx).map { it to Widgets.style(ctx, it.glanceId) }
    }
    val picked = remember { mutableStateListOf<Int>() }
    LaunchedEffect(placed) { placed?.let { list -> picked.clear(); picked.addAll(list.map { it.first.appWidgetId }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restyle my widgets") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val list = placed
                when {
                    list == null -> Text("Looking for your widgets…")
                    list.isEmpty() -> Text("No Custo widgets on your home screen yet.")
                    else -> {
                        Text("Pick which widgets get the ${theme.name} look. What they show stays the same.", style = MaterialTheme.typography.bodyMedium)
                        list.forEach { (w, style) ->
                            val on = w.appWidgetId in picked
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                    .clickable { if (on) picked.remove(w.appWidgetId) else picked.add(w.appWidgetId) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(on, null)
                                Text(w.kind.label, Modifier.weight(1f))
                                WidgetThemes.byId(style.themeId)?.let {
                                    Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRestyle(placed.orEmpty().filter { it.first.appWidgetId in picked }) },
                enabled = picked.isNotEmpty() && !placed.isNullOrEmpty(),
            ) { Text("Restyle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------- Pinning several widgets ----------

/** Holds one "add these widgets" run; [SetPinPanel] does the work and shows progress. */
class SetPinner {
    var jobs by mutableStateOf<List<SetPinJob>>(emptyList()); private set
    var run by mutableStateOf(PinRun(0))
    var runId by mutableIntStateOf(0); private set
    var status by mutableStateOf("")
    internal val signals = MutableSharedFlow<PinEvent>(extraBufferCapacity = 4)

    fun start(list: List<SetPinJob>) {
        if (list.isEmpty()) return
        jobs = list; run = PinRun(list.size); status = ""; runId++
    }

    fun skip() { signals.tryEmit(PinEvent.Skipped) }
    fun stop() { run = run.on(PinEvent.Stop); signals.tryEmit(PinEvent.Stop) }
    fun close() { jobs = emptyList() }
}

@Composable
fun SetPinPanel(pinner: SetPinner, theme: WidgetTheme, onMatchIcons: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(pinner.runId) {
        if (pinner.jobs.isEmpty()) return@LaunchedEffect
        if (!Widgets.pinSupported(ctx)) { pinner.run = pinner.run.on(PinEvent.Unsupported); return@LaunchedEffect }
        while (!pinner.run.done) {
            val job = pinner.jobs[pinner.run.index]
            val token = SetPins.newToken()
            pinner.status = "Asking for ${job.label}…"
            val asked = runCatching { SetPins.request(ctx, job, token) }.getOrDefault(false)
            if (!asked) { pinner.run = pinner.run.on(PinEvent.Skipped); continue }
            pinner.run = pinner.run.on(PinEvent.Asked)
            pinner.status = "Tap Add on the prompt"
            val outcome = coroutineScope {
                withTimeoutOrNull(120_000) {
                    merge(
                        SetPinBus.added.filter { token in it }.map { PinEvent.Added },
                        pinner.signals,
                        flow {
                            // The launcher's prompt covers Custo. Once it closes without a callback, move on.
                            val prompted = withTimeoutOrNull(2500) { lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.RESUMED) } } != null
                            if (prompted) {
                                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                                delay(4000)
                            } else delay(30_000)
                            emit(PinEvent.Skipped)
                        },
                    ).first()
                } ?: PinEvent.Skipped
            }
            pinner.run = pinner.run.on(outcome)
            delay(350)
        }
        pinner.status = ""
    }

    if (pinner.jobs.isEmpty()) return
    val run = pinner.run
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (!run.done) "Adding widgets" else if (run.stopped) "Stopped" else "All set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!run.done) {
                    val job = pinner.jobs.getOrNull(run.index)
                    Text("${run.index + 1} of ${run.total}: ${job?.label.orEmpty()}", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { (run.index + 0.5f) / run.total }, modifier = Modifier.fillMaxWidth())
                    Text(pinner.status, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Android asks once for every widget. Tap Add each time and Custo moves on to the next.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(run.summary())
                    if (run.added > 0) Text(
                        "Long-press a widget to move or resize it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (run.done) TextButton(onClick = { pinner.close() }) { Text("OK") }
            else TextButton(onClick = { pinner.skip() }, enabled = run.phase == PinPhase.WAITING) { Text("Skip") }
        },
        dismissButton = {
            if (!run.done) TextButton(onClick = { pinner.stop() }) { Text("Stop") }
            else if (theme.icons != null && run.added > 0) TextButton(onClick = { pinner.close(); onMatchIcons() }) { Text("Match icons") }
        },
    )
}
