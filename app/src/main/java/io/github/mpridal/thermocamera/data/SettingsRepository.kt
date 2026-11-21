package io.github.mpridal.thermocamera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.mpridal.thermocamera.core.Orientation
import io.github.mpridal.thermocamera.core.OverlayAlignment
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.core.RangePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ViewMode { THERMAL, OVERLAY, SIDE_BY_SIDE, PHOTO }

/**
 * HT-102 video resolution (UVC frame index). The thermal matrix is in the first 30 720 bytes of every
 * frame in all three resolutions (verified on captures), smaller frames may come faster.
 */
enum class VideoResolution(val frameIndex: Int, val width: Int, val height: Int) {
    R640(1, 640, 480),
    R320(2, 320, 240),
    R160(3, 160, 120),
}

/** Which camera provides the normal image (zadání F11 bod 3). */
enum class PhotoCamera {
    /** Camera inside the HT-102 module (640 × 480). */
    MODULE,

    /** Rear camera of the phone for preview and photos (recommended). */
    PHONE,

    /** Live preview from the module (fixed alignment), photo from the phone on shutter. */
    PHONE_WITH_MODULE_PREVIEW,
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reduceTransparency: Boolean = false,
    val useSimulator: Boolean = false,
    val viewMode: ViewMode = ViewMode.THERMAL,
    val blend: Float = 0.55f,
    val palette: Palette = Palette.IRON,
    val rangePreset: RangePreset = RangePreset.INTERIOR,
    val emissivity: Float = 0.95f,
    /** Mirrored by default: with the lens facing away from the user (like the rear camera) the image is mirrored (tested on Pixel 10 Pro). */
    val orientation: Orientation = Orientation(rotation = 0, mirror = true),
    val moduleAlignment: OverlayAlignment = OverlayAlignment.DEFAULT,
    val photoCamera: PhotoCamera = PhotoCamera.MODULE,
    val autoAlign: Boolean = true,
    /** Device id for which the "better phone camera" offer was answered; offered again on another device. */
    val betterCameraAnsweredFor: String = "",
    /** Requested video frame rate of the HT-102 stream (Pro pokročilé); thermal data comes with every frame. */
    val videoFps: Int = DEFAULT_VIDEO_FPS,
    val videoResolution: VideoResolution = VideoResolution.R320,
    // ---- Phase 3: range, calibration, thermal bridge (zadání F2, F3) ----
    val customMin: Float = 12f,
    val customMax: Float = 24f,
    /** Locked range: with AUTO the current automatic range is frozen into [lockedMin]..[lockedMax]. */
    val rangeLocked: Boolean = false,
    val lockedMin: Float = 12f,
    val lockedMax: Float = 24f,
    val reflected: Float = 20f,
    val calOffset: Float = 0f,
    val calGain: Float = 1f,
    /** Inside air θi and outside air θe for the temperature factor fRsi. */
    val insideTemp: Float = 21f,
    val outsideTemp: Float = 0f,
    val fRsiLimit: Float = 0.75f,
    val isotherm: Boolean = false,
    /** Name of the calibration profile last applied (shown on the main screen). */
    val activeProfile: String = "",
    // ---- Phase 4/6 ----
    val saveToGallery: Boolean = true,
    val fahrenheit: Boolean = false,
    val smoothing: Boolean = true,
    val pixelated: Boolean = false,
    /** Project new snapshots go to; -1 = "No project". */
    val activeProjectId: Long = -1L,
    /** Check GitHub for a newer version at start (zadání kap. 9 bod 7). */
    val checkUpdates: Boolean = true,
    val updatesWifiOnly: Boolean = false,
) {
    /** Correction applied to raw camera values (emissivity, reflected temperature, offset, gain). */
    fun correction() = io.github.mpridal.thermocamera.core.Correction(emissivity, reflected, calOffset, calGain)

    /** Fixed colour range for the current settings, or null for automatic. */
    fun fixedRange(): io.github.mpridal.thermocamera.core.TempRange? = when {
        rangeLocked && rangePreset == RangePreset.AUTO -> io.github.mpridal.thermocamera.core.TempRange(lockedMin, maxOf(lockedMax, lockedMin + 0.5f))
        rangePreset == RangePreset.CUSTOM -> io.github.mpridal.thermocamera.core.TempRange(customMin, maxOf(customMax, customMin + 0.5f))
        else -> rangePreset.range
    }

    companion object {
        /** Frame rates the HT-102 advertises for 640 × 480 (UVC descriptor). */
        val VIDEO_FPS_OPTIONS = listOf(5, 10, 15, 20, 30)
        const val DEFAULT_VIDEO_FPS = 30

        /** The simulator is on by default only on an emulator (zadání kap. 3.1), which has no USB camera. */
        val isEmulator: Boolean by lazy {
            android.os.Build.FINGERPRINT.startsWith("generic") || android.os.Build.HARDWARE.contains("ranchu") ||
                android.os.Build.HARDWARE.contains("goldfish") || android.os.Build.PRODUCT.contains("sdk")
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val reduceTransparency = booleanPreferencesKey("reduce_transparency")
        val simulator = booleanPreferencesKey("use_simulator")
        val viewMode = stringPreferencesKey("view_mode")
        val blend = floatPreferencesKey("blend")
        val palette = stringPreferencesKey("palette")
        val range = stringPreferencesKey("range_preset")
        val emissivity = floatPreferencesKey("emissivity")
        val rotation = intPreferencesKey("module_rotation")
        val mirror = booleanPreferencesKey("module_mirror")
        val alignDx = floatPreferencesKey("align_dx")
        val alignDy = floatPreferencesKey("align_dy")
        val alignScale = floatPreferencesKey("align_scale")
        val alignAngle = floatPreferencesKey("align_angle")
        val photoCamera = stringPreferencesKey("photo_camera")
        val autoAlign = booleanPreferencesKey("auto_align")
        val betterCameraAnswered = stringPreferencesKey("better_camera_answered_for")
        val videoFps = intPreferencesKey("video_fps")
        val videoResolution = stringPreferencesKey("video_resolution")
        val customMin = floatPreferencesKey("custom_min")
        val customMax = floatPreferencesKey("custom_max")
        val rangeLocked = booleanPreferencesKey("range_locked")
        val lockedMin = floatPreferencesKey("locked_min")
        val lockedMax = floatPreferencesKey("locked_max")
        val reflected = floatPreferencesKey("reflected")
        val calOffset = floatPreferencesKey("cal_offset")
        val calGain = floatPreferencesKey("cal_gain")
        val insideTemp = floatPreferencesKey("inside_temp")
        val outsideTemp = floatPreferencesKey("outside_temp")
        val fRsiLimit = floatPreferencesKey("frsi_limit")
        val isotherm = booleanPreferencesKey("isotherm")
        val activeProfile = stringPreferencesKey("active_profile")
        val saveToGallery = booleanPreferencesKey("save_to_gallery")
        val fahrenheit = booleanPreferencesKey("fahrenheit")
        val smoothing = booleanPreferencesKey("smoothing")
        val pixelated = booleanPreferencesKey("pixelated")
        val activeProjectId = androidx.datastore.preferences.core.longPreferencesKey("active_project_id")
        val checkUpdates = booleanPreferencesKey("check_updates")
        val updatesWifiOnly = booleanPreferencesKey("updates_wifi_only")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            themeMode = p[Keys.theme].toEnum(d.themeMode),
            reduceTransparency = p[Keys.reduceTransparency] ?: d.reduceTransparency,
            useSimulator = p[Keys.simulator] ?: Settings.isEmulator,
            viewMode = p[Keys.viewMode].toEnum(d.viewMode),
            blend = p[Keys.blend] ?: d.blend,
            palette = p[Keys.palette].toEnum(d.palette),
            rangePreset = p[Keys.range].toEnum(d.rangePreset),
            emissivity = p[Keys.emissivity] ?: d.emissivity,
            orientation = Orientation(
                rotation = p[Keys.rotation]?.takeIf { it in Orientation.ROTATIONS } ?: 0,
                mirror = p[Keys.mirror] ?: d.orientation.mirror,
            ),
            moduleAlignment = OverlayAlignment(
                dx = p[Keys.alignDx] ?: 0f,
                dy = p[Keys.alignDy] ?: 0f,
                scale = (p[Keys.alignScale] ?: 1f).coerceIn(0.2f, 5f),
                angleDeg = p[Keys.alignAngle] ?: 0f,
            ),
            photoCamera = p[Keys.photoCamera].toEnum(d.photoCamera),
            autoAlign = p[Keys.autoAlign] ?: d.autoAlign,
            betterCameraAnsweredFor = p[Keys.betterCameraAnswered] ?: "",
            videoFps = p[Keys.videoFps]?.takeIf { it in Settings.VIDEO_FPS_OPTIONS } ?: d.videoFps,
            videoResolution = p[Keys.videoResolution].toEnum(d.videoResolution),
            customMin = p[Keys.customMin] ?: d.customMin,
            customMax = p[Keys.customMax] ?: d.customMax,
            rangeLocked = p[Keys.rangeLocked] ?: d.rangeLocked,
            lockedMin = p[Keys.lockedMin] ?: d.lockedMin,
            lockedMax = p[Keys.lockedMax] ?: d.lockedMax,
            reflected = p[Keys.reflected] ?: d.reflected,
            calOffset = p[Keys.calOffset] ?: d.calOffset,
            calGain = p[Keys.calGain] ?: d.calGain,
            insideTemp = p[Keys.insideTemp] ?: d.insideTemp,
            outsideTemp = p[Keys.outsideTemp] ?: d.outsideTemp,
            fRsiLimit = p[Keys.fRsiLimit] ?: d.fRsiLimit,
            isotherm = p[Keys.isotherm] ?: d.isotherm,
            activeProfile = p[Keys.activeProfile] ?: d.activeProfile,
            saveToGallery = p[Keys.saveToGallery] ?: d.saveToGallery,
            fahrenheit = p[Keys.fahrenheit] ?: d.fahrenheit,
            smoothing = p[Keys.smoothing] ?: d.smoothing,
            pixelated = p[Keys.pixelated] ?: d.pixelated,
            activeProjectId = p[Keys.activeProjectId] ?: d.activeProjectId,
            checkUpdates = p[Keys.checkUpdates] ?: d.checkUpdates,
            updatesWifiOnly = p[Keys.updatesWifiOnly] ?: d.updatesWifiOnly,
        )
    }

    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[Keys.theme] = v.name }
    suspend fun setReduceTransparency(v: Boolean) = context.dataStore.edit { it[Keys.reduceTransparency] = v }
    suspend fun setUseSimulator(v: Boolean) = context.dataStore.edit { it[Keys.simulator] = v }
    suspend fun setViewMode(v: ViewMode) = context.dataStore.edit { it[Keys.viewMode] = v.name }
    suspend fun setBlend(v: Float) = context.dataStore.edit { it[Keys.blend] = v.coerceIn(0f, 1f) }
    suspend fun setPalette(v: Palette) = context.dataStore.edit { it[Keys.palette] = v.name }
    suspend fun setRangePreset(v: RangePreset) = context.dataStore.edit { it[Keys.range] = v.name }
    suspend fun setEmissivity(v: Float) = context.dataStore.edit { it[Keys.emissivity] = v.coerceIn(0.1f, 1f) }
    suspend fun setOrientation(v: Orientation) = context.dataStore.edit { it[Keys.rotation] = v.rotation; it[Keys.mirror] = v.mirror }
    suspend fun setModuleAlignment(v: OverlayAlignment) = context.dataStore.edit {
        it[Keys.alignDx] = v.dx; it[Keys.alignDy] = v.dy; it[Keys.alignScale] = v.scale; it[Keys.alignAngle] = v.angleDeg
    }
    suspend fun setPhotoCamera(v: PhotoCamera) = context.dataStore.edit { it[Keys.photoCamera] = v.name }
    suspend fun setAutoAlign(v: Boolean) = context.dataStore.edit { it[Keys.autoAlign] = v }
    suspend fun setVideoFps(v: Int) = context.dataStore.edit { it[Keys.videoFps] = v }
    suspend fun setCustomRange(min: Float, max: Float) = context.dataStore.edit { it[Keys.customMin] = min; it[Keys.customMax] = max }
    suspend fun setRangeLocked(locked: Boolean, min: Float = 0f, max: Float = 0f) = context.dataStore.edit {
        it[Keys.rangeLocked] = locked
        if (locked) { it[Keys.lockedMin] = min; it[Keys.lockedMax] = max }
    }
    suspend fun setReflected(v: Float) = context.dataStore.edit { it[Keys.reflected] = v.coerceIn(-40f, 100f) }
    suspend fun setCalibration(offset: Float, gain: Float) = context.dataStore.edit { it[Keys.calOffset] = offset; it[Keys.calGain] = gain }
    suspend fun setBridge(inside: Float, outside: Float, limit: Float) = context.dataStore.edit {
        it[Keys.insideTemp] = inside; it[Keys.outsideTemp] = outside; it[Keys.fRsiLimit] = limit.coerceIn(0.3f, 0.95f)
    }
    suspend fun setIsotherm(v: Boolean) = context.dataStore.edit { it[Keys.isotherm] = v }
    suspend fun setSaveToGallery(v: Boolean) = context.dataStore.edit { it[Keys.saveToGallery] = v }
    suspend fun setFahrenheit(v: Boolean) = context.dataStore.edit { it[Keys.fahrenheit] = v }
    suspend fun setSmoothing(v: Boolean) = context.dataStore.edit { it[Keys.smoothing] = v }
    suspend fun setPixelated(v: Boolean) = context.dataStore.edit { it[Keys.pixelated] = v }

    /** Applies a calibration profile in one transaction and remembers its name. */
    suspend fun applyProfile(p: io.github.mpridal.thermocamera.data.db.CalibrationProfileEntity) = context.dataStore.edit {
        it[Keys.range] = p.rangePreset
        it[Keys.customMin] = p.customMin
        it[Keys.customMax] = p.customMax
        it[Keys.emissivity] = p.emissivity
        it[Keys.reflected] = p.reflected
        it[Keys.calOffset] = p.offset
        it[Keys.calGain] = p.gain
        it[Keys.rangeLocked] = false
        it[Keys.activeProfile] = p.name
    }

    suspend fun setCheckUpdates(v: Boolean) = context.dataStore.edit { it[Keys.checkUpdates] = v }
    suspend fun setUpdatesWifiOnly(v: Boolean) = context.dataStore.edit { it[Keys.updatesWifiOnly] = v }

    suspend fun setActiveProject(id: Long) = context.dataStore.edit { it[Keys.activeProjectId] = id }

    suspend fun setActiveProfile(name: String) = context.dataStore.edit { it[Keys.activeProfile] = name }

    suspend fun setVideoResolution(v: VideoResolution) = context.dataStore.edit { it[Keys.videoResolution] = v.name }
    suspend fun setBetterCameraAnsweredFor(device: String) = context.dataStore.edit { it[Keys.betterCameraAnswered] = device }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
