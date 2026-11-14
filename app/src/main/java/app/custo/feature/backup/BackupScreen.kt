package app.custo.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.custo.container
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
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

    ScreenScaffold(title = "Backup", onBack = onBack) {
        item {
            SectionCard("Export profile", Icons.Rounded.FileDownload, "Looks, widgets, overlays and your original system values, in one file.", onClick = {
                exporter.launch("custo-profile-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json")
            })
        }
        item {
            SectionCard("Import profile", Icons.Rounded.FileUpload, "Replaces your current Custo settings.", onClick = {
                importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            })
        }
        item { Hint("Plain JSON. Custo has no internet permission, so nothing leaves your phone.") }
    }
}
