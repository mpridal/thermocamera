package io.github.mpridal.thermocamera.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.data.ViewMode
import io.github.mpridal.thermocamera.ui.components.GlassChip
import io.github.mpridal.thermocamera.ui.components.rememberNumberFormatter
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme

/**
 * "Your phone has a better camera" (zadání F11 bod 2). Shown once per device after the first
 * successful HT-102 connection. The phone image itself can only be shown after the CAMERA
 * permission, which is asked for when the user chooses it – so the sheet shows the module view
 * and both resolutions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetterCameraSheet(
    moduleText: String,
    phoneText: String,
    preview: LiveFrameUi,
    onUsePhone: () -> Unit,
    onKeepModule: () -> Unit,
) {
    val c = ThermoTheme.colors
    val fmt = rememberNumberFormatter()
    val noPoints = remember { PointActions(emptyList(), { _, _ -> }, { _, _, _ -> }, {}) }
    ModalBottomSheet(
        onDismissRequest = onKeepModule,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.menu,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(io.github.mpridal.thermocamera.ui.components.LocalHazeState provides null) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.better_camera_title), style = MaterialTheme.typography.titleLarge, color = c.text)
            if (preview.visible != null) {
                MeasuringSurface(
                    preview, ViewMode.PHOTO, 0f, Palette.IRON, fmt, stacked = true, points = noPoints,
                    modifier = Modifier.width(200.dp),
                )
            }
            Text(moduleText, color = c.textMuted, fontSize = 14.sp)
            Text(phoneText, color = c.text, fontSize = 16.sp)
            Text(stringResource(R.string.better_camera_tip), color = c.text, fontSize = 14.sp, textAlign = TextAlign.Center)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(c.accent)
                    .clickable(onClick = onUsePhone).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.better_camera_use), color = c.onAccent, fontSize = 15.sp) }
            GlassChip(stringResource(R.string.better_camera_keep), onKeepModule)
            Text(stringResource(R.string.better_camera_note), color = c.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
    }
}
