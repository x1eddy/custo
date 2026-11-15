package app.custo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.custo.R

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, opsz: Float, width: Float = 100f) = Font(
    R.font.bricolage,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opsz),
        FontVariation.width(width),
    ),
)

/** Display face: Bricolage Grotesque (OFL), tight and slightly condensed at large sizes. */
val Display = FontFamily(
    bricolage(800, 96f, 88f),
    bricolage(700, 48f, 92f),
    bricolage(600, 24f),
    bricolage(500, 14f),
)

private val Base = Typography()
private val CustoType = Base.copy(
    displayLarge = Base.displayLarge.copy(fontFamily = Display, fontWeight = FontWeight(800), letterSpacing = (-1.5).sp),
    displayMedium = Base.displayMedium.copy(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 52.sp, lineHeight = 52.sp, letterSpacing = (-1.2).sp),
    displaySmall = Base.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-0.8).sp),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight(700), letterSpacing = (-0.4).sp),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight(700), letterSpacing = (-0.3).sp),
    titleLarge = Base.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 22.sp, letterSpacing = (-0.2).sp),
    titleMedium = Base.titleMedium.copy(fontFamily = Display, fontWeight = FontWeight(600), fontSize = 17.sp),
    labelLarge = Base.labelLarge.copy(fontFamily = Display, fontWeight = FontWeight(600), fontSize = 15.sp),
)

/**
 * Custo's theme. [appearance] (Settings → App appearance) picks light/dark, AMOLED black, the accent
 * source, corner roundness and reduced motion.
 */
@Composable
fun CustoTheme(
    appearance: AppAppearance = AppAppearance(),
    dark: Boolean = appearance.isDark(isSystemInDarkTheme()),
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val seed = appearance.seed
    // minSdk 31: Material You is always available, so Custo previews whatever palette the user applies.
    var scheme = remember(seed, dark, ctx) {
        if (seed != null) schemeFromSeed(seed, dark) else if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    }
    if (dark && appearance.amoled) {
        scheme = scheme.amoled()
    } else if (dark && seed == null) {
        // Deeper, inkier surfaces than stock Material so color tiles and previews pop.
        scheme = scheme.copy(
            background = lerp(scheme.background, Color.Black, 0.55f),
            surface = lerp(scheme.surface, Color.Black, 0.55f),
            surfaceContainerLowest = lerp(scheme.surfaceContainerLowest, Color.Black, 0.5f),
            surfaceContainerLow = lerp(scheme.surfaceContainerLow, Color.Black, 0.35f),
            surfaceContainer = lerp(scheme.surfaceContainer, Color.Black, 0.3f),
        )
    }
    val shapes = remember(appearance.corners) { shapesFor(appearance.corners) }
    CompositionLocalProvider(LocalReduceMotion provides appearance.reduceMotion) {
        MaterialTheme(colorScheme = scheme, typography = CustoType, shapes = shapes, content = content)
    }
}
