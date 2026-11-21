package io.github.mpridal.thermocamera.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    @Test fun lutEndpointsMatchFirstAndLastStop() {
        for (p in Palette.entries) {
            assertEquals(p.name, p.stops.first().argb, p.lut.first())
            assertEquals(p.name, p.stops.last().argb, p.lut.last())
            assertEquals(Palette.LUT_SIZE, p.lut.size)
        }
    }

    @Test fun grayIsMonotonic() {
        val lut = Palette.GRAY.lut
        for (i in 1 until lut.size) assertTrue((lut[i] and 0xFF) >= (lut[i - 1] and 0xFF))
        assertEquals(0xFF000000.toInt(), lut[0])
        assertEquals(0xFFFFFFFF.toInt(), lut[255])
    }

    @Test fun isothermColourIsNotInAnyPalette() {
        for (p in Palette.entries) assertFalse(p.name, p.lut.contains(Palette.ISOTHERM_ARGB))
    }
}

class InterpolationTest {
    private val src = FloatArray(16) { (it * 7 % 11).toFloat() }

    @Test fun bicubicPreservesNodeValues() {
        // Scale 3 (odd) puts some output pixel centres exactly on source centres.
        val dst = FloatArray(12 * 12)
        Interpolation.resize(src, 4, 4, dst, 12, 12, Interpolation.Mode.BICUBIC)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(src[y * 4 + x], dst[(3 * y + 1) * 12 + (3 * x + 1)], 1e-4f)
        }
    }

    @Test fun separableResamplerMatchesReferenceBicubic() {
        val f = FloatArray(32 * 32)
        SimulatedScene().fill(f, 1.0)
        val dst = FloatArray(320 * 320)
        Interpolation.Resampler(32, 32, 320, 320).resize(f, dst)
        for (y in 0 until 320 step 7) for (x in 0 until 320 step 5) {
            val ref = Interpolation.bicubic(f, 32, 32, (x + 0.5f) / 10f - 0.5f, (y + 0.5f) / 10f - 0.5f)
            assertEquals(ref, dst[y * 320 + x], 1e-3f)
        }
    }

    @Test fun sameSizeIsIdentity() {
        val dst = FloatArray(16)
        Interpolation.resize(src, 4, 4, dst, 4, 4)
        for (i in src.indices) assertEquals(src[i], dst[i], 1e-4f)
    }

    @Test fun constantStaysConstant() {
        val c = FloatArray(16) { 21.5f }
        val dst = FloatArray(320 * 320)
        Interpolation.resize(c, 4, 4, dst, 320, 320)
        for (v in dst) assertEquals(21.5f, v, 1e-4f)
    }

    @Test fun nearestReturnsSourceValuesOnly() {
        val dst = FloatArray(40 * 40)
        Interpolation.resize(src, 4, 4, dst, 40, 40, Interpolation.Mode.NEAREST)
        assertTrue(dst.all { v -> src.any { it == v } })
    }

    @Test fun bilinearMidpoint() {
        val s = floatArrayOf(0f, 10f, 20f, 30f)
        assertEquals(15f, Interpolation.bilinear(s, 2, 2, 0.5f, 0.5f), 1e-5f)
    }
}

class CorrectionTest {
    @Test fun emissivityOneDoesNotChangeValue() {
        val c = Correction(emissivity = 1f)
        for (t in listOf(-20f, 0f, 19.6f, 100f, 300f)) assertEquals(t, c.apply(t), 1e-4f)
    }

    @Test fun lowerEmissivityRaisesWarmObject() {
        // An object warmer than its surroundings looks colder than it is when ε < 1.
        val c = Correction(emissivity = 0.9f, reflectedCelsius = 20f)
        assertTrue(c.apply(50f) > 50f)
        assertEquals(20f, c.apply(20f), 1e-3f) // at reflected temperature nothing changes
    }

    @Test fun offsetAndGain() {
        val c = Correction(emissivity = 1f, offset = 1.5f, gain = 2f)
        assertEquals(41.5f, c.apply(20f), 1e-4f)
    }

    @Test fun singlePointOffset() {
        assertEquals(0.8f, Correction.offsetFor(actual = 36.0f, measured = 35.2f), 1e-4f)
    }

    @Test fun twoPointCalibrationHitsBothReferences() {
        val (gain, offset) = Correction.twoPoint(0f, 1.2f, 50f, 48.7f)
        val c = Correction(emissivity = 1f, gain = gain, offset = offset)
        assertEquals(0f, c.apply(1.2f), 1e-3f)
        assertEquals(50f, c.apply(48.7f), 1e-3f)
    }
}

