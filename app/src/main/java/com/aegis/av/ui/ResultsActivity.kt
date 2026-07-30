package com.aegis.av.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aegis.av.R
import com.aegis.av.data.Prefs
import com.aegis.av.data.QuarantineStore
import com.aegis.av.data.Threat
import com.aegis.av.data.ThreatLevel
import com.aegis.av.databinding.ActivityResultsBinding
import com.aegis.av.databinding.ItemThreatBinding
import java.io.File

class ResultsActivity : AppCompatActivity() {

    private lateinit var b: ActivityResultsBinding
    private val items = ArrayList<Threat>()
    private lateinit var adapter: ThreatsAdapter

    /** 卸载回执：返回后校验应用是否确实已卸载。 */
    private var pendingUninstallPkg: String? = null
    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val pkg = pendingUninstallPkg ?: return@registerForActivityResult
        pendingUninstallPkg = null
        val stillThere = runCatching { packageManager.getPackageInfo(pkg, 0) }.isSuccess
        Toast.makeText(
            this,
            if (stillThere) R.string.toast_uninstall_failed else R.string.toast_deleted,
            Toast.LENGTH_SHORT,
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        // 按威胁等级排序：病毒 > 高 > 中 > 低（枚举声明序即优先级）
        items.addAll(com.aegis.av.service.ScanService.lastThreats.sortedBy { it.level })
        adapter = ThreatsAdapter(items,
            onUninstall = { t, pos -> uninstall(t, pos) },
            onQuarantine = { t, pos -> quarantine(t, pos) },
            onDelete = { t, pos -> deleteFile(t, pos) },
            onIgnore = { t, pos -> ignore(t, pos) },
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        renderEmpty()
    }

    private fun renderEmpty() {
        b.layoutEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun uninstall(t: Threat, pos: Int) {
        val pkg = t.packageName ?: return
        runCatching {
            pendingUninstallPkg = pkg
            uninstallLauncher.launch(
                Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg"))
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true)
            )
        }.onFailure {
            pendingUninstallPkg = null
            Toast.makeText(this, R.string.toast_action_failed, Toast.LENGTH_SHORT).show()
        }
        removeAt(pos)
    }

    private fun quarantine(t: Threat, pos: Int) {
        val path = t.path ?: return
        val entry = QuarantineStore.quarantine(this, path, t.title)
        Toast.makeText(
            this,
            if (entry != null) R.string.toast_quarantined else R.string.toast_action_failed,
            Toast.LENGTH_SHORT,
        ).show()
        if (entry != null) removeAt(pos)
    }

    private fun deleteFile(t: Threat, pos: Int) {
        val path = t.path ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_confirm_msg, path))
            .setPositiveButton(R.string.delete) { _, _ ->
                val ok = File(path).delete()
                Toast.makeText(
                    this,
                    if (ok) R.string.toast_deleted else R.string.toast_action_failed,
                    Toast.LENGTH_SHORT,
                ).show()
                if (ok) removeAt(pos)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun ignore(t: Threat, pos: Int) {
        Prefs.ignore(t.key)
        Toast.makeText(this, R.string.toast_ignored, Toast.LENGTH_SHORT).show()
        removeAt(pos)
    }

    private fun removeAt(pos: Int) {
        if (pos in items.indices) {
            items.removeAt(pos)
            adapter.notifyItemRemoved(pos)
            renderEmpty()
        }
    }

    // ---------------------------------------------------------------------

    class ThreatsAdapter(
        private val data: List<Threat>,
        val onUninstall: (Threat, Int) -> Unit,
        val onQuarantine: (Threat, Int) -> Unit,
        val onDelete: (Threat, Int) -> Unit,
        val onIgnore: (Threat, Int) -> Unit,
    ) : RecyclerView.Adapter<ThreatsAdapter.Holder>() {

        class Holder(val binding: ItemThreatBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemThreatBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val t = data[position]
            val ctx = h.binding.root.context

            h.binding.tvTitle.text = t.title
            h.binding.tvDetail.text = t.detail
            h.binding.chipLevel.text = when (t.level) {
                ThreatLevel.MALWARE -> ctx.getString(R.string.level_malware)
                ThreatLevel.HIGH -> ctx.getString(R.string.level_high)
                ThreatLevel.MEDIUM -> ctx.getString(R.string.level_medium)
                ThreatLevel.LOW -> ctx.getString(R.string.level_low)
            }
            val colorRes = when (t.level) {
                ThreatLevel.MALWARE, ThreatLevel.HIGH -> R.color.danger
                ThreatLevel.MEDIUM -> R.color.warn
                ThreatLevel.LOW -> R.color.safe
            }
            h.binding.chipLevel.setChipBackgroundColorResource(colorRes)

            // 按威胁类型展示可用操作
            h.binding.btnUninstall.visibility = if (t.packageName != null) View.VISIBLE else View.GONE
            h.binding.btnQuarantine.visibility = if (t.path != null) View.VISIBLE else View.GONE
            h.binding.btnDelete.visibility = if (t.path != null) View.VISIBLE else View.GONE

            h.binding.btnUninstall.setOnClickListener { onUninstall(t, h.bindingAdapterPosition) }
            h.binding.btnQuarantine.setOnClickListener { onQuarantine(t, h.bindingAdapterPosition) }
            h.binding.btnDelete.setOnClickListener { onDelete(t, h.bindingAdapterPosition) }
            h.binding.btnIgnore.setOnClickListener { onIgnore(t, h.bindingAdapterPosition) }
        }
    }
}
