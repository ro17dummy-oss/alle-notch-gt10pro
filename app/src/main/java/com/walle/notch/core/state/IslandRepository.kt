package com.walle.notch.core.state

import android.content.Context
import android.content.SharedPreferences
import com.walle.notch.core.model.BatteryTelemetry
import com.walle.notch.core.model.CallTimerState
import com.walle.notch.core.model.EveMood
import com.walle.notch.core.model.IslandCalibration
import com.walle.notch.core.model.IslandMode
import com.walle.notch.core.model.IslandUiState
import com.walle.notch.core.model.MusicState
import com.walle.notch.core.model.NotificationPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object IslandRepository {

    private const val PREFS_NAME = "walle_notch_gt10pro_prefs"

    private val _uiState = MutableStateFlow(IslandUiState())
    val uiState: StateFlow<IslandUiState> = _uiState.asStateFlow()

    // Media control callbacks wired by NotchNotificationListenerService
    var onMediaPlayPause: (() -> Unit)? = null
    var onMediaNext: (() -> Unit)? = null
    var onMediaPrevious: (() -> Unit)? = null
    var onMediaSeekTo: ((Long) -> Unit)? = null

    fun loadCalibration(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cal = IslandCalibration(
            offsetXDp = prefs.getInt("offsetXDp", 0),
            offsetYDp = prefs.getInt("offsetYDp", 0),
            idleWidthDp = prefs.getInt("idleWidthDp", 148),
            idleHeightDp = prefs.getInt("idleHeightDp", 36),
            cameraHoleSizeDp = prefs.getInt("cameraHoleSizeDp", 18),
            splitPillEnabled = prefs.getBoolean("splitPillEnabled", true),
            autoHideLandscape = prefs.getBoolean("autoHideLandscape", true),
            xrayCameraGuide = prefs.getBoolean("xrayCameraGuide", false)
        )
        val savedMoodName = prefs.getString("eveMood", EveMood.HAPPY.name)
        val mood = try {
            EveMood.valueOf(savedMoodName ?: EveMood.HAPPY.name)
        } catch (_: Exception) {
            EveMood.HAPPY
        }

        _uiState.update { it.copy(calibration = cal, eveMood = mood) }
    }

    fun updateCalibration(context: Context, transform: (IslandCalibration) -> IslandCalibration) {
        val updated = transform(_uiState.value.calibration)
        _uiState.update { it.copy(calibration = updated) }
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("offsetXDp", updated.offsetXDp)
            .putInt("offsetYDp", updated.offsetYDp)
            .putInt("idleWidthDp", updated.idleWidthDp)
            .putInt("idleHeightDp", updated.idleHeightDp)
            .putInt("cameraHoleSizeDp", updated.cameraHoleSizeDp)
            .putBoolean("splitPillEnabled", updated.splitPillEnabled)
            .putBoolean("autoHideLandscape", updated.autoHideLandscape)
            .putBoolean("xrayCameraGuide", updated.xrayCameraGuide)
            .apply()
    }

    fun setEveMood(context: Context?, mood: EveMood) {
        _uiState.update { it.copy(eveMood = mood) }
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("eveMood", mood.name).apply()
        }
    }

    fun cycleEveMood(context: Context?) {
        val current = _uiState.value.eveMood
        val moods = EveMood.values()
        val nextIndex = (current.ordinal + 1) % moods.size
        setEveMood(context, moods[nextIndex])
    }

    fun resetCalibration(context: Context) {
        updateCalibration(context) { IslandCalibration() }
    }

    fun setMode(mode: IslandMode, expand: Boolean = false) {
        _uiState.update { it.copy(mode = mode, isExpanded = expand) }
    }

    fun setExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isExpanded = expanded) }
    }

    fun toggleExpanded() {
        _uiState.update { it.copy(isExpanded = !it.isExpanded) }
    }

    fun setLandscapeFullscreen(isLandscape: Boolean) {
        _uiState.update { it.copy(isLandscapeFullscreen = isLandscape) }
    }

    fun setOverlayServiceRunning(running: Boolean) {
        _uiState.update { it.copy(isOverlayServiceRunning = running) }
    }

    fun setNotificationListenerConnected(connected: Boolean) {
        _uiState.update { it.copy(isNotificationListenerConnected = connected) }
    }

    fun updateMusic(transform: (MusicState) -> MusicState) {
        _uiState.update { state ->
            val updatedMusic = transform(state.music)
            val nextMode = if (updatedMusic.isPlaying && state.mode == IslandMode.IDLE) {
                IslandMode.MUSIC
            } else {
                state.mode
            }
            state.copy(music = updatedMusic, mode = nextMode)
        }
    }

    fun postNotification(payload: NotificationPayload) {
        _uiState.update { state ->
            state.copy(
                notification = payload,
                mode = IslandMode.NOTIFICATION,
                isExpanded = false
            )
        }
    }

    fun updateBattery(telemetry: BatteryTelemetry, triggerBanner: Boolean = false) {
        _uiState.update { state ->
            state.copy(
                battery = telemetry,
                mode = if (triggerBanner) IslandMode.SOLAR_CHARGE else state.mode,
                isExpanded = if (triggerBanner) false else state.isExpanded
            )
        }
    }

    fun updateCallTimer(transform: (CallTimerState) -> CallTimerState) {
        _uiState.update { state ->
            state.copy(callTimer = transform(state.callTimer))
        }
    }

    fun postFeedback(message: String?) {
        _uiState.update { it.copy(lastFeedbackMessage = message) }
    }
}
