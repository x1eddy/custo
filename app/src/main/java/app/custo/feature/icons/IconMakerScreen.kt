package app.custo.feature.icons

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.feature.profiles.AppList
import app.custo.feature.profiles.LaunchableApp
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.ThumbPicker
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random

private enum class Section(val label: String, val icon: ImageVector) {
    SHAPE("Shape", Icons.Rounded.Category),
    BACK("Colors", Icons.Rounded.FormatColorFill),
    PICTURE("Picture", Icons.Rounded.Image),
    SYMBOL("Symbol", Icons.Rounded.Star),
    EFFECTS("Effects", Icons.Rounded.AutoAwesome),
    STICKER("Sticker", Icons.Rounded.Favorite),
    MIX("Mix", Icons.Rounded.Shuffle),
}

private enum class Effect(val label: String) { NONE("Flat"), GLASS("Glass"), SOFT("Soft"), DEPTH("3-D"), NEON("Neon"), EMBOSS("Pressed") }

private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFF111111, 0xFF8A93A6, 0xFFFF4D8D, 0xFFFF6B6B, 0xFFFFA36B, 0xFFFFD60A, 0xFFA8E063,
    0xFF2DFFB3, 0xFF00D9F5, 0xFF4A90C2, 0xFF6D4AFF, 0xFFA78BFA, 0xFFFFD6E8, 0xFFF4EEDF, 0xFFD4AF37,
).map { it.toInt() }

/** One editable color: its dot label, current value, and how to write it back. */
private class ColorSlot(val label: String, val color: Int, val set: (Int) -> Unit)

