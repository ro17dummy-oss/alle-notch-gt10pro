package com.walle.notch.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.walle.notch.core.model.IslandMode
import com.walle.notch.core.model.IslandUiState
import com.walle.notch.core.state.IslandRepository
import com.walle.notch.ui.island.WallEDynamicIslandOverlay
import com.walle.notch.ui.theme.AxiomAlertRed
import com.walle.notch.ui.theme.DirectivePlantGreen
import com.walle.notch.ui.theme.EveCyanGlow
import com.walle.notch.ui.theme.MechaBorder
import com.walle.notch.ui.theme.MechaCardSurface
import com.walle.notch.ui.theme.MechaDarkSurface
import com.walle.notch.ui.theme.PitchBlack
import com.walle.notch.ui.theme.WallENotchTheme
import com.walle.notch.ui.theme.WallESolarAmber
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IslandRepository.loadCalibration(applicationContext)

        setContent {
            WallENotchTheme {
                val state by IslandRepository.uiState.collectAsState()
                WallESettingsScreen(state = state)
            }
        }
    }
}

@Composable
private fun WallESettingsScreen(state: IslandUiState) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val cal = state.calibration

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MechaDarkSurface)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MechaCardSurface)
                .border(1.dp, MechaBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "WALL-E NOTCH • INFINIX GT 10 PRO",
                color = WallESolarAmber,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Dynamic Island & Gaming HUD",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Dirancang khusus untuk punch-hole tengah layar 6.67\" AMOLED 120Hz Infinix GT 10 Pro.",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp
            )
        }

        // In-App Live Interactive Preview Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MechaCardSurface)
                .border(1.dp, MechaBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preview Interaktif Langsung",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.isExpanded) "EXPANDED" else "COMPACT",
                    color = EveCyanGlow,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF080B12))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .clickable { IslandRepository.setExpanded(false) }
                    .padding(top = 10.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                WallEDynamicIslandOverlay(
                    state = state,
                    onRequestKeyboardFocus = {}
                )
            }

            state.lastFeedbackMessage?.let { msg ->
                Text(
                    text = msg,
                    color = DirectivePlantGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Section 1: System Permissions (Accessibility Overlay & Notification Listener)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MechaCardSurface)
                .border(1.dp, MechaBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "1. Aktifkan Layanan Sistem",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            PermissionRowCard(
                title = "Overlay Aksesibilitas (Wajib)",
                subtitle = "Menampilkan Wall-E Notch tepat di atas kamera depan & status bar",
                isActive = state.isOverlayServiceRunning,
                buttonText = "Buka Aksesibilitas"
            ) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            PermissionRowCard(
                title = "Akses Notifikasi & Media (Wajib)",
                subtitle = "Membaca lagu Spotify/YT Music dan membalas pesan WhatsApp",
                isActive = state.isNotificationListenerConnected,
                buttonText = "Buka Izin Notif"
            ) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        // Section 2: Trigger & Test 6 Modes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MechaCardSurface)
                .border(1.dp, MechaBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "2. Uji Coba 6 Mode Aktivitas",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeSelectButton(
                    modifier = Modifier.weight(1f),
                    label = "EVE Idle",
                    selected = state.mode == IslandMode.IDLE,
                    accent = EveCyanGlow
                ) {
                    IslandRepository.setMode(IslandMode.IDLE, expand = false)
                }
                ModeSelectButton(
                    modifier = Modifier.weight(1f),
                    label = "Musik",
                    selected = state.mode == IslandMode.MUSIC,
                    accent = DirectivePlantGreen
                ) {
                    IslandRepository.updateMusic { it.copy(isPlaying = true) }
                    IslandRepository.setMode(IslandMode.MUSIC, expand = false)
                }
                ModeSelectButton(
                    modifier = Modifier.weight(1f),
                    label = "Cas 45W",
                    selected = state.mode == IslandMode.SOLAR_CHARGE,
                    accent = WallESolarAmber
                ) {
                    IslandRepository.setMode(IslandMode.SOLAR_CHARGE, expand = false)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeSelectButton(
                    modifier = Modifier.weight(1f),
                    label = "Notif WA",
                    selected = state.mode == IslandMode.NOTIFICATION,
                    accent = EveCyanGlow
                ) {
                    IslandRepository.setMode(IslandMode.NOTIFICATION, expand = false)
                }
                ModeSelectButton(
                    modifier = Modifier.weight(1f),
                    label = "Telepon",
                    selected = state.mode == IslandMode.CALL_TIMER,
                    accent = DirectivePlantGreen
                ) {
                    IslandRepository.setMode(IslandMode.CALL_TIMER, expand = false)
                }
                ModeSelectButton(
                    modifier = Modifier.weight(1f),
                    label = "Gaming HUD",
                    selected = state.mode == IslandMode.GAMING_HUD,
                    accent = AxiomAlertRed
                ) {
                    IslandRepository.setMode(IslandMode.GAMING_HUD, expand = false)
                }
            }

            Button(
                onClick = { IslandRepository.toggleExpanded() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = WallESolarAmber,
                    contentColor = PitchBlack
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (state.isExpanded) "Kecilkan Kapsul (Collapse)" else "Perbesar Kapsul (Expand)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Section 3: Infinix GT 10 Pro Camera Punch-Hole Calibration
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MechaCardSurface)
                .border(1.dp, MechaBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "3. Kalibrasi Kamera Infinix GT 10 Pro",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Reset Default",
                    color = EveCyanGlow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        IslandRepository.resetCalibration(context)
                    }
                )
            }

            CalibrationSliderItem(
                label = "Posisi Vertikal (Offset Y)",
                valueText = "${cal.offsetYDp} dp",
                value = cal.offsetYDp.toFloat(),
                valueRange = 0f..28f
            ) { newVal ->
                IslandRepository.updateCalibration(context) { it.copy(offsetYDp = newVal.roundToInt()) }
            }

            CalibrationSliderItem(
                label = "Posisi Horizontal (Offset X)",
                valueText = "${cal.offsetXDp} dp",
                value = cal.offsetXDp.toFloat(),
                valueRange = -24f..24f
            ) { newVal ->
                IslandRepository.updateCalibration(context) { it.copy(offsetXDp = newVal.roundToInt()) }
            }

            CalibrationSliderItem(
                label = "Lebar Kapsul Saat Idle",
                valueText = "${cal.idleWidthDp} dp",
                value = cal.idleWidthDp.toFloat(),
                valueRange = 96f..176f
            ) { newVal ->
                IslandRepository.updateCalibration(context) { it.copy(idleWidthDp = newVal.roundToInt()) }
            }

            CalibrationSliderItem(
                label = "Diameter Punch-Hole Kamera",
                valueText = "${cal.cameraHoleSizeDp} dp",
                value = cal.cameraHoleSizeDp.toFloat(),
                valueRange = 12f..28f
            ) { newVal ->
                IslandRepository.updateCalibration(context) { it.copy(cameraHoleSizeDp = newVal.roundToInt()) }
            }

            CalibrationToggleRow(
                label = "Tampilkan Garis Bantu Kamera (X-Ray)",
                checked = cal.xrayCameraGuide
            ) { checked ->
                IslandRepository.updateCalibration(context) { it.copy(xrayCameraGuide = checked) }
            }

            CalibrationToggleRow(
                label = "Aktifkan Split Pill (Timer di Kanan Kapsul)",
                checked = cal.splitPillEnabled
            ) { checked ->
                IslandRepository.updateCalibration(context) { it.copy(splitPillEnabled = checked) }
            }

            CalibrationToggleRow(
                label = "Auto-Hide saat Layar Landscape Fullscreen",
                checked = cal.autoHideLandscape
            ) { checked ->
                IslandRepository.updateCalibration(context) { it.copy(autoHideLandscape = checked) }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionRowCard(
    title: String,
    subtitle: String,
    isActive: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isActive) "AKTIF" else "OFF",
                    color = if (isActive) DirectivePlantGreen else AxiomAlertRed,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp
            )
        }
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) Color.White.copy(alpha = 0.14f) else EveCyanGlow,
                contentColor = if (isActive) Color.White else PitchBlack
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = buttonText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModeSelectButton(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (selected) accent else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CalibrationSliderItem(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = Color.White, fontSize = 12.sp)
            Text(
                text = valueText,
                color = WallESolarAmber,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = WallESolarAmber,
                activeTrackColor = WallESolarAmber,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            )
        )
    }
}

@Composable
private fun CalibrationToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PitchBlack,
                checkedTrackColor = EveCyanGlow
            )
        )
    }
}
