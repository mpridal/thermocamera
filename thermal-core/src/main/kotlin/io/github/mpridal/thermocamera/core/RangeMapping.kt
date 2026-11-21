package io.github.mpridal.thermocamera.core

/** Colour-mapping range in °C. Determines colours only, never the measured values. */
data class TempRange(val min: Float, val max: Float) {
    init {
        require(max > min) { "Range max ($max) must be above min ($min)" }
    }
}

/** Range presets (zadání F2). [range] is null for AUTO and CUSTOM. */
enum class RangePreset(val range: TempRange?) {
    AUTO(null),
    INTERIOR(TempRange(12f, 24f)),
    FACADE(TempRange(-15f, 15f)),
    HEATING(TempRange(20f, 80f)),
    FULL(TempRange(-20f, 300f)),
    CUSTOM(null),
}

/**
 * Automatic range = 2nd and 98th percentile, smoothed with an EMA so that the image does not flicker.
 * Not thread-safe; one instance per stream.
 */
class AutoRange(
    private val alpha: Float = 0.3f,
    private val lowPercentile: Float = 0.02f,
    private val highPercentile: Float = 0.98f,
    private val minSpan: Float = 1f,
) {
    private var low = Float.NaN
    private var high = Float.NaN
    private var scratch = FloatArray(0)

    fun update(values: FloatArray): TempRange {
        if (scratch.size != values.size) scratch = FloatArray(values.size)
        values.copyInto(scratch)
        scratch.sort()
        val lo = scratch[((scratch.size - 1) * lowPercentile).toInt()]
        val hi = scratch[((scratch.size - 1) * highPercentile).toInt()]
        if (low.isNaN()) {
            low = lo; high = hi
        } else {
            low += alpha * (lo - low)
            high += alpha * (hi - high)
        }
        return current()
    }

    fun current(): TempRange {
        if (low.isNaN()) return TempRange(0f, minSpan)
        val mid = (low + high) / 2
        return if (high - low < minSpan) TempRange(mid - minSpan / 2, mid + minSpan / 2) else TempRange(low, high)
    }

    fun reset() {
        low = Float.NaN; high = Float.NaN
    }
}

/** Exponential moving average over whole frames, for display only (zadání kap. 4.2 step 3). */
class FrameSmoother(var alpha: Float = 0.5f) {
    private var state: FloatArray? = null

    fun smooth(values: FloatArray): FloatArray {
        val s = state
        if (s == null || s.size != values.size) {
            state = values.copyOf()
            return state!!
        }
        for (i in values.indices) s[i] += alpha * (values[i] - s[i])
        return s
    }

    fun reset() { state = null }
}
