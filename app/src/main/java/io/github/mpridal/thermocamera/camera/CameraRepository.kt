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
import kotlinx.coroutines.launch
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

    init {
        // Video frame rate for the HT-102 (Nastavení › Pro pokročilé).
        scope.launch { settings.settings.map { it.videoFps }.distinctUntilChanged().collect { ht102.setRequestedFps(it) } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val active: StateFlow<ThermalCameraSource> = settings.settings
        .map { it.useSimulator }
        .distinctUntilChanged()
        .map { sim -> if (sim) simulator else ht102 }
        .stateIn(scope, SharingStarted.Eagerly, ht102)
}
