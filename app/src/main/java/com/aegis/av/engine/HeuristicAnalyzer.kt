package com.aegis.av.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * 启发式风险分析：
 * 对无法匹配签名的应用，根据"危险权限组合 + 安装来源"计算风险分。
 * 借鉴了商业杀软的 App Advisor / Privacy Audit 思路。
 */
object HeuristicAnalyzer {

    const val LEVEL_HIGH = 45
    const val LEVEL_MEDIUM = 25

    /** 权限权重表（自研规则）。 */
    private val PERM_WEIGHTS: Map<String, Pair<Int, String>> = mapOf(
        "android.permission.SYSTEM_ALERT_WINDOW" to (15 to "悬浮窗权限（常见于覆盖层攻击）"),
        "android.permission.REQUEST_INSTALL_PACKAGES" to (15 to "可安装其他应用"),
        "android.permission.SEND_SMS" to (15 to "可发送短信（话费类木马常见）"),
        "android.permission.READ_SMS" to (12 to "可读取短信（验证码窃取风险）"),
        "android.permission.RECEIVE_SMS" to (8 to "可拦截短信"),
        "android.permission.RECORD_AUDIO" to (12 to "可录音"),
        "android.permission.CAMERA" to (8 to "可使用相机"),
        "android.permission.READ_CONTACTS" to (8 to "可读取通讯录"),
        "android.permission.READ_CALL_LOG" to (10 to "可读取通话记录"),
        "android.permission.PROCESS_OUTGOING_CALLS" to (10 to "可监控拨出电话"),
        "android.permission.ACCESS_FINE_LOCATION" to (8 to "可获取精确位置"),
        "android.permission.WRITE_SETTINGS" to (10 to "可修改系统设置"),
        "android.permission.READ_PHONE_STATE" to (6 to "可读取设备标识"),
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to (18 to "声明无障碍服务组件（高危，常被远控木马滥用）"),
        "android.permission.WAKE_LOCK" to (2 to "保持唤醒"),
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to (6 to "要求忽略电池优化"),
        "android.permission.FOREGROUND_SERVICE" to (2 to "可常驻前台服务"),
    )

    /** 常见可信应用商店。 */
    private val TRUSTED_INSTALLERS = setOf(
        "com.android.vending",            // Google Play
        "com.google.android.feedback",
        "com.huawei.appmarket",           // 华为应用市场
        "com.xiaomi.market",              // 小米商店
        "com.heytap.market",              // OPPO
        "com.bbk.appstore",               // vivo
        "com.tencent.android.qqdownloader", // 应用宝
        "com.qihoo.appstore",             // 360 手机助手
        "com.pp.assistant",               // PP助手
        "com.wandoujia.phoenix2",         // 豌豆荚
        "com.samsung.android.apps.samsungapps", // Galaxy Store
        "com.amazon.venezia",             // Amazon
        "org.fdroid.fdroid",              // F-Droid
    )

    data class Verdict(val score: Int, val reasons: List<String>)

    fun analyze(
        context: Context,
        pkg: PackageInfo,
        appInfo: ApplicationInfo,
    ): Verdict {
        var score = 0
        val reasons = ArrayList<String>()

        val requested = pkg.requestedPermissions?.toSet() ?: emptySet()
        for (perm in requested) {
            PERM_WEIGHTS[perm]?.let { (w, why) ->
                score += w
                reasons.add("+$w $why")
            }
        }

        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val installer = installerOf(context.packageManager, pkg.packageName)
        if (!isSystem) {
            if (installer == null) {
                score += 12
                reasons.add("+12 安装来源未知（侧载应用）")
            } else if (installer !in TRUSTED_INSTALLERS) {
                score += 8
                reasons.add("+8 非知名商店安装：$installer")
            }
        }

        // 危险组合加成：同时具备"读短信+联网+装包/悬浮窗"属于典型木马画像
        if (requested.containsAll(listOf("android.permission.READ_SMS", "android.permission.INTERNET")) &&
            (requested.contains("android.permission.REQUEST_INSTALL_PACKAGES") ||
                requested.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        ) {
            score += 10
            reasons.add("+10 危险组合：短信 + 网络 + (安装器/悬浮窗)")
        }
        if (requested.contains("android.permission.RECORD_AUDIO") &&
            requested.contains("android.permission.CAMERA") &&
            !isSystem
        ) {
            score += 6
            reasons.add("+6 同时申请摄像头与麦克风")
        }

        return Verdict(score.coerceAtLeast(0), reasons)
    }

    fun levelOf(score: Int): com.aegis.av.data.ThreatLevel = when {
        score >= LEVEL_HIGH -> com.aegis.av.data.ThreatLevel.HIGH
        score >= LEVEL_MEDIUM -> com.aegis.av.data.ThreatLevel.MEDIUM
        else -> com.aegis.av.data.ThreatLevel.LOW
    }

    @Suppress("DEPRECATION")
    fun installerOf(pm: PackageManager, packageName: String): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { pm.getInstallSourceInfo(packageName).installingPackageName }.getOrNull()
        } else {
            runCatching { pm.getInstallerPackageName(packageName) }.getOrNull()
        }
    }

    /** 应用签名证书的 SHA-256（用于证书指纹黑名单）。 */
    fun signingCertSha256(context: Context, packageName: String): String? {
        return runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= 28) {
                val pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val info = pi.signingInfo ?: return null
                val signers = info.apkContentsSigners ?: info.signingCertificateHistory
                signers?.firstOrNull()?.toByteArray()?.let { HashEngine.sha256Hex(it) }
            } else {
                val pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                pi.signatures?.firstOrNull()?.toByteArray()?.let { HashEngine.sha256Hex(it) }
            }
        }.getOrNull()
    }

    @SuppressLint("QueryAllPackagesNeeded")
    @Suppress("DEPRECATION")
    fun installedPackages(context: Context, withPermissions: Boolean): List<PackageInfo> {
        val flags = if (withPermissions) PackageManager.GET_PERMISSIONS else 0
        return runCatching { context.packageManager.getInstalledPackages(flags) }
            .getOrDefault(emptyList())
    }
}
