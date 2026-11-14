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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val initial = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) Route.WidgetEditor(widgetId) else null
        setContent { CustoTheme { CustoNavHost(initial) } }
    }

    override fun onResume() {
        super.onResume()
        container.shizuku.refresh()
        lifecycleScope.launch { app.custo.feature.widgets.Widgets.refreshAll(this@MainActivity) }
    }
}
