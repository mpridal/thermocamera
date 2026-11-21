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
import io.github.mpridal.thermocamera.core.ThermalBridge
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
    /** Temperature factor of the MIN spot (zadání F3), null when θi ≤ θe. */
    val fRsi: Float? = null,
    /** fRsi of each user point, same order as the points. */
    val pointsFRsi: List<Float?> = emptyList(),
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameras: CameraRepository,
    private val settingsRepo: SettingsRepository,
    private val snapshots: io.github.mpridal.thermocamera.snapshot.SnapshotStore,
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

    /** Megapixels of photos from the phone camera while it runs (badge "foto … Mpx"). */
    val photoMegapixels: StateFlow<Float?> = combine(phone.captureSize, _phoneInfoForBadge()) { size, info ->
        size?.let { it.width * it.height / 1e6f } ?: info
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
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

    /** HT-102 plugged in but the CAMERA permission (required by Android for USB cameras) is missing. */
    val needsCameraForUsb: StateFlow<Boolean> = cameras.ht102.needsCameraPermission

    fun onCameraPermissionResult() = cameras.ht102.onCameraPermissionResult()

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
                        if (!phone.start()) {
                            Log.w(TAG, "Phone camera did not start")
                        } else {
                            if (_torch.value) applyTorch(true)
                            // With the CAMERA permission all lens data is available – refresh the FOV estimate.
                            launch(Dispatchers.Default) { PhoneCameraInfo.best(context)?.let { _phoneInfo.value = it } }
                        }
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
        // Reference for calibration: emissivity-corrected centre, before offset/gain (zadání F2).
        val emOnly = Correction(s.emissivity, s.reflected)
        val c0 = (f[15, 15] + f[16, 15] + f[15, 16] + f[16, 16]) / 4f
        recordCenterSample(f.timestampNanos, emOnly.apply(c0))
        lastRaw = Triple(orientedB.copyOf(), w, h)
        s.correction().applyTo(orientedB, corrected)
        lastCorrected = corrected.copyOf()
        val m = Measure.of(corrected, w, h, _points.value.map { it.x to it.y })
        val display = if (s.smoothing) smoother.smooth(corrected) else corrected
        val autoR = autoRange.update(display)
        lastAutoRange = autoR
        val range = s.fixedRange() ?: autoR
        val isoBelow = if (s.isotherm && s.insideTemp > s.outsideTemp) {
            ThermalBridge.isothermThreshold(s.insideTemp, s.outsideTemp, s.fRsiLimit)
        } else null
        val mode = if (s.pixelated) Interpolation.Mode.NEAREST else Interpolation.Mode.BICUBIC
        val px = renderer.render(display, w, h, range, s.palette, mode, isoBelow)
        val bmp = thermalBitmaps.next().apply { setPixels(px, 0, DISPLAY_SIZE, 0, 0, DISPLAY_SIZE, DISPLAY_SIZE) }
        val bgPx = bgRenderer.render(display, w, h, range, s.palette, Interpolation.Mode.BICUBIC)
        val fRsi = ThermalBridge.fRsi(m.min.celsius, s.insideTemp, s.outsideTemp)
        val pointsFRsi = m.points.map { ThermalBridge.fRsi(it.celsius, s.insideTemp, s.outsideTemp) }
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
                fRsi = fRsi,
                pointsFRsi = pointsFRsi,
            )
        }
    }

    // ---- Snapshots (zadání F4) ----
    @Volatile private var lastRaw: Triple<FloatArray, Int, Int>? = null

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /**
     * Saves a measurement snapshot: raw matrix, the normal image, a full phone photo when the phone
     * camera is active, the rendered JPG and the metadata. Returns the new id, or null without data.
     */
    suspend fun takeSnapshot(): Long? {
        if (_saving.value) return null
        val (raw, w, h) = lastRaw ?: return null
        _saving.value = true
        try {
            val s = settings.value
            val f = _frame.value
            val fromPhone = f.visibleFromPhone
            val vis = (if (fromPhone) phoneLatest else moduleLatest)?.let { (argb, vw, vh) ->
                Bitmap.createBitmap(argb.copyOf(vw * vh), vw, vh, Bitmap.Config.ARGB_8888)
            }
            val visCrop = f.visibleCrop
            val dir = snapshots.newDir()
            // Full-resolution phone photo (F11 bod 5) with the thermal square scaled to it.
            var photoCrop: CropSquare? = null
            if (phone.running.value && s.photoCamera != PhotoCamera.MODULE) {
                val preview = phoneLatest
                val size = phone.takePhoto(File(dir, io.github.mpridal.thermocamera.snapshot.SnapshotStore.PHOTO))
                if (size != null && preview != null) {
                    val k = maxOf(size.width, size.height).toFloat() / maxOf(preview.second, preview.third)
                    val pc = phoneCrop ?: _phoneInfo.value?.let { info ->
                        CropSquare(preview.second / 2f, preview.third / 2f, FovMath.pixelsInThermal(minOf(preview.second, preview.third), info.vfovDeg))
                    }
                    photoCrop = pc?.let { CropSquare(it.cx * k, it.cy * k, it.side * k, it.angleDeg) }
                }
            }
            val now = System.currentTimeMillis()
            val pts = _points.value.map { it.x to it.y }
            val locale = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()[0] ?: java.util.Locale.getDefault()
            val rangeName = context.getString(rangeNameRes(s.rangePreset))
            val input = io.github.mpridal.thermocamera.snapshot.RenderInput(
                raw = raw, width = w, height = h, visible = vis, visibleCrop = visCrop,
                viewMode = s.viewMode, blend = s.blend, palette = s.palette,
                range = s.fixedRange() ?: f.range, correction = s.correction(), points = pts,
                insideTemp = s.insideTemp, outsideTemp = s.outsideTemp, fRsiLimit = s.fRsiLimit, isotherm = s.isotherm,
                rangeName = rangeName, createdAt = now,
            )
            return withContext(Dispatchers.Default) {
                val result = io.github.mpridal.thermocamera.snapshot.SnapshotRenderer(context, locale).render(input)
                val entity = io.github.mpridal.thermocamera.data.db.SnapshotEntity(
                    createdAt = now, projectId = s.activeProjectId.takeIf { it >= 0 }, dir = dir.name, title = "", note = "",
                    viewMode = s.viewMode.name, blend = s.blend, palette = s.palette.name, rangePreset = s.rangePreset.name,
                    rangeMin = result.range.min, rangeMax = result.range.max, emissivity = s.emissivity, reflected = s.reflected,
                    offset = s.calOffset, gain = s.calGain, insideTemp = s.insideTemp, outsideTemp = s.outsideTemp,
                    fRsiLimit = s.fRsiLimit, points = pts.joinToString(";") { "${it.first},${it.second}" },
                    orientationRotation = s.orientation.rotation, orientationMirror = s.orientation.mirror,
                    cameraSource = if (fromPhone || photoCrop != null) "PHONE" else "MODULE",
                    appVersion = io.github.mpridal.thermocamera.BuildConfig.VERSION_NAME,
                    minC = result.measurements.min.celsius, maxC = result.measurements.max.celsius, centerC = result.measurements.center,
                    hasVisible = vis != null, hasPhoto = photoCrop != null,
                )
                val id = snapshots.save(dir, entity, raw, w, h, result.bitmap, vis, visCrop, photoCrop, s.saveToGallery)
                result.bitmap.recycle()
                id
            }
        } catch (e: Exception) {
            Log.w(TAG, "Snapshot failed", e)
            return null
        } finally {
            _saving.value = false
        }
    }

    // ---- Calibration (zadání F2) ----
    private val centerSamples = ArrayDeque<Pair<Long, Float>>()
    @Volatile private var lastAutoRange: TempRange? = null

    @Synchronized
    private fun recordCenterSample(ts: Long, v: Float) {
        centerSamples.addLast(ts to v)
        while (centerSamples.size > 200) centerSamples.removeFirst()
    }

    @Synchronized
    private fun samplesSince(ts: Long): List<Float> = centerSamples.filter { it.first >= ts }.map { it.second }

    /**
     * Averages the emissivity-corrected centre (2 × 2) over [durationMs] (≈ 18 frames at 9 Hz) –
     * the "measured" value of a reference for 1- and 2-point calibration. Null without data.
     */
    suspend fun measureReference(durationMs: Long = 2_000): Float? {
        val start = System.nanoTime()
        delay(durationMs)
        val v = samplesSince(start)
        return if (v.size >= 3) v.average().toFloat() else null
    }

    /** Freezes the current automatic range (lock) or releases it. */
    fun setRangeLocked(locked: Boolean) = viewModelScope.launch {
        val r = lastAutoRange ?: _frame.value.range
        if (locked && r != null) settingsRepo.setRangeLocked(true, r.min, r.max) else settingsRepo.setRangeLocked(false)
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
        settingsRepo.setRangeLocked(false)
        settingsRepo.setActiveProfile("")
    }
    fun useSimulator() = viewModelScope.launch { settingsRepo.setUseSimulator(true) }
    fun setPhotoCamera(p: PhotoCamera) = viewModelScope.launch { settingsRepo.setPhotoCamera(p) }
    fun setModuleAlignment(a: OverlayAlignment) = viewModelScope.launch { settingsRepo.setModuleAlignment(a) }
    fun setOrientation(o: Orientation) = viewModelScope.launch {
        autoRange.reset(); smoother.reset()
        settingsRepo.setOrientation(o)
    }

    private fun _phoneInfoForBadge() = combine(_phoneInfo, phone.running) { info, running -> if (running) info?.megapixels else null }

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
        fun rangeNameRes(p: RangePreset): Int = when (p) {
            RangePreset.AUTO -> io.github.mpridal.thermocamera.R.string.range_auto
            RangePreset.INTERIOR -> io.github.mpridal.thermocamera.R.string.range_interior
            RangePreset.FACADE -> io.github.mpridal.thermocamera.R.string.range_facade
            RangePreset.HEATING -> io.github.mpridal.thermocamera.R.string.range_heating
            RangePreset.FULL -> io.github.mpridal.thermocamera.R.string.range_full
            RangePreset.CUSTOM -> io.github.mpridal.thermocamera.R.string.range_custom
        }

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
