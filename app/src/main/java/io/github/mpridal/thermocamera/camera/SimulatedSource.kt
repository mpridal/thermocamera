package io.github.mpridal.thermocamera.camera

import io.github.mpridal.thermocamera.core.SimulatedScene
import io.github.mpridal.thermocamera.core.ThermalFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Synthetic camera (zadání kap. 3.1): room corner with a window, cold lintel and a radiator,
 * ~9 Hz thermal frames plus a matching 320 × 240 visible frame.
 * The thermal field of view corresponds to the centred 240 × 240 square of the visible frame.
 */
class SimulatedSource(
    private val fps: Float = 9f,
) : ThermalCameraSource {

    override val kind = ThermalCameraSource.Kind.SIMULATOR

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val scene = SimulatedScene()

    private val _state = MutableStateFlow<CameraState>(CameraState.Disconnected)
    override val state: StateFlow<CameraState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<ThermalFrame>(extraBufferCapacity = 1)
    override val frames: SharedFlow<ThermalFrame> = _frames.asSharedFlow()

    private val _visible = MutableSharedFlow<VisibleFrame>(extraBufferCapacity = 1)
    override val visibleFrames: SharedFlow<VisibleFrame> = _visible.asSharedFlow()

    override suspend fun start() {
        if (job?.isActive == true) return
        _state.value = CameraState.Connecting
        val start = System.nanoTime()
        val periodNs = (1e9f / fps).toLong()
        job = scope.launch {
            _state.value = CameraState.Streaming(fps)
            var next = System.nanoTime()
            while (isActive) {
                // Fixed rate: the next frame is due one period after the previous one was due,
                // regardless of how long producing a frame took.
                next += periodNs
                val now = System.nanoTime()
                val t = (now - start) / 1e9
                val values = FloatArray(scene.width * scene.height)
                scene.fill(values, t)
                _frames.tryEmit(ThermalFrame(scene.width, scene.height, values, now))
                _visible.tryEmit(renderVisible(t, now))
                val wait = next - System.nanoTime()
                if (wait > 0) delay(wait / 1_000_000) else next = System.nanoTime()
            }
        }
    }

    override suspend fun stop() {
        job?.cancel()
        job = null
        _state.value = CameraState.Disconnected
    }

    override suspend fun selfTest(): SelfTestReport = SelfTestReport(
        listOf(
            SelfTestReport.Step("device", true, "simulator"),
            SelfTestReport.Step("frames", _state.value is CameraState.Streaming, "$fps Hz"),
        ),
    )

    override fun diagnostics() = DiagnosticsDump("SimulatedSource fps=$fps size=${scene.width}x${scene.height}")

    /** Simple flat-shaded drawing of the same scene as seen by a normal camera. */
    /**
     * The scene is drawn once into a slightly larger image; each frame only copies a crop shifted
     * by the same sway as the thermal scene (cheap enough for 9 Hz even on an emulator).
     */
    private val visibleScene: IntArray by lazy {
        val w = VISIBLE_W + 2 * MARGIN
        val h = VISIBLE_H + 2 * MARGIN
        val crop = VISIBLE_H // the thermal FOV is the centred square of side VISIBLE_H
        val x0 = (VISIBLE_W - crop) / 2 + MARGIN
        IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            visibleColorAt((x - x0).toFloat() / (crop - 1), (y - MARGIN).toFloat() / (VISIBLE_H - 1))
        }
    }

    private fun renderVisible(t: Double, now: Long): VisibleFrame {
        val w = VISIBLE_W
        val h = VISIBLE_H
        val srcW = w + 2 * MARGIN
        val px = IntArray(w * h)
        // Same sway as SimulatedScene (in thermal pixels of 1/31 of the square), converted to image pixels.
        val dx = (sin(t * 0.6) * 0.8 / 31.0 * (h - 1)).roundToInt().coerceIn(-MARGIN, MARGIN)
        val dy = (sin(t * 0.45 + 1.0) * 0.5 / 31.0 * (h - 1)).roundToInt().coerceIn(-MARGIN, MARGIN)
        val scene = visibleScene
        for (y in 0 until h) {
            System.arraycopy(scene, (y + MARGIN + dy) * srcW + MARGIN + dx, px, y * w, w)
        }
        return VisibleFrame(w, h, px, now)
    }

    private fun visibleColorAt(u: Float, v: Float): Int {
        // Ceiling above the line v < 0.03, wall below; slight shading into the corner.
        val shade = (0.82f + 0.18f * ((u + v).coerceIn(0f, 1f))).coerceIn(0f, 1f)
        var r = 226; var g = 221; var b = 212
        if (v < 0.03f) { r = 240; g = 238; b = 232 }
        if (u < 0.02f) { r = 205; g = 200; b = 192 }
        val inWindowX = u in 0.52f..0.88f
        if (inWindowX && v in 0.27f..0.30f) { r = 245; g = 245; b = 245 } // frame
        if (inWindowX && v in 0.30f..0.62f) { r = 150; g = 190; b = 225 } // glass / sky
        if (inWindowX && v in 0.62f..0.645f) { r = 250; g = 250; b = 250 } // sill
        if (u in 0.55f..0.85f && v in 0.74f..0.90f) {
            val stripe = ((u * 60).toInt() % 2 == 0)
            r = if (stripe) 238 else 222; g = r; b = r - 4
        }
        if (v > 0.93f) { r = 140; g = 104; b = 72 } // wooden floor
        return (0xFF shl 24) or ((r * shade).roundToInt().coerceIn(0, 255) shl 16) or
            ((g * shade).roundToInt().coerceIn(0, 255) shl 8) or (b * shade).roundToInt().coerceIn(0, 255)
    }

    companion object {
        const val VISIBLE_W = 320
        const val VISIBLE_H = 240
        private const val MARGIN = 16
    }
}
