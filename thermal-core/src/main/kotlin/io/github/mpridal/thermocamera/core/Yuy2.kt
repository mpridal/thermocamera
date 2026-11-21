package io.github.mpridal.thermocamera.core

/** YUY2 (YUYV 4:2:2, byte order Y0 U Y1 V) to ARGB, BT.601 limited range, integer arithmetic. */
object Yuy2 {

    fun toArgb(src: ByteArray, width: Int, height: Int, dst: IntArray) {
        require(width % 2 == 0) { "YUY2 width must be even" }
        require(src.size >= width * height * 2 && dst.size >= width * height)
        var s = 0
        var d = 0
        val pairs = width * height / 2
        for (i in 0 until pairs) {
            val y0 = (src[s].toInt() and 0xFF) - 16
            val u = (src[s + 1].toInt() and 0xFF) - 128
            val y1 = (src[s + 2].toInt() and 0xFF) - 16
            val v = (src[s + 3].toInt() and 0xFF) - 128
            val rv = 409 * v
            val guv = -100 * u - 208 * v
            val bu = 516 * u
            val c0 = 298 * y0 + 128
            val c1 = 298 * y1 + 128
            dst[d] = pack(c0 + rv, c0 + guv, c0 + bu)
            dst[d + 1] = pack(c1 + rv, c1 + guv, c1 + bu)
            s += 4
            d += 2
        }
    }

    private fun pack(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (clamp(r shr 8) shl 16) or (clamp(g shr 8) shl 8) or clamp(b shr 8)

    private fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v
}
