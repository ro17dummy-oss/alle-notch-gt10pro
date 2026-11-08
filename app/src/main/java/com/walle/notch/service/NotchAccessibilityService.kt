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
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
    private var launcherScrollResetJob: Job? = null
    private var lastKnownAppPkg: String = ""

    private val launcherPackages = mutableSetOf(
        "com.transsion.XOSLauncher",
        "com.transsion.XOSLauncher.upgrade",
        "com.transsion.hilauncher",
        "com.transsion.hilauncher.upgrade",
        "com.transsion.itel.launcher",
        "com.transsion.launcher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oppo.launcher",
        "com.vivo.launcher"
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
            val resolves = packageManager.queryIntentActivities(intent, 0)
            for (r in resolves) {
                r.activityInfo?.packageName?.let { launcherPackages.add(it) }
            }
            val defaultResolve = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            defaultResolve?.activityInfo?.packageName?.let { launcherPackages.add(it) }
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

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = km?.isKeyguardLocked == true
        IslandRepository.setKeyguardLocked(isLocked)
        if (isLocked) return

        val eventType = event.eventType
        val eventPkg = event.packageName?.toString().orEmpty()
        val className = event.className?.toString().orEmpty()
        val screenHeight = resources.displayMetrics.heightPixels

        when (eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Saat di Home Launcher, jika user mengusap ke atas / ke bawah (scroll vertikal), langsung kecilkan EVE
                if (!IslandRepository.uiState.value.isAppOpen && isLauncherPackage(eventPkg)) {
                    val isVerticalScroll = event.maxScrollY > 0 ||
                        event.scrollY > 0 ||
                        className.contains("RecyclerView", ignoreCase = true) ||
                        className.contains("GridView", ignoreCase = true) ||
                        className.contains("ScrollView", ignoreCase = true) ||
                        className.contains("AllApps", ignoreCase = true) ||
                        className.contains("Drawer", ignoreCase = true)

                    if (isVerticalScroll) {
                        IslandRepository.setMenuOrDrawerOpen(true)
                        scheduleLauncherDrawerRecheck(screenHeight)
                    } else {
                        // Usap halaman Home (PagedView / Workspace): cek apakah App Drawer / Search terbuka
                        val drawerActive = isLauncherDrawerOrSearchVisible(screenHeight)
                        IslandRepository.setMenuOrDrawerOpen(drawerActive)
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Hindari overhead berlebih pada CONTENT_CHANGED jika sedang di dalam aplikasi
                if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    IslandRepository.uiState.value.isAppOpen &&
                    !isLauncherPackage(eventPkg) &&
                    eventPkg != "com.android.systemui"
                ) {
                    return
                }
                evaluateWindowsAndState(eventType, eventPkg, className, screenHeight)
            }
        }
    }

    private fun evaluateWindowsAndState(
        eventType: Int,
        eventPkg: String,
        className: String,
        screenHeight: Int
    ) {
        var shadeOpenFromWindows = false
        var imeVisible = false
        var topAppPkg: String? = null

        try {
            val winList = windows
            if (!winList.isNullOrEmpty()) {
                val rect = Rect()
                for (w in winList) {
                    when (w.type) {
                        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> {
                            w.getBoundsInScreen(rect)
                            if (rect.height() > screenHeight / 6) {
                                imeVisible = true
                            }
                        }
                        AccessibilityWindowInfo.TYPE_SYSTEM -> {
                            val wPkg = try { w.root?.packageName?.toString() } catch (_: Exception) { null }
                            w.getBoundsInScreen(rect)
                            // Panel notifikasi / Control Center ketika diusap ke bawah menutupi > 35% tinggi layar
                            if ((wPkg == null || wPkg == "com.android.systemui") &&
                                rect.height() > (screenHeight * 0.35f).toInt()
                            ) {
                                shadeOpenFromWindows = true
                            }
                        }
                        AccessibilityWindowInfo.TYPE_APPLICATION -> {
                            if (topAppPkg == null) {
                                val wPkg = try { w.root?.packageName?.toString() } catch (_: Exception) { null }
                                if (!wPkg.isNullOrBlank() &&
                                    wPkg != packageName &&
                                    !isTransientOrSystemPackage(wPkg)
                                ) {
                                    topAppPkg = wPkg
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        val activeRootPkg = try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }

        // 1. Deteksi Notification Shade / Quick Settings (usap ke bawah dari atas layar)
        val shadeFromEvent = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventPkg == "com.android.systemui" && (
                className.contains("Notification", ignoreCase = true) ||
                className.contains("Shade", ignoreCase = true) ||
                className.contains("QuickSettings", ignoreCase = true) ||
                className.contains("ControlCenter", ignoreCase = true) ||
                className.contains("Panel", ignoreCase = true)
            )
        val isShadeOpen = shadeOpenFromWindows || shadeFromEvent
        IslandRepository.setNotificationShadeOpen(isShadeOpen)

        // 2. Tentukan aplikasi foreground yang sesungguhnya (abaikan SystemUI, Back ripple, Volume, Keyboard)
        val candidatePkg = when {
            !topAppPkg.isNullOrBlank() -> topAppPkg
            !activeRootPkg.isNullOrBlank() && !isTransientOrSystemPackage(activeRootPkg) -> activeRootPkg
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                eventPkg.isNotBlank() &&
                !isTransientOrSystemPackage(eventPkg) -> eventPkg
            else -> lastKnownAppPkg
        }

        if (candidatePkg.isBlank()) return
        lastKnownAppPkg = candidatePkg

        val isSelf = candidatePkg == packageName
        val isLauncher = isLauncherPackage(candidatePkg)

        if (isSelf) {
            // Di dalam aplikasi Wall-E Notch sendiri: tampilkan EVE normal agar bisa dikalibrasi
            launcherScrollResetJob?.cancel()
            IslandRepository.setAppOpen(false)
            IslandRepository.setMenuOrDrawerOpen(false)
            return
        }

        if (isLauncher) {
            // Benar-benar sudah keluar dari aplikasi dan berada di Home Launcher
            IslandRepository.setAppOpen(false)

            val drawerClassMatch = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                isLauncherPackage(eventPkg) && (
                    className.contains("Drawer", ignoreCase = true) ||
                    className.contains("AllApps", ignoreCase = true) ||
                    className.contains("AppsView", ignoreCase = true) ||
                    className.contains("Search", ignoreCase = true) ||
                    className.contains("HiBoard", ignoreCase = true) ||
                    className.contains("ZeroScreen", ignoreCase = true)
                )

            // Jika di Home Launcher lalu keyboard muncul (misal usap bawah buka Global Search) atau App Drawer terbuka
            val drawerOrSearchVisible = drawerClassMatch ||
                imeVisible ||
                isLauncherDrawerOrSearchVisible(screenHeight)

            IslandRepository.setMenuOrDrawerOpen(drawerOrSearchVisible)
        } else {
            // Sedang berada di dalam aplikasi: tetap mengecil (P1) meskipun tekan Back atau tutup keyboard
            launcherScrollResetJob?.cancel()
            IslandRepository.setAppOpen(true)
            IslandRepository.setMenuOrDrawerOpen(false)
        }
    }

    private fun scheduleLauncherDrawerRecheck(screenHeight: Int) {
        launcherScrollResetJob?.cancel()
        launcherScrollResetJob = serviceScope.launch {
            delay(500)
            if (!IslandRepository.uiState.value.isAppOpen) {
                val stillOpen = isLauncherDrawerOrSearchVisible(screenHeight)
                IslandRepository.setMenuOrDrawerOpen(stillOpen)
            }
        }
    }

    private fun isLauncherDrawerOrSearchVisible(screenHeight: Int): Boolean {
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return false
        val rootPkg = root.packageName?.toString().orEmpty()
        if (!isLauncherPackage(rootPkg)) return false

        return try {
            checkNodeForDrawerOrSearch(root, screenHeight, depth = 0)
        } catch (_: Exception) {
            false
        }
    }

    private fun checkNodeForDrawerOrSearch(
        node: AccessibilityNodeInfo,
        screenHeight: Int,
        depth: Int
    ): Boolean {
        if (depth > 7 || !node.isVisibleToUser) return false

        val id = node.viewIdResourceName?.lowercase().orEmpty()
        val cls = node.className?.toString().orEmpty()
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // 1. Jika ada kolom input pencarian (EditText) yang sedang fokus di Home / App Drawer / Global Search
        if (cls.contains("EditText", ignoreCase = true) && node.isFocused) {
            return true
        }

        // 2. Deteksi container App Drawer / Global Search berdasarkan ID view di XOSLauncher / Launcher3
        if (id.contains("all_apps") ||
            id.contains("apps_view") ||
            id.contains("apps_list") ||
            id.contains("drawer") ||
            id.contains("global_search") ||
            id.contains("branch_search") ||
            id.contains("search_container") ||
            id.contains("zero_screen") ||
            id.contains("hiboard")
        ) {
            // Pastikan container tersebut sedang ditarik ke atas layar (bukan tersembunyi di bawah layar)
            if (rect.top < (screenHeight * 0.45f).toInt() && rect.height() > (screenHeight * 0.40f).toInt()) {
                return true
            }
        }

        // 3. Deteksi list aplikasi vertikal (RecyclerView / GridView) yang memenuhi layar (App Drawer terbuka)
        if ((cls.contains("RecyclerView", ignoreCase = true) || cls.contains("GridView", ignoreCase = true)) &&
            node.isScrollable &&
            rect.top < (screenHeight * 0.38f).toInt() &&
            rect.height() > (screenHeight * 0.52f).toInt() &&
            !id.contains("workspace") &&
            !id.contains("hotseat") &&
            !id.contains("cell_layout")
        ) {
            return true
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            if (checkNodeForDrawerOrSearch(child, screenHeight, depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        if (launcherPackages.contains(pkg)) return true
        val lower = pkg.lowercase()
        if (lower.contains("launcher") || lower.contains("miui.home") || lower.contains("trebuchet")) {
            launcherPackages.add(pkg)
            return true
        }
        if (isDefaultLauncher(pkg)) {
            launcherPackages.add(pkg)
            return true
        }
        return false
    }

    private fun isTransientOrSystemPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        val lower = pkg.lowercase()
        return lower == "android" ||
            lower == "com.android.systemui" ||
            lower.contains("inputmethod") ||
            lower.contains("keyboard") ||
            lower.contains("kikaoem") ||
            lower.contains("swiftkey") ||
            lower.contains("gboard") ||
            lower.contains("permissioncontroller") ||
            lower.contains("volume")
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
