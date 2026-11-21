package io.github.mpridal.thermocamera.snapshot

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.core.Correction
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.core.RangePreset
import io.github.mpridal.thermocamera.core.TempRange
import io.github.mpridal.thermocamera.data.ViewMode
import io.github.mpridal.thermocamera.data.db.SnapshotEntity
import io.github.mpridal.thermocamera.ui.components.NumberFormatter
import io.github.mpridal.thermocamera.ui.live.LiveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Display overrides for re-rendering a saved snapshot (zadání F4 – přerenderování). */
data class RenderOptions(
    val viewMode: ViewMode,
    val blend: Float,
    val palette: Palette,
    val range: TempRange?,
    val emissivity: Float,
) {
    companion object {
        fun of(s: SnapshotEntity) = RenderOptions(
            viewMode = ViewMode.entries.firstOrNull { it.name == s.viewMode } ?: ViewMode.THERMAL,
            blend = s.blend,
            palette = Palette.entries.firstOrNull { it.name == s.palette } ?: Palette.IRON,
            range = TempRange(s.rangeMin, maxOf(s.rangeMax, s.rangeMin + 0.5f)),
            emissivity = s.emissivity,
        )
    }
}

/** Builds share files (zadání F5): JPG, side-by-side JPG, PDF report, CSV. Files go to cache/exports. */
@Singleton
class SnapshotExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SnapshotStore,
) {
    val locale: Locale get() = AppCompatDelegate.getApplicationLocales()[0] ?: Locale.getDefault()

    private fun exportDir() = File(context.cacheDir, "exports").apply { mkdirs() }

    fun points(s: SnapshotEntity): List<Pair<Float, Float>> = s.points.split(';').filter { it.isNotBlank() }.mapNotNull {
        val (x, y) = it.split(',').map { v -> v.toFloatOrNull() ?: return@mapNotNull null }
        x to y
    }

    /** Re-renders a snapshot with [opt]; null if its raw data cannot be read. */
    fun render(s: SnapshotEntity, opt: RenderOptions, sideBySide: Boolean = opt.viewMode == ViewMode.SIDE_BY_SIDE, square: Int = 1080): RenderResult? {
        val (raw, w, h) = store.readThermal(s) ?: return null
        val vis = store.readVisible(s)
        val preset = RangePreset.entries.firstOrNull { it.name == s.rangePreset } ?: RangePreset.AUTO
        val input = RenderInput(
            raw = raw, width = w, height = h, visible = vis?.first, visibleCrop = vis?.second,
            viewMode = opt.viewMode, blend = opt.blend, palette = opt.palette, range = opt.range,
            correction = Correction(opt.emissivity, s.reflected, s.offset, s.gain), points = points(s),
            insideTemp = s.insideTemp, outsideTemp = s.outsideTemp, fRsiLimit = s.fRsiLimit, isotherm = false,
            rangeName = context.getString(LiveViewModel.rangeNameRes(preset)), createdAt = s.createdAt, title = s.title,
        )
        return SnapshotRenderer(context, locale).render(input, sideBySide, square).also { vis?.first?.recycle() }
    }

    private fun baseName(s: SnapshotEntity) = "thermocamera-" + s.dir

    suspend fun jpg(s: SnapshotEntity, sideBySide: Boolean): File? = withContext(Dispatchers.Default) {
        val opt = RenderOptions.of(s)
        val r = render(s, opt, sideBySide) ?: return@withContext null
        val f = File(exportDir(), baseName(s) + if (sideBySide) "-side.jpg" else ".jpg")
        f.outputStream().use { r.bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        r.bitmap.recycle()
        f
    }

    suspend fun csv(s: SnapshotEntity): File? = withContext(Dispatchers.IO) {
        val (raw, w, h) = store.readThermal(s) ?: return@withContext null
        val values = FloatArray(raw.size).also { Correction(s.emissivity, s.reflected, s.offset, s.gain).applyTo(raw, it) }
        val fmt = NumberFormatter(locale)
        val r = context.resources
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(s.createdAt))
        val sb = StringBuilder()
        // Separator ';' and decimal comma/point by language so Excel opens it directly (zadání F5).
        fun row(k: String, v: String) = sb.append(k).append(';').append(v).append("\r\n")
        row(r.getString(R.string.app_name), r.getString(R.string.csv_header_note))
        row("date", date)
        if (s.title.isNotBlank()) row("title", s.title.replace(';', ','))
        if (s.note.isNotBlank()) row("note", s.note.replace(';', ',').replace('\n', ' '))
        row("emissivity", fmt.two(s.emissivity))
        row("reflected", fmt.one(s.reflected))
        row("offset", fmt.two(s.offset))
        row("gain", fmt.two(s.gain))
        row("inside", fmt.one(s.insideTemp))
        row("outside", fmt.one(s.outsideTemp))
        row("size", "${w}x$h")
        sb.append("\r\n")
        for (y in 0 until h) {
            sb.append((0 until w).joinToString(";") { x -> fmt.one(values[y * w + x]) }).append("\r\n")
        }
        File(exportDir(), baseName(s) + ".csv").apply { writeText(BOM + sb) } // BOM for Excel
    }

    /** One page per snapshot (A4): image, values, note, θi/θe, date, project (zadání F5). */
    suspend fun pdf(list: List<SnapshotEntity>, projectName: (Long?) -> String): File? = withContext(Dispatchers.Default) {
        if (list.isEmpty()) return@withContext null
        val r = context.resources
        val fmt = NumberFormatter(locale)
        val doc = PdfDocument()
        val pageW = 595; val pageH = 842; val margin = 40
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.rgb(40, 44, 52) }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.rgb(90, 96, 108) }
        list.forEachIndexed { i, s ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create())
            val c: Canvas = page.canvas
            var y = margin + 18f
            c.drawText(r.getString(R.string.report_title), margin.toFloat(), y, title)
            y += 20f
            val date = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, locale).format(Date(s.createdAt))
            c.drawText(listOf(s.title, projectName(s.projectId), date).filter { it.isNotBlank() }.joinToString(" · "), margin.toFloat(), y, text)
            y += 14f
            val rendered = render(s, RenderOptions.of(s), square = 900)
            if (rendered != null) {
                val bmp = rendered.bitmap
                val maxW = pageW - 2 * margin
                val scale = minOf(maxW.toFloat() / bmp.width, 560f / bmp.height)
                val dst = Rect(margin, y.toInt(), margin + (bmp.width * scale).toInt(), y.toInt() + (bmp.height * scale).toInt())
                c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                y = dst.bottom + 22f
                val m = rendered.measurements
                val cel = { v: Float -> fmt.one(v) + " °C" }
                c.drawText("MIN ${cel(m.min.celsius)}   ${r.getString(R.string.label_center)} ${cel(m.center)}   MAX ${cel(m.max.celsius)}", margin.toFloat(), y, text)
                y += 16f
                m.points.forEachIndexed { k, p -> c.drawText("P${k + 1}: ${cel(p.celsius)}", margin.toFloat() + (k % 5) * 100f, y, text) }
                if (m.points.isNotEmpty()) y += 16f
                rendered.fRsi?.let {
                    c.drawText("fRsi ${fmt.two(it)} (≥ ${fmt.two(s.fRsiLimit)}) · θi ${cel(s.insideTemp)} · θe ${cel(s.outsideTemp)}", margin.toFloat(), y, text)
                    y += 16f
                }
                c.drawText("ε ${fmt.two(s.emissivity)} · ${r.getString(R.string.cal_reflected)} ${cel(s.reflected)}", margin.toFloat(), y, text)
                y += 16f
                bmp.recycle()
            }
            if (s.note.isNotBlank()) {
                s.note.lines().forEach { line -> c.drawText(line.take(110), margin.toFloat(), y, text); y += 14f }
            }
            c.drawText(r.getString(R.string.bridge_note), margin.toFloat(), pageH - margin - 12f, muted)
            c.drawText(r.getString(R.string.report_page, i + 1, list.size), pageW - margin - 70f, pageH - margin.toFloat(), muted)
            doc.finishPage(page)
        }
        val name = if (list.size == 1) baseName(list[0]) + ".pdf" else "thermocamera-report-${list.size}.pdf"
        val f = File(exportDir(), name)
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        f
    }

    private companion object {
        /** UTF-8 byte-order mark so Excel detects the encoding. */
        val BOM = Char(0xFEFF).toString()
    }

    fun uri(f: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)

    /** Share intent for one or more files. */
    fun shareIntent(files: List<File>, mime: String): Intent {
        val uris = ArrayList(files.map { uri(it) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = mime
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, context.getString(R.string.share_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
