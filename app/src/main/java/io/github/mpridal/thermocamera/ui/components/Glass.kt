package io.github.mpridal.thermocamera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.mpridal.thermocamera.ui.theme.LocalReduceTransparency
import io.github.mpridal.thermocamera.ui.theme.ThermoTheme

/** Haze state of the screen background that glass panels blur (zadání kap. 7.1). */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

enum class GlassLevel { PANEL, HI, SHEET, IMAGE_LABEL }

/**
 * Glass surface: translucent fill + backdrop blur + 1 dp light border + soft shadow.
 * Falls back to a more opaque solid fill when transparency is reduced or no haze source exists.
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(16.dp),
    level: GlassLevel = GlassLevel.PANEL,
    tint: Color? = null,
    border: Color? = null,
    elevation: Dp = 0.dp,
): Modifier {
    val c = ThermoTheme.colors
    val hazeState = LocalHazeState.current
    val reduce = LocalReduceTransparency.current
    val (fill, blur) = when (level) {
        GlassLevel.PANEL -> c.glass to 22.dp
        GlassLevel.HI -> c.glassHi to 0.dp
        GlassLevel.SHEET -> c.sheet to 30.dp
        GlassLevel.IMAGE_LABEL -> c.imgLabel to 12.dp
    }
    val fillColor = tint ?: fill
    var m = this
    if (elevation > 0.dp) m = m.shadow(elevation, shape, ambientColor = c.shadow, spotColor = c.shadow)
    m = m.clip(shape)
    m = if (hazeState != null && !reduce && blur > 0.dp) {
        val root = c.root
        val style = remember(fillColor, blur, root) {
            HazeBlurStyle {
                blurRadius(blur)
                backgroundColor(root)
                noiseFactor(0f)
                colorEffects(listOf(HazeColorEffect.tint(fillColor)))
            }
        }
        val input = remember(hazeState) { HazeInput.Sources(hazeState) }
        m.hazeBlur(input = input, style = style)
    } else {
        val solid = if (reduce && level != GlassLevel.HI) {
            if (tint != null) tint.compositeOverSolid(c.glassSolid) else c.glassSolid
        } else fillColor
        m.background(solid)
    }
    return m.border(1.dp, border ?: c.glassBorder, shape)
}

private fun Color.compositeOverSolid(base: Color): Color {
    val a = alpha
    return Color(
        red = red * a + base.red * (1 - a),
        green = green * a + base.green * (1 - a),
        blue = blue * a + base.blue * (1 - a),
        alpha = base.alpha,
    )
}

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    level: GlassLevel = GlassLevel.PANEL,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glass(shape, level), content = content)
}
