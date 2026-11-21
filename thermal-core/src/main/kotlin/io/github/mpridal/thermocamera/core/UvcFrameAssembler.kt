package io.github.mpridal.thermocamera.core

/**
 * Reassembles uncompressed UVC video frames from isochronous payloads (UVC 1.0, 2.4.3.3).
 *
 * Every payload starts with a header: byte 0 = header length, byte 1 = bmHeaderInfo
 * (bit 0 FID toggles per frame, bit 1 EOF, bit 6 ERR). A frame is complete when EOF is set
 * or FID toggles. Only frames of exactly [frameBytes] without an error bit are delivered;
 * partial or damaged ones are counted and dropped.
 *
 * Not thread-safe. [onFrame] receives the internal buffer – copy it if it must outlive the call.
 */
class UvcFrameAssembler(
    val frameBytes: Int,
    private val onFrame: (ByteArray) -> Unit,
) {
    private val buffer = ByteArray(frameBytes)
    private var length = 0
    private var overflow = false
    private var error = false
    private var fid = -1

    var framesDelivered = 0L
        private set
    var framesDropped = 0L
        private set

    /** Longest payload header seen – the HT-102 is expected to use plain 12-byte headers. */
    var maxHeaderLength = 0
        private set

    /** Feeds one isochronous packet ([len] bytes of [data] starting at [offset]). */
    fun push(data: ByteArray, offset: Int, len: Int) {
        if (len < 2) return // empty packet, common between frames
        val hLen = data[offset].toInt() and 0xFF
        if (hLen < 2 || hLen > len) return // not a valid header
        if (hLen > maxHeaderLength) maxHeaderLength = hLen
        val info = data[offset + 1].toInt() and 0xFF
        val packetFid = info and 0x01

        if (fid != -1 && packetFid != fid && length > 0) finish()
        fid = packetFid
        if (info and 0x40 != 0) error = true

        val payload = len - hLen
        if (payload > 0) {
            if (length + payload > frameBytes) {
                overflow = true
            } else {
                System.arraycopy(data, offset + hLen, buffer, length, payload)
                length += payload
            }
        }
        if (info and 0x02 != 0) finish()
    }

    /** Marks a lost packet (transfer error): the current frame cannot be trusted. */
    fun packetLost() {
        if (length > 0) error = true
    }

    private fun finish() {
        if (length == frameBytes && !overflow && !error) {
            framesDelivered++
            onFrame(buffer)
        } else if (length > 0) {
            framesDropped++
        }
        length = 0
        overflow = false
        error = false
    }

    fun reset() {
        length = 0; overflow = false; error = false; fid = -1
    }
}
