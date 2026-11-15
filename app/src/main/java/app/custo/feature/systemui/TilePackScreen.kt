package app.custo.feature.systemui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.draw.clip
import app.custo.ui.components.IconNote
import app.custo.ui.components.InfoButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.custo.container
import app.custo.nav.Navigator
import app.custo.ui.components.ActionRunner
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.rememberActionRunner
import androidx.compose.foundation.layout.Row
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
        CustoTileSpec.entries.forEach { spec ->
            item(key = spec.name) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        M3Icon(painterResource(spec.icon), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(stringResource(spec.label), style = MaterialTheme.typography.titleMedium)
                        if (spec == CustoTileSpec.WIRELESS_DEBUGGING) IconNote(Icons.Rounded.Warning, "May stop Shizuku", MaterialTheme.colorScheme.error)
                    }
                    InfoButton(stringResource(spec.label), spec.desc)
                    FilledTonalIconButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            requestAdd(ctx, spec, runner)
                        } else if (ready) {
                            runner.run("Added") { c.tweaks.addTile(ComponentName(ctx, spec.cls).flattenToString()) }
                        } else {
                            runner.message("Start Shizuku first")
                        }
                    }) { M3Icon(Icons.Rounded.Add, "Add to Quick Settings") }
                }
            }
        }
    }
}
