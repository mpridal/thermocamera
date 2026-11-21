package io.github.mpridal.thermocamera.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ImageRegistrationTest {

    /** Synthetic "room": smooth gradient plus rectangles and discs of different brightness. */
    private fun scene(w: Int, h: Int, seed: Int): GrayImage {
        val r = Random(seed)
        val img = GrayImage(w, h)
        for (y in 0 until h) for (x in 0 until w) img.data[y * w + x] = 90f + 60f * x / w + 30f * y / h
        repeat(25) {
            val cx = r.nextInt(w); val cy = r.nextInt(h)
            val rw = 10 + r.nextInt(w / 5); val rh = 10 + r.nextInt(h / 5)
            val v = r.nextInt(255).toFloat()
            val disc = r.nextBoolean()
            for (y in maxOf(0, cy - rh) until minOf(h, cy + rh)) for (x in maxOf(0, cx - rw) until minOf(w, cx + rw)) {
                val inside = !disc || ((x - cx) * (x - cx)).toFloat() / (rw * rw) + ((y - cy) * (y - cy)).toFloat() / (rh * rh) <= 1f
                if (inside) img.data[y * w + x] = v
            }
        }
        return img
    }

    /** Module view = crop of the phone view at [x0], [y0] with size [cw] × [ch], resampled to 640 × 480, with exposure change and noise. */
    private fun moduleView(phone: GrayImage, x0: Int, y0: Int, cw: Int, ch: Int, seed: Int): GrayImage {
        val crop = GrayImage(cw, ch)
        for (y in 0 until ch) for (x in 0 until cw) crop.data[y * cw + x] = phone[x0 + x, y0 + y]
        val m = crop.resampled(640, 480)
        val r = Random(seed)
        for (i in m.data.indices) m.data[i] = (m.data[i] * 0.8f + 20f + (r.nextFloat() - 0.5f) * 12f).coerceIn(0f, 255f)
        return m
    }

    private fun check(phoneW: Int, phoneH: Int, x0: Int, y0: Int, cw: Int, seed: Int) {
        val phone = scene(phoneW, phoneH, seed)
        val ch = cw * 3 / 4
        val module = moduleView(phone, x0, y0, cw, ch, seed + 1)
        val res = ImageRegistration.register(module, phone)
        assertNotNull("no match", res)
        res!!
        val expScale = cw / 640f
        // Error in pixels of the downscaled (320 px wide) phone image must stay below 2 px (zadání kap. 10).
        val k = 320f / phoneW
        val errX = abs(res.tx - x0) * k
        val errY = abs(res.ty - y0) * k
        val errSize = abs(res.scale - expScale) * 640f * k
        assertTrue("error x=$errX y=$errY size=$errSize result=$res", errX < 2f && errY < 2f && errSize < 2f)
    }

    @Test fun findsCentredCrop() = check(1280, 960, 320, 240, 640, seed = 1)

    @Test fun findsOffsetSmallCrop() = check(1280, 960, 150, 500, 420, seed = 2)

    @Test fun findsLargeCrop() = check(1280, 960, 100, 60, 1000, seed = 3)

    @Test fun findsCropInWidePhoneImage() = check(1600, 1200, 700, 300, 560, seed = 4)

    @Test fun rejectsUnrelatedImages() {
        val phone = scene(1280, 960, 10)
        val module = scene(640, 480, 99)
        val res = ImageRegistration.register(module, phone)
        assertTrue("unexpected match $res", res == null || res.score < 0.5f)
    }

    @Test fun rejectsFlatImage() {
        val phone = GrayImage(1280, 960, FloatArray(1280 * 960) { 128f })
        val module = scene(640, 480, 5)
        assertNull(ImageRegistration.register(module, phone))
    }
}

class ImageRegistrationTimingTest {
    @Test fun printTiming() {
        val phone = GrayImage(1280, 960, FloatArray(1280 * 960) { ((it * 37) % 251).toFloat() })
        val module = GrayImage(640, 480, FloatArray(640 * 480) { ((it * 13) % 241).toFloat() })
        repeat(3) { ImageRegistration.register(module, phone) } // warm-up
        val n = 5
        val t0 = System.nanoTime()
        repeat(n) { ImageRegistration.register(module, phone) }
        println("REGISTRATION_MS=" + (System.nanoTime() - t0) / n / 1_000_000)
    }
}
