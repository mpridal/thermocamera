package io.github.mpridal.thermocamera.core

/**
 * Extracts the 32 × 32 thermal matrix that the Hti HT-102 hides in its ordinary YUY2 video frame
 * (see docs/ht102-protocol.md).
 *
 * The data sits in the least significant bit of the chroma bytes (odd byte offsets, U/V) at the
 * start of the frame. Each thermal pixel occupies 30 bytes (15 YUY2 pixels):
 * - LSB of byte 1 = sign (1 = negative),
 * - LSBs of bytes 3, 5, …, 29 = 14-bit magnitude, most significant bit first,
 * - value in tenths of °C.
 * Pixels are stored row by row (row-major), so the matrix uses the first
 * 32 · 32 · 30 = 30 720 bytes of the frame (the first 24 lines at 640 × 480).
 */
object Ht102Decoder {
    const val WIDTH = 32
    const val HEIGHT = 32
    const val BYTES_PER_PIXEL = 30
    const val REQUIRED_BYTES = WIDTH * HEIGHT * BYTES_PER_PIXEL

    /** Frame size the camera has to stream for the thermal data to be present. */
    const val FRAME_WIDTH = 640
    const val FRAME_HEIGHT = 480

    /**
     * Decodes into [out] (°C, row-major, size 1024) from a YUY2 frame starting at [offset] of [yuy2].
     */
    fun decode(yuy2: ByteArray, out: FloatArray, offset: Int = 0) {
        require(out.size == WIDTH * HEIGHT) { "out must hold ${WIDTH * HEIGHT} values" }
        require(yuy2.size - offset >= REQUIRED_BYTES) { "Frame too small: ${yuy2.size - offset} B" }
        var p = offset
        for (i in 0 until WIDTH * HEIGHT) {
            out[i] = decodePixel(yuy2, p) / 10f
            p += BYTES_PER_PIXEL
        }
    }

    /** Raw value (tenths of °C) of one thermal pixel encoded at byte offset [p]. */
    fun decodePixel(b: ByteArray, p: Int): Int {
        var v = 0
        var k = 3
        while (k < BYTES_PER_PIXEL) {
            v = (v shl 1) or (b[p + k].toInt() and 1)
            k += 2
        }
        return if (b[p + 1].toInt() and 1 != 0) -v else v
    }

    fun decode(yuy2: ByteArray, timestampNanos: Long = 0L): ThermalFrame {
        val out = FloatArray(WIDTH * HEIGHT)
        decode(yuy2, out)
        return ThermalFrame(WIDTH, HEIGHT, out, timestampNanos)
    }

    /**
     * Writes [celsius] into the chroma LSBs of [yuy2] in the same format – used by tests and by the
     * simulator to produce frames that look exactly like the camera's.
     */
    fun encode(celsius: FloatArray, yuy2: ByteArray, offset: Int = 0) {
        require(celsius.size == WIDTH * HEIGHT && yuy2.size - offset >= REQUIRED_BYTES)
        var p = offset
        for (t in celsius) {
            val raw = Math.round(t * 10f)
            val mag = kotlin.math.abs(raw).coerceAtMost(0x3FFF)
            yuy2[p + 1] = ((yuy2[p + 1].toInt() and 0xFE) or (if (raw < 0) 1 else 0)).toByte()
            var bit = 13
            var k = 3
            while (k < BYTES_PER_PIXEL) {
                yuy2[p + k] = ((yuy2[p + k].toInt() and 0xFE) or ((mag shr bit) and 1)).toByte()
                bit--; k += 2
            }
            p += BYTES_PER_PIXEL
        }
    }
}
