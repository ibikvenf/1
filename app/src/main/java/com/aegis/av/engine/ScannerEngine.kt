package com.aegis.av.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Environment
import com.aegis.av.R
import com.aegis.av.data.Prefs
import com.aegis.av.data.ScanSummary
import com.aegis.av.data.ScanUiState
import com.aegis.av.data.SignatureDatabase
import com.aegis.av.data.SignatureRepository
import com.aegis.av.data.Threat
import com.aegis.av.data.ThreatLevel
import com.aegis.av.data.ThreatType
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 扫描引擎：驱动"应用签名查杀 + 文件签名查杀 + 启发式风险"。
 * 全程离线，符合"文件永不离开设备"原则。
 */
class ScannerEngine(private val context: Context) {

    private val ids = AtomicLong(1)
    private val threats = ArrayList<Threat>()

    private var state = ScanUiState()
    private var onProgress: (ScanUiState) -> Unit = {}

    private lateinit var db: SignatureDatabase
    private var startedAt = 0L

    @Volatile
    private var cancelled = false

    /** 由外部（服务的协程取消回调）请求停止，引擎在循环边界尽快退出。 */
    fun requestCancel() {
        cancelled = true
    }

    data class Result(val threats: List<Threat>, val summary: ScanSummary)

    /** 全量扫描入口。可在协程中取消。 */
    suspend fun start(
        scanApps: Boolean,
        roots: List<File>,
        onProgress: (ScanUiState) -> Unit,
    ): Result {
        this.onProgress = onProgress
        startedAt = System.currentTimeMillis()
        db = SignatureRepository.load()
        threats.clear()
        state = ScanUiState(running = true)
        push()

        val maxBytes = Prefs.maxFileMb.toLong() * 1024L * 1024L

        // ---- 第一阶段：已安装应用 ----
        if (scanApps) {
            update(phase = context.getString(R.string.phase_apps))
            val pkgs = HeuristicAnalyzer.installedPackages(context, withPermissions = true)
                .filter { pi ->
                    val ai = pi.applicationInfo
                    ai != null && (Prefs.scanSystemApps ||
                        (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0)
                }
            for (pkg in pkgs) {
                if (cancelled) break
                val appInfo = pkg.applicationInfo ?: continue
                update(currentTarget = appInfo.packageName)
                scanApkFile(appInfo.sourceDir, appInfo.packageName, labelOf(appInfo))

                // 启发式（系统应用跳过，降低误报）
                if (Prefs.heuristicsEnabled && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                    pkg.takeIf { it.requestedPermissions != null }?.let { info ->
                        val v = HeuristicAnalyzer.analyze(context, info, appInfo)
                        val level = HeuristicAnalyzer.levelOf(v.score)
                        if (level == ThreatLevel.HIGH || level == ThreatLevel.MEDIUM) {
                            addThreat(
                                Threat(
                                    id = ids.getAndIncrement(),
                                    title = context.getString(R.string.threat_risk_app),
                                    detail = context.getString(
                                        R.string.risk_detail_fmt,
                                        labelOf(appInfo), v.score, v.reasons.joinToString("\n")
                                    ),
                                    level = level,
                                    type = ThreatType.HEURISTIC,
                                    packageName = appInfo.packageName,
                                )
                            )
                        }
                    }
                }
                state = state.copy(scannedApps = state.scannedApps + 1)
                pushThrottled()
            }
        }

        // ---- 第二阶段：文件扫描 ----
        var stop = false
        if (roots.isNotEmpty() && !cancelled) {
            update(phase = context.getString(R.string.phase_files))
            val estimate = quickEstimate(roots, maxBytes)
            update(totalEstimate = estimate)
            for (root in roots) {
                if (stop) break
                walkAndScan(root, maxBytes) { stop = true }
                if (stop) cancelled = true
            }
        }

        val finished = System.currentTimeMillis()
        if (cancelled) state = state.copy(cancelled = true)
        state = state.copy(running = false, finished = true, phase = "", currentTarget = "")
        push()

        val summary = ScanSummary(
            startedAt = startedAt,
            finishedAt = finished,
            scannedFiles = state.scannedFiles,
            scannedApps = state.scannedApps,
            threatCount = threats.size,
            cancelled = cancelled,
        )
        return Result(threats.toList(), summary)
    }

    /** 扫描单个文件（实时防护 / 病毒库自检用）。 */
    fun scanFile(file: File): Threat? {
        db = SignatureRepository.load()
        val h = HashEngine.ofFile(file) ?: return null
        val name = db.lookup(h) ?: return null
        return Threat(
            id = ids.getAndIncrement(),
            title = name,
            detail = context.getString(R.string.threat_file_detail_fmt, file.absolutePath, h.sha256),
            level = ThreatLevel.MALWARE,
            type = ThreatType.SIGNATURE,
            path = file.absolutePath,
            hash = h.sha256,
        ).takeUnless { isIgnored(it.key) }
    }

    /** 扫描单个已安装应用。 */
    fun scanPackage(packageName: String): Threat? {
        db = SignatureRepository.load()
        val pm = context.packageManager
        val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() ?: return null
        return scanApkFile(appInfo.sourceDir, packageName, labelOf(appInfo))
    }

    // ------------------------------------------------------------------

    private fun scanApkFile(sourceDir: String?, packageName: String, label: String): Threat? {
        if (sourceDir.isNullOrEmpty()) return null
        val h = HashEngine.ofFile(File(sourceDir)) ?: return null
        val name = db.lookup(h) ?: return null
        val t = Threat(
            id = ids.getAndIncrement(),
            title = name,
            detail = context.getString(R.string.threat_app_detail_fmt, label, packageName, h.sha256),
            level = ThreatLevel.MALWARE,
            type = ThreatType.SIGNATURE,
            packageName = packageName,
            hash = h.sha256,
        )
        return if (isIgnored(t.key)) null else addThreat(t)
    }

    private var lastPush = 0L

    private fun pushThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastPush >= 200) {
            lastPush = now
            push()
        }
    }

    private fun push() {
        lastPush = System.currentTimeMillis()
        onProgress(state)
    }

    private fun update(
        phase: String? = null,
        currentTarget: String? = null,
        totalEstimate: Int? = null,
    ) {
        state = state.copy(
            phase = phase ?: state.phase,
            currentTarget = currentTarget ?: state.currentTarget,
            totalEstimate = totalEstimate ?: state.totalEstimate,
        )
        pushThrottled()
    }

    private fun addThreat(t: Threat): Threat? {
        if (isIgnored(t.key)) return null
        threats.add(t)
        state = state.copy(threatCount = threats.size)
        push()
        return t
    }

    private fun isIgnored(key: String): Boolean = key in Prefs.ignoredThreats

    private fun labelOf(appInfo: ApplicationInfo): String =
        runCatching { context.packageManager.getApplicationLabel(appInfo).toString() }
            .getOrDefault(appInfo.packageName)

    // ------------------------------ 文件遍历 ------------------------------

    private val skipDirs = setOf(".thumbnails", ".trash", "LOST.DIR", "..", ".")
    private var walkedFiles = 0

    private fun quickEstimate(roots: List<File>, maxBytes: Long): Int {
        walkedFiles = 0
        var count = 0
        val stack = ArrayDeque<File>()
        roots.forEach { if (it.isDirectory) stack.addLast(it) }
        while (stack.isNotEmpty() && walkedFiles < ESTIMATE_CAP) {
            val dir = stack.removeFirst()
            val list = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (f in list) {
                if (f.isDirectory) {
                    if (f.name !in skipDirs && f.absolutePath !in PROTECTED_DIRS) stack.addLast(f)
                } else if (f.isFile) {
                    walkedFiles++
                    if (f.length() in 1..maxBytes) count++
                }
            }
        }
        return count
    }

    private fun walkAndScan(root: File, maxBytes: Long, requestStop: () -> Unit) {
        if (cancelled) { requestStop(); return }
        when {
            root.isDirectory -> {
                if (root.name in skipDirs || root.absolutePath in PROTECTED_DIRS) return
                val list = runCatching { root.listFiles() }.getOrNull() ?: return
                for (f in list) {
                    if (cancelled) { requestStop(); return }
                    walkAndScan(f, maxBytes, requestStop)
                    if (cancelled) return
                }
            }
            root.isFile -> {
                val len = root.length()
                if (len <= 0L || len > maxBytes) return
                update(currentTarget = root.absolutePath)
                val t = scanFile(root)
                if (t != null) addThreat(t)
                state = state.copy(scannedFiles = state.scannedFiles + 1)
                pushThrottled()
            }
        }
    }

    companion object {
        private const val ESTIMATE_CAP = 300_000

        /** 这些目录即使可读也不扫（系统目录 / 其他应用私有目录）。 */
        private val PROTECTED_DIRS: Set<String> by lazy {
            val ext = Environment.getExternalStorageDirectory()
            setOf(
                ext.absolutePath + "/Android/data",
                ext.absolutePath + "/Android/obb",
                ext.absolutePath + "/Android/sandbox",
            )
        }

        fun fullScanRoots(): List<File> =
            listOf(Environment.getExternalStorageDirectory())

        fun quickScanRoots(): List<File> = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        ).filter { it.isDirectory }
    }
}
