package app.custo.feature.themes

import android.content.ActivityNotFoundException
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SupportBadge
import app.custo.ui.components.SwitchRow
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.flow.map

@Composable
fun LiveWallpaperScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val runner = rememberActionRunner()
    val saved by c.looks.saved.collectAsStateWithLifecycle(emptyList())
    val prefs by remember { c.prefs.data.map<Preferences, Preferences?> { it } }.collectAsStateWithLifecycle(null)
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val active = remember(tick) { LiveWallpaperService.isActive(ctx) }

    val p = prefs ?: return
    val spec = WallSpec.decode(p[ThemeKeys.liveSpec]) ?: WallSpec(WallStyle.AURORA, BUILT_IN_LOOKS.first().wall.colors)
    val opts = LiveOptions.from(p)
    fun <T> set(key: Preferences.Key<T>, v: T) = runner.run { c.prefs.set(key, v) }

    ScreenScaffold(title = "Live wallpaper", onBack = onBack, actions = {
        app.custo.ui.components.InfoButton(
            "Live wallpaper",
            "Your wallpaper, slowly moving. It stops when hidden and pauses in battery saver, so it's easy on battery. Pick a look below, or use Make it live in Wallpapers → Make your own.\n\n" +
                "Only when unlocked: still on the lock screen. Parallax: shifts as you swipe home pages. Time of day: warmer at sunrise and sunset, darker at night. Battery: colors fade as the battery drains.",
        )
    }) {
        item {
            SectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LivePreview(spec, opts, Modifier.width(150.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SupportBadge(if (active) "In use" else "Not in use", active)
                        Text(spec.style.label, style = MaterialTheme.typography.titleMedium)
                        if (!spec.style.animated) Hint("Still style")
                        Button(onClick = {
                            try { ctx.startActivity(LiveWallpaperService.pickerIntent(ctx)) }
                            catch (_: ActivityNotFoundException) { runner.message("Live wallpapers aren't supported here") }
                        }) { Icon(Icons.Rounded.Wallpaper, null); Text("  Apply") }
                    }
                }
            }
        }
        item {
            SectionCard("Wallpaper") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(BUILT_IN_LOOKS + saved, key = { it.name + it.builtIn }) { look ->
                        LookCard(look, onClick = { set(ThemeKeys.liveSpec, look.wall.encode()) }, selected = look.wall == spec)
                    }
                }
            }
        }
        item {
            SectionCard("Motion") {
                SwitchRow("Animate", null, opts.animate) { set(ThemeKeys.liveAnimate, it) }
                SwitchRow("Only when unlocked", null, opts.unlockedOnly, enabled = opts.animate) { set(ThemeKeys.liveUnlockedOnly, it) }
                SwitchRow("Parallax", null, opts.parallax) { set(ThemeKeys.liveParallax, it) }
                Text("Speed · ${"%.2f".format(opts.speed)}×", style = MaterialTheme.typography.labelMedium)
                var speed by remember(opts.speed) { mutableFloatStateOf(opts.speed) }
                Slider(speed, { speed = it }, valueRange = 0.25f..3f, onValueChangeFinished = { set(ThemeKeys.liveSpeed, speed) })
                Text("Frame rate", style = MaterialTheme.typography.labelMedium)
                val rates = listOf(10, 15, 20)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    rates.forEachIndexed { i, r ->
                        SegmentedButton(opts.fps == r, { set(ThemeKeys.liveFps, r) }, SegmentedButtonDefaults.itemShape(i, rates.size)) {
                            Text(when (r) { 10 -> "Saver"; 15 -> "Normal"; else -> "Smooth" })
                        }
                    }
                }
            }
        }
        item {
            SectionCard("Reactions") {
                SwitchRow("Time of day", null, opts.timeOfDay) { set(ThemeKeys.liveTimeOfDay, it) }
                SwitchRow("Battery", null, opts.battery) { set(ThemeKeys.liveBattery, it) }
            }
        }
    }
}

/** In-app animated preview, drawn with the same code as the live wallpaper. */
@Composable
private fun LivePreview(spec: WallSpec, opts: LiveOptions, modifier: Modifier = Modifier) {
    var t by remember(spec) { mutableFloatStateOf(0f) }
    LaunchedEffect(spec, opts.animate, opts.speed, opts.fps) {
        if (!opts.animate || !spec.style.animated) return@LaunchedEffect
        var last = 0L
        var acc = 0L
        val step = 1_000_000_000L / opts.fps
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) acc += now - last
                last = now
                if (acc >= step) { t += acc / 1e9f * opts.speed; acc = 0 }
            }
        }
    }
    Box(modifier.aspectRatio(9f / 19.5f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Canvas(Modifier.fillMaxSize().then(if (spec.blur > 0.01f) Modifier.blur((spec.blur * 8).dp) else Modifier)) {
            val frameT = t
            drawIntoCanvas { WallpaperStudio.draw(it.nativeCanvas, spec, size.width, size.height, frameT) }
        }
    }
}
