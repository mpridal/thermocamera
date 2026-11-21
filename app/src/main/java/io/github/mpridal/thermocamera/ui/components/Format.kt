package io.github.mpridal.thermocamera.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.core.RangePreset
import java.text.NumberFormat
import java.util.Locale

/** Locale-aware number formatting (decimal comma in cs/es/it/fr/de, point in en). */
class NumberFormatter(locale: Locale) {
    private val one = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1; maximumFractionDigits = 1; isGroupingUsed = false
    }
    private val two = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2; isGroupingUsed = false
    }
    private val zero = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 0; isGroupingUsed = false }

    fun one(v: Float): String = one.format(v.toDouble())
    fun two(v: Float): String = two.format(v.toDouble())
    fun zero(v: Float): String = zero.format(v.toDouble())

    /** Short in-image label, e.g. "13,4°". */
    fun deg(v: Float): String = one(v) + "°"
}

@Composable
fun rememberNumberFormatter(): NumberFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { NumberFormatter(locale) }
}

/** "19,6 °C" with a non-breaking space. */
@Composable
fun celsius(f: NumberFormatter, v: Float): String = stringResource(R.string.temp_celsius, f.one(v))

@Composable
fun rangePresetName(p: RangePreset): String = stringResource(
    when (p) {
        RangePreset.AUTO -> R.string.range_auto
        RangePreset.INTERIOR -> R.string.range_interior
        RangePreset.FACADE -> R.string.range_facade
        RangePreset.HEATING -> R.string.range_heating
        RangePreset.FULL -> R.string.range_full
        RangePreset.CUSTOM -> R.string.range_custom
    },
)

@Composable
fun paletteName(p: Palette): String = stringResource(
    when (p) {
        Palette.IRON -> R.string.palette_iron
        Palette.RAINBOW -> R.string.palette_rainbow
        Palette.GRAY -> R.string.palette_gray
        Palette.ARCTIC -> R.string.palette_arctic
    },
)
