package app.custo.feature.widgets

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.TextAlign
import app.custo.R
import app.custo.feature.widgetsets.HudMode
import app.custo.feature.widgetsets.WidgetThemes
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.coroutines.resume

// ---------- Media ----------

class MediaWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val np = MediaRepo.nowPlaying(context)
        val open = np?.pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
        val fg = fgProvider(style)
        Frame(style, open?.let { tapStart(it) } ?: tapStart(editIntent(context, id))) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (np?.art != null) {
                    Image(ImageProvider(np.art), "Album art", GlanceModifier.size(56.dp).cornerRadius(14.dp))
                } else {
                    Image(ImageProvider(R.drawable.ic_music), null, GlanceModifier.size(40.dp), colorFilter = ColorFilter.tint(fg))
                }
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    WText(np?.title?.ifEmpty { null } ?: "Nothing playing", maxLines = 1, style = style.text(16f, bold = true).copy(textAlign = TextAlign.Start))
                    WText(
                        if (np == null) "Allow notification access in Custo → Widgets" else np.artist,
                        maxLines = 1, style = style.text(13f).copy(textAlign = TextAlign.Start),
                    )
                }
                if (np != null) {
                    MediaButton(R.drawable.ic_prev, "prev", style)
                    MediaButton(if (np.playing) R.drawable.ic_pause else R.drawable.ic_play, "toggle", style)
                    MediaButton(R.drawable.ic_next, "next", style)
                }
            }
        }
    }

    @Composable
    private fun MediaButton(icon: Int, cmd: String, style: WidgetStyle) {
        val label = when (cmd) { "prev" -> "Previous"; "next" -> "Next"; else -> "Play or pause" }
        val pop = TapFrames.pop(tapFrame("media:$cmd"))
        PressButton(style, tapRun<MediaAction>("cmd" to cmd), GlanceModifier.size(40.dp), ButtonLook(icon = icon, iconDp = 24f * pop, tint = fgArgb(style), desc = label))
    }
}

class MediaAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val cmd = parameters[CMD] ?: return
        MediaRepo.command(context, cmd)
        // The button pops while the player catches up, then the widget re-reads what's playing.
        TapFx.play(context, glanceId, MediaWidget(), "media:$cmd")
        kotlinx.coroutines.delay((350 - TapFrames.TOTAL_MS).coerceAtLeast(0))
        Widgets.bump(context, MediaWidget(), glanceId)
    }

    companion object { val CMD = ActionParameters.Key<String>("cmd") }
}

class MediaReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = MediaWidget() }

// ---------- Note ----------

class NoteWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        Frame(style, tapStart(editIntent(context, id))) {
            WText(style.note, style = style.text(17f), modifier = GlanceModifier.fillMaxWidth())
        }
    }
}

class NoteReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = NoteWidget() }

// ---------- Photo ----------

class PhotoWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val bmp = remember(style.photoUri) { WidgetArt.photo(context, style.photoUri) }
        Frame(style, tapStart(editIntent(context, id)), padded = bmp == null) {
            if (bmp != null) {
                val inset = style.borderWidth.dp
                Image(
                    ImageProvider(bmp), "Photo",
                    GlanceModifier.fillMaxSize().padding(inset).cornerRadius((style.corner - style.borderWidth).coerceAtLeast(0).dp),
                    contentScale = ContentScale.Crop,
                )
            } else if (style.themeId != null) {
                // The set's art fills the frame; just a small hint at the bottom.
                Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    WText("Tap to add a photo", style = style.text(11f, alpha = 0.8f).copy(textAlign = TextAlign.Center), maxLines = 1)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(ImageProvider(R.drawable.ic_photo), null, GlanceModifier.size(36.dp), colorFilter = ColorFilter.tint(fgProvider(style)))
                    Spacer(GlanceModifier.height(6.dp))
                    WText("Tap to pick a photo", style = style.text(13f).copy(textAlign = TextAlign.Center))
                }
            }
        }
    }
}

class PhotoReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = PhotoWidget() }

// ---------- App shortcuts ----------

class AppsWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val apps = style.apps.take(6)
        val icons = remember(apps) { apps.map { it to WidgetArt.appIcon(context, it) } }
        val labels = remember(apps) { apps.map { WidgetArt.appLabel(context, it) } }
        val size = LocalSize.current
        val iconDp = ((size.width.value - style.padding * 2) / apps.size.coerceAtLeast(1) - 12f).coerceIn(28f, 56f) * style.textScale.coerceAtMost(1.3f)
        val theme = WidgetThemes.byId(style.themeId)
        val slotted = theme?.hudMode(WidgetKind.APPS) == HudMode.SLOTS
        Frame(style, if (apps.isEmpty()) tapStart(editIntent(context, id)) else null) {
            if (slotted && theme != null) {
                // The set draws a row of slots; each app sits centered in its slot.
                val f = theme.frame
                val w = size.width.value - 2 * (f.outerSide + f.innerSide)
                val h = size.height.value - (f.outerTop + f.innerTop + f.outerBottom + f.innerBottom)
                val n = theme.hud!!.appSlots(apps.size, w, h)
                val slotDp = minOf(w / n, h) * 0.62f
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until n) {
                        val pair = icons.getOrNull(i)
                        val launch = pair?.let { context.packageManager.getLaunchIntentForPackage(it.first) }
                        val icon = pair?.second
                        if (launch != null && icon != null) {
                            PressButton(style, tapStart(launch), GlanceModifier.defaultWeight(), ButtonLook(bitmap = icon, iconDp = slotDp, desc = labels[i]))
                        } else {
                            Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                                if (icon != null) Image(ImageProvider(icon), labels[i], GlanceModifier.size(slotDp.dp))
                            }
                        }
                    }
                }
            } else if (apps.isEmpty()) {
                WText("Tap to pick apps", style = style.text(14f))
            } else {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    icons.forEachIndexed { i, (pkg, icon) ->
                        val launch = context.packageManager.getLaunchIntentForPackage(pkg)?.let { tapStart(it) }
                        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            // The icon squishes when tapped; the label (in the widget's font) opens the app too.
                            if (icon != null && launch != null) {
                                PressButton(style, launch, GlanceModifier.size(iconDp.dp), ButtonLook(bitmap = icon, iconDp = iconDp, desc = labels[i]))
                            } else if (icon != null) Image(ImageProvider(icon), labels[i], GlanceModifier.size(iconDp.dp))
                            if (style.showLabels) {
                                Spacer(GlanceModifier.height(4.dp))
                                WText(
                                    labels[i], maxLines = 1, style = style.text(11f).copy(textAlign = TextAlign.Center),
                                    modifier = if (launch != null) GlanceModifier.clickable(launch.glance()) else GlanceModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class AppsReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = AppsWidget() }

// ---------- Quick toggles ----------

object Torch {
    val ON = booleanPreferencesKey("torch_on")

    fun cameraId(cm: CameraManager): String? = runCatching {
        cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    /** Current torch state: a TorchCallback reports it right after registering. */
    suspend fun isOn(cm: CameraManager, id: String): Boolean? = withTimeoutOrNull(600) {
        suspendCancellableCoroutine { cont ->
            val cb = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == id && cont.isActive) { cm.unregisterTorchCallback(this); cont.resume(enabled) }
                }
            }
            cm.registerTorchCallback(cb, Handler(Looper.getMainLooper()))
            cont.invokeOnCancellation { cm.unregisterTorchCallback(cb) }
        }
    }
}

class TorchAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val cm = context.getSystemService(CameraManager::class.java)
        val id = Torch.cameraId(cm) ?: return
        val target = !(Torch.isOn(cm, id) ?: false)
        runCatching { cm.setTorchMode(id, target) }
        TapFx.play(context, glanceId, TogglesWidget(), "torch") { it[Torch.ON] = target }
    }
}

class TogglesWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val torchOn = currentState(Torch.ON) == true
        Frame(style, null) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Toggle(style, R.drawable.ic_flashlight, "Torch", torchOn, tapRun<TorchAction>(), TapFrames.pop(tapFrame("torch")))
                Toggle(style, R.drawable.ic_wifi, "Internet", false, tapStart(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)))
                Toggle(style, R.drawable.ic_bluetooth, "Bluetooth", false, tapStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
                Toggle(style, R.drawable.ic_volume, "Sound", false, tapStart(Intent(Settings.Panel.ACTION_VOLUME)))
            }
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.Toggle(style: WidgetStyle, icon: Int, label: String, on: Boolean, tap: Tap, pop: Float = 1f) {
        val d = 44 * style.textScale
        PressButton(
            style, tap, GlanceModifier.defaultWeight().height(d.dp),
            ButtonLook(
                icon = icon, iconDp = d * 0.5f * pop, tint = if (on) bgArgb(style) else fgArgb(style), desc = label,
                shape = R.drawable.tap_shape_circle, shapeColor = if (on) fgArgb(style) else fgArgb(style, 0.14f), shapeDp = d,
            ),
        )
    }
}

class TogglesReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = TogglesWidget() }

// ---------- Daily quote ----------

object Quotes {
    val SKIP = intPreferencesKey("quote_skip")
}

class NextQuoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TapFx.play(context, glanceId, QuoteWidget(), "quote") { it[Quotes.SKIP] = (it[Quotes.SKIP] ?: 0) + 1 }
    }
}

class QuoteWidget : CustoWidget() {
    @Composable
    override fun Content(context: Context, id: GlanceId) {
        val style = currentStyle()
        val skip = currentState(Quotes.SKIP) ?: 0
        val offset = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(id) + skip
        val (quote, author) = WidgetLogic.QUOTES[WidgetLogic.quoteIndex(LocalDate.now(), offset, WidgetLogic.QUOTES.size)]
        // The next quote fades in.
        val reveal = TapFrames.reveal(tapFrame("quote"))
        Frame(style, tapRun<NextQuoteAction>()) {
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = style.hAlign.glance()) {
                WText("“$quote”", style = style.text(17f, alpha = reveal).copy(fontStyle = FontStyle.Italic), modifier = GlanceModifier.fillMaxWidth())
                if (style.showAuthor) {
                    Spacer(GlanceModifier.height(6.dp))
                    WText("— $author", style = style.text(13f, alpha = 0.75f * reveal), modifier = GlanceModifier.fillMaxWidth())
                }
            }
        }
    }
}

class QuoteReceiver : CustoReceiver() { override val glanceAppWidget: GlanceAppWidget = QuoteWidget() }
