package app.custo.feature.themes

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.feature.systemui.MonetStyle
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemesScreen(nav: Navigator) {
    val c = LocalContext.current.container
    val runner = rememberActionRunner()
    val shizuku by c.shizuku.state.collectAsStateWithLifecycle()
    val saved by c.looks.saved.collectAsStateWithLifecycle(emptyList())

    var spec by remember { mutableStateOf(WallSpec()) }
    var slot by remember { mutableIntStateOf(1) }
    var target by remember { mutableStateOf(WallTarget.BOTH) }
    var matchSystem by remember { mutableStateOf(true) }
    var iconLook by remember { mutableStateOf<Look?>(null) }

    fun applyLook(look: Look) = runner.run("“${look.name}” applied") {
        c.wallpapers.apply(look.wall, WallTarget.BOTH)
        if (shizuku == ShizukuState.READY) {
            c.tweaks.applyMonet(look.seed, MonetStyle.of(look.style))
            look.hiddenIcons?.let { c.tweaks.setHiddenIcons(it.toSet()) }
        }
        if (look.icons != null) iconLook = look
    }

    iconLook?.let { look ->
        AlertDialog(
            onDismissRequest = { iconLook = null },
            title = { Text("Icons too?") },
            text = { Text("“${look.name}” has its own icon style. Pick apps to give it?") },
            confirmButton = {
                TextButton(onClick = {
                    iconLook = null
                    look.icons?.let { nav.push(Route.IconMaker(it.encode(), pickApps = true)) }
                }) { Text("Pick apps") }
            },
            dismissButton = { TextButton(onClick = { iconLook = null }) { Text("Not now") } },
        )
    }

    ScreenScaffold(title = "Themes") {
        item { app.custo.feature.widgetsets.WidgetSetsEntryCard { nav.push(Route.WidgetSets) } }
        item {
            SectionCard("Icons", Icons.Rounded.Apps, "Change app icons, make your own icon style, use icon packs.", onClick = { nav.push(Route.IconsHub) })
        }
        item { SectionLabel("Looks") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(BUILT_IN_LOOKS + saved, key = { it.name + it.builtIn }) { look ->
                    LookCard(look, onClick = { spec = look.wall; applyLook(look) }, onDelete = if (look.builtIn) null else ({ runner.run { c.looks.delete(look.name) } }))
                }
            }
        }
        item { Hint("One tap: wallpaper + matching system colors.") }

        item { SectionLabel("Make a wallpaper") }
        item {
            SectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    WallPreview(spec, Modifier.width(132.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SmallSlider("Grain", spec.grain) { spec = spec.copy(grain = it) }
                        SmallSlider("Softness", spec.blur) { spec = spec.copy(blur = it) }
                        SmallSlider("Detail", spec.complexity) { spec = spec.copy(complexity = it) }
                        SmallSlider("Angle", spec.angle / 360f) { spec = spec.copy(angle = (it * 360f).roundToInt().toFloat()) }
                        FilledTonalButton(onClick = {
                            val r = Random(System.nanoTime())
                            spec = spec.copy(colors = WallpaperStudio.randomPalette(r), seed = r.nextInt(), angle = (r.nextFloat() * 360f).roundToInt().toFloat())
                        }) { Icon(Icons.Rounded.Casino, null); Text("  Shuffle") }
                    }
                }
                Text("Style", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WallStyle.entries.forEach { s -> FilterChip(selected = spec.style == s, onClick = { spec = spec.copy(style = s) }, label = { Text(s.label) }) }
                }
                Text("Colors", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    spec.colors.forEachIndexed { i, col ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color(col))
                                .border(if (i == slot) 3.dp else 1.dp, if (i == slot) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { slot = i },
                        )
                    }
                    if (spec.colors.size < 5) IconButton(onClick = { spec = spec.copy(colors = spec.colors + spec.colors.last()); slot = spec.colors.lastIndex }) { Icon(Icons.Rounded.Add, "Add color") }
                    if (spec.colors.size > 1) IconButton(onClick = { spec = spec.copy(colors = spec.colors.dropLast(1)); slot = slot.coerceAtMost(spec.colors.lastIndex) }) { Icon(Icons.Rounded.Remove, "Remove color") }
                }
                val safeSlot = slot.coerceIn(0, spec.colors.lastIndex)
                ColorPicker(Color(spec.colors[safeSlot]), { nc -> spec = spec.copy(colors = spec.colors.toMutableList().also { it[safeSlot] = nc.toArgb() }) })

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    WallTarget.entries.forEachIndexed { i, t ->
                        SegmentedButton(selected = target == t, onClick = { target = t }, shape = SegmentedButtonDefaults.itemShape(i, WallTarget.entries.size)) { Text(t.label) }
                    }
                }
                SwitchRow(
                    "Match system colors",
                    if (shizuku == ShizukuState.READY) "Recolor the system to fit" else "Needs Shizuku",
                    matchSystem && shizuku == ShizukuState.READY,
                    enabled = shizuku == ShizukuState.READY,
                ) { matchSystem = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        runner.run("Wallpaper set") {
                            c.wallpapers.apply(spec, target)
                            if (matchSystem && shizuku == ShizukuState.READY) {
                                c.tweaks.applyMonet(spec.colors.getOrElse(1) { spec.colors[0] }, MonetStyle.TONAL_SPOT)
                            }
                        }
                    }) { Icon(Icons.Rounded.AutoAwesome, null); Text("  Apply") }
                    OutlinedButton(onClick = {
                        runner.run("Saved to Looks") {
                            val n = saved.size + 1
                            c.looks.save(Look("My look $n", spec, spec.colors.getOrElse(1) { spec.colors[0] }))
                        }
                    }) { Icon(Icons.Rounded.Bookmark, null); Text("  Save look") }
                    OutlinedButton(onClick = {
                        runner.run {
                            c.prefs.set(ThemeKeys.liveSpec, spec.encode())
                            nav.push(Route.LiveWallpaper)
                        }
                    }) { Icon(Icons.Rounded.Animation, null); Text("  Make it live") }
                }
            }
        }

        item { SectionLabel("More") }
        item {
            SectionCard("Live wallpaper", Icons.Rounded.Animation, "Your wallpaper, slowly moving. Easy on battery.", onClick = { nav.push(Route.LiveWallpaper) })
        }
        item {
            SectionCard("Day & night", Icons.Rounded.Schedule, "Switch looks at set times.", onClick = { nav.push(Route.WallSchedule) })
        }
        item {
            SectionCard("Video wallpaper", Icons.Rounded.Movie, "Loop one of your videos. Silent unless you want sound.", onClick = { nav.push(Route.VideoWallpaper) })
        }
        item {
            SectionCard("Collage", Icons.Rounded.Dashboard, "Put 2 to 6 photos together as a wallpaper.", onClick = { nav.push(Route.Collage) })
        }
    }
}

@Composable
private fun SmallSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Slider(value, onChange, Modifier.padding(bottom = 0.dp))
}

@Composable
fun WallPreview(spec: WallSpec, modifier: Modifier = Modifier) {
    val bmp by produceState<Bitmap?>(null, spec) {
        delay(40) // let slider drags settle before rendering
        value = withContext(Dispatchers.Default) { WallpaperStudio.render(spec, 270, 585) }
    }
    Box(modifier.aspectRatio(9f / 19.5f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Crossfade(bmp, label = "wall") { b ->
            if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        // Fake clock so the preview reads as a phone.
        Text(
            "12:30", Modifier.align(Alignment.TopCenter).padding(top = 22.dp),
            style = MaterialTheme.typography.headlineSmall, color = Color.White.copy(alpha = 0.92f),
        )
    }
}

@Composable
internal fun LookCard(look: Look, onClick: () -> Unit, onDelete: (() -> Unit)? = null, selected: Boolean = false) {
    Column(Modifier.width(104.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            WallPreview(
                look.wall,
                Modifier.fillMaxWidth().then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier,
                ),
            )
            if (onDelete != null) IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                Icon(Icons.Rounded.Close, "Delete", tint = Color.White)
            }
        }
        Text(look.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    }
}
