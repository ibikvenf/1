package com.aegis.av.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aegis.av.R
import com.aegis.av.databinding.ActivityScanBinding
import com.aegis.av.service.ScanService
import kotlinx.coroutines.launch

class ScanActivity : AppCompatActivity() {

    private lateinit var b: ActivityScanBinding
    private var viewedResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityScanBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.btnCancel.setOnClickListener { ScanService.stopScan(this) }
        b.btnViewResults.setOnClickListener {
            viewedResult = true
            startActivity(Intent(this, ResultsActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ScanService.state.collect { s -> render(s) }
            }
        }
    }

    private var lastSampleTime = 0L
    private var lastSampleTotal = 0

    /** 每秒一次的吞吐估算（应用×10 + 文件数）。 */
    private fun speedSuffix(s: com.aegis.av.data.ScanUiState): String {
        if (!s.running) {
            lastSampleTime = 0L
            lastSampleTotal = 0
            return ""
        }
        val now = System.currentTimeMillis()
        val total = s.scannedFiles + s.scannedApps * 10
        if (lastSampleTime == 0L || now - lastSampleTime < 1000) {
            if (lastSampleTime == 0L) {
                lastSampleTime = now
                lastSampleTotal = total
            }
            return ""
        }
        val speed = (total - lastSampleTotal).toLong() * 1000 / (now - lastSampleTime)
        lastSampleTime = now
        lastSampleTotal = total
        return getString(R.string.scan_speed_fmt, speed)
    }

    private fun render(s: com.aegis.av.data.ScanUiState) {
        if (!s.running && !s.finished) return // 尚无扫描

        b.tvPhase.text = when {
            s.finished && s.cancelled -> getString(R.string.scan_cancelled)
            s.finished -> getString(R.string.scan_finished)
            s.phase.isNotEmpty() -> s.phase
            else -> getString(R.string.scan_preparing)
        }

        b.tvCurrent.text = s.currentTarget
        b.tvCounts.text = getString(
            R.string.scan_counts_fmt,
            s.scannedApps, s.scannedFiles, s.threatCount,
        ) + speedSuffix(s)

        if (s.totalEstimate > 0) {
            b.progress.isIndeterminate = false
            b.progress.max = s.totalEstimate
            b.progress.progress = (s.scannedFiles + s.scannedApps * 10).coerceAtMost(s.totalEstimate)
        } else {
            b.progress.isIndeterminate = s.running
        }

        b.btnCancel.isEnabled = s.running
        b.groupFinished.visibility = if (s.finished) View.VISIBLE else View.GONE
        b.btnViewResults.text = getString(R.string.view_results_with_count, s.threatCount)

        // 扫描完成且无威胁：自动回退到主页，避免多余点击
        if (s.finished && s.threatCount == 0 && !s.cancelled && !viewedResult) {
            b.tvAutoClose.visibility = View.VISIBLE
        }
    }
}
