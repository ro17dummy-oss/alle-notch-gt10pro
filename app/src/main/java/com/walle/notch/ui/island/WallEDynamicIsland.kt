package com.walle.notch.ui.island

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
    // Jendela overlay berukuran tetap: notch dirata atas-tengah, area transparan di sekitarnya
    // hanya dipakai untuk "tap di luar notch" -> collapse saat sedang expand.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(state.isExpanded) {
                detectTapGestures {
                    if (state.isExpanded) IslandRepository.setExpanded(false)
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        NotchBody(state = state, onRequestKeyboardFocus = onRequestKeyboardFocus)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotchBody(
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
    // Hilang: geser ke atas + memudar (260ms). Muncul: turun dari atas tanpa membal
    // (pantulan membuat notch sempat turun & lepas dari bezel atas).
    // Ukuran tidak diubah, jadi tidak ada pergeseran ke samping.
    val hideProgress by animateFloatAsState(
        targetValue = if (state.isNotchHidden) 1f else 0f,
        animationSpec = if (state.isNotchHidden) {
            tween(durationMillis = 260, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = 1f, stiffness = 600f)
        },
        label = "notchHide"
    )

    val isHiddenInCamera = cal.autoHideLandscape && state.isLandscapeFullscreen && !state.isExpanded

    // Debounce: status peek (app/drawer) sering berkedip beberapa kali saat kembali ke Home.
    // Masuk ke peek langsung, tapi keluar dari peek ditahan 150ms agar animasi tidak bolak-balik.
    val rawPeek = state.isAppPeek
    var heldPeek by remember { mutableStateOf(rawPeek) }
    LaunchedEffect(rawPeek) {
        if (rawPeek) {
            heldPeek = true
        } else {
            delay(150L)
            heldPeek = false
        }
    }
    val canHoldPeek = state.mode == IslandMode.IDLE && !state.isExpanded && !state.isNotchHidden
    val isAppPeek = rawPeek || (heldPeek && canHoldPeek)

    // Satu "bahasa gerak" untuk semua properti morph (lebar, tinggi, sudut, radius):
    // masuk peek lebih cepat (~65% durasi keluar), keluar peek tanpa membal, expand critically damped (850f) tanpa goyang.
    val morphSpec = when {
        isAppPeek -> spring<Dp>(dampingRatio = 1f, stiffness = 1300f)
        state.isExpanded -> spring<Dp>(dampingRatio = 1f, stiffness = 850f)
        else -> spring<Dp>(dampingRatio = 1f, stiffness = 650f)
    }

    val cornerWidth by animateDpAsState(
        targetValue = if (isAppPeek) 4.dp else cal.cornerWidthDp.dp,
        animationSpec = morphSpec,
        label = "notchCorner"
    )
    val targetBottomRadius = when {
        isAppPeek -> 3.dp
        state.isExpanded -> 28.dp
        else -> (cal.cornerWidthDp * 0.64f).dp
    }

    // Ukuran "normal" (tanpa peek): dipakai untuk layout isi agar isi tidak terjepit saat notch tumbuh
    val normalWidth: Dp = when {
        isHiddenInCamera -> cal.cameraHoleSizeDp.dp
        state.isExpanded -> when (state.mode) {
            IslandMode.IDLE -> 318.dp          // Nusa Multi-Card Interactive Dashboard
            IslandMode.MUSIC -> 318.dp         // Nusa-style sleek music player
            IslandMode.SOLAR_CHARGE -> 296.dp  // Sleek solar charge visor
            IslandMode.NOTIFICATION -> 320.dp  // Clean quick reply card
            IslandMode.CALL_TIMER -> 300.dp
        }
        else -> when (state.mode) {
            IslandMode.IDLE -> cal.idleWidthDp.dp
            IslandMode.MUSIC -> 248.dp
            IslandMode.SOLAR_CHARGE -> 232.dp
            IslandMode.NOTIFICATION -> cal.idleWidthDp.dp // Tetap di ukuran biasa 202dp!
            IslandMode.CALL_TIMER -> 244.dp
        }
    }

    val normalHeight: Dp = when {
        isHiddenInCamera -> cal.cameraHoleSizeDp.dp
        state.isExpanded -> when (state.mode) {
            IslandMode.IDLE -> 122.dp          // 4-Card Dashboard (Musik, Sholat, To-Do, Stopwatch)
            IslandMode.MUSIC -> 148.dp
            IslandMode.SOLAR_CHARGE -> 114.dp
            IslandMode.NOTIFICATION -> 110.dp  // Tinggi pas untuk balas cepat tanpa mubazir
            IslandMode.CALL_TIMER -> 124.dp
        }
        else -> cal.idleHeightDp.dp
    }

    val targetTotalWidth: Dp = if (isAppPeek) 64.dp else normalWidth
    val targetHeight: Dp = if (isAppPeek) 4.dp else normalHeight

    val animatedWidth by animateDpAsState(
        targetValue = targetTotalWidth,
        animationSpec = morphSpec,
        label = "notchWidth"
    )

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = morphSpec,
        label = "notchHeight"
    )

    val animatedBottomRadius by animateDpAsState(
        targetValue = targetBottomRadius,
        animationSpec = morphSpec,
        label = "notchBottomRadius"
    )

    // Padding isi tetap (tidak dianimasikan) karena isi di-layout pada ukuran final
    val contentHorizontalPad = if (state.isExpanded) 24.dp else cal.cornerWidthDp.dp

    // Isi (mata EVE dll) fade-in setelah notch hampir selesai tumbuh; fade-out cepat saat mengecil
    val contentAlpha by animateFloatAsState(
        targetValue = if (isAppPeek) 0f else 1f,
        animationSpec = if (isAppPeek) {
            tween(durationMillis = 80, easing = LinearEasing)
        } else {
            tween(durationMillis = 200, delayMillis = 90, easing = FastOutSlowInEasing)
        },
        label = "notchContentAlpha"
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
            .graphicsLayer {
                translationY = -hideProgress * (size.height + 12.dp.toPx())
                alpha = 1f - 0.7f * hideProgress.coerceIn(0f, 1f)
            }
            .width(animatedWidth)
            .height(animatedHeight)
            .clip(notchShape)
            .background(PitchBlack)
            .pointerInput(state.isExpanded, state.mode, isAppPeek) {
                if (isAppPeek) return@pointerInput
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
                enabled = !isAppPeek && !state.isExpanded,
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
        // Garis kontur kurva bawah halus (Bottom Squircle Rim Highlight) saat di-expand selain notif
        if (state.isExpanded && state.mode != IslandMode.NOTIFICATION) {
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

        if (!isHiddenInCamera && (!isAppPeek || contentAlpha > 0.01f)) {
            // Isi di-layout pada ukuran FINAL lalu di-clip oleh bentuk notch yang tumbuh (rata atas-tengah),
            // jadi mata EVE tidak terjepit / berubah susunan selama transisi.
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopCenter, unbounded = true)
                    .size(normalWidth, normalHeight)
                    .graphicsLayer { alpha = contentAlpha }
                    .padding(horizontal = contentHorizontalPad),
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
@OptIn(ExperimentalFoundationApi::class)
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

    // Transisi crossfade halus antara Dashboard Expanded dan Mata EVE Standby
    val dashboardAlpha by animateFloatAsState(
        targetValue = if (state.isExpanded) 1f else 0f,
        animationSpec = if (state.isExpanded) {
            tween(durationMillis = 200, delayMillis = 110, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 90, easing = LinearEasing)
        },
        label = "dashboardAlpha"
    )

    val compactEyesAlpha by animateFloatAsState(
        targetValue = if (!state.isExpanded && state.eveIdlePhase != EveIdlePhase.TOUCH_BLACKOUT) 1f else 0f,
        animationSpec = if (!state.isExpanded) {
            tween(durationMillis = 220, delayMillis = 140, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 80, easing = LinearEasing)
        },
        label = "compactEyesAlpha"
    )

    if (state.isExpanded || dashboardAlpha > 0.01f) {
        // EXPANDED: NUSA MULTI-CARD INTERACTIVE DASHBOARD dengan HorizontalPager
        val pagerState = rememberPagerState(pageCount = { 4 })
        val coroutineScope = rememberCoroutineScope()
        val activeTab = pagerState.currentPage
        val view = LocalView.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dashboardAlpha }
                .padding(top = 8.dp, bottom = 6.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val headerTitle = when (activeTab) {
                        0 -> "EVE · PEMUTAR MUSIK"
                        1 -> "PRAYER · ${state.prayer.cityName.uppercase()}"
                        2 -> "CATATAN & PENGINGAT"
                        else -> "STOPWATCH DIGITAL"
                    }
                    val headerColor = when (activeTab) {
                        0 -> EveCyanGlow
                        1 -> Color(0xFF38BDF8)
                        2 -> WallESolarAmber
                        else -> DirectivePlantGreen
                    }
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(headerColor)
                    )
                    Text(
                        text = headerTitle,
                        color = headerColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

                PillActionButton(
                    text = "✕",
                    bg = Color.White.copy(alpha = 0.12f),
                    fg = Color.White
                ) {
                    IslandRepository.setExpanded(false)
                }
            }

            // Card Body (HorizontalPager 4 Kartu: Musik, Sholat, To-Do, Stopwatch)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) { page ->
                when (page) {
                    0 -> {
                        // KARTU 0: Pemutar Musik (Sesuai referensi gambar user, tanpa header jam & tanpa balon lirik)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Album Art Squircle di Kiri
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E222A)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.music.albumArt != null) {
                                    Image(
                                        bitmap = state.music.albumArt.asImageBitmap(),
                                        contentDescription = "Cover",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = "🎵",
                                        fontSize = 22.sp
                                    )
                                }
                            }

                            // Bagian Kanan: Judul, Seekbar, Tombol Kontrol
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Judul & Artis
                                Column {
                                    Text(
                                        text = if (state.music.title.isNotBlank()) state.music.title else "Not Playing",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (state.music.artist.isNotBlank()) state.music.artist else "EVE Audio Track",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Seekbar Progress Line: 00:00 --- [Bar] --- -03:45
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = formatDuration(state.music.positionMs),
                                        color = Color(0xFF94A3B8),
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(3.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    ) {
                                        val progressFraction = if (state.music.durationMs > 0) {
                                            (state.music.positionMs.toFloat() / state.music.durationMs).coerceIn(0f, 1f)
                                        } else 0.35f
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(progressFraction)
                                                .background(Color.White)
                                        )
                                    }
                                    val remainingMs = (state.music.durationMs - state.music.positionMs).coerceAtLeast(0L)
                                    Text(
                                        text = "-${formatDuration(remainingMs)}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                // Kontrol Bawah: ⏮   ▶/⏸   ⏭   dan 🔊 di Kanan (Tanpa balon kutipan)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Spacer(modifier = Modifier.width(18.dp)) // Penyeimbang kiri

                                    // Tombol Media Tengah
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text(
                                            text = "⏮",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            modifier = Modifier.clickable { IslandRepository.onMediaPrevious?.invoke() }
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(if (state.music.isPlaying) EveCyanGlow else Color.White.copy(alpha = 0.2f))
                                                .clickable { IslandRepository.onMediaPlayPause?.invoke() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (state.music.isPlaying) "⏸" else "▶",
                                                color = if (state.music.isPlaying) PitchBlack else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "⏭",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            modifier = Modifier.clickable { IslandRepository.onMediaNext?.invoke() }
                                        )
                                    }

                                    // Ikon Speaker Kanan
                                    Text(
                                        text = "🔊",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // KARTU 1: Pengingat Sholat (Sesuai referensi gambar user, tanpa header jam)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Sisi Kiri: Info Sholat Berikutnya
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Next",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = state.prayer.nextPrayerName,
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = state.prayer.nextPrayerTime,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = state.prayer.countdownText,
                                            color = Color(0xFF38BDF8),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    // Progress Bar Kecil
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
                                            .height(2.5.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(state.prayer.celestialProgress)
                                                .background(Color(0xFF38BDF8))
                                        )
                                    }
                                }
                            }

                            // Sisi Kanan: Busur Lengkung Langit (Celestial Arc) + Bola Bercahaya (Glowing Orb)
                            Box(
                                modifier = Modifier
                                    .width(155.dp)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                CelestialPrayerArc(
                                    progress = state.prayer.celestialProgress
                                )
                            }
                        }
                    }
                    2 -> {
                        // KARTU 2: Catatan Kecil (Opsi B: Mini To-Do Checklist yang dapat dicentang)
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            state.todoList.take(3).forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            try {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            } catch (_: Exception) {}
                                            IslandRepository.toggleTodoItem(item.id)
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Checkbox Squircle
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (item.isDone) DirectivePlantGreen else Color.White.copy(alpha = 0.12f)
                                            )
                                            .border(
                                                1.dp,
                                                if (item.isDone) DirectivePlantGreen else Color.White.copy(alpha = 0.25f),
                                                RoundedCornerShape(4.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.isDone) {
                                            Text(
                                                text = "✓",
                                                color = PitchBlack,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }

                                    // Teks Tugas
                                    Text(
                                        text = item.text,
                                        color = if (item.isDone) Color(0xFF64748B) else Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (item.isDone) FontWeight.Normal else FontWeight.Medium,
                                        textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // KARTU 3: Stopwatch Digital
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Digit Display Monospace di Kiri
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                val totalMs = state.stopwatch.elapsedMillis
                                val minutes = (totalMs / 60000)
                                val seconds = (totalMs % 60000) / 1000
                                val millis = (totalMs % 1000) / 100
                                val timeString = "%02d:%02d.%d".format(minutes, seconds, millis)

                                Text(
                                    text = timeString,
                                    color = EveCyanGlow,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = if (state.stopwatch.isRunning) "STOPWATCH BERJALAN" else "SIAP",
                                    color = if (state.stopwatch.isRunning) DirectivePlantGreen else Color(0xFF94A3B8),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Tombol Aksi di Kanan
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Tombol Reset
                                PillActionButton(
                                    text = "↺ Reset",
                                    bg = Color.White.copy(alpha = 0.1f),
                                    fg = Color.White
                                ) {
                                    IslandRepository.resetStopwatch()
                                }

                                // Tombol Start / Pause
                                PillActionButton(
                                    text = if (state.stopwatch.isRunning) "⏸ Jeda" else "▶ Mulai",
                                    bg = if (state.stopwatch.isRunning) WallESolarAmber else EveCyanGlow,
                                    fg = PitchBlack
                                ) {
                                    if (state.stopwatch.isRunning) {
                                        IslandRepository.pauseStopwatch()
                                    } else {
                                        IslandRepository.startStopwatch()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Indikator Titik Halaman Dashboard Bawah (Klik titik langsung berpindah)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0..3) {
                    val isSelected = i == activeTab
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 14.dp else 4.dp,
                        label = "tabDotW"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .height(3.5.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) EveCyanGlow.copy(alpha = 0.9f)
                                else Color.White.copy(alpha = 0.25f)
                            )
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(i)
                                }
                            }
                    )
                }
            }
        }
    } else {
        // COLLAPSED STANDBY (202dp x 36dp)
        if (compactEyesAlpha <= 0.01f) {
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

        // Lirik realistis sebelum tidur: kiri -> tahan -> kanan -> tahan -> tengah -> kedip mengantuk (~2.2s)
        val lookX = remember { Animatable(0f) }
        val drowsy = remember { Animatable(1f) }
        LaunchedEffect(state.eveIdlePhase) {
            if (state.eveIdlePhase == EveIdlePhase.PRE_SLEEP_LOOK) {
                lookX.animateTo(-4f, tween(300, easing = FastOutSlowInEasing))
                delay(430)
                lookX.animateTo(4f, tween(400, easing = FastOutSlowInEasing))
                delay(430)
                lookX.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
                drowsy.animateTo(0.15f, tween(130))
                drowsy.animateTo(0.6f, tween(100))
                drowsy.animateTo(0.15f, tween(150))
            } else {
                lookX.snapTo(0f)
                drowsy.snapTo(1f)
            }
        }

        // zZZ siklus 1.2s, tiap huruf muncul bergantian (Cyan, Amber, Sky Blue)
        val zPhase by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "zPhase"
        )
        fun zAlpha(index: Int): Float {
            val local = ((zPhase - index * 0.28f) % 1f + 1f) % 1f
            return if (local < 0.28f) local / 0.28f else 1f - (local - 0.28f) / 0.72f
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = compactEyesAlpha }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Wing: MATA KIRI EVE
            Row(
                modifier = Modifier
                    .weight(1f)
                    .offset { IntOffset(lookX.value.dp.roundToPx(), 0) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                EveStandbyEyeRenderer(
                    phase = state.eveIdlePhase,
                    blinkScaleY = if (state.eveIdlePhase == EveIdlePhase.PRE_SLEEP_LOOK) drowsy.value else eyeScaleY,
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
                    .offset { IntOffset(lookX.value.dp.roundToPx(), 0) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Spacer(modifier = Modifier.width(6.dp))
                EveStandbyEyeRenderer(
                    phase = state.eveIdlePhase,
                    blinkScaleY = if (state.eveIdlePhase == EveIdlePhase.PRE_SLEEP_LOOK) drowsy.value else eyeScaleY,
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
                            color = EveCyanGlow.copy(alpha = zAlpha(0)),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        Text(
                            text = "Z",
                            color = WallESolarAmber.copy(alpha = zAlpha(1)),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        Text(
                            text = "Z",
                            color = Color(0xFF38BDF8).copy(alpha = zAlpha(2)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

/**
 * Busur Lengkung Langit (Celestial Arc) untuk Pengingat Sholat:
 * Menggambar kurva lengkung parabola dengan 5 titik waktu sholat dan bola matahari/bulan
 * bercahaya (glowing orb) yang bergerak secara dinamis mengikuti waktu saat ini.
 */
@Composable
private fun CelestialPrayerArc(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        val w = size.width
        val h = size.height
        val baselineY = h * 0.70f
        val peakY = h * 0.12f

        val arcPath = Path().apply {
            moveTo(6.dp.toPx(), baselineY)
            quadraticBezierTo(w * 0.5f, peakY, w - 6.dp.toPx(), baselineY)
        }

        // Area bayangan gradasi di bawah busur
        val fillPath = Path().apply {
            moveTo(6.dp.toPx(), baselineY)
            quadraticBezierTo(w * 0.5f, peakY, w - 6.dp.toPx(), baselineY)
            lineTo(w - 6.dp.toPx(), baselineY + 2.dp.toPx())
            lineTo(6.dp.toPx(), baselineY + 2.dp.toPx())
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(Color(0xFF38BDF8).copy(alpha = 0.20f), Color.Transparent)
            )
        )

        // Garis busur lengkung gradasi
        drawPath(
            path = arcPath,
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFFF59E0B))
            ),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // 5 Titik Sholat sepanjang busur (Subuh, Dzuhur, Ashar, Maghrib, Isya)
        val tValues = floatArrayOf(0.0f, 0.28f, 0.54f, 0.78f, 1.0f)
        val startX = 6.dp.toPx()
        val endX = w - 6.dp.toPx()

        for (t in tValues) {
            val u = 1f - t
            val px = u * u * startX + 2f * u * t * (w * 0.5f) + t * t * endX
            val py = u * u * baselineY + 2f * u * t * peakY + t * t * baselineY
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = 2.2.dp.toPx(),
                center = Offset(px, py)
            )
        }

        // Posisi Bola Matahari / Bulan Bercahaya (Glowing Orb)
        val orbT = progress.coerceIn(0.05f, 0.95f)
        val uOrb = 1f - orbT
        val orbX = uOrb * uOrb * startX + 2f * uOrb * orbT * (w * 0.5f) + orbT * orbT * endX
        val orbY = uOrb * uOrb * baselineY + 2f * uOrb * orbT * peakY + orbT * orbT * baselineY

        // Aura cahaya luar
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFBBF24).copy(alpha = 0.55f), Color.Transparent),
                center = Offset(orbX, orbY),
                radius = 12.dp.toPx()
            ),
            radius = 12.dp.toPx(),
            center = Offset(orbX, orbY)
        )
        // Cincin luar
        drawCircle(
            color = Color(0xFFFBBF24).copy(alpha = 0.5f),
            radius = 5.5.dp.toPx(),
            center = Offset(orbX, orbY),
            style = Stroke(width = 1.2.dp.toPx())
        )
        // Inti bola putih terang
        drawCircle(
            color = Color.White,
            radius = 3.5.dp.toPx(),
            center = Offset(orbX, orbY)
        )
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

/**
 * Komponen Logo Ikon Sosial Media / Aplikasi Murni SVG Vector (Sangat Ringan, Tanpa Bitmap/Emoji)
 * Mendukung WhatsApp, Telegram, Instagram, TikTok, Facebook, Messenger, X, Gmail, YouTube, dll.
 */
@Composable
private fun AuthenticAppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 20.dp
) {
    val pkg = packageName.lowercase()
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(sizeDp * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when {
                pkg.contains("whatsapp") -> {
                    drawRect(Color(0xFF25D366))
                    val cx = w * 0.50f
                    val cy = h * 0.48f
                    val r = size.minDimension * 0.34f
                    // Speech bubble outline SVG
                    drawCircle(
                        color = Color.White,
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                    val tailPath = Path().apply {
                        moveTo(cx - r * 0.55f, cy + r * 0.55f)
                        lineTo(cx - r * 0.98f, cy + r * 0.98f)
                        lineTo(cx - r * 0.22f, cy + r * 0.80f)
                        close()
                    }
                    drawPath(tailPath, Color.White)
                    // Handset SVG path inside bubble
                    val phonePath = Path().apply {
                        moveTo(cx - r * 0.32f, cy - r * 0.30f)
                        quadraticBezierTo(cx - r * 0.38f, cy + r * 0.15f, cx + r * 0.28f, cy + r * 0.32f)
                    }
                    drawPath(
                        path = phonePath,
                        color = Color.White,
                        style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                pkg.contains("telegram") -> {
                    drawRect(Color(0xFF0088CC))
                    val p = Path().apply {
                        moveTo(w * 0.20f, h * 0.50f)
                        lineTo(w * 0.78f, h * 0.26f)
                        lineTo(w * 0.64f, h * 0.74f)
                        lineTo(w * 0.46f, h * 0.58f)
                        close()
                    }
                    drawPath(p, Color.White)
                }
                pkg.contains("instagram") -> {
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFFFD521), Color(0xFFF50000), Color(0xFFB900B4), Color(0xFF7000FF))
                        )
                    )
                    val pad = w * 0.22f
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(pad, pad),
                        size = Size(w - 2 * pad, h - 2 * pad),
                        cornerRadius = CornerRadius(w * 0.15f),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.13f,
                        center = Offset(w / 2f, h / 2f),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.035f,
                        center = Offset(w * 0.67f, h * 0.33f)
                    )
                }
                pkg.contains("tiktok") || pkg.contains("musically") || pkg.contains("trill") -> {
                    drawRect(Color(0xFF0F0F0F))
                    val notePath = Path().apply {
                        moveTo(w * 0.56f, h * 0.22f)
                        lineTo(w * 0.56f, h * 0.64f)
                        quadraticBezierTo(w * 0.56f, h * 0.78f, w * 0.40f, h * 0.78f)
                        quadraticBezierTo(w * 0.26f, h * 0.78f, w * 0.26f, h * 0.64f)
                        quadraticBezierTo(w * 0.26f, h * 0.50f, w * 0.42f, h * 0.50f)
                        moveTo(w * 0.56f, h * 0.26f)
                        quadraticBezierTo(w * 0.66f, h * 0.38f, w * 0.76f, h * 0.38f)
                    }
                    drawPath(notePath, Color.White, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
                }
                pkg.contains("facebook") || pkg.contains("katana") -> {
                    drawRect(Color(0xFF1877F2))
                    val fPath = Path().apply {
                        moveTo(w * 0.64f, h * 0.24f)
                        lineTo(w * 0.54f, h * 0.24f)
                        quadraticBezierTo(w * 0.46f, h * 0.24f, w * 0.46f, h * 0.34f)
                        lineTo(w * 0.46f, h * 0.80f)
                        moveTo(w * 0.36f, h * 0.48f)
                        lineTo(w * 0.62f, h * 0.48f)
                    }
                    drawPath(fPath, Color.White, style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round))
                }
                pkg.contains("orca") || pkg.contains("messenger") -> {
                    drawRect(Color(0xFF0084FF))
                    drawCircle(Color.White, radius = w * 0.32f, center = Offset(w * 0.50f, h * 0.48f))
                    val bolt = Path().apply {
                        moveTo(w * 0.34f, h * 0.54f)
                        lineTo(w * 0.47f, h * 0.40f)
                        lineTo(w * 0.54f, h * 0.49f)
                        lineTo(w * 0.66f, h * 0.42f)
                    }
                    drawPath(bolt, Color(0xFF0084FF), style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
                }
                pkg.contains("gmail") -> {
                    drawRect(Color.White)
                    val p = Path().apply {
                        moveTo(w * 0.18f, h * 0.28f)
                        lineTo(w * 0.50f, h * 0.52f)
                        lineTo(w * 0.82f, h * 0.28f)
                    }
                    drawPath(p, Color(0xFFEA4335), style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
                    drawRoundRect(
                        color = Color(0xFFEA4335),
                        topLeft = Offset(w * 0.16f, h * 0.26f),
                        size = Size(w * 0.68f, h * 0.48f),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        style = Stroke(width = 1.3.dp.toPx())
                    )
                }
                pkg.contains("youtube") -> {
                    drawRoundRect(
                        color = Color(0xFFFF0000),
                        cornerRadius = CornerRadius(w * 0.24f)
                    )
                    val p = Path().apply {
                        moveTo(w * 0.38f, h * 0.32f)
                        lineTo(w * 0.68f, h * 0.50f)
                        lineTo(w * 0.38f, h * 0.68f)
                        close()
                    }
                    drawPath(p, Color.White)
                }
                pkg.contains("twitter") || pkg.contains("x.android") -> {
                    drawRect(Color(0xFF14171A))
                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.25f, h * 0.25f),
                        end = Offset(w * 0.75f, h * 0.75f),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.75f, h * 0.25f),
                        end = Offset(w * 0.25f, h * 0.75f),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                else -> {
                    // Default Clean SVG Chat Bubble Icon (Tanpa Emoji)
                    drawRect(Color(0xFF25D366))
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(w * 0.22f, h * 0.24f),
                        size = Size(w * 0.56f, h * 0.44f),
                        cornerRadius = CornerRadius(w * 0.14f)
                    )
                    val tail = Path().apply {
                        moveTo(w * 0.30f, h * 0.65f)
                        lineTo(w * 0.24f, h * 0.80f)
                        lineTo(w * 0.44f, h * 0.68f)
                        close()
                    }
                    drawPath(tail, Color.White)
                }
            }
        }
    }
}

