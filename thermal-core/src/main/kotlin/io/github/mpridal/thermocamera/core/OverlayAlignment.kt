package io.github.mpridal.thermocamera.core

import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the square thermal field of view lies in a visible image (zadání F1 – zarovnání překryvu).
 *
 * The default (all zero / scale 1) is the centred square with side = image height, which is what a
 * camera next to the sensor with the same vertical FOV would see. [dx], [dy] move the centre in
 * fractions of the image height, [scale] multiplies the side, [angleDeg] rotates clockwise.
 */
data class OverlayAlignment(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val angleDeg: Float = 0f,
) {
    init {
        require(scale > 0f) { "scale must be positive" }
    }

    fun cropSquare(imageW: Int, imageH: Int): CropSquare = CropSquare(
        cx = imageW / 2f + dx * imageH,
        cy = imageH / 2f + dy * imageH,
        side = imageH * scale,
        angleDeg = angleDeg,
    )

    companion object {
        val DEFAULT = OverlayAlignment()

        /** Inverse of [cropSquare]. */
        fun fromCrop(c: CropSquare, imageW: Int, imageH: Int) = OverlayAlignment(
            dx = (c.cx - imageW / 2f) / imageH,
            dy = (c.cy - imageH / 2f) / imageH,
            scale = c.side / imageH,
            angleDeg = c.angleDeg,
        )
    }
}

/** A square in image pixels: centre, side and clockwise rotation. */
data class CropSquare(val cx: Float, val cy: Float, val side: Float, val angleDeg: Float = 0f) {

    /** Maps normalised thermal coordinates (0..1, 0..1) to image pixels. */
    fun map(u: Float, v: Float): Pair<Float, Float> {
        val a = Math.toRadians(angleDeg.toDouble())
        val x = (u - 0.5f) * side
        val y = (v - 0.5f) * side
        return (cx + (x * cos(a) - y * sin(a)).toFloat()) to (cy + (x * sin(a) + y * cos(a)).toFloat())
    }

    /** Applies a similarity transform (scale [s], rotation [angleDeg], translation) to the square. */
    fun transformed(s: Float, rotDeg: Float, tx: Float, ty: Float): CropSquare {
        val a = Math.toRadians(rotDeg.toDouble())
        val nx = s * (cx * cos(a) - cy * sin(a)).toFloat() + tx
        val ny = s * (cx * sin(a) + cy * cos(a)).toFloat() + ty
        return CropSquare(nx, ny, side * s, angleDeg + rotDeg)
    }
}
