package io.github.mpridal.thermocamera.snapshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.core.Correction
import io.github.mpridal.thermocamera.core.CropSquare
import io.github.mpridal.thermocamera.core.FrameRenderer
import io.github.mpridal.thermocamera.core.Interpolation
import io.github.mpridal.thermocamera.core.Measure
import io.github.mpridal.thermocamera.core.Measurements
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.core.TempRange
import io.github.mpridal.thermocamera.core.ThermalBridge
import io.github.mpridal.thermocamera.data.ViewMode
import io.github.mpridal.thermocamera.ui.components.NumberFormatter
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Everything needed to draw a snapshot – from the live view or re-rendered from saved raw data. */
data class RenderInput(
    /** Raw thermal values (°C, before user corrections), oriented as shown. */
    val raw: FloatArray,
    val width: Int,
    val height: Int,
    val visible: Bitmap?,
    /** Where the thermal square lies in [visible]. */
    val visibleCrop: CropSquare?,
    val viewMode: ViewMode,
    val blend: Float,
    val palette: Palette,
    /** Fixed range, or null for automatic (2nd–98th percentile). */
    val range: TempRange?,
    val correction: Correction,
    val points: List<Pair<Float, Float>>,
    val insideTemp: Float,
    val outsideTemp: Float,
    val fRsiLimit: Float,
    val isotherm: Boolean,
    val rangeName: String,
    val createdAt: Long,
    val title: String = "",
)

data class RenderResult(val bitmap: Bitmap, val measurements: Measurements, val range: TempRange, val fRsi: Float?)

/**
 * Draws a measurement snapshot (zadání F4/F5): the image exactly as in the app (mode, blend,
 * palette, markers, scale) plus a strip with values. Works on any thread.
 */
class SnapshotRenderer(private val context: Context, private val locale: Locale) {

    private val fmt = NumberFormatter(locale)

    fun corrected(input: RenderInput): FloatArray =
        FloatArray(input.raw.size).also { input.correction.applyTo(input.raw, it) }

    fun render(input: RenderInput, sideBySide: Boolean = input.viewMode == ViewMode.SIDE_BY_SIDE, square: Int = 1080): RenderResult {
        val values = corrected(input)
        val m = Measure.of(values, input.width, input.height, input.points)
        val range = input.range ?: autoRange(values)
        val fRsi = ThermalBridge.fRsi(m.min.celsius, input.insideTemp, input.outsideTemp)

        val panels = if (sideBySide) 2 else 1
        val gap = if (sideBySide) 16 else 0
        val stripH = (square * 0.30f).toInt()
        val out = Bitmap.createBitmap(square * panels + gap, square + stripH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(10, 12, 16))

        val thermal = thermalBitmap(values, input, range, square)
        if (sideBySide) {
            drawPanel(canvas, 0f, square, input, thermal, ViewMode.THERMAL, m, range)
            drawPanel(canvas, (square + gap).toFloat(), square, input, thermal, ViewMode.PHOTO, m, range)
        } else {
            drawPanel(canvas, 0f, square, input, thermal, input.viewMode, m, range)
        }
        drawStrip(canvas, square.toFloat(), out.width.toFloat(), stripH.toFloat(), input, m, fRsi)
        thermal.recycle()
        return RenderResult(out, m, range, fRsi)
    }

    private fun autoRange(values: FloatArray): TempRange {
        val sorted = values.copyOf().also { it.sort() }
        val lo = sorted[((sorted.size - 1) * 0.02f).toInt()]
        val hi = sorted[((sorted.size - 1) * 0.98f).toInt()]
        return if (hi - lo < 1f) TempRange((lo + hi) / 2 - 0.5f, (lo + hi) / 2 + 0.5f) else TempRange(lo, hi)
    }