/**
 * DIY icon editor. [initial] (JSON) opens a given style, e.g. from a theme; otherwise the last draft.
 * With [pickApps] the "apply to apps" picker opens right away. The pretend home screen on top stays
 * in view while editing; tapping an app there styles just that app.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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

    // Per-app tweaks for the bulk apply: package -> its own style.
    val overrides = remember { mutableStateMapOf<String, IconStyle>() }
    var editing by remember { mutableStateOf<String?>(null) }
    val current = editing?.let { overrides[it] } ?: style
    fun set(f: (IconStyle) -> IconStyle) {
        val e = editing
        if (e != null) overrides[e] = f(overrides[e] ?: style) else style = f(style)
    }

    val apps by produceState<List<LaunchableApp>>(emptyList()) { value = AppList.load(ctx) }
    var section by remember { mutableStateOf(Section.SHAPE) }
    var expanded by remember { mutableStateOf(true) }
    var bulk by remember { mutableStateOf(pickApps) }
    var one by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var attaching by remember { mutableStateOf(false) }

    // Renders follow the edits once the finger rests, not on every slider frame.
    val shown = rememberDebounced(style to overrides.toMap(), 90)
    val thumbBase = rememberDebounced(current, 160)
    val demoPkg = editing ?: ctx.packageName
    val editingLabel = apps.firstOrNull { it.pkg == editing }?.label

    PinQueuePanel(queue)
    if (bulk) AppPickerDialog("Re-skin which apps?", multi = true, onDismiss = { bulk = false }) { picked ->
        bulk = false
        queue.start(styleJobs(style, picked, overrides.toMap()))
    }
    if (one) AppPickerDialog("Use for which app?", multi = false, onDismiss = { one = false }) { picked ->
        one = false
        val a = picked.first()
        queue.start(listOf(styleJob(overrides[a.pkg] ?: style, a)))
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
                if (looks.isEmpty()) Text("Save a look in Wallpapers → Make your own first. Then its icons can come along.")
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

    ScreenScaffold(title = "Icon maker", onBack = { nav.pop() }, actions = {
        app.custo.ui.components.InfoButton(
            "Icon maker",
            "The pretend home screen shows your apps with this style. Tap one of them to give just that app its own tweaks; tap it again to go back to all apps.\n\n" +
                "Launcher shape fills the whole icon and lets your launcher cut it, like normal app icons. Turn it off to keep your shape.\n\n" +
                "Puzzle spreads one picture across all the apps you apply it to, in the order you pick them.\n\n" +
                "Your launcher asks once per app. Custo lines them up so you just tap Add each time.",
        )
    }) {
        stickyHeader(key = "preview") {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                // The header sticks under the status bar, so it keeps clear of it.
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MockHome(
                        apps, { a -> shown.second[a.pkg] ?: shown.first }, Modifier.fillMaxWidth(),
                        rows = if (expanded) 5 else 2, renderPx = 144, selected = editing,
                        onTap = { a ->
                            if (editing == a.pkg) editing = null
                            else { if (a.pkg !in overrides) overrides[a.pkg] = style; editing = a.pkg }
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (editingLabel != null) InputChip(
                            selected = true, onClick = { editing = null },
                            label = { Text(editingLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { Icon(Icons.Rounded.Close, "All apps", Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f, fill = false),
                        ) else Spacer(Modifier.weight(1f))
                        if (editingLabel != null) Spacer(Modifier.weight(1f))
                        IconButton(onClick = { set { shuffle(it) } }) { Icon(Icons.Rounded.Casino, "Shuffle") }
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Smaller preview" else "Bigger preview")
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(Section.entries) { sec ->
                            FilterChip(
                                selected = sec == section, onClick = { section = sec },
                                label = { Text(sec.label) }, leadingIcon = { Icon(sec.icon, null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "looks") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Column(
                        Modifier.width(72.dp).clip(MaterialTheme.shapes.medium).clickable { nav.push(Route.IconThemes) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text("Themes", style = MaterialTheme.typography.labelSmall)
                    }
                }
                items(PRESET_STYLES + saved, key = { (if (it in PRESET_STYLES) "p:" else "s:") + it.name }) { s ->
                    val custom = s !in PRESET_STYLES
                    Column(Modifier.width(72.dp).clip(MaterialTheme.shapes.medium).clickable { set { s.style } }, horizontalAlignment = Alignment.CenterHorizontally) {
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

        item(key = "section") {
            SectionCard {
                when (section) {
                    Section.SHAPE -> ShapeControls(current, thumbBase, demoPkg, ::set)
                    Section.BACK -> BackControls(current, thumbBase, demoPkg, ::set)
                    Section.PICTURE -> PictureControls(current, ::set)
                    Section.SYMBOL -> SymbolControls(current, thumbBase, demoPkg, ::set)
                    Section.EFFECTS -> EffectControls(current, thumbBase, demoPkg, ::set)
                    Section.STICKER -> StickerControls(current, thumbBase, demoPkg, ::set)
                    Section.MIX -> MixControls(current, apps, overrides, editing, ::set) { pkg ->
                        overrides.remove(pkg); if (editing == pkg) editing = null
                    }
                }
            }
        }

        item(key = "use") {
            SectionCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { bulk = true }) { Icon(Icons.Rounded.SelectAll, null); Text("  Apply to apps") }
                    OutlinedButton(onClick = { one = true }) { Icon(Icons.AutoMirrored.Rounded.AddToHomeScreen, null); Text("  One app") }
                    OutlinedButton(onClick = { naming = true }) { Icon(Icons.Rounded.Bookmark, null); Text("  Save style") }
                    OutlinedButton(onClick = { attaching = true }) { Icon(Icons.Rounded.Palette, null); Text("  Add to a look") }
                }
            }
        }
    }
}

// --- sections ---

private typealias Setter = ((IconStyle) -> IconStyle) -> Unit

@Composable
private fun Thumb(style: IconStyle, pkg: String) {
    IconBitmap(rememberStyledIcon(style, previewPkg(style, pkg), "Custo", 112), Modifier.size(52.dp))
}

@Composable
private fun ShapeControls(s: IconStyle, base: IconStyle, pkg: String, set: Setter) {
    ThumbPicker(IconShape.entries, s.shape, { it.label }, { v -> set { it.copy(shape = v) } }, thumbWidth = 64, thumbHeight = 64) { v, _ ->
        Thumb(base.copy(shape = v, launcherShape = false, sticker = Sticker.NONE), pkg)
    }
    if (s.shape == IconShape.ROUNDED) LabeledSlider("Corners", s.corner / 0.5f) { v -> set { it.copy(corner = v * 0.5f) } }
    if (s.shape == IconShape.BLOB) TextButton(onClick = { set { it.copy(seed = Random.nextInt()) } }) { Icon(Icons.Rounded.Casino, null); Text("  New pebble") }
    SwitchRow("Launcher shape", null, s.launcherShape) { v -> set { it.copy(launcherShape = v) } }
}

private fun kindOf(s: IconStyle): BgKind = when (s.bgKind) {
    BgKind.AUTO, BgKind.IMAGE -> if (s.bg.size <= 1) BgKind.SOLID else BgKind.LINEAR
    else -> s.bgKind
}

private fun withKind(s: IconStyle, k: BgKind): IconStyle {
    val colors = if (k != BgKind.SOLID && s.bg.size < 2) {
        val first = s.bg.firstOrNull() ?: 0xFF6D4AFF.toInt()
        listOf(first, IconPalette.rotateHue(first, 50f))
    } else s.bg
    // A picture stays on; its colors come back when the picture is removed.
    return s.copy(bg = if (k == BgKind.SOLID) colors.take(1) else colors, bgKind = if (s.bgKind == BgKind.IMAGE) BgKind.IMAGE else k)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackControls(s: IconStyle, base: IconStyle, pkg: String, set: Setter) {
    val kinds = listOf(BgKind.SOLID, BgKind.LINEAR, BgKind.RADIAL, BgKind.SWEEP)
    val kind = kindOf(s)
    ThumbPicker(kinds, kind, { it.label }, { v -> set { withKind(it, v) } }, thumbWidth = 64, thumbHeight = 64) { k, _ ->
        Thumb(withKind(base.copy(bgKind = BgKind.AUTO), k), pkg)
    }
    val slots = s.bg.mapIndexed { i, col ->
        ColorSlot("${i + 1}", col) { v -> set { st -> st.copy(bg = st.bg.toMutableList().also { l -> if (i < l.size) l[i] = v }) } }
    }
    ColorSlots(slots) {
        if (kind != BgKind.SOLID && s.bg.size < 4) IconButton(onClick = { set { it.copy(bg = it.bg + IconPalette.rotateHue(it.bg.last(), 35f)) } }) { Icon(Icons.Rounded.Add, "Add a color") }
        if (kind != BgKind.SOLID && s.bg.size > 2) IconButton(onClick = { set { it.copy(bg = it.bg.dropLast(1)) } }) { Icon(Icons.Rounded.Remove, "Remove a color") }
    }
    if (kind == BgKind.LINEAR || kind == BgKind.SWEEP) LabeledSlider("Angle", s.angle / 360f) { v -> set { it.copy(angle = (v * 360f).roundToInt().toFloat()) } }

    ThumbPicker(IconPattern.entries, s.pattern, { it.label }, { v -> set { it.copy(pattern = v) } }, thumbWidth = 64, thumbHeight = 64) { pt, _ ->
        Thumb(base.copy(pattern = pt, patternOpacity = if (pt == IconPattern.NONE) base.patternOpacity else base.patternOpacity.coerceAtLeast(0.5f)), pkg)
    }
    if (s.pattern != IconPattern.NONE) {
        LabeledSlider("Size", s.patternSize) { v -> set { it.copy(patternSize = v) } }
        LabeledSlider("Strength", s.patternOpacity) { v -> set { it.copy(patternOpacity = v) } }
        ColorSlots(listOf(ColorSlot("Pattern", s.patternColor) { v -> set { it.copy(patternColor = v) } }))
        if (s.pattern == IconPattern.CONFETTI) TextButton(onClick = { set { it.copy(seed = Random.nextInt()) } }) { Text("Scatter again") }
    }

    ThumbPicker(IconTexture.entries, s.texture, { it.label }, { v -> set { it.copy(texture = v) } }, thumbWidth = 64, thumbHeight = 64) { t, _ ->
        Thumb(base.copy(texture = t, textureOpacity = base.textureOpacity.coerceAtLeast(0.6f)), pkg)
    }
    if (s.texture != IconTexture.NONE) LabeledSlider("Texture", s.textureOpacity) { v -> set { it.copy(textureOpacity = v) } }
}

@Composable
private fun PictureControls(s: IconStyle, set: Setter) {
    val ctx = LocalContext.current
    val runner = rememberActionRunner()
    var refresh by remember { mutableIntStateOf(0) }
    val walls by produceState(emptyList<File>(), refresh) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { IconImages.savedWallpapers(ctx) } }
    val photos by produceState(emptyList<File>(), refresh) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { IconImages.photos(ctx) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runner.run {
            val path = IconImages.importPhoto(ctx, uri)
            set { it.copy(image = path, bgKind = BgKind.IMAGE) }
            refresh++
        }
    }
    fun pick(f: File) = set { it.copy(image = f.absolutePath, bgKind = BgKind.IMAGE) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Icon(Icons.Rounded.AddPhotoAlternate, null); Text("  Photo")
        }
        if (s.usesImage) TextButton(onClick = { set { it.copy(bgKind = BgKind.AUTO) } }) { Text("No picture") }
    }
    if (walls.isNotEmpty()) PictureRow(walls, s.image) { pick(it) }
    if (photos.isNotEmpty()) PictureRow(photos, s.image) { pick(it) }
    if (walls.isEmpty() && photos.isEmpty()) Hint("Saved wallpapers show up here too")
    if (s.usesImage) {
        LabeledSlider("Zoom", (s.imageZoom - 1f) / 3f) { v -> set { it.copy(imageZoom = 1f + v * 3f) } }
        LabeledSlider("Left / right", (s.imageX + 1f) / 2f) { v -> set { it.copy(imageX = v * 2f - 1f) } }
        LabeledSlider("Up / down", (s.imageY + 1f) / 2f) { v -> set { it.copy(imageY = v * 2f - 1f) } }
        SwitchRow("Puzzle", "One picture across all the apps", s.puzzle) { v -> set { it.copy(puzzle = v) } }
    }
}

@Composable
private fun PictureRow(files: List<File>, selected: String?, onPick: (File) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(files.take(40), key = { it.absolutePath }) { f ->
            val bmp by produceState<Bitmap?>(null, f.absolutePath) { value = IconImages.loadAsync(f.absolutePath, 240) }
            val on = f.absolutePath == selected
            Box(
                Modifier.size(64.dp, 96.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(if (on) 3.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { onPick(f) },
            ) {
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymbolControls(s: IconStyle, base: IconStyle, pkg: String, set: Setter) {
    val ctx = LocalContext.current
    ThumbPicker(FgMode.entries, s.fg, { fgShort(it) }, { v -> set { it.copy(fg = v) } }, thumbWidth = 64, thumbHeight = 64) { m, _ ->
        Thumb(base.copy(fg = m), pkg)
    }
    when (s.fg) {
        FgMode.GLYPH -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GLYPHS.forEach { (name, v) ->
                val on = s.glyph == name
                Box(
                    Modifier.size(42.dp).clip(MaterialTheme.shapes.small)
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { set { it.copy(glyph = name) } },
                    contentAlignment = Alignment.Center,
                ) { Icon(v, name, Modifier.size(24.dp)) }
            }
        }
        FgMode.TEXT -> OutlinedTextField(s.text, { t -> set { it.copy(text = t.take(2)) } }, label = { Text("1–2 letters") }, singleLine = true)
        FgMode.PACK -> {
            val packs by produceState<List<IconPack>?>(null) { value = IconPacks.installed(ctx) }
            val list = packs
            when {
                list == null -> Unit
                list.isEmpty() -> Hint("No icon packs installed")
                else -> {
                    LaunchedEffect(list) { if (s.pack.isEmpty() || list.none { it.pkg == s.pack }) set { it.copy(pack = list.first().pkg) } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        list.forEach { p -> FilterChip(selected = s.pack == p.pkg, onClick = { set { it.copy(pack = p.pkg) } }, label = { Text(p.label) }) }
                    }
                }
            }
        }
        else -> Unit
    }
    val slots = buildList {
        if (s.fg != FgMode.APP_ORIGINAL && s.fg != FgMode.PACK) add(ColorSlot(if (s.fg == FgMode.DUOTONE) "Dark" else "Symbol", s.fgColor) { v -> set { it.copy(fgColor = v) } })
        if (s.fg == FgMode.DUOTONE) add(ColorSlot("Light", s.fgColor2) { v -> set { it.copy(fgColor2 = v) } })
    }
    if (slots.isNotEmpty()) ColorSlots(slots)
    LabeledSlider("Size", (s.fgScale - 0.3f) / 0.6f) { v -> set { it.copy(fgScale = 0.3f + v * 0.6f) } }
    LabeledSlider("Left / right", s.fgX + 0.5f) { v -> set { it.copy(fgX = snap(v - 0.5f)) } }
    LabeledSlider("Up / down", s.fgY + 0.5f) { v -> set { it.copy(fgY = snap(v - 0.5f)) } }
    LabeledSlider("Turn", (s.fgRotation + 180f) / 360f) { v -> set { it.copy(fgRotation = (v * 360f - 180f).let { r -> if (kotlin.math.abs(r) < 4f) 0f else r.roundToInt().toFloat() }) } }
}

/** Snaps near-center offsets back to exactly 0 so centering is easy with a finger. */
private fun snap(v: Float) = if (kotlin.math.abs(v) < 0.02f) 0f else v

