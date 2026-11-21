package io.github.mpridal.thermocamera.core

/**
 * Thermal-bridge helpers (zadání F3).
 * fRsi = (θsi − θe) / (θi − θe)
 */
object ThermalBridge {
    const val DEFAULT_LIMIT = 0.75f

    /** Below this inside–outside difference the result is unreliable and the UI warns. */
    const val MIN_RELIABLE_DIFFERENCE = 10f

    fun fRsi(surface: Float, inside: Float, outside: Float): Float? {
        val d = inside - outside
        if (d <= 0f) return null
        return (surface - outside) / d
    }

    /** Surface temperature below which fRsi < [limit]: θe + limit · (θi − θe). */
    fun isothermThreshold(inside: Float, outside: Float, limit: Float = DEFAULT_LIMIT): Float =
        outside + limit * (inside - outside)

    fun isReliable(inside: Float, outside: Float): Boolean = inside - outside >= MIN_RELIABLE_DIFFERENCE
}
