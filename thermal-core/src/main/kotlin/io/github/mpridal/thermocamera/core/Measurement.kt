package io.github.mpridal.thermocamera.core

/** A temperature at a position in source-grid pixel-centre coordinates. */
data class Spot(val x: Float, val y: Float, val celsius: Float)

data class Measurements(
    val min: Spot,
    val max: Spot,
    /** Average of the 2 × 2 block in the centre. */
    val center: Float,
    /** User points P1–P5, same order as requested. */
    val points: List<Spot>,
)

object Measure {

    /**
     * @param points user points as (x, y) in source-grid pixel-centre coordinates.
     */
    fun of(values: FloatArray, w: Int, h: Int, points: List<Pair<Float, Float>> = emptyList()): Measurements {
        require(values.size == w * h)
        var minI = 0
        var maxI = 0
        for (i in 1 until values.size) {
            if (values[i] < values[minI]) minI = i
            if (values[i] > values[maxI]) maxI = i
        }
        val cx = w / 2 - 1
        val cy = h / 2 - 1
        val xs = if (w >= 2) cx..cx + 1 else 0..0
        val ys = if (h >= 2) cy..cy + 1 else 0..0
        var sum = 0f
        var n = 0
        for (y in ys) for (x in xs) { sum += values[y * w + x]; n++ }
        return Measurements(
            min = Spot((minI % w).toFloat(), (minI / w).toFloat(), values[minI]),
            max = Spot((maxI % w).toFloat(), (maxI / w).toFloat(), values[maxI]),
            center = sum / n,
            points = points.map { (x, y) -> Spot(x, y, Interpolation.bilinear(values, w, h, x, y)) },
        )
    }
}
