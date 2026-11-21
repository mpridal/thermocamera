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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import io.github.mpridal.thermocamera.ui.theme.NumberStyle
import androidx.compose.foundation.horizontalScroll
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
import io.github.mpridal.thermocamera.data.PhotoCamera
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
import io.github.mpridal.thermocamera.ui.components.celsius
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
    onChoosePhotoCamera: (PhotoCamera) -> Unit,
    snackbar: SnackbarHostState,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.cameraState.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    val frame by vm.frame.collectAsStateWithLifecycle()
    val points by vm.points.collectAsStateWithLifecycle()
    val frozen by vm.frozen.collectAsStateWithLifecycle()
    val pointActions = PointActions(points, vm::addPoint, vm::movePoint, vm::removePoint)
    val torch by vm.torch.collectAsStateWithLifecycle()
    val phoneActive by vm.phoneActive.collectAsStateWithLifecycle()
    val phoneInfo by vm.phoneInfo.collectAsStateWithLifecycle()
    val alignTip by vm.alignTip.collectAsStateWithLifecycle()
    val offer by vm.showBetterCameraOffer.collectAsStateWithLifecycle()
    val fmt = rememberNumberFormatter()
    val scope = rememberCoroutineScope()
    val comingLater = stringResource(R.string.coming_later)
    val notYet: () -> Unit = { scope.launch { snackbar.showSnackbar(comingLater) } }
    val photoText = stringResource(R.string.photo_saved, "%s")
    val mpFormat = stringResource(R.string.megapixels, "%s")
    // Shutter: with the phone camera a full-resolution photo; saving measurement snapshots comes in Phase 4.
    val onShutter: () -> Unit = {
        scope.launch {
            val mp = vm.takePhonePhoto()
            if (mp == null) snackbar.showSnackbar(comingLater)
            else snackbar.showSnackbar(photoText.replace("%s", mpFormat.replace("%s", fmt.zero(mp))))
        }
    }
    val photoBadge: String? = if (phoneActive && settings.photoCamera != PhotoCamera.MODULE) {
        phoneInfo?.let { stringResource(R.string.badge_photo, stringResource(R.string.megapixels, fmt.zero(it.megapixels))) }
    } else null

    // Keep the screen on during the live view (zadání F1).
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val streaming = state is CameraState.Streaming
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Layout by window size class (zadání kap. 7.5): compact height = phone landscape,
            // compact width = phone portrait, otherwise tablet (panel right in landscape, below in portrait).
            val layout = when {
                maxHeight < 480.dp -> LiveLayout.PHONE_LANDSCAPE
                maxWidth < 600.dp -> LiveLayout.PHONE_PORTRAIT
                maxWidth > maxHeight -> LiveLayout.TABLET_LANDSCAPE
                else -> LiveLayout.TABLET_PORTRAIT
            }
            val topBar: @Composable () -> Unit = {
                TopBar(
                    state, source, frame.fps, fmt, onOpenSettings, onOpenCameraTest,
                    torchAvailable = vm.torchAvailable, torch = torch, onTorch = vm::toggleTorch,
                )
            }
            val surface: @Composable (Modifier, Boolean) -> Unit = { m, stacked ->
                MeasuringSurface(frame, settings.viewMode, settings.blend, settings.palette, fmt, stacked = stacked, pointActions, m)
            }
            when {
                !streaming -> Column(Modifier.fillMaxSize()) {
                    topBar()
                    DisconnectedContent(onStartTest = onOpenCameraTest, onUseSimulator = vm::useSimulator, modifier = Modifier.weight(1f))
                }
                layout == LiveLayout.PHONE_PORTRAIT -> Column(Modifier.fillMaxSize()) {
                    topBar()
                    Column(
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Badges(settings, fmt, photoBadge)
                        if (alignTip) AlignTip()
                        surface(Modifier.fillMaxWidth().weight(1f, fill = false).align(Alignment.CenterHorizontally), true)
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Controls(vm, settings, frame, fmt, notYet, frozen)
                        }
                    }
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BottomBar(notYet, onShutter) }
                }
                layout == LiveLayout.PHONE_LANDSCAPE -> Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Narrow strip: status chip and values.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusOnly(state, source, frame.fps, fmt, onOpenCameraTest)
                            CompactValues(frame, fmt)
                        }
                        if (alignTip) AlignTip()
                        surface(Modifier.weight(1f).align(Alignment.CenterHorizontally), false)
                    }
                    SidePanel(
                        vm, settings, torch, onOpenSettings, notYet, onShutter,
                        Modifier.width(140.dp).fillMaxHeight(),
                    )
                }
                else -> Row(Modifier.fillMaxSize()) {
                    NavRail(onOpenCameraTest = onOpenCameraTest, onOpenSettings = onOpenSettings, notYet = notYet)
                    val portrait = layout == LiveLayout.TABLET_PORTRAIT
                    Column(Modifier.weight(1f).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        topBar()
                        ModeSelector(settings.viewMode, vm::setViewMode)
                        Badges(settings, fmt, photoBadge)
                        if (alignTip) AlignTip()
                        Row(
                            Modifier.then(if (portrait) Modifier.fillMaxWidth() else Modifier.weight(1f)),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            surface(Modifier.weight(1f, fill = false).widthIn(max = 660.dp).fillMaxHeight(), false)
                            VerticalPill(notYet, onShutter)
                        }
                        if (settings.viewMode == ViewMode.OVERLAY) BlendSlider(settings.blend, vm::setBlend, fmt)
                        if (portrait) {
                            Column(
                                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) { TabletPanelContent(vm, settings, frame, points, fmt, frozen) }
                        }
                    }
                    if (!portrait) {
                        Column(
                            Modifier.width(336.dp).fillMaxHeight().padding(vertical = 12.dp).padding(end = 12.dp)
                                .glass(RoundedCornerShape(24.dp)).verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) { TabletPanelContent(vm, settings, frame, points, fmt, frozen) }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
        if (offer) {
            BetterCameraSheet(
                moduleText = stringResource(R.string.better_camera_module, stringResource(R.string.megapixels, fmt.two(0.31f))),
                phoneText = phoneInfo?.let { stringResource(R.string.better_camera_phone, stringResource(R.string.megapixels, fmt.zero(it.megapixels))) } ?: "",
                preview = frame,
                onUsePhone = { vm.answerBetterCamera(); onChoosePhotoCamera(PhotoCamera.PHONE) },
                onKeepModule = { vm.answerBetterCamera() },
            )
        }
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
    torchAvailable: Boolean,
    torch: Boolean,
    onTorch: () -> Unit,
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
        if (torchAvailable) {
            GlassIconButton(AppIcons.Flash, stringResource(R.string.cd_flashlight), onTorch, active = torch)
            Spacer(Modifier.width(4.dp))
        }
        GlassIconButton(Icons.Filled.Settings, stringResource(R.string.cd_settings), onOpenSettings)
    }
}

