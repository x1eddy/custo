package app.custo.feature.widgets

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.custo.ui.components.Hint
import app.custo.ui.components.SectionCard
import kotlin.math.roundToInt

/** Background fill for plain widgets: color, frosted glass, outline only, or a photo (crop-to-fill, dim, blur). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackgroundOptions(style: WidgetStyle, onChange: (WidgetStyle) -> Unit) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // Keep access after restarts; the widget reads it later from the home screen.
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onChange(style.copy(bgLook = BgLook.PHOTO, bgPhoto = uri.toString()))
        }
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    SectionCard("Background") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BgLook.entries.forEach { b ->
                FilterChip(style.bgLook == b, {
                    if (b == BgLook.PHOTO && style.bgPhoto == null) pick() else onChange(style.copy(bgLook = b))
                }, { Text(b.label) })
            }
        }
        when (style.bgLook) {
            BgLook.GLASS -> Hint("See-through tint with a soft sheen and a thin bright rim. Opacity sets how strong the tint is.")
            BgLook.OUTLINE -> Hint("Just a line in the text color (or the border color, if you set a border).")
            BgLook.PHOTO -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pick() }) { Icon(Icons.Rounded.Image, null); Text("  Change photo") }
                }
                Text("Dim · ${(style.bgDim * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(style.bgDim, { onChange(style.copy(bgDim = it)) }, valueRange = 0f..0.9f)
                Text("Blur · ${(style.bgBlur * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(style.bgBlur, { onChange(style.copy(bgBlur = it)) }, valueRange = 0f..1f)
                Hint("The photo is scaled down and cropped to fill. Only this photo is shared with Custo.")
            }
            BgLook.SOLID -> Unit
        }
    }
}
