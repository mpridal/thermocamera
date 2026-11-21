package io.github.mpridal.thermocamera.camera

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mpridal.thermocamera.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Picks the active [ThermalCameraSource] according to the settings (simulator vs. real camera). */
@Singleton
class CameraRepository @Inject constructor(
    @ApplicationContext context: Context,
    settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val simulator = SimulatedSource()
    val ht102 = Ht102Source(context)

    @OptIn(ExperimentalCoroutinesApi::class)
    val active: StateFlow<ThermalCameraSource> = settings.settings
        .map { it.useSimulator }
        .distinctUntilChanged()
        .map { sim -> if (sim) simulator else ht102 }
        .stateIn(scope, SharingStarted.Eagerly, ht102)
}
