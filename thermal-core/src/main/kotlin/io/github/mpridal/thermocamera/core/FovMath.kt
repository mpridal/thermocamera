package io.github.mpridal.thermocamera.core

import kotlin.math.atan
import kotlin.math.min
import kotlin.math.tan

/** Field-of-view helpers for choosing and aligning the phone camera (zadání F11). */
object FovMath {
    /** HT-102 thermal field of view, 43° × 43°. */
    const val THERMAL_FOV_DEG = 43f

    /** Full field of view in degrees from sensor size and focal length (both in mm). */
    fun fovDeg(sensorMm: Float, focalMm: Float): Float =
        Math.toDegrees(2.0 * atan(sensorMm / (2.0 * focalMm))).toFloat()

    /**
     * Pixels of the camera image that fall inside the thermal field of view along one axis:
     * `pixels · tan(thermal/2) / tan(fov/2)`, never more than the axis has.
     */
    fun pixelsInThermal(pixels: Int, fovDeg: Float, thermalDeg: Float = THERMAL_FOV_DEG): Float {
        val r = tan(Math.toRadians(thermalDeg / 2.0)) / tan(Math.toRadians(fovDeg / 2.0))
        return min(pixels.toFloat(), (pixels * r).toFloat())
    }

    /** Effective resolution (pixels) of the camera inside the square thermal field of view. */
    fun effectivePixels(width: Int, height: Int, hfovDeg: Float, vfovDeg: Float): Float =
        pixelsInThermal(width, hfovDeg) * pixelsInThermal(height, vfovDeg)

    /**
     * Initial guess where the thermal square lies in a camera image with the given vertical FOV
     * (used before image registration finds the exact place): centred, side from the FOV ratio.
     */
    fun estimatedCrop(width: Int, height: Int, vfovDeg: Float): CropSquare =
        CropSquare(width / 2f, height / 2f, pixelsInThermal(height, vfovDeg))
}
