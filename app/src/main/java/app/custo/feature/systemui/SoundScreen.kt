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
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import app.custo.ui.components.Hint
import app.custo.ui.components.ScreenScaffold
import app.custo.ui.components.SectionCard
import app.custo.ui.components.rememberActionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SOUNDS = listOf(
    Toggle(Namespace.SYSTEM, "haptic_feedback_enabled", "Touch vibration", default = true),
    Toggle(Namespace.SYSTEM, "sound_effects_enabled", "Touch sounds", default = true),
    Toggle(Namespace.SYSTEM, "lockscreen_sounds_enabled", "Screen lock sound", default = true),
    Toggle(Namespace.SECURE, "charging_sounds_enabled", "Charging sound", default = true),
    Toggle(Namespace.SECURE, "charging_vibration_enabled", "Charging vibration", default = true),
)

/** Ringtone types with the `system` setting RingtoneManager stores them in. */
private enum class Tone(val type: Int, val key: String, val label: String) {
    RINGTONE(RingtoneManager.TYPE_RINGTONE, "ringtone", "Ringtone"),
    NOTIFICATION(RingtoneManager.TYPE_NOTIFICATION, "notification_sound", "Notification sound"),
    ALARM(RingtoneManager.TYPE_ALARM, "alarm_alert", "Alarm sound"),
}

private data class SoundState(val toggles: Map<String, ToggleState>, val tones: Map<Tone, String>)

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
    var reload by remember { mutableIntStateOf(0) }
    var picking by rememberSaveable { mutableIntStateOf(-1) }
    val st = rememberSysLoad(ready, reload, runner) {
        val tones = withContext(Dispatchers.IO) { Tone.entries.associateWith { toneTitle(ctx, it.type) } }
        SoundState(Probes.load(ctx, c.tweaks, SOUNDS), tones)
    }

    fun act(msg: String? = null, block: suspend () -> Unit) = runner.run(msg) { block(); reload++ }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val tone = Tone.entries.getOrNull(picking)
        picking = -1
        if (tone == null || res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri: Uri? = res.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
        act("${tone.label} set") {
            c.tweaks.rememberOriginal(Namespace.SYSTEM, tone.key)
            if (!Settings.System.canWrite(ctx)) c.tweaks.grantWriteSettings(ctx.packageName)
            withContext(Dispatchers.IO) { RingtoneManager.setActualDefaultRingtoneUri(ctx, tone.type, uri) }
        }
    }

    ScreenScaffold(title = "Sound & haptics", onBack = { nav.pop() }) {
        if (!ready) { lockedItem(nav); return@ScreenScaffold }
        if (st == null) { item { Hint("Reading your system…") }; return@ScreenScaffold }
        item {
            SectionCard("Sounds & vibration", Icons.Rounded.Vibration) {
                ToggleRows(SOUNDS, st.toggles) { t, on -> act { c.tweaks.setToggle(t, on) } }
            }
        }
        item {
            SectionCard("Sounds", Icons.Rounded.MusicNote) {
                Tone.entries.forEach { tone ->
                    ListItem(
                        headlineContent = { Text(tone.label) },
                        supportingContent = { Text(st.tones[tone] ?: "") },
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