@Composable
private fun Badges(settings: Settings, fmt: NumberFormatter, photoBadge: String?) {
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
        if (photoBadge != null) {
            Box(Modifier.glass(RoundedCornerShape(10.dp), GlassLevel.IMAGE_LABEL).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(photoBadge, color = c.imgLabelText, fontSize = 12.sp)
            }
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
    frozen: Boolean,
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
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        GlassChip(stringResource(R.string.action_add_point), { vm.addPoint(15.5f, 15.5f) }, leading = "+")
        GlassChip(
            stringResource(if (frozen) R.string.action_unfreeze else R.string.action_freeze),
            { vm.setFrozen(!frozen) },
            leading = if (frozen) "▶" else "❚❚",
        )
    }
}

@Composable
private fun BottomBar(notYet: () -> Unit, onShutter: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 104.dp).glass(RoundedCornerShape(30.dp), elevation = 8.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GlassIconButton(AppIcons.Gallery, stringResource(R.string.cd_gallery), notYet)
        ShutterButton(stringResource(R.string.cd_shutter), onShutter)
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

@Composable
private fun AlignTip() {
    val c = ThermoTheme.colors
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), tint = c.warnFill, border = c.warnBorder).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, null, tint = c.warnTitle, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.align_failing_tip), color = c.warnText, fontSize = 13.sp)
    }
}

private enum class LiveLayout { PHONE_PORTRAIT, PHONE_LANDSCAPE, TABLET_PORTRAIT, TABLET_LANDSCAPE }

