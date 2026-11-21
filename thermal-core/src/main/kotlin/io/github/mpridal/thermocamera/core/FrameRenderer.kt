package io.github.mpridal.thermocamera.core

/**
 * Maps a (corrected) temperature grid to ARGB pixels of display size:
 * up-scaling → range → palette LUT → optional isotherm highlight.
 *
 * Buffers are reused between frames; the returned array is owned by the renderer and
 * overwritten by the next call. Not thread-safe.
 */
class FrameRenderer(
    val outWidth: Int = 320,
    val outHeight: Int = 320,
) {
    private val scaled = FloatArray(outWidth * outHeight)
    private var resampler: Interpolation.Resampler? = null
    private val pixels = IntArray(outWidth * outHeight)

    fun render(
        values: FloatArray,
        srcW: Int,
        srcH: Int,
        range: TempRange,
        palette: Palette,
        mode: Interpolation.Mode = Interpolation.Mode.BICUBIC,
        isothermBelow: Float? = null,
    ): IntArray {
        val rs = resampler?.takeIf { it.srcW == srcW && it.srcH == srcH }
            ?: Interpolation.Resampler(srcW, srcH, outWidth, outHeight).also { resampler = it }
        rs.resize(values, scaled, mode)
        val lut = palette.lut
        val span = range.max - range.min
        val last = Palette.LUT_SIZE - 1
        for (i in pixels.indices) {
            val t = scaled[i]
            pixels[i] = if (isothermBelow != null && t < isothermBelow) {
                Palette.ISOTHERM_ARGB
            } else {
                var idx = ((t - range.min) / span * last).toInt()
                if (idx < 0) idx = 0 else if (idx > last) idx = last
                lut[idx]
            }
        }
        return pixels
    }
}
