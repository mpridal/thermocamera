package io.github.mpridal.thermocamera.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Typography (zadání kap. 7.3). Inter and JetBrains Mono will be bundled in res/font
 * (OFL); until then the system sans-serif and monospace are used – see docs/decisions.md.
 */
val UiFont: FontFamily = FontFamily.SansSerif
val NumberFont: FontFamily = FontFamily.Monospace

val ThermoTypography = Typography(
    titleLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    bodyMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.05.em),
    labelLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

/** Numbers: monospaced so values do not jump when they change. */
val NumberStyle = TextStyle(fontFamily = NumberFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
val NumberStyleSmall = TextStyle(fontFamily = NumberFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
