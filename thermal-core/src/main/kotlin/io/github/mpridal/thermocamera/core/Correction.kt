package io.github.mpridal.thermocamera.core

import kotlin.math.pow

/**
 * User corrections applied to raw camera values (zadání F2, kap. 4.2 step 1):
 * emissivity + reflected temperature, then linear `T = gain · T + offset`.
 */
data class Correction(
    val emissivity: Float = 0.95f,
    val reflectedCelsius: Float = 20f,
    val offset: Float = 0f,
    val gain: Float = 1f,
) {
    init {
        require(emissivity in 0.10f..1.00f) { "Emissivity out of range: $emissivity" }
    }

    fun apply(measuredCelsius: Float): Float = gain * emissivityCorrected(measuredCelsius) + offset

    /** Applies the correction to every value of [src] into [dst] (may be the same array). */
    fun applyTo(src: FloatArray, dst: FloatArray) {
        require(src.size == dst.size)
        for (i in src.indices) dst[i] = apply(src[i])
    }

    /**
     * `T_obj = ((T_mer⁴ − (1 − ε) · T_odr⁴) / ε)^(1/4)` in Kelvin.
     * The T⁴ law is an approximation for the 8–14 µm band, good enough for ±3 °C.
     */
    fun emissivityCorrected(measuredCelsius: Float): Float {
        if (emissivity >= 1f) return measuredCelsius
        val tm = (measuredCelsius + KELVIN).toDouble()
        val tr = (reflectedCelsius + KELVIN).toDouble()
        val e = emissivity.toDouble()
        val inner = (tm.pow(4) - (1 - e) * tr.pow(4)) / e
        if (inner <= 0.0) return -KELVIN
        return (inner.pow(0.25) - KELVIN).toFloat()
    }

    companion object {
        const val KELVIN = 273.15f

        /** Single-point calibration: offset so that [measured] reads as [actual]. */
        fun offsetFor(actual: Float, measured: Float): Float = actual - measured

        /**
         * Two-point calibration from two references, returns (gain, offset) such that
         * `gain · measured + offset = actual` holds for both points.
         */
        fun twoPoint(actual1: Float, measured1: Float, actual2: Float, measured2: Float): Pair<Float, Float> {
            val dm = measured2 - measured1
            require(kotlin.math.abs(dm) > 0.5f) { "Reference temperatures are too close" }
            val gain = (actual2 - actual1) / dm
            return gain to (actual1 - gain * measured1)
        }
    }
}
