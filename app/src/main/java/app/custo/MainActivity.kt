package app.custo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.custo.nav.CustoNavHost
import app.custo.nav.Route
import app.custo.ui.theme.CustoTheme
import android.appwidget.AppWidgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.data.Keys
import app.custo.feature.home.Onboarding
import app.custo.ui.theme.AppAppearance
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val debugSet = if (BuildConfig.DEBUG) intent.getStringExtra("debug_widget_set") else null
        val initial = when {
            widgetId != AppWidgetManager.INVALID_APPWIDGET_ID -> Route.WidgetEditor(widgetId)
            debugSet != null -> Route.WidgetSet(debugSet)
            else -> null
        }
        setContent {
            val appearance by remember { container.prefs.data.map(AppAppearance::read) }.collectAsStateWithLifecycle(AppAppearance())
            val dark = appearance.isDark(isSystemInDarkTheme())
            // Status/navigation bar icons follow Custo's own light/dark choice, not just the system's.
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    // The same scrims enableEdgeToEdge() uses by default (seen only with 3-button navigation).
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.argb(0xE6, 0xFF, 0xFF, 0xFF), android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B)) { dark },
                )
            }
            CustoTheme(appearance, dark) {
                val onboarded by container.prefs.flow(Keys.onboarded, false).collectAsStateWithLifecycle(initialValue = null)
                var startRoute by remember { mutableStateOf<Route?>(initial) }
                when (onboarded) {
                    null -> Unit // first frame: DataStore not read yet; the splash/window background shows
                    false -> Onboarding { setup ->
                        if (setup) startRoute = Route.ShizukuSetup
                        lifecycleScope.launch { container.prefs.set(Keys.onboarded, true) }
                    }
                    true -> CustoNavHost(startRoute)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        container.shizuku.refresh()
        // Off the main thread and after the first frames: it's ~35 binder calls plus a Glance
        // re-render per placed widget, which used to compete with the screen opening.
        lifecycleScope.launch(Dispatchers.Default) {
            delay(1_500)
            app.custo.feature.widgets.Widgets.refreshAll(this@MainActivity)
        }
    }
}
