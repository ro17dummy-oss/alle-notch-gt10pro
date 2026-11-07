package com.walle.notch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.walle.notch.core.model.BatteryTelemetry
import com.walle.notch.core.state.IslandRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
