package com.aegis.av.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** 扫描历史（JSON 文件存储，最多保留 50 条）。 */
object HistoryStore {

    private const val FILE_NAME = "scan_history.json"
    private const val MAX_ENTRIES = 50

    private val gson = Gson()

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun add(context: Context, summary: ScanSummary) {
        val list = load(context).toMutableList()
        list.add(0, summary)
        while (list.size > MAX_ENTRIES) list.removeAt(list.size - 1)
        runCatching { file(context).writeText(gson.toJson(list)) }
    }

    @Synchronized
    fun load(context: Context): List<ScanSummary> {
        val f = file(context)
        if (!f.isFile) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<ScanSummary>>() {}.type
            gson.fromJson<List<ScanSummary>>(f.readText(), type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }
}
