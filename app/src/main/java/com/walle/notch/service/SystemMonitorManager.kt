package com.walle.notch.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.walle.notch.core.model.BatteryTelemetry
import com.walle.notch.core.model.SystemHudState
import com.walle.notch.core.model.SystemHudType
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
    private var volumeObserver: ContentObserver? = null
    private var lastVolume: Int = -1

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    val current = readBatteryTelemetry(intent)
                    IslandRepository.updateBattery(current, triggerBanner = false)
                    val wattText = if (current.isFastCharging45W) "45W Cepat" else "Mengisi Daya"
                    IslandRepository.postSystemHud(
                        SystemHudState(
                            type = SystemHudType.CHARGER_CONNECTED,
                            title = "Pengisian Daya",
                            subtitle = "$wattText · ${current.level}%",
                            valueFraction = current.level / 100f,
                            batteryLevel = current.level
                        ),
                        durationMs = 3000L
                    )
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val current = readBatteryTelemetry(intent)
                    IslandRepository.updateBattery(current, triggerBanner = false)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    val current = IslandRepository.uiState.value.battery.copy(isCharging = false)
                    IslandRepository.updateBattery(current, triggerBanner = false)
                    IslandRepository.postSystemHud(
                        SystemHudState(
                            type = SystemHudType.CHARGER_DISCONNECTED,
                            title = "Kabel Dilepas",
                            subtitle = "Sisa baterai ${current.level}%",
                            valueFraction = current.level / 100f,
                            batteryLevel = current.level
                        ),
                        durationMs = 3000L
                    )
                }
                "android.media.VOLUME_CHANGED_ACTION" -> {
                    onVolumeChanged()
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val ringerMode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
                    val (title, icon) = when (ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> "Hening" to "silent"
                        AudioManager.RINGER_MODE_VIBRATE -> "Getar" to "vibrate"
                        AudioManager.RINGER_MODE_NORMAL -> "Dering" to "normal"
                        else -> "" to ""
                    }
                    if (title.isNotEmpty()) {
                        IslandRepository.postSystemHud(
                            SystemHudState(
                                type = SystemHudType.RINGER_MODE,
                                title = title,
                                subtitle = "Mode Suara",
                                ringerModeName = icon
                            ),
                            durationMs = 2000L
                        )
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val name = try {
                        device?.name?.takeIf { it.isNotBlank() } ?: "Perangkat Audio"
                    } catch (_: SecurityException) {
                        "Perangkat Audio"
                    }
                    val bat = extractBluetoothBattery(intent, device)
                    val sub = if (bat in 1..100) "Connected · $bat%" else "Connected"
                    IslandRepository.postSystemHud(
                        SystemHudState(
                            type = SystemHudType.BLUETOOTH_CONNECTED,
                            title = name,
                            subtitle = sub,
                            valueFraction = if (bat in 1..100) bat / 100f else 1f,
                            batteryLevel = bat
                        ),
                        durationMs = 3500L
                    )
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val name = try {
                        device?.name?.takeIf { it.isNotBlank() } ?: "Perangkat Audio"
                    } catch (_: SecurityException) {
                        "Perangkat Audio"
                    }
                    IslandRepository.postSystemHud(
                        SystemHudState(
                            type = SystemHudType.BLUETOOTH_DISCONNECTED,
                            title = name,
                            subtitle = "Disconnected",
                            valueFraction = 0f,
                            batteryLevel = -1
                        ),
                        durationMs = 3000L
                    )
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
                addAction("android.media.VOLUME_CHANGED_ACTION")
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            }
            context.registerReceiver(systemReceiver, filter)
            isRegistered = true

            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            lastVolume = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
            registerVolumeObserver()
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
                context.unregisterReceiver(systemReceiver)
            } catch (_: Exception) {
            }
            unregisterVolumeObserver()
            isRegistered = false
        }
    }

    private fun registerVolumeObserver() {
        try {
            volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    onVolumeChanged()
                }
            }
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver!!
            )
        } catch (_: Exception) {}
    }

    private fun unregisterVolumeObserver() {
        volumeObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
            volumeObserver = null
        }
    }

    private fun onVolumeChanged() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (current != lastVolume) {
            lastVolume = current
            val fraction = (current.toFloat() / max).coerceIn(0f, 1f)
            IslandRepository.postSystemHud(
                SystemHudState(
                    type = SystemHudType.VOLUME,
                    valueFraction = fraction,
                    title = "Volume",
                    subtitle = "${(fraction * 100).toInt()}%"
                ),
                durationMs = 1800L
            )
        }
    }

    private fun extractBluetoothBattery(intent: Intent, device: BluetoothDevice?): Int {
        val extraBat = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
        if (extraBat in 0..100) return extraBat

        if (device != null) {
            try {
                val method = device.javaClass.getMethod("getBatteryLevel")
                val level = method.invoke(device) as? Int ?: -1
                if (level in 0..100) return level
            } catch (_: Exception) {}
        }
        return -1
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
        // Penghematan daya baterai: Jangan lakukan pembaruan jika layar mati atau tidak ada media/panggilan aktif
        if (state.isKeyguardLocked && !state.music.isPlaying && !state.callTimer.isCallConnected && !state.callTimer.isIncomingCall) {
            return
        }

        // Ticking hanya saat panggilan telepon benar-benar aktif / masuk
        if (state.callTimer.isCallConnected || state.callTimer.isIncomingCall) {
            IslandRepository.updateCallTimer { ct ->
                val nextTimer = if (ct.timerRemainingSec > 0) ct.timerRemainingSec - 1 else 0
                val nextCallSec = if (ct.isCallConnected) ct.callDurationSec + 1 else ct.callDurationSec
                ct.copy(
                    timerRemainingSec = nextTimer,
                    callDurationSec = nextCallSec
                )
            }
        }

        // Ticking durasi pemutar musik hanya saat lagu sedang diputar
        if (state.music.isPlaying && state.music.durationMs > 0) {
            IslandRepository.updateMusic { m ->
                val nextPos = (m.positionMs + 1000L) % m.durationMs.coerceAtLeast(1000L)
                m.copy(positionMs = nextPos)
            }
        }
    }
}
