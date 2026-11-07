package com.walle.notch.ui.island

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WallEDynamicIslandOverlay(
    state: IslandUiState,
    onRequestKeyboardFocus: (Boolean) -> Unit
) {
    val cal = state.calibration

    // Release keyboard focus whenever island collapses or switches away from notification
    LaunchedEffect(state.isExpanded, state.mode) {
        if (!state.isExpanded || state.mode != IslandMode.NOTIFICATION) {
            onRequestKeyboardFocus(false)
        }
    }

    // Auto-hide into punch-hole when landscape fullscreen is active
    val isHiddenInCamera = cal.autoHideLandscape && state.isLandscapeFullscreen && !state.isExpanded

    val targetWidth: Dp = when {
        isHiddenInCamera -> cal.cameraHoleSizeDp.dp
        state.isExpanded -> 352.dp
        else -> when (state.mode) {
            IslandMode.IDLE -> cal.idleWidthDp.dp
            IslandMode.MUSIC -> 198.dp
            IslandMode.SOLAR_CHARGE -> 238.dp
            IslandMode.NOTIFICATION -> 252.dp
            IslandMode.CALL_TIMER -> 248.dp
            IslandMode.GAMING_HUD -> 256.dp
        }
    }

    val targetHeight: Dp = when {
        isHiddenInCamera -> cal.cameraHoleSizeDp.dp
        state.isExpanded -> when (state.mode) {
            IslandMode.IDLE -> 152.dp
            IslandMode.MUSIC -> 176.dp
            IslandMode.SOLAR_CHARGE -> 164.dp
            IslandMode.NOTIFICATION -> 182.dp
            IslandMode.CALL_TIMER -> 156.dp
            IslandMode.GAMING_HUD -> 168.dp
        }
        else -> when (state.mode) {
            IslandMode.IDLE -> cal.idleHeightDp.dp
            IslandMode.MUSIC -> 36.dp
            IslandMode.SOLAR_CHARGE -> 38.dp
            IslandMode.NOTIFICATION -> 40.dp
            IslandMode.CALL_TIMER -> 40.dp
            IslandMode.GAMING_HUD -> 36.dp
        }
    }

    val targetCornerRadius: Dp = if (state.isExpanded) 32.dp else 50.dp

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandWidth"
    )

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandHeight"
    )

    val animatedCorner by animateDpAsState(
        targetValue = targetCornerRadius,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "islandCorner"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isHiddenInCamera -> Color.Transparent
            state.mode == IslandMode.IDLE -> EveCyanGlow.copy(alpha = 0.42f)
            state.mode == IslandMode.MUSIC -> DirectivePlantGreen.copy(alpha = 0.50f)
            state.mode == IslandMode.SOLAR_CHARGE -> WallESolarAmber.copy(alpha = 0.70f)
            state.mode == IslandMode.NOTIFICATION -> EveCyanGlow.copy(alpha = 0.65f)
            state.mode == IslandMode.CALL_TIMER -> DirectivePlantGreen.copy(alpha = 0.65f)
            state.mode == IslandMode.GAMING_HUD -> AxiomAlertRed.copy(alpha = 0.65f)
            else -> WallESolarAmber.copy(alpha = 0.35f)
        },
        animationSpec = tween(250),
        label = "borderColor"
    )

    val showSplitBubble = cal.splitPillEnabled &&
        !state.isExpanded &&
        !isHiddenInCamera &&
        state.callTimer.timerRunning &&
        (state.mode == IslandMode.IDLE || state.mode == IslandMode.MUSIC)

    val splitWidth by animateDpAsState(
        targetValue = if (showSplitBubble) 56.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "splitWidth"
    )

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Center
    ) {
        // Main Capsule
        Box(
            modifier = Modifier
                .width(animatedWidth)
                .height(animatedHeight)
                .clip(RoundedCornerShape(animatedCorner))
                .background(PitchBlack)
                .border(1.dp, borderColor, RoundedCornerShape(animatedCorner))
                .combinedClickable(
                    onClick = {
                        // Single Tap = Expand (or collapse if already in Idle Expanded)
                        if (!state.isExpanded) {
                            IslandRepository.setExpanded(true)
                        } else if (state.mode == IslandMode.IDLE) {
                            IslandRepository.setExpanded(false)
                        }
                    },
                    onLongClick = {
                        // Long Press = Collapse or trigger source notification intent
                        state.notification?.contentIntent?.let { pending ->
                            try {
                                pending.send()
                            } catch (_: Exception) {
                            }
                        }
                        IslandRepository.setExpanded(!state.isExpanded)
                    }
                ),
            contentAlignment = Alignment.Center
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
                    IslandMode.GAMING_HUD -> GamingHudModuleContent(state)
                }
            }

            // Optional X-Ray Physical Punch-Hole Guide for Infinix GT 10 Pro calibration
            if (cal.xrayCameraGuide) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .size(cal.cameraHoleSizeDp.dp)
                        .clip(CircleShape)
                        .background(AxiomAlertRed.copy(alpha = 0.35f))
                        .border(1.5.dp, AxiomAlertRed, CircleShape)
                )
            }
        }

        // Split Pill (Secondary Right Bubble for Concurrent Timer)
        if (splitWidth > 4.dp) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .width(splitWidth)
                    .height(animatedHeight.coerceAtMost(38.dp))
                    .clip(RoundedCornerShape(50.dp))
                    .background(PitchBlack)
                    .border(1.dp, WallESolarAmber.copy(alpha = 0.55f), RoundedCornerShape(50.dp))
                    .clickable {
                        IslandRepository.setMode(IslandMode.CALL_TIMER, expand = true)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatSeconds(state.callTimer.timerRemainingSec),
                    color = WallESolarAmber,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun IdleModuleContent(state: IslandUiState) {
    val infinite = rememberInfiniteTransition(label = "eveBlink")
    val eyeScaleY by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 180, delayMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eyeScaleY"
    )

    if (!state.isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left of Punch-Hole: EVE Cyan Blinking Eyes
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(width = 9.dp, height = 6.dp)
                        .scale(scaleX = 1f, scaleY = eyeScaleY)
                        .clip(CircleShape)
                        .background(EveCyanGlow)
                )
                Box(
                    modifier = Modifier
                        .size(width = 9.dp, height = 6.dp)
                        .scale(scaleX = 1f, scaleY = eyeScaleY)
                        .clip(CircleShape)
                        .background(EveCyanGlow)
                )
            }

            // Right of Punch-Hole: Mini Wall-E Solar Bars
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val activeBars = (state.battery.level / 25).coerceIn(1, 4)
                for (i in 1..4) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height((6 + i * 2).dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (i <= activeBars) WallESolarAmber else Color.White.copy(alpha = 0.25f))
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WALL-E × EVE OS",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Infinix GT 10 Pro • ${state.gaming.refreshRateHz}Hz Aktif",
                        color = EveCyanGlow,
                        fontSize = 12.sp
                    )
                }
                PillActionButton(text = "Tutup", bg = Color.White.copy(alpha = 0.14f), fg = Color.White) {
                    IslandRepository.setExpanded(false)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickModeTile(
                    modifier = Modifier.weight(1f),
                    title = "Musik",
                    subtitle = "Audio",
                    accent = DirectivePlantGreen
                ) {
                    IslandRepository.setMode(IslandMode.MUSIC, expand = true)
                }
                QuickModeTile(
                    modifier = Modifier.weight(1f),
                    title = "Surya ${state.battery.level}%",
                    subtitle = "${state.battery.temperatureCelsius}°C",
                    accent = WallESolarAmber
                ) {
                    IslandRepository.setMode(IslandMode.SOLAR_CHARGE, expand = true)
                }
                QuickModeTile(
                    modifier = Modifier.weight(1f),
                    title = "GT HUD",
                    subtitle = "${state.gaming.refreshRateHz} FPS",
                    accent = AxiomAlertRed
                ) {
                    IslandRepository.setMode(IslandMode.GAMING_HUD, expand = true)
                }
            }
        }
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
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(WallESolarAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "W",
                        color = PitchBlack,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = music.title.substringBefore(" "),
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.height(16.dp)
            ) {
                val m1 = if (music.isPlaying) bar1 else 0.3f
                val m2 = if (music.isPlaying) bar2 else 0.3f
                val m3 = if (music.isPlaying) bar3 else 0.3f
                Box(Modifier.width(3.dp).fillMaxHeight(m1).clip(CircleShape).background(EveCyanGlow))
                Box(Modifier.width(3.dp).fillMaxHeight(m2).clip(CircleShape).background(WallESolarAmber))
                Box(Modifier.width(3.dp).fillMaxHeight(m3).clip(CircleShape).background(DirectivePlantGreen))
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
                .padding(16.dp),
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
                            .size(40.dp)
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

            // Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
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
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = formatSeconds((music.durationMs / 1000L).toInt()),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Transport Controls
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
                .padding(horizontal = 14.dp),
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
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WALL-E SOLAR CHARGE METER",
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

            // 8-Bar Wall-E Solar Level Array
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (i in 1..8) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp)
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
                    text = "Suhu: ${bat.temperatureCelsius}°C • ${bat.voltageMv}mV",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillActionButton(
                        text = if (bat.bypassChargingEnabled) "Bypass: ON" else "Bypass: OFF",
                        bg = if (bat.bypassChargingEnabled) DirectivePlantGreen else Color.White.copy(alpha = 0.15f),
                        fg = if (bat.bypassChargingEnabled) PitchBlack else Color.White
                    ) {
                        IslandRepository.updateBattery(
                            bat.copy(bypassChargingEnabled = !bat.bypassChargingEnabled)
                        )
                    }
                    PillActionButton(text = "Tutup", bg = Color.White.copy(alpha = 0.14f), fg = Color.White) {
                        IslandRepository.setExpanded(false)
                    }
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
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(DirectivePlantGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = notif.appLabel,
                        color = PitchBlack,
                        fontSize = 12.sp,
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
                modifier = Modifier.width(92.dp)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
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

            // Quick Reply Input Row
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

            // Quick Preset Chips
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
                .padding(horizontal = 12.dp),
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
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (ct.isCallConnected) {
                            "TERSAMBUNG • ${formatSeconds(ct.callDurationSec)}"
                        } else {
                            "PANGGILAN & TIMER AXIOM"
                        },
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
private fun GamingHudModuleContent(state: IslandUiState) {
    val g = state.gaming
    if (!state.isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${g.refreshRateHz} FPS | ${g.cpuTempCelsius}°C",
                color = DirectivePlantGreen,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "RAM ${g.ramUsedPercent}%",
                color = EveCyanGlow,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GT 10 PRO MECHA ENGINE",
                        color = AxiomAlertRed,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Dimensity 8050 • Gaming HUD",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                PillActionButton(text = "Tutup", bg = Color.White.copy(alpha = 0.14f), fg = Color.White) {
                    IslandRepository.setExpanded(false)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Layar: ${g.refreshRateHz}Hz",
                    color = DirectivePlantGreen,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Suhu: ${g.cpuTempCelsius}°C",
                    color = WallESolarAmber,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "RAM: ${g.ramUsedGb}/${g.ramTotalGb}GB",
                    color = EveCyanGlow,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillActionButton(
                    text = if (g.lock120Hz) "120Hz Lock: ON" else "120Hz Lock: OFF",
                    bg = if (g.lock120Hz) EveCyanGlow else Color.White.copy(alpha = 0.14f),
                    fg = if (g.lock120Hz) PitchBlack else Color.White
                ) {
                    IslandRepository.updateGaming { it.copy(lock120Hz = !it.lock120Hz) }
                }
                PillActionButton(
                    text = if (g.blockPopups) "Blokir Notif: ON" else "Blokir Notif: OFF",
                    bg = if (g.blockPopups) WallESolarAmber else Color.White.copy(alpha = 0.14f),
                    fg = if (g.blockPopups) PitchBlack else Color.White
                ) {
                    IslandRepository.updateGaming { it.copy(blockPopups = !it.blockPopups) }
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
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuickModeTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Text(
            text = title,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

private fun formatSeconds(totalSec: Int): String {
    val m = (totalSec / 60).coerceAtLeast(0)
    val s = (totalSec % 60).coerceAtLeast(0)
    return "%02d:%02d".format(m, s)
}
