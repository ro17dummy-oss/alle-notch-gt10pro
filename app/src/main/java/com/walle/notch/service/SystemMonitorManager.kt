package com.walle.notch.service

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.view.Display
import com.walle.notch.core.model.BatteryTelemetry
import com.walle.notch.core.state.IslandRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class SystemMonitorManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var monitorJob: Job? = null
    private var isRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    val current = readBatteryTelemetry(intent)
                    IslandRepository.updateBattery(current, triggerBanner = true)
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val current = readBatteryTelemetry(intent)
                    IslandRepository.updateBattery(current, triggerBanner = false)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    val current = IslandRepository.uiState.value.battery.copy(isCharging = false)
                    IslandRepository.updateBattery(current, triggerBanner = false)
                }
            }
        }
    }

    fun start() {
        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            context.registerReceiver(batteryReceiver, filter)
            isRegistered = true
        }

        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                sampleGamingAndSystemMetrics()
                tickTimersAndMedia()
                delay(1000L)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        if (isRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
            isRegistered = false
        }
    }

    private fun readBatteryTelemetry(intent: Intent): BatteryTelemetry {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 84)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val pct = ((level * 100f) / scale).roundToInt().coerceIn(0, 100)

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 362)
        val tempC = tempTenths / 10f
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4180)

        val currentState = IslandRepository.uiState.value.battery
        return currentState.copy(
            level = pct,
            isCharging = isCharging,
            isFastCharging45W = voltage >= 4000,
            temperatureCelsius = tempC,
            voltageMv = voltage
        )
    }

    private fun sampleGamingAndSystemMetrics() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val totalGb = if (memInfo.totalMem > 0) {
            memInfo.totalMem / (1024f * 1024f * 1024f)
        } else {
            8.0f
        }
        val usedBytes = (memInfo.totalMem - memInfo.availMem).coerceAtLeast(0L)
        val usedGb = usedBytes / (1024f * 1024f * 1024f)
        val usedPct = if (memInfo.totalMem > 0) {
            ((usedBytes * 100f) / memInfo.totalMem).roundToInt().coerceIn(1, 99)
        } else {
            62
        }

        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        val refreshHz = display?.refreshRate?.roundToInt() ?: 120

        val cpuTemp = readCpuThermalCelsius()
            ?: (IslandRepository.uiState.value.battery.temperatureCelsius + 2.5f)

        IslandRepository.updateGaming { prev ->
            prev.copy(
                refreshRateHz = if (prev.lock120Hz) 120 else refreshHz,
                cpuTempCelsius = (cpuTemp * 10f).roundToInt() / 10f,
                ramUsedPercent = usedPct,
                ramUsedGb = (usedGb * 10f).roundToInt() / 10f,
                ramTotalGb = (totalGb * 10f).roundToInt() / 10f
            )
        }
    }

    private fun readCpuThermalCelsius(): Float? {
        return try {
            val file = File("/sys/class/thermal/thermal_zone0/temp")
            if (file.canRead()) {
                val raw = file.readText().trim().toFloatOrNull() ?: return null
                val temp = if (raw > 1000f) raw / 1000f else raw / 10f
                if (temp in 20f..95f) temp else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tickTimersAndMedia() {
        val state = IslandRepository.uiState.value
        if (state.callTimer.timerRunning) {
            IslandRepository.updateCallTimer { ct ->
                val nextTimer = if (ct.timerRemainingSec > 0) ct.timerRemainingSec - 1 else 165
                val nextCallSec = if (ct.isCallConnected) ct.callDurationSec + 1 else ct.callDurationSec
                ct.copy(
                    timerRemainingSec = nextTimer,
                    callDurationSec = nextCallSec
                )
            }
        }
        if (state.music.isPlaying && state.music.durationMs > 0) {
            IslandRepository.updateMusic { m ->
                val nextPos = (m.positionMs + 1000L) % m.durationMs.coerceAtLeast(1000L)
                m.copy(positionMs = nextPos)
            }
        }
    }
}
