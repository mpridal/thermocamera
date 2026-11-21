package io.github.mpridal.thermocamera.camera

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import io.github.mpridal.thermocamera.core.UvcFrameAssembler
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal UVC 1.0 streaming of uncompressed YUY2 from a webcam-like device
 * (zadání kap. 3 – the HT-102 is a plain UVC camera, see docs/ht102-protocol.md).
 *
 * Sends only the standard requests every UVC host sends (the vendor app does the same through
 * libuvc): VS_PROBE_CONTROL GET/SET_CUR, VS_COMMIT_CONTROL SET_CUR and SET_INTERFACE.
 */
class UvcStream(
    private val device: UsbDevice,
    private val conn: UsbDeviceConnection,
    private val formatIndex: Int = 1,
    private val frameIndex: Int = 1,           // 1 = 640 × 480 on the HT-102
    private val frameIntervalNs100: Int = 1_000_000, // 10 fps – thermal data comes at ~9 Hz anyway
    val frameBytes: Int = 640 * 480 * 2,
) {
    private lateinit var vsAlt0: UsbInterface
    private var vsActive: UsbInterface? = null
    private var vc: UsbInterface? = null
    private var reader: IsoReader? = null

    var negotiated: String = ""
        private set

    /** Negotiates the stream and selects the alternate setting. Does not start reading yet. */
    fun open(): IsoReader {
        val interfaces = (0 until device.interfaceCount).map { device.getInterface(it) }
        vc = interfaces.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_VIDEO && it.interfaceSubclass == SC_VIDEOCONTROL }
        val vsAll = interfaces.filter { it.interfaceClass == UsbConstants.USB_CLASS_VIDEO && it.interfaceSubclass == SC_VIDEOSTREAMING }
        vsAlt0 = vsAll.firstOrNull { it.alternateSetting == 0 } ?: throw UsbStreamException("No VideoStreaming interface")
        vc?.let { conn.claimInterface(it, true) }
        if (!conn.claimInterface(vsAlt0, true)) throw UsbStreamException("Cannot claim VideoStreaming interface")
        conn.setInterface(vsAlt0)

        val vsId = vsAlt0.id
        val probe = ByteArray(PROBE_LEN)
        control(GET_CUR, VS_PROBE_CONTROL, vsId, probe) // defaults (not all devices answer)
        ByteBuffer.wrap(probe).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0, 1)                           // bmHint: keep dwFrameInterval
            put(2, formatIndex.toByte())
            put(3, frameIndex.toByte())
            putInt(4, frameIntervalNs100)
        }
        control(SET_CUR, VS_PROBE_CONTROL, vsId, probe, mustSucceed = true)
        control(GET_CUR, VS_PROBE_CONTROL, vsId, probe, mustSucceed = true)
        val bb = ByteBuffer.wrap(probe).order(ByteOrder.LITTLE_ENDIAN)
        val maxFrame = bb.getInt(18)
        val maxPayload = bb.getInt(22)
        control(SET_CUR, VS_COMMIT_CONTROL, vsId, probe, mustSucceed = true)
        negotiated = "format ${probe[2]} frame ${probe[3]} interval ${bb.getInt(4)} maxFrame $maxFrame maxPayload $maxPayload"
        Log.i(TAG, "UVC commit: $negotiated")

        // Smallest alternate setting whose isochronous bandwidth fits the payload size.
        val alts = vsAll.filter { it.alternateSetting > 0 }
            .mapNotNull { itf -> isoIn(itf)?.let { ep -> Triple(itf, ep, effectiveSize(ep)) } }
            .sortedBy { it.third }
        val chosen = alts.firstOrNull { it.third >= maxPayload } ?: alts.lastOrNull()
            ?: throw UsbStreamException("No isochronous alternate setting")
        if (!conn.setInterface(chosen.first)) throw UsbStreamException("SET_INTERFACE failed")
        vsActive = chosen.first
        return IsoReader(conn.fileDescriptor, chosen.second.address, chosen.third).also { reader = it }
    }

    /** Reads until [isActive] returns false; frames go to [assembler]. */
    fun pump(assembler: UvcFrameAssembler, isActive: () -> Boolean, onIdle: () -> Unit = {}) {
        val r = reader ?: throw IllegalStateException("open() first")
        while (isActive()) {
            val n = r.read()
            if (n == 0) { onIdle(); continue }
            for (i in 0 until n) {
                val len = r.lengths[i]
                if (len < 0) assembler.packetLost() else assembler.push(r.buffer, i * r.packetSize, len)
            }
            onIdle()
        }
    }

    fun close() {
        runCatching { reader?.close() }
        reader = null
        runCatching { conn.setInterface(vsAlt0) } // alt 0 = stop streaming
        runCatching { conn.releaseInterface(vsAlt0) }
        vc?.let { runCatching { conn.releaseInterface(it) } }
    }

    private fun control(request: Int, selector: Int, itf: Int, data: ByteArray, mustSucceed: Boolean = false): Int {
        val type = if (request == SET_CUR) REQ_TYPE_OUT else REQ_TYPE_IN
        val n = conn.controlTransfer(type, request, selector shl 8, itf, data, data.size, 1000)
        if (n < 0 && mustSucceed) throw UsbStreamException("UVC request 0x%02X selector %d failed".format(request, selector))
        return n
    }

    private fun isoIn(itf: UsbInterface): UsbEndpoint? = (0 until itf.endpointCount).map { itf.getEndpoint(it) }
        .firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_ISOC && it.direction == UsbConstants.USB_DIR_IN }

    companion object {
        private const val TAG = "UvcStream"
        private const val SC_VIDEOCONTROL = 1
        private const val SC_VIDEOSTREAMING = 2
        private const val REQ_TYPE_OUT = 0x21 // class, interface, host-to-device
        private const val REQ_TYPE_IN = 0xA1
        private const val SET_CUR = 0x01
        private const val GET_CUR = 0x81
        private const val VS_PROBE_CONTROL = 0x01
        private const val VS_COMMIT_CONTROL = 0x02
        private const val PROBE_LEN = 26 // UVC 1.0

        /** wMaxPacketSize bits 0–10 = size, bits 11–12 = additional transactions per microframe. */
        fun effectiveSize(ep: UsbEndpoint): Int {
            val mps = ep.maxPacketSize
            return (mps and 0x7FF) * (1 + ((mps shr 11) and 0x3))
        }
    }
}
