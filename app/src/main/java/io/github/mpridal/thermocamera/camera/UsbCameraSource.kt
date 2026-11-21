package io.github.mpridal.thermocamera.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import io.github.mpridal.thermocamera.core.ThermalFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Phase-0 placeholder for the real HT-102 source: it tracks attach/detach of the camera
 * (VID 0x0416, PID 0x9393) so the UI can show the right state, but does not stream yet,
 * because the thermal-data channel is still being researched (docs/ht102-protocol.md).
 */
class UsbCameraSource(private val context: Context) : ThermalCameraSource {

    override val kind = ThermalCameraSource.Kind.HT102

    private val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _state = MutableStateFlow<CameraState>(CameraState.Disconnected)
    override val state: StateFlow<CameraState> = _state.asStateFlow()
    override val frames: Flow<ThermalFrame> = emptyFlow()
    override val visibleFrames: Flow<VisibleFrame>? = null

    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override suspend fun start() {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        refresh()
    }

    override suspend fun stop() {
        if (registered) {
            context.unregisterReceiver(receiver)
            registered = false
        }
    }

    private fun refresh() {
        _state.value = if (findCamera() != null) {
            CameraState.Error(CameraState.Reason.PROTOCOL_UNSUPPORTED)
        } else {
            CameraState.Disconnected
        }
    }

    fun findCamera(): UsbDevice? = usb.deviceList.values.firstOrNull { isHt102(it) }

    override suspend fun selfTest() = SelfTestReport(
        listOf(SelfTestReport.Step("device", findCamera() != null, findCamera()?.deviceName ?: "-")),
    )

    override fun diagnostics() = DiagnosticsDump("UsbCameraSource state=${_state.value}")

    companion object {
        const val HT102_VID = 0x0416
        const val HT102_PID = 0x9393

        fun isHt102(d: UsbDevice) = d.vendorId == HT102_VID && d.productId == HT102_PID
    }
}
