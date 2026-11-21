package io.github.mpridal.thermocamera.camera

import io.github.mpridal.thermocamera.core.ThermalFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A thermal camera (zadání kap. 4.1). Implementations: [SimulatedSource], [Ht102Source]. */
interface ThermalCameraSource {
    val kind: Kind
    val state: StateFlow<CameraState>
    val frames: Flow<ThermalFrame>

    /** Null if the source has no visible-light camera of its own. */
    val visibleFrames: Flow<VisibleFrame>?

    suspend fun start()
    suspend fun stop()
    suspend fun selfTest(): SelfTestReport
    fun diagnostics(): DiagnosticsDump

    enum class Kind { SIMULATOR, HT102 }
}

sealed interface CameraState {
    data object Disconnected : CameraState
    data object Connecting : CameraState
    data object NeedsPermission : CameraState
    data class Streaming(val fps: Float) : CameraState
    data class Error(val reason: Reason) : CameraState

    enum class Reason { NO_DATA, USB_ERROR }
}

/** A visible-light frame as ARGB pixels (row-major). */
class VisibleFrame(
    val width: Int,
    val height: Int,
    val argb: IntArray,
    val timestampNanos: Long,
)

data class SelfTestReport(val steps: List<Step>) {
    data class Step(val id: String, val ok: Boolean, val detail: String)

    val ok: Boolean get() = steps.all { it.ok }
}

data class DiagnosticsDump(val text: String)
