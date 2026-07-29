package com.aegis.av.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aegis.av.data.Prefs
import com.aegis.av.service.RealtimeService

/** 开机 / 应用自身升级后，若实时防护处于开启状态则自动恢复。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (Prefs.realtimeEnabled) {
                    RealtimeService.start(context)
                }
            }
        }
    }
}
