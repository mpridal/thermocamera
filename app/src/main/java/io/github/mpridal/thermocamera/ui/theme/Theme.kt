package io.github.mpridal.thermocamera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.mpridal.thermocamera.data.ThemeMode

/** Colour tokens from zadání kap. 7.2. */
@Immutable
data class ThermoColors(
    val isDark: Boolean,
    val root: Color,
    val veil: Color,
    val glass: Color,
    val glassHi: Color,
    val sheet: Color,
    val glassBorder: Color,
    /** Solid fallback for glass when blur is off ("Reduce transparency"). */
    val glassSolid: Color,
    /** Opened menus (dropdowns): glass with a higher opacity so that the content below does not show through. */
    val menu: Color,
    val shadow: Color,
    val text: Color,
    val textMuted: Color,
    val link: Color,
    val segOff: Color,
    val imgLabel: Color,
    val imgLabelText: Color,
    val coldText: Color,
    val hotText: Color,
    val okChipFill: Color,
    val okChipBorder: Color,
    val errChipFill: Color,
    val errChipBorder: Color,
    val warnFill: Color,
    val warnBorder: Color,
    val warnTitle: Color,
    val warnText: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
) {
    // Shared by both themes.
    val accent = Color(0xFFFF8A3D)
    val onAccent = Color(0xFF1A0E05)
    val markMin = Color(0xFF5AB0FF)
    val markMax = Color(0xFFFF5B4A)
    val connected = Color(0xFF3DD68C)
    val error = Color(0xFFFF6B6B)
    val warning = Color(0xFFFFC14D)
}

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r, g, b, (a * 255).toInt())

val DarkThermoColors = ThermoColors(
    isDark = true,
    root = Color(0xFF0A0C10),
    veil = rgba(8, 10, 14, .40f),
    glass = rgba(32, 36, 46, .46f),
    glassHi = rgba(255, 255, 255, .10f),
    sheet = rgba(22, 25, 33, .66f),
    glassBorder = rgba(255, 255, 255, .14f),
    glassSolid = rgba(32, 36, 46, .85f),
    menu = rgba(28, 31, 40, .97f),
    shadow = rgba(0, 0, 0, .32f),
    text = Color(0xFFF5F7FA),
    textMuted = Color(0xFFB9C0CC),
    link = Color(0xFFFFB07A),
    segOff = Color(0xFFD3D8E1),
    imgLabel = rgba(10, 12, 16, .46f),
    imgLabelText = Color.White,
    coldText = Color(0xFF8CC8FF),
    hotText = Color(0xFFFFB07A),
    okChipFill = rgba(61, 214, 140, .16f),
    okChipBorder = rgba(61, 214, 140, .45f),
    errChipFill = rgba(255, 107, 107, .16f),
    errChipBorder = rgba(255, 107, 107, .45f),
    warnFill = rgba(255, 193, 77, .16f),
    warnBorder = rgba(255, 193, 77, .40f),
    warnTitle = Color(0xFFFFD27A),
    warnText = Color(0xFFEFE3CB),
    gradientStart = Color(0xFF3A1E6B),
    gradientEnd = Color(0xFF8A3A12),
)

val LightThermoColors = ThermoColors(
    isDark = false,
    root = Color(0xFFF2F3F6),
    veil = rgba(245, 246, 250, .35f),
    glass = rgba(255, 255, 255, .50f),
    glassHi = rgba(255, 255, 255, .62f),
    sheet = rgba(250, 251, 253, .70f),
    glassBorder = rgba(255, 255, 255, .78f),
    glassSolid = rgba(255, 255, 255, .90f),
    menu = rgba(250, 251, 253, .98f),
    shadow = rgba(60, 50, 80, .14f),
    text = Color(0xFF14161C),
    textMuted = Color(0xFF454C5A),
    link = Color(0xFFA9480C),
    segOff = Color(0xFF2E3440),
    imgLabel = rgba(255, 255, 255, .62f),
    imgLabelText = Color(0xFF14161C),
    coldText = Color(0xFF15539E),
    hotText = Color(0xFF9A300C),
    okChipFill = rgba(61, 214, 140, .26f),
    okChipBorder = rgba(24, 140, 84, .42f),
    errChipFill = rgba(255, 107, 107, .24f),
    errChipBorder = rgba(190, 40, 40, .42f),
    warnFill = rgba(255, 183, 52, .28f),
    warnBorder = rgba(190, 120, 0, .42f),
    warnTitle = Color(0xFF5E3A00),
    warnText = Color(0xFF4F3D1C),
    gradientStart = Color(0xFFD9C8F5),
    gradientEnd = Color(0xFFFFD2B0),
)

val LocalThermoColors = staticCompositionLocalOf { DarkThermoColors }

/** True when glass panels must use the solid fallback instead of backdrop blur. */
val LocalReduceTransparency = staticCompositionLocalOf { false }

@Composable
fun ThermoTheme(
    themeMode: ThemeMode,
    reduceTransparency: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkThermoColors else LightThermoColors
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.root, surface = c.root, onSurface = c.text, onBackground = c.text)
    } else {
        lightColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.root, surface = c.root, onSurface = c.text, onBackground = c.text)
    }
    CompositionLocalProvider(
        LocalThermoColors provides c,
        LocalReduceTransparency provides reduceTransparency,
    ) {
        MaterialTheme(colorScheme = scheme, typography = ThermoTypography, content = content)
    }
}

object ThermoTheme {
    val colors: ThermoColors
        @Composable get() = LocalThermoColors.current
}
