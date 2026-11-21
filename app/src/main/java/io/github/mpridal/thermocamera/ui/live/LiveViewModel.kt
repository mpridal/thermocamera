package io.github.mpridal.thermocamera.ui.live

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mpridal.thermocamera.camera.CameraRepository
import io.github.mpridal.thermocamera.camera.CameraState
import io.github.mpridal.thermocamera.camera.ThermalCameraSource
import io.github.mpridal.thermocamera.camera.VisibleFrame
import io.github.mpridal.thermocamera.core.AutoRange
import io.github.mpridal.thermocamera.core.Correction
import io.github.mpridal.thermocamera.core.FrameRenderer
import io.github.mpridal.thermocamera.core.FrameSmoother
import io.github.mpridal.thermocamera.core.Interpolation
import io.github.mpridal.thermocamera.core.Measure
import io.github.mpridal.thermocamera.core.Measurements
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.core.RangePreset
import io.github.mpridal.thermocamera.core.TempRange
import io.github.mpridal.thermocamera.core.ThermalFrame
import io.github.mpridal.thermocamera.data.Settings
import io.github.mpridal.thermocamera.data.SettingsRepository
import io.github.mpridal.thermocamera.data.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LiveFrameUi(
    /** Increments with every rendered frame so Compose redraws the reused bitmaps. */
    val frameNo: Long = 0,
    val thermal: ImageBitmap? = null,
    val background: ImageBitmap? = null,
    val visible: ImageBitmap? = null,
    val measurements: Measurements? = null,
    val range: TempRange? = null,
    val fps: Float = 0f,
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val cameras: CameraRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val source: StateFlow<ThermalCameraSource> = cameras.active

    @OptIn(ExperimentalCoroutinesApi::class)
    val cameraState: StateFlow<CameraState> = cameras.active
        .flatMapLatest { it.state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CameraState.Disconnected)

    private val _frame = MutableStateFlow(LiveFrameUi())
    val frame: StateFlow<LiveFrameUi> = _frame.asStateFlow()

    private val renderer = FrameRenderer(DISPLAY_SIZE, DISPLAY_SIZE)
    private val bgRenderer = FrameRenderer(BG_SIZE, BG_SIZE)
    private val thermalBitmaps = BitmapRing(DISPLAY_SIZE, DISPLAY_SIZE)
    private val bgBitmaps = BitmapRing(BG_SIZE, BG_SIZE)
    private var visibleBitmaps: BitmapRing? = null
    private val autoRange = AutoRange()
    private val smoother = FrameSmoother(0.5f)
    private var corrected = FloatArray(0)
    private var lastTs = 0L
    private var fpsEma = 0f
    private var frameNo = 0L

    /** The camera streams only while the app is visible (set from MainActivity onStart/onStop). */
    private val foreground = MutableStateFlow(false)

    fun setForeground(visible: Boolean) {
        foreground.value = visible
    }

    init {
        viewModelScope.launch {
            combine(cameras.active, foreground) { src, fg -> src.takeIf { fg } }.collectLatest { src ->
                if (src == null) return@collectLatest // previous source was stopped in its finally block
                autoRange.reset(); smoother.reset(); fpsEma = 0f; lastTs = 0L
                _frame.value = LiveFrameUi()
                src.start()
                try {
                    coroutineScope {
                        launch { src.frames.collect { processThermal(it) } }
                        src.visibleFrames?.let { vf -> launch { vf.collect { processVisible(it) } } }
                    }
                } finally {
                    src.stop()
                }
            }
        }
    }

    private suspend fun processThermal(f: ThermalFrame) = withContext(Dispatchers.Default) {
        val s = settings.value
        if (corrected.size != f.celsius.size) corrected = FloatArray(f.celsius.size)
        Correction(emissivity = s.emissivity).applyTo(f.celsius, corrected)
        val m = Measure.of(corrected, f.width, f.height)
        val display = smoother.smooth(corrected)
        val range = s.rangePreset.range ?: autoRange.update(display)
        val px = renderer.render(display, f.width, f.height, range, s.palette, Interpolation.Mode.BICUBIC)
        val bmp = thermalBitmaps.next().apply { setPixels(px, 0, DISPLAY_SIZE, 0, 0, DISPLAY_SIZE, DISPLAY_SIZE) }
        val bgPx = bgRenderer.render(display, f.width, f.height, range, s.palette, Interpolation.Mode.BICUBIC)
        val bg = bgBitmaps.next().apply { setPixels(bgPx, 0, BG_SIZE, 0, 0, BG_SIZE, BG_SIZE) }
        if (lastTs != 0L) {
            val inst = 1e9f / (f.timestampNanos - lastTs).coerceAtLeast(1L)
            fpsEma = if (fpsEma == 0f) inst else fpsEma + 0.2f * (inst - fpsEma)
        }
        lastTs = f.timestampNanos
        frameNo++
        _frame.update {
            it.copy(
                frameNo = frameNo,
                thermal = bmp.asImageBitmap(),
                background = bg.asImageBitmap(),
                measurements = m,
                range = range,
                fps = fpsEma,
            )
        }
    }

    private suspend fun processVisible(v: VisibleFrame) = withContext(Dispatchers.Default) {
        val ring = visibleBitmaps?.takeIf { it.width == v.width && it.height == v.height }
            ?: BitmapRing(v.width, v.height).also { visibleBitmaps = it }
        val bmp = ring.next().apply { setPixels(v.argb, 0, v.width, 0, 0, v.width, v.height) }
        _frame.update { it.copy(visible = bmp.asImageBitmap()) }
    }

    fun setViewMode(m: ViewMode) = viewModelScope.launch { settingsRepo.setViewMode(m) }
    fun setBlend(b: Float) = viewModelScope.launch { settingsRepo.setBlend(b) }
    fun setPalette(p: Palette) = viewModelScope.launch { settingsRepo.setPalette(p) }
    fun setRangePreset(r: RangePreset) = viewModelScope.launch {
        autoRange.reset()
        settingsRepo.setRangePreset(r)
    }
    fun useSimulator() = viewModelScope.launch { settingsRepo.setUseSimulator(true) }

    /** Three bitmaps in rotation: one shown, one possibly still drawn, one being written. */
    private class BitmapRing(val width: Int, val height: Int) {
        private val items = Array(3) { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
        private var i = 0
        fun next(): Bitmap = items[i].also { i = (i + 1) % items.size }
    }

    companion object {
        const val DISPLAY_SIZE = 320
        const val BG_SIZE = 8
    }
}
