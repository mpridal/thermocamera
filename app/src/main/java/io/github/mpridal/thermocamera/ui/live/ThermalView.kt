package io.github.mpridal.thermocamera.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.core.CropSquare
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.data.ViewMode
import io.github.mpridal.thermocamera.ui.components.GlassLevel
import io.github.mpridal.thermocamera.ui.components.NumberFormatter
import io.github.mpridal.thermocamera.ui.components.celsius
import io.github.mpridal.thermocamera.ui.components.glass
import io.github.mpridal.thermocamera.ui.theme.NumberStyleSmall
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme
import kotlin.math.hypot

/** Callbacks for user points P1–P5; coordinates are in thermal-grid pixel-centre units. */
class PointActions(
    val points: List<UserPoint>,
    val onAdd: (Float, Float) -> Unit,
    val onMove: (Int, Float, Float) -> Unit,
    val onRemove: (Int) -> Unit,
)

/**
 * The measuring surface. Never translucent or blurred (zadání kap. 7.1).
 * Side by side: [stacked] = one above the other (phone portrait), otherwise next to each other.
 * Gestures (F1): tap = new point, drag a point = move, long press on a point = remove.
 */
@Composable
fun MeasuringSurface(
    frame: LiveFrameUi,
    viewMode: ViewMode,
    blend: Float,
    palette: Palette,
    fmt: NumberFormatter,
    stacked: Boolean,
    points: PointActions,
    modifier: Modifier = Modifier,
) {
    if (viewMode == ViewMode.SIDE_BY_SIDE) {
        val content: @Composable (Modifier) -> Unit = { m ->
            ImageSquare(frame, ViewMode.THERMAL, blend, palette, fmt, points, m)
            ImageSquare(frame, ViewMode.PHOTO, blend, palette, fmt, points, m)
        }
        if (stacked) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                content(Modifier.weight(1f).aspectRatio(1f))
            }
        } else {
            Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                content(Modifier.weight(1f).aspectRatio(1f))
            }
        }
    } else {
        ImageSquare(frame, viewMode, blend, palette, fmt, points, modifier.aspectRatio(1f))
    }
}

@Composable
private fun ImageSquare(
    frame: LiveFrameUi,
    viewMode: ViewMode,
    blend: Float,
    palette: Palette,
    fmt: NumberFormatter,
    points: PointActions,
    modifier: Modifier,
) {
    val c = ThermoTheme.colors
    val m = frame.measurements
    val showThermal = viewMode != ViewMode.PHOTO
    val gw = frame.gridW.toFloat()
    val gh = frame.gridH.toFloat()
    val actions by rememberUpdatedState(points)
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .pointerInput(gw, gh) {
                detectTapGestures(
                    onTap = { o -> actions.onAdd(toGrid(o.x, size.width, gw), toGrid(o.y, size.height, gh)) },
                    onLongPress = { o -> nearestPoint(actions.points, o, size.width.toFloat(), gw, gh)?.let(actions.onRemove) },
                )
            }
            .pointerInput(gw, gh) {
                var dragging = -1
                detectDragGestures(
                    onDragStart = { o -> dragging = nearestPoint(actions.points, o, size.width.toFloat(), gw, gh) ?: -1 },
                    onDragEnd = { dragging = -1 },
                    onDragCancel = { dragging = -1 },
                ) { change, _ ->
                    if (dragging >= 0) {
                        change.consume()
                        actions.onMove(dragging, toGrid(change.position.x, size.width, gw), toGrid(change.position.y, size.height, gh))
                    }
                }
            },
    ) {
        val sizeDp = maxWidth
        val frameNo = frame.frameNo // read so that reused bitmaps redraw
        Canvas(Modifier.fillMaxSize()) {
            frameNo.hashCode()
            if (viewMode != ViewMode.THERMAL) frame.visible?.let { drawVisibleSquare(it, frame.visibleCrop) }
            if (showThermal) frame.thermal?.let {
                drawImage(
                    it,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    alpha = if (viewMode == ViewMode.OVERLAY) blend else 1f,
                    filterQuality = FilterQuality.Low,
                )
            }
            drawCrosshair()
            if (showThermal && m != null) {
                drawMarker((m.min.x + 0.5f) / gw, (m.min.y + 0.5f) / gh, pointingDown = true, color = c.markMin)
                drawMarker((m.max.x + 0.5f) / gw, (m.max.y + 0.5f) / gh, pointingDown = false, color = c.markMax)
            }
            for (pt in points.points) drawUserPoint((pt.x + 0.5f) / gw, (pt.y + 0.5f) / gh)
        }
        if (m != null) {
            val centerText = celsius(fmt, m.center)
            val cd = stringResource(R.string.cd_crosshair, centerText)
            ImageLabel(centerText, Modifier.align(Alignment.Center).offset(x = 52.dp, y = (-16).dp).semantics { contentDescription = cd })
            if (showThermal) {
                ImageLabel("▼ " + fmt.deg(m.min.celsius), Modifier.labelAt(m.min.x, m.min.y, gw, gh, sizeDp), c.markMin)
                ImageLabel("▲ " + fmt.deg(m.max.celsius), Modifier.labelAt(m.max.x, m.max.y, gw, gh, sizeDp), c.markMax)
            }
            m.points.forEachIndexed { i, sp ->
                val cdp = stringResource(R.string.cd_point, i + 1, celsius(fmt, sp.celsius))
                ImageLabel("P${i + 1} " + fmt.deg(sp.celsius), Modifier.labelAt(sp.x, sp.y, gw, gh, sizeDp).semantics { contentDescription = cdp })
            }
        }
        if (showThermal) frame.range?.let { r ->
            val cdScale = stringResource(R.string.cd_scale, celsius(fmt, r.min), celsius(fmt, r.max))
            ScaleBar(
                palette, fmt.zero(r.max), fmt.zero(r.min),
                Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).fillMaxHeight(0.62f)
                    .semantics { contentDescription = cdScale },
            )
        }
    }
}

