package io.github.mpridal.thermocamera.camera

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.mpridal.thermocamera.core.Ht102Decoder
import io.github.mpridal.thermocamera.core.ThermalFrame
import io.github.mpridal.thermocamera.core.UvcFrameAssembler
import io.github.mpridal.thermocamera.core.Yuy2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hti HT-102 (zadání kap. 3, F6): plain UVC stream YUY2 640 × 480; the 32 × 32 thermal matrix is
 * decoded from the chroma LSBs of every frame ([Ht102Decoder]). The video frame itself is the
 * visible image of the module camera.
 *
 * States: Disconnected → NeedsPermission → Connecting → Streaming(fps) / Error.
 * Watchdog: no thermal frame for [WATCHDOG_NS] → Error(NO_DATA), recovers automatically.
 */
class Ht102Source(private val context: Context) : ThermalCameraSource {

    override val kind = ThermalCameraSource.Kind.HT102

    private val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var streamJob: Job? = null
    @Volatile private var streamingDevice: String? = null

    private val _state = MutableStateFlow<CameraState>(CameraState.Disconnected)
    override val state: StateFlow<CameraState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<ThermalFrame>(extraBufferCapacity = 2)
    override val frames: SharedFlow<ThermalFrame> = _frames.asSharedFlow()

    private val _visible = MutableSharedFlow<VisibleFrame>(extraBufferCapacity = 1)
    override val visibleFrames: SharedFlow<VisibleFrame> = _visible.asSharedFlow()

    /** Diagnostics of the last session, shown in the camera test and exported. */
    @Volatile var lastNegotiation: String = ""
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var stats = Stats()
        private set

    data class Stats(
        val videoFrames: Long = 0,
        val dropped: Long = 0,
        val thermalFrames: Long = 0,
        val maxHeader: Int = 0,
        /** Measured video frames per second (all complete frames). */
        val videoFps: Float = 0f,
    )

    /** Requested video frame rate; changing it restarts the stream. */
    @Volatile var requestedFps: Int = 30
        private set

    fun setRequestedFps(fps: Int) {
        if (fps == requestedFps) return
        requestedFps = fps
        restartStream()
    }

    /** Requested video resolution as (UVC frame index, width, height); changing it restarts the stream. */
    @Volatile var requestedFrame: Triple<Int, Int, Int> = Triple(2, 320, 240)
        private set

    fun setRequestedFrame(frameIndex: Int, width: Int, height: Int) {
        val f = Triple(frameIndex, width, height)
        if (f == requestedFrame) return
        requestedFrame = f
        restartStream()
    }

    /** Stops the current stream so that [refresh] opens it again with the new parameters. */
    private fun restartStream() {
        scope.launch {
            lock.withLock { streamJob?.cancelAndJoin(); streamJob = null }
            refresh()
        }
    }

