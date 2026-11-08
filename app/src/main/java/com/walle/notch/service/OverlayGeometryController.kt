package com.walle.notch.service

import android.view.WindowManager
import com.walle.notch.core.state.IslandRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Jendela overlay dengan ukuran TETAP (bukan WRAP_CONTENT).
 *
 * WRAP_CONTENT membuat jendela di-resize setiap frame saat notch beranimasi; saat tumbuh dari
 * kecil ke normal, surface baru tertinggal satu-dua frame sehingga terlihat glitch/terpotong.
 * Dengan ukuran tetap, seluruh animasi berjalan di dalam surface yang sama (tanpa relayout jendela).
 *
 * - COMPACT: cukup untuk semua bentuk non-expanded (idle 202, musik 248, call 244, ...)
 * - LARGE  : cukup untuk bentuk expanded terbesar (320x148); dipakai saat expanded, dan
 *            baru dikecilkan setelah animasi collapse selesai.
 * - Saat mode peek (notch mengecil di dalam aplikasi) jendela dibuat NOT_TOUCHABLE agar
 *   area transparan tidak menghalangi sentuhan ke aplikasi di bawahnya.
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
        val w = if (large) LARGE_W_DP else COMPACT_W_DP
        val h = if (large) LARGE_H_DP else COMPACT_H_DP
        lp.width = (w * density).toInt()
        lp.height = (h * density).toInt()
        var f = baseFlags
        if (!focusable) f = f or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (touchless) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        lp.flags = f
    }

    /** [update] dipanggil setiap geometri/flag berubah; implementasinya memanggil applyTo + updateViewLayout. */
    fun start(update: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            IslandRepository.uiState
                .map { it.isExpanded to it.isAppPeek }
                .distinctUntilChanged()
                .collectLatest { (expanded, peek) ->
                    touchless = peek
                    if (expanded) {
                        large = true
                        update()
                    } else {
                        update()
                        if (large) {
                            delay(COLLAPSE_SETTLE_MS)
                            large = false
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
        const val COMPACT_W_DP = 260
        const val COMPACT_H_DP = 44
        const val LARGE_W_DP = 336
        const val LARGE_H_DP = 164
        private const val COLLAPSE_SETTLE_MS = 520L
    }
}
