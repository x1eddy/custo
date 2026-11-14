package app.custo

import android.content.Context
import app.custo.data.Prefs
import app.custo.data.custoStore
import app.custo.data.originalsStore
import app.custo.feature.backup.BackupManager
import app.custo.feature.icons.IconRepository
import app.custo.feature.profiles.ProfileEngine
import app.custo.feature.profiles.ProfilesRepository
import app.custo.feature.themes.LooksRepository
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
    val backup = BackupManager(app, prefs, originals)
    val profiles = ProfilesRepository(prefs)
    val profileEngine = ProfileEngine(profiles, tweaks, shizuku)

    /** Process-wide scope for fire-and-forget work (boot re-apply, tiles). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val statusBarKeeper = StatusBarKeeper(prefs, tweaks, shizuku)
}
