package io.github.mpridal.thermocamera.core

import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthetic room-corner scene for the simulator (zadání kap. 3.1):
 * cold corner at the ceiling (top-left), cold window lintel, warm radiator under the window,
 * mild noise ±0.1 °C. Pure function of time so it can be unit-tested and replayed.
 */
class SimulatedScene(
    val width: Int = 32,
    val height: Int = 32,
    private val wallCelsius: Float = 19.6f,
    seed: Int = 42,
) {
    private val random = Random(seed)

    /** Fills [out] with the scene at time [tSeconds]. */
    fun fill(out: FloatArray, tSeconds: Double) {
        require(out.size == width * height)
        // Slow sway, as if the camera were hand-held.
        val dx = (sin(tSeconds * 0.6) * 0.8).toFloat()
        val dy = (sin(tSeconds * 0.45 + 1.0) * 0.5).toFloat()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val u = (x + dx) / (width - 1)
                val v = (y + dy) / (height - 1)
                out[y * width + x] = temperatureAt(u, v) + (random.nextFloat() - 0.5f) * 0.2f
            }
        }
    }

    /** Noise-free temperature at normalised scene coordinates (0..1). */
    fun temperatureAt(u: Float, v: Float): Float {
        var t = wallCelsius
        // Cold corner wall/ceiling, top-left.
        val dCorner = u * u + v * v
        t -= 6.2f * exp(-dCorner / 0.035f)
        // Edge along the ceiling, slightly colder.
        t -= 1.6f * exp(-(v * v) / 0.004f)
        // Window: glass slightly colder, lintel (top edge of window) noticeably cold.
        val inWindowX = u in 0.52f..0.88f
        if (inWindowX && v in 0.30f..0.62f) t = 15.8f
        if (inWindowX) t -= 3.4f * exp(-((v - 0.29f) * (v - 0.29f)) / 0.0012f)
        // Radiator under the window.
        if (u in 0.55f..0.85f && v in 0.74f..0.90f) t = 43.1f - 6f * kotlin.math.abs(u - 0.70f)
        // Warm air plume above the radiator.
        if (u in 0.55f..0.85f && v in 0.64f..0.74f) t += 4f * (v - 0.64f) / 0.10f
        return t
    }
}
