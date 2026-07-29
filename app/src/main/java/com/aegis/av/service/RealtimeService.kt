package com.aegis.av.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aegis.av.R
import com.aegis.av.data.Prefs
import com.aegis.av.engine.ScannerEngine
import com.aegis.av.ui.ResultsActivity
import com.aegis.av.util.Notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 实时防护：借鉴 Hypatia 的"递归 FileObserver"方案，
 * 监听共享存储上的文件写入/移动事件，落盘即时查杀。
 */
class RealtimeService : Service() {

    companion object {
        const val ACTION_STOP_GUARD = "com.aegis.av.action.STOP_GUARD"

        private const val MAX_DEPTH = 5

        // infix or 不是编译期常量表达式，因此用普通 val
        private val EVENT_MASK = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or
            FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
            FileObserver.DELETE_SELF or FileObserver.MOVE_SELF

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, RealtimeService::class.java)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RealtimeService::class.java).setAction(ACTION_STOP_GUARD)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val observers = ConcurrentHashMap<String, FileObserver>()
    private val engine by lazy { ScannerEngine(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_GUARD) {
            shutdown()
            return START_NOT_STICKY
        }
        Prefs.realtimeEnabled = true
        startForeground(Notify.ID_GUARD, guardNotification())
        scope.launch { watch(ScannerEngine.fullScanRoots()) }
        return START_STICKY
    }

    private fun shutdown() {
        observers.values.forEach { runCatching { it.stopWatching() } }
        observers.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ------------------------- 递归 FileObserver -------------------------

    private fun watch(roots: List<File>) {
        for (root in roots) attachRecursive(root, depth = 0)
    }

    private fun attachRecursive(dir: File, depth: Int) {
        if (depth > MAX_DEPTH || observers.size > 2048) return
        attach(dir)
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (c in children) {
            if (c.isDirectory && !isProtected(c)) attachRecursive(c, depth + 1)
        }
    }

    private fun attach(dir: File) {
        val path = dir.absolutePath
        if (observers.containsKey(path)) return
        val obs = createObserver(dir)
        observers[path] = obs
        runCatching { obs.startWatching() }
    }

    @Suppress("DEPRECATION")
    private fun createObserver(dir: File): FileObserver {
        return object : FileObserver(dir.absolutePath, EVENT_MASK) {
            override fun onEvent(event: Int, name: String?) {
                if (name == null) return
                val target = File(dir, name)
                val masked = event and FileObserver.ALL_EVENTS
                when {
                    masked and FileObserver.CREATE != 0 && target.isDirectory -> {
                        attachRecursive(target, 0)
                    }
                    masked and (FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO) != 0 -> {
                        if (target.isFile && !isProtected(target)) scanNow(target)
                    }
                    masked and (FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) != 0 -> {
                        observers.remove(target.absolutePath)?.stopWatching()
                    }
                }
            }
        }
    }

    private fun isProtected(f: File): Boolean {
        val p = f.absolutePath
        val root = Environment.getExternalStorageDirectory().absolutePath
        return p.startsWith("$root/Android/data") || p.startsWith("$root/Android/obb")
    }

    // ----------------------------- 即时查杀 -----------------------------

    private val recentlyScanned = LinkedHashMap<String, Long>()

    private fun scanNow(file: File) {
        // 去抖：同一文件 5 秒内只查一次
        val now = System.currentTimeMillis()
        synchronized(recentlyScanned) {
            val last = recentlyScanned[file.absolutePath] ?: 0L
            if (now - last < 5000) return
            recentlyScanned[file.absolutePath] = now
            if (recentlyScanned.size > 512) {
                recentlyScanned.entries.remove(recentlyScanned.entries.first())
            }
        }
        scope.launch scan@{
            val threat = runCatching { engine.scanFile(file) }.getOrNull() ?: return@scan
            ScanService.lastThreats = listOf(threat)
            Notify.alert(
                this@RealtimeService,
                Notify.ID_ALERT_BASE + (System.currentTimeMillis() % 1000).toInt(),
                getString(R.string.alert_realtime_title),
                getString(R.string.alert_realtime_fmt, threat.title, threat.path ?: ""),
                Intent(this@RealtimeService, ResultsActivity::class.java),
            )
        }
    }

    private fun guardNotification(): Notification {
        val stopPi = PendingIntent.getService(
            this, 2,
            Intent(this, RealtimeService::class.java).setAction(ACTION_STOP_GUARD),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notify.CHANNEL_GUARD)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.guard_running_title))
            .setContentText(getString(R.string.guard_running_text))
            .setOngoing(true)
            .addAction(0, getString(R.string.stop), stopPi)
            .build()
    }

    override fun onDestroy() {
        observers.values.forEach { runCatching { it.stopWatching() } }
        observers.clear()
        Prefs.realtimeEnabled = false
        scope.cancel()
        super.onDestroy()
    }
}
