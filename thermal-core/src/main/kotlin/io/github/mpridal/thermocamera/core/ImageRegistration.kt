package io.github.mpridal.thermocamera.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A grayscale image, row-major, values 0..255 as floats. */
class GrayImage(val width: Int, val height: Int, val data: FloatArray = FloatArray(width * height)) {
    init {
        require(data.size == width * height)
    }

    operator fun get(x: Int, y: Int) = data[y * width + x]

    /** Area-average downscale (box filter) to [w] × [h]. */
    fun resized(w: Int, h: Int): GrayImage {
        val out = GrayImage(w, h)
        val sx = width.toFloat() / w
        val sy = height.toFloat() / h
        for (y in 0 until h) {
            val y0 = (y * sy).toInt(); val y1 = maxOf(y0 + 1, ((y + 1) * sy).toInt().coerceAtMost(height))
            for (x in 0 until w) {
                val x0 = (x * sx).toInt(); val x1 = maxOf(x0 + 1, ((x + 1) * sx).toInt().coerceAtMost(width))
                var sum = 0f
                for (yy in y0 until y1) for (xx in x0 until x1) sum += data[yy * width + xx]
                out.data[y * w + x] = sum / ((y1 - y0) * (x1 - x0))
            }
        }
        return out
    }

    /** Bilinear resample to [w] × [h] (for up- or downscaling templates by non-integer factors). */
    fun resampled(w: Int, h: Int): GrayImage {
        if (w < width && h < height && (width / w) >= 2) return resized(w, h)
        val out = GrayImage(w, h)
        val src = data
        for (y in 0 until h) {
            val fy = (y + 0.5f) * height / h - 0.5f
            for (x in 0 until w) {
                val fx = (x + 0.5f) * width / w - 0.5f
                out.data[y * w + x] = Interpolation.bilinear(src, width, height, fx, fy)
            }
        }
        return out
    }

    /** Gradient magnitude (Sobel-like, central differences) – robust against exposure and colour differences. */
    fun gradient(): GrayImage {
        val out = GrayImage(width, height)
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val gx = this[x + 1, y] - this[x - 1, y]
            val gy = this[x, y + 1] - this[x, y - 1]
            out.data[y * width + x] = sqrt(gx * gx + gy * gy)
        }
        return out
    }

    /** 3 × 3 box blur – smooths edge images so the match score changes gradually with scale. */
    fun blurred(): GrayImage {
        val out = GrayImage(width, height)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0f; var n = 0
            for (dy in -1..1) {
                val yy = y + dy
                if (yy < 0 || yy >= height) continue
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx < 0 || xx >= width) continue
                    sum += data[yy * width + xx]; n++
                }
            }
            out.data[y * width + x] = sum / n
        }
        return out
    }

    companion object {
        /** From ARGB pixels (luma, BT.601). */
        fun fromArgb(argb: IntArray, w: Int, h: Int): GrayImage {
            val g = GrayImage(w, h)
            for (i in 0 until w * h) {
                val p = argb[i]
                g.data[i] = 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
            }
            return g
        }
    }
}

/**
 * Finds where the module camera image lies inside the phone camera image (zadání F11 bod 4):
 * a scale + translation (similarity without rotation – both cameras are mounted upright),
 * by zero-mean normalised cross-correlation of gradient images, coarse to fine.
 *
 * Result maps module pixel (mx, my) → phone pixel (scale · mx + tx, scale · my + ty), both in
 * the original resolutions passed to [register].
 */
object ImageRegistration {
    @JvmStatic var debug = false

    data class Result(val scale: Float, val tx: Float, val ty: Float, val score: Float) {
        fun mapX(mx: Float) = scale * mx + tx
        fun mapY(my: Float) = scale * my + ty
    }

    /** Below this correlation the match is considered unreliable. */
    const val MIN_SCORE = 0.35f

