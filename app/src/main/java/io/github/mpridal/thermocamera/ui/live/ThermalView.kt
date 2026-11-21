package io.github.mpridal.thermocamera.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.data.ViewMode
import io.github.mpridal.thermocamera.ui.components.GlassLevel
import io.github.mpridal.thermocamera.ui.components.NumberFormatter
import io.github.mpridal.thermocamera.ui.components.celsius
import io.github.mpridal.thermocamera.ui.components.glass
import io.github.mpridal.thermocamera.ui.theme.NumberStyleSmall
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme

/**
 * The measuring surface. Never translucent or blurred (zadání kap. 7.1).
 * Side by side: [stacked] = one above the other (phone portrait), otherwise next to each other.
 */
@Composable
fun MeasuringSurface(
    frame: LiveFrameUi,
    viewMode: ViewMode,
    blend: Float,
    palette: Palette,
    fmt: NumberFormatter,
    stacked: Boolean,
    modifier: Modifier = Modifier,
) {
    if (viewMode == ViewMode.SIDE_BY_SIDE) {
        val content: @Composable (Modifier) -> Unit = { m ->
            ImageSquare(frame, ViewMode.THERMAL, blend, palette, fmt, m)
            ImageSquare(frame, ViewMode.PHOTO, blend, palette, fmt, m)
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
        ImageSquare(frame, viewMode, blend, palette, fmt, modifier.aspectRatio(1f))
    }
}

@Composable
private fun ImageSquare(
    frame: LiveFrameUi,
    viewMode: ViewMode,
    blend: Float,
    palette: Palette,
    fmt: NumberFormatter,
    modifier: Modifier,
) {
    val c = ThermoTheme.colors
    val m = frame.measurements
    val showThermal = viewMode != ViewMode.PHOTO
    BoxWithConstraints(modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
        val sizeDp = maxWidth
        val frameNo = frame.frameNo // read so that reused bitmaps redraw
        Canvas(Modifier.fillMaxSize()) {
            frameNo.hashCode()
            if (viewMode != ViewMode.THERMAL) frame.visible?.let { drawVisibleSquare(it) }
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
                drawMarker(m.min.x, m.min.y, pointingDown = true, color = c.markMin)
                drawMarker(m.max.x, m.max.y, pointingDown = false, color = c.markMax)
            }
        }
        if (m != null) {
            val centerText = celsius(fmt, m.center)
            val cd = stringResource(R.string.cd_crosshair, centerText)
            ImageLabel(centerText, Modifier.align(Alignment.Center).offset(x = 52.dp, y = (-16).dp).semantics { contentDescription = cd })
            if (showThermal) {
                ImageLabel("▼ " + fmt.deg(m.min.celsius), Modifier.labelAt(m.min.x, m.min.y, sizeDp), c.markMin)
                ImageLabel("▲ " + fmt.deg(m.max.celsius), Modifier.labelAt(m.max.x, m.max.y, sizeDp), c.markMax)
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

/** Centre-crops the 4:3 visible frame to the square thermal field of view. */
private fun DrawScope.drawVisibleSquare(img: ImageBitmap) {
    val side = minOf(img.width, img.height)
    drawImage(
        img,
        srcOffset = IntOffset((img.width - side) / 2, (img.height - side) / 2),
        srcSize = IntSize(side, side),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.Low,
    )
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

private fun DrawScope.drawMarker(gx: Float, gy: Float, pointingDown: Boolean, color: Color) {
    val px = (gx + 0.5f) / GRID * size.width
    val py = (gy + 0.5f) / GRID * size.height
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

private const val GRID = 32f

/** Places a label next to a grid position, kept inside the image. */
private fun Modifier.labelAt(gx: Float, gy: Float, side: Dp): Modifier {
    val x = side * ((gx + 0.5f) / GRID)
    val y = side * ((gy + 0.5f) / GRID)
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
