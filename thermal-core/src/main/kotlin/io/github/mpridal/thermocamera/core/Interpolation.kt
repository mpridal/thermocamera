package io.github.mpridal.thermocamera.core

/**
 * Up-scaling of a small temperature grid to display size.
 *
 * Output pixel centres are mapped onto source pixel centres, so that a source value
 * sits exactly at the centre of its block. Bicubic uses Catmull-Rom, which passes
 * through the source values (node values are preserved).
 */
object Interpolation {

    enum class Mode { BICUBIC, NEAREST }

    fun resize(
        src: FloatArray,
        srcW: Int,
        srcH: Int,
        dst: FloatArray,
        dstW: Int,
        dstH: Int,
        mode: Mode = Mode.BICUBIC,
    ) {
        Resampler(srcW, srcH, dstW, dstH).resize(src, dst, mode)
    }

    /**
     * Resizing with precomputed, separable Catmull-Rom weights for a fixed source/target size.
     * About 8 multiply-adds per output pixel instead of 16 taps with weight evaluation, and no
     * allocations per call – meant to be kept and reused for every frame.
     */
    class Resampler(val srcW: Int, val srcH: Int, val dstW: Int, val dstH: Int) {
        private val xIdx = IntArray(dstW * 4)
        private val xWt = FloatArray(dstW * 4)
        private val yIdx = IntArray(dstH * 4)
        private val yWt = FloatArray(dstH * 4)
        private val xNearest = IntArray(dstW)
        private val yNearest = IntArray(dstH)
        private val rows = FloatArray(srcH * dstW)

        init {
            fillTables(srcW, dstW, xIdx, xWt, xNearest)
            fillTables(srcH, dstH, yIdx, yWt, yNearest)
        }

        fun resize(src: FloatArray, dst: FloatArray, mode: Mode = Mode.BICUBIC) {
            require(src.size == srcW * srcH && dst.size == dstW * dstH)
            if (mode == Mode.NEAREST) {
                for (y in 0 until dstH) {
                    val row = yNearest[y] * srcW
                    val o = y * dstW
                    for (x in 0 until dstW) dst[o + x] = src[row + xNearest[x]]
                }
                return
            }
            // Horizontal pass: srcH rows × dstW columns.
            for (sy in 0 until srcH) {
                val r = sy * srcW
                val o = sy * dstW
                for (x in 0 until dstW) {
                    val k = x * 4
                    rows[o + x] = src[r + xIdx[k]] * xWt[k] + src[r + xIdx[k + 1]] * xWt[k + 1] +
                        src[r + xIdx[k + 2]] * xWt[k + 2] + src[r + xIdx[k + 3]] * xWt[k + 3]
                }
            }
            // Vertical pass.
            for (y in 0 until dstH) {
                val k = y * 4
                val r0 = yIdx[k] * dstW; val w0 = yWt[k]
                val r1 = yIdx[k + 1] * dstW; val w1 = yWt[k + 1]
                val r2 = yIdx[k + 2] * dstW; val w2 = yWt[k + 2]
                val r3 = yIdx[k + 3] * dstW; val w3 = yWt[k + 3]
                val o = y * dstW
                for (x in 0 until dstW) {
                    dst[o + x] = rows[r0 + x] * w0 + rows[r1 + x] * w1 + rows[r2 + x] * w2 + rows[r3 + x] * w3
                }
            }
        }

        private fun fillTables(srcN: Int, dstN: Int, idx: IntArray, wt: FloatArray, nearest: IntArray) {
            val s = srcN.toFloat() / dstN
            for (d in 0 until dstN) {
                val f = (d + 0.5f) * s - 0.5f
                val i0 = kotlin.math.floor(f).toInt()
                val t = f - i0
                for (j in -1..2) {
                    idx[d * 4 + j + 1] = clamp(i0 + j, srcN)
                    wt[d * 4 + j + 1] = catmullRom(j - t)
                }
                nearest[d] = clamp(Math.round(f), srcN)
            }
        }
    }

    /** Catmull-Rom bicubic sample at a fractional source position (pixel-centre coordinates). */
    fun bicubic(src: FloatArray, w: Int, h: Int, fx: Float, fy: Float): Float {
        val x0 = kotlin.math.floor(fx).toInt()
        val y0 = kotlin.math.floor(fy).toInt()
        val tx = fx - x0
        val ty = fy - y0
        var result = 0f
        for (j in -1..2) {
            val yy = clamp(y0 + j, h)
            val wy = catmullRom(j - ty)
            var row = 0f
            for (i in -1..2) {
                val xx = clamp(x0 + i, w)
                row += src[yy * w + xx] * catmullRom(i - tx)
            }
            result += row * wy
        }
        return result
    }

    /** Bilinear sample at a fractional source position (pixel-centre coordinates). */
    fun bilinear(src: FloatArray, w: Int, h: Int, fx: Float, fy: Float): Float {
        val x0 = kotlin.math.floor(fx).toInt()
        val y0 = kotlin.math.floor(fy).toInt()
        val tx = fx - x0
        val ty = fy - y0
        val a = src[clamp(y0, h) * w + clamp(x0, w)]
        val b = src[clamp(y0, h) * w + clamp(x0 + 1, w)]
        val c = src[clamp(y0 + 1, h) * w + clamp(x0, w)]
        val d = src[clamp(y0 + 1, h) * w + clamp(x0 + 1, w)]
        val top = a + (b - a) * tx
        val bottom = c + (d - c) * tx
        return top + (bottom - top) * ty
    }

    private fun catmullRom(t: Float): Float {
        val x = kotlin.math.abs(t)
        return when {
            x < 1f -> 1.5f * x * x * x - 2.5f * x * x + 1f
            x < 2f -> -0.5f * x * x * x + 2.5f * x * x - 4f * x + 2f
            else -> 0f
        }
    }

    private fun clamp(v: Int, size: Int) = if (v < 0) 0 else if (v >= size) size - 1 else v
}
