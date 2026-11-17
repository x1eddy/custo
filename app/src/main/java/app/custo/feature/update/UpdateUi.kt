package app.custo.feature.update

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.custo.container
import app.custo.ui.components.SwitchRow

/** The release a card should show for [s], if any. */
private fun infoOf(s: UpdateState): UpdateInfo? = when (s) {
    is UpdateState.Available -> s.info
    is UpdateState.Downloading -> s.info
    is UpdateState.Installing -> s.info
    is UpdateState.Failed -> s.info
    else -> null
}

/** Home banner: only when a newer version is out and the user didn't say "Later" to it. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val updater = LocalContext.current.container.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val skipped by updater.skipped.collectAsStateWithLifecycle("")
    val info = infoOf(state) ?: return
    if (state is UpdateState.Available && info.version == skipped) return
    UpdateCard(state, info, onLater = { updater.skip(info) }, modifier = modifier)
}

/** Settings section: current version, automatic checks and "Check now". */
@Composable
fun UpdateSettings() {
    val updater = LocalContext.current.container.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val auto by updater.autoCheck.collectAsStateWithLifecycle(true)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val info = infoOf(state)
        if (info != null) {
            UpdateCard(state, info, onLater = null)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Custo ${updater.currentVersion}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when (state) {
                            UpdateState.Checking -> "Checking…"
                            UpdateState.UpToDate -> "You have the latest version"
                            is UpdateState.Failed -> (state as UpdateState.Failed).message
                            else -> "Updates come from GitHub"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { updater.check(manual = true) }, enabled = state !is UpdateState.Checking) { Text("Check now") }
            }
        }
        SwitchRow("Check automatically", "Once a day; you always choose when to install", auto) { updater.setAutoCheck(it) }
    }
}

@Composable
private fun UpdateCard(state: UpdateState, info: UpdateInfo, onLater: (() -> Unit)?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val updater = ctx.container.updater
    var notes by remember { mutableStateOf(false) }
    // Android's "allow installs from Custo" screen: install as soon as the user comes back with it allowed.
    var waitingForPermission by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(waitingForPermission) {
        if (!waitingForPermission) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (updater.canInstall()) { waitingForPermission = false; updater.downloadAndInstall(info) }
        }
    }
    fun install() {
        if (updater.canInstall()) updater.downloadAndInstall(info) else { waitingForPermission = true; updater.openInstallPermission() }
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text("Custo ${info.version}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (state) {
                            is UpdateState.Downloading -> "Downloading… ${(state.progress * 100).toInt()} %"
                            is UpdateState.Installing -> "Installing…"
                            is UpdateState.Failed -> state.message
                            else -> "New version" + if (info.apkBytes > 0) " · ${"%.1f".format(info.apkBytes / 1_048_576f)} MB" else ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (state is UpdateState.Downloading) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (info.notes.isNotBlank()) TextButton(onClick = { notes = true }) { Text("What's new") }
                Row(Modifier.weight(1f)) {}
                when (state) {
                    is UpdateState.Downloading -> TextButton(onClick = { updater.cancel() }) { Text("Cancel") }
                    is UpdateState.Installing -> {}
                    else -> {
                        if (onLater != null) TextButton(onClick = onLater) { Text("Later") }
                        Button(onClick = ::install) { Text(if (state is UpdateState.Failed) "Retry" else "Update") }
                    }
                }
            }
        }
    }

    if (notes) {
        AlertDialog(
            onDismissRequest = { notes = false },
            title = { Text("What's new in ${info.version}") },
            text = { Text(info.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { notes = false; install() }) { Text("Update") } },
            dismissButton = { TextButton(onClick = { notes = false }) { Text("Close") } },
        )
    }
}
