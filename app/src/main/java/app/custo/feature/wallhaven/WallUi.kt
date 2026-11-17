package app.custo.feature.wallhaven

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.custo.ui.theme.LocalReduceMotion
import java.util.Locale
import androidx.compose.material.icons.rounded.ThumbDown

/** Short human sizes: "840 KB", "6.2 MB". */
object WallFormat {
    fun bytes(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "${(b + 512) / 1024} KB"
        b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", b / (1024.0 * 1024.0 * 1024.0))
    }

    /** "#1a2b3c" → ARGB, null when malformed. */
    fun color(hex: String): Int? {
        val h = hex.trim().removePrefix("#")
        if (h.length != 6) return null
        return h.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
    }
}

/** Soft moving placeholder while a picture loads. */
@Composable
fun Shimmer(modifier: Modifier = Modifier, tint: Color? = null) {
    val base = tint ?: MaterialTheme.colorScheme.surfaceContainerHigh
    if (LocalReduceMotion.current) {
        Box(modifier.background(base))
        return
    }
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "x")
    val hi = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier.background(base).background(
            Brush.linearGradient(
                listOf(Color.Transparent, hi.copy(alpha = 0.9f), Color.Transparent),
                start = androidx.compose.ui.geometry.Offset(x * 600f, 0f),
                end = androidx.compose.ui.geometry.Offset(x * 600f + 300f, 600f),
            ),
        ),
    )
}

/**
 * A wallpaper picture tile: [src] is an https URL or a local file path. Shows a shimmer (tinted
 * with the image's main color when known) until it loads; [overlay] draws on top.
 */
@Composable
fun WallTile(
    src: String?,
    modifier: Modifier = Modifier,
    aspect: Float? = 9f / 16f,
    targetPx: Int = 480,
    placeholder: Color? = null,
    corner: Int = 18,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val img by rememberWallImage(src, targetPx)
    Box(
        modifier.fillMaxWidth().then(if (aspect != null) Modifier.aspectRatio(aspect) else Modifier).clip(RoundedCornerShape(corner.dp))
            .then(
                when {
                    onLongClick != null -> @OptIn(ExperimentalFoundationApi::class) Modifier.combinedClickable(onLongClick = onLongClick, onClick = onClick ?: {})
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                },
            ),
    ) {
        Crossfade(img, label = "tile") { b ->
            if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Shimmer(Modifier.fillMaxSize(), placeholder)
        }
        overlay()
    }
}

/** Dark fade at the bottom of a picture so white text stays readable. */
val BottomScrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))

/**
 * Long-press menu on a wallpaper tile: "Not this" hides the picture everywhere for good, deletes it
 * if it was saved, and refills its pack with the next good one.
 */
@Composable
fun NotThisMenu(expanded: Boolean, onDismiss: () -> Unit, onNotThis: () -> Unit) {
    androidx.compose.material3.DropdownMenu(expanded, onDismiss) {
        androidx.compose.material3.DropdownMenuItem(
            text = { androidx.compose.material3.Text("Not this") },
            leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.ThumbDown, null) },
            onClick = { onDismiss(); onNotThis() },
        )
    }
}