/** Screen position → thermal-grid pixel-centre coordinate, clamped to the grid. */
private fun toGrid(pos: Float, sizePx: Int, grid: Float): Float = (pos / sizePx * grid - 0.5f).coerceIn(0f, grid - 1f)

/** Index of the user point within ~28 dp of [o], or null. */
private fun PointerInputScope.nearestPoint(points: List<UserPoint>, o: Offset, side: Float, gw: Float, gh: Float): Int? {
    val limit = 28.dp.toPx()
    var best = -1
    var bestD = Float.MAX_VALUE
    points.forEachIndexed { i, p ->
        val d = hypot((p.x + 0.5f) / gw * side - o.x, (p.y + 0.5f) / gh * side - o.y)
        if (d < bestD) { bestD = d; best = i }
    }
    return if (best >= 0 && bestD <= limit) best else null
}

/** Draws the part of the visible frame that corresponds to the thermal square (zarovnání překryvu). */
private fun DrawScope.drawVisibleSquare(img: ImageBitmap, crop: CropSquare?) {
    val c = crop ?: CropSquare(img.width / 2f, img.height / 2f, minOf(img.width, img.height).toFloat())
    val k = size.width / c.side
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        rotate(-c.angleDeg, pivot = Offset.Zero)
        scale(k, k, pivot = Offset.Zero)
        translate(-c.cx, -c.cy)
    }) {
        drawImage(img, filterQuality = FilterQuality.Low)
    }
}

private fun DrawScope.drawCrosshair() {
    val cx = size.width / 2
    val cy = size.height / 2
    val arm = size.minDimension * 0.045f
    val gap = arm * 0.35f
    for ((col, w) in listOf(Color.Black.copy(alpha = 0.45f) to 4.dp.toPx(), Color.White to 1.6.dp.toPx())) {
        drawLine(col, Offset(cx - arm, cy), Offset(cx - gap, cy), w)
        drawLine(col, Offset(cx + gap, cy), Offset(cx + arm, cy), w)
        drawLine(col, Offset(cx, cy - arm), Offset(cx, cy - gap), w)
        drawLine(col, Offset(cx, cy + gap), Offset(cx, cy + arm), w)
    }
}

/** [u], [v] in 0..1 of the image. */
private fun DrawScope.drawMarker(u: Float, v: Float, pointingDown: Boolean, color: Color) {
    val px = u * size.width
    val py = v * size.height
    val s = 7.dp.toPx()
    val path = Path().apply {
        if (pointingDown) {
            moveTo(px - s, py - s); lineTo(px + s, py - s); lineTo(px, py + s * 0.6f)
        } else {
            moveTo(px - s, py + s); lineTo(px + s, py + s); lineTo(px, py - s * 0.6f)
        }
        close()
    }
    drawPath(path, color)
    drawPath(path, Color.White, style = Stroke(1.2.dp.toPx()))
}

private fun DrawScope.drawUserPoint(u: Float, v: Float) {
    val p = Offset(u * size.width, v * size.height)
    val r = 7.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.45f), r, p, style = Stroke(4.dp.toPx()))
    drawCircle(Color.White, r, p, style = Stroke(2.dp.toPx()))
}

/** Places a label next to a grid position (pixel-centre coordinates), kept inside the image. */
private fun Modifier.labelAt(gx: Float, gy: Float, gw: Float, gh: Float, side: Dp): Modifier {
    val x = side * ((gx + 0.5f) / gw)
    val y = side * ((gy + 0.5f) / gh)
    val lx = (x + 10.dp).coerceAtMost(side - 76.dp).coerceAtLeast(4.dp)
    val ly = (y - 12.dp).coerceIn(4.dp, side - 28.dp)
    return this.offset(x = lx, y = ly)
}

@Composable
private fun ImageLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val c = ThermoTheme.colors
    Box(modifier.glass(RoundedCornerShape(8.dp), GlassLevel.IMAGE_LABEL).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, style = NumberStyleSmall, color = color ?: c.imgLabelText, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ScaleBar(palette: Palette, top: String, bottom: String, modifier: Modifier) {
    // Top = hot end of the palette.
    val stops = palette.stops.map { (1f - it.position) to Color(it.argb) }.sortedBy { it.first }.toTypedArray()
    Column(modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ImageLabel(top)
        Box(
            Modifier.weight(1f).width(12.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(*stops)),
        )
        ImageLabel(bottom)
    }
}
