package io.github.mpridal.thermocamera.camera

import java.io.Closeable

/**
 * Isochronous IN transfers over usbfs (native, `ht102_usb.c`). Android's Java USB API cannot do
 * isochronous transfers, which is the only way the HT-102 streams video.
 */
class IsoReader(
    fd: Int,
    endpoint: Int,
    val packetSize: Int,
    val packetsPerUrb: Int = 32,
    numUrbs: Int = 8,
) : Closeable {

    private var handle: Long = nativeOpen(fd, endpoint, packetSize, packetsPerUrb, numUrbs)
    val buffer = ByteArray(packetSize * packetsPerUrb)
    val lengths = IntArray(packetsPerUrb)

    init {
        if (handle <= 0L) throw UsbStreamException("Cannot start isochronous transfers (errno ${-handle})")
    }

    /**
     * Waits for one completed transfer. Packet i is at `buffer[i * packetSize]` with length
     * `lengths[i]` (-1 = lost packet). Returns the number of packets, 0 on timeout.
     * @throws CameraGoneException when the device disappeared.
     */
    fun read(timeoutMs: Int = 250): Int {
        val n = nativeRead(handle, buffer, lengths, timeoutMs)
        if (n == -ENODEV || n == -ESHUTDOWN || n == -EPIPE) throw CameraGoneException()
        if (n < 0) throw UsbStreamException("Isochronous read failed (errno ${-n})")
        return n
    }

    override fun close() {
        if (handle > 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    companion object {
        private const val EPIPE = 32
        private const val ENODEV = 19
        private const val ESHUTDOWN = 108

        init {
            System.loadLibrary("ht102usb")
        }

        @JvmStatic private external fun nativeOpen(fd: Int, endpoint: Int, packetSize: Int, packetsPerUrb: Int, numUrbs: Int): Long
        @JvmStatic private external fun nativeRead(handle: Long, out: ByteArray, lengths: IntArray, timeoutMs: Int): Int
        @JvmStatic private external fun nativeClose(handle: Long)
    }
}

open class UsbStreamException(message: String) : Exception(message)
class CameraGoneException : UsbStreamException("Camera disconnected")