private fun effect(s: IconStyle, e: Effect): IconStyle {
    val flat = s.copy(gloss = 0f, neumorph = 0f, bevel = 0f, emboss = 0f, glow = 0f, border = 0f, innerStroke = 0f)
    return when (e) {
        Effect.NONE -> flat
        Effect.GLASS -> flat.copy(
            bg = s.bg.map { (it and 0xFFFFFF) or (0x66 shl 24) }, gloss = 0.6f, border = 0.1f, borderColor = 0xAAFFFFFF.toInt(),
            fgColor = 0xFFFFFFFF.toInt(), shadow = 0.25f, bgKind = if (s.bgKind == BgKind.IMAGE) BgKind.AUTO else s.bgKind,
        )
        Effect.SOFT -> flat.copy(bg = listOf(0xFFE6E9EF.toInt()), bgKind = BgKind.AUTO, neumorph = 0.8f, shadow = 0f, fgColor = 0xFF8A93A6.toInt())
        Effect.DEPTH -> flat.copy(bevel = 0.6f, emboss = 0.4f, shadow = 0.5f)
        Effect.NEON -> flat.copy(
            bg = listOf(0xFF0B0B1E.toInt(), 0xFF1A1A40.toInt()), bgKind = BgKind.AUTO, glow = 0.7f, glowColor = s.fgColor.takeIf { it != 0xFFFFFFFF.toInt() } ?: 0xFF2DFFB3.toInt(),
            fgColor = s.fgColor.takeIf { it != 0xFFFFFFFF.toInt() } ?: 0xFF2DFFB3.toInt(), border = 0.1f, borderColor = 0x992DFFB3.toInt(), shadow = 0f,
        )
        Effect.EMBOSS -> flat.copy(emboss = 0.7f, shadow = 0f, innerStroke = 0.4f, innerStrokeColor = 0x33000000)
    }
}

