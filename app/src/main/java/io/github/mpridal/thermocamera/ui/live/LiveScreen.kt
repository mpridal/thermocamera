package io.github.mpridal.thermocamera.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.camera.CameraState
import io.github.mpridal.thermocamera.camera.ThermalCameraSource
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.core.RangePreset
import io.github.mpridal.thermocamera.data.Settings
import io.github.mpridal.thermocamera.data.ViewMode
import io.github.mpridal.thermocamera.ui.components.AppIcons
import io.github.mpridal.thermocamera.ui.components.GlassChip
import io.github.mpridal.thermocamera.ui.components.GlassIconButton
import io.github.mpridal.thermocamera.ui.components.GlassLevel
import io.github.mpridal.thermocamera.ui.components.NumberFormatter
import io.github.mpridal.thermocamera.ui.components.SegmentedControl
import io.github.mpridal.thermocamera.ui.components.ShutterButton
import io.github.mpridal.thermocamera.ui.components.StatusChip
import io.github.mpridal.thermocamera.ui.components.StatusKind
import io.github.mpridal.thermocamera.ui.components.ValueTile
import io.github.mpridal.thermocamera.ui.components.glass
import io.github.mpridal.thermocamera.ui.components.paletteName
import io.github.mpridal.thermocamera.ui.components.rangePresetName
import io.github.mpridal.thermocamera.ui.components.rememberNumberFormatter
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme
import kotlinx.coroutines.launch

@Composable
fun LiveScreen(
    vm: LiveViewModel,
    onOpenSettings: () -> Unit,
    onOpenCameraTest: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.cameraState.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    val frame by vm.frame.collectAsStateWithLifecycle()
    val fmt = rememberNumberFormatter()
    val scope = rememberCoroutineScope()
    val comingLater = stringResource(R.string.coming_later)
    val notYet: () -> Unit = { scope.launch { snackbar.showSnackbar(comingLater) } }

    // Keep the screen on during the live view (zadání F1).
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val streaming = state is CameraState.Streaming
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            Column(Modifier.fillMaxSize()) {
                TopBar(state, source, frame.fps, fmt, onOpenSettings, onOpenCameraTest)
                if (!streaming) {
                    DisconnectedContent(onStartTest = onOpenCameraTest, onUseSimulator = vm::useSimulator, modifier = Modifier.weight(1f))
                } else if (wide) {
                    Row(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Badges(settings, fmt)
                            Spacer(Modifier.height(6.dp))
                            MeasuringSurface(frame, settings.viewMode, settings.blend, settings.palette, fmt, stacked = false, Modifier.weight(1f))
                        }
                        Column(
                            Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Controls(vm, settings, frame, fmt, notYet)
                            BottomBar(notYet)
                        }
                    }
                } else {
                    Column(
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Badges(settings, fmt)
                        MeasuringSurface(
                            frame, settings.viewMode, settings.blend, settings.palette, fmt, stacked = true,
                            Modifier.fillMaxWidth().weight(1f, fill = false).align(Alignment.CenterHorizontally),
                        )
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Controls(vm, settings, frame, fmt, notYet)
                        }
                    }
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BottomBar(notYet) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}

