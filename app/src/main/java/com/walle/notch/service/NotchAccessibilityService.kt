package com.walle.notch.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
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
import com.walle.notch.core.state.IslandRepository
import com.walle.notch.ui.island.WallEDynamicIslandOverlay
import com.walle.notch.ui.theme.WallENotchTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NotchAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var systemMonitor: SystemMonitorManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private val launcherPackages = mutableSetOf(
        "com.transsion.hilauncher",
        "com.transsion.XOSLauncher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home"
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    IslandRepository.setKeyguardLocked(true)
                }
                Intent.ACTION_USER_PRESENT -> {
                    IslandRepository.setKeyguardLocked(false)
                }
                Intent.ACTION_SCREEN_ON -> {
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    IslandRepository.setKeyguardLocked(km?.isKeyguardLocked == true)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        IslandRepository.loadCalibration(applicationContext)
        IslandRepository.setOverlayServiceRunning(true)

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        IslandRepository.setLandscapeHorizontal(isLandscape)

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        IslandRepository.setKeyguardLocked(km?.isKeyguardLocked == true)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            registerReceiver(screenReceiver, filter)
        } catch (_: Exception) {}

        detectInstalledLaunchers()

        systemMonitor = SystemMonitorManager(applicationContext).also { it.start() }
        attachOverlayWindow()
        observeCalibrationOffsets()
        observeNotchHidden()
        IslandRepository.restartEveIdleCountdown()
    }

    // Jendela overlay tetap VISIBLE selama animasi geser-atas; baru GONE setelah selesai
    private fun observeNotchHidden() {
        serviceScope.launch {
            IslandRepository.uiState
                .map { it.isNotchHidden }
                .distinctUntilChanged()
                .collectLatest { hidden ->
                    if (hidden) {
                        delay(350)
                        composeView?.visibility = View.GONE
                    } else {
                        composeView?.visibility = View.VISIBLE
                    }
                }
        }
    }

    private fun detectInstalledLaunchers() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolves = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (r in resolves) {
                r.activityInfo?.packageName?.let { launcherPackages.add(it) }
            }
        } catch (_: Exception) {
        }
    }

    private fun attachOverlayWindow() {
        if (composeView != null) return

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val owner = OverlayLifecycleOwner()
        owner.onCreate()
        owner.onResume()
        lifecycleOwner = owner

        val density = resources.displayMetrics.density
        val cal = IslandRepository.uiState.value.calibration

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
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
            // Sentuhan di luar notch HANYA aktif saat notch sedang Expand -> langsung collapse/tutup
            setOnTouchListener { _, ev ->
                if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    if (IslandRepository.uiState.value.isExpanded) {
                        IslandRepository.setExpanded(false)
                    }
                }
                false
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

    private fun observeCalibrationOffsets() {
        serviceScope.launch {
            IslandRepository.uiState
                .map { it.calibration.offsetXDp }
                .distinctUntilChanged()
                .collect { offsetXDp ->
                    val wm = windowManager ?: return@collect
                    val view = composeView ?: return@collect
                    val lp = layoutParams ?: return@collect
                    val density = resources.displayMetrics.density
                    lp.x = (offsetXDp * density).toInt()
                    lp.y = 0
                    try {
                        wm.updateViewLayout(view, lp)
                    } catch (_: Exception) {
                    }
                }
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
        lp.softInputMode = if (focusable) {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }
        try {
            wm.updateViewLayout(view, lp)
            if (focusable) {
                view.post {
                    view.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        IslandRepository.setLandscapeHorizontal(isLandscape)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val isLocked = km?.isKeyguardLocked == true
                IslandRepository.setKeyguardLocked(isLocked)
                if (isLocked) return

                val activeRootPkg = try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
                val eventPkg = event.packageName?.toString().orEmpty()
                val className = event.className?.toString().orEmpty()

                // Target package: utamakan root foreground active window
                val targetPkg = if (!activeRootPkg.isNullOrBlank() && activeRootPkg != "android") {
                    activeRootPkg
                } else {
                    eventPkg
                }
                if (targetPkg.isBlank()) return

                val isLauncher = launcherPackages.contains(targetPkg) || isDefaultLauncher(targetPkg)
                val isSystemUi = targetPkg == "com.android.systemui"
                val isSelf = targetPkg == packageName

                // Notification Shade / Quick Settings status bar pull
                val isShade = isSystemUi && (
                    className.contains("Notification", true) ||
                    className.contains("Panel", true) ||
                    className.contains("Shade", true) ||
                    className.contains("QuickSettings", true) ||
                    className.contains("StatusBar", true)
                )

                // Menu drawer usap ke atas di Home Launcher
                val isDrawer = isLauncher && (
                    className.contains("Drawer", true) ||
                    className.contains("AllApps", true) ||
                    className.contains("AppsView", true) ||
                    className.contains("Search", true)
                )

                // Sedang berada di aplikasi: bukan launcher, bukan systemui, bukan app sendiri
                val isApp = !isLauncher && !isSystemUi && !isSelf

                IslandRepository.setAppOpen(isApp)
                IslandRepository.setMenuOrDrawerOpen(isDrawer)
                IslandRepository.setNotificationShadeOpen(isShade)
            }
        }
    }

    private fun isDefaultLauncher(pkg: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolve = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolve?.activityInfo?.packageName == pkg
        } catch (_: Exception) {
            false
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        IslandRepository.setOverlayServiceRunning(false)
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
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
}

private class OverlayLifecycleOwner :
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
