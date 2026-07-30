package com.aegis.av.data

import java.io.File

/**
 * 自定义黑名单库（ClamAV 系列未覆盖的两类高危特征）：
 * ```
 *   pkg:<包名前缀>           # 命中包名前缀即报毒（伪造银行客户端等）
 *   cert:<签名证书SHA-256>   # 命中证书指纹即报毒（木马家族常共用证书）
 * ```
 * 与签名库同目录合并加载，空文件亦可安全使用。
 */
class BlacklistDb {

    private val pkgRules = ArrayList<String>()
    private val certRules = HashSet<String>()

    val size: Int get() = pkgRules.size + certRules.size

    fun load(file: File) {
        if (!file.isFile) return
        runCatching {
            file.forEachLine { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                when {
                    line.startsWith("pkg:", ignoreCase = true) ->
                        line.substring(4).trim().takeIf { it.isNotEmpty() }?.let { pkgRules.add(it) }

                    line.startsWith("cert:", ignoreCase = true) ->
                        line.substring(5).trim().lowercase()
                            .takeIf { it.length == 64 }?.let { certRules.add(it) }
                }
            }
        }
    }

    /** 返回命中的包名前缀规则；未命中返回 null。 */
    fun matchPackage(pkg: String): String? = pkgRules.firstOrNull { pkg.startsWith(it) }

    fun matchCert(sha256: String?): Boolean =
        sha256 != null && sha256.lowercase() in certRules
}
