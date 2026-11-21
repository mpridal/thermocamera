package io.github.mpridal.thermocamera.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Keeps every camera test and USB diagnostics result in the app's private storage, so they can be
 * read from a computer without copy-pasting (debug build: `adb shell run-as <app> cat files/diagnostics/latest-*.txt`,
 * see docs/manual-test.md). Nothing leaves the phone on its own.
 */
object DiagnosticsLog {
    private const val TAG = "ThermoDiag"
    private const val KEEP = 30

    fun dir(context: Context) = File(context.filesDir, "diagnostics").apply { mkdirs() }

    /** Saves [text] as `<kind>-<timestamp>.txt` and `latest-<kind>.txt`; keeps the newest [KEEP] files. */
    fun save(context: Context, kind: String, text: String): File? = runCatching {
        val d = dir(context)
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = File(d, "$kind-$stamp.txt").apply { writeText(text) }
        File(d, "latest-$kind.txt").writeText(text)
        d.listFiles { f -> !f.name.startsWith("latest-") }
            ?.sortedByDescending { it.name.substringAfter('-') }
            ?.drop(KEEP)
            ?.forEach { it.delete() } // only the app's own old logs
        Log.i(TAG, "saved ${file.name}\n$text")
        file
    }.getOrNull()
}
