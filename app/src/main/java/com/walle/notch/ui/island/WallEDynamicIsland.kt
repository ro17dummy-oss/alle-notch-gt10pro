package com.walle.notch.ui.island

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.walle.notch.core.model.EveMood
import com.walle.notch.core.model.IslandMode
import com.walle.notch.core.model.IslandUiState
import com.walle.notch.core.model.NotificationPayload
import com.walle.notch.core.state.IslandRepository
import com.walle.notch.service.NotchNotificationListenerService
import com.walle.notch.ui.theme.AxiomAlertRed
import com.walle.notch.ui.theme.DirectivePlantGreen
import com.walle.notch.ui.theme.EveCyanGlow
import com.walle.notch.ui.theme.PitchBlack
import com.walle.notch.ui.theme.WallESolarAmber
import kotlinx.coroutines.delay

private val CuteBlushPink = Color(0xFFFF7597)
private val SoftCyanHalo = Color(0xFF00E5FF)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WallEDynamicIslandOverlay(
    state: IslandUiState,
    onRequestKeyboardFocus: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val cal = state.calibration

    LaunchedEffect(state.isExpanded, state.mode) {
        if (!state.isExpanded || state.mode != IslandMode.NOTIFICATION) {
            onRequestKeyboardFocus(false)
        }
    }

    // Auto-collapse seperti Nusa / Dynamic Island setelah 7 detik tanpa interaksi
    LaunchedEffect(state.isExpanded, state.eveMood, state.mode) {
        if (state.isExpanded && state.mode != IslandMode.NOTIFICATION) {
            delay(7000L)
            IslandRepository.setExpanded(false)
        }
    }

    // Perilaku sembunyi otomatis sesuai permintaan pengguna:
    // 1. Hilang sepenuhnya jika layar horizontal
    // 2. Hilang ketika membuka Aplikasi saat mode Standby IDLE (muncul jika ada event Dynamic Island: musik/notif/cas)
    // 3. Hilang ketika gulir menu atas/bawah (status bar / notification shade)
    val isCompletelyHidden = (cal.hideWhenHorizontal && state.isLandscapeHorizontal) ||
        (cal.hideWhenAppOpen && state.isAppOpen && state.mode == IslandMode.IDLE && !state.isExpanded) ||
        (cal.hideWhenMenuScrolled && state.isNotificationShadeOpen && !state.isExpanded)

    if (isCompletelyHidden) {
        Box(modifier = Modifier.size(0.dp))
        return
    }

    val isHiddenInCamera = cal.autoHideLandscape && state.isLandscapeFullscreen && !state.isExpanded

    // Tetap menggunakan proporsi kurva LiquidNotchShape baik saat Collapse maupun Expand
    val cornerWidth = if (state.isExpanded) {
        (cal.cornerWidthDp + 4).coerceAtMost(44).dp
    } else {
        cal.cornerWidthDp.dp
    }

    val targetTotalWidth: Dp = when {
        isHiddenInCamera -> cal.cameraHoleSizeDp.dp
        state.isExpanded -> when (state.mode) {
            IslandMode.IDLE -> 276.dp          // Giant EVE Visor: proporsional seperti collapse versi besar
            IslandMode.MUSIC -> 304.dp         // Nusa-style sleek music player
            IslandMode.SOLAR_CHARGE -> 284.dp  // Sleek solar charge visor
            IslandMode.NOTIFICATION -> 312.dp  // Clean quick reply card
            IslandMode.CALL_TIMER -> 292.dp
        }
        else -> when (state.mode) {
            IslandMode.IDLE -> cal.idleWidthDp.dp
            IslandMode.MUSIC -> 248.dp
            IslandMode.SOLAR_CHARGE -> 232.dp
            IslandMode.NOTIFICATION -> 248.dp
            IslandMode.CALL_TIMER -> 244.dp
        }
    }

    val targetHeight: Dp = when {
        isHiddenInCamera -> cal.cameraHoleSizeDp.dp
        state.isExpanded -> when (state.mode) {
            IslandMode.IDLE -> 88.dp           // Giant EVE Visor: ramping, bersih, fokus ke mata EVE besar
            IslandMode.MUSIC -> 114.dp
            IslandMode.SOLAR_CHARGE -> 94.dp
            IslandMode.NOTIFICATION -> 128.dp
            IslandMode.CALL_TIMER -> 104.dp
        }
        else -> cal.idleHeightDp.dp
    }

    val animatedWidth by animateDpAsState(
        targetValue = targetTotalWidth,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "notchWidth"
    )

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "notchHeight"
    )

    // 100% Mirror-Symmetric Liquid Notch Shape
    val notchShape = remember(cornerWidth) {
        LiquidNotchShape(cornerWidth = cornerWidth)
    }

    Box(
        modifier = Modifier
            .width(animatedWidth)
            .height(animatedHeight)
            .shadow(
                elevation = if (state.isExpanded) 18.dp else 6.dp,
                shape = notchShape,
                ambientColor = PitchBlack,
                spotColor = PitchBlack
            )
            .clip(notchShape)
            .background(PitchBlack)
            .pointerInput(state.isExpanded) {
                if (state.isExpanded) {
                    var totalDragY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDragY = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            totalDragY += dragAmount
                            if (totalDragY < -18f) {
                                try {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                } catch (_: Exception) {}
                                IslandRepository.setExpanded(false)
                            }
                        }
                    )
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    try {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } catch (_: Exception) {}

                    if (!state.isExpanded) {
                        // Ketuk saat collapse -> Membesar (Expand) seperti Dynamic Island
                        IslandRepository.setExpanded(true)
                    } else if (state.mode == IslandMode.IDLE) {
                        // Saat Giant EVE Visor terbuka, ketuk untuk mengganti ekspresi wajah EVE!
                        IslandRepository.cycleEveMood(context)
                    } else {
                        IslandRepository.setExpanded(false)
                    }
                },
                onLongClick = {
                    try {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    } catch (_: Exception) {}
                    if (state.isExpanded) {
                        IslandRepository.setExpanded(false)
                    } else {
                        IslandRepository.cycleEveMood(context)
                    }
                }
            )
            .padding(horizontal = cornerWidth),
        contentAlignment = Alignment.TopCenter
    ) {
        // Garis kilau kaca helm EVE tipis (Pixar Visor Glass Shine) saat di-expand
        if (state.isExpanded) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val shinePath = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.14f)
                    quadraticBezierTo(
                        size.width * 0.5f,
                        size.height * 0.04f,
                        size.width * 0.92f,
                        size.height * 0.14f
                    )
                }
                drawPath(
                    path = shinePath,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            EveCyanGlow.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.22f),
                            EveCyanGlow.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    ),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        if (!isHiddenInCamera) {
            when (state.mode) {
                IslandMode.IDLE -> IdleModuleContent(state)
                IslandMode.MUSIC -> MusicModuleContent(state)
                IslandMode.SOLAR_CHARGE -> SolarChargeModuleContent(state)
                IslandMode.NOTIFICATION -> NotificationModuleContent(
                    state = state,
                    onFocusInput = onRequestKeyboardFocus
                )
                IslandMode.CALL_TIMER -> CallTimerModuleContent(state)
            }
        }

        // Hardware-calibrated camera hole guide for Infinix GT 10 Pro
        if (cal.xrayCameraGuide) {
            Box(
                modifier = Modifier
                    .padding(top = cal.cameraTopOffsetDp.dp)
                    .size(cal.cameraHoleSizeDp.dp)
                    .clip(CircleShape)
                    .background(AxiomAlertRed.copy(alpha = 0.45f))
            )
        }
    }
}