    private fun thermalBitmap(values: FloatArray, input: RenderInput, range: TempRange, size: Int): Bitmap {
        val r = FrameRenderer(RENDER_GRID, RENDER_GRID)
        val iso = if (input.isotherm && input.insideTemp > input.outsideTemp) ThermalBridge.isothermThreshold(input.insideTemp, input.outsideTemp, input.fRsiLimit) else null
        val px = r.render(values, input.width, input.height, range, input.palette, Interpolation.Mode.BICUBIC, iso)
        val small = Bitmap.createBitmap(px, RENDER_GRID, RENDER_GRID, Bitmap.Config.ARGB_8888)
        val big = Bitmap.createScaledBitmap(small, size, size, true)
        if (big !== small) small.recycle()
        return big
    }

    private fun drawPanel(canvas: Canvas, left: Float, size: Int, input: RenderInput, thermal: Bitmap, mode: ViewMode, m: Measurements, range: TempRange) {
        canvas.save()
        canvas.translate(left, 0f)
        canvas.clipRect(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawColor(Color.BLACK)
        val vis = input.visible
        if (mode != ViewMode.THERMAL && vis != null) {
            val c = input.visibleCrop ?: CropSquare(vis.width / 2f, vis.height / 2f, minOf(vis.width, vis.height).toFloat())
            val k = size / c.side
            val mx = Matrix().apply {
                postTranslate(-c.cx, -c.cy)
                postScale(k, k)
                postRotate(-c.angleDeg)
                postTranslate(size / 2f, size / 2f)
            }
            canvas.drawBitmap(vis, mx, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        if (mode != ViewMode.PHOTO) {
            val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = if (mode == ViewMode.OVERLAY) (input.blend * 255).toInt() else 255 }
            canvas.drawBitmap(thermal, 0f, 0f, p)
        }
        val s = size.toFloat()
        drawCrosshair(canvas, s)
        label(canvas, fmt.one(m.center) + " °C", s / 2 + s * 0.05f, s / 2 - s * 0.03f, Color.WHITE)
        if (mode != ViewMode.PHOTO) {
            val gw = input.width.toFloat(); val gh = input.height.toFloat()
            marker(canvas, (m.min.x + 0.5f) / gw * s, (m.min.y + 0.5f) / gh * s, true, MIN_COLOR)
            label(canvas, "▼ " + fmt.deg(m.min.celsius), (m.min.x + 0.5f) / gw * s + 18, (m.min.y + 0.5f) / gh * s, MIN_COLOR)
            marker(canvas, (m.max.x + 0.5f) / gw * s, (m.max.y + 0.5f) / gh * s, false, MAX_COLOR)
            label(canvas, "▲ " + fmt.deg(m.max.celsius), (m.max.x + 0.5f) / gw * s + 18, (m.max.y + 0.5f) / gh * s, MAX_COLOR)
            m.points.forEachIndexed { i, sp ->
                val x = (sp.x + 0.5f) / gw * s; val y = (sp.y + 0.5f) / gh * s
                canvas.drawCircle(x, y, s * 0.012f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.004f; color = Color.WHITE })
                label(canvas, "P${i + 1} " + fmt.deg(sp.celsius), x + 18, y, Color.WHITE)
            }
            scaleBar(canvas, s, input.palette, range)
        }
        canvas.restore()
    }

    private fun drawCrosshair(canvas: Canvas, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = s * 0.003f }
        val arm = s * 0.045f; val gap = arm * 0.35f; val c = s / 2
        canvas.drawLine(c - arm, c, c - gap, c, p); canvas.drawLine(c + gap, c, c + arm, c, p)
        canvas.drawLine(c, c - arm, c, c - gap, p); canvas.drawLine(c, c + gap, c, c + arm, p)
    }

