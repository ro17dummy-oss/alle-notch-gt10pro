package com.walle.notch.core.state

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.walle.notch.core.model.BatteryTelemetry
import com.walle.notch.core.model.CallTimerState
import com.walle.notch.core.model.EveIdlePhase
import com.walle.notch.core.model.EveMood
import com.walle.notch.core.model.IslandCalibration
import com.walle.notch.core.model.IslandMode
import com.walle.notch.core.model.IslandUiState
import com.walle.notch.core.model.MusicState
import com.walle.notch.core.model.NotificationPayload
import com.walle.notch.core.model.PrayerAlertState
import com.walle.notch.core.model.PrayerSchedule
import com.walle.notch.core.model.StopwatchState
import com.walle.notch.core.model.TodoItem
import com.walle.notch.service.PrayerAlarmReceiver
import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random
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
    private const val KEY_TODO_LIST = "todoList"

    private val PADANG_PRAYERS = listOf(
        Triple("Subuh", 280, "04:40"),
        Triple("Dzuhur", 725, "12:05"),
        Triple("Ashar", 927, "15:27"),
        Triple("Maghrib", 1090, "18:10"),
        Triple("Isya", 1162, "19:22")
    )

    private val repoScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var appContext: Context? = null
    private var transientBannerJob: Job? = null
    private var prayerAlertJob: Job? = null
    private var activePrayerRingtone: Ringtone? = null
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
        appContext = context.applicationContext
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
        val savedLastPage = prefs.getInt("lastExpandedPage", 0).coerceIn(0, 3)
        val savedTodos = loadTodoList(prefs)

        _uiState.update { state ->
            state.copy(
                calibration = cal,
                eveMood = mood,
                lastExpandedPage = savedLastPage,
                todoList = savedTodos ?: state.todoList
            )
        }
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

    fun setLastExpandedPage(page: Int, context: Context? = null) {
        val validPage = page.coerceIn(0, 3)
        if (_uiState.value.lastExpandedPage == validPage) return
        _uiState.update { it.copy(lastExpandedPage = validPage) }
        val ctx = context ?: appContext
        ctx?.let {
            it.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("lastExpandedPage", validPage)
                .apply()
        }
    }

    fun cycleEveMood(context: Context?) {
        val current = _uiState.value.eveMood
        val moods = EveMood.values()
        val nextIndex = (current.ordinal + 1) % moods.size
        setEveMood(context, moods[nextIndex])
    }

    fun setAppOpen(isApp: Boolean) {
        val wasPeek = _uiState.value.isAppPeek
        _uiState.update { it.copy(isAppOpen = isApp) }
        if (isApp && _uiState.value.isExpanded) {
            setExpanded(false)
        }
        syncPeekLifecycle(wasPeek, _uiState.value.isAppPeek)
    }

    fun setKeyguardLocked(locked: Boolean) {
        val wasLocked = _uiState.value.isKeyguardLocked
        _uiState.update { it.copy(isKeyguardLocked = locked) }
        if (locked && _uiState.value.isExpanded) {
            setExpanded(false)
        }
        if (!locked && wasLocked) {
            // Baru dibuka dari lock screen: bangunkan EVE menyapa
            restartEveIdleCountdown()
        }
    }

    fun setMenuOrDrawerOpen(isOpen: Boolean) {
        val wasPeek = _uiState.value.isAppPeek
        _uiState.update { it.copy(isMenuOrDrawerOpen = isOpen) }
        if (isOpen && _uiState.value.isExpanded) {
            setExpanded(false)
        }
        syncPeekLifecycle(wasPeek, _uiState.value.isAppPeek)
    }

    fun setNotificationShadeOpen(isShade: Boolean) {
        val wasPeek = _uiState.value.isAppPeek
        _uiState.update { it.copy(isNotificationShadeOpen = isShade) }
        if (isShade && _uiState.value.isExpanded) {
            setExpanded(false)
        }
        syncPeekLifecycle(wasPeek, _uiState.value.isAppPeek)
    }

    /**
     * Saat masuk ke garis tipis (App / Usap Atas / Usap Bawah): matikan timer tidur & buat menghitam (TOUCH_BLACKOUT).
     * Saat kembali ke Home dari garis tipis: biarkan notch hitam polos selama animasi membesar (400ms),
     * lalu jika user sudah diam baru munculkan mata EVE Standby.
     */
    private fun syncPeekLifecycle(wasPeek: Boolean, nowPeek: Boolean) {
        if (nowPeek && !wasPeek) {
            eveWakeJob?.cancel()
            eveIdleCycleJob?.cancel()
            _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.TOUCH_BLACKOUT) }
        } else if (!nowPeek && wasPeek) {
            eveWakeJob?.cancel()
            eveIdleCycleJob?.cancel()
            _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.TOUCH_BLACKOUT) }
            eveWakeJob = repoScope.launch {
                // Tunggu animasi membesar selesai dalam kondisi hitam murni, lalu masuk Standby jika layar diam
                delay(1_200L)
                restartEveIdleCountdown()
            }
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

        // Detik 0 s/d 4: Wajah awal santai (Acak antara HAPPY ^_^ atau NORMAL O_O)
        val initialMood = if (Random.nextBoolean()) EveMood.HAPPY else EveMood.NORMAL
        _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.AWAKE_BLINK, eveMood = initialMood) }

        eveIdleCycleJob = repoScope.launch {
            // Berkedip santai selama 4 detik
            delay(4_000L)

            // Detik 4 s/d 5.6: Kejutan micro-expression spontan (WINK ;) / LOVE <3 / PLANT 🌱)
            val surprises = listOf(EveMood.WINK, EveMood.LOVE, EveMood.PLANT)
            val chosen = surprises[Random.nextInt(surprises.size)]
            _uiState.update { it.copy(eveMood = chosen) }
            delay(1_600L)

            // Detik 5.6 s/d 10: Kembali tersenyum ramah (HAPPY ^_^)
            _uiState.update { it.copy(eveMood = EveMood.HAPPY) }
            delay(4_400L)

            // Detik 10 s/d 12.2: Melirik kiri-kanan realistis sebelum tidur
            _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.PRE_SLEEP_LOOK) }
            delay(2_200L)

            // Detik 12.2+: Tidur lelap dengan breathing glow + zZZ melayang
            _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.SLEEPING_ZZZ) }
        }
    }

    /**
     * Aktivitas / Sentuhan Layar di Home:
     * - Jika EVE sedang tidur -> Melek (350ms) -> Senyum ^_^ (450ms) -> Menghitam (TOUCH_BLACKOUT)
     * - Jika EVE sedang melek biasa -> Langsung menghitam saja (TOUCH_BLACKOUT) selama ada aktivitas
     * - Setelah layar diam 3 detik tanpa aktivitas -> Kembali ke EVE Standby
     */
    fun onScreenTouched() {
        val state = _uiState.value
        if (state.isAppPeek || state.isNotchHidden || state.mode != IslandMode.IDLE) return
        if (state.eveIdlePhase == EveIdlePhase.WAKE_OPEN || state.eveIdlePhase == EveIdlePhase.WAKE_SMILE) return

        eveIdleCycleJob?.cancel()
        eveWakeJob?.cancel()

        val wasSleeping = state.eveIdlePhase == EveIdlePhase.SLEEPING_ZZZ ||
            state.eveIdlePhase == EveIdlePhase.PRE_SLEEP_LOOK

        eveWakeJob = repoScope.launch {
            if (wasSleeping) {
                _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.WAKE_OPEN) }
                delay(350L)
                _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.WAKE_SMILE) }
                delay(450L)
            }
            // Menghitam saja selama ada aktivitas
            _uiState.update { it.copy(eveIdlePhase = EveIdlePhase.TOUCH_BLACKOUT) }
            // Diam 3.0 detik tanpa aktivitas -> EVE standby muncul kembali
            delay(3_000L)
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
        if (expanded) {
            transientBannerJob?.cancel()
            refreshPrayerTimes()
        }
        _uiState.update { it.copy(isExpanded = expanded) }
    }

    fun dismissNotification() {
        transientBannerJob?.cancel()
        _uiState.update { state ->
            val nextMode = if (state.music.isPlaying) IslandMode.MUSIC else IslandMode.IDLE
            state.copy(mode = nextMode, isExpanded = false)
        }
        if (_uiState.value.mode == IslandMode.IDLE && !_uiState.value.isAppOpen) {
            restartEveIdleCountdown()
        }
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
            if (_uiState.value.mode == IslandMode.IDLE && !_uiState.value.isAppOpen) {
                restartEveIdleCountdown()
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

    private var stopwatchJob: Job? = null

    fun toggleTodoItem(id: Int) {
        _uiState.update { state ->
            val updated = state.todoList.map {
                if (it.id == id) it.copy(isDone = !it.isDone) else it
            }
            saveTodoList(updated)
            state.copy(todoList = updated)
        }
    }

    fun addTodoItem(text: String) {
        val sanitized = text.trim().replace("\n", " ").replace("||", " ")
        if (sanitized.isBlank()) return
        _uiState.update { state ->
            val nextId = (state.todoList.maxOfOrNull { it.id } ?: 0) + 1
            val updated = state.todoList + TodoItem(id = nextId, text = sanitized, isDone = false)
            saveTodoList(updated)
            state.copy(todoList = updated)
        }
    }

    fun deleteTodoItem(id: Int) {
        _uiState.update { state ->
            val updated = state.todoList.filterNot { it.id == id }
            saveTodoList(updated)
            state.copy(todoList = updated)
        }
    }

    private fun loadTodoList(prefs: SharedPreferences): List<TodoItem>? {
        if (!prefs.contains(KEY_TODO_LIST)) return null
        val raw = prefs.getString(KEY_TODO_LIST, "") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split("||")
                if (parts.size < 3) return@mapNotNull null
                val id = parts[0].toIntOrNull() ?: return@mapNotNull null
                val isDone = parts.last().toBooleanStrictOrNull() ?: false
                val text = parts.subList(1, parts.size - 1).joinToString("||")
                if (text.isBlank()) null else TodoItem(id = id, text = text, isDone = isDone)
            }
            .toList()
    }

    private fun saveTodoList(list: List<TodoItem>) {
        val ctx = appContext ?: return
        val encoded = list.joinToString("\n") { "${it.id}||${it.text}||${it.isDone}" }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TODO_LIST, encoded)
            .apply()
    }

    fun startStopwatch() {
        if (_uiState.value.stopwatch.isRunning) return
        _uiState.update { it.copy(stopwatch = it.stopwatch.copy(isRunning = true)) }
        stopwatchJob?.cancel()
        stopwatchJob = repoScope.launch {
            var lastTick = System.currentTimeMillis()
            while (_uiState.value.stopwatch.isRunning) {
                delay(60L)
                val now = System.currentTimeMillis()
                val delta = now - lastTick
                lastTick = now
                _uiState.update {
                    it.copy(stopwatch = it.stopwatch.copy(elapsedMillis = it.stopwatch.elapsedMillis + delta))
                }
            }
        }
    }

    fun pauseStopwatch() {
        stopwatchJob?.cancel()
        _uiState.update { it.copy(stopwatch = it.stopwatch.copy(isRunning = false)) }
    }

    fun resetStopwatch() {
        stopwatchJob?.cancel()
        _uiState.update { it.copy(stopwatch = StopwatchState(isRunning = false, elapsedMillis = 0L)) }
    }

    fun refreshPrayerTimes() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val next = PADANG_PRAYERS.firstOrNull { it.second > currentMinutes }
            ?: Triple("Subuh", 280 + 1440, "04:40")
        val diffMinutes = if (next.second >= currentMinutes) next.second - currentMinutes else (next.second + 1440) - currentMinutes
        val hours = diffMinutes / 60
        val mins = diffMinutes % 60
        val countdown = if (hours > 0) "in ${hours} h ${mins} min" else "in ${mins} min"

        val progress = when {
            currentMinutes < 280 -> 0.95f
            currentMinutes > 1162 -> 0.92f
            else -> ((currentMinutes - 280).toFloat() / (1162 - 280)).coerceIn(0.05f, 0.95f)
        }

        _uiState.update { state ->
            state.copy(
                prayer = PrayerSchedule(
                    cityName = "Padang",
                    subuh = "04:40",
                    dzuhur = "12:05",
                    ashar = "15:27",
                    maghrib = "18:10",
                    isya = "19:22",
                    nextPrayerName = next.first,
                    nextPrayerTime = next.third,
                    countdownText = countdown,
                    celestialProgress = progress
                )
            )
        }
    }

    fun schedulePrayerAlarms(context: Context) {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val jakartaTz = TimeZone.getTimeZone("Asia/Jakarta")
        val nowMillis = System.currentTimeMillis()

        PADANG_PRAYERS.forEachIndexed { index, (name, totalMinutes, timeStr) ->
            val cal = Calendar.getInstance(jakartaTz).apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, totalMinutes / 60)
                set(Calendar.MINUTE, totalMinutes % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= nowMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = Intent(appCtx, PrayerAlarmReceiver::class.java).apply {
                putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME, name)
                putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_TIME, timeStr)
            }
            val pi = PendingIntent.getBroadcast(
                appCtx,
                3000 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.canScheduleExactAlarms()
                } else {
                    true
                }
                if (canExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                }
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        }
    }

    fun triggerPrayerAlert(context: Context, prayerName: String, prayerTime: String) {
        val appCtx = context.applicationContext
        transientBannerJob?.cancel()
        prayerAlertJob?.cancel()
        stopPrayerSoundAndVibration(appCtx)

        _uiState.update { state ->
            state.copy(
                prayerAlert = PrayerAlertState(prayerName = prayerName, prayerTime = prayerTime),
                mode = IslandMode.PRAYER_ALERT,
                isExpanded = false
            )
        }

        startPrayerSoundAndVibration(appCtx)

        prayerAlertJob = repoScope.launch {
            delay(8000L)
            dismissPrayerAlert()
        }
    }

    fun dismissPrayerAlert() {
        prayerAlertJob?.cancel()
        appContext?.let { stopPrayerSoundAndVibration(it) }
        _uiState.update { state ->
            if (state.mode == IslandMode.PRAYER_ALERT) {
                val nextMode = if (state.music.isPlaying) IslandMode.MUSIC else IslandMode.IDLE
                state.copy(mode = nextMode, isExpanded = false)
            } else {
                state
            }
        }
        if (_uiState.value.mode == IslandMode.IDLE && !_uiState.value.isAppOpen) {
            restartEveIdleCountdown()
        }
    }

    private fun startPrayerSoundAndVibration(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                ringtone.play()
                activePrayerRingtone = ringtone
            }
        } catch (_: Exception) {}

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                // Pola getar lembut bertahap seperti ketukan pengingat sholat
                val timings = longArrayOf(0L, 350L, 250L, 350L, 600L, 450L, 250L, 450L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitudes = intArrayOf(0, 180, 0, 200, 0, 230, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timings, -1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun stopPrayerSoundAndVibration(context: Context) {
        try {
            activePrayerRingtone?.stop()
        } catch (_: Exception) {}
        activePrayerRingtone = null

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.cancel()
        } catch (_: Exception) {}
    }
}
