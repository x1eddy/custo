package app.custo

import android.app.Application
import android.content.Context
import kotlinx.coroutines.launch

class CustoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        app.custo.feature.widgets.WidgetFonts.init(this)
        container = AppContainer(this)
        container.shizuku.start()
        container.statusBarKeeper.watch(container.appScope)
        // Set backgrounds are read by widgets and previews; load them early, off the main thread.
        container.appScope.launch { container.setBackgrounds.ensureLoaded() }
        // Updates: a quiet check now and then, plus a daily background check (if the user keeps it on).
        container.updater.checkIfDue()
        container.appScope.launch { container.updater.schedule(container.prefs.get(app.custo.feature.update.Updater.AUTO, true)) }
    }
}

val Context.container: AppContainer get() = (applicationContext as CustoApp).container
