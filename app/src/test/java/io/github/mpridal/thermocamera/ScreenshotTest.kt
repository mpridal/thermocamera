package io.github.mpridal.thermocamera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.mpridal.thermocamera.data.ThemeMode
import io.github.mpridal.thermocamera.data.ViewMode
import io.github.mpridal.thermocamera.ui.calibration.ThermalBridgeBar
import io.github.mpridal.thermocamera.ui.components.GlassChip
import io.github.mpridal.thermocamera.ui.components.SegmentedControl
import io.github.mpridal.thermocamera.ui.components.StatusChip
import io.github.mpridal.thermocamera.ui.components.StatusKind
import io.github.mpridal.thermocamera.ui.components.ValueTile
import io.github.mpridal.thermocamera.ui.components.rememberNumberFormatter
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot matrix (zadání kap. 10): the components with the longest texts in 2 themes × 6 languages
 * (+ pseudo-locale en-XA) × phone / tablet width. Images go to app/build/outputs/roborazzi/ –
 * they show clipped or broken layouts at a glance (`./gradlew :app:recordRoborazziDebug`).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = android.app.Application::class)
class ScreenshotTest(private val lang: String, private val dark: Boolean, private val widthDp: Int) {

    @get:Rule val compose = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}-{2}")
        fun params(): List<Array<Any>> = buildList {
            for (lang in listOf("en", "cs", "es", "it", "fr", "de", "en-rXA")) for (dark in listOf(false, true)) for (w in listOf(360, 840)) {
                add(arrayOf(lang, dark, w))
            }
        }
    }

    @Test
    fun components() {
        RuntimeEnvironment.setQualifiers("+$lang-w${widthDp}dp-h900dp-${if (dark) "night" else "notnight"}")
        compose.setContent {
            ThermoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, reduceTransparency = true) {
                Showcase()
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/components-$lang-${if (dark) "dark" else "light"}-${widthDp}dp.png")
    }

    @Composable
    private fun Showcase() {
        val c = ThermoTheme.colors
        val fmt = rememberNumberFormatter()
        Column(
            Modifier.fillMaxWidth().background(c.root).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, color = c.text)
            StatusChip(stringResource(R.string.status_connected, "9"), StatusKind.OK)
            StatusChip(stringResource(R.string.status_needs_camera_permission), StatusKind.PENDING)
            StatusChip(stringResource(R.string.status_error_usb), StatusKind.ERROR)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValueTile(stringResource(R.string.label_min), fmt.t1(11.4f), "▼", c.coldText, Modifier.weight(1f))
                ValueTile(stringResource(R.string.label_center), fmt.t1(19.6f), "+", c.text, Modifier.weight(1f))
                ValueTile(stringResource(R.string.label_max), fmt.t1(44.2f), "▲", c.hotText, Modifier.weight(1f))
            }
            ThermalBridgeBar(0.64f, 0.75f, fmt)
            SegmentedControl(
                options = listOf(
                    ViewMode.THERMAL to stringResource(R.string.mode_thermal),
                    ViewMode.OVERLAY to stringResource(R.string.mode_overlay),
                    ViewMode.SIDE_BY_SIDE to stringResource(R.string.mode_side_by_side),
                    ViewMode.PHOTO to stringResource(R.string.mode_photo),
                ),
                selected = ViewMode.OVERLAY,
                onSelect = {},
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(stringResource(R.string.chip_range, stringResource(R.string.range_facade)) + " ▾", {})
                GlassChip(stringResource(R.string.action_add_point), {}, leading = "+")
                GlassChip(stringResource(R.string.action_freeze), {}, leading = "❚❚")
                GlassChip(stringResource(R.string.action_start_camera_test), {})
                GlassChip(stringResource(R.string.better_camera_keep), {})
            }
            Text(stringResource(R.string.bridge_small_difference), color = c.warnText)
            Text(stringResource(R.string.disconnected_help_body), color = c.textMuted)
        }
    }
}