/** Status chip alone (phone landscape strip). */
@Composable
private fun StatusOnly(state: CameraState, source: ThermalCameraSource, fps: Float, fmt: NumberFormatter, onClick: () -> Unit) {
    val (text, kind) = statusText(state, source, fmt.zero(fps))
    StatusChip(text, kind, onClick = onClick)
}

@Composable
private fun statusText(state: CameraState, source: ThermalCameraSource, fpsText: String): Pair<String, StatusKind> = when (state) {
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

/** MIN / CENTER / MAX in one compact glass row. */
@Composable
private fun CompactValues(frame: LiveFrameUi, fmt: NumberFormatter) {
    val c = ThermoTheme.colors
    val m = frame.measurements
    Row(
        Modifier.glass(RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▼ " + (m?.let { fmt.one(it.min.celsius) } ?: "–"), color = c.coldText, style = NumberStyle, fontSize = 14.sp)
        Text("+ " + (m?.let { fmt.one(it.center) } ?: "–"), color = c.text, style = NumberStyle, fontSize = 14.sp)
        Text("▲ " + (m?.let { fmt.one(it.max.celsius) } ?: "–"), color = c.hotText, style = NumberStyle, fontSize = 14.sp)
    }
}

/** Phone landscape: vertical glass panel 140 dp – torch + settings, mode, shutter, gallery. */
@Composable
private fun SidePanel(
    vm: LiveViewModel,
    settings: Settings,
    torch: Boolean,
    onOpenSettings: () -> Unit,
    notYet: () -> Unit,
    onShutter: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.glass(RoundedCornerShape(28.dp)).verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (vm.torchAvailable) GlassIconButton(AppIcons.Flash, stringResource(R.string.cd_flashlight), vm::toggleTorch, active = torch)
            GlassIconButton(Icons.Filled.Settings, stringResource(R.string.cd_settings), onOpenSettings)
        }
        VerticalModes(settings.viewMode, vm::setViewMode)
        ShutterButton(stringResource(R.string.cd_shutter), onShutter)
        GlassIconButton(AppIcons.Gallery, stringResource(R.string.cd_gallery), notYet)
    }
}

@Composable
private fun modeOptions() = listOf(
    ViewMode.THERMAL to stringResource(R.string.mode_thermal),
    ViewMode.OVERLAY to stringResource(R.string.mode_overlay),
    ViewMode.SIDE_BY_SIDE to stringResource(R.string.mode_side_by_side),
    ViewMode.PHOTO to stringResource(R.string.mode_photo),
)

@Composable
private fun ModeSelector(selected: ViewMode, onSelect: (ViewMode) -> Unit) {
    SegmentedControl(options = modeOptions(), selected = selected, onSelect = onSelect)
}

