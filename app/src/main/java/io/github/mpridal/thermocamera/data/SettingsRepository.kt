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
import io.github.mpridal.thermocamera.core.Palette
import io.github.mpridal.thermocamera.core.RangePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ViewMode { THERMAL, OVERLAY, SIDE_BY_SIDE, PHOTO }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reduceTransparency: Boolean = false,
    val useSimulator: Boolean = DEFAULT_SIMULATOR,
    val viewMode: ViewMode = ViewMode.THERMAL,
    val blend: Float = 0.55f,
    val palette: Palette = Palette.IRON,
    val rangePreset: RangePreset = RangePreset.INTERIOR,
    val emissivity: Float = 0.95f,
) {
    companion object {
        /**
         * Until Ht102Source can read thermal data (Phase 0/1), the simulator is on by default
         * so the app shows a live image. Switch the default off once the camera works.
         */
        const val DEFAULT_SIMULATOR = true
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
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            themeMode = p[Keys.theme].toEnum(d.themeMode),
            reduceTransparency = p[Keys.reduceTransparency] ?: d.reduceTransparency,
            useSimulator = p[Keys.simulator] ?: d.useSimulator,
            viewMode = p[Keys.viewMode].toEnum(d.viewMode),
            blend = p[Keys.blend] ?: d.blend,
            palette = p[Keys.palette].toEnum(d.palette),
            rangePreset = p[Keys.range].toEnum(d.rangePreset),
            emissivity = p[Keys.emissivity] ?: d.emissivity,
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
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
