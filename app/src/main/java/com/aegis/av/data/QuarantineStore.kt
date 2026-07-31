package com.aegis.av.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 隔离区：把可疑文件移入应用私有目录并加 .quar 后缀，
 * 记录原始位置，可还原或彻底删除。
 */
object QuarantineStore {

    private const val INDEX = "quarantine_index.json"

    data class Entry(
        val id: String,
        val originalPath: String,
        val quarantinedPath: String,
        val threatName: String,
        val time: Long,
    )

    private val gson = Gson()

    private fun dir(context: Context): File =
        File(context.filesDir, "quarantine").apply { mkdirs() }

    private fun indexFile(context: Context): File = File(dir(context), INDEX)

    @Synchronized
    fun list(context: Context): List<Entry> {
        val f = indexFile(context)
        if (!f.isFile) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<Entry>>() {}.type
            gson.fromJson<List<Entry>>(f.readText(), type)?.filter { File(it.quarantinedPath).isFile }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun save(context: Context, entries: List<Entry>) {
        runCatching { indexFile(context).writeText(gson.toJson(entries)) }
    }

    /** 隔离一个文件。成功返回 Entry，失败返回 null。 */
    @Synchronized
    fun quarantine(context: Context, sourcePath: String, threatName: String): Entry? {
        val src = File(sourcePath)
        if (!src.isFile) return null
        val id = "${System.currentTimeMillis()}_${src.name.hashCode()}"
        val dest = File(dir(context), "$id.quar")

        // 同一文件系统可快速 rename，否则复制 + 删除。
        val moved = runCatching { src.renameTo(dest) }.getOrDefault(false) ||
            runCatching {
                src.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                src.delete()
            }.getOrDefault(false)
        if (!moved) return null

        val entry = Entry(id, sourcePath, dest.absolutePath, threatName, System.currentTimeMillis())
        save(context, list(context) + entry)
        return entry
    }

    @Synchronized
    fun restore(context: Context, id: String): Boolean {
        val entries = list(context)
        val entry = entries.firstOrNull { it.id == id } ?: return false
        val src = File(entry.quarantinedPath)
        val dest = File(entry.originalPath)
        val ok = runCatching {
            dest.parentFile?.mkdirs()
            src.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            src.delete()
        }.getOrDefault(false)
        if (ok) save(context, entries - entry)
        return ok
    }

    @Synchronized
    fun delete(context: Context, id: String): Boolean {
        val entries = list(context)
        val entry = entries.firstOrNull { it.id == id } ?: return false
        File(entry.quarantinedPath).delete()
        save(context, entries - entry)
        return true
    }
}
