package com.aegis.av.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aegis.av.data.Prefs
import com.aegis.av.service.RealtimeService
import com.aegis.av.util.ScanScheduler

/** 开机 / 应用自身升级后，恢复实时防护与每日定时查杀。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (Prefs.realtimeEnabled) {
                    RealtimeService.start(context)
                }
                if (Prefs.autoScanDaily) {
                    ScanScheduler.schedule(context)
                }
            }
        }
    }
}
