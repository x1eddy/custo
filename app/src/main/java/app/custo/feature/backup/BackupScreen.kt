package app.custo.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.custo.container
import app.custo.ui.components.HubTile
import app.custo.ui.components.InfoButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.rememberActionRunner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val runner = rememberActionRunner()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runner.run("Profile exported") { c.backup.export(uri) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runner.run { val n = c.backup.import(uri); runner.message("Imported $n settings") }
    }

    ScreenScaffold(title = "Backup", onBack = onBack, actions = {
        InfoButton(
            "Backup",
            "Export saves looks, widgets, overlays and your original system values in one plain JSON file. " +
                "Import replaces your current Custo settings. Custo has no internet permission, so nothing leaves your phone.",
        )
    }) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HubTile("Export", Icons.Rounded.FileDownload, Modifier.weight(1f)) {
                    exporter.launch("custo-profile-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json")
                }
                HubTile("Import", Icons.Rounded.FileUpload, Modifier.weight(1f), badge = {
                    Text("Replaces", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }) {
                    importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }
        }
    }
}
