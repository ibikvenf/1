package com.aegis.av.data

import android.content.Context
import android.os.SystemClock
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 签名库仓库：
 *  - 首次启动把 assets 中的内置签名库(.hsb/.hdb) 复制到内部存储
 *  - 下载的签名库同样保存在内部存储
 *  - 扫描前统一合并加载（带修改时间缓存，未变化则秒开）
 */
object SignatureRepository {

    data class DbSource(
        val name: String,
        val url: String,
        var enabled: Boolean = true,
        var lastModified: String = "",
    )

    private const val ASSET_DIR = "signatures"
    private const val KEY_CACHE_MS = 5_000L

    private lateinit var app: Context
    private val gson = Gson()

    @Volatile
    private var cached: SignatureDatabase? = null
    private var cacheStamp = 0L

    @Volatile
    private var cachedBlacklist: BlacklistDb? = null
    private var blackStamp = 0L

    fun init(context: Context) {
        app = context.applicationContext
        copyBundledIfNeeded()
    }

    private fun dbDir(): File = File(app.filesDir, "signatures").apply { mkdirs() }

    private fun copyBundledIfNeeded() {
        runCatching {
            val names = app.assets.list(ASSET_DIR) ?: return
            for (name in names) {
                val isSig = name.endsWith(".hsb") || name.endsWith(".hdb")
                val isBlacklist = name == "blacklist.txt"
                if (!isSig && !isBlacklist) continue
                val out = File(dbDir(), "builtin_$name")
                if (out.exists() && out.length() > 0) continue
                app.assets.open("$ASSET_DIR/$name").use { ins ->
                    out.outputStream().use { ins.copyTo(it) }
                }
            }
        }
    }

    /** 加载/合并全部签名库。结果带 5 秒缓存，供引擎反复调用。 */
    @Synchronized
    fun load(): SignatureDatabase {
        val now = SystemClock.elapsedRealtime()
        cached?.let { if (now - cacheStamp < KEY_CACHE_MS) return it }
        val db = SignatureDatabase()
        dbDir().listFiles()?.sortedBy { it.name }?.forEach { f ->
            if (f.name.endsWith(".hsb") || f.name.endsWith(".hdb")) {
                runCatching { db.load(f) }
            }
        }
        cached = db
        cacheStamp = now
        return db
    }

    /** 加载黑名单库（包名前缀 + 证书指纹），同样带 5 秒缓存。 */
    @Synchronized
    fun blacklist(): BlacklistDb {
        val now = SystemClock.elapsedRealtime()
        cachedBlacklist?.let { if (now - blackStamp < KEY_CACHE_MS) return it }
        val db = BlacklistDb()
        dbDir().listFiles()?.forEach { f ->
            if (f.name.endsWith("blacklist.txt")) runCatching { db.load(f) }
        }
        cachedBlacklist = db
        blackStamp = now
        return db
    }

    fun invalidate() {
        synchronized(this) {
            cacheStamp = 0L
            blackStamp = 0L
        }
    }

    fun info(): Pair<Int, Long> = load().signatureCount to Prefs.dbLastUpdate

    /** 病毒库下载源，可增删改，持久化为 JSON。 */
    fun sources(): MutableList<DbSource> {
        Prefs.dbSourcesJson?.let { json ->
            runCatching {
                val type = object : TypeToken<MutableList<DbSource>>() {}.type
                return gson.fromJson<MutableList<DbSource>>(json, type) ?: defaultSources()
            }
        }
        return defaultSources()
    }

    fun saveSources(list: List<DbSource>) {
        Prefs.dbSourcesJson = gson.toJson(list)
    }

    private fun defaultSources(): MutableList<DbSource> = mutableListOf(
        // ClamAV 兼容格式 (.hsb/.hdb) 即可，用户可自行替换为自托管的签名地址。
        DbSource(
            name = "Android Malware Bazaar（示例源，可自行替换为内网/自建签名地址）",
            url = "https://example.com/aegis/signatures/android.hsb",
            enabled = false,
        ),
    )
}
