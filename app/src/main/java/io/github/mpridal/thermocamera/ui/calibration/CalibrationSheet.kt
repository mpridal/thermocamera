package io.github.mpridal.thermocamera.ui.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.core.Correction
import io.github.mpridal.thermocamera.core.RangePreset
import io.github.mpridal.thermocamera.core.ThermalBridge
import io.github.mpridal.thermocamera.data.Settings
import io.github.mpridal.thermocamera.data.SettingsRepository
import io.github.mpridal.thermocamera.data.db.CalibrationProfileDao
import io.github.mpridal.thermocamera.data.db.CalibrationProfileEntity
import io.github.mpridal.thermocamera.ui.components.GlassChip
import io.github.mpridal.thermocamera.ui.components.GlassLevel
import io.github.mpridal.thermocamera.ui.components.NumberFormatter
import io.github.mpridal.thermocamera.ui.components.NumberStepper
import io.github.mpridal.thermocamera.ui.components.celsius
import io.github.mpridal.thermocamera.ui.components.glass
import io.github.mpridal.thermocamera.ui.components.rangePresetName
import io.github.mpridal.thermocamera.ui.components.rememberNumberFormatter
import io.github.mpridal.thermocamera.ui.live.LiveViewModel
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings and calibration profiles for the "Range and calibration" sheet (zadání F2, F3). */
@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val profilesDao: CalibrationProfileDao,
) : ViewModel() {
    val settings: StateFlow<Settings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val profiles: StateFlow<List<CalibrationProfileEntity>> = profilesDao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setCustom(min: Float, max: Float) = viewModelScope.launch { repo.setCustomRange(min, max); repo.setActiveProfile("") }
    fun setEmissivity(v: Float) = viewModelScope.launch { repo.setEmissivity(v); repo.setActiveProfile("") }
    fun setReflected(v: Float) = viewModelScope.launch { repo.setReflected(v); repo.setActiveProfile("") }
    fun setCalibration(offset: Float, gain: Float) = viewModelScope.launch { repo.setCalibration(offset, gain); repo.setActiveProfile("") }
    fun setBridge(i: Float, e: Float, l: Float) = viewModelScope.launch { repo.setBridge(i, e, l) }
    fun setIsotherm(v: Boolean) = viewModelScope.launch { repo.setIsotherm(v) }

    fun saveProfile(name: String) = viewModelScope.launch {
        val s = settings.value
        profilesDao.insert(
            CalibrationProfileEntity(
                name = name.trim(), rangePreset = s.rangePreset.name, customMin = s.customMin, customMax = s.customMax,
                emissivity = s.emissivity, reflected = s.reflected, offset = s.calOffset, gain = s.calGain,
            ),
        )
        repo.setActiveProfile(name.trim())
    }

    fun applyProfile(p: CalibrationProfileEntity) = viewModelScope.launch { repo.applyProfile(p) }
    fun deleteProfile(p: CalibrationProfileEntity) = viewModelScope.launch { profilesDao.delete(p) }
}

private data class Material(val label: Int, val emissivity: Float)

private val MATERIALS = listOf(
    Material(R.string.material_paint, 0.95f),
    Material(R.string.material_brick, 0.93f),
    Material(R.string.material_glass, 0.92f),
    Material(R.string.material_wood, 0.90f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationSheet(vm: CalibrationViewModel, liveVm: LiveViewModel, onDismiss: () -> Unit) {
    val c = ThermoTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.menu,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        // No backdrop blur inside the sheet – it would show the live image behind the sheet.
        androidx.compose.runtime.CompositionLocalProvider(io.github.mpridal.thermocamera.ui.components.LocalHazeState provides null) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 640.dp).align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(R.string.cal_title), style = MaterialTheme.typography.titleLarge, color = c.text)
                CalibrationContent(vm, liveVm)
            }
        }
    }
}

