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
    val orientation: Orientation = Orientation(),
    val moduleAlignment: OverlayAlignment = OverlayAlignment.DEFAULT,
    val photoCamera: PhotoCamera = PhotoCamera.MODULE,
    val autoAlign: Boolean = true,
    /** Device id for which the "better phone camera" offer was answered; offered again on another device. */
    val betterCameraAnsweredFor: String = "",
) {
    companion object {
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
                mirror = p[Keys.mirror] ?: false,
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
    suspend fun setBetterCameraAnsweredFor(device: String) = context.dataStore.edit { it[Keys.betterCameraAnswered] = device }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
