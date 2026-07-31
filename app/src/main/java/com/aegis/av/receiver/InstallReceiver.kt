package com.aegis.av.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aegis.av.R
import com.aegis.av.engine.ScannerEngine
import com.aegis.av.service.ScanService
import com.aegis.av.ui.ResultsActivity
import com.aegis.av.util.Notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 新应用安装 / 更新完成时立即查杀其 APK。
 * ACTION_PACKAGE_ADDED 在 Android 8.0+ 的隐式广播豁免名单内，清单注册可正常收到。
 */
class InstallReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return // 不查自己

        val pending = goAsync()
        scope.launch {
            try {
                val threat = ScannerEngine(context).scanPackage(pkg) ?: return@launch
                ScanService.lastThreats = listOf(threat)
                Notify.alert(
                    context, Notify.ID_ALERT_BASE + pkg.hashCode() % 500,
                    context.getString(R.string.alert_new_app_title),
                    context.getString(R.string.alert_new_app_fmt, pkg, threat.title),
                    Intent(context, ResultsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } finally {
                pending.finish()
            }
        }
    }
}
