package app.custo.feature.overlays

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.custo.container
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val PREVIEW_PX = 256

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickersScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val ui = rememberOverlayUi()
    val p = ui.p
    val scope = rememberCoroutineScope()
    val saved = p[OverlayKeys.stickerList]
    // Local copy for smooth dragging; follows prefs until the user starts editing.
    var list by remember { mutableStateOf<List<Sticker>?>(null) }
    var dirty by remember { mutableIntStateOf(0) }
    LaunchedEffect(saved) { if (dirty == 0) list = StickerMath.decode(saved) }
    val stickers = list ?: StickerMath.decode(saved)
    var selected by remember { mutableIntStateOf(-1) }

    fun edit(new: List<Sticker>) { list = new; dirty++ }
    LaunchedEffect(dirty) {
        if (dirty == 0) return@LaunchedEffect
        delay(400)
        val l = list ?: return@LaunchedEffect
        c.prefs.set(OverlayKeys.stickerList, StickerMath.encode(l))
        withContext(Dispatchers.IO) { StickerStore.prune(ctx, l) }
    }
    fun addSticker(s: Sticker) {
        if (stickers.size >= StickerMath.MAX_STICKERS) { ui.runner.message("That's the limit: ${StickerMath.MAX_STICKERS} stickers"); return }
        edit(stickers + s); selected = stickers.size
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val s = StickerStore.import(ctx, uri)
            if (s == null) ui.runner.message("Couldn't open that image") else addSticker(s.copy(size = 0.35f))
        }
    }
    val screen = remember { c.wallpapers.screenSize() }

    ScreenScaffold("Stickers", onBack) {
        item {
            SectionCard(subtitle = "Stickers float on your home screen only. Taps go right through to your apps.") {
                SwitchRow("Show on home screen", null, p[OverlayKeys.stickers] == true) { ui.set(OverlayKeys.stickers, it) }
            }
        }
        item {
            SectionCard(subtitle = "Drag to move. Pinch to resize. Twist with two fingers to rotate.") {
                Box(Modifier.fillMaxWidth(0.72f).align(androidx.compose.ui.Alignment.CenterHorizontally)) {
                    StickerCanvas(stickers, selected, screen.first / screen.second.toFloat(), onSelect = { selected = it }) { i, s ->
                        edit(stickers.toMutableList().also { it[i] = s })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selected in stickers.indices) {
                        OutlinedButton(onClick = { edit(StickerMath.bringToFront(stickers, selected)); selected = stickers.lastIndex }) {
                            Icon(Icons.Rounded.FlipToFront, null); Text("  Front")
                        }
                        OutlinedButton(onClick = { edit(stickers.filterIndexed { i, _ -> i != selected }); selected = -1 }) {
                            Icon(Icons.Rounded.Delete, null); Text("  Remove")
                        }
                    }
                    FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Rounded.Image, null); Text("  Your image")
                    }
                }
            }
        }
        item {
            SectionCard("Add a sticker") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StickerArt.names) { n ->
                        val bmp = rememberSticker("b:$n", 144)
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { addSticker(StickerStore.newBundled(n)) }
                                .padding(6.dp),
                        ) { bmp?.let { Image(it, n, Modifier.fillMaxWidth()) } }
                    }
                }
            }
        }
        item {
            SectionCard("Place them for real", subtitle = "Opens your home screen so you can drag stickers into place. Tap Done when finished.") {
                Button(onClick = {
                    if (!ui.running) { ui.runner.message("Turn on the overlay service first"); return@Button }
                    if (stickers.isEmpty()) { ui.runner.message("Add a sticker first"); return@Button }
                    ui.runner.run {
                        c.prefs.edit {
                            it[OverlayKeys.stickerList] = StickerMath.encode(stickers)
                            it[OverlayKeys.stickers] = true
                            it[OverlayKeys.stickerArrange] = true
                        }
                        dirty = 0
                        ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Icon(Icons.Rounded.Home, null); Text("  Arrange on home screen") }
                Hint("Needs the overlay service. Nothing runs while you're in other apps.")
            }
        }
    }
}

@Composable
private fun rememberSticker(src: String, px: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, src, px) {
        value = withContext(Dispatchers.Default) { StickerStore.bitmap(ctx, src, px)?.asImageBitmap() }
    }
    return bmp
}

/** Home-screen-like canvas: a soft wallpaper, a faint app grid, and the stickers on top. */
@Composable
private fun StickerCanvas(
    stickers: List<Sticker>,
    selected: Int,
    aspect: Float,
    onSelect: (Int) -> Unit,
    onChange: (Int, Sticker) -> Unit,
) {
    val ctx = LocalContext.current
    val images = remember { HashMap<String, Bitmap>() }
    val current by rememberUpdatedState(stickers)
    val sel by rememberUpdatedState(selected)
    val outline = MaterialTheme.colorScheme.primary
    val bg = Brush.verticalGradient(listOf(Color(0xFF3A4A8C), Color(0xFF8E6BBF), Color(0xFFF2A2A8)))
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(aspect.coerceIn(0.3f, 1f)).clip(RoundedCornerShape(24.dp)).background(bg)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val hit = StickerMath.topmostAt(current, down.position.x, down.position.y, w, h)
                    onSelect(hit)
                    if (hit < 0) return@awaitEachGesture
                    do {
                        val e = awaitPointerEvent()
                        val pan = e.calculatePan(); val zoom = e.calculateZoom(); val rot = e.calculateRotation()
                        val s = current.getOrNull(sel) ?: break
                        if (pan != Offset.Zero || zoom != 1f || rot != 0f) {
                            onChange(sel, StickerMath.transform(s, pan.x, pan.y, zoom, rot, w, h))
                            e.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (e.changes.any { it.pressed })
                }
            },
    ) {
        val w = size.width; val h = size.height
        // Faint app icons so it reads as a home screen.
        val cell = w / 4
        for (row in 0 until 2) for (col in 0 until 4) {
            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(col * cell + cell * 0.22f, h - (row + 1) * cell * 1.05f - cell * 0.2f),
                androidx.compose.ui.geometry.Size(cell * 0.56f, cell * 0.56f), androidx.compose.ui.geometry.CornerRadius(cell * 0.16f))
        }
        stickers.forEachIndexed { i, s ->
            val px = (s.size * w).roundToInt().coerceAtLeast(16)
            val key = s.src
            val bmp = images[key] ?: StickerStore.bitmap(ctx, s.src, PREVIEW_PX)?.also { images[key] = it }
            val hw = StickerMath.halfW(s, w); val hh = StickerMath.halfH(s, w)
            val cx = s.x * w; val cy = s.y * h
            rotate(s.rot, Offset(cx, cy)) {
                if (bmp != null) drawImage(
                    bmp.asImageBitmap(), srcOffset = IntOffset.Zero, srcSize = IntSize(bmp.width, bmp.height),
                    dstOffset = IntOffset((cx - hw).roundToInt(), (cy - hh).roundToInt()), dstSize = IntSize(px, (px * s.aspect).roundToInt()),
                )
                if (i == selected) drawRect(outline, Offset(cx - hw, cy - hh), androidx.compose.ui.geometry.Size(hw * 2, hh * 2), style = Stroke(2.dp.toPx()))
            }
        }
    }
}
