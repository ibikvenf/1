package com.aegis.av.data

/** 威胁检测方式。 */
enum class ThreatType { SIGNATURE, HEURISTIC }

/** 威胁等级。 */
enum class ThreatLevel { MALWARE, HIGH, MEDIUM, LOW }

/**
 * 一个被检出的威胁。
 *  - 文件类威胁：path 非空
 *  - 应用类威胁：packageName 非空
 */
data class Threat(
    val id: Long,
    val title: String,
    val detail: String,
    val level: ThreatLevel,
    val type: ThreatType,
    val path: String? = null,
    val packageName: String? = null,
    val hash: String? = null,
) {
    /** 用于"忽略"白名单与去重的稳定 key。 */
    val key: String
        get() = packageName?.let { "pkg:$it:$title" }
            ?: path?.let { "file:$it:$title" }
            ?: "misc:$title:$detail"
}

/** 扫描进行中 UI 状态。 */
data class ScanUiState(
    val running: Boolean = false,
    val phase: String = "",
    val currentTarget: String = "",
    val scannedFiles: Int = 0,
    val scannedApps: Int = 0,
    val totalEstimate: Int = 0,
    val threatCount: Int = 0,
    val finished: Boolean = false,
    val cancelled: Boolean = false,
)

/** 一次扫描的结果汇总（会存入历史记录）。 */
data class ScanSummary(
    val startedAt: Long,
    val finishedAt: Long,
    val scannedFiles: Int,
    val scannedApps: Int,
    val threatCount: Int,
    val cancelled: Boolean,
)

/** 应用风险审计条目。 */
data class AppRisk(
    val packageName: String,
    val appName: String,
    val sourceApk: String,
    val isSystem: Boolean,
    val installer: String?,
    val score: Int,
    val level: ThreatLevel,
    val reasons: List<String>,
)