@Composable
private fun EffectControls(s: IconStyle, base: IconStyle, pkg: String, set: Setter) {
    // Presets are one-tap starting points, so none stays highlighted.
    ThumbPicker<Effect?>(Effect.entries, null, { it?.label.orEmpty() }, { e -> if (e != null) set { effect(it, e) } }, thumbWidth = 64, thumbHeight = 64) { e, _ ->
        if (e != null) Thumb(effect(base, e), pkg)
    }
    LabeledSlider("Shadow", s.shadow) { v -> set { it.copy(shadow = v) } }
    if (s.shadow > 0f) {
        LabeledSlider("Shadow blur", s.shadowBlur) { v -> set { it.copy(shadowBlur = v) } }
        LabeledSlider("Shadow distance", s.shadowDistance) { v -> set { it.copy(shadowDistance = v) } }
        LabeledSlider("Shadow direction", s.shadowAngle / 360f) { v -> set { it.copy(shadowAngle = (v * 360f).roundToInt().toFloat()) } }
    }
    LabeledSlider("Glow", s.glow) { v -> set { it.copy(glow = v) } }
    LabeledSlider("3-D rim", s.bevel) { v -> set { it.copy(bevel = v) } }
    LabeledSlider("Raised symbol", s.emboss) { v -> set { it.copy(emboss = v) } }
    LabeledSlider("Shine", s.gloss) { v -> set { it.copy(gloss = v) } }
    LabeledSlider("Soft UI", s.neumorph) { v -> set { it.copy(neumorph = v) } }
    LabeledSlider("Border", s.border) { v -> set { it.copy(border = v) } }
    if (s.border > 0f) SwitchRow("Dashed", null, s.borderDashed) { v -> set { it.copy(borderDashed = v) } }
    LabeledSlider("Inner line", s.innerStroke) { v -> set { it.copy(innerStroke = v) } }
    val slots = buildList {
        if (s.shadow > 0f) add(ColorSlot("Shadow", s.shadowColor) { v -> set { it.copy(shadowColor = v) } })
        if (s.glow > 0f) add(ColorSlot("Glow", s.glowColor) { v -> set { it.copy(glowColor = v) } })
        if (s.border > 0f) add(ColorSlot("Border", s.borderColor) { v -> set { it.copy(borderColor = v) } })
        if (s.innerStroke > 0f) add(ColorSlot("Inner", s.innerStrokeColor) { v -> set { it.copy(innerStrokeColor = v) } })
    }
    if (slots.isNotEmpty()) ColorSlots(slots)
}

