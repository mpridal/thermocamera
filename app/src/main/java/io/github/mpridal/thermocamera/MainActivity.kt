package io.github.mpridal.thermocamera

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.github.mpridal.thermocamera.camera.CameraRepository
import io.github.mpridal.thermocamera.data.PhotoCamera
import io.github.mpridal.thermocamera.ui.align.AlignmentScreen
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import io.github.mpridal.thermocamera.camera.CameraState
import io.github.mpridal.thermocamera.ui.cameratest.CameraTestScreen
import javax.inject.Inject
import io.github.mpridal.thermocamera.ui.components.LocalHazeState
import io.github.mpridal.thermocamera.ui.diagnostics.UsbDiagnosticsScreen
import io.github.mpridal.thermocamera.ui.live.LiveScreen
import io.github.mpridal.thermocamera.ui.live.LiveViewModel
import io.github.mpridal.thermocamera.ui.settings.SettingsScreen
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val liveVm: LiveViewModel by viewModels()

    @Inject lateinit var cameras: CameraRepository

    override fun onStart() {
        super.onStart()
        liveVm.setForeground(true)
    }

    override fun onStop() {
        liveVm.setForeground(false)
        super.onStop()
    }

    /** The app is opened by the system when the HT-102 is plugged in (USB_DEVICE_ATTACHED). */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        cameras.ht102.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by liveVm.settings.collectAsStateWithLifecycle()
            ThermoTheme(settings.themeMode, settings.reduceTransparency) {
                AppRoot(liveVm)
            }
        }
    }
}

private object Routes {
    const val LIVE = "live"
    const val SETTINGS = "settings"
    const val USB_DIAG = "usb-diagnostics"
    const val CAMERA_TEST = "camera-test"
    const val ALIGN = "align"
}

@Composable
private fun AppRoot(liveVm: LiveViewModel) {
    val hazeState = remember { HazeState() }
    val snackbar = remember { SnackbarHostState() }
    val nav = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val permissionDenied = androidx.compose.ui.res.stringResource(R.string.camera_permission_denied)
    var pendingPhotoCamera by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<PhotoCamera?>(null) }
    val cameraPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val wanted = pendingPhotoCamera
        pendingPhotoCamera = null
        if (granted && wanted != null) {
            liveVm.setPhotoCamera(wanted)
        } else {
            liveVm.setPhotoCamera(PhotoCamera.MODULE)
            scope.launch { snackbar.showSnackbar(permissionDenied) }
        }
    }
    /** Choosing the phone camera asks for the CAMERA permission first (zadání F11 bod 7). */
    val choosePhotoCamera: (PhotoCamera) -> Unit = { choice ->
        val needs = choice != PhotoCamera.MODULE &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needs) {
            pendingPhotoCamera = choice
            cameraPermission.launch(android.Manifest.permission.CAMERA)
        } else {
            liveVm.setPhotoCamera(choice)
        }
    }
    Box(Modifier.fillMaxSize()) {
        ScreenBackground(liveVm, Modifier.fillMaxSize().hazeSource(hazeState))
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            NavHost(nav, startDestination = Routes.LIVE) {
                composable(Routes.LIVE) {
                    LiveScreen(
                        liveVm,
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onOpenCameraTest = { nav.navigate(Routes.CAMERA_TEST) },
                        snackbar = snackbar,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        hiltViewModel(),
                        onBack = { nav.popBackStack() },
                        onOpenUsbDiagnostics = { nav.navigate(Routes.USB_DIAG) },
                        onOpenAlignment = { nav.navigate(Routes.ALIGN) },
                        onChoosePhotoCamera = choosePhotoCamera,
                    )
                }
                composable(Routes.ALIGN) {
                    AlignmentScreen(liveVm, onBack = { nav.popBackStack() })
                }
                composable(Routes.CAMERA_TEST) {
                    CameraTestScreen(hiltViewModel(), onBack = { nav.popBackStack() })
                }
                composable(Routes.USB_DIAG) {
                    UsbDiagnosticsScreen(hiltViewModel(), onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

/**
 * Screen background (zadání kap. 7.1): the live thermal image shrunk to 8 × 8 and stretched
 * with bilinear filtering (naturally blurred) + a veil; without a camera a soft gradient.
 */
@Composable
private fun ScreenBackground(liveVm: LiveViewModel, modifier: Modifier) {
    val c = ThermoTheme.colors
    val frame by liveVm.frame.collectAsStateWithLifecycle()
    val state by liveVm.cameraState.collectAsStateWithLifecycle()
    val bg = frame.background
    Box(modifier.background(c.root)) {
        if (bg != null && state is CameraState.Streaming) {
            val n = frame.frameNo
            Canvas(Modifier.fillMaxSize()) {
                n.hashCode()
                drawImage(bg, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
                drawRect(c.veil)
            }
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c.gradientStart, c.root, c.gradientEnd))))
        }
    }
}
