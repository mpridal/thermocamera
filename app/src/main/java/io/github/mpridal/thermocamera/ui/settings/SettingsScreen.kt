package io.github.mpridal.thermocamera.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mpridal.thermocamera.BuildConfig
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.core.Orientation
import io.github.mpridal.thermocamera.data.PhotoCamera
import io.github.mpridal.thermocamera.data.Settings
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import io.github.mpridal.thermocamera.data.SettingsRepository
import io.github.mpridal.thermocamera.data.ThemeMode
import io.github.mpridal.thermocamera.ui.components.GlassIconButton
import io.github.mpridal.thermocamera.ui.components.GlassLevel
import io.github.mpridal.thermocamera.ui.components.SegmentedControl
import io.github.mpridal.thermocamera.ui.components.glass
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<Settings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    fun setTheme(m: ThemeMode) = viewModelScope.launch { repo.setThemeMode(m) }
    fun setReduceTransparency(v: Boolean) = viewModelScope.launch { repo.setReduceTransparency(v) }
    fun setSimulator(v: Boolean) = viewModelScope.launch { repo.setUseSimulator(v) }
    fun setRotation(r: Int) = viewModelScope.launch { repo.setOrientation(settings.value.orientation.copy(rotation = r)) }
    fun setMirror(m: Boolean) = viewModelScope.launch { repo.setOrientation(settings.value.orientation.copy(mirror = m)) }
    fun setPhotoCamera(p: PhotoCamera) = viewModelScope.launch { repo.setPhotoCamera(p) }
    fun setAutoAlign(v: Boolean) = viewModelScope.launch { repo.setAutoAlign(v) }
    fun setVideoFps(v: Int) = viewModelScope.launch { repo.setVideoFps(v) }
    fun setSaveToGallery(v: Boolean) = viewModelScope.launch { repo.setSaveToGallery(v) }
    fun setFahrenheit(v: Boolean) = viewModelScope.launch { repo.setFahrenheit(v) }
    fun setSmoothing(v: Boolean) = viewModelScope.launch { repo.setSmoothing(v) }
    fun setPixelated(v: Boolean) = viewModelScope.launch { repo.setPixelated(v) }
    fun setCheckUpdates(v: Boolean) = viewModelScope.launch { repo.setCheckUpdates(v) }
    fun setUpdatesWifiOnly(v: Boolean) = viewModelScope.launch { repo.setUpdatesWifiOnly(v) }
    fun setVideoResolution(v: io.github.mpridal.thermocamera.data.VideoResolution) = viewModelScope.launch { repo.setVideoResolution(v) }
}

