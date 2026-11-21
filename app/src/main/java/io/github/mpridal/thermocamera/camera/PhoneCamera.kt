package io.github.mpridal.thermocamera.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.mpridal.thermocamera.core.FovMath
import io.github.mpridal.thermocamera.core.Orientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** What the phone's best rear camera offers inside the thermal field of view (zadání F11 bod 1). */
data class PhoneCameraInfo(
    val cameraId: String,
    val jpegWidth: Int,
    val jpegHeight: Int,
    val hfovDeg: Float,
    val vfovDeg: Float,
    val hasFlash: Boolean,
) {
    val megapixels: Float get() = jpegWidth * jpegHeight / 1e6f

    /** Megapixels that fall inside the 43° × 43° thermal field of view. */
    val effectiveMegapixels: Float get() = FovMath.effectivePixels(jpegWidth, jpegHeight, hfovDeg, vfovDeg) / 1e6f

    companion object {
        /** The rear camera whose field of view covers the thermal one with the most pixels in it. */
        fun best(context: Context): PhoneCameraInfo? {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            return runCatching {
                cm.cameraIdList.mapNotNull { id ->
                    val ch = cm.getCameraCharacteristics(id)
                    if (ch.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) return@mapNotNull null
                    val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return@mapNotNull null
                    val jpeg = map.getOutputSizes(android.graphics.ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
                        ?: return@mapNotNull null
                    val sensor = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return@mapNotNull null
                    val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: return@mapNotNull null
                    // Sensor is landscape; the app shows the image in landscape 4:3 too.
                    val w = maxOf(jpeg.width, jpeg.height)
                    val h = minOf(jpeg.width, jpeg.height)
                    PhoneCameraInfo(
                        cameraId = id,
                        jpegWidth = w,
                        jpegHeight = h,
                        hfovDeg = FovMath.fovDeg(maxOf(sensor.width, sensor.height), focal),
                        vfovDeg = FovMath.fovDeg(minOf(sensor.width, sensor.height), focal),
                        hasFlash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                    )
                }.filter { it.hfovDeg >= FovMath.THERMAL_FOV_DEG && it.vfovDeg >= FovMath.THERMAL_FOV_DEG * 0.9f }
                    .maxByOrNull { it.effectiveMegapixels }
            }.getOrNull()
        }
    }
}

/**
 * Phone rear camera via CameraX (zadání F11 bod 3): a stream of upright landscape frames
 * (~960 × 720) for the preview and image registration, and full-resolution photos.
 * Uses its own lifecycle so the view model can start and stop it.
 */
class PhoneCamera(private val context: Context) : LifecycleOwner {

    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var capture: ImageCapture? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var targetRotation = android.view.Surface.ROTATION_0

    private val _frames = MutableSharedFlow<VisibleFrame>(extraBufferCapacity = 1)
    val frames: SharedFlow<VisibleFrame> = _frames.asSharedFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val ring = Array(3) { IntArray(0) }
    private var ringIdx = 0
    private var rgba = ByteArray(0)
    private var lastFrameNs = 0L

    /** Starts preview frames and photo capture. Must be called on the main thread. */
    suspend fun start(): Boolean = withContext(Dispatchers.Main) {
        if (_running.value) return@withContext true
        val provider = runCatching { awaitProvider() }.getOrElse {
            Log.w(TAG, "No camera provider", it); return@withContext false
        }
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(960, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(selector)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(targetRotation)
            .build()
            .also { it.setAnalyzer(analysisExecutor, ::onImage) }
        val photo = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
            .setTargetRotation(targetRotation)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build(),
            )
            .build()
        return@withContext runCatching {
            provider.unbindAll()
            registry.currentState = Lifecycle.State.RESUMED
            camera = provider.bindToLifecycle(this@PhoneCamera, CameraSelector.DEFAULT_BACK_CAMERA, analysis, photo)
            capture = photo
            analysisUseCase = analysis
            _running.value = true
            true
        }.getOrElse {
            Log.w(TAG, "Cannot bind camera", it)
            registry.currentState = Lifecycle.State.CREATED
            false
        }
    }

    suspend fun stop() = withContext(Dispatchers.Main) {
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) registry.currentState = Lifecycle.State.CREATED
        runCatching { awaitProvider().unbindAll() }
        camera = null
        capture = null
        _running.value = false
    }

    val hasFlash: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    /** Display rotation (Surface.ROTATION_*) so that frames and photos come out upright. */
    fun setTargetRotation(surfaceRotation: Int) {
        targetRotation = surfaceRotation
        analysisUseCase?.targetRotation = surfaceRotation
        capture?.targetRotation = surfaceRotation
    }

    fun enableTorch(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
    }

    /** Takes a full-resolution JPEG into [file]; returns its size or null on failure. */
    suspend fun takePhoto(file: File): Size? {
        val c = capture ?: return null
        return suspendCancellableCoroutine { cont ->
            c.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(file.path, opts)
                        cont.resume(Size(opts.outWidth, opts.outHeight))
                    }

                    override fun onError(e: ImageCaptureException) {
                        Log.w(TAG, "Photo failed", e)
                        cont.resume(null)
                    }
                },
            )
        }
    }

    private fun onImage(image: ImageProxy) {
        image.use { img ->
            val now = System.nanoTime()
            if (now - lastFrameNs < MIN_FRAME_NS) return // ~10 fps is plenty for preview and registration
            lastFrameNs = now
            val w = img.width
            val h = img.height
            val plane = img.planes[0]
            val buf = plane.buffer
            val rowStride = plane.rowStride
            if (rgba.size < rowStride * h) rgba = ByteArray(rowStride * h)
            buf.rewind()
            buf.get(rgba, 0, minOf(buf.remaining(), rgba.size))
            val raw = IntArray(w * h)
            for (y in 0 until h) {
                var s = y * rowStride
                val o = y * w
                for (x in 0 until w) {
                    val r = rgba[s].toInt() and 0xFF
                    val g = rgba[s + 1].toInt() and 0xFF
                    val b = rgba[s + 2].toInt() and 0xFF
                    raw[o + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    s += 4
                }
            }
            // Make the frame upright as the user holds the phone.
            val o = Orientation(((img.imageInfo.rotationDegrees % 360) + 360) % 360)
            val ow = o.outWidth(w, h)
            val oh = o.outHeight(w, h)
            if (ring[ringIdx].size != w * h) ring[ringIdx] = IntArray(w * h)
            val out = ring[ringIdx]
            ringIdx = (ringIdx + 1) % ring.size
            o.apply(raw, w, h, out)
            _frames.tryEmit(VisibleFrame(ow, oh, out, now))
        }
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val f = ProcessCameraProvider.getInstance(context)
        f.addListener({ cont.resume(f.get()) }, ContextCompat.getMainExecutor(context))
    }

    companion object {
        private const val TAG = "PhoneCamera"
        private const val MIN_FRAME_NS = 90_000_000L
    }
}
