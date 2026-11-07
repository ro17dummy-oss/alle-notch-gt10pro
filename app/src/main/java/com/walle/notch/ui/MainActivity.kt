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
                text = "Cute EVE Liquid Notch",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Lekukan halus menyatu ke bezel atas layar Infinix GT 10 Pro dengan maskot EVE interaktif yang super menggemaskan.",
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
                    text = "Preview Sentuh Wajah EVE",
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
                    .height(210.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF131B2B))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .clickable { IslandRepository.setExpanded(false) },
                contentAlignment = Alignment.TopCenter
            ) {
                WallEDynamicIslandOverlay(
                    state = state,
                    onRequestKeyboardFocus = {}
                )
            }

            Text(
                text = "Tip: Ketuk wajah EVE di kotak preview di atas untuk berganti ekspresi!",
                color = EveCyanGlow,
                fontSize = 12.sp,
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

            // Method A: Standard Overlay (Bebas dari Restricted Settings!)
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

            // Method B: Accessibility
            PermissionRowCard(
                title = "Opsi B: Layanan Aksesibilitas",
                subtitle = "Menyatu lebih dalam di atas status bar bawaan HP",
                isActive = state.isOverlayServiceRunning,
                buttonText = "Aksesibilitas"
            ) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            // Helper button: Direct open App Details to click the 3 dots
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

            // Notification Listener
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

            // Other Activities
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

        // Section 3: Auto-Hide
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
                text = "3. Opsi Perilaku",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            CalibrationToggleRow(
                label = "Tampilkan Titik Kamera (X-Ray)",
                checked = cal.xrayCameraGuide
            ) { checked ->
                IslandRepository.updateCalibration(context) { it.copy(xrayCameraGuide = checked) }
            }

            CalibrationToggleRow(
                label = "Sembunyikan otomatis saat video Landscape",
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
