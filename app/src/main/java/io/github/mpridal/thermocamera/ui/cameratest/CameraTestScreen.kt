package io.github.mpridal.thermocamera.ui.cameratest

import android.content.ClipData
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.ui.components.GlassChip
import io.github.mpridal.thermocamera.ui.components.GlassIconButton
import io.github.mpridal.thermocamera.ui.components.celsius
import io.github.mpridal.thermocamera.ui.components.glass
import io.github.mpridal.thermocamera.ui.components.rememberNumberFormatter
import io.github.mpridal.thermocamera.ui.theme.NumberStyleSmall
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme
import kotlinx.coroutines.launch

@Composable
fun CameraTestScreen(vm: CameraTestViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = ThermoTheme.colors
    val fmt = rememberNumberFormatter()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.test_title)

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), onBack)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 640.dp).align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.simulator) Text(stringResource(R.string.test_simulator_note), color = c.textMuted, fontSize = 13.sp)

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TestStep.entries.forEachIndexed { i, step ->
                    val r = ui.steps.getValue(step)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (mark, color) = when (r.status) {
                            StepStatus.OK -> "✓" to c.connected
                            StepStatus.FAIL -> "✕" to c.error
                            StepStatus.RUNNING -> "…" to c.warning
                            StepStatus.PENDING -> "•" to c.textMuted
                        }
                        if (r.status == StepStatus.RUNNING) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = c.warning, strokeWidth = 2.dp)
                        } else {
                            Text(mark, color = color, fontSize = 18.sp, modifier = Modifier.width(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("${i + 1}. " + stepName(step), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (r.detail.isNotEmpty()) {
                            Text(if (step == TestStep.SENSOR) stringResource(R.string.test_detail_faulty, r.detail) else r.detail, color = c.textMuted, style = NumberStyleSmall)
                        }
                    }
                }
            }

            if (ui.finished) {
                val fail = ui.firstFailure
                val ok = fail == null
                Column(
                    Modifier.fillMaxWidth().glass(
                        RoundedCornerShape(16.dp),
                        tint = if (ok) c.okChipFill else c.errChipFill,
                        border = if (ok) c.okChipBorder else c.errChipBorder,
                    ).padding(16.dp),
                ) {
                    Text(
                        if (ok) stringResource(R.string.test_result_ok) else stringResource(R.string.test_result_fail, advice(fail!!)),
                        color = c.text, fontSize = 16.sp,
                    )
                }
            } else if (ui.running) {
                Text(stringResource(R.string.test_running), color = c.textMuted)
            }

            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.test_raw_data), color = c.textMuted, style = MaterialTheme.typography.labelMedium)
                ui.raw?.let { img ->
                    Image(
                        img, contentDescription = stringResource(R.string.test_raw_data),
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None,
                    )
                }
                ui.rawRange?.let { r ->
                    Text(celsius(fmt, r.min) + " – " + celsius(fmt, r.max), color = c.text, style = NumberStyleSmall)
                }
                ui.noise?.let { n ->
                    Text(stringResource(R.string.test_noise, stringResource(R.string.temp_celsius, fmt.two(n))), color = c.text, style = NumberStyleSmall)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(stringResource(R.string.action_repeat), vm::run)
                GlassChip(stringResource(R.string.action_done), onBack)
                GlassChip(stringResource(R.string.action_copy_diagnostics), {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(title, ui.diagnostics))) }
                })
            }
        }
    }
}

@Composable
private fun stepName(s: TestStep): String = stringResource(
    when (s) {
        TestStep.DEVICE -> R.string.test_step_device
        TestStep.PERMISSION -> R.string.test_step_permission
        TestStep.THERMAL -> R.string.test_step_thermal
        TestStep.VISIBLE -> R.string.test_step_visible
        TestStep.SENSOR -> R.string.test_step_sensor
        TestStep.READING -> R.string.test_step_reading
    },
)

@Composable
private fun advice(s: TestStep): String = stringResource(
    when (s) {
        TestStep.DEVICE -> R.string.test_advice_device
        TestStep.PERMISSION -> R.string.test_advice_permission
        TestStep.THERMAL -> R.string.test_advice_thermal
        TestStep.VISIBLE -> R.string.test_advice_visible
        TestStep.SENSOR -> R.string.test_advice_sensor
        TestStep.READING -> R.string.test_advice_reading
    },
)
