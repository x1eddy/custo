package app.custo.feature.systemui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * "Keep this?" countdown for display changes that may leave the screen unusable. It runs in the app
 * scope, not in composition: changing the resolution recreates the activity, and the revert must
 * still happen if the UI that started it is gone.
 */
object RevertTimer {
    data class Pending(val title: String, val seconds: Int)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending

    private var job: Job? = null
    private var revert: (suspend () -> Unit)? = null

    @Synchronized
    fun start(scope: CoroutineScope, title: String, seconds: Int = 15, revert: suspend () -> Unit) {
        job?.cancel()
        this.revert = revert
        job = scope.launch {
            for (s in seconds downTo 1) {
                _pending.value = Pending(title, s)
                delay(1000)
            }
            revertNow(scope)
        }
    }

    @Synchronized
    fun keep() {
        job?.cancel()
        job = null
        revert = null
        _pending.value = null
    }

    @Synchronized
    fun revertNow(scope: CoroutineScope) {
        val r = revert
        job?.cancel()
        job = null
        revert = null
        _pending.value = null
        if (r != null) scope.launch { runCatching { r() } }
    }
}

/** Snackbar text for a failed display change (same wording as ActionRunner). */
internal fun errorText(e: Throwable): String =
    if (e is app.custo.shizuku.ShellUnavailableException) "Start Shizuku first. Open Home → Set up Shizuku"
    else e.message?.take(160) ?: "Something went wrong"

@Composable
fun RevertDialog(scope: CoroutineScope) {
    val p by RevertTimer.pending.collectAsStateWithLifecycle()
    val pending = p ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(pending.title) },
        text = { Text("Switching back in ${pending.seconds} s") },
        confirmButton = { TextButton(onClick = { RevertTimer.keep() }) { Text("Keep") } },
        dismissButton = { TextButton(onClick = { RevertTimer.revertNow(scope) }) { Text("Revert") } },
    )
}
