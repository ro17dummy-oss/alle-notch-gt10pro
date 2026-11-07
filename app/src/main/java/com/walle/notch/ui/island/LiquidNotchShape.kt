package com.walle.notch.ui.island

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 100% Mirror-Symmetric Liquid Notch matching the reference photo:
 * - Left S-curve: (0, 0) -> (cornerWidth, height)
 * - Horizontal flat bottom: (cornerWidth, height) -> (width - cornerWidth, height)
 * - Right S-curve: (width - cornerWidth, height) -> (width, 0) strictly mirrored
 */
class LiquidNotchShape(
    private val cornerWidth: Dp = 20.dp
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cW = with(density) { cornerWidth.toPx() }.coerceAtMost(size.width / 2f)
        val w = size.width
        val h = size.height

        val earW = cW * 0.42f
        val earH = h * 0.46f

        // Left side control points (Cubic Bézier S-curve)
        val cp1x = earW * 0.55f
        val cp1y = 0f
        val cp2x = earW
        val cp2y = earH * 0.45f

        val cp3x = earW
        val cp3y = h - (h - earH) * 0.45f
        val cp4x = cW - (cW - earW) * 0.55f
        val cp4y = h

        // Right side control points (EXACT mathematical mirror: x' = w - x)
        val rcp3x = w - cp4x
        val rcp3y = cp4y
        val rcp4x = w - cp3x
        val rcp4y = cp3y

        val rcp1x = w - cp2x
        val rcp1y = cp2y
        val rcp2x = w - cp1x
        val rcp2y = cp1y

        val path = Path().apply {
            moveTo(0f, 0f)

            // Left top flare arc
            cubicTo(cp1x, cp1y, cp2x, cp2y, earW, earH)

            // Left bottom roundness arc
            cubicTo(cp3x, cp3y, cp4x, cp4y, cW, h)

            // Flat horizontal bottom
            lineTo(w - cW, h)

            // Right bottom roundness arc (mirrored)
            cubicTo(rcp3x, rcp3y, rcp4x, rcp4y, w - earW, earH)

            // Right top flare arc (mirrored)
            cubicTo(rcp1x, rcp1y, rcp2x, rcp2y, w, 0f)

            // Close along top bezel
            lineTo(0f, 0f)
            close()
        }

        return Outline.Generic(path)
    }
}
