package io.github.mpridal.thermocamera.ui.live

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mpridal.thermocamera.camera.CameraRepository
import io.github.mpridal.thermocamera.camera.CameraState
import io.github.mpridal.thermocamera.camera.PhoneCamera
import io.github.mpridal.thermocamera.camera.PhoneCameraInfo
import io.github.mpridal.thermocamera.camera.ThermalCameraSource
import io.github.mpridal.thermocamera.camera.VisibleFrame
import io.github.mpridal.thermocamera.core.AutoRange
import io.github.mpridal.thermocamera.core.Correction
import io.github.mpridal.thermocamera.core.CropSquare
import io.github.mpridal.thermocamera.core.FovMath
import io.github.mpridal.thermocamera.core.FrameRenderer
import io.github.mpridal.thermocamera.core.FrameSmoother
import io.github.mpridal.thermocamera.core.GrayImage
import io.github.mpridal.thermocamera.core.ImageRegistration
import io.github.mpridal.thermocamera.core.Interpolation
import io.github.mpridal.thermocamera.core.Measure
import io.github.mpridal.thermocamera.core.Measurements
import io.github.mpridal.thermocamera.core.Orientation
import io.github.mpridal.thermocamera.core.OverlayAlignment
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.core.RangePreset
import io.github.mpridal.thermocamera.core.TempRange
import io.github.mpridal.thermocamera.core.ThermalFrame
import io.github.mpridal.thermocamera.data.PhotoCamera
import io.github.mpridal.thermocamera.data.Settings
import io.github.mpridal.thermocamera.data.SettingsRepository
import io.github.mpridal.thermocamera.data.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    /** True when [visible] comes from the phone camera. */
    val visibleFromPhone: Boolean = false,
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
    private var orientedA = FloatArray(0)
    private var orientedB = FloatArray(0)
    private var lastCorrected: FloatArray? = null
    private var lastTs = 0L
    private var fpsEma = 0f
    private var frameNo = 0L

    // Latest module image (upright on screen) and phone image, for display and registration.
    @Volatile private var moduleLatest: Triple<IntArray, Int, Int>? = null
    @Volatile private var moduleCropLatest: CropSquare? = null
    @Volatile private var moduleBase: Triple<Int, Int, Int>? = null // base width, height, compensation

    /** While the alignment wizard is open the module image is shown even in phone-camera mode. */
    private val _wizardOpen = MutableStateFlow(false)
    fun setAlignmentWizardOpen(open: Boolean) { _wizardOpen.value = open }

    /** Crop of the module image for a candidate alignment (used live by the wizard). */
    fun moduleCropFor(a: OverlayAlignment): CropSquare? = moduleBase?.let { (w1, h1, comp) ->
        a.cropSquare(w1, h1).rotatedWithImage(comp, w1, h1)
    }
    @Volatile private var phoneLatest: Triple<IntArray, Int, Int>? = null
    private val moduleBufs = Array(3) { IntArray(0) }
    private val moduleTmp = Array(3) { IntArray(0) }
    private var moduleRing = 0

    private val _points = MutableStateFlow<List<UserPoint>>(emptyList())
    val points: StateFlow<List<UserPoint>> = _points.asStateFlow()

    private val _frozen = MutableStateFlow(false)
    val frozen: StateFlow<Boolean> = _frozen.asStateFlow()

    /** The camera streams only while the app is visible (set from MainActivity onStart/onStop). */
    private val foreground = MutableStateFlow(false)

    /** Display rotation in degrees (0/90/180/270), set from MainActivity. */
    @Volatile private var displayRotation = 0

    // ---- Phone camera (F11) ----
    private val phone = PhoneCamera(context)
    private val _phoneInfo = MutableStateFlow<PhoneCameraInfo?>(null)
    val phoneInfo: StateFlow<PhoneCameraInfo?> = _phoneInfo.asStateFlow()
    val phoneActive: StateFlow<Boolean> = phone.running
    @Volatile private var phoneCrop: CropSquare? = null
    private var registrationFailures = 0
    private val _alignTip = MutableStateFlow(false)
    val alignTip: StateFlow<Boolean> = _alignTip.asStateFlow()

    // ---- Torch (F7) ----
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val torchCameraId: String? = runCatching {
        cameraManager.cameraIdList.firstOrNull { id ->
            val ch = cameraManager.getCameraCharacteristics(id)
            ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()
    val torchAvailable: Boolean = torchCameraId != null
    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch.asStateFlow()

    /** Device identity for the "better camera" offer – offered again only on another device. */
    private val deviceKey = "${Build.MANUFACTURER} ${Build.MODEL}"

    /** Show the "Your phone has a better camera" sheet (F11 bod 2). */
    val showBetterCameraOffer: StateFlow<Boolean> = combine(settings, cameraState, _phoneInfo) { s, st, info ->
        st is CameraState.Streaming && cameras.active.value.kind == ThermalCameraSource.Kind.HT102 &&
            s.photoCamera == PhotoCamera.MODULE && s.betterCameraAnsweredFor != deviceKey &&
            info != null && info.effectiveMegapixels > MODULE_EFFECTIVE_MP
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setForeground(visible: Boolean) {
        foreground.value = visible
        // Torch goes off when leaving the app and comes back on return (F7).
        applyTorch(if (visible) _torch.value else false)
    }

    /** Surface.ROTATION_* of the display. */
    fun setDisplayRotation(surfaceRotation: Int) {
        displayRotation = surfaceRotation * 90
        phone.setTargetRotation(surfaceRotation)
        autoRange.reset(); smoother.reset()
    }

    init {
        viewModelScope.launch(Dispatchers.Default) { _phoneInfo.value = PhoneCameraInfo.best(context) }

        // Thermal camera stream.
        viewModelScope.launch {
            combine(cameras.active, foreground) { src, fg -> src.takeIf { fg } }.collectLatest { src ->
                if (src == null) return@collectLatest // previous source was stopped in its finally block
                autoRange.reset(); smoother.reset(); fpsEma = 0f; lastTs = 0L
                _frame.value = LiveFrameUi()
                src.start()
                try {
                    coroutineScope {
                        launch { src.frames.collect { processThermal(it, src.kind) } }
                        src.visibleFrames?.let { vf -> launch { vf.collect { processModuleVisible(it, src.kind) } } }
                    }
                } finally {
                    src.stop()
                }
            }
        }

        // Phone camera: runs while visible and chosen in settings (and the permission is granted).
        viewModelScope.launch {
            combine(foreground, settings) { fg, s -> fg && s.photoCamera != PhotoCamera.MODULE }
                .distinctUntilChanged()
                .collectLatest { wanted ->
                    val permitted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    if (wanted && permitted) {
                        if (!phone.start()) Log.w(TAG, "Phone camera did not start")
                        else if (_torch.value) applyTorch(true)
                    } else {
                        phone.stop()
                        phoneLatest = null
                        phoneCrop = null
                        _alignTip.value = false
                    }
                }
        }
        viewModelScope.launch {
            phone.frames.collect { processPhoneVisible(it) }
        }

        // Image registration ~2× per second (F11 bod 4).
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(REGISTRATION_PERIOD_MS)
                val s = settings.value
                if (!phone.running.value || !s.autoAlign || _frozen.value) continue
                val m = moduleLatest ?: continue
                val mc = moduleCropLatest ?: continue
                val p = phoneLatest ?: continue
                val res = runCatching {
                    ImageRegistration.register(GrayImage.fromArgb(m.first, m.second, m.third), GrayImage.fromArgb(p.first, p.second, p.third))
                }.getOrNull()
                if (res != null) {
                    registrationFailures = 0
                    _alignTip.value = false
                    phoneCrop = CropSquare(res.mapX(mc.cx), res.mapY(mc.cy), mc.side * res.scale, mc.angleDeg)
                } else if (++registrationFailures >= FAILURES_FOR_TIP) {
                    // Keep the last valid transformation, only show the tip.
                    _alignTip.value = true
                }
            }
        }
    }

    /** Rotation that keeps the module (fixed to the phone body) upright on a rotated display. */
    private fun compensation(kind: ThermalCameraSource.Kind): Int =
        if (kind == ThermalCameraSource.Kind.HT102) (360 - displayRotation) % 360 else 0

    private suspend fun processThermal(f: ThermalFrame, kind: ThermalCameraSource.Kind) = withContext(Dispatchers.Default) {
        if (_frozen.value) return@withContext
        val s = settings.value
        val user = s.orientation
        val comp = Orientation(compensation(kind))
        val w1 = user.outWidth(f.width, f.height); val h1 = user.outHeight(f.width, f.height)
        val w = comp.outWidth(w1, h1); val h = comp.outHeight(w1, h1)
        if (orientedA.size != f.celsius.size) { orientedA = FloatArray(f.celsius.size); orientedB = FloatArray(f.celsius.size) }
        user.apply(f.celsius, f.width, f.height, orientedA)
        comp.apply(orientedA, w1, h1, orientedB)
        if (corrected.size != orientedB.size) corrected = FloatArray(orientedB.size)
        Correction(emissivity = s.emissivity).applyTo(orientedB, corrected)
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

    /** Module camera frame: same orientation as the thermal sensor (it is fixed to it). */
    private suspend fun processModuleVisible(v: VisibleFrame, kind: ThermalCameraSource.Kind) = withContext(Dispatchers.Default) {
        if (_frozen.value) return@withContext
        val s = settings.value
        val user = s.orientation
        val comp = Orientation(compensation(kind))
        val n = v.width * v.height
        val i = moduleRing
        moduleRing = (moduleRing + 1) % moduleBufs.size
        if (moduleBufs[i].size != n) { moduleBufs[i] = IntArray(n); moduleTmp[i] = IntArray(n) }
        val w1 = user.outWidth(v.width, v.height); val h1 = user.outHeight(v.width, v.height)
        user.apply(v.argb, v.width, v.height, moduleTmp[i])
        comp.apply(moduleTmp[i], w1, h1, moduleBufs[i])
        val w = comp.outWidth(w1, h1); val h = comp.outHeight(w1, h1)
        val crop = s.moduleAlignment.cropSquare(w1, h1).rotatedWithImage(comp.rotation, w1, h1)
        moduleLatest = Triple(moduleBufs[i], w, h)
        moduleCropLatest = crop
        moduleBase = Triple(w1, h1, comp.rotation)
        if (s.photoCamera != PhotoCamera.PHONE || !phone.running.value || _wizardOpen.value) {
            val bmp = visibleBitmap(w, h).apply { setPixels(moduleBufs[i], 0, w, 0, 0, w, h) }
            _frame.update { it.copy(visible = bmp.asImageBitmap(), visibleCrop = crop, visibleFromPhone = false) }
        }
    }

    private suspend fun processPhoneVisible(v: VisibleFrame) = withContext(Dispatchers.Default) {
        if (_frozen.value) return@withContext
        phoneLatest = Triple(v.argb, v.width, v.height)
        if (settings.value.photoCamera != PhotoCamera.PHONE || _wizardOpen.value) return@withContext
        val crop = phoneCrop ?: _phoneInfo.value?.let { info ->
            // Before registration succeeds: centred square from the FOV ratio (short side ↔ vertical FOV).
            val side = FovMath.pixelsInThermal(minOf(v.width, v.height), info.vfovDeg)
            CropSquare(v.width / 2f, v.height / 2f, side)
        }
        val bmp = visibleBitmap(v.width, v.height).apply { setPixels(v.argb, 0, v.width, 0, 0, v.width, v.height) }
        _frame.update { it.copy(visible = bmp.asImageBitmap(), visibleCrop = crop, visibleFromPhone = true) }
    }

    private fun visibleBitmap(w: Int, h: Int): Bitmap {
        val ring = visibleBitmaps?.takeIf { it.width == w && it.height == h } ?: BitmapRing(w, h).also { visibleBitmaps = it }
        return ring.next()
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

    // ---- Torch ----
    fun toggleTorch() {
        _torch.value = !_torch.value
        applyTorch(_torch.value)
    }

    private fun applyTorch(on: Boolean) {
        if (phone.running.value) {
            phone.enableTorch(on)
        } else {
            torchCameraId?.let { id -> runCatching { cameraManager.setTorchMode(id, on) } }
        }
    }

    // ---- Photos (F11 bod 3) ----
    /** Takes a full-resolution phone photo; returns megapixels, or null if the phone camera is not active. */
    suspend fun takePhonePhoto(): Float? {
        if (!phone.running.value) return null
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val size = phone.takePhoto(File(dir, "photo-${System.currentTimeMillis()}.jpg")) ?: return null
        return size.width * size.height / 1e6f
    }

    fun answerBetterCamera() = viewModelScope.launch { settingsRepo.setBetterCameraAnsweredFor(deviceKey) }

    fun setViewMode(m: ViewMode) = viewModelScope.launch { settingsRepo.setViewMode(m) }
    fun setBlend(b: Float) = viewModelScope.launch { settingsRepo.setBlend(b) }
    fun setPalette(p: Palette) = viewModelScope.launch { settingsRepo.setPalette(p) }
    fun setRangePreset(r: RangePreset) = viewModelScope.launch {
        autoRange.reset()
        settingsRepo.setRangePreset(r)
    }
    fun useSimulator() = viewModelScope.launch { settingsRepo.setUseSimulator(true) }
    fun setPhotoCamera(p: PhotoCamera) = viewModelScope.launch { settingsRepo.setPhotoCamera(p) }
    fun setModuleAlignment(a: OverlayAlignment) = viewModelScope.launch { settingsRepo.setModuleAlignment(a) }
    fun setOrientation(o: Orientation) = viewModelScope.launch {
        autoRange.reset(); smoother.reset()
        settingsRepo.setOrientation(o)
    }

    override fun onCleared() {
        applyTorch(false)
    }

    /** Three bitmaps in rotation: one shown, one possibly still drawn, one being written. */
    private class BitmapRing(val width: Int, val height: Int) {
        private val items = Array(3) { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
        private var i = 0
        fun next(): Bitmap = items[i].also { i = (i + 1) % items.size }
    }

    companion object {
        private const val TAG = "LiveViewModel"
        const val DISPLAY_SIZE = 320
        const val BG_SIZE = 8
        const val MAX_POINTS = 5
        private const val REGISTRATION_PERIOD_MS = 500L
        private const val FAILURES_FOR_TIP = 6

        /** Module camera inside the 43° square: 480 × 480 px (default alignment) ≈ 0.23 Mpx. */
        const val MODULE_EFFECTIVE_MP = 0.23f
    }
}
