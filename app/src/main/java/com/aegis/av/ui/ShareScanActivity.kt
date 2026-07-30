package com.aegis.av.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aegis.av.R
import com.aegis.av.data.SignatureRepository
import com.aegis.av.engine.HashEngine
import com.aegis.av.engine.Hashes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 分享查杀：接收其他应用通过 ACTION_SEND / ACTION_VIEW 发来的
 * 文件（content:// 或 file://）与文本，流式哈希查杀后弹窗报告。
 * 借鉴 Hypatia 路线图"扫描分享文件"功能的产品形态。
 */
class ShareScanActivity : AppCompatActivity() {

    private data class Verdict(
        val targetName: String,
        val hashes: Hashes?,
        val threatName: String?,
        val isText: Boolean,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_scan)

        lifecycleScope.launch {
            val verdict = withContext(Dispatchers.IO) { scanIntent() }
            showResult(verdict)
        }
    }

    private fun scanIntent(): Verdict {
        val db = SignatureRepository.load()

        // 1) 分享文本（可直接捕获分享来的 EICAR 测试字符串）
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
            val h = HashEngine.ofBytes(text.toByteArray(Charsets.UTF_8))
            return Verdict(
                text.take(80), h, db.lookup(h), isText = true,
            )
        }

        // 2) 分享/打开文件
        @Suppress("DEPRECATION")
        val uri = (intent.data
            ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) as? Uri)
            ?: return Verdict("", null, null, false)

        val name = displayNameOf(uri)
        val hashes = contentResolver.openInputStream(uri)?.use { HashEngine.ofStream(it) }
        return Verdict(name, hashes, hashes?.let { db.lookup(it) }, isText = false)
    }

    private fun displayNameOf(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx)?.let { return it }
                    }
                }
        }
        return uri.lastPathSegment ?: uri.toString()
    }

    private fun showResult(v: Verdict) {
        val builder = AlertDialog.Builder(this)
        when {
            v.hashes == null -> builder
                .setTitle(R.string.share_result_title)
                .setMessage(getString(R.string.share_no_content))

            v.threatName != null -> builder
                .setTitle(R.string.alert_threats_found_title)
                .setMessage(
                    getString(
                        R.string.share_threat_fmt,
                        v.targetName, v.threatName, v.hashes.sha256,
                    )
                )

            else -> builder
                .setTitle(R.string.share_result_title)
                .setMessage(
                    getString(
                        R.string.share_safe_fmt,
                        v.targetName, v.hashes.sha256,
                    )
                )
        }
        builder
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
