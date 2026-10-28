package com.example.personabgm.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Parallelogram used for the Persona 5 look. [slant] is horizontal offset as a fraction of the
 * shape's height, so the angle stays the same whatever the width.
 */
class SlantedShape(private val slant: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val dx = (size.height * slant).coerceIn(0f, size.width / 2f)
        val path = Path().apply {
            moveTo(dx, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - dx, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is SlantedShape && other.slant == slant
    override fun hashCode(): Int = slant.hashCode()
}
