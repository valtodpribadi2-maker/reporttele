package com.autoreporter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ReporterService : AccessibilityService() {

    private val TAG = "AutoReporter"
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        var instance: ReporterService? = null
    }

    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        showToast("AutoReporter siap ✓")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { isRunning = false }

    // ================================================================
    // GET SCREEN SIZE
    // ================================================================
    private fun getScreenSize(): Pair<Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return Pair(dm.widthPixels, dm.heightPixels)
    }

    // ================================================================
    // ENTRY POINT — dipanggil MainActivity setelah countdown selesai
    // ================================================================
    fun runFullSequence() {
        isRunning = true
        val (w, h) = getScreenSize()

        showToast("Tap ⋮...")

        // STEP 1: Tap ⋮ — pojok kanan atas
        // X = width - ~50px, Y = ~110px dari atas (di bawah status bar)
        val dotX = w - 52f
        val dotY = 115f

        handler.postDelayed({
            gestTap(dotX, dotY)

            // STEP 2: Tap "Laporkan" di popup
            // Popup muncul dari kanan atas, "Laporkan" adalah item ke-4
            // Dari screenshot: Y kira2 26% dari height, X kira2 68% dari width
            handler.postDelayed({
                // Coba node detection dulu untuk "Laporkan"
                val roots = getAllRoots()
                val tapped = tapText(roots, "Laporkan", "Report")
                if (tapped) {
                    showToast("✓ Laporkan")
                } else {
                    // Fallback: koordinat estimasi posisi "Laporkan" di popup
                    val lapX = w * 0.68f
                    val lapY = h * 0.265f
                    showToast("Tap Laporkan (koordinat)...")
                    gestTap(lapX, lapY)
                }

                // STEP 3: "Illegal goods and services"
                handler.postDelayed({
                    stepIllegalGoods()
                }, 1200)

            }, 1000)
        }, 500)
    }

    // ================================================================
    // STEP 3: Illegal goods and services
    // ================================================================
    private fun stepIllegalGoods(retry: Int = 0) {
        if (retry > 8) { showToast("⚠ Tidak ketemu 'Illegal goods'"); return }
        val roots = getAllRoots()
        if (tapText(roots, "Illegal goods and services")) {
            showToast("✓ Illegal goods and services")
            handler.postDelayed({ stepDrugs() }, 1200)
        } else {
            handler.postDelayed({ stepIllegalGoods(retry + 1) }, 600)
        }
    }

    // ================================================================
    // STEP 4: Drugs
    // ================================================================
    private fun stepDrugs(retry: Int = 0) {
        if (retry > 8) { showToast("⚠ Tidak ketemu 'Drugs'"); return }
        val roots = getAllRoots()
        if (tapText(roots, "Drugs")) {
            showToast("✓ Drugs")
            handler.postDelayed({ stepIllegalDrugs() }, 1200)
        } else {
            handler.postDelayed({ stepDrugs(retry + 1) }, 600)
        }
    }

    // ================================================================
    // STEP 5: Illegal drugs
    // ================================================================
    private fun stepIllegalDrugs(retry: Int = 0) {
        if (retry > 8) { showToast("⚠ Tidak ketemu 'Illegal drugs'"); return }
        val roots = getAllRoots()
        if (tapText(roots, "Illegal drugs")) {
            showToast("✓ Illegal drugs")
            handler.postDelayed({ stepKirimLaporan() }, 1200)
        } else {
            handler.postDelayed({ stepIllegalDrugs(retry + 1) }, 600)
        }
    }

    // ================================================================
    // STEP 6: Kirim Laporan (tombol biru di halaman form)
    // ================================================================
    private fun stepKirimLaporan(retry: Int = 0) {
        if (retry > 8) { showToast("⚠ Tidak ketemu 'Kirim Laporan'"); return }
        val roots = getAllRoots()
        if (tapText(roots, "Kirim Laporan", "Send Report")) {
            showToast("✓ Kirim Laporan")
            // Setelah ini bisa muncul mode pilih pesan
            handler.postDelayed({ stepPilihPesan() }, 1500)
        } else {
            // Cek apakah sudah langsung di mode pilih pesan
            if (hasText(roots, "Pilih pesan", "Select messages", "Laporkan Pesan")) {
                showToast("Mode pilih pesan...")
                stepPilihPesan()
            } else {
                handler.postDelayed({ stepKirimLaporan(retry + 1) }, 600)
            }
        }
    }

    // ================================================================
    // STEP 7: Pilih pesan (centang semua) lalu submit
    // ================================================================
    private fun stepPilihPesan(retry: Int = 0) {
        if (retry > 10) {
            // Sudah cukup, coba submit saja
            stepSubmitPesan()
            return
        }

        val roots = getAllRoots()

        // Cek apakah tombol submit sudah muncul
        if (hasTextContains(roots, "Laporkan", "Report")) {
            handler.postDelayed({ stepSubmitPesan() }, 500)
            return
        }

        // Centang semua checkbox yang ada
        var checked = 0
        for (root in roots) {
            checked += checkAllCheckboxes(root)
        }

        if (checked > 0) {
            showToast("✓ Dipilih $checked pesan")
            handler.postDelayed({ stepSubmitPesan() }, 800)
        } else {
            // Coba tap pesan pertama yang visible
            tapFirstVisibleMessage(roots)
            handler.postDelayed({ stepPilihPesan(retry + 1) }, 800)
        }
    }

    // ================================================================
    // STEP 8: Submit — tap "Laporkan N pesan"
    // ================================================================
    private fun stepSubmitPesan(retry: Int = 0) {
        if (retry > 8) { showToast("✅ Selesai!"); done(); return }
        val roots = getAllRoots()
        // Cari tombol "Laporkan N pesan" atau "Laporkan Pesan"
        if (tapTextContains(roots, "Laporkan", "Report")) {
            showToast("✅ Laporan berhasil dikirim!")
            done()
        } else {
            handler.postDelayed({ stepSubmitPesan(retry + 1) }, 600)
        }
    }

    private fun done() {
        isRunning = false
        sendBroadcast(Intent("com.autoreporter.REPORT_DONE"))
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private fun getAllRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try { windows?.forEach { win -> win?.root?.let { roots.add(it) } } } catch (e: Exception) { }
        rootInActiveWindow?.let { if (!roots.contains(it)) roots.add(it) }
        return roots
    }

    private fun tapText(roots: List<AccessibilityNodeInfo>, vararg texts: String): Boolean {
        for (root in roots) {
            for (text in texts) {
                val node = findNode(root, text) ?: continue
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    gestTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                    return true
                }
                if (performClick(node)) return true
            }
        }
        return false
    }

    private fun tapTextContains(roots: List<AccessibilityNodeInfo>, vararg kws: String): Boolean {
        for (root in roots) {
            val node = deepFindContains(root, kws, 0) ?: continue
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                gestTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                return true
            }
            if (performClick(node)) return true
        }
        return false
    }

    private fun hasText(roots: List<AccessibilityNodeInfo>, vararg texts: String): Boolean {
        for (root in roots) for (text in texts) { if (findNode(root, text) != null) return true }
        return false
    }

    private fun hasTextContains(roots: List<AccessibilityNodeInfo>, vararg kws: String): Boolean {
        for (root in roots) { if (deepFindContains(root, kws, 0) != null) return true }
        return false
    }

    private fun checkAllCheckboxes(node: AccessibilityNodeInfo?): Int {
        if (node == null) return 0
        var count = 0
        if ((node.className?.contains("CheckBox") == true || node.isCheckable) && !node.isChecked) {
            if (performClick(node)) count++
        }
        for (i in 0 until node.childCount) count += checkAllCheckboxes(node.getChild(i))
        return count
    }

    private fun tapFirstVisibleMessage(roots: List<AccessibilityNodeInfo>) {
        val (_, h) = getScreenSize()
        for (root in roots) {
            val msg = findFirstMessage(root, h) ?: continue
            val bounds = Rect()
            msg.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) { gestTap(bounds.centerX().toFloat(), bounds.centerY().toFloat()); return }
        }
    }

    private fun findFirstMessage(node: AccessibilityNodeInfo?, screenH: Int): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isVisibleToUser && node.isClickable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.height() > 50 && bounds.top > screenH * 0.15 && bounds.bottom < screenH * 0.85) return node
        }
        for (i in 0 until node.childCount) {
            val r = findFirstMessage(node.getChild(i), screenH)
            if (r != null) return r
        }
        return null
    }

    private fun findNode(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (!nodes.isNullOrEmpty()) return nodes.firstOrNull { it.isVisibleToUser }
        return deepFind(root, text, 0)
    }

    private fun deepFind(node: AccessibilityNodeInfo?, text: String, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 30) return null
        val t = node.text?.toString() ?: ""
        val d = node.contentDescription?.toString() ?: ""
        if ((t.equals(text, ignoreCase = true) || d.equals(text, ignoreCase = true)) && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val r = deepFind(node.getChild(i), text, depth + 1); if (r != null) return r
        }
        return null
    }

    private fun deepFindContains(node: AccessibilityNodeInfo?, kws: Array<out String>, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 30) return null
        val t = node.text?.toString()?.lowercase() ?: ""
        val d = node.contentDescription?.toString()?.lowercase() ?: ""
        for (kw in kws) {
            if ((t.contains(kw.lowercase()) || d.contains(kw.lowercase())) && node.isVisibleToUser) return node
        }
        for (i in 0 until node.childCount) {
            val r = deepFindContains(node.getChild(i), kws, depth + 1); if (r != null) return r
        }
        return null
    }

    private fun gestTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 120)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) = Log.d(TAG, "Tap OK ($x,$y)")
            override fun onCancelled(g: GestureDescription) = Log.d(TAG, "Tap cancelled ($x,$y)")
        }, null)
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var t = node; var i = 0
        while (!t.isClickable && t.parent != null && i < 8) { t = t.parent; i++ }
        return t.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun showToast(msg: String) {
        handler.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
        Log.d(TAG, msg)
    }
}