/** Modes stacked vertically for the narrow side panel. */
@Composable
private fun VerticalModes(selected: ViewMode, onSelect: (ViewMode) -> Unit) {
    val c = ThermoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
        modeOptions().forEach { (mode, label) ->
            val sel = mode == selected
            Box(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
                    .then(if (sel) Modifier.background(c.accent) else Modifier)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) c.onAccent else c.segOff, fontSize = 12.sp, lineHeight = 13.sp, maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/** Tablet: vertical glass pill next to the image – gallery, shutter, video. */
@Composable
private fun VerticalPill(notYet: () -> Unit, onShutter: () -> Unit) {
    Column(
        Modifier.width(104.dp).glass(RoundedCornerShape(30.dp), elevation = 8.dp).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GlassIconButton(AppIcons.Gallery, stringResource(R.string.cd_gallery), notYet)
        ShutterButton(stringResource(R.string.cd_shutter), onShutter)
        GlassIconButton(AppIcons.Video, stringResource(R.string.cd_video), notYet)
    }
}

@Composable
private fun BlendSlider(blend: Float, onChange: (Float) -> Unit, fmt: NumberFormatter) {
    val c = ThermoTheme.colors
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.blend), color = c.textMuted, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        var local by remember(blend) { mutableStateOf(blend) }
        Slider(
            value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(fmt.zero(local * 100) + " %", color = c.text, fontSize = 13.sp)
    }
}

/** Tablet navigation rail. Gallery and calibration arrive in later phases. */
@Composable
private fun NavRail(onOpenCameraTest: () -> Unit, onOpenSettings: () -> Unit, notYet: () -> Unit) {
    val c = ThermoTheme.colors
    Column(
        Modifier.width(96.dp).fillMaxHeight().padding(8.dp).glass(RoundedCornerShape(24.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RailItem("◉", stringResource(R.string.rail_camera), selected = true) {}
        RailItem("▦", stringResource(R.string.cd_gallery), selected = false, onClick = notYet)
        RailItem("◐", stringResource(R.string.rail_calibration), selected = false, onClick = notYet)
        RailItem("✓", stringResource(R.string.rail_test), selected = false, onClick = onOpenCameraTest)
        Spacer(Modifier.weight(1f))
        RailItem("⚙", stringResource(R.string.cd_settings), selected = false, onClick = onOpenSettings)
    }
}

@Composable
private fun RailItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = ThermoTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .then(if (selected) Modifier.background(c.glassHi) else Modifier)
            .clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, color = if (selected) c.accent else c.text, fontSize = 20.sp)
        // Labels may wrap to two lines, never get cut (zadání kap. 7.5 – "Einstellungen").
        Text(label, color = c.text, fontSize = 11.sp, lineHeight = 12.sp, maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Tablet right panel (landscape) or panel below the image (portrait). */
@Composable
private fun TabletPanelContent(
    vm: LiveViewModel,
    settings: Settings,
    frame: LiveFrameUi,
    points: List<UserPoint>,
    fmt: NumberFormatter,
    frozen: Boolean,
) {
    val c = ThermoTheme.colors
    val m = frame.measurements
    Text(stringResource(R.string.panel_measurement).uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
        style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ValueTile(stringResource(R.string.label_min), m?.let { fmt.one(it.min.celsius) } ?: "–", "▼", c.coldText, Modifier.weight(1f))
        ValueTile(stringResource(R.string.label_center), m?.let { fmt.one(it.center) } ?: "–", "+", c.text, Modifier.weight(1f))
        ValueTile(stringResource(R.string.label_max), m?.let { fmt.one(it.max.celsius) } ?: "–", "▲", c.hotText, Modifier.weight(1f))
    }
    m?.points?.forEachIndexed { i, sp ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)) {
            Text("○ P${i + 1}", color = c.text, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(celsius(fmt, sp.celsius), color = c.text, style = NumberStyle, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { vm.removePoint(i) }, contentAlignment = Alignment.Center) {
                Text("✕", color = c.textMuted, fontSize = 16.sp)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (points.size < LiveViewModel.MAX_POINTS) GlassChip(stringResource(R.string.action_add_point), { vm.addPoint(15.5f, 15.5f) }, leading = "+")
        GlassChip(
            stringResource(if (frozen) R.string.action_unfreeze else R.string.action_freeze),
            { vm.setFrozen(!frozen) },
            leading = if (frozen) "▶" else "❚❚",
        )
    }
    if (settings.rangePreset == RangePreset.FACADE) FacadeWarning()
    Text(stringResource(R.string.panel_range).uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
        style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RangePreset.entries.filter { it != RangePreset.CUSTOM }.forEach { p ->
            val sel = p == settings.rangePreset
            Box(
                Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
                    .then(if (sel) Modifier.background(c.accent) else Modifier.glass(RoundedCornerShape(20.dp)))
                    .clickable { vm.setRangePreset(p) }.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(rangePresetName(p), color = if (sel) c.onAccent else c.text, fontSize = 13.sp) }
        }
    }
    Text(stringResource(R.string.panel_palette).uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
        style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Palette.entries.forEach { p ->
            val sel = p == settings.palette
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { vm.setPalette(p) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(*p.stops.map { it.position to androidx.compose.ui.graphics.Color(it.argb) }.toTypedArray()))
                        .then(if (sel) Modifier.border(2.dp, c.accent, RoundedCornerShape(8.dp)) else Modifier),
                )
                Text(paletteName(p), color = c.text, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FacadeWarning() {
    val c = ThermoTheme.colors
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), tint = c.warnFill, border = c.warnBorder).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, null, tint = c.warnTitle, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.facade_warning), color = c.warnText, fontSize = 13.sp)
    }
}
