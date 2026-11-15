package app.custo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.custo.feature.systemui.Tonal

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class AccentSource(val label: String) { MATERIAL_YOU("Material You"), CUSTO("Custo"), CUSTOM("Custom") }

enum class CornerStyle(val label: String) { ROUNDED("Rounded"), SOFT("Soft"), SHARP("Sharp") }

/** How Custo itself looks. Stored in the app's DataStore; every field has a default. */
data class AppAppearance(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    /** Pure black surfaces in dark mode (saves power on OLED screens). */
    val amoled: Boolean = false,
    val accent: AccentSource = AccentSource.MATERIAL_YOU,
    val customAccent: Int = CUSTO_ACCENT,
    val corners: CornerStyle = CornerStyle.ROUNDED,
    /** Skips decorative motion (Home's entrance and press bounces, the logo animation, sliding screens). */
    val reduceMotion: Boolean = false,
) {
    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    /** The seed color for [AccentSource.CUSTO] / [AccentSource.CUSTOM], or null for Material You. */
    val seed: Int? get() = when (accent) {
        AccentSource.MATERIAL_YOU -> null
        AccentSource.CUSTO -> CUSTO_ACCENT
        AccentSource.CUSTOM -> customAccent
    }

    companion object {
        /** Custo's own violet. */
        const val CUSTO_ACCENT = 0xFF6D4AFF.toInt()

        val MODE = stringPreferencesKey("app_theme_mode")
        val AMOLED = booleanPreferencesKey("app_amoled")
        val ACCENT = stringPreferencesKey("app_accent")
        val CUSTOM_ACCENT = intPreferencesKey("app_custom_accent")
        val CORNERS = stringPreferencesKey("app_corners")
        val REDUCE_MOTION = booleanPreferencesKey("app_reduce_motion")

        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

        fun read(p: Preferences): AppAppearance {
            val d = AppAppearance()
            return AppAppearance(
                mode = enumOr(p[MODE], d.mode),
                amoled = p[AMOLED] ?: d.amoled,
                accent = enumOr(p[ACCENT], d.accent),
                customAccent = p[CUSTOM_ACCENT] ?: d.customAccent,
                corners = enumOr(p[CORNERS], d.corners),
                reduceMotion = p[REDUCE_MOTION] ?: d.reduceMotion,
            )
        }
    }
}

/** True when the user asked for less motion (see [AppAppearance.reduceMotion]). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Material shapes for each corner style; [CornerStyle.ROUNDED] is Custo's original set. */
fun shapesFor(style: CornerStyle): Shapes = when (style) {
    CornerStyle.ROUNDED -> Shapes(
        extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(32.dp),
    )
    CornerStyle.SOFT -> Shapes(
        extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(14.dp), extraLarge = RoundedCornerShape(20.dp),
    )
    CornerStyle.SHARP -> Shapes(
        extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(2.dp), medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp), extraLarge = RoundedCornerShape(8.dp),
    )
}

/** A full Material color scheme grown from one [seed] color with Custo's tonal ramps. */
fun schemeFromSeed(seed: Int, dark: Boolean): ColorScheme {
    val (h, s, _) = Tonal.toHsl(seed)
    fun ramp(hue: Float, sat: Float) = Tonal.ramp(Tonal.fromHsl(((hue % 360f) + 360f) % 360f, sat.coerceIn(0f, 1f), 0.5f))
    val pr = ramp(h, s.coerceAtLeast(0.45f))
    val se = ramp(h, s * 0.35f)
    val te = ramp(h + 60f, s.coerceAtLeast(0.4f) * 0.8f)
    val ne = ramp(h, 0.06f)
    val nv = ramp(h, 0.14f)
    fun c(m: Map<Int, Int>, shade: Int) = Color(m.getValue(shade))
    return if (dark) darkColorScheme(
        primary = c(pr, 200), onPrimary = c(pr, 800), primaryContainer = c(pr, 700), onPrimaryContainer = c(pr, 100),
        secondary = c(se, 200), onSecondary = c(se, 800), secondaryContainer = c(se, 700), onSecondaryContainer = c(se, 100),
        tertiary = c(te, 200), onTertiary = c(te, 800), tertiaryContainer = c(te, 700), onTertiaryContainer = c(te, 100),
        background = lerp(c(ne, 1000), c(ne, 900), 0.6f), onBackground = c(ne, 100),
        surface = lerp(c(ne, 1000), c(ne, 900), 0.6f), onSurface = c(ne, 100),
        surfaceVariant = c(nv, 800), onSurfaceVariant = c(nv, 200), outline = c(nv, 400), outlineVariant = c(nv, 700),
        surfaceContainerLowest = c(ne, 1000), surfaceContainerLow = c(ne, 900), surfaceContainer = lerp(c(ne, 900), c(ne, 800), 0.35f),
        surfaceContainerHigh = lerp(c(ne, 900), c(ne, 800), 0.65f), surfaceContainerHighest = c(ne, 800),
        inverseSurface = c(ne, 100), inverseOnSurface = c(ne, 800), inversePrimary = c(pr, 600),
    ) else lightColorScheme(
        primary = c(pr, 600), onPrimary = c(pr, 0), primaryContainer = c(pr, 100), onPrimaryContainer = c(pr, 900),
        secondary = c(se, 600), onSecondary = c(se, 0), secondaryContainer = c(se, 100), onSecondaryContainer = c(se, 900),
        tertiary = c(te, 600), onTertiary = c(te, 0), tertiaryContainer = c(te, 100), onTertiaryContainer = c(te, 900),
        background = c(ne, 10), onBackground = c(ne, 900), surface = c(ne, 10), onSurface = c(ne, 900),
        surfaceVariant = c(nv, 100), onSurfaceVariant = c(nv, 700), outline = c(nv, 500), outlineVariant = c(nv, 200),
        surfaceContainerLowest = c(ne, 0), surfaceContainerLow = c(ne, 50), surfaceContainer = c(ne, 50),
        surfaceContainerHigh = c(ne, 100), surfaceContainerHighest = c(ne, 200),
        inverseSurface = c(ne, 800), inverseOnSurface = c(ne, 50), inversePrimary = c(pr, 200),
    )
}

/** Pure-black surfaces for AMOLED screens, keeping the scheme's accents. */
fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black, surface = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF070707), surfaceContainer = Color(0xFF0E0E0F),
    surfaceContainerHigh = Color(0xFF161618), surfaceContainerHighest = Color(0xFF1F1F22),
    surfaceVariant = Color(0xFF1A1A1D), inverseOnSurface = Color.Black,
)
