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
import io.github.mpridal.thermocamera.core.CropSquare
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

/** User measuring point in thermal-grid pixel-centre coordinates (0..31). */
data class UserPoint(val x: Float, val y: Float)

data class LiveFrameUi(
    /** Increments with every rendered frame so Compose redraws the reused bitmaps. */
    val frameNo: Long = 0,
    val thermal: ImageBitmap? = null,
    val background: ImageBitmap? = null,
    val visible: ImageBitmap? = null,
    val measurements: Measurements? = null,
    val range: TempRange? = null,
    val fps: Float = 0f,
    /** Size of the (oriented) thermal grid – points and markers are in this coordinate system. */
    val gridW: Int = 32,
    val gridH: Int = 32,
    /** Where the thermal square lies in [visible] (zarovnání překryvu). */
    val visibleCrop: CropSquare? = null,
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
    private var oriented = FloatArray(0)
    private var lastCorrected: FloatArray? = null
    private var visibleOriented: Array<IntArray>? = null
    private var visibleRing = 0

    private val _points = MutableStateFlow<List<UserPoint>>(emptyList())
    val points: StateFlow<List<UserPoint>> = _points.asStateFlow()

    private val _frozen = MutableStateFlow(false)
    val frozen: StateFlow<Boolean> = _frozen.asStateFlow()
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
        if (_frozen.value) return@withContext
        val s = settings.value
        val o = s.orientation
        val w = o.outWidth(f.width, f.height)
        val h = o.outHeight(f.width, f.height)
        if (oriented.size != f.celsius.size) oriented = FloatArray(f.celsius.size)
        o.apply(f.celsius, f.width, f.height, oriented)
        if (corrected.size != oriented.size) corrected = FloatArray(oriented.size)
        Correction(emissivity = s.emissivity).applyTo(oriented, corrected)
        lastCorrected = corrected.copyOf()
        val m = Measure.of(corrected, w, h, _points.value.map { it.x to it.y })
        val display = smoother.smooth(corrected)
        val range = s.rangePreset.range ?: autoRange.update(display)
        val px = renderer.render(display, w, h, range, s.palette, Interpolation.Mode.BICUBIC)
        val bmp = thermalBitmaps.next().apply { setPixels(px, 0, DISPLAY_SIZE, 0, 0, DISPLAY_SIZE, DISPLAY_SIZE) }
        val bgPx = bgRenderer.render(display, w, h, range, s.palette, Interpolation.Mode.BICUBIC)
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
                gridW = w,
                gridH = h,
            )
        }
    }

    private suspend fun processVisible(v: VisibleFrame) = withContext(Dispatchers.Default) {
        if (_frozen.value) return@withContext
        val s = settings.value
        // The module camera is fixed to the thermal sensor: same orientation.
        val o = s.orientation
        val w = o.outWidth(v.width, v.height)
        val h = o.outHeight(v.width, v.height)
        val bufs = visibleOriented?.takeIf { it[0].size == v.width * v.height }
            ?: Array(3) { IntArray(v.width * v.height) }.also { visibleOriented = it }
        val argb = bufs[visibleRing].also { visibleRing = (visibleRing + 1) % bufs.size }
        o.apply(v.argb, v.width, v.height, argb)
        val ring = visibleBitmaps?.takeIf { it.width == w && it.height == h }
            ?: BitmapRing(w, h).also { visibleBitmaps = it }
        val bmp = ring.next().apply { setPixels(argb, 0, w, 0, 0, w, h) }
        val crop = s.moduleAlignment.cropSquare(w, h)
        _frame.update { it.copy(visible = bmp.asImageBitmap(), visibleCrop = crop) }
    }

    /** Recomputes measurements for the (possibly frozen) last frame after points changed. */
    private fun remeasure() {
        val c = lastCorrected ?: return
        val f = _frame.value
        val m = Measure.of(c, f.gridW, f.gridH, _points.value.map { it.x to it.y })
        _frame.update { it.copy(measurements = m) }
    }

    fun addPoint(x: Float, y: Float) {
        if (_points.value.size >= MAX_POINTS) return
        _points.update { it + UserPoint(x, y) }
        remeasure()
    }

    fun movePoint(index: Int, x: Float, y: Float) {
        _points.update { list -> list.mapIndexed { i, p -> if (i == index) UserPoint(x, y) else p } }
        remeasure()
    }

    fun removePoint(index: Int) {
        _points.update { list -> list.filterIndexed { i, _ -> i != index } }
        remeasure()
    }

    fun setFrozen(v: Boolean) {
        _frozen.value = v
    }

    fun setPhotoCamera(p: io.github.mpridal.thermocamera.data.PhotoCamera) = viewModelScope.launch {
        settingsRepo.setPhotoCamera(p)
    }

    fun setModuleAlignment(a: io.github.mpridal.thermocamera.core.OverlayAlignment) = viewModelScope.launch {
        settingsRepo.setModuleAlignment(a)
    }

    fun setOrientation(o: io.github.mpridal.thermocamera.core.Orientation) = viewModelScope.launch {
        autoRange.reset(); smoother.reset()
        settingsRepo.setOrientation(o)
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
        const val MAX_POINTS = 5
    }
}
