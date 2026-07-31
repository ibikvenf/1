package com.aegis.av.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.aegis.av.receiver.ScheduledScanReceiver
import java.util.Calendar

/**
 * 每日定时查杀调度器。
 * 采用 AlarmManager 不精确唤醒闹钟（无需精确闹钟权限、可被系统合并省电），
 * 设定在每天 03:00 左右触发一次全盘扫描。
 */
object ScanScheduler {

    private const val REQ_CODE = 42002

    fun schedule(context: Context) {
        val am = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next3Am(),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.applicationContext.getSystemService(AlarmManager::class.java)
            ?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context.applicationContext,
            REQ_CODE,
            Intent(context.applicationContext, ScheduledScanReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 下一个凌晨 3 点的时间戳。 */
    fun next3Am(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
