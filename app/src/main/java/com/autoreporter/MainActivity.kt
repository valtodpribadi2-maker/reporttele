package com.autoreporter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private var countdown: CountDownTimer? = null

    private val doneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runOnUiThread {
                tvStatus.text = "✅ Laporan berhasil dikirim!\n\nTap MULAI untuk lapor channel lain."
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
                tvStatus.text = "⚠️ Aktifkan AutoReporter\ndi Settings → Aksesibilitas dulu!"
                openAccessibilitySettings()
                return@setOnClickListener
            }
            val svc = ReporterService.instance
            if (svc == null) {
                tvStatus.text = "❌ Service belum aktif\nBuka Aksesibilitas & aktifkan AutoReporter"
                openAccessibilitySettings()
                return@setOnClickListener
            }

            // Mulai countdown 5 detik
            // Selama countdown, user pindah ke Telegram & buka channel target
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            startCountdown(svc)
        }

        btnStop.setOnClickListener {
            countdown?.cancel()
            ReporterService.instance?.stopReporting()
            tvStatus.text = "⏹ Dihentikan"
            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }
    }

    private fun startCountdown(svc: ReporterService) {
        // Beri user 5 detik untuk pindah ke Telegram dan buka channel
        countdown = object : CountDownTimer(5000, 1000) {
            override fun onTick(ms: Long) {
                val sec = (ms / 1000) + 1
                tvStatus.text = "⏱ $sec detik...\n\nSEGERA buka Telegram\n& buka channel yang mau dilaporkan!\n\nApp akan auto-tap ⋮ → Laporkan\nsecara otomatis."
            }
            override fun onFinish() {
                tvStatus.text = "🤖 Berjalan otomatis...\nLihat Toast untuk progres"
                // Jalankan full sequence
                svc.runFullSequence()
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        if (!btnStart.isEnabled) return // sedang berjalan, jangan update status
        if (isAccessibilityEnabled()) {
            tvStatus.text = "✅ Siap!\n\nCara pakai:\n1. Tap MULAI\n2. Dalam 5 detik, buka Telegram\n3. Buka channel yang mau dilaporkan\n4. App otomatis tap ⋮ → Laporkan → selesai"
        } else {
            tvStatus.text = "⚠️ Belum aktif\n\nBuka Settings → Aksesibilitas\n→ AutoReporter → Aktifkan"
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
        countdown?.cancel()
        unregisterReceiver(doneReceiver)
    }
}