/** Supported app languages in the order of the combo box (zadání F10). */
val APP_LANGUAGES = listOf("cs", "en", "es", "it", "fr", "de")

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onOpenUsbDiagnostics: () -> Unit,
    onOpenAlignment: () -> Unit,
    onChoosePhotoCamera: (PhotoCamera) -> Unit,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val c = ThermoTheme.colors
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), onBack)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(stringResource(R.string.settings_section_appearance)) {
                SegmentedControl(
                    options = listOf(
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ),
                    selected = s.themeMode,
                    onSelect = vm::setTheme,
                )
                SwitchRow(
                    stringResource(R.string.settings_reduce_transparency),
                    stringResource(R.string.settings_reduce_transparency_summary),
                    s.reduceTransparency,
                    vm::setReduceTransparency,
                )
            }
            Section(stringResource(R.string.settings_section_display)) {
                Text(stringResource(R.string.settings_units), color = c.text, fontSize = 15.sp)
                SegmentedControl(options = listOf(false to "°C", true to "°F"), selected = s.fahrenheit, onSelect = vm::setFahrenheit)
                SwitchRow(stringResource(R.string.settings_smoothing), "", s.smoothing, vm::setSmoothing)
                SwitchRow(stringResource(R.string.settings_pixelated), "", s.pixelated, vm::setPixelated)
            }
            Section(stringResource(R.string.settings_section_camera)) {
                Text(stringResource(R.string.settings_orientation), color = c.text, fontSize = 15.sp)
                SegmentedControl(
                    options = Orientation.ROTATIONS.map { it to "$it°" },
                    selected = s.orientation.rotation,
                    onSelect = vm::setRotation,
                )
                SwitchRow(
                    stringResource(R.string.settings_mirror),
                    stringResource(R.string.settings_mirror_summary),
                    s.orientation.mirror,
                    vm::setMirror,
                )
                NavRow(
                    stringResource(R.string.settings_alignment),
                    stringResource(R.string.settings_alignment_summary),
                    onOpenAlignment,
                )
                Text(stringResource(R.string.settings_photo_camera), color = c.text, fontSize = 15.sp)
                listOf(
                    PhotoCamera.PHONE to R.string.photo_camera_phone,
                    PhotoCamera.PHONE_WITH_MODULE_PREVIEW to R.string.photo_camera_phone_module_preview,
                    PhotoCamera.MODULE to R.string.photo_camera_module,
                ).forEach { (option, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = s.photoCamera == option, role = Role.RadioButton) { onChoosePhotoCamera(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.photoCamera == option, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.accent))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label), color = c.text, fontSize = 15.sp)
                    }
                }
                if (s.photoCamera != PhotoCamera.MODULE) {
                    SwitchRow(stringResource(R.string.settings_auto_align), "", s.autoAlign, vm::setAutoAlign)
                }
            }
            Section(stringResource(R.string.gallery_title)) {
                SwitchRow(stringResource(R.string.settings_save_to_gallery), "", s.saveToGallery, vm::setSaveToGallery)
            }
            Section(stringResource(R.string.settings_section_updates)) {
                SwitchRow(stringResource(R.string.settings_check_updates), "", s.checkUpdates, vm::setCheckUpdates)
                SwitchRow(stringResource(R.string.settings_updates_wifi_only), "", s.updatesWifiOnly, vm::setUpdatesWifiOnly)
            }
            Section(stringResource(R.string.settings_section_language)) { LanguageDropdown() }
            Section(stringResource(R.string.settings_section_advanced)) {
                SwitchRow(
                    stringResource(R.string.settings_simulator),
                    stringResource(R.string.settings_simulator_summary),
                    s.useSimulator,
                    vm::setSimulator,
                )
                Text(stringResource(R.string.settings_video_fps), color = c.text, fontSize = 15.sp)
                Text(stringResource(R.string.settings_video_fps_summary), color = c.textMuted, fontSize = 13.sp)
                SegmentedControl(
                    options = Settings.VIDEO_FPS_OPTIONS.map { it to it.toString() },
                    selected = s.videoFps,
                    onSelect = vm::setVideoFps,
                )
                Text(stringResource(R.string.settings_video_resolution), color = c.text, fontSize = 15.sp)
                SegmentedControl(
                    options = io.github.mpridal.thermocamera.data.VideoResolution.entries.map { it to "${it.width}×${it.height}" },
                    selected = s.videoResolution,
                    onSelect = vm::setVideoResolution,
                )
                NavRow(
                    stringResource(R.string.settings_usb_diagnostics),
                    stringResource(R.string.settings_usb_diagnostics_summary),
                    onOpenUsbDiagnostics,
                )
            }
            Section(stringResource(R.string.settings_section_about)) {
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = c.textMuted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = ThermoTheme.colors
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        Text(title.uppercase(locale), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        content()
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = ThermoTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp)
            if (summary.isNotEmpty()) Text(summary, color = c.textMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
        )
    }
}

@Composable
private fun NavRow(title: String, summary: String, onClick: () -> Unit) {
    val c = ThermoTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.link, fontSize = 15.sp)
            Text(summary, color = c.textMuted, fontSize = 13.sp)
        }
        Text("›", color = c.textMuted, fontSize = 20.sp)
    }
}

/** Language combo box (zadání F10): AUTO first, then languages in their own names with a code. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown() {
    val c = ThermoTheme.colors
    val current = AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)?.language
    val selected = current?.takeIf { it in APP_LANGUAGES }
    val phone = remember { LocaleListCompat.getAdjustedDefault()[0]?.language ?: "en" }
    val phoneLang = if (phone in APP_LANGUAGES) phone else "en"
    var expanded by remember { mutableStateOf(false) }

    val label: @Composable (String?) -> String = { code ->
        if (code == null) stringResource(R.string.language_auto, languageName(phoneLang))
        else languageCode(code) + " · " + languageName(code)
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        Row(
            Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .glass(RoundedCornerShape(14.dp), GlassLevel.HI)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(selected), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(if (expanded) "▴" else "▾", color = c.textMuted)
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = c.menu,
        ) {
            (listOf<String?>(null) + APP_LANGUAGES).forEach { code ->
                DropdownMenuItem(
                    text = { Text(label(code), color = c.text) },
                    trailingIcon = if (code == selected) {
                        { Icon(Icons.Filled.Check, null, tint = c.accent, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        expanded = false
                        AppCompatDelegate.setApplicationLocales(
                            if (code == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(code),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun languageName(code: String): String = stringResource(
    when (code) {
        "cs" -> R.string.lang_name_cs
        "es" -> R.string.lang_name_es
        "it" -> R.string.lang_name_it
        "fr" -> R.string.lang_name_fr
        "de" -> R.string.lang_name_de
        else -> R.string.lang_name_en
    },
)

@Composable
private fun languageCode(code: String): String = stringResource(
    when (code) {
        "cs" -> R.string.lang_code_cs
        "es" -> R.string.lang_code_es
        "it" -> R.string.lang_code_it
        "fr" -> R.string.lang_code_fr
        "de" -> R.string.lang_code_de
        else -> R.string.lang_code_en
    },
)
