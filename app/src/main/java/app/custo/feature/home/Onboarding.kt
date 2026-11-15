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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.layout.RowScope
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
                            Text("All in one", style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Feature(0, Icons.Rounded.Tune, "System UI", Modifier.weight(1f))
                                Feature(1, Icons.Rounded.Palette, "Themes", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Feature(2, Icons.Rounded.Widgets, "Widgets", Modifier.weight(1f))
                                Feature(3, Icons.Rounded.Layers, "Overlays", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.enterAnimation(4)) {
                                Perk(Icons.Rounded.Favorite, "Free")
                                Perk(Icons.Rounded.Block, "No ads")
                                Perk(Icons.Rounded.CloudOff, "Offline")
                            }
                        }
                        else -> {
                            Text("Go deeper", style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(8.dp))
                            Text("With Shizuku. No root.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(28.dp))
                            val ready = shizuku == ShizukuState.READY
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Step(0, Icons.Rounded.Download, "Install", ready || shizuku != ShizukuState.NOT_INSTALLED)
                                StepLink()
                                Step(1, Icons.Rounded.PlayArrow, "Start", ready || shizuku == ShizukuState.NO_PERMISSION)
                                StepLink()
                                Step(2, Icons.Rounded.Key, "Allow", ready)
                            }
                            Spacer(Modifier.height(28.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.enterAnimation(3)) {
                                Perk(Icons.Rounded.Palette, "Colors")
                                Perk(Icons.Rounded.Tune, "Status bar")
                                Perk(Icons.Rounded.GridView, "Tiles")
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
private fun Feature(index: Int, icon: ImageVector, title: String, modifier: Modifier) {
    Column(
        modifier.enterAnimation(index).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp)) }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Perk(icon: ImageVector, label: String) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** One setup step as a big icon with a word under it; ticked when done. */
@Composable
private fun Step(index: Int, icon: ImageVector, label: String, done: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.enterAnimation(index), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(if (done) cs.primary else cs.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(if (done) Icons.Rounded.Check else icon, null, tint = if (done) cs.onPrimary else cs.onPrimaryContainer, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RowScope.StepLink() {
    Box(Modifier.weight(1f).padding(horizontal = 6.dp).padding(bottom = 24.dp).height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
}
