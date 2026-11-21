package io.github.mpridal.thermocamera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mpridal.thermocamera.ui.theme.NumberStyle
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme

enum class StatusKind { OK, PENDING, ERROR }

/** Status chip (zadání F6, kap. 7.4): 36 dp, glass tinted by state, glowing dot. */
@Composable
fun StatusChip(text: String, kind: StatusKind, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val c = ThermoTheme.colors
    val (fill, border, dot) = when (kind) {
        StatusKind.OK -> Triple(c.okChipFill, c.okChipBorder, c.connected)
        StatusKind.PENDING -> Triple(c.warnFill, c.warnBorder, c.warning)
        StatusKind.ERROR -> Triple(c.errChipFill, c.errChipBorder, c.error)
    }
    Row(
        modifier
            .heightIn(min = 36.dp)
            .glass(RoundedCornerShape(18.dp), tint = fill, border = border)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .shadow(6.dp, CircleShape, ambientColor = dot, spotColor = dot)
                .clip(CircleShape)
                .background(dot),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = c.text, fontSize = 13.sp, maxLines = 2)
    }
}

/** Round 44 dp glass icon button with a 48 dp touch target. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = ThermoTheme.colors.warning,
) {
    val c = ThermoTheme.colors
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .then(if (active) Modifier.clip(CircleShape).background(activeColor) else Modifier.glass(CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = if (active) c.onAccent else c.text, modifier = Modifier.size(22.dp))
        }
    }
}

/** Segmented control: 4 segments in a glass track, the selected one solid accent with a glow. */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ThermoTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).glass(RoundedCornerShape(22.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((value, label) in options) {
            val isSel = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .then(
                        if (isSel) Modifier.shadow(8.dp, RoundedCornerShape(19.dp), ambientColor = c.accent, spotColor = c.accent)
                            .clip(RoundedCornerShape(19.dp)).background(c.accent)
                        else Modifier.clip(RoundedCornerShape(19.dp)),
                    )
                    .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(value) })
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSel) c.onAccent else c.segOff,
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Value tile (MIN / CENTER / MAX): 60 dp, radius 16. */
@Composable
fun ValueTile(label: String, value: String, marker: String, valueColor: Color, modifier: Modifier = Modifier) {
    val c = ThermoTheme.colors
    Column(
        modifier.heightIn(min = 60.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(marker, color = valueColor, fontSize = 11.sp)
            Spacer(Modifier.width(4.dp))
            Text(label, color = c.textMuted, fontSize = 11.sp, maxLines = 1)
        }
        Text(value, style = NumberStyle, color = valueColor, maxLines = 1)
    }
}

/** Small pill chip used for quick options (range, palette, add point). */
@Composable
fun GlassChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: String? = null) {
    val c = ThermoTheme.colors
    Row(
        modifier
            .heightIn(min = 40.dp)
            .glass(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(leading, color = c.accent, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = c.text, fontSize = 13.sp, maxLines = 1)
    }
}

/** Shutter: 80 dp, 4 dp ring in text colour, glowing accent inside. */
@Composable
fun ShutterButton(contentDescription: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = ThermoTheme.colors
    Box(
        Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(4.dp, c.text.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .shadow(if (enabled) 14.dp else 0.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent)
                .clip(CircleShape)
                .background(c.accent.copy(alpha = if (enabled) 1f else 0.4f)),
        )
    }
}
