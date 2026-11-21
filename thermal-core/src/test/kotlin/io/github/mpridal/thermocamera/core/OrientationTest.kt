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
