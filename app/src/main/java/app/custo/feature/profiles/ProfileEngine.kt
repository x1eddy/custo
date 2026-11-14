package app.custo.feature.profiles

import app.custo.feature.systemui.SystemTweaks
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.shizuku.ShizukuManager
import app.custo.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies a per-app profile when its app comes to the front and puts the previous values back
 * when the user leaves. Fed by the overlay (accessibility) service's window-change events.
 */
class ProfileEngine(
    private val repo: ProfilesRepository,
    private val tweaks: SystemTweaks,
    private val shizuku: ShizukuManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var active: AppProfile? = null
    /** Values that were in place before [active] was applied. */
    private var saved: Map<Pair<Namespace, String>, String?> = emptyMap()
    private var pending: Job? = null

    /** Call on every foreground-app change. Debounced so app-switch animations don't thrash settings. */
    fun onForegroundApp(pkg: String?) {
        pending?.cancel()
        pending = scope.launch {
            delay(400)
            mutex.withLock { switchTo(pkg) }
        }
    }

    private suspend fun switchTo(pkg: String?) {
        if (shizuku.state.value != ShizukuState.READY) return
        val target = ProfileLogic.forPackage(pkg, repo.all())
        if (target?.id == active?.id) return
        runCatching {
            // Put back what the previous profile changed.
            saved.forEach { (k, v) -> tweaks.write(k.first, k.second, v) }
            saved = emptyMap()
            active = null
            if (target != null) {
                val changes = ProfileLogic.changes(target)
                saved = changes.associate { (it.ns to it.key) to tweaks.read(it.ns, it.key) }
                changes.forEach { tweaks.write(it.ns, it.key, it.value) }
                active = target
            }
        }
    }
}
