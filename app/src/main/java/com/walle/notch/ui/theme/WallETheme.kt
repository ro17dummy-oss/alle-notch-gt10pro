package com.walle.notch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Wall-E & EVE Signature Colors (Optimized for Infinix GT 10 Pro AMOLED)
val PitchBlack = Color(0xFF000000)
val WallESolarAmber = Color(0xFFF5B041)
val EveCyanGlow = Color(0xFF00E5FF)
val DirectivePlantGreen = Color(0xFF2ECC71)
val AxiomAlertRed = Color(0xFFFF3B5C)
val MechaDarkSurface = Color(0xFF0D131F)
val MechaCardSurface = Color(0xFF151E30)
val MechaBorder = Color(0xFF263552)

private val WallEDarkColorScheme = darkColorScheme(
    primary = WallESolarAmber,
    secondary = EveCyanGlow,
    tertiary = DirectivePlantGreen,
    background = MechaDarkSurface,
    surface = MechaCardSurface,
    onPrimary = PitchBlack,
    onSecondary = PitchBlack,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun WallENotchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WallEDarkColorScheme,
        content = content
    )
}
