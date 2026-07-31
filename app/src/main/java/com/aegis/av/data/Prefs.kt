package com.aegis.av.data

import android.content.Context
import android.content.SharedPreferences

/** 轻量设置存储（SharedPreferences 封装）。 */
object Prefs {

    private const val NAME = "aegis_prefs"

    private const val KEY_REALTIME = "realtime_enabled"
    private const val KEY_HEURISTICS = "heuristics_enabled"
    private const val KEY_AUTO_UPDATE = "auto_update_db"
    private const val KEY_SCAN_SYSTEM_APPS = "scan_system_apps"
    private const val KEY_MAX_FILE_MB = "max_file_mb"
    private const val KEY_LAST_SCAN = "last_scan_time"
    private const val KEY_IGNORED = "ignored_threats"
    private const val KEY_DB_UPDATED = "db_last_update"
    private const val KEY_SOURCES = "db_sources"
    private const val KEY_INCREMENTAL = "incremental_quick"
    private const val KEY_DAILY_SCAN = "daily_auto_scan"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    private val p: SharedPreferences get() = prefs

    var realtimeEnabled: Boolean
        get() = p.getBoolean(KEY_REALTIME, false)
        set(v) = p.edit().putBoolean(KEY_REALTIME, v).apply()

    var heuristicsEnabled: Boolean
        get() = p.getBoolean(KEY_HEURISTICS, true)
        set(v) = p.edit().putBoolean(KEY_HEURISTICS, v).apply()

    var autoUpdateDb: Boolean
        get() = p.getBoolean(KEY_AUTO_UPDATE, false)
        set(v) = p.edit().putBoolean(KEY_AUTO_UPDATE, v).apply()

    var scanSystemApps: Boolean
        get() = p.getBoolean(KEY_SCAN_SYSTEM_APPS, false)
        set(v) = p.edit().putBoolean(KEY_SCAN_SYSTEM_APPS, v).apply()

    /** 快速扫描是否仅检查上次扫描后发生变化的文件。 */
    var incrementalQuick: Boolean
        get() = p.getBoolean(KEY_INCREMENTAL, true)
        set(v) = p.edit().putBoolean(KEY_INCREMENTAL, v).apply()

    /** 每天凌晨自动全盘查杀（AlarmManager 不精确闹钟，省电不打扰）。 */
    var autoScanDaily: Boolean
        get() = p.getBoolean(KEY_DAILY_SCAN, false)
        set(v) = p.edit().putBoolean(KEY_DAILY_SCAN, v).apply()

    /** 超过该大小的文件跳过（避免超大文件拖慢扫描）。 */
    var maxFileMb: Int
        get() = p.getInt(KEY_MAX_FILE_MB, 512)
        set(v) = p.edit().putInt(KEY_MAX_FILE_MB, v).apply()

    var lastScanTime: Long
        get() = p.getLong(KEY_LAST_SCAN, 0L)
        set(v) = p.edit().putLong(KEY_LAST_SCAN, v).apply()

    var dbLastUpdate: Long
        get() = p.getLong(KEY_DB_UPDATED, 0L)
        set(v) = p.edit().putLong(KEY_DB_UPDATED, v).apply()

    /** 用户选择"忽略"的威胁 key 集合（签名名 或 包名 或 路径）。 */
    var ignoredThreats: Set<String>
        get() = p.getStringSet(KEY_IGNORED, emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet(KEY_IGNORED, HashSet(v)).apply()

    fun ignore(key: String) {
        ignoredThreats = ignoredThreats + key
    }

    var dbSourcesJson: String?
        get() = p.getString(KEY_SOURCES, null)
        set(v) = p.edit().putString(KEY_SOURCES, v).apply()
}
