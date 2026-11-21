package io.github.mpridal.thermocamera.diagnostics

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import io.github.mpridal.thermocamera.BuildConfig
import io.github.mpridal.thermocamera.camera.UsbCameraSource
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Read-only USB diagnostics (zadání kap. 3, úkol 1). Produces a plain-text dump that the user
 * can export and send back; descriptors are only read after the user granted USB permission.
 * Never sends any request to the device other than reading the cached raw descriptors.
 */
object UsbDiagnostics {

    fun dump(usb: UsbManager, rawDescriptors: Map<String, ByteArray> = emptyMap()): String = buildString {
        appendLine("ThermoCamera USB diagnostics")
        appendLine("generated: ${ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
        appendLine("app: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        val devices = usb.deviceList.values.sortedBy { it.deviceName }
        if (devices.isEmpty()) appendLine("No USB devices.")
        for (d in devices) {
            appendDevice(d, usb.hasPermission(d))
            rawDescriptors[d.deviceName]?.let { raw ->
                appendLine("  raw descriptors (${raw.size} B):")
                appendLine(hexDump(raw, indent = "    "))
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendDevice(d: UsbDevice, permission: Boolean) {
        val ht = if (UsbCameraSource.isHt102(d)) "  <-- HT-102" else ""
        appendLine("DEVICE ${d.deviceName}$ht")
        appendLine("  VID 0x%04X  PID 0x%04X  class %s  subclass 0x%02X  protocol 0x%02X".format(
            d.vendorId, d.productId, className(d.deviceClass), d.deviceSubclass, d.deviceProtocol,
        ))
        appendLine("  permission: $permission")
        // Names and serial need permission on API 29+.
        runCatching {
            appendLine("  manufacturer: ${d.manufacturerName}")
            appendLine("  product: ${d.productName}")
            appendLine("  version: ${d.version}")
            appendLine("  serial: ${if (permission) d.serialNumber else "(needs permission)"}")
        }.onFailure { appendLine("  names: (needs permission)") }
        appendLine("  configurations: ${d.configurationCount}, interfaces: ${d.interfaceCount}")
        for (i in 0 until d.interfaceCount) {
            val itf = d.getInterface(i)
            appendLine(
                "  IF ${itf.id} alt ${itf.alternateSetting}  class %s  sub 0x%02X  proto 0x%02X  endpoints %d  name %s".format(
                    className(itf.interfaceClass), itf.interfaceSubclass, itf.interfaceProtocol, itf.endpointCount, itf.name,
                ),
            )
            for (e in 0 until itf.endpointCount) {
                val ep = itf.getEndpoint(e)
                val dir = if (ep.direction == UsbConstants.USB_DIR_IN) "IN " else "OUT"
                val type = when (ep.type) {
                    UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "control"
                    UsbConstants.USB_ENDPOINT_XFER_ISOC -> "isochronous"
                    UsbConstants.USB_ENDPOINT_XFER_BULK -> "bulk"
                    UsbConstants.USB_ENDPOINT_XFER_INT -> "interrupt"
                    else -> "?"
                }
                appendLine("    EP 0x%02X %s %-11s maxPacket %d (0x%04X)  interval %d".format(
                    ep.address, dir, type, ep.maxPacketSize and 0x7FF, ep.maxPacketSize, ep.interval,
                ))
            }
        }
    }

    fun className(c: Int): String = when (c) {
        UsbConstants.USB_CLASS_PER_INTERFACE -> "0x00 (per interface)"
        UsbConstants.USB_CLASS_VIDEO -> "0x0E (video)"
        UsbConstants.USB_CLASS_HID -> "0x03 (HID)"
        UsbConstants.USB_CLASS_MASS_STORAGE -> "0x08 (mass storage)"
        UsbConstants.USB_CLASS_VENDOR_SPEC -> "0xFF (vendor)"
        UsbConstants.USB_CLASS_MISC -> "0xEF (misc/IAD)"
        else -> "0x%02X".format(c)
    }

    fun hexDump(bytes: ByteArray, indent: String = ""): String = buildString {
        for (off in bytes.indices step 16) {
            append(indent).append("%04X: ".format(off))
            val end = minOf(off + 16, bytes.size)
            for (i in off until end) append("%02X ".format(bytes[i].toInt() and 0xFF))
            appendLine()
        }
    }.trimEnd()
}