    private var registered = false
    private var permissionAsked = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == ACTION_PERMISSION && !intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                _state.value = CameraState.NeedsPermission
                return
            }
            refresh()
        }
    }

    override suspend fun start() {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(ACTION_PERMISSION)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        permissionAsked = false
        refresh()
    }

    override suspend fun stop() {
        if (registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
        lock.withLock { streamJob?.cancelAndJoin(); streamJob = null }
        _state.value = CameraState.Disconnected
    }

    fun findCamera(): UsbDevice? = usb.deviceList.values.firstOrNull { isHt102(it) }

    /** Re-evaluates the USB situation: attach, detach, permission granted. Safe to call any time. */
    fun refresh() {
        scope.launch {
            lock.withLock {
                val dev = findCamera()
                when {
                    dev == null -> {
                        _needsCameraPermission.value = false
                        streamJob?.cancelAndJoin(); streamJob = null
                        _state.value = CameraState.Disconnected
                    }
                    // Android 10+ grants USB access to a UVC camera only to apps holding the CAMERA
                    // permission; without it the USB dialog is silently ignored. Ask for CAMERA first.
                    !hasCameraPermission() -> {
                        _state.value = CameraState.NeedsPermission
                        _needsCameraPermission.value = true
                    }
                    !usb.hasPermission(dev) -> {
                        _needsCameraPermission.value = false
                        _state.value = CameraState.NeedsPermission
                        if (!permissionAsked) {
                            permissionAsked = true
                            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                            val pi = PendingIntent.getBroadcast(context, 1, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags)
                            usb.requestPermission(dev, pi)
                        }
                    }
                    streamJob?.isActive == true && streamingDevice == dev.deviceName -> Unit
                    else -> {
                        streamJob?.cancelAndJoin()
                        streamJob = scope.launch(Dispatchers.IO) { runStream(dev) }
                    }
                }
            }
        }
    }

    private val _needsCameraPermission = MutableStateFlow(false)

    /** True when the HT-102 is plugged in but the app still lacks the CAMERA runtime permission. */
    val needsCameraPermission: StateFlow<Boolean> = _needsCameraPermission.asStateFlow()

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Called after the CAMERA permission dialog was answered. */
    fun onCameraPermissionResult() {
        _needsCameraPermission.value = !hasCameraPermission()
        permissionAsked = false
        refresh()
    }

    /** Asks for USB permission again (button on the status chip / test screen). */
    fun requestPermissionAgain() {
        permissionAsked = false
        refresh()
    }

    private suspend fun runStream(dev: UsbDevice) {
        val job = currentCoroutineContext()[Job]
        _state.value = CameraState.Connecting
        streamingDevice = dev.deviceName
        val conn = usb.openDevice(dev)
        if (conn == null) {
            lastError = "openDevice failed"
            _state.value = CameraState.Error(CameraState.Reason.USB_ERROR)
            return
        }
        val (frameIndex, frameW, frameH) = requestedFrame
        val stream = UvcStream(
            dev, conn,
            frameIndex = frameIndex,
            frameIntervalNs100 = 10_000_000 / requestedFps,
            frameBytes = frameW * frameH * 2,
        )
        var videoCount = 0L
        var videoWindowStart = System.nanoTime()
        var videoFps = 0f
        val prevThermal = FloatArray(Ht102Decoder.WIDTH * Ht102Decoder.HEIGHT)
        val visibleRing = Array(4) { IntArray(frameW * frameH) }
        var ring = 0
        var lastThermalNs = System.nanoTime()
        var fps = 0f
        var lastFpsPublish = 0L
        var thermalCount = 0L
        try {
            stream.open()
            lastNegotiation = stream.negotiated
            lastError = null
            val assembler = UvcFrameAssembler(stream.frameBytes) { raw ->
                val now = System.nanoTime()
                // Thermal: the sensor runs at ~9 Hz, video at 10 fps → skip repeated matrices.
                val t = FloatArray(Ht102Decoder.WIDTH * Ht102Decoder.HEIGHT)
                Ht102Decoder.decode(raw, t)
                if (!t.contentEquals(prevThermal)) {
                    t.copyInto(prevThermal)
                    val dt = now - lastThermalNs
                    if (thermalCount > 0 && dt > 0) {
                        val inst = 1e9f / dt
                        fps = if (fps == 0f) inst else fps + 0.2f * (inst - fps)
                    }
                    lastThermalNs = now
                    thermalCount++
                    _frames.tryEmit(ThermalFrame(Ht102Decoder.WIDTH, Ht102Decoder.HEIGHT, t, now))
                    if (now - lastFpsPublish > 1_000_000_000L || _state.value !is CameraState.Streaming) {
                        _state.value = CameraState.Streaming(fps)
                        lastFpsPublish = now
                    }
                }
                // Visible image from the module camera.
                val argb = visibleRing[ring]; ring = (ring + 1) % visibleRing.size
                Yuy2.toArgb(raw, frameW, frameH, argb)
                _visible.tryEmit(VisibleFrame(frameW, frameH, argb, now))
            }
            lastThermalNs = System.nanoTime()
            stream.pump(assembler, isActive = { job?.isActive != false }) {
                val silent = System.nanoTime() - lastThermalNs
                if (silent > WATCHDOG_NS && _state.value !is CameraState.Error) {
                    _state.value = CameraState.Error(CameraState.Reason.NO_DATA)
                }
                val nowNs = System.nanoTime()
                if (nowNs - videoWindowStart >= 2_000_000_000L) {
                    videoFps = (assembler.framesDelivered - videoCount) * 1e9f / (nowNs - videoWindowStart)
                    videoCount = assembler.framesDelivered
                    videoWindowStart = nowNs
                }
                stats = Stats(assembler.framesDelivered, assembler.framesDropped, thermalCount, assembler.maxHeaderLength, videoFps)
            }
        } catch (e: CameraGoneException) {
            _state.value = CameraState.Disconnected
        } catch (e: Exception) {
            Log.w(TAG, "Stream failed", e)
            lastError = e.message ?: e.javaClass.simpleName
            if (findCamera() == null) {
                _state.value = CameraState.Disconnected
            } else {
                _state.value = CameraState.Error(CameraState.Reason.USB_ERROR)
            }
        } finally {
            // Also when opening failed half-way, so the diagnostics show how far negotiation got.
            if (stream.negotiated.isNotEmpty()) lastNegotiation = stream.negotiated
            stream.close()
            conn.close()
            streamingDevice = null
        }
    }

    override suspend fun selfTest() = SelfTestReport(
        listOf(SelfTestReport.Step("device", findCamera() != null, findCamera()?.deviceName ?: "-")),
    )

    override fun diagnostics() = DiagnosticsDump(
        buildString {
            appendLine("Ht102Source state=${_state.value}")
            appendLine("negotiation: $lastNegotiation")
            appendLine("stats: $stats")
            lastError?.let { appendLine("last error: $it") }
        },
    )

    fun deviceDescription(): String? = findCamera()?.let { "USB UVC %04X:%04X %s".format(it.vendorId, it.productId, it.deviceName) }

    companion object {
        private const val TAG = "Ht102Source"
        const val HT102_VID = 0x0416
        const val HT102_PID = 0x9393
        const val FRAME_W = Ht102Decoder.FRAME_WIDTH
        const val FRAME_H = Ht102Decoder.FRAME_HEIGHT
        const val WATCHDOG_NS = 2_000_000_000L
        private const val ACTION_PERMISSION = "io.github.mpridal.thermocamera.HT102_PERMISSION"

        fun isHt102(d: UsbDevice) = d.vendorId == HT102_VID && d.productId == HT102_PID
    }
}