    private fun marker(canvas: Canvas, x: Float, y: Float, down: Boolean, color: Int) {
        val s = 16f
        val path = Path().apply {
            if (down) { moveTo(x - s, y - s); lineTo(x + s, y - s); lineTo(x, y + s * 0.6f) } else { moveTo(x - s, y + s); lineTo(x + s, y + s); lineTo(x, y - s * 0.6f) }
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f })
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; typeface = Typeface.MONOSPACE }

    /** Text on a dark rounded box, kept inside the panel. */
    private fun label(canvas: Canvas, text: String, x: Float, y: Float, color: Int) {
        val w = labelPaint.measureText(text)
        val clip = canvas.clipBounds
        val lx = x.coerceIn(8f, clip.width() - w - 24f)
        val ly = y.coerceIn(40f, clip.height() - 16f)
        canvas.drawRoundRect(RectF(lx - 8, ly - 34, lx + w + 8, ly + 10), 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(150, 10, 12, 16) })
        canvas.drawText(text, lx, ly, labelPaint.apply { this.color = color })
    }

    private fun scaleBar(canvas: Canvas, s: Float, palette: Palette, range: TempRange) {
        val x = s - 56f; val top = s * 0.2f; val bottom = s * 0.8f
        val colors = palette.stops.map { it.argb }.reversed().toIntArray()
        val positions = palette.stops.map { 1f - it.position }.reversed().toFloatArray()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, top, 0f, bottom, colors, positions, Shader.TileMode.CLAMP) }
        canvas.drawRoundRect(RectF(x, top, x + 24f, bottom), 12f, 12f, paint)
        label(canvas, fmt.zero(range.max), x - 20f, top - 12f, Color.WHITE)
        label(canvas, fmt.zero(range.min), x - 20f, bottom + 44f, Color.WHITE)
    }

    private fun drawStrip(canvas: Canvas, top: Float, width: Float, height: Float, input: RenderInput, m: Measurements, fRsi: Float?) {
        val r = context.resources
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = height * 0.17f; typeface = Typeface.MONOSPACE; color = Color.WHITE }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = height * 0.11f; color = Color.rgb(185, 192, 204) }
        val pad = 36f
        var y = top + height * 0.24f
        val c = { v: Float -> fmt.one(v) + " °C" }
        val values = "▼ ${c(m.min.celsius)}   + ${c(m.center)}   ▲ ${c(m.max.celsius)}"
        // Shrink to fit the strip width.
        val w = big.measureText(values)
        if (w > width - 2 * pad) big.textSize *= (width - 2 * pad) / w
        canvas.drawText(values, pad, y, big)
        y += height * 0.2f
        if (m.points.isNotEmpty()) {
            canvas.drawText(m.points.mapIndexed { i, p -> "P${i + 1} ${c(p.celsius)}" }.joinToString("   "), pad, y, small.apply { color = Color.WHITE })
            y += height * 0.15f
        }
        val bridge = fRsi?.let {
            "fRsi ${fmt.two(it)} (≥ ${fmt.two(input.fRsiLimit)}) · θi ${c(input.insideTemp)} · θe ${c(input.outsideTemp)}"
        }
        if (bridge != null) {
            canvas.drawText(bridge, pad, y, small.apply { color = if (fRsi < input.fRsiLimit) Color.rgb(255, 193, 77) else Color.rgb(185, 192, 204) })
            y += height * 0.15f
        }
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(input.createdAt))
        canvas.drawText(
            listOf("ε ${fmt.two(input.correction.emissivity)}", input.rangeName, date, input.title).filter { it.isNotBlank() }.joinToString(" · "),
            pad, y, small.apply { color = Color.rgb(185, 192, 204) },
        )
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = height * 0.1f; color = Color.rgb(255, 138, 61); textAlign = Paint.Align.RIGHT }
        canvas.drawText(r.getString(R.string.app_name), width - pad, top + height - 28f, brand)
    }

    companion object {
        private const val RENDER_GRID = 320
        private val MIN_COLOR = Color.rgb(90, 176, 255)
        private val MAX_COLOR = Color.rgb(255, 91, 74)
    }
}
