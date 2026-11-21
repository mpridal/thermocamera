package io.github.mpridal.thermocamera.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class UvcFrameAssemblerTest {

    /** Splits [frame] into UVC payloads of at most [payloadMax] bytes with 12-byte headers. */
    private fun packets(frame: ByteArray, fid: Int, payloadMax: Int, eof: Boolean = true, err: Boolean = false): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        var off = 0
        while (off < frame.size) {
            val n = minOf(payloadMax, frame.size - off)
            val last = off + n == frame.size
            var info = 0x80 or fid or (if (last && eof) 0x02 else 0) or (if (err) 0x40 else 0)
            val p = ByteArray(12 + n)
            p[0] = 12; p[1] = info.toByte()
            System.arraycopy(frame, off, p, 12, n)
            out += p
            off += n
        }
        return out
    }

    private fun frame(size: Int, seed: Int) = ByteArray(size) { ((it * 7 + seed) and 0xFF).toByte() }

    @Test fun assemblesFramesWithEof() {
        val got = mutableListOf<ByteArray>()
        val a = UvcFrameAssembler(5000) { got += it.copyOf() }
        val f1 = frame(5000, 1); val f2 = frame(5000, 2)
        packets(f1, 0, 1788).forEach { a.push(it, 0, it.size) }
        a.push(byteArrayOf(12, 0x81.toByte()), 0, 2) // header-only packet
        packets(f2, 1, 1788).forEach { a.push(it, 0, it.size) }
        assertEquals(2, got.size)
        assertArrayEquals(f1, got[0]); assertArrayEquals(f2, got[1])
        assertEquals(0, a.framesDropped)
        assertEquals(12, a.maxHeaderLength)
    }

    @Test fun assemblesFramesOnFidToggleWithoutEof() {
        val got = mutableListOf<ByteArray>()
        val a = UvcFrameAssembler(3000) { got += it.copyOf() }
        val f1 = frame(3000, 3)
        packets(f1, 0, 1000, eof = false).forEach { a.push(it, 0, it.size) }
        assertEquals(0, got.size)
        packets(frame(3000, 4), 1, 1000, eof = false).first().let { a.push(it, 0, it.size) }
        assertEquals(1, got.size)
        assertArrayEquals(f1, got[0])
    }

    @Test fun dropsShortErroneousAndOversizedFrames() {
        var count = 0
        val a = UvcFrameAssembler(3000) { count++ }
        packets(frame(2000, 5), 0, 1000).forEach { a.push(it, 0, it.size) }          // too short
        packets(frame(3000, 6), 1, 1000, err = true).forEach { a.push(it, 0, it.size) } // error bit
        packets(frame(3500, 7), 0, 1000).forEach { a.push(it, 0, it.size) }          // too long
        val lost = packets(frame(3000, 8), 1, 1000)
        a.push(lost[0], 0, lost[0].size); a.packetLost(); lost.drop(1).forEach { a.push(it, 0, it.size) }
        assertEquals(0, count)
        assertEquals(4, a.framesDropped)
        packets(frame(3000, 9), 0, 1000).forEach { a.push(it, 0, it.size) }          // good again
        assertEquals(1, count)
    }

    @Test fun realFrameSurvivesAssemblyAndDecodes() {
        val real = requireNotNull(javaClass.getResourceAsStream("/ht102/frame-640x480-yuy2.bin")).use { it.readBytes() }
        var decoded: ThermalFrame? = null
        val a = UvcFrameAssembler(640 * 480 * 2) { decoded = Ht102Decoder.decode(it) }
        packets(real, 1, 1788).forEach { a.push(it, 0, it.size) }
        assertEquals(18.5f, decoded!![0, 0], 0.05f)
    }
}

class Yuy2Test {
    @Test fun grayLevels() {
        // Y=16 → black, Y=235 → white, neutral chroma.
        val src = byteArrayOf(16, 128.toByte(), 235.toByte(), 128.toByte())
        val dst = IntArray(2)
        Yuy2.toArgb(src, 2, 1, dst)
        assertEquals(0xFF000000.toInt(), dst[0])
        assertEquals(0xFFFFFFFF.toInt(), dst[1])
    }

    @Test fun redIsRed() {
        // BT.601 red ≈ Y 81, U 90, V 240.
        val src = byteArrayOf(81, 90, 81, 240.toByte())
        val dst = IntArray(2)
        Yuy2.toArgb(src, 2, 1, dst)
        val r = (dst[0] shr 16) and 0xFF; val g = (dst[0] shr 8) and 0xFF; val b = dst[0] and 0xFF
        assert(r > 240 && g < 15 && b < 15) { "got $r,$g,$b" }
    }
}
