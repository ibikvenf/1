package com.aegis.av.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aegis.av.R
import com.aegis.av.data.Prefs
import com.aegis.av.data.SignatureRepository
import com.aegis.av.databinding.ActivityMainBinding
import com.aegis.av.service.RealtimeService
import com.aegis.av.service.ScanService
import com.aegis.av.update.DatabaseUpdater
import com.aegis.av.util.Perms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var updating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // 扫描入口
        b.btnQuickScan.setOnClickListener { startScan(ScanService.ACTION_QUICK) }
        b.btnFullScan.setOnClickListener { startScan(ScanService.ACTION_FULL) }

        // 功能入口
        b.cardApps.setOnClickListener { open(AppsActivity::class.java) }
        b.cardQuarantine.setOnClickListener { open(QuarantineActivity::class.java) }
        b.cardHistory.setOnClickListener { open(HistoryActivity::class.java) }
        b.cardUpdate.setOnClickListener { open(UpdateActivity::class.java) }
        b.cardSettingsFeature.setOnClickListener { open(SettingsActivity::class.java) }

        // 实时防护开关的具体逻辑在 refreshStatus() 中绑定（避免重复回调）

        // 病毒库一键更新
        b.btnUpdateDb.setOnClickListener { updateDatabase() }

        // 存储权限提示条
        b.btnGrantStorage.setOnClickListener { Perms.requestStorageAccess(this) }

        // 未处理威胁 -> 处理页
        b.btnHandleThreat.setOnClickListener { open(ResultsActivity::class.java) }

        b.btnEicarHelp.setOnClickListener { showEicarDialog() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        maybeAutoUpdateDb()
    }

    private var autoUpdateChecked = false

    /** 开启"自动更新病毒库"且超过 24 小时未更新时静默更新一次。 */
    private fun maybeAutoUpdateDb() {
        if (autoUpdateChecked || updating) return
        autoUpdateChecked = true
        val stale = System.currentTimeMillis() - Prefs.dbLastUpdate > 24L * 3600_000L
        if (Prefs.autoUpdateDb && stale) {
            updateDatabase(silent = true)
        }
    }

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))

    private fun startScan(action: String) {
        if (!Perms.hasStorageAccess(this)) {
            Perms.requestStorageAccess(this)
        }
        if (!Perms.hasNotificationAccess(this)) {
            Perms.requestNotificationAccess(this)
        }
        ScanService.startScan(this, action)
        startActivity(Intent(this, ScanActivity::class.java))
    }

    private fun refreshStatus() {
        // 顶部保护状态
        val lastScan = Prefs.lastScanTime
        val realtime = Prefs.realtimeEnabled
        val storageOk = Perms.hasStorageAccess(this)

        val (icon, color, text) = when {
            !storageOk -> Triple(
                R.drawable.ic_shield_off, R.color.warn,
                getString(R.string.status_no_permission),
            )
            realtime && lastScan > 0 -> Triple(
                R.drawable.ic_shield, R.color.safe,
                getString(R.string.status_protected),
            )
            else -> Triple(
                R.drawable.ic_shield, R.color.warn,
                getString(
                    if (lastScan > 0) R.string.status_partial else R.string.status_first_run,
                ),
            )
        }
        b.imgStatus.setImageResource(icon)
        b.imgStatus.setColorFilter(ContextCompat.getColor(this, color))
        b.tvStatus.text = text

        // 权限提示条
        b.layoutPermBanner.visibility = if (storageOk) View.GONE else View.VISIBLE

        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        b.tvLastScan.text = if (lastScan > 0)
            getString(R.string.last_scan_fmt, df.format(Date(lastScan)))
        else getString(R.string.last_scan_never)

        // 病毒库信息与未处理威胁横幅（IO 操作放后台线程）
        val lastScanSeen = lastScan
        lifecycleScope.launch {
            val dbInfo = withContext(Dispatchers.IO) { SignatureRepository.info() }
            val entry = withContext(Dispatchers.IO) {
                com.aegis.av.data.HistoryStore.load(applicationContext).firstOrNull()
            }
            b.tvDbInfo.text = if (dbInfo.second > 0)
                getString(R.string.db_info_fmt, dbInfo.first, df.format(Date(dbInfo.second)))
            else getString(R.string.db_info_builtin_fmt, dbInfo.first)

            val openCount = entry?.summary
                ?.takeIf { it.threatCount > 0 && it.finishedAt == lastScanSeen }
                ?.threatCount ?: 0
            b.layoutThreatBanner.visibility = if (openCount > 0) View.VISIBLE else View.GONE
            if (openCount > 0) {
                b.tvThreatBanner.text = getString(R.string.banner_threats_fmt, openCount)
            }
        }

        b.switchRealtime.setOnCheckedChangeListener(null)
        b.switchRealtime.isChecked = realtime
        b.switchRealtime.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!Perms.hasNotificationAccess(this)) Perms.requestNotificationAccess(this)
                if (!Perms.hasStorageAccess(this)) {
                    Perms.requestStorageAccess(this)
                    b.switchRealtime.isChecked = false
                    return@setOnCheckedChangeListener
                }
                RealtimeService.start(this)
            } else {
                RealtimeService.stop(this)
            }
        }
    }

    private fun updateDatabase(silent: Boolean = false) {
        if (updating) return
        updating = true
        b.btnUpdateDb.isEnabled = false
        b.btnUpdateDb.text = getString(R.string.updating)
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                val out = ArrayList<DatabaseUpdater.UpdateResult>()
                DatabaseUpdater.updateAll(applicationContext) { out.add(it) }
                out
            }
            updating = false
            b.btnUpdateDb.isEnabled = true
            b.btnUpdateDb.text = getString(R.string.update_db)
            refreshStatus()
            if (!silent) {
                ResultsDialogs.dbUpdate(this@MainActivity, results)
            }
        }
    }

    private fun showEicarDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.eicar_title)
            .setMessage(R.string.eicar_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