@Composable
private fun TopBar(
    state: CameraState,
    source: ThermalCameraSource,
    fps: Float,
    fmt: NumberFormatter,
    onOpenSettings: () -> Unit,
    onOpenCameraTest: () -> Unit,
) {
    val fpsText = fmt.zero(fps)
    val (text, kind) = when (state) {
        is CameraState.Streaming -> stringResource(
            if (source.kind == ThermalCameraSource.Kind.SIMULATOR) R.string.status_simulator else R.string.status_connected,
            fpsText,
        ) to StatusKind.OK
        CameraState.Connecting -> stringResource(R.string.status_connecting) to StatusKind.PENDING
        CameraState.NeedsPermission -> stringResource(R.string.status_needs_permission) to StatusKind.PENDING
        CameraState.Disconnected -> stringResource(R.string.status_disconnected) to StatusKind.ERROR
        is CameraState.Error -> stringResource(
            when (state.reason) {
                CameraState.Reason.NO_DATA -> R.string.status_error_no_data
                CameraState.Reason.USB_ERROR -> R.string.status_error_usb
            },
        ) to StatusKind.ERROR
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(text, kind, onClick = onOpenCameraTest, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        GlassIconButton(Icons.Filled.Settings, stringResource(R.string.cd_settings), onOpenSettings)
    }
}

@Composable
private fun Badges(settings: Settings, fmt: NumberFormatter) {
    val c = ThermoTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.glass(RoundedCornerShape(10.dp), GlassLevel.IMAGE_LABEL).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (settings.rangePreset != RangePreset.AUTO) {
                Icon(Icons.Filled.Lock, stringResource(R.string.cd_range_locked), tint = c.imgLabelText, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
            }
            val r = settings.rangePreset.range
            val txt = rangePresetName(settings.rangePreset) + (r?.let { " " + fmt.zero(it.min) + "–" + fmt.zero(it.max) + " °C" } ?: "")
            Text(txt, color = c.imgLabelText, fontSize = 12.sp)
        }
        Box(Modifier.glass(RoundedCornerShape(10.dp), GlassLevel.IMAGE_LABEL).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(stringResource(R.string.badge_emissivity, fmt.two(settings.emissivity)), color = c.imgLabelText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Controls(
    vm: LiveViewModel,
    settings: Settings,
    frame: LiveFrameUi,
    fmt: NumberFormatter,
    notYet: () -> Unit,
) {
    val c = ThermoTheme.colors
    val m = frame.measurements
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ValueTile(stringResource(R.string.label_min), m?.let { fmt.one(it.min.celsius) } ?: "–", "▼", c.coldText, Modifier.weight(1f))
        ValueTile(stringResource(R.string.label_center), m?.let { fmt.one(it.center) } ?: "–", "+", c.text, Modifier.weight(1f))
        ValueTile(stringResource(R.string.label_max), m?.let { fmt.one(it.max.celsius) } ?: "–", "▲", c.hotText, Modifier.weight(1f))
    }
    if (settings.rangePreset == RangePreset.FACADE) {
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), tint = c.warnFill, border = c.warnBorder).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, null, tint = c.warnTitle, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.facade_warning), color = c.warnText, fontSize = 13.sp)
        }
    }
    SegmentedControl(
        options = listOf(
            ViewMode.THERMAL to stringResource(R.string.mode_thermal),
            ViewMode.OVERLAY to stringResource(R.string.mode_overlay),
            ViewMode.SIDE_BY_SIDE to stringResource(R.string.mode_side_by_side),
            ViewMode.PHOTO to stringResource(R.string.mode_photo),
        ),
        selected = settings.viewMode,
        onSelect = { vm.setViewMode(it) },
    )
    if (settings.viewMode == ViewMode.OVERLAY) {
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.blend), color = c.textMuted, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            var local by remember(settings.blend) { mutableStateOf(settings.blend) }
            Slider(
                value = local,
                onValueChange = { local = it },
                onValueChangeFinished = { vm.setBlend(local) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(fmt.zero(local * 100) + " %", color = c.text, fontSize = 13.sp)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            var open by remember { mutableStateOf(false) }
            GlassChip(stringResource(R.string.chip_range, rangePresetName(settings.rangePreset)) + " ▾", { open = true })
            DropdownMenu(open, { open = false }, containerColor = ThermoTheme.colors.menu) {
                RangePreset.entries.filter { it != RangePreset.CUSTOM }.forEach { p ->
                    DropdownMenuItem(text = { Text(rangePresetName(p)) }, onClick = { open = false; vm.setRangePreset(p) })
                }
            }
        }
        Box {
            var open by remember { mutableStateOf(false) }
            GlassChip(paletteName(settings.palette) + " ▾", { open = true }, leading = "◐")
            DropdownMenu(open, { open = false }, containerColor = ThermoTheme.colors.menu) {
                Palette.entries.forEach { p ->
                    DropdownMenuItem(text = { Text(paletteName(p)) }, onClick = { open = false; vm.setPalette(p) })
                }
            }
        }
        GlassChip(stringResource(R.string.action_add_point), notYet, leading = "+")
    }
}

@Composable
private fun BottomBar(notYet: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 104.dp).glass(RoundedCornerShape(30.dp), elevation = 8.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GlassIconButton(AppIcons.Gallery, stringResource(R.string.cd_gallery), notYet)
        ShutterButton(stringResource(R.string.cd_shutter), notYet)
        GlassIconButton(AppIcons.Video, stringResource(R.string.cd_video), notYet)
    }
}

@Composable
private fun DisconnectedContent(onStartTest: () -> Unit, onUseSimulator: () -> Unit, modifier: Modifier = Modifier) {
    val c = ThermoTheme.colors
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ModuleIllustration()
        Text(stringResource(R.string.disconnected_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = c.text)
        Text(stringResource(R.string.disconnected_body), color = c.textMuted, fontSize = 14.sp)
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
            Text(stringResource(R.string.disconnected_help_title), color = c.text, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.disconnected_help_body), color = c.textMuted, fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.accent)
                    .clickable(onClick = onStartTest)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.action_start_camera_test), color = c.onAccent, fontSize = 14.sp) }
            GlassChip(stringResource(R.string.action_use_simulator), onUseSimulator)
        }
    }
}

@Composable
private fun ModuleIllustration() {
    val c = ThermoTheme.colors
    androidx.compose.foundation.Canvas(Modifier.size(width = 160.dp, height = 96.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(c.glassBorder, topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.62f, h * 0.7f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f))
        drawCircle(c.accent, radius = h * 0.16f, center = androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.5f))
        drawCircle(c.textMuted, radius = h * 0.09f, center = androidx.compose.ui.geometry.Offset(w * 0.52f, h * 0.5f))
        drawRoundRect(c.textMuted, topLeft = androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.4f), size = androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f))
    }
}
