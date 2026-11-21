package io.github.mpridal.thermocamera.ui.cameratest

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mpridal.thermocamera.camera.CameraRepository
import io.github.mpridal.thermocamera.camera.CameraState
import io.github.mpridal.thermocamera.camera.Ht102Source
import io.github.mpridal.thermocamera.camera.ThermalCameraSource
import io.github.mpridal.thermocamera.core.FrameRenderer
import io.github.mpridal.thermocamera.core.Interpolation
import io.github.mpridal.thermocamera.core.Orientation
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import io.github.mpridal.thermocamera.core.TempRange
import io.github.mpridal.thermocamera.core.ThermalFrame
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.abs

enum class TestStep { DEVICE, PERMISSION, THERMAL, VISIBLE, SENSOR, READING }

enum class StepStatus { PENDING, RUNNING, OK, FAIL }

data class StepResult(val status: StepStatus = StepStatus.PENDING, val detail: String = "")

data class CameraTestUi(
    val running: Boolean = false,
    val steps: Map<TestStep, StepResult> = TestStep.entries.associateWith { StepResult() },
    val finished: Boolean = false,
    val firstFailure: TestStep? = null,
    val raw: ImageBitmap? = null,
    val rawRange: TempRange? = null,
    val noise: Float? = null,
    val simulator: Boolean = false,
    val diagnostics: String = "",
)

/**
 * Camera test (zadání F6): runs the six checks against the active source and keeps a raw
 * 32 × 32 preview (nearest neighbour) and the frame-to-frame noise.
 */
