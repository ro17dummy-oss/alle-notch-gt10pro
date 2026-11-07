package com.walle.notch.core.model

import android.app.PendingIntent
import android.app.RemoteInput
import android.graphics.Bitmap

enum class IslandMode {
    IDLE,
    MUSIC,
    SOLAR_CHARGE,
    NOTIFICATION,
    CALL_TIMER
}

enum class EveMood {
    HAPPY,   // ^_^ Happy curved eyes with soft blush cheeks
    WINK,    // ;) Cute playful wink
    LOVE,    // <3 Glowing cyan heart eyes
    SLEEPY,  // -_- Restful sleepy eyes with zZ
    PLANT,   // 🌱 Wall-E green plant sprout directive
    NORMAL   // O_O Classic EVE blinking oval eyes
}

data class IslandCalibration(
    val offsetXDp: Int = 0,
    val offsetYDp: Int = 0,
    val idleWidthDp: Int = 148,
    val idleHeightDp: Int = 36,
    val cameraHoleSizeDp: Int = 18,
    val splitPillEnabled: Boolean = true,
    val autoHideLandscape: Boolean = true,
    val xrayCameraGuide: Boolean = false
)

data class MusicState(
    val isPlaying: Boolean = false,
    val title: String = "Put On Your Sunday Clothes",
    val artist: String = "Wall-E Archive Tape",
    val packageName: String = "",
    val albumArt: Bitmap? = null,
    val durationMs: Long = 194_000L,
    val positionMs: Long = 78_000L,
    val outputDeviceName: String = "EVE Audio"
)

data class NotificationPayload(
    val key: String = "",
    val packageName: String = "com.whatsapp",
    val appLabel: String = "WA",
    val sender: String = "EVE Probe 01",
    val message: String = "Apakah sampel tanaman sudah aman di dek?",
    val timestamp: Long = System.currentTimeMillis(),
    val contentIntent: PendingIntent? = null,
    val replyActionIntent: PendingIntent? = null,
    val remoteInput: RemoteInput? = null
)

data class BatteryTelemetry(
    val level: Int = 84,
    val isCharging: Boolean = false,
    val isFastCharging45W: Boolean = true,
    val bypassChargingEnabled: Boolean = true,
    val temperatureCelsius: Float = 36.2f,
    val voltageMv: Int = 4180
)

data class CallTimerState(
    val callerName: String = "Kapten B. McCrea",
    val isIncomingCall: Boolean = false,
    val isCallConnected: Boolean = false,
    val callDurationSec: Int = 0,
    val timerRunning: Boolean = true,
    val timerRemainingSec: Int = 165,
    val acceptIntent: PendingIntent? = null,
    val declineIntent: PendingIntent? = null
)

data class IslandUiState(
    val mode: IslandMode = IslandMode.IDLE,
    val eveMood: EveMood = EveMood.HAPPY,
    val isExpanded: Boolean = false,
    val isLandscapeFullscreen: Boolean = false,
    val calibration: IslandCalibration = IslandCalibration(),
    val music: MusicState = MusicState(),
    val notification: NotificationPayload? = null,
    val battery: BatteryTelemetry = BatteryTelemetry(),
    val callTimer: CallTimerState = CallTimerState(),
    val isOverlayServiceRunning: Boolean = false,
    val isNotificationListenerConnected: Boolean = false,
    val lastFeedbackMessage: String? = null
)
