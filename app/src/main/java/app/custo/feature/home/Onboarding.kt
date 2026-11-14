package app.custo.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.CustoMark
import app.custo.ui.components.enterAnimation

/** Three quick pages on first launch. [onDone] gets true if the user wants to set up Shizuku now. */
@Composable
fun Onboarding(onDone: (setupShizuku: Boolean) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val shizuku by LocalContext.current.container.shizuku.state.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (page < 2) TextButton(onClick = { onDone(false) }) { Text("Skip") }
            }
            AnimatedContent(
                targetState = page,
                transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut()) },
                modifier = Modifier.weight(1f),
                label = "onboarding",
            ) { p ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    when (p) {
                        0 -> {
                            CustoMark(Modifier.size(120.dp), animate = true)
                            Spacer(Modifier.height(36.dp))
                            Text("Custo", style = MaterialTheme.typography.displayMedium, modifier = Modifier.enterAnimation(2))
                            Text("Make your phone yours.", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.enterAnimation(3))
                        }
                        1 -> {
                            Text("One app for all of it", style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(24.dp))
                            Feature(0, Icons.Rounded.Tune, "System UI", "Colors, status bar, tiles, motion")
                            Feature(1, Icons.Rounded.Palette, "Themes", "Wallpapers and matching colors")
                            Feature(2, Icons.Rounded.Widgets, "Widgets", "Clocks, battery, music, notes")
                            Feature(3, Icons.Rounded.Layers, "Overlays", "Edge light, battery bar, volume")
                            Spacer(Modifier.height(16.dp))
                            Text("Free. No ads. Works offline.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        else -> {
                            Text("Unlock the deep stuff", style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "System colors, the status bar and Quick Settings need Shizuku, a free helper app. No root. You can do this later from Home.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (shizuku == ShizukuState.READY) {
                                Spacer(Modifier.height(16.dp))
                                Text("Shizuku is ready. You're all set.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    repeat(3) { i ->
                        val w by animateDpAsState(if (i == page) 22.dp else 8.dp, label = "dot")
                        Box(
                            Modifier.height(8.dp).width(w).clip(CircleShape)
                                .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                    }
                }
                if (page < 2) {
                    Button(onClick = { page++ }) { Text("Next") }
                } else if (shizuku == ShizukuState.READY) {
                    Button(onClick = { onDone(false) }) { Text("Start") }
                } else {
                    TextButton(onClick = { onDone(false) }) { Text("Later") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onDone(true) }) { Text("Set up") }
                }
            }
        }
    }
}

@Composable
private fun Feature(index: Int, icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(vertical = 8.dp).enterAnimation(index), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
