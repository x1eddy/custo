package app.custo.feature.systemui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.custo.container
import app.custo.nav.Navigator
import app.custo.ui.components.ActionRunner
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@RequiresApi(33)
private fun requestAdd(ctx: Context, spec: CustoTileSpec, runner: ActionRunner) {
    val sbm = ctx.getSystemService(StatusBarManager::class.java) ?: return
    sbm.requestAddTileService(
        ComponentName(ctx, spec.cls),
        ctx.getString(spec.label),
        Icon.createWithResource(ctx, spec.icon),
        ctx.mainExecutor,
    ) { result ->
        when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> runner.message("Added")
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> runner.message("Already in Quick Settings")
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> Unit
            else -> runner.message("Couldn't add. Edit Quick Settings to add it.")
        }
    }
}

@Composable
fun TilePackScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()

    ScreenScaffold(title = "Custo tiles", onBack = { nav.pop() }) {
        item { Hint("Extra Quick Settings tiles. They need Shizuku running.") }
        CustoTileSpec.entries.forEach { spec ->
            item(key = spec.name) {
                SectionCard(stringResource(spec.label), null, spec.desc) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        M3Icon(painterResource(spec.icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                requestAdd(ctx, spec, runner)
                            } else if (ready) {
                                runner.run("Added") { c.tweaks.addTile(ComponentName(ctx, spec.cls).flattenToString()) }
                            } else {
                                runner.message("Start Shizuku first, or add it from the Quick Settings editor.")
                            }
                        }) { Text("Add to Quick Settings") }
                    }
                }
            }
        }
    }
}
