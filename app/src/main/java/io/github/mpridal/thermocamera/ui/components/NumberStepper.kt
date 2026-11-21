package io.github.mpridal.thermocamera.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mpridal.thermocamera.R
import io.github.mpridal.thermocamera.ui.theme.NumberStyle
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme
import kotlin.math.roundToInt

/**
 * Number input: − / editable value / + with a fixed [step] (e.g. 0.1 °C, zadání F2).
 * Accepts both decimal comma and point. [decimals] controls display and rounding.
 */
@Composable
fun NumberStepper(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    step: Float,
    min: Float,
    max: Float,
    decimals: Int,
    fmt: NumberFormatter,
    modifier: Modifier = Modifier,
    unit: String = "",
) {
    val c = ThermoTheme.colors
    fun format(v: Float) = when (decimals) { 0 -> fmt.zero(v); 1 -> fmt.one(v); else -> fmt.two(v) }
    fun round(v: Float): Float {
        val k = Math.pow(10.0, decimals.toDouble()).toFloat()
        return ((v * k).roundToInt() / k).coerceIn(min, max)
    }
    var text by remember { mutableStateOf(format(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) text = format(value) }
    fun commit() {
        text.replace(',', '.').replace(' ', ' ').trim().toFloatOrNull()?.let { onChange(round(it)) }
        text = format(value)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = c.textMuted, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StepButton("−", stringResource(R.string.cd_decrease, label)) { onChange(round(value - step)) }
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).glass(RoundedCornerShape(12.dp), GlassLevel.HI).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = NumberStyle.copy(color = c.text, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        if (focused && !it.isFocused) commit()
                        focused = it.isFocused
                    },
                )
            }
            if (unit.isNotEmpty()) Text(unit, color = c.textMuted, fontSize = 13.sp, modifier = Modifier.width(24.dp))
            StepButton("+", stringResource(R.string.cd_increase, label)) { onChange(round(value + step)) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, cd: String, onClick: () -> Unit) {
    val c = ThermoTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = cd, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            Text(symbol, color = c.text, fontSize = 20.sp)
        }
    }
}
