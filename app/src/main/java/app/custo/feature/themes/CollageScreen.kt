package app.custo.feature.themes

import androidx.compose.runtime.mutableIntStateOf
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.custo.container
import app.custo.ui.components.ColorDot
import app.custo.ui.components.ColorPicker
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val TEXT_COLORS = listOf(0xFFFFFFFF, 0xFF1C1B1F, 0xFFFFD166, 0xFFFF8FAB, 0xFF90E0EF).map { it.toInt() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val runner = rememberActionRunner()
    var uris by rememberSaveable { mutableStateOf(listOf<String>()) }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    var spacing by rememberSaveable { mutableFloatStateOf(0.02f) }
    var margin by rememberSaveable { mutableFloatStateOf(0.04f) }
    var radius by rememberSaveable { mutableFloatStateOf(0.03f) }
    var bg by rememberSaveable { mutableStateOf(CollageBg.BLUR) }
    var bgColor by rememberSaveable { mutableIntStateOf(0xFF1C1B1F.toInt()) }
    var text by rememberSaveable { mutableStateOf("") }
    var textSize by rememberSaveable { mutableFloatStateOf(0.07f) }
    var textColor by rememberSaveable { mutableIntStateOf(TEXT_COLORS[0]) }
    var target by rememberSaveable { mutableStateOf(WallTarget.BOTH) }

    val count = uris.size.coerceIn(CollageLogic.MIN_PHOTOS, CollageLogic.MAX_PHOTOS)
    val choices = CollageLogic.forCount(count)
    val template = CollageLogic.byId(templateId)?.takeIf { it.count == count } ?: choices.first()
    val spec = CollageSpec(template, spacing, margin, radius, bg, bgColor, text.trim(), textSize, textColor)
    val (sw, sh) = remember { c.wallpapers.screenSize() }
    val photoUris = uris.map(Uri::parse)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(CollageLogic.MAX_PHOTOS)) { picked ->
        if (picked.isEmpty()) return@rememberLauncherForActivityResult
        if (picked.size < CollageLogic.MIN_PHOTOS) runner.message("Pick at least ${CollageLogic.MIN_PHOTOS} photos")
        else uris = picked.take(CollageLogic.MAX_PHOTOS).map { it.toString() }
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // Small photos for the live preview, loaded once per pick.
    val previewW = 360
    val previewH = (previewW * sh / sw.toFloat()).roundToInt()
    val previewPhotos by produceState<List<Bitmap?>>(emptyList(), uris) {
        value = withContext(Dispatchers.IO) { photoUris.map { CollageRenderer.load(ctx, it, 300, 300) } }
    }
    val face = remember { CollageRenderer.typeface(ctx) }
    val preview by produceState<Bitmap?>(null, spec, previewPhotos) {
        delay(30)
        value = withContext(Dispatchers.Default) { CollageRenderer.render(spec, previewPhotos, previewW, previewH, face) }
    }

    suspend fun renderFull(): Bitmap {
        val photos = CollageRenderer.loadFor(ctx, spec, photoUris, sw, sh)
        return withContext(Dispatchers.Default) { CollageRenderer.render(spec, photos, sw, sh, face) }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { out ->
        if (out != null) runner.run("Saved. Pick it in the Photo widget.") {
            val bmp = renderFull()
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(out)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) } ?: error("Couldn't save there")
            }
            bmp.recycle()
        }
    }

    ScreenScaffold(title = "Collage", onBack = onBack, actions = {
        app.custo.ui.components.InfoButton(
            "Collage",
            "Puts ${CollageLogic.MIN_PHOTOS} to ${CollageLogic.MAX_PHOTOS} photos together as one wallpaper. Only the photos you pick are shared with Custo. Saved images also work in the Photo widget.",
        )
    }) {
        item {
            SectionCard {
                if (uris.isEmpty()) {
                    Button(onClick = ::pick) { Icon(Icons.Rounded.AddPhotoAlternate, null); Text("  Pick photos") }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(200.dp).aspectRatio(sw / sh.toFloat()).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            preview?.let { Image(it.asImageBitmap(), "Collage preview", Modifier.fillMaxSize()) }
                        }
                    }
                    OutlinedButton(onClick = ::pick) { Icon(Icons.Rounded.AddPhotoAlternate, null); Text("  Change photos (${uris.size})") }
                }
            }
        }
        if (uris.isNotEmpty()) {
            item {
                SectionCard("Layout") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        choices.forEach { t -> TemplateThumb(t, t == template) { templateId = t.id } }
                    }
                    LabeledSlider("Spacing", spacing, 0f..0.06f) { spacing = it }
                    LabeledSlider("Outer margin", margin, 0f..0.12f) { margin = it }
                    LabeledSlider("Corner radius", radius, 0f..0.08f) { radius = it }
                }
            }
            item {
                SectionCard("Background") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        CollageBg.entries.forEachIndexed { i, b ->
                            SegmentedButton(bg == b, { bg = b }, SegmentedButtonDefaults.itemShape(i, CollageBg.entries.size)) { Text(b.label) }
                        }
                    }
                    if (bg == CollageBg.COLOR) ColorPicker(Color(bgColor), { bgColor = it.toArgb() })
                }
            }
            item {
                SectionCard("Text") {
                    OutlinedTextField(text, { text = it.take(40) }, placeholder = { Text("Say something") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (text.isNotBlank()) {
                        LabeledSlider("Size", textSize, 0.04f..0.12f) { textSize = it }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TEXT_COLORS.forEach { col -> ColorDot(Color(col), col == textColor, 36) { textColor = col } }
                        }
                    }
                }
            }
            item {
                SectionCard("Use it") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        WallTarget.entries.forEachIndexed { i, t ->
                            SegmentedButton(target == t, { target = t }, SegmentedButtonDefaults.itemShape(i, WallTarget.entries.size)) { Text(t.label) }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            runner.run("Wallpaper set") {
                                val bmp = renderFull()
                                withContext(Dispatchers.IO) { WallpaperManager.getInstance(ctx).setBitmap(bmp, null, true, target.flags) }
                                bmp.recycle()
                            }
                        }) { Icon(Icons.Rounded.Wallpaper, null); Text("  Set wallpaper") }
                        FilledTonalButton(onClick = { saver.launch("Custo collage.jpg") }) { Icon(Icons.Rounded.Save, null); Text("  Save image") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(value, onChange, valueRange = range)
    }
}

/** A tiny drawing of the template's cells. */
@Composable
private fun TemplateThumb(t: CollageTemplate, selected: Boolean, onClick: () -> Unit) {
    val cell = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(width = 48.dp, height = 72.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(if (selected) 2.dp else 0.dp, if (selected) cell else Color.Transparent, shape)
            .clickable(onClick = onClick).padding(5.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            CollageLogic.cells(t, size.width, size.height, 3.dp.toPx(), 0f).forEach { b ->
                drawRoundRect(cell.copy(alpha = if (selected) 0.9f else 0.45f), Offset(b.l, b.t), Size(b.w, b.h), CornerRadius(3.dp.toPx()))
            }
        }
    }
}
