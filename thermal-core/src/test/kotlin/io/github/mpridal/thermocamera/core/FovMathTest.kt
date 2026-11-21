package io.github.mpridal.thermocamera.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FovMathTest {
    @Test fun fovFromSensorAndFocalLength() {
        // 36 mm wide sensor at 18 mm focal length → 90°.
        assertEquals(90f, FovMath.fovDeg(36f, 18f), 0.01f)
    }

    @Test fun cameraWithThermalFovSeesEverything() {
        assertEquals(480f, FovMath.pixelsInThermal(480, 43f), 0.01f)
        assertEquals(480f, FovMath.pixelsInThermal(480, 30f), 0.01f) // narrower than thermal → capped
    }

    @Test fun wideCameraUsesPartOfItsSensor() {
        // tan(21.5°) / tan(40°) ≈ 0.4696
        assertEquals(4000 * 0.4696f, FovMath.pixelsInThermal(4000, 80f), 2f)
    }

    @Test fun phoneBeatsModuleCamera() {
        val module = FovMath.effectivePixels(640, 480, 57f, 43f)
        val phone = FovMath.effectivePixels(4000, 3000, 80f, 64f)
        assertTrue("phone $phone vs module $module", phone > 5 * module)
    }

    @Test fun estimatedCropIsCentred() {
        val c = FovMath.estimatedCrop(1280, 960, 64f)
        assertEquals(640f, c.cx, 0f); assertEquals(480f, c.cy, 0f)
        assertTrue(c.side < 960f)
    }
}
