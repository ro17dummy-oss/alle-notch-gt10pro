package com.walle.notch.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import com.walle.notch.core.model.EveMood
import com.walle.notch.core.model.IslandMode
import com.walle.notch.core.model.IslandUiState
import com.walle.notch.core.state.IslandRepository
import com.walle.notch.service.NotchWindowOverlayService
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

    val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }

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
                text = "Liquid Symmetric Notch",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Terkalibrasi presisi dengan punch-hole kamera Infinix GT 10 Pro: Lebar 220dp, Kurva 30dp, Kamera 23dp.",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp
            )
        }

        // Live Preview Box
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
                    text = "Preview Kurva Presisi & EVE",
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
                    .height(176.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF131B2B))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .clickable { IslandRepository.setExpanded(false) },
                contentAlignment = Alignment.TopCenter
            ) {
                WallEDynamicIslandOverlay(
                    state = state.copy(isAppOpen = false, isLandscapeHorizontal = false, isNotificationShadeOpen = false),
                    onRequestKeyboardFocus = {}
                )
            }

            Text(
                text = "Tip: Ketuk Notch untuk Expand (Giant EVE Visor) • Ketuk wajah EVE untuk ganti mood • Usap ke atas / ketuk luar untuk memperkecil!",
                color = EveCyanGlow,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Section 1: Activation & Permissions
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
                text = "1. Aktivasi Notch (Pilih Salah Satu)",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            PermissionRowCard(
                title = "Opsi A: Izin Tampilkan di Atas Aplikasi Lain",
                subtitle = "Paling mudah, tidak akan diblokir oleh 'Pengaturan Terbatas' Android 13/14",
                isActive = hasOverlayPermission && state.isOverlayServiceRunning,
                buttonText = if (!hasOverlayPermission) "Beri Izin" else if (state.isOverlayServiceRunning) "Stop" else "Nyalakan"
            ) {
                if (!hasOverlayPermission) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                } else {
                    if (state.isOverlayServiceRunning) {
                        NotchWindowOverlayService.stop(context)
                    } else {
                        NotchWindowOverlayService.start(context)
                    }
                }
            }

            PermissionRowCard(
                title = "Opsi B: Layanan Aksesibilitas",
                subtitle = "Menyatu lebih dalam di atas status bar bawaan HP",
                isActive = state.isOverlayServiceRunning,
                buttonText = "Aksesibilitas"
            ) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.10f),
                    contentColor = EveCyanGlow
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Buka Info Aplikasi Wall-E (Untuk Titik Tiga ⋮ Izinkan Pengaturan)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            PermissionRowCard(
                title = "Akses Notifikasi & Spotify",
                subtitle = "Membaca lagu Spotify/YT Music dan membalas pesan WhatsApp",
                isActive = state.isNotificationListenerConnected,
                buttonText = "Izin Notif"
            ) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        // Section 2: Cute EVE Expressions
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
                text = "2. Pilih Ekspresi Menggemaskan EVE",
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
                    label = "^_^ Senang",
                    selected = state.eveMood == EveMood.HAPPY,
                    accent = EveCyanGlow
                ) {
                    IslandRepository.setMode(IslandMode.IDLE, expand = false)
                    IslandRepository.setEveMood(context, EveMood.HAPPY)
                }
                ModeSelectButton(
                    modifier = Modifier.weight(1f),
                    label = ";-) Kedip",
                    selected = state.eveMood == EveMood.WINK,
                    accent = WallESolarAmber
                ) {
                    IslandRepository.setMode(IslandMode.IDLE, expand = false)
                    IslandRepository.setEveMood(context, EveMood.WINK)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeSelectButton(
                    modifier = Modifier.weight(1f),
                    label = "<3 Cinta",
                    selected = state.eveMood == EveMood.LOVE,
                    accent = Color(0xFFFF7597)
                ) {
                    IslandRepository.setMode(IslandMode.IDLE, expand = false)
                    IslandRepository.setEveMood(context, EveMood.LOVE)
                }
                ModeSelectButton(
                    modifier = Modifier.weight(1f),
                    label = "🌱 Tanaman",
                    selected = state.eveMood == EveMood.PLANT,
                    accent = DirectivePlantGreen
                ) {
                    IslandRepository.setMode(IslandMode.IDLE, expand = false)
                    IslandRepository.setEveMood(context, EveMood.PLANT)
                }
            }

            Text(
                text = "Uji Aktivitas Lainnya:",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeSelectButton(
                    modifier = Modifier.weight(1f),
                    label = "Musik Spotify",
                    selected = state.mode == IslandMode.MUSIC,
                    accent = DirectivePlantGreen
                ) {
                    IslandRepository.updateMusic { it.copy(isPlaying = true) }
                    IslandRepository.setMode(IslandMode.MUSIC, expand = false)
                }
                ModeSelectButton(
                    modifier = Modifier.weight(1f),
                    label = "Cas Solar 45W",
                    selected = state.mode == IslandMode.SOLAR_CHARGE,
                    accent = WallESolarAmber
                ) {
                    IslandRepository.setMode(IslandMode.SOLAR_CHARGE, expand = false)
                }
                ModeSelectButton(
                    modifier = Modifier.weight(1f),
                    label = "Notif WA",
                    selected = state.mode == IslandMode.NOTIFICATION,
                    accent = EveCyanGlow
                ) {
                    IslandRepository.setMode(IslandMode.NOTIFICATION, expand = false)
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
                    text = if (state.isExpanded) "Tutup Visor (Collapse)" else "Buka Visor Notch (Expand)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Section 3: Hardware Camera Dimensions & Calibration
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
                    text = "3. Dimensi Hardware GT 10 Pro",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Reset Default",
                    color = EveCyanGlow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EveCyanGlow.copy(alpha = 0.12f))
                        .clickable {
                            IslandRepository.resetCalibration(context)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            CalibrationSliderItem(
                label = "Tinggi Notch (Height)",
                valueText = "${cal.idleHeightDp} dp",
                value = cal.idleHeightDp.toFloat(),
                valueRange = 28f..54f
            ) { newVal ->
                IslandRepository.updateCalibration(context) { it.copy(idleHeightDp = newVal.roundToInt()) }
            }

            CalibrationSliderItem(
                label = "Lebar Total Notch Saat Standby",
                valueText = "${cal.idleWidthDp} dp",
                value = cal.idleWidthDp.toFloat(),
                valueRange = 160f..260f
            ) { newVal ->
                IslandRepository.updateCalibration(context) { it.copy(idleWidthDp = newVal.roundToInt()) }
            }

            CalibrationSliderItem(
                label = "Lebar Lekukan Kurva (Corner Width)",
                valueText = "${cal.cornerWidthDp} dp",
                value = cal.cornerWidthDp.toFloat(),
                valueRange = 16f..46f
            ) { newVal ->
                IslandRepository.updateCalibration(context) { it.copy(cornerWidthDp = newVal.roundToInt()) }
            }

            CalibrationSliderItem(
                label = "Ukuran Lubang Kamera Punch-Hole",
                valueText = "${cal.cameraHoleSizeDp} dp",
                value = cal.cameraHoleSizeDp.toFloat(),
                valueRange = 14f..28f
            ) { newVal ->
                IslandRepository.updateCalibration(context) { it.copy(cameraHoleSizeDp = newVal.roundToInt()) }
            }

            CalibrationSliderItem(
                label = "Jarak Kamera ke Bezel Atas (Top Offset)",
                valueText = "${cal.cameraTopOffsetDp} dp",
                value = cal.cameraTopOffsetDp.toFloat(),
                valueRange = 0f..14f
            ) { newVal ->
                IslandRepository.updateCalibration(context) { it.copy(cameraTopOffsetDp = newVal.roundToInt()) }
            }

            CalibrationToggleRow(
                label = "Hilang saat membuka Aplikasi (Hanya di Home Screen)",
                checked = cal.hideWhenAppOpen
            ) { checked ->
                IslandRepository.updateCalibration(context) { it.copy(hideWhenAppOpen = checked) }
            }

            CalibrationToggleRow(
                label = "Hilang saat gulir menu atas/bawah (Panel Notif)",
                checked = cal.hideWhenMenuScrolled
            ) { checked ->
                IslandRepository.updateCalibration(context) { it.copy(hideWhenMenuScrolled = checked) }
            }

            CalibrationToggleRow(
                label = "Hilang sepenuhnya jika layar horizontal (Landscape)",
                checked = cal.hideWhenHorizontal
            ) { checked ->
                IslandRepository.updateCalibration(context) { it.copy(hideWhenHorizontal = checked) }
            }

            CalibrationToggleRow(
                label = "Tampilkan Titik Kamera Merah (X-Ray Guide)",
                checked = cal.xrayCameraGuide
            ) { checked ->
                IslandRepository.updateCalibration(context) { it.copy(xrayCameraGuide = checked) }
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
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
