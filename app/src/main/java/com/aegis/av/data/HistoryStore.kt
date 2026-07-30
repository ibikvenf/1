package com.aegis.av.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** 扫描历史（JSON 文件存储，最多保留 50 条；含威胁快照便于回看）。 */
object HistoryStore {

    private const val FILE_NAME = "scan_history.json"
    private const val MAX_ENTRIES = 50
    private const val MAX_THREATS_PER_ENTRY = 100

    /** summary/threats 均可空，兼容旧版本纯摘要格式安全解析。 */
    data class HistoryEntry(
        val summary: ScanSummary? = null,
        val threats: List<Threat>? = null,
    )

    private val gson = Gson()

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun add(context: Context, summary: ScanSummary, threats: List<Threat> = emptyList()) {
        val list = load(context).toMutableList()
        list.add(0, HistoryEntry(summary, threats.take(MAX_THREATS_PER_ENTRY)))
        while (list.size > MAX_ENTRIES) list.removeAt(list.size - 1)
        runCatching { file(context).writeText(gson.toJson(list)) }
    }

    @Synchronized
    fun load(context: Context): List<HistoryEntry> {
        val f = file(context)
        if (!f.isFile) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<HistoryEntry>>() {}.type
            (gson.fromJson<List<HistoryEntry>>(f.readText(), type) ?: emptyList())
                .filter { it.summary != null }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }
}