@Composable
private fun StickerControls(s: IconStyle, base: IconStyle, pkg: String, set: Setter) {
    ThumbPicker(Sticker.entries, s.sticker, { it.label }, { v -> set { it.copy(sticker = v) } }, thumbWidth = 64, thumbHeight = 64) { k, _ ->
        Thumb(base.copy(sticker = k), pkg)
    }
    if (s.sticker != Sticker.NONE) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                StickerCorner.TOP_START to Icons.Rounded.NorthWest, StickerCorner.TOP_END to Icons.Rounded.NorthEast,
                StickerCorner.BOTTOM_START to Icons.Rounded.SouthWest, StickerCorner.BOTTOM_END to Icons.Rounded.SouthEast,
            ).forEach { (corner, icon) ->
                FilterChip(selected = s.stickerCorner == corner, onClick = { set { it.copy(stickerCorner = corner) } }, label = { Icon(icon, corner.name, Modifier.size(18.dp)) })
            }
        }
        ColorSlots(listOf(ColorSlot("Sticker", s.stickerColor) { v -> set { it.copy(stickerColor = v) } }))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MixControls(
    s: IconStyle,
    apps: List<LaunchableApp>,
    overrides: Map<String, IconStyle>,
    editing: String?,
    set: Setter,
    onReset: (String) -> Unit,
) {
    SwitchRow("Rainbow", "Every app its own color", s.rainbow) { v -> set { it.copy(rainbow = v) } }
    if (s.rainbow) TextButton(onClick = { set { it.copy(seed = Random.nextInt()) } }) { Icon(Icons.Rounded.Casino, null); Text("  New colors") }
    FilledTonalButton(onClick = { set { shuffle(it) } }) { Icon(Icons.Rounded.Shuffle, null); Text("  Surprise me") }
    if (overrides.isEmpty()) Hint("Tap an app in the preview to style just that one")
    else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        overrides.keys.forEach { pkg ->
            val label = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
            InputChip(
                selected = pkg == editing, onClick = { onReset(pkg) }, label = { Text(label, maxLines = 1) },
                trailingIcon = { Icon(Icons.Rounded.Close, "Reset", Modifier.size(16.dp)) },
            )
        }
    }
}

