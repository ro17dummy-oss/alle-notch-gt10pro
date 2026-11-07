package com.walle.notch.ui.island

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Creates the exact liquid top-bezel notch shape matching the user reference:
 * - Concave flared ears at top-left and top-right merging seamlessly into the bezel (y = 0)
 * - Smooth convex rounded corners at bottom-left and bottom-right
 * - Fully enclosed top edge attached to the screen edge
 */
class LiquidNotchShape(
    private val earRadius: Dp = 16.dp,
    private val bottomRadius: Dp = 18.dp
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val rEar = with(density) { earRadius.toPx() }
        val rBottom = with(density) { bottomRadius.toPx() }
        val w = size.width
        val h = size.height

        val path = Path().apply {
            // Start at top-left outer ear at (0, 0)
            moveTo(0f, 0f)

            // Concave curve down-right into the left vertical wall
            quadraticBezierTo(
                rEar * 0.95f, 0f,
                rEar, rEar
            )

            // Left vertical wall down towards bottom-left corner
            lineTo(rEar, (h - rBottom).coerceAtLeast(rEar))

            // Convex curve into flat bottom
            quadraticBezierTo(
                rEar, h,
                rEar + rBottom, h
            )

            // Horizontal bottom edge
            lineTo((w - rEar - rBottom).coerceAtLeast(rEar + rBottom), h)

            // Convex curve up into right vertical wall
            quadraticBezierTo(
                w - rEar, h,
                w - rEar, (h - rBottom).coerceAtLeast(rEar)
            )

            // Right vertical wall up towards top-right ear
            lineTo(w - rEar, rEar)

            // Concave curve up-right out into top bezel
            quadraticBezierTo(
                w - (rEar * 0.05f), 0f,
                w, 0f
            )

            // Close back along top edge (y = 0)
            lineTo(0f, 0f)
            close()
        }

        return Outline.Generic(path)
    }
}
