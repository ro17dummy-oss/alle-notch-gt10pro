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
 * 100% Mirror-Symmetric Liquid Notch with Continuous Squircle Bottom Corners:
 * - Top Concave Ear Flare: (0, 0) -> (earW, earH) with 1:1 aspect ratio at the top bezel
 * - Vertical Side Wall: (earW, earH) -> (earW, h - bottomCornerH) for clean straight sides when expanded
 * - Bottom Convex Squircle Corner: (earW, h - bottomCornerH) -> (earW + bottomR, h) with 1:1 aspect ratio
 * - Flat Horizontal Bottom: (earW + bottomR, h) -> (w - earW - bottomR, h)
 * - Strictly mirrored right side: (w - earW - bottomR, h) -> (w, 0)
 */
class LiquidNotchShape(
    private val cornerWidth: Dp = 36.dp,
    private val bottomCornerRadius: Dp? = null
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(
            buildLiquidNotchPath(
                size = size,
                density = density,
                cornerWidth = cornerWidth,
                bottomCornerRadius = bottomCornerRadius
            )
        )
    }

    companion object {
        fun buildLiquidNotchPath(
            size: Size,
            density: Density,
            cornerWidth: Dp,
            bottomCornerRadius: Dp? = null
        ): Path {
            val w = size.width
            val h = size.height
            val cW = with(density) { cornerWidth.toPx() }.coerceAtMost(w * 0.45f)

            // Top bezel concave ear flare stays compact and crisp at the top edge
            val earW = cW * 0.36f
            val earH = earW.coerceAtMost(h * 0.42f)

            // Bottom convex corner radius: 64% of cornerWidth in standby, or explicit radius when expanded
            val requestedBottomR = bottomCornerRadius?.let { with(density) { it.toPx() } } ?: (cW - earW)
            val maxBottomR = ((w / 2f) - earW).coerceAtLeast(0f)
            val bottomR = requestedBottomR.coerceAtMost(maxBottomR)

            // Keep 1:1 aspect ratio so expanding height never stretches the bottom corner vertically
            val bottomCornerH = bottomR.coerceAtMost((h - earH).coerceAtLeast(0f))
            val bottomStartY = (h - bottomCornerH).coerceAtLeast(earH)
            val bottomEndX = earW + bottomR

            // Bezier kappa constants: 0.55228 for top ear, 0.62 for smooth Apple-style bottom squircle
            val kEar = 0.55228f
            val kSquircle = 0.62f

            return Path().apply {
                moveTo(0f, 0f)

                // 1. Left Top Concave Ear Flare: (0, 0) -> (earW, earH)
                cubicTo(
                    earW * kEar, 0f,
                    earW, earH * (1f - kEar),
                    earW, earH
                )

                // 2. Left Vertical Side Wall: (earW, earH) -> (earW, bottomStartY)
                if (bottomStartY > earH) {
                    lineTo(earW, bottomStartY)
                }

                // 3. Left Bottom Convex Squircle Corner: (earW, bottomStartY) -> (bottomEndX, h)
                cubicTo(
                    earW, bottomStartY + bottomCornerH * kSquircle,
                    bottomEndX - bottomR * kSquircle, h,
                    bottomEndX, h
                )

                // 4. Flat Horizontal Bottom: (bottomEndX, h) -> (w - bottomEndX, h)
                lineTo(w - bottomEndX, h)

                // 5. Right Bottom Convex Squircle Corner (Exact Mirror): (w - bottomEndX, h) -> (w - earW, bottomStartY)
                cubicTo(
                    w - bottomEndX + bottomR * kSquircle, h,
                    w - earW, bottomStartY + bottomCornerH * kSquircle,
                    w - earW, bottomStartY
                )

                // 6. Right Vertical Side Wall (Exact Mirror): (w - earW, bottomStartY) -> (w - earW, earH)
                if (bottomStartY > earH) {
                    lineTo(w - earW, earH)
                }

                // 7. Right Top Concave Ear Flare (Exact Mirror): (w - earW, earH) -> (w, 0)
                cubicTo(
                    w - earW, earH * (1f - kEar),
                    w - earW * kEar, 0f,
                    w, 0f
                )

                // 8. Close along top bezel
                lineTo(0f, 0f)
                close()
            }
        }
    }
}
