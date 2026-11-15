package app.custo.feature.overlays

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.custo.container
import app.custo.shizuku.ShizukuState
import app.custo.ui.components.IconBadge
import app.custo.ui.components.InfoButton
import app.custo.ui.components.InfoText
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.delay

object NotificationAccess {
    fun component(ctx: Context) = ComponentName(ctx, CustoNotificationListener::class.java)
    fun granted(ctx: Context) = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Opens the toggle for Custo's listener itself (not the app's own notification settings). */
    fun openSettings(ctx: Context) {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(ctx).flattenToString())
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        for (i in listOf(detail, list)) {
            if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    /** App info, where Android 13+ hides "Allow restricted settings" in the ⋮ menu for sideloaded apps. */
    fun openAppInfo(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/**
 * Shown wherever a feature needs notification access. Hides itself once access is on.
 * Note: this is *notification access* (reading notifications), not permission to *send* them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationAccessCard(reason: String) {
    val ctx = LocalContext.current
    val c = ctx.container
    val runner = rememberActionRunner()
    val shizuku by c.shizuku.state.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val granted = remember(tick) { NotificationAccess.granted(ctx) }
    if (granted) return

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.NotificationsActive, size = 40, container = MaterialTheme.colorScheme.errorContainer, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text("Notification access", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            InfoButton("Notification access") {
                InfoText(reason)
                InfoText("This lets Custo see notifications. It's not about Custo sending any. Nothing is stored or sent.")
                InfoText("If Android says \"Restricted setting\", open App info → ⋮ → Allow restricted settings, then try again.")
                TextButton(onClick = { NotificationAccess.openAppInfo(ctx) }) { Text("Open App info") }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shizuku == ShizukuState.READY) {
                Button(onClick = {
                    runner.run("Notification access on") {
                        c.tweaks.allowNotificationListener(NotificationAccess.component(ctx).flattenToString())
                        delay(600); tick++
                    }
                }) { Text("Turn on") }
                OutlinedButton(onClick = { NotificationAccess.openSettings(ctx) }) { Text("Settings") }
            } else {
                Button(onClick = { NotificationAccess.openSettings(ctx) }) { Text("Turn on") }
            }
        }
    }
}