class MeasureTest {
    @Test fun minMaxCenterAndPoints() {
        val w = 4
        val v = FloatArray(16) { 20f }
        v[1 * w + 1] = 22f; v[1 * w + 2] = 22f; v[2 * w + 1] = 22f; v[2 * w + 2] = 22f
        v[0] = 10f
        v[3 * w + 3] = 40f
        val m = Measure.of(v, 4, 4, listOf(1.5f to 1.5f))
        assertEquals(10f, m.min.celsius, 0f); assertEquals(0f, m.min.x, 0f); assertEquals(0f, m.min.y, 0f)
        assertEquals(40f, m.max.celsius, 0f); assertEquals(3f, m.max.x, 0f); assertEquals(3f, m.max.y, 0f)
        assertEquals(22f, m.center, 1e-5f)
        assertEquals(22f, m.points.single().celsius, 1e-5f)
    }
}

class AutoRangeTest {
    @Test fun stableUnderNoise() {
        val scene = SimulatedScene(seed = 1)
        val frame = FloatArray(32 * 32)
        val auto = AutoRange()
        repeat(30) { scene.fill(frame, 0.0); auto.update(frame) }
        val first = auto.current()
        repeat(10) { scene.fill(frame, 0.0); auto.update(frame) }
        val second = auto.current()
        assertEquals(first.min, second.min, 0.15f)
        assertEquals(first.max, second.max, 0.15f)
    }

    @Test fun enforcesMinimumSpan() {
        val r = AutoRange(minSpan = 1f).update(FloatArray(1024) { 20f })
        assertEquals(1f, r.max - r.min, 1e-4f)
    }
}

class ThermalBridgeTest {
    @Test fun fRsiMatchesHandCalculation() {
        // θi 21, θe -5, θsi 11.64 → (16.64)/(26) = 0.64
        assertEquals(0.64f, ThermalBridge.fRsi(11.64f, 21f, -5f)!!, 1e-3f)
    }

    @Test fun fRsiUndefinedWithoutDifference() {
        assertNull(ThermalBridge.fRsi(15f, 10f, 10f))
    }

    @Test fun isothermThresholdIsLimitBoundary() {
        val th = ThermalBridge.isothermThreshold(21f, -5f, 0.75f)
        assertEquals(0.75f, ThermalBridge.fRsi(th, 21f, -5f)!!, 1e-5f)
    }

    @Test fun reliability() {
        assertFalse(ThermalBridge.isReliable(21f, 15f))
        assertTrue(ThermalBridge.isReliable(21f, 0f))
    }
}

class FrameRendererTest {
    @Test fun mapsRangeEndsToPaletteEnds() {
        val r = FrameRenderer(2, 1)
        val px = r.render(floatArrayOf(0f, 100f), 2, 1, TempRange(0f, 100f), Palette.GRAY, Interpolation.Mode.NEAREST)
        assertEquals(0xFF000000.toInt(), px[0])
        assertEquals(0xFFFFFFFF.toInt(), px[1])
    }

    @Test fun clampsOutOfRange() {
        val r = FrameRenderer(2, 1)
        val px = r.render(floatArrayOf(-50f, 500f), 2, 1, TempRange(0f, 100f), Palette.IRON, Interpolation.Mode.NEAREST)
        assertEquals(Palette.IRON.lut.first(), px[0])
        assertEquals(Palette.IRON.lut.last(), px[1])
    }

    @Test fun isothermHighlightsColdPixels() {
        val r = FrameRenderer(2, 1)
        val px = r.render(floatArrayOf(10f, 20f), 2, 1, TempRange(0f, 30f), Palette.IRON, Interpolation.Mode.NEAREST, isothermBelow = 15f)
        assertEquals(Palette.ISOTHERM_ARGB, px[0])
        assertTrue(px[1] != Palette.ISOTHERM_ARGB)
    }
}

class SimulatedSceneTest {
    @Test fun sceneHasColdCornerAndWarmRadiator() {
        val s = SimulatedScene()
        val f = FloatArray(32 * 32)
        s.fill(f, 0.0)
        val m = Measure.of(f, 32, 32)
        assertTrue("min ${m.min.celsius}", m.min.celsius < 15f)
        assertTrue("max ${m.max.celsius}", m.max.celsius > 38f)
        assertTrue("center ${m.center}", m.center in 12f..30f)
    }
}
