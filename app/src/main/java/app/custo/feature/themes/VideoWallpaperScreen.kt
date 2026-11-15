package app.custo.feature.themes

import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VideoWallpaperScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val runner = rememberActionRunner()
    val p by c.prefs.data.collectAsStateWithLifecycle(emptyPreferences())
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val active = remember(tick) { VideoWallpaperService.isActive(ctx) }
    val uri = p[VideoKeys.uri]
    val crop = p[VideoKeys.crop] != false
    fun <T> set(key: Preferences.Key<T>, v: T) = runner.run { c.prefs.set(key, v) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) runner.run("Video picked") {
            ctx.contentResolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Let go of the old video so Custo doesn't keep access it no longer needs.
            uri?.let { old -> if (old != picked.toString()) runCatching { ctx.contentResolver.releasePersistableUriPermission(old.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            c.prefs.set(VideoKeys.uri, picked.toString())
        }
    }

    val thumb by produceState<Bitmap?>(null, uri) {
        value = uri?.let { u -> withContext(Dispatchers.IO) { frameOf(ctx, u.toUri()) } }
    }

    ScreenScaffold(title = "Video wallpaper", onBack = onBack, actions = {
        app.custo.ui.components.InfoButton(
            "Video wallpaper",
            "Loops one of your videos, quietly unless you turn on sound. It plays only while you can see it and pauses in battery saver. Short clips use the least battery.",
        )
    }) {
        item {
            SectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.width(132.dp).aspectRatio(9f / 19.5f).clip(MaterialTheme.shapes.medium).background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        val b = thumb
                        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
                        else Icon(Icons.Rounded.Movie, null, tint = Color.White.copy(alpha = 0.6f))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SupportBadge(if (active) "In use" else "Not in use", active)
                        Text(if (uri == null) "No video yet" else "Your video", style = MaterialTheme.typography.titleMedium)
                        FilledTonalButton(onClick = { picker.launch(arrayOf("video/*")) }) {
                            Icon(Icons.Rounded.Movie, null); Text(if (uri == null) "  Pick a video" else "  Change video")
                        }
                    }
                }
                Button(onClick = {
                    if (uri == null) { runner.message("Pick a video first"); return@Button }
                    try { ctx.startActivity(VideoWallpaperService.pickerIntent(ctx)) }
                    catch (_: ActivityNotFoundException) { runner.message("Live wallpapers aren't supported here") }
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Wallpaper, null); Text("  Set video wallpaper") }
            }
        }
        item {
            SectionCard("Playback") {
                SwitchRow("Sound", null, p[VideoKeys.sound] == true) { set(VideoKeys.sound, it) }
                Text("Scale", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(crop, { set(VideoKeys.crop, true) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Fill (crop)") }
                    SegmentedButton(!crop, { set(VideoKeys.crop, false) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Fit") }
                }
            }
        }
    }
}

private fun frameOf(ctx: android.content.Context, uri: Uri): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(ctx, uri)
        r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, 640)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { r.release() }
    }
}
