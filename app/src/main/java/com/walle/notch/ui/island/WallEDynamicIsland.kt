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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.walle.notch.core.model.EveIdlePhase
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

private enum class MediaIconType {
    PREV, PLAY, PAUSE, NEXT
}

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
    LaunchedEffect(state.isExpanded, state.eveMood, state.mode, state.music.isPlaying) {
        if (state.isExpanded && state.mode != IslandMode.NOTIFICATION) {
            delay(7500L)
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

    val cornerWidth = cal.cornerWidthDp.dp
    val targetBottomRadius = if (state.isExpanded) {
        28.dp
    } else {
        (cal.cornerWidthDp * 0.64f).dp
    }

    val targetTotalWidth: Dp = when {
        isHiddenInCamera -> cal.cameraHoleSizeDp.dp
        state.isExpanded -> when (state.mode) {
            IslandMode.IDLE -> 280.dp          // Giant EVE Visor: proporsional seperti collapse versi besar
            IslandMode.MUSIC -> 318.dp         // Nusa-style sleek music player (lebar lega)
            IslandMode.SOLAR_CHARGE -> 296.dp  // Sleek solar charge visor
            IslandMode.NOTIFICATION -> 320.dp  // Clean quick reply card
            IslandMode.CALL_TIMER -> 300.dp
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
            IslandMode.IDLE -> 92.dp           // Giant EVE Visor: ramping, bersih, fokus ke mata EVE besar
            IslandMode.MUSIC -> 148.dp         // Tinggi cukup agar tombol bawah & judul tidak kepotong
            IslandMode.SOLAR_CHARGE -> 114.dp
            IslandMode.NOTIFICATION -> 152.dp
            IslandMode.CALL_TIMER -> 124.dp
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

    val animatedBottomRadius by animateDpAsState(
        targetValue = targetBottomRadius,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "notchBottomRadius"
    )

    val animatedHorizontalPad by animateDpAsState(
        targetValue = if (state.isExpanded) 24.dp else cornerWidth,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "notchHorizontalPad"
    )

    // 100% Mirror-Symmetric Liquid Notch Shape with 1:1 Bottom Squircle Corners
    val notchShape = remember(cornerWidth, animatedBottomRadius) {
        LiquidNotchShape(
            cornerWidth = cornerWidth,
            bottomCornerRadius = animatedBottomRadius
        )
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
            .pointerInput(state.isExpanded, state.mode) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        totalDragY += dragAmount
                        if (totalDragY < -18f) {
                            try {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            } catch (_: Exception) {}
                            if (state.isExpanded) {
                                IslandRepository.setExpanded(false)
                            } else if (state.mode == IslandMode.IDLE) {
                                // Gesture swipe up saat standby: langsung tidur instan
                                IslandRepository.triggerQuickNap()
                            }
                        }
                    }
                )
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    try {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } catch (_: Exception) {}

                    if (!state.isExpanded) {
                        IslandRepository.setExpanded(true)
                    } else if (state.mode == IslandMode.IDLE) {
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
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        // Garis kontur kurva bawah halus (Bottom Squircle Rim Highlight) saat di-expand
        if (state.isExpanded) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val rimPath = LiquidNotchShape.buildLiquidNotchPath(
                    size = size,
                    density = this,
                    cornerWidth = cornerWidth,
                    bottomCornerRadius = animatedBottomRadius
                )
                drawPath(
                    path = rimPath,
                    brush = Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.78f to Color.White.copy(alpha = 0.08f),
                        1.0f to EveCyanGlow.copy(alpha = 0.24f)
                    ),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        if (!isHiddenInCamera) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = animatedHorizontalPad),
                contentAlignment = Alignment.TopCenter
            ) {
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
        // Jika sedang Touch Blackout, notch hitam murni tanpa konten
        if (state.eveIdlePhase == EveIdlePhase.TOUCH_BLACKOUT) {
            Box(modifier = Modifier.fillMaxSize())
            return
        }

        // Animasi bernapas (breathing glow) saat tidur nyenyak
        val breathingAlpha by infinite.animateFloat(
            initialValue = 0.55f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sleepBreathingAlpha"
        )

        // Animasi lirik mata realistis sebelum tidur (PRE_SLEEP_LOOK)
        val lookOffsetXDp by animateDpAsState(
            targetValue = when (state.eveIdlePhase) {
                EveIdlePhase.PRE_SLEEP_LOOK -> (-4).dp
                else -> 0.dp
            },
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            label = "lookOffsetXDp"
        )

        // Animasi ritme ZZZ lincah tema Wall-E (Cyan & Amber, siklus 1.2s)
        val zzzAlpha1 by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, delayMillis = 0, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "z1"
        )
        val zzzAlpha2 by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, delayMillis = 200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "z2"
        )
        val zzzAlpha3 by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, delayMillis = 400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "z3"
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Wing: MATA KIRI EVE
            Row(
                modifier = Modifier
                    .weight(1f)
                    .offset(x = lookOffsetXDp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                EveStandbyEyeRenderer(
                    phase = state.eveIdlePhase,
                    blinkScaleY = eyeScaleY,
                    breathingAlpha = breathingAlpha,
                    isLeft = true
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Punch hole camera clear area in center (23dp Infinix GT 10 Pro)
            Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

            // Right Wing: MATA KANAN EVE + LIVELY ZZZ (Wall-E Theme)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .offset(x = lookOffsetXDp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Spacer(modifier = Modifier.width(6.dp))
                EveStandbyEyeRenderer(
                    phase = state.eveIdlePhase,
                    blinkScaleY = eyeScaleY,
                    breathingAlpha = breathingAlpha,
                    isLeft = false
                )

                // Emot Z Z Z saat tidur nyenyak
                if (state.eveIdlePhase == EveIdlePhase.SLEEPING_ZZZ) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        Text(
                            text = "z",
                            color = EveCyanGlow.copy(alpha = zzzAlpha1),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        Text(
                            text = "Z",
                            color = WallESolarAmber.copy(alpha = zzzAlpha2),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        Text(
                            text = "Z",
                            color = EveCyanGlow.copy(alpha = zzzAlpha3),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }
    } else {
        // EXPANDED: GIANT LIVING EVE VISOR
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp, bottom = 10.dp, start = 4.dp, end = 4.dp)
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
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .size(width = 13.dp, height = 7.dp)
                            .clip(CircleShape)
                            .background(CuteBlushPink.copy(alpha = blushAlpha))
                    )
                    Spacer(modifier = Modifier.width(7.dp))
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
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    CuteEyeRenderer(
                        mood = state.eveMood,
                        isLeft = false,
                        scaleY = eyeScaleY,
                        heartScale = heartScale,
                        sizeScale = 2.35f
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    if (state.eveMood == EveMood.PLANT) {
                        Text(
                            text = "🌱",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else if (state.eveMood == EveMood.SLEEPY) {
                        Text(
                            text = "zZ",
                            color = EveCyanGlow.copy(alpha = 0.85f),
                            fontSize = 14.sp,
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

            // Indikator titik mood minimalis di bagian paling bawah visor
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clickable {
                        IslandRepository.setExpanded(false)
                    }
                    .padding(bottom = 2.dp),
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

/**
 * Renderer Mata EVE Khusus Standby (Camera-Flanking):
 * Mendukung fase AWAKE_BLINK, PRE_SLEEP_LOOK, SLEEPING_ZZZ, WAKE_OPEN, dan WAKE_SMILE.
 */
@Composable
private fun EveStandbyEyeRenderer(
    phase: EveIdlePhase,
    blinkScaleY: Float,
    breathingAlpha: Float,
    isLeft: Boolean
) {
    when (phase) {
        EveIdlePhase.WAKE_SMILE -> {
            // Tahap 2 Saat Bangun: Senyum manis melengkung ^_^
            Canvas(
                modifier = Modifier.size(width = 12.dp, height = 7.dp)
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
                drawPath(
                    path = path,
                    color = EveCyanGlow.copy(alpha = 0.35f),
                    style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = path,
                    color = EveCyanGlow,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        EveIdlePhase.SLEEPING_ZZZ -> {
            // Tidur Nyenyak: Garis lengkung slit tidur (˘ ˘) + Breathing Glow
            Canvas(
                modifier = Modifier.size(width = 12.dp, height = 5.dp)
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
                    color = EveCyanGlow.copy(alpha = 0.30f * breathingAlpha),
                    style = Stroke(width = 4.0.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = path,
                    color = EveCyanGlow.copy(alpha = breathingAlpha),
                    style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        EveIdlePhase.WAKE_OPEN -> {
            // Tahap 1 Saat Bangun: Melek jelas (400ms)
            Canvas(
                modifier = Modifier
                    .size(width = 10.dp, height = 15.dp)
                    .scale(1.1f)
            ) {
                drawOval(
                    color = EveCyanGlow.copy(alpha = 0.35f),
                    size = size.copy(width = size.width * 1.25f, height = size.height * 1.25f),
                    topLeft = Offset(-size.width * 0.12f, -size.height * 0.12f)
                )
                drawOval(color = EveCyanGlow)
            }
        }
        EveIdlePhase.AWAKE_BLINK, EveIdlePhase.PRE_SLEEP_LOOK, EveIdlePhase.TOUCH_BLACKOUT -> {
            // Standby Biasa: Kapsul LED ramping 9x14dp + kedip santai
            Canvas(
                modifier = Modifier
                    .size(width = 9.dp, height = 14.dp)
                    .scale(scaleX = 1f, scaleY = blinkScaleY)
            ) {
                drawOval(
                    color = EveCyanGlow.copy(alpha = 0.30f),
                    size = size.copy(width = size.width * 1.22f, height = size.height * 1.22f),
                    topLeft = Offset(-size.width * 0.11f, -size.height * 0.11f)
                )
                drawOval(color = EveCyanGlow)
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
                drawPath(
                    path = path,
                    color = SoftCyanHalo.copy(alpha = 0.28f),
                    style = Stroke(width = (5.2f * sizeScale).dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = path,
                    color = EveCyanGlow,
                    style = Stroke(width = (2.4f * sizeScale).dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        EveMood.WINK -> {
            if (isLeft) {
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
 * - Expanded:
 *   1. Baris Atas (mengapit kamera fisik): Mata EVE di kiri kamera, Equalizer 4-Bar di kanan kamera.
 *   2. Baris Tengah (di bawah kamera fisik, bebas terpotong): Album Art + Judul Lagu & Penyanyi lebar penuh.
 *   3. Baris Scrubber: Waktu berjalan + Progress Bar glowing + Sisa waktu.
 *   4. Baris Tombol Bawah: Tombol EVE Visor + Tombol Ikon Vector (Prev, Play/Pause, Next) dengan ruang napas bawah 14dp.
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
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f)
            ) {
                CuteEyeRenderer(
                    mood = EveMood.HAPPY,
                    isLeft = false,
                    scaleY = 1f,
                    heartScale = 1f,
                    sizeScale = 0.85f
                )
                Spacer(modifier = Modifier.width(6.dp))
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
        // EXPANDED NUSA-STYLE SLEEK MUSIC VISOR (Height = 148dp, Zero Clipping, Camera-Safe!)
        val progress = if (music.durationMs > 0) {
            (music.positionMs.toFloat() / music.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0.4f
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, bottom = 14.dp, start = 4.dp, end = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Camera Bar (Mengapit lubang kamera fisik secara simetris 100%)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    CuteEyeRenderer(
                        mood = state.eveMood,
                        isLeft = true,
                        scaleY = 1f,
                        heartScale = 1f,
                        sizeScale = 0.95f
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = music.outputDeviceName,
                        color = EveCyanGlow.copy(alpha = 0.85f),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Ruang tepat di lubang kamera fisik
                Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                        modifier = Modifier.height(14.dp)
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

            // 2. Song Info Row (Berada di bawah lubang kamera fisik, lebar penuh & tidak tertutup kamera!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                        Canvas(modifier = Modifier.size(16.dp)) {
                            drawCircle(
                                color = PitchBlack,
                                radius = size.minDimension * 0.45f,
                                style = Stroke(width = 2.2.dp.toPx())
                            )
                            drawCircle(
                                color = PitchBlack,
                                radius = size.minDimension * 0.16f
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = music.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = music.artist,
                        color = EveCyanGlow.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            // 3. Nusa-style Glowing Progress Scrubber
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatSeconds((music.positionMs / 1000L).toInt()),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.5.dp)
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
                    fontFamily = FontFamily.Monospace,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }

            // 4. Bottom Row: Custom Vector Media Controls (Tidak pernah kepotong & tanpa emoji sistem!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillActionButton(
                    text = "EVE Visor",
                    bg = Color.White.copy(alpha = 0.12f),
                    fg = EveCyanGlow
                ) {
                    IslandRepository.setMode(IslandMode.IDLE, expand = true)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MediaIconButton(
                        iconType = MediaIconType.PREV,
                        bg = Color.White.copy(alpha = 0.12f),
                        fg = Color.White
                    ) {
                        IslandRepository.onMediaPrevious?.invoke()
                    }
                    MediaIconButton(
                        iconType = if (music.isPlaying) MediaIconType.PAUSE else MediaIconType.PLAY,
                        bg = EveCyanGlow,
                        fg = PitchBlack
                    ) {
                        IslandRepository.onMediaPlayPause?.invoke()
                    }
                    MediaIconButton(
                        iconType = MediaIconType.NEXT,
                        bg = Color.White.copy(alpha = 0.12f),
                        fg = Color.White
                    ) {
                        IslandRepository.onMediaNext?.invoke()
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaIconButton(
    iconType: MediaIconType,
    bg: Color,
    fg: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(11.dp)) {
            val w = size.width
            val h = size.height
            when (iconType) {
                MediaIconType.PREV -> {
                    drawLine(
                        color = fg,
                        start = Offset(w * 0.18f, h * 0.18f),
                        end = Offset(w * 0.18f, h * 0.82f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    val path = Path().apply {
                        moveTo(w * 0.85f, h * 0.15f)
                        lineTo(w * 0.32f, h * 0.50f)
                        lineTo(w * 0.85f, h * 0.85f)
                        close()
                    }
                    drawPath(path = path, color = fg)
                }
                MediaIconType.NEXT -> {
                    val path = Path().apply {
                        moveTo(w * 0.15f, h * 0.15f)
                        lineTo(w * 0.68f, h * 0.50f)
                        lineTo(w * 0.15f, h * 0.85f)
                        close()
                    }
                    drawPath(path = path, color = fg)
                    drawLine(
                        color = fg,
                        start = Offset(w * 0.82f, h * 0.18f),
                        end = Offset(w * 0.82f, h * 0.82f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                MediaIconType.PLAY -> {
                    val path = Path().apply {
                        moveTo(w * 0.24f, h * 0.12f)
                        lineTo(w * 0.86f, h * 0.50f)
                        lineTo(w * 0.24f, h * 0.88f)
                        close()
                    }
                    drawPath(path = path, color = fg)
                }
                MediaIconType.PAUSE -> {
                    drawLine(
                        color = fg,
                        start = Offset(w * 0.30f, h * 0.16f),
                        end = Offset(w * 0.30f, h * 0.84f),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = fg,
                        start = Offset(w * 0.70f, h * 0.16f),
                        end = Offset(w * 0.70f, h * 0.84f),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
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
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "45W SOLAR",
                    color = WallESolarAmber,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "${bat.level}%",
                    color = DirectivePlantGreen,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    } else {
        // Sleek Expanded Solar Charge Visor (Height = 114dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp, bottom = 14.dp, start = 4.dp, end = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    CuteEyeRenderer(
                        mood = EveMood.HAPPY,
                        isLeft = true,
                        scaleY = 1f,
                        heartScale = 1f,
                        sizeScale = 1.2f
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "45W FAST",
                        color = WallESolarAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "${bat.level}% • ${bat.temperatureCelsius}°C",
                        color = DirectivePlantGreen,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Text(
                text = "All-Round FastCharge Aktif",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )

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
                .padding(horizontal = 4.dp, vertical = 2.dp),
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
                        fontWeight = FontWeight.Black,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
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
                .padding(top = 10.dp, bottom = 14.dp, start = 4.dp, end = 4.dp),
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
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(DirectivePlantGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = notif.appLabel.take(2),
                            color = PitchBlack,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                    Text(
                        text = notif.sender,
                        color = EveCyanGlow,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillActionButton(text = "Tutup", bg = Color.White.copy(alpha = 0.12f), fg = Color.White) {
                        onFocusInput(false)
                        IslandRepository.setMode(IslandMode.IDLE, expand = false)
                    }
                }
            }

            Text(
                text = notif.message,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
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
                            fontSize = 11.sp,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                    BasicTextField(
                        value = replyText,
                        onValueChange = {
                            onFocusInput(true)
                            replyText = it
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 11.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
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
                .padding(horizontal = 4.dp, vertical = 2.dp),
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

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatSeconds(ct.timerRemainingSec),
                    color = WallESolarAmber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp, bottom = 14.dp, start = 4.dp, end = 4.dp),
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (ct.isCallConnected) formatSeconds(ct.callDurationSec) else formatSeconds(ct.timerRemainingSec),
                        color = DirectivePlantGreen,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
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
            .height(26.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

private fun formatSeconds(totalSec: Int): String {
    val m = (totalSec / 60).coerceAtLeast(0)
    val s = (totalSec % 60).coerceAtLeast(0)
    return "%02d:%02d".format(m, s)
}
