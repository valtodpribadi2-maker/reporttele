package com.autoreporter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val doneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runOnUiThread {
                tvStatus.text = "✅ Laporan berhasil dikirim!"
                btnStart.isEnabled = true
                btnStop.isEnabled = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop  = findViewById(R.id.btnStop)

        registerReceiver(doneReceiver, IntentFilter("com.autoreporter.REPORT_DONE"))

        btnStart.setOnClickListener {
            if (!isAccessibilityEnabled()) {
                tvStatus.text = "⚠️ Aktifkan AutoReporter di Aksesibilitas dulu!"
                openAccessibilitySettings()
                return@setOnClickListener
            }
            val svc = ReporterService.instance
            if (svc != null) {
                svc.startReporting()
                tvStatus.text = "🔍 Berjalan...\n\n1. Buka Telegram\n2. Buka channel yang mau dilaporkan\n3. Tap ⋮ pojok kanan atas\n4. App otomatis klik sisanya"
                btnStart.isEnabled = false
                btnStop.isEnabled = true
            } else {
                tvStatus.text = "❌ Service belum aktif, buka Aksesibilitas"
                openAccessibilitySettings()
            }
        }

        btnStop.setOnClickListener {
            ReporterService.instance?.stopReporting()
            tvStatus.text = "⏹ Dihentikan"
            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAccessibilityEnabled()) {
            tvStatus.text = "✅ Siap digunakan.\nTap MULAI lalu buka Telegram."
        } else {
            tvStatus.text = "⚠️ Aktifkan AutoReporter\ndi Settings → Aksesibilitas"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/${ReporterService::class.java.canonicalName}"
        return try {
            val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
            if (enabled == 1) {
                val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                services?.split(":")?.any { it.equals(service, ignoreCase = true) } == true
            } else false
        } catch (e: Exception) { false }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(doneReceiver)
    }
}
