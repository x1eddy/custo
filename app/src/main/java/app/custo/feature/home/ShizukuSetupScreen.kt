package app.custo.feature.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.shizuku.ShizukuManager
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.InfoButton
import app.custo.ui.components.InfoText
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShizukuSetupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val state by c.shizuku.state.collectAsStateWithLifecycle()
    fun open(intent: Intent) = runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    ScreenScaffold(title = "Shizuku", onBack = onBack, actions = {
        InfoButton(
            "Why Shizuku",
            "Shizuku gives Custo the same access as a computer plugged in over USB. That's enough to restyle the system, with no root. " +
                "It's free and open source, and Custo only runs what you tap.",
        )
    }) {
        item {
            val done = state != ShizukuState.NOT_INSTALLED
            StepCard(1, Icons.Rounded.Download, "Install", done) {
                if (!done) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuManager.SHIZUKU_PACKAGE}"))) }) { Text("Play Store") }
                    OutlinedButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))) }) { Text("GitHub") }
                }
            }
        }
        item {
            val done = state == ShizukuState.NO_PERMISSION || state == ShizukuState.READY
            StepCard(2, Icons.Rounded.PlayArrow, "Start", done, info = {
                InfoText("Developer options → Wireless debugging on → in Shizuku, pair and start. Repeat after reboots.")
                InfoText("No Developer options? Settings → About phone → tap Build number 7×.")
            }) {
                if (!done) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }) { Text("Developer options") }
                    OutlinedButton(onClick = {
                        ctx.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)?.let { open(it) }
                    }) { Text("Open Shizuku") }
                }
            }
        }
        item {
            val done = state == ShizukuState.READY
            StepCard(3, Icons.Rounded.Key, "Allow", done) {
                if (!done) Button(onClick = { c.shizuku.requestPermission() }, enabled = state == ShizukuState.NO_PERMISSION) { Text("Allow access") }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoButton("Got a computer?") {
                    InfoText("Connect over USB and run:")
                    SelectionContainer {
                        Text("adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Icon(Icons.Rounded.Computer, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { c.shizuku.refresh() }) { Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Text("  Check again") }
            }
        }
    }
}

/** A numbered setup step: big icon, one word, a check when done. */
@Composable
private fun StepCard(n: Int, icon: ImageVector, title: String, done: Boolean, info: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(if (done) cs.primary else cs.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(if (done) Icons.Rounded.Check else icon, null, tint = if (done) cs.onPrimary else cs.onPrimaryContainer, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("$n", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            if (info != null) InfoButton(title, content = info)
        }
        content()
    }
}