@Composable
fun CalibrationContent(vm: CalibrationViewModel, liveVm: LiveViewModel) {
    val c = ThermoTheme.colors
    val fmt = rememberNumberFormatter()
    val s by vm.settings.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]

    // ---- Range ----
    SectionTitle(stringResource(R.string.cal_section_range))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RangePreset.entries.forEach { p -> SelectChip(rangePresetName(p), p == s.rangePreset) { liveVm.setRangePreset(p) } }
    }
    if (s.rangePreset == RangePreset.CUSTOM) {
        NumberStepper(stringResource(R.string.cal_min), s.customMin, { vm.setCustom(it, maxOf(s.customMax, it + 0.5f)) }, 0.1f, -20f, 299.5f, 1, fmt, unit = "°C")
        NumberStepper(stringResource(R.string.cal_max), s.customMax, { vm.setCustom(minOf(s.customMin, it - 0.5f), it) }, 0.1f, -19.5f, 300f, 1, fmt, unit = "°C")
    }
    if (s.rangePreset == RangePreset.AUTO) {
        SwitchLine(stringResource(R.string.cal_lock_range), stringResource(R.string.cal_lock_range_summary), s.rangeLocked) { liveVm.setRangeLocked(it) }
    }
    if (s.rangePreset == RangePreset.FACADE) Note(stringResource(R.string.facade_warning), warn = true)

    // ---- Emissivity ----
    SectionTitle(stringResource(R.string.cal_section_emissivity))
    Row(verticalAlignment = Alignment.CenterVertically) {
        var local by remember(s.emissivity) { mutableStateOf(s.emissivity) }
        Slider(
            value = local, onValueChange = { local = it }, onValueChangeFinished = { vm.setEmissivity((local * 100).toInt() / 100f) },
            valueRange = 0.10f..1.00f, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
        )
        Spacer(Modifier.width(8.dp))
        Text("ε " + fmt.two(local), color = c.text, fontSize = 15.sp)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MATERIALS.forEach { m ->
            SelectChip(stringResource(m.label) + " " + fmt.two(m.emissivity), kotlin.math.abs(s.emissivity - m.emissivity) < 0.005f) { vm.setEmissivity(m.emissivity) }
        }
    }
    Note(stringResource(R.string.cal_materials_note))
    NumberStepper(stringResource(R.string.cal_reflected), s.reflected, vm::setReflected, 0.5f, -40f, 100f, 1, fmt, unit = "°C")

    // ---- Calibration by reference ----
    SectionTitle(stringResource(R.string.cal_section_calibration))
    Note(stringResource(R.string.cal_hint))
    var actual1 by remember { mutableStateOf(36.0f) }
    var actual2 by remember { mutableStateOf(0.0f) }
    var measured1 by remember { mutableStateOf<Float?>(null) }
    var measured2 by remember { mutableStateOf<Float?>(null) }
    var measuring by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<Int?>(null) }
    for (ref in 1..2) {
        val actual = if (ref == 1) actual1 else actual2
        val measured = if (ref == 1) measured1 else measured2
        NumberStepper(
            stringResource(R.string.cal_reference, ref), actual,
            { if (ref == 1) actual1 = it else actual2 = it }, 0.1f, -20f, 300f, 1, fmt, unit = "°C",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassChip(stringResource(if (measuring == ref) R.string.cal_measuring else R.string.cal_measure), {
                if (measuring == 0) {
                    measuring = ref; error = null
                    scope.launch {
                        val v = liveVm.measureReference()
                        if (v == null) error = R.string.cal_no_data
                        if (ref == 1) measured1 = v else measured2 = v
                        measuring = 0
                    }
                }
            })
            measured?.let { Text(stringResource(R.string.cal_measured, celsius(fmt, it)), color = c.text, fontSize = 14.sp) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        measured1?.let { m1 ->
            AccentButton(stringResource(R.string.cal_apply_one)) { vm.setCalibration(Correction.offsetFor(actual1, m1), 1f) }
        }
    }
    if (measured1 != null && measured2 != null) {
        AccentButton(stringResource(R.string.cal_apply_two)) {
            val m1 = measured1!!; val m2 = measured2!!
            if (kotlin.math.abs(m2 - m1) <= 0.5f) {
                error = R.string.cal_too_close
            } else {
                val (gain, offset) = Correction.twoPoint(actual1, m1, actual2, m2)
                vm.setCalibration(offset, gain)
            }
        }
    }
    error?.let { Note(stringResource(it), warn = true) }
    val offText = (if (s.calOffset >= 0) "+" else "") + fmt.one(s.calOffset) + "\u00a0°C"
    Text(stringResource(R.string.cal_current, offText, String.format(locale, "%.3f", s.calGain)), color = c.text, fontSize = 14.sp)
    if (s.calOffset != 0f || s.calGain != 1f) GlassChip(stringResource(R.string.cal_reset), { vm.setCalibration(0f, 1f) })

    // ---- Thermal bridge ----
    SectionTitle(stringResource(R.string.cal_section_bridge))
    NumberStepper(stringResource(R.string.bridge_inside), s.insideTemp, { vm.setBridge(it, s.outsideTemp, s.fRsiLimit) }, 0.5f, -30f, 40f, 1, fmt, unit = "°C")
    NumberStepper(stringResource(R.string.bridge_outside), s.outsideTemp, { vm.setBridge(s.insideTemp, it, s.fRsiLimit) }, 0.5f, -40f, 40f, 1, fmt, unit = "°C")
    NumberStepper(stringResource(R.string.bridge_limit), s.fRsiLimit, { vm.setBridge(s.insideTemp, s.outsideTemp, it) }, 0.01f, 0.3f, 0.95f, 2, fmt)
    if (!ThermalBridge.isReliable(s.insideTemp, s.outsideTemp)) Note(stringResource(R.string.bridge_small_difference), warn = true)
    SwitchLine(stringResource(R.string.bridge_isotherm), "", s.isotherm, vm::setIsotherm)
    Note(stringResource(R.string.bridge_note))

    // ---- Profiles ----
    SectionTitle(stringResource(R.string.cal_section_profiles))
    if (profiles.isEmpty()) Note(stringResource(R.string.profile_none))
    profiles.forEach { p ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((if (p.name == s.activeProfile) "● " else "") + p.name, color = c.text, fontSize = 15.sp)
                Text(rangePresetName(RangePreset.entries.firstOrNull { it.name == p.rangePreset } ?: RangePreset.AUTO) + " · ε " + fmt.two(p.emissivity), color = c.textMuted, fontSize = 12.sp)
            }
            GlassChip(stringResource(R.string.profile_apply), { vm.applyProfile(p) })
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_delete)) { vm.deleteProfile(p) },
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = c.textMuted, fontSize = 16.sp) }
        }
    }
    var name by remember { mutableStateOf("") }
    Text(stringResource(R.string.profile_name), color = c.textMuted, fontSize = 13.sp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).heightIn(min = 48.dp).glass(RoundedCornerShape(12.dp), GlassLevel.HI).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (name.isNotBlank()) AccentButton(stringResource(R.string.profile_save)) { vm.saveProfile(name); name = "" }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val c = ThermoTheme.colors
    Text(text.uppercase(LocalConfiguration.current.locales[0]), style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Note(text: String, warn: Boolean = false) {
    val c = ThermoTheme.colors
    if (warn) {
        Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(12.dp), tint = c.warnFill, border = c.warnBorder).padding(10.dp)) {
            Text(text, color = c.warnText, fontSize = 13.sp)
        }
    } else {
        Text(text, color = c.textMuted, fontSize = 13.sp)
    }
}

@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = ThermoTheme.colors
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp))
            .then(if (selected) Modifier.background(c.accent) else Modifier.glass(RoundedCornerShape(20.dp)))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) c.onAccent else c.text, fontSize = 13.sp) }
}

@Composable
private fun AccentButton(text: String, onClick: () -> Unit) {
    val c = ThermoTheme.colors
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(22.dp)).background(c.accent)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c.onAccent, fontSize = 14.sp) }
}

@Composable
private fun SwitchLine(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = ThermoTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp)
            if (summary.isNotEmpty()) Text(summary, color = c.textMuted, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent))
    }
}

/** fRsi warning bar (zadání F3): shown only when the MIN spot is below the limit. */
@Composable
fun ThermalBridgeBar(fRsi: Float?, limit: Float, fmt: NumberFormatter, modifier: Modifier = Modifier) {
    if (fRsi == null || fRsi >= limit) return
    val c = ThermoTheme.colors
    Row(
        modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), tint = c.warnFill, border = c.warnBorder).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚠", color = c.warnTitle, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.bridge_warning), color = c.warnTitle, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("fRsi " + fmt.two(fRsi) + " < " + fmt.two(limit), color = c.warnText, fontSize = 14.sp)
    }
}
