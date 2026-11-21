package io.github.mpridal.thermocamera.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ht102DecoderTest {

    /** Real 640 × 480 YUY2 frame captured from the user's HT-102 on Windows, 2026-11-21. */
    private fun realFrame(): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/ht102/frame-640x480-yuy2.bin")).use { it.readBytes() }

    @Test fun decodesRealCameraFrame() {
        val bytes = realFrame()
        assertEquals(640 * 480 * 2, bytes.size)
        val f = Ht102Decoder.decode(bytes)
        val m = Measure.of(f.celsius, 32, 32)
        // Indoor scene: values match what the frame showed (room ~18–20 °C, warm object ~28–31 °C).
        assertEquals(15.7f, m.min.celsius, 0.05f)
        assertEquals(31.3f, m.max.celsius, 0.05f)
        assertTrue(f.celsius.all { it in 10f..40f })
        // Top-left corner value, read with an independent Python decoder.
        assertEquals(18.5f, f[0, 0], 0.05f)
    }

    @Test fun encodeDecodeRoundTrip() {
        val values = FloatArray(1024) { i -> (i % 97) * 3.3f - 20f } // includes negatives
        values[5] = 300f
        val frame = ByteArray(640 * 480 * 2) { (it * 31).toByte() }
        Ht102Decoder.encode(values, frame)
        val out = FloatArray(1024)
        Ht102Decoder.decode(frame, out)
        for (i in values.indices) assertEquals(values[i], out[i], 0.051f)
    }

    @Test fun encodingOnlyTouchesChromaLsbs() {
        val frame = ByteArray(Ht102Decoder.REQUIRED_BYTES) { 0x80.toByte() }
        val before = frame.copyOf()
        Ht102Decoder.encode(FloatArray(1024) { 21.7f }, frame)
        for (i in frame.indices) {
            val diff = (frame[i].toInt() xor before[i].toInt()) and 0xFF
            assertTrue("byte $i", diff == 0 || (diff == 1 && i % 2 == 1))
        }
    }
}
