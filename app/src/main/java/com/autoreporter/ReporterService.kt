package com.autoreporter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ReporterService : AccessibilityService() {

    private val TAG = "AutoReporter"
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        var instance: ReporterService? = null
    }

    // ================================================================
    // Flow berdasarkan screenshot nyata:
    // 1. Tap ⋮ → popup → tap "Laporkan"
    // 2. Bottom sheet → tap "Illegal goods and services"
    // 3. Sub-menu → tap "Drugs"
    // 4. Sub-menu → tap "Illegal drugs"
    // 5. Form → tap "Kirim Laporan"
    // 6. Mode pilih pesan → centang semua → tap "Laporkan N pesan"
    // ================================================================

    private enum class State {
        IDLE,
        STEP_ILLEGAL_GOODS,
        STEP_DRUGS,
        STEP_ILLEGAL_DRUGS,
        STEP_KIRIM_LAPORAN,
        STEP_PILIH_PESAN,
        STEP_SUBMIT_PESAN,
        DONE
    }

    private var state = State.IDLE
    private var isRunning = false
    private var retryCount = 0
    private val MAX_RETRY = 10

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        showToast("AutoReporter siap ✓")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning) return
        event ?: return
        // Jalankan proses di setiap event UI
        handler.postDelayed({ tick() }, 600)
    }

    override fun onInterrupt() {
        isRunning = false
    }

    // ================================================================
    // START — dipanggil dari MainActivity
    // ================================================================
    fun startReporting() {
        state = State.IDLE
        isRunning = true
        retryCount = 0
        showToast("Mulai — buka Telegram, tap ⋮ lalu Laporkan")
        handler.postDelayed({ tick() }, 1500)
    }

    fun stopReporting() {
        isRunning = false
        state = State.IDLE
        showToast("Dihentikan")
    }

    // ================================================================
    // MAIN TICK — cek state dan jalankan aksi
    // ================================================================
    private fun tick() {
        if (!isRunning) return
        retryCount++
        if (retryCount > MAX_RETRY * 6) {
            showToast("⚠ Timeout. Tap MULAI lagi dari awal.")
            isRunning = false
            return
        }

        val roots = getAllRoots()

        when (state) {

            State.IDLE, State.STEP_ILLEGAL_GOODS -> {
                // Prioritas 1: langsung cek "Illegal goods and services" sudah visible
                if (tapText(roots, "Illegal goods and services")) {
                    showToast("✓ Illegal goods and services")
                    state = State.STEP_DRUGS
                    retryCount = 0
                    return
                }
                // Prioritas 2: cari tombol Laporkan di popup atau bottom sheet
                if (state == State.IDLE && tapText(roots, "Laporkan", "Report")) {
                    showToast("✓ Laporkan")
                    state = State.STEP_ILLEGAL_GOODS
                    retryCount = 0
                    return
                }
            }

            State.STEP_DRUGS -> {
                if (tapText(roots, "Drugs")) {
                    showToast("✓ Drugs")
                    state = State.STEP_ILLEGAL_DRUGS
                    retryCount = 0
                }
            }

            State.STEP_ILLEGAL_DRUGS -> {
                if (tapText(roots, "Illegal drugs")) {
                    showToast("✓ Illegal drugs")
                    state = State.STEP_KIRIM_LAPORAN
                    retryCount = 0
                }
            }

            State.STEP_KIRIM_LAPORAN -> {
                // Halaman form setelah "Illegal drugs"
                // Tombolnya "Kirim Laporan" (bukan "Laporkan")
                if (tapText(roots, "Kirim Laporan", "Send Report", "Submit")) {
                    showToast("✓ Kirim Laporan")
                    // Setelah ini mungkin muncul mode pilih pesan
                    state = State.STEP_PILIH_PESAN
                    retryCount = 0
                    return
                }
                // Fallback: cek apakah sudah langsung di mode pilih pesan
                if (hasText(roots, "Pilih pesan", "Select messages")) {
                    state = State.STEP_PILIH_PESAN
                    retryCount = 0
                }
            }

            State.STEP_PILIH_PESAN -> {
                // Mode "Pilih pesan" — centang semua pesan yang ada
                // Cek apakah sudah ada pesan yang dipilih (tombol "Laporkan N pesan" muncul)
                if (hasText(roots, "Laporkan", "Laporkan Pesan", "Report")) {
                    // Ada pesan yang sudah terpilih, langsung submit
                    state = State.STEP_SUBMIT_PESAN
                    retryCount = 0
                    tick()
                    return
                }
                // Centang semua checkbox/pesan yang terlihat
                val checked = checkAllMessages(roots)
                if (checked > 0) {
                    showToast("✓ Dipilih $checked pesan")
                    state = State.STEP_SUBMIT_PESAN
                    retryCount = 0
                } else {
                    // Tidak ada yang bisa dicentang, coba tap semua pesan visible
                    tapFirstVisibleMessage(roots)
                }
            }

            State.STEP_SUBMIT_PESAN -> {
                // Tap tombol "Laporkan N pesan" / "Laporkan Pesan"
                if (tapTextContains(roots, "Laporkan", "Report")) {
                    showToast("✅ Laporan berhasil dikirim!")
                    state = State.DONE
                    isRunning = false
                    sendBroadcast(Intent("com.autoreporter.REPORT_DONE"))
                }
            }

            State.DONE -> {
                isRunning = false
            }
        }
    }

    // ================================================================
    // HELPERS — node finder + gesture
    // ================================================================

    private fun getAllRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            windows?.forEach { win -> win?.root?.let { roots.add(it) } }
        } catch (e: Exception) { }
        rootInActiveWindow?.let { if (!roots.contains(it)) roots.add(it) }
        return roots
    }

    private fun tapText(roots: List<AccessibilityNodeInfo>, vararg texts: String): Boolean {
        for (root in roots) {
            for (text in texts) {
                val node = findNode(root, text) ?: continue
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty && bounds.centerX() > 0 && bounds.centerY() > 0) {
                    gestTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                    return true
                }
                // Fallback performAction
                if (performClick(node)) return true
            }
        }
        return false
    }

    // Tap yang contains text (untuk "Laporkan 2 pesan" dll)
    private fun tapTextContains(roots: List<AccessibilityNodeInfo>, vararg keywords: String): Boolean {
        for (root in roots) {
            val found = findNodeContains(root, *keywords)
            if (found != null) {
                val bounds = Rect()
                found.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    gestTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                    return true
                }
                if (performClick(found)) return true
            }
        }
        return false
    }

    private fun hasText(roots: List<AccessibilityNodeInfo>, vararg texts: String): Boolean {
        for (root in roots) {
            for (text in texts) {
                if (findNode(root, text) != null) return true
            }
        }
        return false
    }

    // Centang semua message checkbox yang ada di mode "Pilih pesan"
    private fun checkAllMessages(roots: List<AccessibilityNodeInfo>): Int {
        var count = 0
        for (root in roots) {
            count += checkAllCheckboxes(root)
        }
        return count
    }

    private fun checkAllCheckboxes(node: AccessibilityNodeInfo?): Int {
        if (node == null) return 0
        var count = 0
        // Cek apakah node ini adalah checkbox yang belum dicentang
        if (node.className?.contains("CheckBox") == true ||
            node.isCheckable && !node.isChecked) {
            if (performClick(node)) count++
        }
        for (i in 0 until node.childCount) {
            count += checkAllCheckboxes(node.getChild(i))
        }
        return count
    }

    // Tap pesan pertama yang visible (di mode pilih pesan)
    private fun tapFirstVisibleMessage(roots: List<AccessibilityNodeInfo>) {
        for (root in roots) {
            val msg = findFirstMessage(root)
            if (msg != null) {
                val bounds = Rect()
                msg.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    gestTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                    return
                }
            }
        }
    }

    private fun findFirstMessage(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        // Cari node yang kelihatan seperti pesan (bukan tombol, bukan header)
        if (node.isVisibleToUser && node.isClickable &&
            node.className?.contains("RelativeLayout") == true) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            // Harus di area tengah layar (bukan header/footer)
            if (bounds.height() > 50 && bounds.top > 200) return node
        }
        for (i in 0 until node.childCount) {
            val r = findFirstMessage(node.getChild(i))
            if (r != null) return r
        }
        return null
    }

    private fun findNode(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        // Exact match via API
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (!nodes.isNullOrEmpty()) {
            return nodes.firstOrNull { it.isVisibleToUser }
        }
        // Deep search
        return deepFind(root, text, 0)
    }

    private fun deepFind(node: AccessibilityNodeInfo?, text: String, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 30) return null
        val t = node.text?.toString() ?: ""
        val d = node.contentDescription?.toString() ?: ""
        if ((t.equals(text, ignoreCase = true) || d.equals(text, ignoreCase = true)) && node.isVisibleToUser) {
            return node
        }
        for (i in 0 until node.childCount) {
            val r = deepFind(node.getChild(i), text, depth + 1)
            if (r != null) return r
        }
        return null
    }

    private fun findNodeContains(root: AccessibilityNodeInfo, vararg keywords: String): AccessibilityNodeInfo? {
        return deepFindContains(root, keywords, 0)
    }

    private fun deepFindContains(node: AccessibilityNodeInfo?, keywords: Array<out String>, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 30) return null
        val t = node.text?.toString()?.lowercase() ?: ""
        val d = node.contentDescription?.toString()?.lowercase() ?: ""
        for (kw in keywords) {
            if ((t.contains(kw.lowercase()) || d.contains(kw.lowercase())) && node.isVisibleToUser) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val r = deepFindContains(node.getChild(i), keywords, depth + 1)
            if (r != null) return r
        }
        return null
    }

    private fun gestTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                Log.d(TAG, "Gesture OK ($x, $y)")
            }
            override fun onCancelled(g: GestureDescription) {
                Log.d(TAG, "Gesture cancelled ($x, $y)")
            }
        }, null)
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var target = node
        var i = 0
        while (!target.isClickable && target.parent != null && i < 8) {
            target = target.parent; i++
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun showToast(msg: String) {
        handler.post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
        Log.d(TAG, msg)
    }
}
