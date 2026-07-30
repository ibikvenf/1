package com.aegis.av.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aegis.av.data.Prefs
import com.aegis.av.service.ScanService

/** 定时闹钟触发：执行一次全盘查杀（设置开启时）。 */
class ScheduledScanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.autoScanDaily) {
            ScanService.startScan(context, ScanService.ACTION_FULL)
        }
    }
}
