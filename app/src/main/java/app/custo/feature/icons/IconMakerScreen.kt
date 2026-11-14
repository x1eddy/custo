package app.custo.feature.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.feature.profiles.AppList
import app.custo.feature.profiles.LaunchableApp
import app.custo.nav.Navigator
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private enum class Slot(val label: String) { BG1("Back 1"), BG2("Back 2"), BG3("Back 3"), PATTERN("Pattern"), GLOW("Glow"), FG("Symbol") }

/**
 * DIY icon editor. [initial] (JSON) opens a given style, e.g. from a look; otherwise the last draft.
 * With [pickApps] the "apply to apps" picker opens right away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconMakerScreen(nav: Navigator, initial: String? = null, pickApps: Boolean = false) {
    val ctx = LocalContext.current
    val c = ctx.container
    val runner = rememberActionRunner()
    val queue = rememberPinQueue()
    val saved by c.icons.styles.collectAsStateWithLifecycle(emptyList())
    val looks by c.looks.saved.collectAsStateWithLifecycle(emptyList())

    var style by remember { mutableStateOf(IconStyle.decode(initial) ?: IconStyle()) }
    var loaded by remember { mutableStateOf(initial != null) }
    LaunchedEffect(Unit) { if (!loaded) { style = c.icons.draft(); loaded = true } }
    LaunchedEffect(style, loaded) { if (loaded && initial == null) { delay(500); c.icons.saveDraft(style) } }

    val apps by produceState<List<LaunchableApp>>(emptyList()) { value = AppList.load(ctx) }
    var slot by remember { mutableStateOf(Slot.BG1) }
    var bulk by remember { mutableStateOf(pickApps) }
    var one by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var attaching by remember { mutableStateOf(false) }

    PinQueuePanel(queue)
    if (bulk) AppPickerDialog("Re-skin which apps?", multi = true, onDismiss = { bulk = false }) { picked ->
        bulk = false
        val s = style.forBulk()
        queue.start(picked.map { styleJob(s, it) })
    }
    if (one) AppPickerDialog("Use for which app?", multi = false, onDismiss = { one = false }) { picked ->
        one = false
        queue.start(listOf(styleJob(style, picked.first())))
    }
    if (naming) NameDialog("Save style", "Style ${saved.size + 1}", onDismiss = { naming = false }) { name ->
        naming = false
        runner.run("Style saved") { c.icons.saveStyle(SavedStyle(name, style)) }
    }
    if (attaching) AlertDialog(
        onDismissRequest = { attaching = false },
        title = { Text("Add to a look") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (looks.isEmpty()) Text("Save a look on the Themes tab first. Then its icons can come along.")
                looks.forEach { look ->
                    Text(
                        look.name + if (look.icons != null) "  (has icons)" else "",
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
                            attaching = false
                            runner.run("Icons added to “${look.name}”") { c.looks.save(look.copy(icons = style)) }
                        }.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { attaching = false }) { Text("Close") } },
    )

    ScreenScaffold(title = "Icon maker", onBack = { nav.pop() }) {
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val big = rememberStyledIcon(style, previewPkg(style, ctx.packageName), "Custo", 432)
                    IconBitmap(big, Modifier.size(150.dp), "Preview")
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        apps.take(4).chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { a -> IconBitmap(rememberStyledIcon(style.forBulk(), a.pkg, a.label, 144), Modifier.size(56.dp), a.label) }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { style = shuffle(style) }) { Icon(Icons.Rounded.Casino, null); Text("  Shuffle") }
                }
                Hint("Big: this style. Small: how your apps look with it.")
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(PRESET_STYLES + saved, key = { (if (it in PRESET_STYLES) "p:" else "s:") + it.name }) { s ->
                    val custom = s !in PRESET_STYLES
                    Column(Modifier.width(72.dp).clip(MaterialTheme.shapes.medium).clickable { style = s.style }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            IconBitmap(rememberStyledIcon(s.style, previewPkg(s.style, ctx.packageName), "Custo", 144), Modifier.size(60.dp), s.name)
                            if (custom) Box(
                                Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { runner.run { c.icons.deleteStyle(s.name) } },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Close, "Delete", Modifier.size(14.dp)) }
                        }
                        Text(s.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        item { SectionLabel("Shape") }
        item {
            SectionCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconShape.entries.forEach { s -> FilterChip(style.shape == s, { style = style.copy(shape = s) }, label = { Text(s.label) }) }
                }
                SwitchRow(
                    "Let the launcher pick the shape",
                    "Fills the whole icon and lets your launcher cut it, like normal app icons. Turn off to keep your shape.",
                    style.launcherShape,
                ) { style = style.copy(launcherShape = it) }
            }
        }

        item { SectionLabel("Colors") }
        item {
            SectionCard {
                val slots = buildList {
                    add(Slot.BG1); if (style.bg.size > 1) add(Slot.BG2); if (style.bg.size > 2) add(Slot.BG3)
                    if (style.pattern != IconPattern.NONE) add(Slot.PATTERN)
                    if (style.glow > 0f) add(Slot.GLOW)
                    if (style.fg != FgMode.APP_ORIGINAL) add(Slot.FG)
                }
                val active = if (slot in slots) slot else Slot.BG1
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    slots.forEach { sl -> SlotDot(sl.label, Color(colorOf(style, sl)), sl == active) { slot = sl } }
                    if (style.bg.size < 3) IconButton(onClick = { style = style.copy(bg = style.bg + style.bg.last()) }) { Icon(Icons.Rounded.Add, "Add a background color") }
                    if (style.bg.size > 1) IconButton(onClick = { style = style.copy(bg = style.bg.dropLast(1)) }) { Icon(Icons.Rounded.Remove, "Remove a background color") }
                }
                ColorPicker(Color(colorOf(style, active)), { style = withColor(style, active, it.toArgb()) })
                if (style.bg.size > 1) LabeledSlider("Gradient angle", style.angle / 360f) { style = style.copy(angle = (it * 360f).roundToInt().toFloat()) }
            }
        }

        item { SectionLabel("Pattern") }
        item {
            SectionCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconPattern.entries.forEach { p -> FilterChip(style.pattern == p, { style = style.copy(pattern = p) }, label = { Text(p.label) }) }
                }
                if (style.pattern != IconPattern.NONE) {
                    LabeledSlider("Size", style.patternSize) { style = style.copy(patternSize = it) }
                    LabeledSlider("Strength", style.patternOpacity) { style = style.copy(patternOpacity = it) }
                    if (style.pattern == IconPattern.CONFETTI) TextButton(onClick = { style = style.copy(seed = Random.nextInt()) }) { Text("Scatter again") }
                }
            }
        }

        item { SectionLabel("Symbol") }
        item {
            SectionCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FgMode.entries.forEach { m -> FilterChip(style.fg == m, { style = style.copy(fg = m) }, label = { Text(m.label) }) }
                }
                when (style.fg) {
                    FgMode.APP_TINT -> Hint("Each app's own symbol in one color. Uses the app's themed icon when it has one.")
                    FgMode.APP_ORIGINAL -> Hint("Each app's normal icon, placed on your background.")
                    FgMode.GLYPH -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GLYPHS.forEach { (name, v) ->
                                val on = style.glyph == name
                                Box(
                                    Modifier.size(42.dp).clip(MaterialTheme.shapes.small)
                                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable { style = style.copy(glyph = name) },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(v, name, Modifier.size(24.dp)) }
                            }
                        }
                        Hint("When applied to many apps, each app gets its own symbol instead.")
                    }
                    FgMode.TEXT -> {
                        OutlinedTextField(style.text, { t -> style = style.copy(text = t.take(2)) }, label = { Text("1–2 letters") }, singleLine = true)
                        Hint("When applied to many apps, each app gets its own initials.")
                    }
                }
                LabeledSlider("Size", (style.fgScale - 0.3f) / 0.6f) { style = style.copy(fgScale = 0.3f + it * 0.6f) }
                LabeledSlider("Glow", style.glow) { style = style.copy(glow = it) }
                LabeledSlider("Shadow", style.shadow) { style = style.copy(shadow = it) }
            }
        }

        item { SectionLabel("Use it") }
        item {
            SectionCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { bulk = true }) { Icon(Icons.Rounded.SelectAll, null); Text("  Apply to apps") }
                    OutlinedButton(onClick = { one = true }) { Icon(Icons.AutoMirrored.Rounded.AddToHomeScreen, null); Text("  One app") }
                    OutlinedButton(onClick = { naming = true }) { Icon(Icons.Rounded.Bookmark, null); Text("  Save style") }
                    OutlinedButton(onClick = { attaching = true }) { Icon(Icons.Rounded.Palette, null); Text("  Add to a look") }
                }
                Hint("Your launcher asks once per app. Custo lines them up so you just tap Add each time.")
            }
        }
    }
}

/** Symbol/letters styles preview on their own; app styles preview on [fallback]'s icon. */
private fun previewPkg(style: IconStyle, fallback: String): String? = when (style.fg) {
    FgMode.GLYPH, FgMode.TEXT -> null
    else -> fallback
}

