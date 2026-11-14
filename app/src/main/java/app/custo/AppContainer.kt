package app.custo

import android.content.Context
import app.custo.data.Prefs
import app.custo.data.custoStore
import app.custo.data.originalsStore
import app.custo.data.profileStateStore
import app.custo.feature.backup.BackupManager
import app.custo.feature.systemui.SysKeys
import app.custo.shizuku.ShellUnavailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import app.custo.feature.icons.IconRepository
import app.custo.feature.profiles.ProfileEngine
import app.custo.feature.profiles.ProfilesRepository
import app.custo.feature.themes.LooksRepository
import app.custo.feature.widgets.HabitStore
import app.custo.feature.themes.WallpaperStudio
import app.custo.feature.systemui.StatusBarKeeper
import app.custo.feature.systemui.SystemTweaks
import app.custo.shizuku.OriginalsStore
import app.custo.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container: one instance per process, created by [CustoApp]. */
class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val prefs = Prefs(app.custoStore)
    val originals = OriginalsStore(app.originalsStore)
    val shizuku = ShizukuManager(app)
    val tweaks = SystemTweaks(shizuku, originals)
    val wallpapers = WallpaperStudio(app)
    val looks = LooksRepository(prefs)
    val icons = IconRepository(app, prefs)
    val backup = BackupManager(app, prefs)
    val profiles = ProfilesRepository(prefs)
    val profileEngine = ProfileEngine(profiles, tweaks, shizuku, app.profileStateStore)
    val habits = HabitStore(app)

    /** Process-wide scope for fire-and-forget work (boot re-apply, tiles). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val statusBarKeeper = StatusBarKeeper(prefs, tweaks, shizuku)

    private val _undoResult = MutableStateFlow<String?>(null)
    /** Message from the last "Undo all", until the UI shows it. Outlives activity recreation. */
    val undoResult: StateFlow<String?> = _undoResult
    private val _undoRunning = MutableStateFlow(false)
    val undoRunning: StateFlow<Boolean> = _undoRunning

    /**
     * Puts back everything Custo changed. Runs in [appScope]: restoring font scale, density, screen
     * size or Monet recreates the activity, which would cancel a UI-scoped job halfway.
     */
    fun undoAll() {
        if (!_undoRunning.compareAndSet(expect = false, update = true)) return
        appScope.launch {
            val msg = try {
                // Stop per-app profiles first so they don't write their saved values back afterwards.
                profileEngine.reset()
                val notes = tweaks.restoreAll()
                prefs.edit {
                    it.remove(SysKeys.demoOn)
                    it.remove(SysKeys.caffeinePrevTimeout)
                }
                if (notes.isEmpty()) "All back to how it was" else notes.joinToString("\n")
            } catch (e: ShellUnavailableException) {
                "Start Shizuku first. Open Home → Set up Shizuku"
            } catch (e: Throwable) {
                e.message?.take(160) ?: "Something went wrong"
            } finally {
                _undoRunning.value = false
            }
            _undoResult.value = msg
        }
    }

    fun consumeUndoResult() {
        _undoResult.value = null
    }
}
