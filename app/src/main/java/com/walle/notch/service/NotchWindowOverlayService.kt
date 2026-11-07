package com.walle.notch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.walle.notch.R
import com.walle.notch.core.state.IslandRepository
import com.walle.notch.ui.island.WallEDynamicIslandOverlay
import com.walle.notch.ui.theme.WallENotchTheme
import kotlin.math.roundToInt

class NotchWindowOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private var systemMonitor: SystemMonitorManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        IslandRepository.loadCalibration(applicationContext)
        startForegroundNotification()

        systemMonitor = SystemMonitorManager(applicationContext).also { it.start() }
        attachOverlayWindow()
        IslandRepository.setOverlayServiceRunning(true)
    }

    private fun startForegroundNotification() {
        val channelId = "walle_notch_service_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Wall-E Notch Active",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_walle_logo)
            .setContentTitle("Wall-E Notch Aktif")
            .setContentText("Dynamic Island menyatu dengan bezel kamera Infinix GT 10 Pro")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun attachOverlayWindow() {
        if (composeView != null) return

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val owner = ServiceLifecycleOwner()
        owner.onCreate()
        owner.onResume()
        lifecycleOwner = owner

        val density = resources.displayMetrics.density
        val cal = IslandRepository.uiState.value.calibration

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = (cal.offsetXDp * density).toInt()
            y = 0
        }
        layoutParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)

            // Auto-detect hardware DisplayCutout to automatically lock onto the Infinix GT 10 Pro camera hole!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setOnApplyWindowInsetsListener { _, insets ->
                    val cutout = insets.displayCutout
                    if (cutout != null) {
                        val rect = cutout.boundingRectTop
                        if (!rect.isEmpty) {
                            val holeSize = (rect.width() / density).roundToInt()
                            val topOffset = (rect.top / density).roundToInt()
                            IslandRepository.updateCalibration(applicationContext) { prev ->
                                prev.copy(
                                    cameraHoleSizeDp = if (holeSize in 10..26) holeSize else 16,
                                    cameraTopOffsetDp = if (topOffset in 0..16) topOffset else 5
                                )
                            }
                        }
                    }
                    insets
                }
            }

            setContent {
                WallENotchTheme {
                    val state by IslandRepository.uiState.collectAsState()
                    WallEDynamicIslandOverlay(
                        state = state,
                        onRequestKeyboardFocus = { focusable ->
                            setOverlayFocusable(focusable)
                        }
                    )
                }
            }
        }

        composeView = view
        try {
            wm.addView(view, params)
        } catch (_: Exception) {
        }
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val lp = layoutParams ?: return

        lp.flags = if (focusable) {
            (lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv())
        } else {
            (lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        try {
            wm.updateViewLayout(view, lp)
        } catch (_: Exception) {
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        IslandRepository.setLandscapeFullscreen(isLandscape)
    }

    override fun onDestroy() {
        super.onDestroy()
        IslandRepository.setOverlayServiceRunning(false)
        systemMonitor?.stop()
        lifecycleOwner?.onDestroy()
        val wm = windowManager
        val view = composeView
        if (wm != null && view != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
        }
        composeView = null
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, NotchWindowOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NotchWindowOverlayService::class.java))
        }
    }
}

private class ServiceLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