// --- colors ---

/** Dots for each color in play, quick swatches for the active one, and the full picker on demand. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSlots(slots: List<ColorSlot>, extra: @Composable () -> Unit = {}) {
    if (slots.isEmpty()) return
    var active by remember { mutableIntStateOf(0) }
    var custom by remember { mutableStateOf(false) }
    val slot = slots[active.coerceIn(0, slots.lastIndex)]
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        if (slots.size > 1 || slot.label.length > 1) slots.forEachIndexed { i, sl -> SlotDot(sl.label, Color(sl.color), sl === slot) { active = i } }
        extra()
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        items(SWATCHES) { col ->
            val on = (col and 0xFFFFFF) == (slot.color and 0xFFFFFF)
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(col))
                    .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { slot.set((col and 0xFFFFFF) or (slot.color and 0xFF000000.toInt())) },
            )
        }
        item {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { custom = !custom },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.ColorLens, "Any color", Modifier.size(18.dp)) }
        }
    }
    if (custom) ColorPicker(Color(slot.color), { slot.set(it.toArgb()) })
}

/** Symbol/letters styles preview on their own; app styles preview on [fallback]'s icon. */
private fun previewPkg(style: IconStyle, fallback: String): String? = when (style.fg) {
    FgMode.GLYPH -> null
    FgMode.TEXT -> null
    else -> fallback
}

