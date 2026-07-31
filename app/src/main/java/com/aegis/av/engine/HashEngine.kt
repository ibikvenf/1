package com.aegis.av.engine

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

/** 三种哈希值 + 数据大小。 */
class Hashes(
    val md5: String,
    val sha1: String,
    val sha256: String,
    val size: Long,
)

/**
 * 单遍哈希引擎 —— 借鉴 Hypatia/ClamAV 的思路：
 * 数据只读一遍，同时累积 MD5 / SHA-1 / SHA-256，最大程度减少磁盘 IO。
 */
object HashEngine {

    private const val BUFFER_SIZE = 1 shl 15 // 32 KiB

    fun ofFile(path: String): Hashes? = ofFile(File(path))

    fun ofFile(file: File): Hashes? {
        if (!file.isFile || !file.canRead()) return null
        return runCatching { FileInputStream(file).use { ofStream(it) } }.getOrNull()
    }

    /** 从任意流读取（content:// Uri 分享查杀时无需落盘）。 */
    fun ofStream(ins: InputStream): Hashes? {
        return runCatching {
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            val sha256 = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md5.update(buf, 0, n)
                sha1.update(buf, 0, n)
                sha256.update(buf, 0, n)
                size += n
            }
            Hashes(md5.hex(), sha1.hex(), sha256.hex(), size)
        }.getOrNull()
    }

    fun ofBytes(bytes: ByteArray): Hashes {
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        md5.update(bytes); sha1.update(bytes); sha256.update(bytes)
        return Hashes(md5.hex(), sha1.hex(), sha256.hex(), bytes.size.toLong())
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).hex()

    // ------------------------------------------------------------------

    private val HEX = "0123456789abcdef".toCharArray()

    private fun ByteArray.hex(): String {
        val r = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt()
            r[i * 2] = HEX[(v shr 4) and 0xF]
            r[i * 2 + 1] = HEX[v and 0xF]
        }
        return String(r)
    }

    private fun MessageDigest.hex(): String = digest().hex()
}
