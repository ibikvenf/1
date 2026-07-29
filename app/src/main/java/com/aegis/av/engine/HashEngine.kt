package com.aegis.av.engine

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** 三种哈希值 + 文件大小。 */
class Hashes(
    val md5: String,
    val sha1: String,
    val sha256: String,
    val size: Long,
)

/**
 * 单遍哈希引擎 —— 借鉴 Hypatia/ClamAV 的思路：
 * 文件只读一遍，同时累积 MD5 / SHA-1 / SHA-256，最大程度减少磁盘 IO。
 */
object HashEngine {

    private const val BUFFER_SIZE = 1 shl 15 // 32 KiB

    fun ofFile(path: String): Hashes? = ofFile(File(path))

    fun ofFile(file: File): Hashes? {
        if (!file.isFile || !file.canRead()) return null
        return runCatching {
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            val sha256 = MessageDigest.getInstance("SHA-256")
            var size = 0L
            FileInputStream(file).use { ins ->
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    md5.update(buf, 0, n)
                    sha1.update(buf, 0, n)
                    sha256.update(buf, 0, n)
                    size += n
                }
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

    private fun MessageDigest.hex(): String {
        val out = StringBuilder(64)
        for (b in digest()) out.append(((b.toInt() shr 4) and 0xF).toString(16)).append((b.toInt() and 0xF).toString(16))
        return out.toString()
    }
}
