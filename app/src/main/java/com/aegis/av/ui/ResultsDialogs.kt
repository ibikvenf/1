package com.aegis.av.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.aegis.av.R
import com.aegis.av.update.DatabaseUpdater

/** 复用的对话框构建工具。 */
object ResultsDialogs {

    fun dbUpdate(context: Context, results: List<DatabaseUpdater.UpdateResult>) {
        val text = if (results.isEmpty()) {
            context.getString(R.string.update_no_sources)
        } else results.joinToString("\n") { r ->
            val icon = when (r.status) {
                DatabaseUpdater.Status.UPDATED -> "✅"
                DatabaseUpdater.Status.NOT_MODIFIED -> "ℹ️"
                DatabaseUpdater.Status.SKIPPED -> "⏭"
                DatabaseUpdater.Status.FAILED -> "❌"
            }
            val status = when (r.status) {
                DatabaseUpdater.Status.UPDATED -> context.getString(R.string.update_status_updated, r.message)
                DatabaseUpdater.Status.NOT_MODIFIED -> context.getString(R.string.update_status_not_modified)
                DatabaseUpdater.Status.SKIPPED -> context.getString(R.string.update_status_skipped)
                DatabaseUpdater.Status.FAILED -> context.getString(R.string.update_status_failed, r.message)
            }
            "$icon ${r.source}: $status"
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.update_result_title)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
