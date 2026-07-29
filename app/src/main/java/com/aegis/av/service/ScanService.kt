package com.aegis.av.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aegis.av.R
import com.aegis.av.data.HistoryStore
import com.aegis.av.data.Prefs
import com.aegis.av.data.ScanSummary
import com.aegis.av.data.ScanUiState
import com.aegis.av.data.Threat
import com.aegis.av.engine.ScannerEngine
import com.aegis.av.ui.ResultsActivity
import com.aegis.av.util.Notify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 前台扫描服务：承载引擎运行、进度通知、结果落地。
 * UI 通过 [state]（StateFlow）观察；最近一次结果保存在 [lastThreats]/[lastSummary]。
 */
class ScanService : Service() {

    companion object {
        const val ACTION_QUICK = "com.aegis.av.action.SCAN_QUICK"
        const val ACTION_FULL = "com.aegis.av.action.SCAN_FULL"
        const val ACTION_STOP = "com.aegis.av.action.SCAN_STOP"

        val state = MutableStateFlow(ScanUiState())

        @Volatile var lastThreats: List<Threat> = emptyList()
        @Volatile var lastSummary: ScanSummary? = null

        fun startScan(context: Context, action: String) {
            val i = Intent(context, ScanService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, i)
        }

        fun stopScan(context: Context) {
            context.startService(Intent(context, ScanService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var engine: ScannerEngine? = null
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_QUICK -> begin(scanApps = true, roots = ScannerEngine.quickScanRoots())
            ACTION_FULL -> begin(scanApps = true, roots = ScannerEngine.fullScanRoots())
            ACTION_STOP -> {
                job?.cancel()
                engine?.requestCancel()
            }
        }
        return START_NOT_STICKY
    }

    private fun begin(scanApps: Boolean, roots: List<File>) {
        if (job?.isActive == true) return // 已有扫描进行中
        startForeground(Notify.ID_SCAN, progressNotification(getString(R.string.scan_preparing)))

        val eng = ScannerEngine(this)
        engine = eng
        job = scope.launch {
            try {
                val result = eng.start(scanApps, roots) { s ->
                    state.value = s
                    maybeNotifyProgress(s)
                }
                lastThreats = result.threats
                lastSummary = result.summary
                Prefs.lastScanTime = result.summary.finishedAt
                HistoryStore.add(applicationContext, result.summary)
                notifyFinished(result, cancelled = result.summary.cancelled)
            } catch (ce: CancellationException) {
                eng.requestCancel()
            } finally {
                engine = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun maybeNotifyProgress(s: ScanUiState) {
        val now = System.currentTimeMillis()
        if (now - lastNotify < 400) return
        lastNotify = now
        val n = progressNotification(
            text = s.currentTarget.takeLast(40),
            phase = s.phase,
            max = s.totalEstimate,
            progress = s.scannedFiles + s.scannedApps * 10,
        )
        getSystemService(android.app.NotificationManager::class.java)
            ?.notify(Notify.ID_SCAN, n)
    }

    private fun notifyFinished(result: ScannerEngine.Result, cancelled: Boolean) {
        val summary = result.summary
        val text = getString(
            R.string.scan_finished_fmt,
            summary.scannedApps, summary.scannedFiles, summary.threatCount,
        )
        // 结束通知（替换进度）
        val builder = NotificationCompat.Builder(this, Notify.CHANNEL_SCAN)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(if (cancelled) R.string.scan_cancelled else R.string.scan_finished))
            .setContentText(text)
            .setAutoCancel(true)
        if (result.threats.isNotEmpty()) {
            val pi = PendingIntent.getActivity(
                this, 0, Intent(this, ResultsActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setContentIntent(pi)
        }
        getSystemService(android.app.NotificationManager::class.java)
            ?.notify(Notify.ID_SCAN, builder.build())

        // 有威胁 -> 高优先级告警
        if (!cancelled && result.threats.isNotEmpty()) {
            val worst = result.threats.first()
            Notify.alert(
                this, Notify.ID_ALERT_BASE,
                getString(R.string.alert_threats_found_title),
                getString(R.string.alert_threats_found_fmt, result.threats.size, worst.title),
                Intent(this, ResultsActivity::class.java),
            )
        }
    }

    private fun progressNotification(
        text: String,
        phase: String = "",
        max: Int = 0,
        progress: Int = 0,
    ): Notification {
        val cancelPi = PendingIntent.getService(
            this, 1, Intent(this, ScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notify.CHANNEL_SCAN)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.scan_running_title))
            .setContentText(if (phase.isEmpty()) text else "$phase · $text")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(max, progress, max <= 0)
            .addAction(0, getString(R.string.cancel), cancelPi)
            .build()
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
