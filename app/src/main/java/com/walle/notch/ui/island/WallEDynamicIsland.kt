package com.walle.notch.ui.island

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

private val CuteBlushPink = Color(0xFFFF7597)

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

    // Perilaku sembunyi otomatis sesuai permintaan pengguna:
    // 1. Hilang sepenuhnya jika layar horizontal
    // 2. Hilang ketika membuka Aplikasi (hanya tampil di Home Screen)
    // 3. Hilang ketika gulir menu atas/bawah (status bar / notification shade)
    val isCompletelyHidden = (cal.hideWhenHorizontal && state.isLandscapeHorizontal) ||
        (cal.hideWhenAppOpen && state.isAppOpen && !state.isExpanded) ||
        (cal.hideWhenMenuScrolled && state.isNotificationShadeOpen && !state.isExpanded)

    if (isCompletelyHidden) {
        Box(modifier = Modifier.size(0.dp))
        return
    }

    val isHiddenInCamera = cal.autoHideLandscape && state.isLandscapeFullscreen && !state.isExpanded

    val cornerWidth = if (state.isExpanded) 26.dp else cal.cornerWidthDp.dp

    val targetTotalWidth: Dp = when {
        isHiddenInCamera -> cal.cameraHoleSizeDp.dp
        state.isExpanded -> 328.dp
        else -> when (state.mode) {
            IslandMode.IDLE -> cal.idleWidthDp.dp
            IslandMode.MUSIC -> 236.dp
            IslandMode.SOLAR_CHARGE -> 224.dp
            IslandMode.NOTIFICATION -> 244.dp
            IslandMode.CALL_TIMER -> 240.dp
        }
    }

    val targetHeight: Dp = when {
        isHiddenInCamera -> cal.cameraHoleSizeDp.dp
        state.isExpanded -> when (state.mode) {
            IslandMode.IDLE -> 152.dp
            IslandMode.MUSIC -> 168.dp
            IslandMode.SOLAR_CHARGE -> 160.dp
            IslandMode.NOTIFICATION -> 176.dp
            IslandMode.CALL_TIMER -> 154.dp
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
                elevation = if (state.isExpanded) 16.dp else 6.dp,
                shape = notchShape,
                ambientColor = PitchBlack,
                spotColor = PitchBlack
            )
            .clip(notchShape)
            .background(PitchBlack)
            .combinedClickable(
                onClick = {
                    if (state.mode == IslandMode.IDLE && !state.isExpanded) {
                        try {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        } catch (_: Exception) {}
                        IslandRepository.cycleEveMood(context)
                    } else if (!state.isExpanded) {
                        IslandRepository.setExpanded(true)
                    } else {
                        IslandRepository.setExpanded(false)
                    }
                },
                onLongClick = {
                    try {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    } catch (_: Exception) {}
                    IslandRepository.toggleExpanded()
                }
            )
            .padding(horizontal = cornerWidth),
        contentAlignment = Alignment.TopCenter
    ) {
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

@Composable
private fun IdleModuleContent(state: IslandUiState) {
    val context = LocalContext.current
    val infinite = rememberInfiniteTransition(label = "cuteAnimations")

    val eyeScaleY by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 180, delayMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eyeScaleY"
    )

    val heartScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartPulse"
    )

    val blushAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blushPulse"
    )

    if (!state.isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Side: Soft Blush + Left Eye
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
                    heartScale = heartScale
                )
            }

            // Punch hole camera clear area in center (calibrated to match cameraHoleSizeDp)
            Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

            // Right Side: Right Eye + Soft Blush / Accessory
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CuteEyeRenderer(
                    mood = state.eveMood,
                    isLeft = false,
                    scaleY = eyeScaleY,
                    heartScale = heartScale
                )

                if (state.eveMood == EveMood.PLANT) {
                    Text(
                        text = "🌱",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                } else if (state.eveMood == EveMood.SLEEPY) {
                    Text(
                        text = "zZ",
                        color = EveCyanGlow.copy(alpha = 0.75f),
                        fontSize = 10.sp,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 18.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
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
                    Text(text = "🌱", fontSize = 14.sp)
                    Column {
                        Text(
                            text = "EVE COMPANION MASCOT",
                            color = EveCyanGlow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Ketuk ekspresi untuk mengganti wajah EVE",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                    }
                }
                PillActionButton(text = "Tutup", bg = Color.White.copy(alpha = 0.14f), fg = Color.White) {
                    IslandRepository.setExpanded(false)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EveMoodTile(
                    modifier = Modifier.weight(1f),
                    label = "^_^",
                    title = "Senang",
                    selected = state.eveMood == EveMood.HAPPY,
                    accent = EveCyanGlow
                ) {
                    IslandRepository.setEveMood(context, EveMood.HAPPY)
                }
                EveMoodTile(
                    modifier = Modifier.weight(1f),
                    label = ";-)",
                    title = "Kedip",
                    selected = state.eveMood == EveMood.WINK,
                    accent = WallESolarAmber
                ) {
                    IslandRepository.setEveMood(context, EveMood.WINK)
                }
                EveMoodTile(
                    modifier = Modifier.weight(1f),
                    label = "<3",
                    title = "Cinta",
                    selected = state.eveMood == EveMood.LOVE,
                    accent = CuteBlushPink
                ) {
                    IslandRepository.setEveMood(context, EveMood.LOVE)
                }
                EveMoodTile(
                    modifier = Modifier.weight(1f),
                    label = "🌱",
                    title = "Tanaman",
                    selected = state.eveMood == EveMood.PLANT,
                    accent = DirectivePlantGreen
                ) {
                    IslandRepository.setEveMood(context, EveMood.PLANT)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Baterai Surya: ${state.battery.level}%",
                    color = WallESolarAmber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                PillActionButton(
                    text = "Buka Musik",
                    bg = DirectivePlantGreen.copy(alpha = 0.2f),
                    fg = DirectivePlantGreen
                ) {
                    IslandRepository.setMode(IslandMode.MUSIC, expand = true)
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
    heartScale: Float
) {
    when (mood) {
        EveMood.HAPPY -> {
            Canvas(modifier = Modifier.size(width = 12.dp, height = 7.dp)) {
                val path = Path().apply {
                    moveTo(1f, size.height * 0.9f)
                    quadraticBezierTo(
                        size.width / 2f, 0f,
                        size.width - 1f, size.height * 0.9f
                    )
                }
                drawPath(
                    path = path,
                    color = EveCyanGlow,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        EveMood.WINK -> {
            if (isLeft) {
                Box(
                    modifier = Modifier
                        .size(width = 9.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(EveCyanGlow)
                )
            } else {
                Canvas(modifier = Modifier.size(width = 12.dp, height = 7.dp)) {
                    val path = Path().apply {
                        moveTo(1f, size.height * 0.9f)
                        quadraticBezierTo(
                            size.width / 2f, 0f,
                            size.width - 1f, size.height * 0.9f
                        )
                    }
                    drawPath(
                        path = path,
                        color = EveCyanGlow,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
        EveMood.LOVE -> {
            Canvas(
                modifier = Modifier
                    .size(width = 11.dp, height = 11.dp)
                    .scale(heartScale)
            ) {
                val w = size.width
                val h = size.height
                val heartPath = Path().apply {
                    moveTo(w / 2f, h)
                    cubicTo(0f, h * 0.65f, 0f, 0f, w / 4f, 0f)
                    cubicTo(w / 2f, 0f, w / 2f, h * 0.35f, w / 2f, h * 0.35f)
                    cubicTo(w / 2f, h * 0.35f, w / 2f, 0f, (w * 3f) / 4f, 0f)
                    cubicTo(w, 0f, w, h * 0.65f, w / 2f, h)
                    close()
                }
                drawPath(path = heartPath, color = EveCyanGlow)
            }
        }
        EveMood.SLEEPY -> {
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 2.dp)
                    .clip(CircleShape)
                    .background(EveCyanGlow)
            )
        }
        EveMood.PLANT, EveMood.NORMAL -> {
            Box(
                modifier = Modifier
                    .size(width = 9.dp, height = 6.dp)
                    .scale(scaleX = 1f, scaleY = scaleY)
                    .clip(CircleShape)
                    .background(EveCyanGlow)
            )
        }
    }
}

@Composable
private fun EveMoodTile(
    modifier: Modifier = Modifier,
    label: String,
    title: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MusicModuleContent(state: IslandUiState) {
    val music = state.music
    val infinite = rememberInfiniteTransition(label = "eqWave")
    val bar1 by infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "b3"
    )

    if (!state.isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(WallESolarAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "W",
                        color = PitchBlack,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = music.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(90.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatSeconds(state.callTimer.timerRemainingSec),
                    color = WallESolarAmber,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                    modifier = Modifier.height(14.dp)
                ) {
                    val m1 = if (music.isPlaying) bar1 else 0.3f
                    val m2 = if (music.isPlaying) bar2 else 0.3f
                    val m3 = if (music.isPlaying) bar3 else 0.3f
                    Box(Modifier.width(2.5.dp).fillMaxHeight(m1).clip(CircleShape).background(EveCyanGlow))
                    Box(Modifier.width(2.5.dp).fillMaxHeight(m2).clip(CircleShape).background(WallESolarAmber))
                    Box(Modifier.width(2.5.dp).fillMaxHeight(m3).clip(CircleShape).background(DirectivePlantGreen))
                }
            }
        }
    } else {
        val progress = if (music.durationMs > 0) {
            (music.positionMs.toFloat() / music.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0.4f
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 18.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(WallESolarAmber),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "W·E",
                            color = PitchBlack,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = music.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = music.artist,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                PillActionButton(text = "Tutup", bg = Color.White.copy(alpha = 0.12f), fg = Color.White) {
                    IslandRepository.setExpanded(false)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(WallESolarAmber, EveCyanGlow)
                                )
                            )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatSeconds((music.positionMs / 1000L).toInt()),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = formatSeconds((music.durationMs / 1000L).toInt()),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = music.outputDeviceName,
                    color = EveCyanGlow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillActionButton(text = "Prev", bg = Color.White.copy(alpha = 0.14f), fg = Color.White) {
                        IslandRepository.onMediaPrevious?.invoke()
                    }
                    PillActionButton(
                        text = if (music.isPlaying) "Pause" else "Play",
                        bg = WallESolarAmber,
                        fg = PitchBlack
                    ) {
                        IslandRepository.onMediaPlayPause?.invoke()
                    }
                    PillActionButton(text = "Next", bg = Color.White.copy(alpha = 0.14f), fg = Color.White) {
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
    if (!state.isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SOLAR CAS",
                color = WallESolarAmber,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${bat.level}% • 45W",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        val litBars = ((bat.level / 100f) * 8).toInt().coerceIn(1, 8)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 18.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WALL-E SOLAR CHARGE",
                        color = WallESolarAmber,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "45W All-Round FastCharge",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${bat.level}%",
                    color = WallESolarAmber,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (i in 1..8) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i <= litBars) WallESolarAmber else Color.White.copy(alpha = 0.15f)
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Suhu: ${bat.temperatureCelsius}°C",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                PillActionButton(text = "Tutup", bg = Color.White.copy(alpha = 0.14f), fg = Color.White) {
                    IslandRepository.setExpanded(false)
                }
            }
        }
    }
}

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
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(DirectivePlantGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = notif.appLabel,
                        color = PitchBlack,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = notif.sender,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                text = notif.message,
                color = EveCyanGlow,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(96.dp)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 18.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${notif.sender} • ${notif.appLabel}",
                        color = EveCyanGlow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = notif.message,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PillActionButton(text = "Tutup", bg = Color.White.copy(alpha = 0.14f), fg = Color.White) {
                    onFocusInput(false)
                    IslandRepository.setExpanded(false)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onFocusInput(true) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (replyText.isEmpty()) {
                        Text(
                            text = "Ketik balasan cepat...",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp
                        )
                    }
                    BasicTextField(
                        value = replyText,
                        onValueChange = {
                            onFocusInput(true)
                            replyText = it
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        cursorBrush = SolidColor(EveCyanGlow),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PillActionButton(text = "Kirim", bg = EveCyanGlow, fg = PitchBlack) {
                    val finalMsg = replyText.ifBlank { "Siap, diterima!" }
                    NotchNotificationListenerService.sendQuickReply(context, notif, finalMsg)
                    replyText = ""
                    onFocusInput(false)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillActionButton(text = "\"Sudah aman!\"", bg = Color.White.copy(alpha = 0.12f), fg = Color.White) {
                    NotchNotificationListenerService.sendQuickReply(context, notif, "Sudah aman!")
                    onFocusInput(false)
                }
                PillActionButton(text = "\"Otw Axiom\"", bg = Color.White.copy(alpha = 0.12f), fg = Color.White) {
                    NotchNotificationListenerService.sendQuickReply(context, notif, "Otw Axiom")
                    onFocusInput(false)
                }
                Spacer(modifier = Modifier.weight(1f))
                PillActionButton(text = "Dibaca", bg = Color.White.copy(alpha = 0.10f), fg = EveCyanGlow) {
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
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = ct.callerName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillActionButton(text = "Terima", bg = DirectivePlantGreen, fg = PitchBlack) {
                    ct.acceptIntent?.let {
                        try { it.send() } catch (_: Exception) {}
                    }
                    IslandRepository.updateCallTimer {
                        it.copy(isCallConnected = true, isIncomingCall = false, callDurationSec = 1)
                    }
                    IslandRepository.setExpanded(true)
                }
                PillActionButton(text = "Tolak", bg = AxiomAlertRed, fg = Color.White) {
                    ct.declineIntent?.let {
                        try { it.send() } catch (_: Exception) {}
                    }
                    IslandRepository.setMode(IslandMode.IDLE, expand = false)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 18.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (ct.isCallConnected) "TERSAMBUNG • ${formatSeconds(ct.callDurationSec)}" else "PANGGILAN AXIOM",
                        color = DirectivePlantGreen,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = ct.callerName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Timer ${formatSeconds(ct.timerRemainingSec)}",
                    color = WallESolarAmber,
                    fontSize = 12.sp,
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
                        text = "Terima Panggilan",
                        bg = DirectivePlantGreen,
                        fg = PitchBlack
                    ) {
                        IslandRepository.updateCallTimer {
                            it.copy(isCallConnected = true, callDurationSec = 1)
                        }
                    }
                }
                PillActionButton(
                    text = if (ct.isCallConnected) "Akhiri Panggilan" else "Tolak / Tutup",
                    bg = AxiomAlertRed,
                    fg = Color.White
                ) {
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
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.5.dp),
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
