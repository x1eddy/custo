package app.custo.feature.systemui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.custo.container
import app.custo.data.Prefs
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.shizuku.ShizukuManager
import app.custo.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Status bar disable flags live in SystemUI's memory only, so a reboot or SystemUI restart drops them.
 * This keeps the user's wish in prefs and re-sends it whenever Shizuku becomes ready.
 */
class StatusBarKeeper(
    private val prefs: Prefs,
    private val tweaks: SystemTweaks,
    private val shizuku: ShizukuManager,
) {
    /** The stored wish. If "Undo all" wiped the undo record, the wish is dropped too. */
    suspend fun desired(): Set<DisableFlag> {
        val flags = DisableFlag.parse(prefs.get(SysKeys.statusBarFlags, ""))
        if (flags.isNotEmpty() && !tweaks.isRecorded(Namespace.CUSTO, SystemTweaks.STATUSBAR_FLAGS_KEY)) {
            prefs.set(SysKeys.statusBarFlags, "")
            return emptySet()
        }
        return flags
    }

    suspend fun set(flags: Set<DisableFlag>) {
        tweaks.setDisableFlags(flags)
        prefs.set(SysKeys.statusBarFlags, DisableFlag.store(flags))
    }

    /** Re-sends the stored flags (nothing if none are wanted). */
    suspend fun reapply() {
        val flags = desired()
        if (flags.isNotEmpty()) runCatching { tweaks.setDisableFlags(flags) }
    }

    /** Called once per process: re-apply every time Shizuku turns ready. */
    fun watch(scope: CoroutineScope) {
        scope.launch {
            shizuku.state.filter { it == ShizukuState.READY }.collect { runCatching { reapply() } }
        }
    }

    suspend fun awaitReadyAndReapply(timeoutMs: Long) {
        shizuku.refresh()
        withTimeoutOrNull(timeoutMs) { shizuku.state.first { it == ShizukuState.READY } } ?: return
        reapply()
    }
}

/** Re-applies status bar flags after boot, if Shizuku is already up (e.g. started by root or a boot helper). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                runCatching { c.statusBarKeeper.awaitReadyAndReapply(8_000) }
            } finally {
                pending.finish()
            }
        }
    }
}
