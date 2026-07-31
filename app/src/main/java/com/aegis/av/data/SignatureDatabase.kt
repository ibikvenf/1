package com.aegis.av.data

import com.aegis.av.engine.Hashes
import java.io.BufferedReader
import java.io.File
import java.io.InputStream

/**
 * ClamAV 风格本地签名库（格式与 Hypatia 相同，全部离线，不上传任何文件哈希）：
 *
 *   .hdb  ->  MD5哈希:文件大小:恶意软件名
 *   .hsb  ->  SHA1/SHA256哈希:文件大小:恶意软件名
 *
 * 文件大小可为 "*" 表示任意大小。内部使用三个 HashMap 实现 O(1) 查询。
 * 加载完成后视为不可变，可在任意线程安全读取。
 */
class SignatureDatabase {

    data class Sig(val name: String, val size: Long /* -1 表示任意大小 */)

    private val md5Map = HashMap<String, ArrayList<Sig>>()
    private val sha1Map = HashMap<String, ArrayList<Sig>>()
    private val sha256Map = HashMap<String, ArrayList<Sig>>()

    var signatureCount: Int = 0
        private set

    /** 从文件加载（按扩展名推断 .hdb/.hsb）。 */
    fun load(file: File) {
        if (!file.isFile) return
        val isHdb = file.name.endsWith(".hdb", ignoreCase = true)
        file.bufferedReader().use { load(it, isHdb) }
    }

    fun load(stream: InputStream, isHdb: Boolean) {
        stream.bufferedReader().use { load(it, isHdb) }
    }

    private fun load(reader: BufferedReader, isHdb: Boolean) {
        reader.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            val parts = line.split(":", limit = 3)
            if (parts.size < 3) return@forEachLine
            val hash = parts[0].lowercase()
            val size = parts[1].toLongOrNull() ?: if (parts[1] == "*") -1L else return@forEachLine
            val name = parts[2].trim()
            if (name.isEmpty()) return@forEachLine

            val sig = Sig(name, size)
            when {
                isHdb && hash.length == 32 -> md5Map.put(hash, sig)
                !isHdb && hash.length == 40 -> sha1Map.put(hash, sig)
                !isHdb && hash.length == 64 -> sha256Map.put(hash, sig)
                else -> return@forEachLine
            }
            signatureCount++
        }
    }

    private fun HashMap<String, ArrayList<Sig>>.put(key: String, sig: Sig) {
        getOrPut(key) { ArrayList(1) }.add(sig)
    }

    /** 命中则返回恶意软件名，未命中返回 null。SHA-256 优先（最可靠）。 */
    fun lookup(h: Hashes): String? {
        sha256Map.match(h.sha256, h.size)?.let { return it }
        sha1Map.match(h.sha1, h.size)?.let { return it }
        md5Map.match(h.md5, h.size)?.let { return it }
        return null
    }

    private fun HashMap<String, ArrayList<Sig>>.match(hash: String, size: Long): String? {
        val list = get(hash) ?: return null
        for (sig in list) {
            if (sig.size < 0 || sig.size == size) return sig.name
        }
        return null
    }
}
