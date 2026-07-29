package com.aegis.av.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aegis.av.R

/**
 * 统一的通知工具：
 *  - CHANNEL_SCAN   : 扫描进度（低打扰）
 *  - CHANNEL_GUARD  : 实时防护常驻（最小打扰）
 *  - CHANNEL_ALERTS : 威胁告警（高优先级，响铃震动）
 */
object Notify {

    const val CHANNEL_SCAN = "scan"
    const val CHANNEL_GUARD = "guard"
    const val CHANNEL_ALERTS = "alerts"

    const val ID_SCAN = 1001
    const val ID_GUARD = 1002
    const val ID_ALERT_BASE = 2000

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SCAN, context.getString(R.string.channel_scan), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_scan_desc)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_GUARD, context.getString(R.string.channel_guard), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_alerts_desc)
                enableVibration(true)
            }
        )
    }

    fun alert(
        context: Context,
        id: Int,
        title: String,
        text: String,
        tapIntent: Intent? = null,
    ) {
        ensureChannels(context)
        val pi = tapIntent?.let {
            PendingIntent.getActivity(
                context, id, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val n: Notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply { if (pi != null) setContentIntent(pi) }
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, n)
    }
}
