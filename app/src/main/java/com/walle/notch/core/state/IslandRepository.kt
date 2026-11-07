package com.walle.notch.core.state

import android.content.Context
import android.content.SharedPreferences
import com.walle.notch.core.model.BatteryTelemetry
import com.walle.notch.core.model.CallTimerState
import com.walle.notch.core.model.EveIdlePhase
import com.walle.notch.core.model.EveMood
import com.walle.notch.core.model.IslandCalibration
import com.walle.notch.core.model.IslandMode
import com.walle.notch.core.model.IslandUiState
import com.walle.notch.core.model.MusicState
import com.walle.notch.core.model.NotificationPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object IslandRepository {

    private const val PREFS_NAME = "walle_notch_gt10pro_prefs"
    private const val CALIBRATION_VERSION = 2 // v2: Fixed notch 36dp height, 202dp width, 36dp corner width

    private val repoScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transientBannerJob: Job? = null
    private var eveIdleCycleJob: Job? = null
    private var eveWakeJob: Job? = null

    private val _uiState = MutableStateFlow(IslandUiState())
    val uiState: StateFlow<IslandUiState> = _uiState.asStateFlow()

    // Media control callbacks wired by NotchNotificationListenerService
    var onMediaPlayPause: (() -> Unit)? = null
    var onMediaNext: (() -> Unit)? = null
    var onMediaPrevious: (() -> Unit)? = null
    var onMediaSeekTo: ((Long) -> Unit)? = null

    fun loadCalibration(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentVersion = prefs.getInt("calibration_version", 1)

        val cal = if (currentVersion < CALIBRATION_VERSION) {
            val fixed = IslandCalibration(
                idleWidthDp = 202,
                idleHeightDp = 36,
                cornerWidthDp = 36,
                cameraHoleSizeDp = 23,
                cameraTopOffsetDp = 6,
                hideWhenAppOpen = true,
                hideWhenMenuScrolled = true,
                hideWhenHorizontal = true
            )
            prefs.edit()
                .putInt("calibration_version", CALIBRATION_VERSION)
                .putInt("idleWidthDp", fixed.idleWidthDp)
                .putInt("idleHeightDp", fixed.idleHeightDp)
                .putInt("cornerWidthDp", fixed.cornerWidthDp)
                .putInt("cameraHoleSizeDp", fixed.cameraHoleSizeDp)
                .putInt("cameraTopOffsetDp", fixed.cameraTopOffsetDp)
                .putBoolean("hideWhenAppOpen", fixed.hideWhenAppOpen)
                .putBoolean("hideWhenMenuScrolled", fixed.hideWhenMenuScrolled)
                .putBoolean("hideWhenHorizontal", fixed.hideWhenHorizontal)
                .apply()
            fixed
        } else {
            IslandCalibration(
                offsetXDp = prefs.getInt("offsetXDp", 0),
                offsetYDp = prefs.getInt("offsetYDp", 0),
                idleWidthDp = prefs.getInt("idleWidthDp", 202),
                idleHeightDp = prefs.getInt("idleHeightDp", 36),
                cornerWidthDp = prefs.getInt("cornerWidthDp", 36),
                cameraHoleSizeDp = prefs.getInt("cameraHoleSizeDp", 23),
                cameraTopOffsetDp = prefs.getInt("cameraTopOffsetDp", 6),
                hideWhenAppOpen = prefs.getBoolean("hideWhenAppOpen", true),
                hideWhenMenuScrolled = prefs.getBoolean("hideWhenMenuScrolled", true),
                hideWhenHorizontal = prefs.getBoolean("hideWhenHorizontal", true),
                splitPillEnabled = prefs.getBoolean("splitPillEnabled", true),
                autoHideLandscape = prefs.getBoolean("autoHideLandscape", true),
                xrayCameraGuide = prefs.getBoolean("xrayCameraGuide", false)
            )
        }
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
            .putInt("cornerWidthDp", updated.cornerWidthDp)
            .putInt("cameraHoleSizeDp", updated.cameraHoleSizeDp)
            .putInt("cameraTopOffsetDp", updated.cameraTopOffsetDp)
            .putBoolean("hideWhenAppOpen", updated.hideWhenAppOpen)
            .putBoolean("hideWhenMenuScrolled", updated.hideWhenMenuScrolled)
            .putBoolean("hideWhenHorizontal", updated.hideWhenHorizontal)
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

    fun setAppOpen(isApp: Boolean) {
        val wasAppOpen = _uiState.value.isAppOpen
        _uiState.update { it.copy(isAppOpen = isApp) }
        if (isApp && _uiState.value.isExpanded && _uiState.value.mode == IslandMode.IDLE) {
            setExpanded(false)
        }
        if (!isApp && wasAppOpen) {
            // Kembali ke Home Screen: mulai siklus 10 detik standby EVE
            restartEveIdleCountdown()
        }
    }

    fun setNotificationShadeOpen(isShade: Boolean) {
        _uiState.update { it.copy(isNotificationShadeOpen = isShade) }
        if (isShade && _uiState.value.isExpanded) {
            setExpanded(false)
        }
    }

    fun setLandscapeHorizontal(isHorizontal: Boolean) {
        _uiState.update { it.copy(isLandscapeHorizontal = isHorizontal, isLandscapeFullscreen = isHorizontal) }
        if (isHorizontal && _uiState.value.isExpanded) {
            setExpanded(false)
        }
    }

    /**
     * Siklus 10 Detik Standby EVE:
     * 1. 0 - 10s: AWAKE_BLINK (melek ramah mengapit kamera)
     * 2. Detik 10: PRE_SLEEP_LOOK (2.2s lirik kiri-kanan realistis)
     * 3. Detik 12.2+: SLEEPING_ZZZ (tidur nyenyak + zZZ Cyan & Amber)
     */
    fun restartEveIdleCountdown() {
        eveWakeJob?.cancel()
        eveIdleCycleJob?.cancel()
        _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.AWAKE_BLINK) }

        eveIdleCycleJob = repoScope.launch {
            delay(10_000L) // 10 detik penuh
            _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.PRE_SLEEP_LOOK) }
            delay(2_200L) // Durasi lirik kiri-kanan
            _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.SLEEPING_ZZZ) }
        }
    }

    /**
     * Sentuhan Layar (Touch Event):
     * Melek (400ms) ➔ Senyum ^_^ (500ms) ➔ Touch Blackout
     */
    fun onScreenTouched() {
        val state = _uiState.value
        // Saat app terbuka notch tersembunyi; saat EVE sedang bangun jangan diulang
        if (state.isAppOpen || state.mode != IslandMode.IDLE) return
        if (state.eveIdlePhase == EveIdlePhase.WAKE_OPEN || state.eveIdlePhase == EveIdlePhase.WAKE_SMILE) return

        eveIdleCycleJob?.cancel()
        eveWakeJob?.cancel()

        val alreadyBlackout = state.eveIdlePhase == EveIdlePhase.TOUCH_BLACKOUT
        eveWakeJob = repoScope.launch {
            if (!alreadyBlackout) {
                // Tahap 1: Melek jelas (400ms)
                _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.WAKE_OPEN) }
                delay(400L)
                // Tahap 2: Senyum manis ^_^ (500ms)
                _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.WAKE_SMILE) }
                delay(500L)
                // Tahap 3: Blackout Notch bebas gangguan
                _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.TOUCH_BLACKOUT) }
            }
            // Tiap sentuhan baru memperpanjang blackout; diam 3.5 detik -> EVE standby lagi
            delay(3_500L)
            restartEveIdleCountdown()
        }
    }

    /**
     * Gesture Swipe-Up pada notch: Quick Nap instan
     */
    fun triggerQuickNap() {
        eveWakeJob?.cancel()
        eveIdleCycleJob?.cancel()
        eveIdleCycleJob = repoScope.launch {
            _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.PRE_SLEEP_LOOK) }
            delay(2_200L)
            _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.SLEEPING_ZZZ) }
        }
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
            } else if (!updatedMusic.isPlaying && state.mode == IslandMode.MUSIC) {
                IslandMode.IDLE
            } else {
                state.mode
            }
            state.copy(music = updatedMusic, mode = nextMode)
        }
    }

    fun postNotification(payload: NotificationPayload) {
        val previousMode = _uiState.value.mode.let { if (it == IslandMode.NOTIFICATION) IslandMode.IDLE else it }
        _uiState.update { state ->
            state.copy(
                notification = payload,
                mode = IslandMode.NOTIFICATION,
                isExpanded = false
            )
        }
        transientBannerJob?.cancel()
        transientBannerJob = repoScope.launch {
            delay(4500L)
            _uiState.update { state ->
                if (state.mode == IslandMode.NOTIFICATION && !state.isExpanded) {
                    val returnMode = if (state.music.isPlaying) IslandMode.MUSIC else previousMode
                    state.copy(mode = returnMode)
                } else state
            }
        }
    }

    fun updateBattery(telemetry: BatteryTelemetry, triggerBanner: Boolean = false) {
        val previousMode = _uiState.value.mode.let { if (it == IslandMode.SOLAR_CHARGE) IslandMode.IDLE else it }
        _uiState.update { state ->
            state.copy(
                battery = telemetry,
                mode = if (triggerBanner) IslandMode.SOLAR_CHARGE else state.mode,
                isExpanded = if (triggerBanner) false else state.isExpanded
            )
        }
        if (triggerBanner) {
            transientBannerJob?.cancel()
            transientBannerJob = repoScope.launch {
                delay(4000L)
                _uiState.update { state ->
                    if (state.mode == IslandMode.SOLAR_CHARGE && !state.isExpanded) {
                        val returnMode = if (state.music.isPlaying) IslandMode.MUSIC else previousMode
                        state.copy(mode = returnMode)
                    } else state
                }
            }
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