private fun colorOf(s: IconStyle, slot: Slot): Int = when (slot) {
    Slot.BG1 -> s.bg.getOrElse(0) { 0xFF888888.toInt() }
    Slot.BG2 -> s.bg.getOrElse(1) { s.bg.first() }
    Slot.BG3 -> s.bg.getOrElse(2) { s.bg.last() }
    Slot.PATTERN -> s.patternColor
    Slot.GLOW -> s.glowColor
    Slot.FG -> s.fgColor
}

private fun withColor(s: IconStyle, slot: Slot, color: Int): IconStyle = when (slot) {
    Slot.BG1, Slot.BG2, Slot.BG3 -> {
        val i = slot.ordinal
        s.copy(bg = s.bg.toMutableList().also { if (i < it.size) it[i] = color })
    }
    Slot.PATTERN -> s.copy(patternColor = color)
    Slot.GLOW -> s.copy(glowColor = color)
    Slot.FG -> s.copy(fgColor = color)
}

private fun shuffle(s: IconStyle): IconStyle {
    val r = Random(System.nanoTime())
    val hue = r.nextFloat() * 360f
    fun hsl(h: Float, sat: Float, l: Float) = app.custo.feature.systemui.Tonal.fromHsl((h + 360f) % 360f, sat, l)
    val n = 1 + r.nextInt(3)
    val bg = List(n) { i -> hsl(hue + i * (25f + r.nextFloat() * 40f), 0.55f + r.nextFloat() * 0.4f, 0.35f + r.nextFloat() * 0.3f) }
    val light = r.nextFloat() < 0.2f
    return s.copy(
        shape = IconShape.entries.random(r),
        bg = if (light) listOf(hsl(hue, 0.4f, 0.93f)) else bg,
        angle = (r.nextInt(8) * 45).toFloat(),
        pattern = IconPattern.entries.random(r),
        patternOpacity = 0.12f + r.nextFloat() * 0.25f,
        patternSize = r.nextFloat(),
        fgColor = if (light) hsl(hue, 0.6f, 0.3f) else 0xFFFFFFFF.toInt(),
        seed = r.nextInt(),
    )
}

@Composable
private fun SlotDot(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(color)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value.coerceIn(0f, 1f), onChange)
    }
}

@Composable
internal fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it.take(30) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
