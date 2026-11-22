package com.autoreporter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReporterService : AccessibilityService() {

    private val TAG = "AutoReporter"
    private val handler = Handler(Looper.getMainLooper())

    private enum class State {
        IDLE,
        WAITING_ILLEGAL_GOODS,
        WAITING_DRUGS,
        WAITING_ILLEGAL_DRUGS,
        WAITING_LAPORKAN_FINAL,
        DONE
    }

    private var currentState = State.IDLE
    private var isRunning = false

    private val TARGET_TEXTS = mapOf(
        "laporkan_button"  to listOf("Laporkan", "Report"),
        "illegal_goods"    to listOf("Illegal goods and services"),
        "drugs"            to listOf("Drugs"),
        "illegal_drugs"    to listOf("Illegal drugs"),
        "final_report"     to listOf("Laporkan", "LAPORKAN 3 PESAN", "Report", "Send Report")
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning) return
        event ?: return
        val rootNode = rootInActiveWindow ?: return
        handler.postDelayed({ processState(rootNode) }, 600)
    }

    private fun processState(root: AccessibilityNodeInfo) {
        when {
            // IDLE atau WAITING_ILLEGAL_GOODS:
            // Cek langsung apakah "Illegal goods and services" sudah visible.
            // Kalau iya, klik dan lanjut — tidak perlu nunggu tombol Laporkan awal.
            currentState == State.IDLE || currentState == State.WAITING_ILLEGAL_GOODS -> {
                val illegalNode = findNodeByTexts(root, TARGET_TEXTS["illegal_goods"]!!)
                if (illegalNode != null) {
                    Log.d(TAG, "Langsung deteksi: Illegal goods and services")
                    clickNode(illegalNode)
                    currentState = State.WAITING_DRUGS
                    return
                }
                // Fallback: kalau menu belum kebuka, cari tombol Laporkan awal
                if (currentState == State.IDLE) {
                    val node = findNodeByTexts(root, TARGET_TEXTS["laporkan_button"]!!)
                    if (node != null) {
                        Log.d(TAG, "Ditemukan: Tombol Laporkan awal")
                        clickNode(node)
                        currentState = State.WAITING_ILLEGAL_GOODS
                    }
                }
            }

            currentState == State.WAITING_DRUGS -> {
                val node = findNodeByTexts(root, TARGET_TEXTS["drugs"]!!)
                if (node != null) {
                    Log.d(TAG, "Ditemukan: Drugs")
                    clickNode(node)
                    currentState = State.WAITING_ILLEGAL_DRUGS
                }
            }

            currentState == State.WAITING_ILLEGAL_DRUGS -> {
                val node = findNodeByTexts(root, TARGET_TEXTS["illegal_drugs"]!!)
                if (node != null) {
                    Log.d(TAG, "Ditemukan: Illegal drugs")
                    clickNode(node)
                    currentState = State.WAITING_LAPORKAN_FINAL
                }
            }

            currentState == State.WAITING_LAPORKAN_FINAL -> {
                val node = findNodeByTexts(root, TARGET_TEXTS["final_report"]!!)
                if (node != null) {
                    Log.d(TAG, "Submit laporan final")
                    clickNode(node)
                    currentState = State.DONE
                    isRunning = false
                    sendBroadcast(Intent("com.autoreporter.REPORT_DONE"))
                }
            }

            else -> {}
        }
    }

    private fun findNodeByTexts(
        root: AccessibilityNodeInfo,
        texts: List<String>
    ): AccessibilityNodeInfo? {
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isVisibleToUser &&
                        (node.isClickable || node.parent?.isClickable == true)) {
                        return node
                    }
                }
                return nodes.firstOrNull { it.isVisibleToUser }
            }
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        var target = node
        var attempts = 0
        while (!target.isClickable && target.parent != null && attempts < 5) {
            target = target.parent
            attempts++
        }
        val result = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "Klik: ${node.text} — hasil: $result")
    }

    fun startReporting() {
        currentState = State.IDLE
        isRunning = true
        Log.d(TAG, "AutoReporter DIMULAI")
    }

    fun stopReporting() {
        isRunning = false
        currentState = State.IDLE
    }

    override fun onInterrupt() {
        isRunning = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Service terhubung")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        var instance: ReporterService? = null
    }
}