@HiltViewModel
class CameraTestViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val cameras: CameraRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val orientation: StateFlow<Orientation> = settingsRepo.settings.map { it.orientation }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Orientation())

    fun rotate() = viewModelScope.launch { settingsRepo.setOrientation(orientation.value.rotatedBy90()) }
    fun mirror() = viewModelScope.launch { settingsRepo.setOrientation(orientation.value.toggledMirror()) }


    private val _ui = MutableStateFlow(CameraTestUi())
    val ui: StateFlow<CameraTestUi> = _ui.asStateFlow()

    private val rawRenderer = FrameRenderer(RAW_SIZE, RAW_SIZE)
    private val rawBitmap = Bitmap.createBitmap(RAW_SIZE, RAW_SIZE, Bitmap.Config.ARGB_8888)
    private var job: Job? = null
    private var previewJob: Job? = null

    init {
        startPreview()
        run()
        // Every finished test is saved for reading from a computer (DiagnosticsLog).
        viewModelScope.launch {
            ui.map { it.finished to it.diagnostics }.distinctUntilChanged().collect { (finished, text) ->
                if (finished && text.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        io.github.mpridal.thermocamera.diagnostics.DiagnosticsLog.save(context, "camera-test", text)
                    }
                }
            }
        }
    }

    private fun startPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            var prev: FloatArray? = null
            cameras.active.value.frames.collect { f ->
                val o = orientation.value
                val data = FloatArray(f.celsius.size).also { o.apply(f.celsius, f.width, f.height, it) }
                val lo = data.min()
                val hi = maxOf(data.max(), lo + 1f)
                val range = TempRange(lo, hi)
                val px = rawRenderer.render(data, o.outWidth(f.width, f.height), o.outHeight(f.width, f.height), range, Palette.IRON, Interpolation.Mode.NEAREST)
                rawBitmap.setPixels(px, 0, RAW_SIZE, 0, 0, RAW_SIZE, RAW_SIZE)
                val noise = prev?.let { p -> f.celsius.indices.sumOf { abs(f.celsius[it] - p[it]).toDouble() }.toFloat() / f.celsius.size }
                prev = f.celsius.copyOf()
                _ui.update { it.copy(raw = rawBitmap.asImageBitmap(), rawRange = range, noise = noise ?: it.noise) }
            }
        }
    }

    fun run() {
        job?.cancel()
        job = viewModelScope.launch {
            val src = cameras.active.value
            _ui.update { CameraTestUi(running = true, simulator = src.kind == ThermalCameraSource.Kind.SIMULATOR, raw = it.raw, rawRange = it.rawRange, noise = it.noise) }
            startPreview()

            // 1. Device
            set(TestStep.DEVICE, StepStatus.RUNNING)
            val ht = src as? Ht102Source
            val device = if (ht != null) ht.deviceDescription() else "simulator"
            if (device == null) return@launch fail(TestStep.DEVICE)
            set(TestStep.DEVICE, StepStatus.OK, device)

            // 2. Permission (the source asks for it on its own; give the user time to answer)
            set(TestStep.PERMISSION, StepStatus.RUNNING)
            if (src.state.value is CameraState.NeedsPermission) ht?.requestPermissionAgain()
            val granted = withTimeoutOrNull(PERMISSION_WAIT_MS) {
                src.state.first { it !is CameraState.NeedsPermission && it !is CameraState.Disconnected }
            }
            if (granted == null) return@launch fail(TestStep.PERMISSION)
            set(TestStep.PERMISSION, StepStatus.OK)

            // 3.–6. Collect frames for a few seconds.
            set(TestStep.THERMAL, StepStatus.RUNNING)
            set(TestStep.VISIBLE, StepStatus.RUNNING)
            val frames = mutableListOf<ThermalFrame>()
            var visibleSize: Pair<Int, Int>? = null
            val collectThermal = launch { src.frames.collect { frames += it } }
            val collectVisible = launch { src.visibleFrames?.collect { visibleSize = it.width to it.height } }
            delay(COLLECT_MS)
            collectThermal.cancel(); collectVisible.cancel()

            val seconds = COLLECT_MS / 1000f
            val hz = if (frames.size >= 2) (frames.size - 1) / ((frames.last().timestampNanos - frames.first().timestampNanos) / 1e9f) else frames.size / seconds
            val hzText = "%.1f Hz".format(hz)
            // Evaluate every step even when one fails, so the report shows the whole picture.
            var failed: TestStep? = null
            fun result(step: TestStep, ok: Boolean, detail: String) {
                set(step, if (ok) StepStatus.OK else StepStatus.FAIL, detail)
                if (!ok && failed == null) failed = step
            }
            result(TestStep.THERMAL, hz >= MIN_HZ, hzText)

            val vs = visibleSize
            result(TestStep.VISIBLE, vs != null, vs?.let { "${it.first} × ${it.second}" } ?: "")

            if (frames.isEmpty()) {
                result(TestStep.SENSOR, false, "–")
                result(TestStep.READING, false, "–")
            } else {
                val n = frames.first().celsius.size
                var faulty = 0
                for (i in 0 until n) {
                    val outOfRange = frames.any { it.celsius[i] !in -40f..350f }
                    val stuck = frames.size >= 5 && frames.all { it.celsius[i] == frames.first().celsius[i] }
                    if (outOfRange || stuck) faulty++
                }
                result(TestStep.SENSOR, faulty == 0, faulty.toString())
                val last = frames.last()
                val c = (last[15, 15] + last[16, 15] + last[15, 16] + last[16, 16]) / 4f
                result(TestStep.READING, c in -20f..300f, "%.1f °C".format(c))
            }
            _ui.update { it.copy(running = false, finished = true, firstFailure = failed, diagnostics = diagnostics(src)) }
            return@launch
            _ui.update { it.copy(running = false, finished = true, diagnostics = diagnostics(src)) }
        }
    }

    private fun set(step: TestStep, status: StepStatus, detail: String = "") {
        _ui.update { it.copy(steps = it.steps + (step to StepResult(status, detail))) }
    }

    private fun fail(step: TestStep, detail: String = "") {
        set(step, StepStatus.FAIL, detail)
        // Steps that were started together but not evaluated are back to pending.
        _ui.update { u -> u.copy(steps = u.steps.mapValues { (_, r) -> if (r.status == StepStatus.RUNNING) StepResult() else r }) }
        _ui.update { it.copy(running = false, finished = true, firstFailure = step, diagnostics = diagnostics(cameras.active.value)) }
    }

    private fun diagnostics(src: ThermalCameraSource): String = buildString {
        appendLine("ThermoCamera camera test")
        _ui.value.steps.forEach { (k, v) -> appendLine("$k: ${v.status} ${v.detail}") }
        _ui.value.noise?.let { appendLine("noise: %.3f °C".format(it)) }
        append(src.diagnostics().text)
    }

    companion object {
        const val RAW_SIZE = 32 * 8
        private const val COLLECT_MS = 6_000L
        private const val PERMISSION_WAIT_MS = 20_000L
        /** HT-102 delivers ~5–6.5 new thermal matrices per second in practice (docs/ht102-protocol.md, D22). */
        private const val MIN_HZ = 5f
    }
}
