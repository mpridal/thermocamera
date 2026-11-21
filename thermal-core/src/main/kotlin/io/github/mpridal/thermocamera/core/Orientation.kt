package io.github.mpridal.thermocamera.core

/**
 * Orientation of the camera module (zadání F1): the USB-C plug is reversible and the module can
 * face forward or backward, so its images may need a clockwise rotation and a horizontal mirror.
 * Applies equally to the thermal matrix and the module camera image (they are fixed together).
 * Rotation first, then mirror.
 */
data class Orientation(val rotation: Int = 0, val mirror: Boolean = false) {
    init {
        require(rotation in ROTATIONS) { "Rotation must be one of $ROTATIONS, got $rotation" }
    }

    val isIdentity: Boolean get() = rotation == 0 && !mirror

    /** Output width for an input of [w] × [h]. */
    fun outWidth(w: Int, h: Int) = if (rotation == 90 || rotation == 270) h else w
    fun outHeight(w: Int, h: Int) = if (rotation == 90 || rotation == 270) w else h

    fun rotatedBy90(): Orientation = copy(rotation = (rotation + 90) % 360)
    fun toggledMirror(): Orientation = copy(mirror = !mirror)

    /** Source index for the output pixel ([x], [y]) of an output of size [ow] × [oh] (input [w] × [h]). */
    private inline fun srcIndex(x: Int, y: Int, ow: Int, w: Int, h: Int): Int {
        val mx = if (mirror) ow - 1 - x else x
        val sx: Int
        val sy: Int
        when (rotation) {
            90 -> { sx = y; sy = h - 1 - mx }
            180 -> { sx = w - 1 - mx; sy = h - 1 - y }
            270 -> { sx = w - 1 - y; sy = mx }
            else -> { sx = mx; sy = y }
        }
        return sy * w + sx
    }

    fun apply(src: FloatArray, w: Int, h: Int, dst: FloatArray) {
        require(src.size == w * h && dst.size == w * h)
        if (isIdentity) { src.copyInto(dst); return }
        val ow = outWidth(w, h); val oh = outHeight(w, h)
        for (y in 0 until oh) for (x in 0 until ow) dst[y * ow + x] = src[srcIndex(x, y, ow, w, h)]
    }

    fun apply(src: IntArray, w: Int, h: Int, dst: IntArray) {
        require(src.size >= w * h && dst.size >= w * h)
        if (isIdentity) { src.copyInto(dst, 0, 0, w * h); return }
        val ow = outWidth(w, h); val oh = outHeight(w, h)
        for (y in 0 until oh) for (x in 0 until ow) dst[y * ow + x] = src[srcIndex(x, y, ow, w, h)]
    }

    companion object {
        val ROTATIONS = listOf(0, 90, 180, 270)
    }
}
