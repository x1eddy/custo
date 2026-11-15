package app.custo.feature.systemui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import app.custo.container
import app.custo.nav.Navigator
import app.custo.shizuku.ShellCommands.Namespace
import app.custo.ui.components.skeletonItems
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SOUNDS = listOf(
    Toggle(Namespace.SYSTEM, "haptic_feedback_enabled", "Touch vibration", default = true, icon = Icons.Rounded.Vibration),
    Toggle(Namespace.SYSTEM, "sound_effects_enabled", "Touch sounds", default = true, icon = Icons.Rounded.TouchApp),
    Toggle(Namespace.SYSTEM, "lockscreen_sounds_enabled", "Lock sound", default = true, icon = Icons.Rounded.Lock),
    Toggle(Namespace.SECURE, "charging_sounds_enabled", "Charging sound", default = true, icon = Icons.Rounded.BatteryChargingFull),
    Toggle(Namespace.SECURE, "charging_vibration_enabled", "Charging buzz", default = true, icon = Icons.Rounded.Bolt),
)

/** Ringtone types with the `system` setting RingtoneManager stores them in. */
private enum class Tone(val type: Int, val key: String, val label: String, val icon: ImageVector) {
    RINGTONE(RingtoneManager.TYPE_RINGTONE, "ringtone", "Ringtone", Icons.Rounded.Call),
    NOTIFICATION(RingtoneManager.TYPE_NOTIFICATION, "notification_sound", "Notification", Icons.Rounded.Notifications),
    ALARM(RingtoneManager.TYPE_ALARM, "alarm_alert", "Alarm", Icons.Rounded.Alarm),
}

private val SOUND_KEYS = SOUNDS.ids() + Tone.entries.map { Ids.of(Namespace.SYSTEM, it.key) }

private fun toneTitle(ctx: Context, type: Int): String {
    val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, type) ?: return "None"
    return runCatching { RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) }.getOrNull() ?: "Custom"
}

@Composable
fun SoundScreen(nav: Navigator) {
    val ctx = LocalContext.current
    val c = ctx.container
    val ready = rememberShizukuReady()
    val runner = rememberActionRunner()
    var picking by rememberSaveable { mutableIntStateOf(-1) }
    val st = rememberDeviceState("sound", SOUND_KEYS, SOUNDS.probeKeys()) { v -> v.toggles(SOUNDS) to Tone.entries.map { v.v(Namespace.SYSTEM, it.key) } }
    // Tone names come from the media provider (local, no Shizuku); re-read when a tone setting changes.
    val tones by produceState(emptyMap<Tone, String>(), st?.second) {
        value = withContext(Dispatchers.IO) { Tone.entries.associateWith { toneTitle(ctx, it.type) } }
    }

    fun act(msg: String? = null, block: suspend () -> Unit) = runner.run(msg) { block() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val tone = Tone.entries.getOrNull(picking)
        picking = -1
        if (tone == null || res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri: Uri? = res.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
        act("${tone.label} set") {
            c.tweaks.rememberOriginal(Namespace.SYSTEM, tone.key)
            if (!Settings.System.canWrite(ctx)) c.tweaks.grantWriteSettings(ctx.packageName)
            withContext(Dispatchers.IO) { RingtoneManager.setActualDefaultRingtoneUri(ctx, tone.type, uri) }
            c.deviceState.settingWritten(Ids.of(Namespace.SYSTEM, tone.key), uri?.toString())
        }
    }

    ScreenScaffold(title = "Sound", onBack = { nav.pop() }) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        if (st == null) { skeletonItems(2); return@ScreenScaffold }
        item {
            SectionCard("Feedback", Icons.Rounded.Vibration) {
                ToggleRows(SOUNDS, st.first) { t, on -> act { c.tweaks.setToggle(t, on) } }
            }
        }
        item {
            SectionCard("Tones", Icons.Rounded.MusicNote) {
                Tone.entries.forEach { tone ->
                    ListItem(
                        leadingContent = { Icon(tone.icon, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(tone.label) },
                        supportingContent = { Text(tones[tone] ?: "…") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            picking = tone.ordinal
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, tone.type)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, tone.label)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, RingtoneManager.getActualDefaultRingtoneUri(ctx, tone.type))
                            runCatching { picker.launch(intent) }.onFailure { picking = -1; runner.message("No sound picker on this phone") }
                        },
                    )
                }
            }
        }
    }
}
