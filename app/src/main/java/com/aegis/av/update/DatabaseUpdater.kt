package com.aegis.av.update

import android.content.Context
import android.os.SystemClock
import com.aegis.av.data.Prefs
import com.aegis.av.data.SignatureRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * 病毒库在线更新器。
 * 借鉴 Hypatia 的做法：带 If-Modified-Since 条件请求，304 时跳过下载；
 * 先落临时文件再原子替换，避免半成品库；仅接受 .hsb/.hdb 内容。
 */
object DatabaseUpdater {

    data class UpdateResult(val source: String, val status: Status, val message: String = "")
    enum class Status { UPDATED, NOT_MODIFIED, FAILED, SKIPPED }

    private const val TIMEOUT_MS = 20_000

    /** 顺序更新所有启用源，逐源回调进度。必须在 IO 线程调用。 */
    fun updateAll(
        context: Context,
        onProgress: (UpdateResult) -> Unit = {},
    ) {
        val sources = SignatureRepository.sources()
        for (src in sources) {
            val result: UpdateResult = if (!src.enabled) {
                UpdateResult(src.name, Status.SKIPPED)
            } else {
                updateOne(context, src)
            }
            onProgress(result)
        }
        SignatureRepository.saveSources(sources)
        SignatureRepository.invalidate()
        Prefs.dbLastUpdate = System.currentTimeMillis()
    }

    private fun updateOne(context: Context, src: SignatureRepository.DbSource): UpdateResult {
        val fileName = "dl_" + src.url.hashCode().toString(16) +
            if (src.url.endsWith(".hdb")) ".hdb" else ".hsb"
        val out = File(context.filesDir, "signatures/$fileName")
        val tmp = File(out.absolutePath + ".tmp")

        return runCatching {
            val conn = (URI(src.url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "AegisAV/1.0")
                if (src.lastModified.isNotEmpty() && out.exists()) {
                    setRequestProperty("If-Modified-Since", src.lastModified)
                }
            }
            try {
                when (conn.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED ->
                        UpdateResult(src.name, Status.NOT_MODIFIED)

                    HttpURLConnection.HTTP_OK -> {
                        out.parentFile?.mkdirs()
                        conn.inputStream.use { ins ->
                            tmp.outputStream().use { ins.copyTo(it) }
                        }
                        if (tmp.length() <= 0L || !looksLikeSigDb(tmp)) {
                            tmp.delete()
                            UpdateResult(src.name, Status.FAILED, "格式无效")
                        } else {
                            if (out.exists()) out.delete()
                            tmp.renameTo(out)
                            conn.getHeaderField("Last-Modified")?.let { src.lastModified = it }
                            UpdateResult(src.name, Status.UPDATED, "${tmpBytesHuman(out)}")
                        }
                    }

                    else -> UpdateResult(src.name, Status.FAILED, "HTTP ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
        }.getOrElse {
            tmp.delete()
            UpdateResult(src.name, Status.FAILED, it.message ?: it.javaClass.simpleName)
        }
    }

    private fun looksLikeSigDb(f: File): Boolean {
        // 抽样前几行验证 "hex:size:name" 形态
        return runCatching {
            f.bufferedReader().use { br ->
                repeat(5) {
                    val line = br.readLine() ?: return@repeat
                    if (line.isBlank() || line.startsWith("#")) return@repeat
                    val parts = line.trim().split(":", limit = 3)
                    if (parts.size < 3) return false
                    if (!parts[0].all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' }) return false
                }
                true
            }
        }.getOrDefault(false)
    }

    private fun tmpBytesHuman(f: File): String {
        val kb = f.length() / 1024
        return if (kb > 1024) "${kb / 1024} MB" else "$kb KB"
    }
}