/**
 * Mode Standby & Giant Living EVE Visor:
 * Saat Collapse: Wajah EVE imut mengapit kamera.
 * Saat Expand: Tampilan sama persis seperti Collapse namun dalam skala besar (Giant EVE Visor 2.35x)
 * tanpa teks berantakan atau tombol kotak kaku!
 */
@Composable
private fun IdleModuleContent(state: IslandUiState) {
    val infinite = rememberInfiniteTransition(label = "cuteAnimations")

    val eyeScaleY by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 170, delayMillis = 3400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eyeScaleY"
    )

    val eyeFloatY by infinite.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eyeFloatY"
    )

    val heartScale by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartPulse"
    )

    val blushAlpha by infinite.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blushPulse"
    )

    if (!state.isExpanded) {
        // COLLAPSED STANDBY (202dp x 36dp)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Wing: Soft Blush + Left Eye
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 6.dp, height = 3.5.dp)
                        .clip(CircleShape)
                        .background(CuteBlushPink.copy(alpha = blushAlpha))
                )

                CuteEyeRenderer(
                    mood = state.eveMood,
                    isLeft = true,
                    scaleY = eyeScaleY,
                    heartScale = heartScale,
                    sizeScale = 1f
                )
            }

            // Punch hole camera clear area in center
            Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

            // Right Wing: Right Eye + Soft Blush / Accessory
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CuteEyeRenderer(
                    mood = state.eveMood,
                    isLeft = false,
                    scaleY = eyeScaleY,
                    heartScale = heartScale,
                    sizeScale = 1f
                )

                if (state.eveMood == EveMood.PLANT) {
                    Text(
                        text = "🌱",
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                } else if (state.eveMood == EveMood.SLEEPY) {
                    Text(
                        text = "zZ",
                        color = EveCyanGlow.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 3.5.dp)
                            .clip(CircleShape)
                            .background(CuteBlushPink.copy(alpha = blushAlpha))
                    )
                }
            }
        }
    } else {
        // EXPANDED: GIANT LIVING EVE VISOR (Tampilan sama seperti Collapse namun ukuran besar 2.3x & sangat hidup!)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp, bottom = 8.dp, start = 6.dp, end = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .offset(y = eyeFloatY.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Giant Left Wing: Soft Glowing Blush + Giant Left Eye
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .size(width = 13.dp, height = 7.dp)
                            .clip(CircleShape)
                            .background(CuteBlushPink.copy(alpha = blushAlpha))
                    )

                    CuteEyeRenderer(
                        mood = state.eveMood,
                        isLeft = true,
                        scaleY = eyeScaleY,
                        heartScale = heartScale,
                        sizeScale = 2.35f
                    )
                }

                // Ruang tengah kamera fisik
                Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 8).dp))

                // Giant Right Wing: Giant Right Eye + Soft Glowing Blush / Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    CuteEyeRenderer(
                        mood = state.eveMood,
                        isLeft = false,
                        scaleY = eyeScaleY,
                        heartScale = heartScale,
                        sizeScale = 2.35f
                    )

                    if (state.eveMood == EveMood.PLANT) {
                        Text(
                            text = "🌱",
                            fontSize = 18.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else if (state.eveMood == EveMood.SLEEPY) {
                        Text(
                            text = "zZ",
                            color = EveCyanGlow.copy(alpha = 0.85f),
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .size(width = 13.dp, height = 7.dp)
                                .clip(CircleShape)
                                .background(CuteBlushPink.copy(alpha = blushAlpha))
                        )
                    }
                }
            }

            // Indikator titik mood minimalis di bagian paling bawah visor (tanpa teks mengganggu)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clickable {
                        IslandRepository.setExpanded(false)
                    }
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val moods = EveMood.values()
                for (m in moods) {
                    val isSelected = m == state.eveMood
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 12.dp else 4.dp,
                        label = "dotW"
                    )
                    Box(
                        modifier = Modifier
                            .height(3.5.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) EveCyanGlow.copy(alpha = 0.85f)
                                else Color.White.copy(alpha = 0.22f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CuteEyeRenderer(
    mood: EveMood,
    isLeft: Boolean,
    scaleY: Float,
    heartScale: Float,
    sizeScale: Float = 1f
) {
    when (mood) {
        EveMood.HAPPY -> {
            Canvas(
                modifier = Modifier.size(
                    width = (12.5f * sizeScale).dp,
                    height = (7.5f * sizeScale).dp
                )
            ) {
                val path = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.88f)
                    quadraticBezierTo(
                        size.width * 0.5f,
                        -size.height * 0.05f,
                        size.width * 0.92f,
                        size.height * 0.88f
                    )
                }
                // Outer Neon Cyan Halo Glow
                drawPath(
                    path = path,
                    color = SoftCyanHalo.copy(alpha = 0.28f),
                    style = Stroke(width = (5.2f * sizeScale).dp.toPx(), cap = StrokeCap.Round)
                )
                // Core Bright Cyan Eye
                drawPath(
                    path = path,
                    color = EveCyanGlow,
                    style = Stroke(width = (2.4f * sizeScale).dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        EveMood.WINK -> {
            if (isLeft) {
                // Mata kiri terbuka oval bercahaya khas Pixar EVE
                Canvas(
                    modifier = Modifier
                        .size(width = (10.5f * sizeScale).dp, height = (7f * sizeScale).dp)
                        .rotate(-6f)
                ) {
                    drawOval(
                        color = SoftCyanHalo.copy(alpha = 0.28f),
                        size = size.copy(width = size.width * 1.18f, height = size.height * 1.18f),
                        topLeft = Offset(-size.width * 0.09f, -size.height * 0.09f)
                    )
                    drawOval(color = EveCyanGlow)
                }
            } else {
                // Mata kanan mengedip melengkung manis
                Canvas(
                    modifier = Modifier.size(
                        width = (12.5f * sizeScale).dp,
                        height = (7.5f * sizeScale).dp
                    )
                ) {
                    val path = Path().apply {
                        moveTo(size.width * 0.08f, size.height * 0.85f)
                        quadraticBezierTo(
                            size.width * 0.5f,
                            0f,
                            size.width * 0.92f,
                            size.height * 0.85f
                        )
                    }
                    drawPath(
                        path = path,
                        color = SoftCyanHalo.copy(alpha = 0.28f),
                        style = Stroke(width = (5.0f * sizeScale).dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = path,
                        color = EveCyanGlow,
                        style = Stroke(width = (2.4f * sizeScale).dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
        EveMood.LOVE -> {
            Canvas(
                modifier = Modifier
                    .size(width = (11.5f * sizeScale).dp, height = (11.5f * sizeScale).dp)
                    .scale(heartScale)
            ) {
                val w = size.width
                val h = size.height
                val heartPath = Path().apply {
                    moveTo(w / 2f, h)
                    cubicTo(0f, h * 0.65f, 0f, 0f, w / 4f, 0f)
                    cubicTo(w / 2f, 0f, w / 2f, h * 0.32f, w / 2f, h * 0.32f)
                    cubicTo(w / 2f, h * 0.32f, w / 2f, 0f, (w * 3f) / 4f, 0f)
                    cubicTo(w, 0f, w, h * 0.65f, w / 2f, h)
                    close()
                }
                drawPath(
                    path = heartPath,
                    color = CuteBlushPink.copy(alpha = 0.35f),
                    style = Stroke(width = (3.5f * sizeScale).dp.toPx())
                )
                drawPath(path = heartPath, color = CuteBlushPink)
            }
        }
        EveMood.SLEEPY -> {
            // Mata terpejam melengkung ke bawah seperti sedang tidur nyenyak (Nusa Mascot Doze)
            Canvas(
                modifier = Modifier.size(
                    width = (11.5f * sizeScale).dp,
                    height = (5.5f * sizeScale).dp
                )
            ) {
                val path = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.25f)
                    quadraticBezierTo(
                        size.width * 0.5f,
                        size.height * 0.95f,
                        size.width * 0.92f,
                        size.height * 0.25f
                    )
                }
                drawPath(
                    path = path,
                    color = SoftCyanHalo.copy(alpha = 0.25f),
                    style = Stroke(width = (4.5f * sizeScale).dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = path,
                    color = EveCyanGlow.copy(alpha = 0.9f),
                    style = Stroke(width = (2.2f * sizeScale).dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        EveMood.PLANT, EveMood.NORMAL -> {
            // Mata oval miring khas EVE di film Pixar WALL-E dengan kedipan halus
            Canvas(
                modifier = Modifier
                    .size(width = (11f * sizeScale).dp, height = (7f * sizeScale).dp)
                    .rotate(if (isLeft) 7f else -7f)
                    .scale(scaleX = 1f, scaleY = scaleY)
            ) {
                drawOval(
                    color = SoftCyanHalo.copy(alpha = 0.30f),
                    size = size.copy(width = size.width * 1.22f, height = size.height * 1.22f),
                    topLeft = Offset(-size.width * 0.11f, -size.height * 0.11f)
                )
                drawOval(color = EveCyanGlow)
            }
        }
    }
}

/**
 * Mode Musik ala Nusa Notch:
 * - Compact: Sisi kiri (Album Art + Judul Marquee berjalan), Tengah (Kamera), Sisi kanan (Mata EVE + Waveform)
 * - Expanded: Kartu musik ramping dengan glowing progress head & teks marquee halus
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicModuleContent(state: IslandUiState) {
    val music = state.music
    val infinite = rememberInfiniteTransition(label = "eqWave")
    val bar1 by infinite.animateFloat(
        initialValue = 0.28f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(360), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "b3"
    )
    val bar4 by infinite.animateFloat(
        initialValue = 0.80f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(440), RepeatMode.Reverse),
        label = "b4"
    )

    val discSpin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "discSpin"
    )

    if (!state.isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Wing: Spinning Album Disc + Smooth Marquee Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(19.dp)
                        .rotate(if (music.isPlaying) discSpin else 0f)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(WallESolarAmber, EveCyanGlow))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (music.albumArt != null) {
                        Image(
                            bitmap = music.albumArt.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PitchBlack)
                        )
                    }
                }
                Text(
                    text = "${music.title} • ${music.artist}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee()
                )
            }

            // Center Camera Hole Spacer
            Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

            // Right Wing: EVE Eye + Live Waveform Equalizer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                CuteEyeRenderer(
                    mood = EveMood.HAPPY,
                    isLeft = false,
                    scaleY = 1f,
                    heartScale = 1f,
                    sizeScale = 0.85f
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.height(13.dp)
                ) {
                    val m1 = if (music.isPlaying) bar1 else 0.28f
                    val m2 = if (music.isPlaying) bar2 else 0.28f
                    val m3 = if (music.isPlaying) bar3 else 0.28f
                    val m4 = if (music.isPlaying) bar4 else 0.28f
                    Box(Modifier.width(2.2.dp).fillMaxHeight(m1).clip(CircleShape).background(EveCyanGlow))
                    Box(Modifier.width(2.2.dp).fillMaxHeight(m2).clip(CircleShape).background(WallESolarAmber))
                    Box(Modifier.width(2.2.dp).fillMaxHeight(m3).clip(CircleShape).background(DirectivePlantGreen))
                    Box(Modifier.width(2.2.dp).fillMaxHeight(m4).clip(CircleShape).background(CuteBlushPink))
                }
            }
        }
    } else {
        // EXPANDED NUSA-STYLE SLEEK MUSIC VISOR
        val progress = if (music.durationMs > 0) {
            (music.positionMs.toFloat() / music.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0.4f
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp, bottom = 10.dp, start = 6.dp, end = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row framing the camera hole
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(WallESolarAmber, EveCyanGlow))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (music.albumArt != null) {
                            Image(
                                bitmap = music.albumArt.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "♫",
                                color = PitchBlack,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = music.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = music.artist,
                            color = EveCyanGlow.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }

                Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 4).dp))

                // Right side: Animated Equalizer + Mini EVE Eyes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CuteEyeRenderer(
                        mood = state.eveMood,
                        isLeft = true,
                        scaleY = 1f,
                        heartScale = 1f,
                        sizeScale = 1.05f
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                        modifier = Modifier.height(16.dp)
                    ) {
                        val m1 = if (music.isPlaying) bar1 else 0.3f
                        val m2 = if (music.isPlaying) bar2 else 0.3f
                        val m3 = if (music.isPlaying) bar3 else 0.3f
                        val m4 = if (music.isPlaying) bar4 else 0.3f
                        Box(Modifier.width(2.5.dp).fillMaxHeight(m1).clip(CircleShape).background(EveCyanGlow))
                        Box(Modifier.width(2.5.dp).fillMaxHeight(m2).clip(CircleShape).background(WallESolarAmber))
                        Box(Modifier.width(2.5.dp).fillMaxHeight(m3).clip(CircleShape).background(DirectivePlantGreen))
                        Box(Modifier.width(2.5.dp).fillMaxHeight(m4).clip(CircleShape).background(CuteBlushPink))
                    }
                }
            }

            // Middle Row: Nusa-style Glowing Progress Scrubber
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatSeconds((music.positionMs / 1000L).toInt()),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(listOf(EveCyanGlow, WallESolarAmber))
                            )
                    )
                }
                val remainingSec = ((music.durationMs - music.positionMs).coerceAtLeast(0L) / 1000L).toInt()
                Text(
                    text = "-${formatSeconds(remainingSec)}",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Bottom Row: Minimalist Media Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillActionButton(
                    text = "EVE Visor",
                    bg = Color.White.copy(alpha = 0.10f),
                    fg = EveCyanGlow
                ) {
                    IslandRepository.setMode(IslandMode.IDLE, expand = true)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillActionButton(text = "⏮", bg = Color.White.copy(alpha = 0.12f), fg = Color.White) {
                        IslandRepository.onMediaPrevious?.invoke()
                    }
                    PillActionButton(
                        text = if (music.isPlaying) "⏸" else "▶",
                        bg = EveCyanGlow,
                        fg = PitchBlack
                    ) {
                        IslandRepository.onMediaPlayPause?.invoke()
                    }
                    PillActionButton(text = "⏭", bg = Color.White.copy(alpha = 0.12f), fg = Color.White) {
                        IslandRepository.onMediaNext?.invoke()
                    }
                }
            }
        }
    }
}

@Composable
private fun SolarChargeModuleContent(state: IslandUiState) {
    val bat = state.battery
    val animatedPct by animateFloatAsState(
        targetValue = (bat.level / 100f).coerceIn(0.05f, 1f),
        label = "batPct"
    )

    if (!state.isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "⚡",
                    fontSize = 11.sp
                )
                Text(
                    text = "45W",
                    color = WallESolarAmber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

            Text(
                text = "${bat.level}%",
                color = DirectivePlantGreen,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold
            )
        }
    } else {
        // Sleek Expanded Solar Charge Visor
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp, bottom = 10.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CuteEyeRenderer(
                        mood = EveMood.HAPPY,
                        isLeft = true,
                        scaleY = 1f,
                        heartScale = 1f,
                        sizeScale = 1.4f
                    )
                    Text(
                        text = "⚡ 45W",
                        color = WallESolarAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 4).dp))

                Text(
                    text = "${bat.level}% • ${bat.temperatureCelsius}°C",
                    color = DirectivePlantGreen,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
            }

            // Glowing Solar Energy Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedPct)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(WallESolarAmber, DirectivePlantGreen, EveCyanGlow)
                            )
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationModuleContent(
    state: IslandUiState,
    onFocusInput: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val notif = state.notification ?: NotificationPayload()
    var replyText by remember { mutableStateOf("") }

    if (!state.isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(DirectivePlantGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = notif.appLabel.take(2),
                        color = PitchBlack,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = notif.sender,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

            Text(
                text = notif.message,
                color = EveCyanGlow,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee()
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp, bottom = 10.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(DirectivePlantGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = notif.appLabel.take(2),
                            color = PitchBlack,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = notif.sender,
                        color = EveCyanGlow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 4).dp))

                PillActionButton(text = "Selesai", bg = Color.White.copy(alpha = 0.12f), fg = Color.White) {
                    onFocusInput(false)
                    IslandRepository.setMode(IslandMode.IDLE, expand = false)
                }
            }

            Text(
                text = notif.message,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, EveCyanGlow.copy(alpha = 0.25f), CircleShape)
                        .clickable { onFocusInput(true) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (replyText.isEmpty()) {
                        Text(
                            text = "Balas pesan...",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp
                        )
                    }
                    BasicTextField(
                        value = replyText,
                        onValueChange = {
                            onFocusInput(true)
                            replyText = it
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                        cursorBrush = SolidColor(EveCyanGlow),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PillActionButton(text = "Kirim", bg = EveCyanGlow, fg = PitchBlack) {
                    val finalMsg = replyText.ifBlank { "Siap, diterima!" }
                    NotchNotificationListenerService.sendQuickReply(context, notif, finalMsg)
                    replyText = ""
                    onFocusInput(false)
                    IslandRepository.setMode(IslandMode.IDLE, expand = false)
                }
            }
        }
    }
}

@Composable
private fun CallTimerModuleContent(state: IslandUiState) {
    val ct = state.callTimer
    if (!state.isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = ct.callerName,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

            Text(
                text = formatSeconds(ct.timerRemainingSec),
                color = WallESolarAmber,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp, bottom = 10.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ct.callerName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 4).dp))
                Text(
                    text = if (ct.isCallConnected) formatSeconds(ct.callDurationSec) else formatSeconds(ct.timerRemainingSec),
                    color = DirectivePlantGreen,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!ct.isCallConnected) {
                    PillActionButton(
                        text = "Terima",
                        bg = DirectivePlantGreen,
                        fg = PitchBlack
                    ) {
                        ct.acceptIntent?.let {
                            try { it.send() } catch (_: Exception) {}
                        }
                        IslandRepository.updateCallTimer {
                            it.copy(isCallConnected = true, isIncomingCall = false, callDurationSec = 1)
                        }
                    }
                }
                PillActionButton(
                    text = if (ct.isCallConnected) "Akhiri" else "Tolak",
                    bg = AxiomAlertRed,
                    fg = Color.White
                ) {
                    ct.declineIntent?.let {
                        try { it.send() } catch (_: Exception) {}
                    }
                    IslandRepository.updateCallTimer {
                        it.copy(isCallConnected = false, isIncomingCall = false, callDurationSec = 0)
                    }
                    IslandRepository.setMode(IslandMode.IDLE, expand = false)
                }
            }
        }
    }
}

@Composable
private fun PillActionButton(
    text: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatSeconds(totalSec: Int): String {
    val m = (totalSec / 60).coerceAtLeast(0)
    val s = (totalSec % 60).coerceAtLeast(0)
    return "%02d:%02d".format(m, s)
}
