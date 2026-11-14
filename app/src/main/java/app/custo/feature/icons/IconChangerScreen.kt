package app.custo.feature.icons

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.feature.profiles.AppList
import app.custo.feature.profiles.LaunchableApp
import app.custo.nav.Navigator
import app.custo.nav.Route
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SectionLabel
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.flow.first
import java.io.File

private enum class Source(val label: String) { STYLE("Style"), PACK("Pack"), IMAGE("Picture") }

/** Pick an app → choose its new icon and name → add it to the home screen (or update a shortcut [editId]). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconChangerScreen(nav: Navigator, pkg: String?, editId: String?) {
    val ctx = LocalContext.current
    val c = ctx.container
    val runner = rememberActionRunner()
    val queue = rememberPinQueue()
    val savedStyles by c.icons.styles.collectAsStateWithLifecycle(emptyList())
    val imported by c.icons.imported.collectAsStateWithLifecycle()

    var app by remember { mutableStateOf<LaunchableApp?>(null) }
    var picking by remember { mutableStateOf(pkg == null) }
    var label by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(Source.STYLE) }
    var style by remember { mutableStateOf(PRESET_STYLES.first().style) }
    var pack by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
    var image by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(pkg, editId) {
        c.icons.refreshImported()
        if (pkg != null) {
            val a = AppList.load(ctx).firstOrNull { it.pkg == pkg } ?: LaunchableApp(pkg, pkg, null)
            app = a; label = a.label
        }
        if (editId != null) c.icons.history.first().firstOrNull { it.id == editId }?.let { h ->
            label = h.label
            h.style?.let { style = it }
            source = when {
                h.source.startsWith("pack:") -> Source.PACK
                h.source == "image" -> Source.IMAGE
                else -> Source.STYLE
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runner.run { image = c.icons.importImages(listOf(uri)).firstOrNull() ?: error("Couldn't read that picture") }
    }

    PinQueuePanel(queue)
    if (picking) AppPickerDialog("Change which app?", multi = false, onDismiss = { picking = false; if (app == null) nav.pop() }) { picked ->
        picking = false
        val a = picked.first()
        if (a.pkg != app?.pkg) { pack = null }
        app = a; label = a.label
    }

    ScreenScaffold(title = if (editId != null) "Edit icon" else "Change icon", onBack = { nav.pop() }) {
        val a = app ?: return@ScreenScaffold
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        a.icon?.let { Image(it.asImageBitmap(), "Current icon", Modifier.size(56.dp)) }
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(28.dp).width(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    val preview: Bitmap? = when (source) {
                        Source.STYLE -> rememberStyledIcon(style, a.pkg, a.label, 288)
                        Source.PACK -> pack?.second
                        Source.IMAGE -> image?.let { f -> produceState<Bitmap?>(null, f) { value = c.icons.loadImported(f) }.value }
                    }
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        if (preview != null) Image(preview.asImageBitmap(), "New icon", Modifier.size(96.dp))
                        else Text("Pick one below", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (editId == null) TextButton(onClick = { picking = true }) { Text("Pick another app") }
                OutlinedTextField(label, { label = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Name under the icon") }, singleLine = true)
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Source.entries.forEachIndexed { i, s ->
                    SegmentedButton(source == s, { source = s }, SegmentedButtonDefaults.itemShape(i, Source.entries.size)) { Text(s.label) }
                }
            }
        }

        when (source) {
            Source.STYLE -> item {
                SectionCard {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        (savedStyles + PRESET_STYLES).forEach { s ->
                            val bmp = rememberStyledIcon(s.style, a.pkg, a.label, 144)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable { style = s.style }) {
                                IconBitmap(
                                    bmp,
                                    Modifier.size(58.dp).clip(MaterialTheme.shapes.medium).then(
                                        if (style == s.style) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier,
                                    ),
                                    s.name,
                                )
                                Text(s.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                    TextButton(onClick = { nav.push(Route.IconMaker(style.encode())) }) { Icon(Icons.Rounded.AutoFixHigh, null); Text("  Make your own style") }
                }
            }
            Source.PACK -> item {
                SectionCard { PackIconRow(a.pkg, pack?.first) { p, b -> pack = p to b } }
            }
            Source.IMAGE -> item {
                SectionCard {
                    OutlinedButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, null); Text("  Pick a picture")
                    }
                    if (imported.isNotEmpty()) {
                        SectionLabel("Imported")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            imported.forEach { f ->
                                ImportedThumb(
                                    f,
                                    Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).then(
                                        if (image == f) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier,
                                    ).clickable { image = f },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            val ready = when (source) { Source.STYLE -> true; Source.PACK -> pack != null; Source.IMAGE -> image != null }
            Button(
                onClick = {
                    val name = label.ifBlank { a.label }
                    val job = when (source) {
                        Source.STYLE -> styleJob(style, a, name, editId)
                        Source.PACK -> pack!!.let { (p, _) ->
                            PinJob(a.pkg, name, "pack:$p", null, adaptive = false, editId = editId) { cx ->
                                IconPacks.iconFor(cx, p, a.pkg) ?: error("The pack has no icon for ${a.label}")
                            }
                        }
                        Source.IMAGE -> imageJob(image!!, a, name, editId)
                    }
                    queue.start(listOf(job))
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.AutoMirrored.Rounded.AddToHomeScreen, null); Text(if (editId != null) "  Update icon" else "  Add to home screen") }
        }
        item {
            Hint(
                if (editId != null) "If the shortcut is still on your home screen it changes right away. Otherwise your launcher asks to add it again."
                else "Your launcher will ask to add it. The original icon stays; long-press it and pick Remove if you like.",
            )
        }
    }
}
