package app.custo.feature.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.shizuku.ShizukuManager
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.SupportBadge

@Composable
fun ShizukuSetupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val state by c.shizuku.state.collectAsStateWithLifecycle()
    fun open(intent: Intent) = runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    ScreenScaffold(title = "Set up Shizuku", onBack = onBack) {
        item {
            Hint("Shizuku gives Custo the same access as a computer plugged in over USB. That's enough to restyle the system, with no root.")
        }
        item {
            val done = state != ShizukuState.NOT_INSTALLED
            SectionCard("1  Install Shizuku", Icons.Rounded.Download, "Free, open source.", badge = { SupportBadge(if (done) "Done" else "To do", done) }) {
                if (!done) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuManager.SHIZUKU_PACKAGE}"))) }) { Text("Play Store") }
                    OutlinedButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))) }) { Text("GitHub") }
                }
            }
        }
        item {
            val done = state == ShizukuState.NO_PERMISSION || state == ShizukuState.READY
            SectionCard("2  Start it", Icons.Rounded.Wifi, null, badge = { SupportBadge(if (done) "Running" else "To do", done) }) {
                Hint("Developer options → Wireless debugging on → in Shizuku, pair and start. Repeat after reboots.\n(No Developer options? Settings → About phone → tap Build number 7×.)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }) { Text("Developer options") }
                    OutlinedButton(onClick = {
                        ctx.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)?.let { open(it) }
                    }) { Text("Open Shizuku") }
                }
            }
        }
        item {
            val done = state == ShizukuState.READY
            SectionCard("3  Allow Custo", Icons.Rounded.Key, "Custo only runs what you tap.", badge = { SupportBadge(if (done) "Allowed" else "To do", done) }) {
                if (!done) Button(onClick = { c.shizuku.requestPermission() }, enabled = state == ShizukuState.NO_PERMISSION) { Text("Allow access") }
            }
        }
        item {
            SectionCard("Got a computer?", Icons.Rounded.PlayArrow, null) {
                Hint("Connect over USB and run:\nadb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh")
                OutlinedButton(onClick = { c.shizuku.refresh() }) { Text("Check again") }
            }
        }
    }
}