    /**
     * @param module module camera image (any size, typically 640 × 480)
     * @param phone phone camera image (any size, same orientation)
     * @param minScale / maxScale range of the module-in-phone scale relative to the phone image width
     *   (module width in phone pixels / phone width)
     * @return the best match, or null when none reaches [MIN_SCORE]
     */
    fun register(
        module: GrayImage,
        phone: GrayImage,
        minScale: Float = 0.25f,
        maxScale: Float = 1.0f,
    ): Result? {
        // Pyramid: phone at 64 → 160 → 320 px width; module downscaled once to 320 px width.
        val levels = intArrayOf(64, 160, 320)
        val fineW = levels.last()
        val phoneFine = phone.resized(fineW, (phone.height * fineW.toFloat() / phone.width).roundToInt())
        val moduleBase = module.resized(fineW, (module.height * fineW.toFloat() / module.width).roundToInt())
        val moduleAspect = module.height.toFloat() / module.width
        fun phoneAt(w: Int) = (if (w == fineW) phoneFine else phoneFine.resized(w, (phoneFine.height * w.toFloat() / fineW).roundToInt())).gradient().blurred()

        // Level 0: exhaustive over scales and positions.
        val p0 = phoneAt(levels[0])
        val steps = 20
        var rel = 0f; var bx = 0; var by = 0
        var score = -1f
        for (i in 0 until steps) {
            val r = exp(ln(minScale) + (ln(maxScale) - ln(minScale)) * i / (steps - 1))
            val tw = (r * levels[0]).roundToInt()
            val th = (tw * moduleAspect).roundToInt()
            if (tw < 10 || th < 8 || tw > p0.width || th > p0.height) continue
            val tmpl = moduleBase.resampled(tw, th).gradient().blurred()
            val (x, y, sc) = bestNcc(p0, tmpl, 0, p0.width - tw, 0, p0.height - th)
            if (sc > score) { score = sc; rel = r; bx = x; by = y }
        }
        if (score < 0f) return null

        // Finer levels: local search around the previous result, with a narrowing scale range.
        var prevW = levels[0]
        for ((li, w) in levels.withIndex().drop(1)) {
            val p = phoneAt(w)
            val f = w.toFloat() / prevW
            val cx = (bx * f).roundToInt()
            val cy = (by * f).roundToInt()
            val radius = if (li == 1) 4 else 3
            val scaleStep = if (li == 1) 0.03f else 0.01f
            val kRange = if (li == 1) 2 else 4
            var centreRel = rel; var ccx = cx; var ccy = cy
            // If the best scale lands on the edge of the searched range, re-centre and search again.
            for (round in 0 until 3) {
                var bestRel = centreRel; var nbx = ccx; var nby = ccy; var bestK = 0
                score = -1f
                for (k in -kRange..kRange) {
                    val r = centreRel * (1f + scaleStep * k)
                    val tw = (r * w).roundToInt()
                    val th = (tw * moduleAspect).roundToInt()
                    if (tw > p.width || th > p.height) continue
                    val tmpl = moduleBase.resampled(tw, th).gradient().blurred()
                    val (x, y, sc) = bestNcc(
                        p, tmpl,
                        (ccx - radius).coerceIn(0, p.width - tw), (ccx + radius).coerceIn(0, p.width - tw),
                        (ccy - radius).coerceIn(0, p.height - th), (ccy + radius).coerceIn(0, p.height - th),
                    )
                    if (debug) println("L$li round=$round k=$k r=$r tw=$tw x=$x y=$y sc=$sc")
                    if (sc > score) { score = sc; bestRel = r; nbx = x; nby = y; bestK = k }
                }
                centreRel = bestRel; ccx = nbx; ccy = nby
                if (kotlin.math.abs(bestK) < kRange) break
            }
            rel = centreRel; bx = ccx; by = ccy
            prevW = w
        }
        if (score < MIN_SCORE) return null
        // Convert to original resolutions.
        val phoneScale = phone.width.toFloat() / fineW
        return Result(scale = rel * phone.width / module.width, tx = bx * phoneScale, ty = by * phoneScale, score = score)
    }

    /** Exhaustive zero-mean NCC of [t] over [img] for top-left positions in the given ranges. */
    private fun bestNcc(img: GrayImage, t: GrayImage, xMin: Int, xMax: Int, yMin: Int, yMax: Int, step: Int = 1): Triple<Int, Int, Float> {
        val n = t.width * t.height
        var tMean = 0f
        for (v in t.data) tMean += v
        tMean /= n
        val tz = FloatArray(n) { t.data[it] - tMean }
        var tNorm = 0f
        for (v in tz) tNorm += v * v
        tNorm = sqrt(tNorm)
        if (tNorm < 1e-3f) return Triple(xMin, yMin, -1f)

        var bx = xMin; var by = yMin; var bs = -2f
        var y = yMin
        while (y <= yMax) {
            var x = xMin
            while (x <= xMax) {
                var sumI = 0f; var sumII = 0f; var sumIT = 0f
                for (ty in 0 until t.height) {
                    val row = (y + ty) * img.width + x
                    val trow = ty * t.width
                    for (tx in 0 until t.width) {
                        val v = img.data[row + tx]
                        sumI += v; sumII += v * v; sumIT += v * tz[trow + tx]
                    }
                }
                val varI = sumII - sumI * sumI / n
                if (varI > 1e-3f) {
                    val s = sumIT / (sqrt(varI) * tNorm) // tz is zero-mean, so ΣI·tz = Σ(I−Ī)·tz
                    if (s > bs) { bs = s; bx = x; by = y }
                }
                x += step
            }
            y += step
        }
        return Triple(bx, by, bs)
    }

    /** Distance helper for tests and diagnostics. */
    fun error(a: Result, scale: Float, tx: Float, ty: Float): Float = maxOf(abs(a.scale - scale) * 100f, abs(a.tx - tx), abs(a.ty - ty))
}