private fun shuffle(s: IconStyle): IconStyle {
    val r = Random(System.nanoTime())
    val hue = r.nextFloat() * 360f
    fun hsl(h: Float, sat: Float, l: Float) = IconPalette.fromHsl((h + 360f) % 360f, sat, l)
    val n = 1 + r.nextInt(3)
    val bg = List(n) { i -> hsl(hue + i * (25f + r.nextFloat() * 40f), 0.55f + r.nextFloat() * 0.4f, 0.35f + r.nextFloat() * 0.3f) }
    val light = r.nextFloat() < 0.2f
    return s.copy(
        shape = IconShape.entries.random(r),
        bg = if (light) listOf(hsl(hue, 0.4f, 0.93f)) else bg,
        bgKind = if (s.bgKind == BgKind.IMAGE) BgKind.IMAGE else listOf(BgKind.AUTO, BgKind.AUTO, BgKind.RADIAL, BgKind.SWEEP).random(r),
        angle = (r.nextInt(8) * 45).toFloat(),
        pattern = IconPattern.entries.random(r),
        patternOpacity = 0.12f + r.nextFloat() * 0.25f,
        patternSize = r.nextFloat(),
        texture = if (r.nextFloat() < 0.3f) IconTexture.entries.random(r) else IconTexture.NONE,
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

private fun fgShort(m: FgMode) = when (m) {
    FgMode.APP_TINT -> "Tinted"
    FgMode.APP_ORIGINAL -> "Original"
    FgMode.GLYPH -> "Symbol"
    FgMode.TEXT -> "Letters"
    FgMode.SILHOUETTE -> "Silhouette"
    FgMode.OUTLINE -> "Outline"
    FgMode.DUOTONE -> "Duotone"
    FgMode.PACK -> "Icon pack"
}
