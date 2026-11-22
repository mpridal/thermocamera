package io.github.mpridal.thermocamera.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.mpridal.thermocamera.R

/**
 * Typography (zadání kap. 7.3): Inter for the UI, JetBrains Mono for numbers. Both are variable fonts
 * from Google Fonts bundled in res/font (SIL OFL 1.1, licences in /licenses), one file each –
 * the weights below are instances of the `wght` axis.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(res: Int, vararg weights: FontWeight): FontFamily = FontFamily(
    weights.map { w -> Font(res, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))) },
)

val UiFont: FontFamily = variableFamily(
    R.font.inter, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
)
val NumberFont: FontFamily = variableFamily(
    R.font.jetbrains_mono, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
)

private val Default = Typography()

/** All Material styles use Inter, so plain `Text` without an explicit style gets it too. */
val ThermoTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = UiFont),
    displayMedium = Default.displayMedium.copy(fontFamily = UiFont),
    displaySmall = Default.displaySmall.copy(fontFamily = UiFont),
    headlineLarge = Default.headlineLarge.copy(fontFamily = UiFont),
    headlineMedium = Default.headlineMedium.copy(fontFamily = UiFont),
    headlineSmall = Default.headlineSmall.copy(fontFamily = UiFont),
    titleLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = Default.titleMedium.copy(fontFamily = UiFont),
    titleSmall = Default.titleSmall.copy(fontFamily = UiFont),
    bodyLarge = Default.bodyLarge.copy(fontFamily = UiFont),
    bodyMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.05.em),
    labelSmall = Default.labelSmall.copy(fontFamily = UiFont),
)

/** Numbers: monospaced so values do not jump when they change. */
val NumberStyle = TextStyle(fontFamily = NumberFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
val NumberStyleSmall = TextStyle(fontFamily = NumberFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
