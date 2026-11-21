package io.github.mpridal.thermocamera.ui.align

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.core.OverlayAlignment
import io.github.mpridal.thermocamera.data.ViewMode
import io.github.mpridal.thermocamera.ui.components.GlassChip
import io.github.mpridal.thermocamera.ui.components.GlassIconButton
import io.github.mpridal.thermocamera.ui.components.rememberNumberFormatter
import io.github.mpridal.thermocamera.ui.live.LiveViewModel
import io.github.mpridal.thermocamera.ui.live.MeasuringSurface
import io.github.mpridal.thermocamera.ui.live.PointActions
import io.github.mpridal.thermocamera.ui.theme.NumberStyleSmall
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme

/**
 * Overlay alignment wizard for the module camera (zadání F1): the module camera is fixed to the
 * thermal sensor, so a one-off shift / scale / rotation is enough. Gestures move the normal image
 * under the thermal one; the result is stored on "Done".
 */
@Composable
fun AlignmentScreen(liveVm: LiveViewModel, onBack: () -> Unit) {
    val c = ThermoTheme.colors
    val fmt = rememberNumberFormatter()
    val settings by liveVm.settings.collectAsStateWithLifecycle()
    val frame by liveVm.frame.collectAsStateWithLifecycle()
    var align by remember(settings.moduleAlignment) { mutableStateOf(settings.moduleAlignment) }
    val noPoints = remember { PointActions(emptyList(), { _, _ -> }, { _, _, _ -> }, {}) }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), onBack)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.align_title), style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 640.dp).align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.align_hint), color = c.text, fontSize = 14.sp)
            val visible = frame.visible
            if (visible == null) {
                Text(stringResource(R.string.align_needs_camera), color = c.warnText, fontSize = 14.sp)
            } else {
                val shown = frame.copy(visibleCrop = align.cropSquare(visible.width, visible.height))
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    MeasuringSurface(shown, ViewMode.OVERLAY, 0.5f, settings.palette, fmt, stacked = true, points = noPoints, modifier = Modifier.fillMaxSize())
                    // Gesture layer on top: moves the normal image relative to the thermal one.
                    Box(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                val side = size.width.toFloat()
                                val a = align
                                // Image moves by pan → crop window moves the opposite way (in image heights).
                                align = a.copy(
                                    dx = a.dx - pan.x / side * a.scale,
                                    dy = a.dy - pan.y / side * a.scale,
                                    scale = (a.scale / zoom).coerceIn(0.2f, 5f),
                                    angleDeg = a.angleDeg - rotation,
                                )
                            }
                        },
                    )
                }
            }
            Text(
                stringResource(R.string.align_values, fmt.two(align.dx), fmt.two(align.dy), fmt.two(align.scale), fmt.one(align.angleDeg)),
                color = c.textMuted, style = NumberStyleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(stringResource(R.string.align_reset), { align = OverlayAlignment.DEFAULT })
                Box(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(c.accent)
                        .clickable { liveVm.setModuleAlignment(align); onBack() }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.action_done), color = c.onAccent, fontSize = 14.sp) }
            }
        }
    }
}
