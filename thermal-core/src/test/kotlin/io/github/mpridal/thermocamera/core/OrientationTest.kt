package io.github.mpridal.thermocamera.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationTest {
    // 3 × 2 image:  1 2 3
    //               4 5 6
    private val src = intArrayOf(1, 2, 3, 4, 5, 6)

    private fun run(o: Orientation): IntArray = IntArray(6).also { o.apply(src, 3, 2, it) }

    @Test fun identity() = assertArrayEquals(src, run(Orientation()))

    @Test fun rotate90Clockwise() {
        // 4 1
        // 5 2
        // 6 3
        assertArrayEquals(intArrayOf(4, 1, 5, 2, 6, 3), run(Orientation(90)))
        assertEquals(2, Orientation(90).outWidth(3, 2))
    }

    @Test fun rotate180() = assertArrayEquals(intArrayOf(6, 5, 4, 3, 2, 1), run(Orientation(180)))

    @Test fun rotate270() {
        // 3 6
        // 2 5
        // 1 4
        assertArrayEquals(intArrayOf(3, 6, 2, 5, 1, 4), run(Orientation(270)))
    }

    @Test fun mirror() = assertArrayEquals(intArrayOf(3, 2, 1, 6, 5, 4), run(Orientation(0, true)))

    @Test fun fourQuarterTurnsAreIdentity() {
        var o = Orientation()
        repeat(4) { o = o.rotatedBy90() }
        assertEquals(Orientation(), o)
    }

    @Test fun floatSquareRotation() {
        val f = FloatArray(4) { it.toFloat() } // 0 1 / 2 3
        val out = FloatArray(4)
        Orientation(90).apply(f, 2, 2, out)
        assertArrayEquals(floatArrayOf(2f, 0f, 3f, 1f), out, 0f)
    }
}

class CropSquareRotationTest {
    /**
     * Rotating image and crop together must show the crop content rotated the same way as the
     * thermal grid: new crop-relative (u, v) shows what was at the rotated position before.
     */
    @Test fun cropContentRotatesWithImage() {
        val w = 80; val h = 60
        val img = IntArray(w * h) { it }
        val crop = CropSquare(cx = 30f, cy = 20f, side = 20f)
        // Inverse of a clockwise rotation in normalised crop coordinates.
        val before: Map<Int, (Float, Float) -> Pair<Float, Float>> = mapOf(
            0 to { u, v -> u to v },
            90 to { u, v -> v to 1f - u },
            180 to { u, v -> 1f - u to 1f - v },
            270 to { u, v -> 1f - v to u },
        )
        for (r in Orientation.ROTATIONS) {
            val o = Orientation(r)
            val out = IntArray(w * h).also { o.apply(img, w, h, it) }
            val ow = o.outWidth(w, h)
            val c2 = crop.rotatedWithImage(r, w, h)
            for ((u, v) in listOf(0.33f to 0.21f, 0.71f to 0.63f)) {
                val (x2, y2) = c2.map(u, v)
                val (bu, bv) = before.getValue(r)(u, v)
                val (x1, y1) = crop.map(bu, bv)
                val expected = img[kotlin.math.floor(y1).toInt() * w + kotlin.math.floor(x1).toInt()]
                val got = out[kotlin.math.floor(y2).toInt() * ow + kotlin.math.floor(x2).toInt()]
                assertEquals("rotation $r at $u,$v", expected, got)
            }
        }
    }
}
