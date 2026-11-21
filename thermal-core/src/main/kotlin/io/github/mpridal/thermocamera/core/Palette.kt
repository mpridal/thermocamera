package io.github.mpridal.thermocamera.core

/** A colour stop: [position] in 0..1, [argb] as 0xAARRGGBB. */
data class ColorStop(val position: Float, val argb: Int)

/** Thermal palettes (zadání kap. 4.2). Colours of palettes never change with the UI theme. */
enum class Palette(val stops: List<ColorStop>) {
    IRON(
        listOf(
            ColorStop(0.00f, 0xFF080418.toInt()),
            ColorStop(0.14f, 0xFF220C5C.toInt()),
            ColorStop(0.30f, 0xFF70148C.toInt()),
            ColorStop(0.46f, 0xFFBE226E.toInt()),
            ColorStop(0.60f, 0xFFE84A28.toInt()),
            ColorStop(0.74f, 0xFFF88C14.toInt()),
            ColorStop(0.88f, 0xFFFDCE3C.toInt()),
            ColorStop(1.00f, 0xFFFFFADC.toInt()),
        ),
    ),
    RAINBOW(evenly(0xFF1B2A8F, 0xFF1C9BD7, 0xFF3CC46A, 0xFFF2E53A, 0xFFE8352B)),
    GRAY(evenly(0xFF000000, 0xFFFFFFFF)),
    ARCTIC(evenly(0xFF0B1E4F, 0xFF2F6FD6, 0xFFA7D8F5, 0xFFF7F2D8, 0xFFF5A24B));

    /** 256-entry ARGB lookup table, built lazily once per palette. */
    val lut: IntArray by lazy { buildLut(stops) }

    companion object {
        const val LUT_SIZE = 256

        /** Highlight colour for the isotherm (F3) – a vivid cyan distinct from all palettes. */
        const val ISOTHERM_ARGB: Int = 0xFF00E5FF.toInt()

        fun buildLut(stops: List<ColorStop>): IntArray {
            require(stops.size >= 2) { "A palette needs at least two stops" }
            val lut = IntArray(LUT_SIZE)
            for (i in 0 until LUT_SIZE) {
                val t = i / (LUT_SIZE - 1f)
                var k = 0
                while (k < stops.size - 2 && t > stops[k + 1].position) k++
                val a = stops[k]
                val b = stops[k + 1]
                val span = b.position - a.position
                val f = if (span <= 0f) 0f else ((t - a.position) / span).coerceIn(0f, 1f)
                lut[i] = lerpArgb(a.argb, b.argb, f)
            }
            return lut
        }

        private fun lerpArgb(a: Int, b: Int, f: Float): Int {
            fun ch(shift: Int): Int {
                val ca = (a ushr shift) and 0xFF
                val cb = (b ushr shift) and 0xFF
                return Math.round(ca + (cb - ca) * f) and 0xFF
            }
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}

private fun evenly(vararg colors: Long): List<ColorStop> =
    colors.mapIndexed { i, c -> ColorStop(i / (colors.size - 1f), c.toInt()) }
