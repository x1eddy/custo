package app.custo.feature.profiles

import app.custo.data.LenientList
import app.custo.data.Prefs
import app.custo.data.updateList
import app.custo.shizuku.ShellCommands.Namespace
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** One system setting a profile can control. null in a profile = "don't change". */
enum class ProfileSetting(val label: String, val ns: Namespace, val key: String, val on: String, val off: String) {
    GRAYSCALE("Grayscale", Namespace.SECURE, "accessibility_display_daltonizer_enabled", "1", "0"),
    EXTRA_DIM("Extra dim", Namespace.SECURE, "reduce_bright_colors_activated", "1", "0"),
    HEADS_UP("Pop-up notifications", Namespace.GLOBAL, "heads_up_notifications_enabled", "1", "0"),
    AUTO_ROTATE("Auto-rotate", Namespace.SYSTEM, "accelerometer_rotation", "1", "0"),
    TOUCH_SOUNDS("Touch sounds", Namespace.SYSTEM, "sound_effects_enabled", "1", "0"),
}

@Serializable
data class AppProfile(
    val id: String,
    val name: String,
    val packages: List<String> = emptyList(),
    /** ProfileSetting.name -> true/false. Missing = leave alone. */
    val toggles: Map<String, Boolean> = emptyMap(),
    /** Animation scale for all three scales, or null to leave alone. */
    val animationScale: Float? = null,
    /** Screen timeout in seconds, or null. */
    val screenTimeoutSec: Int? = null,
    val enabled: Boolean = true,
)

/** A concrete (namespace, key) -> value change set. Pure, so it can be unit tested. */
data class SettingChange(val ns: Namespace, val key: String, val value: String)

object ProfileLogic {
    private val ANIM_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

    fun forPackage(pkg: String?, profiles: List<AppProfile>): AppProfile? =
        pkg?.let { p -> profiles.firstOrNull { it.enabled && p in it.packages } }

    fun changes(profile: AppProfile): List<SettingChange> = buildList {
        profile.toggles.forEach { (name, on) ->
            val s = ProfileSetting.entries.firstOrNull { it.name == name } ?: return@forEach
            add(SettingChange(s.ns, s.key, if (on) s.on else s.off))
            // Grayscale needs the daltonizer mode set to "monochromacy".
            if (s == ProfileSetting.GRAYSCALE && on) add(SettingChange(Namespace.SECURE, "accessibility_display_daltonizer", "0"))
        }
        profile.animationScale?.let { v -> ANIM_KEYS.forEach { add(SettingChange(Namespace.GLOBAL, it, v.toString())) } }
        profile.screenTimeoutSec?.let { add(SettingChange(Namespace.SYSTEM, "screen_off_timeout", (it * 1000).toString())) }
    }
}

class ProfilesRepository(private val prefs: Prefs) {
    private val key = stringPreferencesKey("app_profiles_json")

    val profiles: Flow<List<AppProfile>> = prefs.flow(key, "[]").map(::decode)

    private fun decode(s: String) = LenientList.decode(AppProfile.serializer(), s)

    suspend fun all(): List<AppProfile> = decode(prefs.get(key, "[]"))

    suspend fun save(p: AppProfile) =
        prefs.updateList(key, AppProfile.serializer(), "profiles") { list -> list.filterNot { it.id == p.id } + p }

    suspend fun delete(id: String) =
        prefs.updateList(key, AppProfile.serializer(), "profiles") { list -> list.filterNot { it.id == id } }
}