/**
 * Komponen Ikon Lonceng Berdering SVG Monokrom (Tanpa Neon)
 */
@Composable
private fun RingingBellIcon(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bellRing")
    val bellRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1600
                0f at 0
                15f at 160
                -13f at 320
                11f at 480
                -8f at 640
                4f at 800
                0f at 960
                0f at 1600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "bellRotation"
    )

    Box(
        modifier = modifier.size(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(16.dp)
                .rotate(bellRotation)
        ) {
            val w = size.width
            val h = size.height

            // Badan Lonceng SVG
            val bellPath = Path().apply {
                moveTo(w * 0.44f, h * 0.12f)
                quadraticBezierTo(w * 0.50f, h * 0.02f, w * 0.56f, h * 0.12f)
                quadraticBezierTo(w * 0.78f, h * 0.22f, w * 0.78f, h * 0.60f)
                quadraticBezierTo(w * 0.82f, h * 0.78f, w * 0.92f, h * 0.82f)
                lineTo(w * 0.08f, h * 0.82f)
                quadraticBezierTo(w * 0.18f, h * 0.78f, w * 0.18f, h * 0.60f)
                quadraticBezierTo(w * 0.22f, h * 0.22f, w * 0.44f, h * 0.12f)
                close()
            }
            drawPath(
                path = bellPath,
                color = Color.White,
                style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Bandul Lonceng Bawah
            val clapperPath = Path().apply {
                moveTo(w * 0.41f, h * 0.86f)
                quadraticBezierTo(w * 0.50f, h * 0.98f, w * 0.59f, h * 0.86f)
            }
            drawPath(
                path = clapperPath,
                color = Color.White,
                style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
            )
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
    val notifPkg = notif.packageName.lowercase()
    val appAccentColor = remember(notifPkg) {
        when {
            notifPkg.contains("whatsapp") -> Color(0xFF25D366)
            notifPkg.contains("telegram") -> Color(0xFF0088CC)
            notifPkg.contains("instagram") -> Color(0xFFE1306C)
            notifPkg.contains("facebook") || notifPkg.contains("katana") -> Color(0xFF1877F2)
            notifPkg.contains("gmail") -> Color(0xFFEA4335)
            notifPkg.contains("youtube") -> Color(0xFFFF0000)
            notifPkg.contains("twitter") || notifPkg.contains("x.android") -> Color(0xFFF8FAFC)
            else -> Color(0xFF25D366)
        }
    }
    var replyText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    if (!state.isExpanded) {
        // Mode Notif Biasa (Compact 202x36dp): Posisi Logo SVG (Kiri) & Animasi Lonceng SVG (Kanan)
        // persis sejajar dengan posisi Mata Kiri & Mata Kanan EVE
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Wing: Posisi Mata Kiri EVE -> Logo SVG Sosial Media / Aplikasi
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                AuthenticAppIcon(
                    packageName = notif.packageName,
                    sizeDp = 18.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Punch hole camera clear area in center (persis sama dengan EVE: cameraHoleSizeDp + 6.dp)
            Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

            // Right Wing: Posisi Mata Kanan EVE -> Animasi Lonceng Berdering SVG
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Spacer(modifier = Modifier.width(6.dp))
                RingingBellIcon()
            }
        }
    } else {
        // Mode Expand Balas Chat (320x110dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, bottom = 10.dp, start = 6.dp, end = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    AuthenticAppIcon(
                        packageName = notif.packageName,
                        sizeDp = 22.dp
                    )
                    Column {
                        Text(
                            text = notif.sender,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${notif.appLabel} · Baru saja",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.5.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width((state.calibration.cameraHoleSizeDp + 6).dp))

                Row(
                    modifier = Modifier.weight(0.5f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillActionButton(
                        text = "✕",
                        bg = Color.White.copy(alpha = 0.12f),
                        fg = Color.White
                    ) {
                        onFocusInput(false)
                        IslandRepository.dismissNotification()
                    }
                }
            }

            // Message text preview box (tap untuk buka chat langsung di WhatsApp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable {
                        notif.contentIntent?.let { pi ->
                            try {
                                pi.send()
                                IslandRepository.dismissNotification()
                            } catch (_: Exception) {}
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = notif.message,
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick reply bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                        .clickable {
                            onFocusInput(true)
                            focusRequester.requestFocus()
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (replyText.isEmpty()) {
                        Text(
                            text = "Balas cepat...",
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
                        cursorBrush = SolidColor(appAccentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }

                PillActionButton(
                    text = "Kirim",
                    bg = appAccentColor,
                    fg = PitchBlack
                ) {
                    if (replyText.isNotBlank()) {
                        onFocusInput(false)
                        NotchNotificationListenerService.sendQuickReply(context, notif, replyText)
                        replyText = ""
                    }
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
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
