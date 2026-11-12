package com.walle.notch.service

import android.view.WindowManager
import com.walle.notch.core.model.IslandMode
import com.walle.notch.core.state.IslandRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Jendela overlay dengan lebar TETAP KONSTAN (326dp) agar Gravity.CENTER_HORIZONTAL
 * tidak pernah menghitung ulang koordinat X (menghilangkan glitch lompat ke kanan/kiri 100%).
 *
 * Selain itu, saat kembali dari garis tipis (peek -> normal), pemanggilan updateViewLayout
 * ditunda sampai animasi membesar selesai (400ms) agar WindowManager tidak menyela animasi GPU.
 */
class OverlayGeometryController(
    private val scope: CoroutineScope,
    private val density: Float,
    private val baseFlags: Int
) {
    private var focusable = false
    private var touchless = false
    private var large = false
    private var job: Job? = null

    fun setFocusable(value: Boolean) {
        focusable = value
    }

    fun applyTo(lp: WindowManager.LayoutParams) {
        val h = if (large) LARGE_H_DP else COMPACT_H_DP
        lp.width = (FIXED_W_DP * density).toInt()
        lp.height = (h * density).toInt()
        var f = baseFlags
        if (!focusable) f = f or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (touchless) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        lp.flags = f
    }

    fun start(update: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            IslandRepository.uiState
                .map { (it.isExpanded || it.mode == IslandMode.PRAYER_ALERT) to it.isAppPeek }
                .distinctUntilChanged()
                .collectLatest { (needLarge, peek) ->
                    if (needLarge) {
                        touchless = false
                        large = true
                        update()
                    } else if (peek) {
                        // Saat mengecil ke garis tipis: langsung aktifkan touch-through
                        touchless = true
                        if (large) {
                            delay(COLLAPSE_SETTLE_MS)
                            large = false
                        }
                        update()
                    } else {
                        // Saat kembali dari garis tipis ke ukuran normal (202x36dp):
                        // JANGAN panggil updateViewLayout selama animasi membesar berlangsung!
                        if (large) {
                            delay(COLLAPSE_SETTLE_MS)
                            large = false
                            touchless = false
                            update()
                        } else if (touchless) {
                            delay(GROW_SETTLE_MS)
                            touchless = false
                            update()
                        }
                    }
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val FIXED_W_DP = 326
        const val COMPACT_H_DP = 44
        const val LARGE_H_DP = 164
        private const val GROW_SETTLE_MS = 400L
        private const val COLLAPSE_SETTLE_MS = 480L
    }
}
