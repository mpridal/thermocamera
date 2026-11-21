package io.github.mpridal.thermocamera.core

/**
 * One thermal frame from a camera source, in °C after the camera's own conversion
 * and before any user corrections (emissivity, offset/gain).
 *
 * Row-major: index = y * width + x, (0, 0) is the top-left corner.
 */
class ThermalFrame(
    val width: Int = 32,
    val height: Int = 32,
    val celsius: FloatArray,
    val timestampNanos: Long,
) {
    init {
        require(width > 0 && height > 0) { "Invalid size $width x $height" }
        require(celsius.size == width * height) { "Expected ${width * height} values, got ${celsius.size}" }
    }

    operator fun get(x: Int, y: Int): Float = celsius[y * width + x]
}
