package com.aegis.av.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aegis.av.R
import com.aegis.av.data.AppRisk
import com.aegis.av.data.ThreatLevel
import com.aegis.av.databinding.ActivityAppsBinding
import com.aegis.av.databinding.ItemRiskAppBinding
import com.aegis.av.engine.HeuristicAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 应用风险审计：按启发式评分排序展示。 */
class AppsActivity : AppCompatActivity() {

    private lateinit var b: ActivityAppsBinding
    private val data = ArrayList<AppRisk>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAppsBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = RiskAdapter(data) { showDetail(it) }

        b.progress.show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { audit() }
            b.progress.hide()
            data.clear()
            data.addAll(result)
            b.recycler.adapter?.notifyDataSetChanged()
            b.tvSummary.text = getString(
                R.string.audit_summary_fmt,
                result.size,
                result.count { it.level == ThreatLevel.HIGH },
                result.count { it.level == ThreatLevel.MEDIUM },
            )
        }
    }

    private fun audit(): List<AppRisk> {
        val pm = packageManager
        val pkgs = HeuristicAnalyzer.installedPackages(this, withPermissions = true)
        val out = ArrayList<AppRisk>()
        for (pkg in pkgs) {
            val ai = pkg.applicationInfo ?: continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val v = HeuristicAnalyzer.analyze(this, pkg, ai)
            val level = HeuristicAnalyzer.levelOf(v.score)
            if (level == ThreatLevel.LOW) continue // 只展示中高风险
            out.add(
                AppRisk(
                    packageName = ai.packageName,
                    appName = runCatching { pm.getApplicationLabel(ai).toString() }
                        .getOrDefault(ai.packageName),
                    sourceApk = ai.sourceDir ?: "",
                    isSystem = isSystem,
                    installer = HeuristicAnalyzer.installerOf(pm, ai.packageName),
                    score = v.score,
                    level = level,
                    reasons = v.reasons,
                )
            )
        }
        return out.sortedByDescending { it.score }
    }

    private fun showDetail(risk: AppRisk) {
        AlertDialog.Builder(this)
            .setTitle("${risk.appName}（${risk.score} 分）")
            .setMessage(
                buildString {
                    append(getString(R.string.audit_pkg_fmt, risk.packageName)).append('\n')
                    append(
                        getString(
                            R.string.audit_installer_fmt,
                            risk.installer ?: getString(R.string.unknown_source),
                        )
                    ).append('\n').append('\n')
                    append(risk.reasons.joinToString("\n"))
                }
            )
            .setPositiveButton(R.string.uninstall) { _, _ ->
                runCatching {
                    startActivity(
                        Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${risk.packageName}"))
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------------

    class RiskAdapter(
        private val data: List<AppRisk>,
        val onClick: (AppRisk) -> Unit,
    ) : RecyclerView.Adapter<RiskAdapter.Holder>() {

        class Holder(val binding: ItemRiskAppBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemRiskAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val item = data[position]
            val ctx = h.binding.root.context
            h.binding.tvName.text = item.appName
            h.binding.tvPkg.text = item.packageName
            h.binding.tvScore.text = ctx.getString(R.string.audit_score_fmt, item.score)
            h.binding.chipRisk.text = when (item.level) {
                ThreatLevel.HIGH -> ctx.getString(R.string.level_high)
                ThreatLevel.MEDIUM -> ctx.getString(R.string.level_medium)
                else -> ctx.getString(R.string.level_low)
            }
            h.binding.chipRisk.setChipBackgroundColorResource(
                when (item.level) {
                    ThreatLevel.HIGH -> R.color.danger
                    ThreatLevel.MEDIUM -> R.color.warn
                    else -> R.color.safe
                }
            )
            h.binding.imgIcon.setImageDrawable(
                runCatching { ctx.packageManager.getApplicationIcon(item.packageName) }
                    .getOrNull() ?: ctx.getDrawable(android.R.drawable.sym_def_app_icon)
            )
            h.binding.root.setOnClickListener { onClick(item) }
        }
    }
}
