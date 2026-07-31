package com.aegis.av.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aegis.av.R
import com.aegis.av.data.SignatureRepository
import com.aegis.av.databinding.ActivityUpdateBinding
import com.aegis.av.databinding.DialogAddSourceBinding
import com.aegis.av.databinding.ItemSourceBinding
import com.aegis.av.update.DatabaseUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** 病毒库管理：源列表（启停/删除/添加）+ 立即更新。 */
class UpdateActivity : AppCompatActivity() {

    private lateinit var b: ActivityUpdateBinding
    private lateinit var sources: MutableList<SignatureRepository.DbSource>
    private var updating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        sources = SignatureRepository.sources()
        b.recycler.layoutManager = LinearLayoutManager(this)
        render()

        b.btnAddSource.setOnClickListener { showAddDialog() }
        b.btnUpdateNow.setOnClickListener { updateNow() }

        refreshDbStatus()
    }

    private fun render() {
        b.recycler.adapter = SourceAdapter(
            sources,
            onToggle = { idx, enabled ->
                sources[idx].enabled = enabled
                SignatureRepository.saveSources(sources)
            },
            onRemove = { idx ->
                sources.removeAt(idx)
                SignatureRepository.saveSources(sources)
                render()
            },
        )
    }

    private fun showAddDialog() {
        val d = DialogAddSourceBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_source_title)
            .setView(d.root)
            .setPositiveButton(R.string.add) { _, _ ->
                val name = d.editName.text.toString().trim()
                val url = d.editUrl.text.toString().trim()
                if (name.isNotEmpty() && (url.startsWith("https://") || url.startsWith("http://"))) {
                    sources.add(SignatureRepository.DbSource(name, url, enabled = true))
                    SignatureRepository.saveSources(sources)
                    render()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateNow() {
        if (updating) return
        updating = true
        b.btnUpdateNow.isEnabled = false
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                val out = ArrayList<DatabaseUpdater.UpdateResult>()
                DatabaseUpdater.updateAll(applicationContext) { out.add(it) }
                out
            }
            updating = false
            b.btnUpdateNow.isEnabled = true
            refreshDbStatus()
            ResultsDialogs.dbUpdate(this@UpdateActivity, results)
        }
    }

    private fun refreshDbStatus() {
        val (count, time) = SignatureRepository.info()
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        b.tvDbStatus.text = if (time > 0)
            getString(R.string.db_info_fmt, count, df.format(Date(time)))
        else getString(R.string.db_info_builtin_fmt, count)
    }

    // ---------------------------------------------------------------------

    class SourceAdapter(
        private val data: List<SignatureRepository.DbSource>,
        val onToggle: (Int, Boolean) -> Unit,
        val onRemove: (Int) -> Unit,
    ) : RecyclerView.Adapter<SourceAdapter.Holder>() {

        class Holder(val binding: ItemSourceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val s = data[position]
            h.binding.tvName.text = s.name
            h.binding.tvUrl.text = s.url
            h.binding.switchEnabled.setOnCheckedChangeListener(null)
            h.binding.switchEnabled.isChecked = s.enabled
            h.binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
                onToggle(h.bindingAdapterPosition, checked)
            }
            h.binding.btnRemove.setOnClickListener { onRemove(h.bindingAdapterPosition) }
        }
    }
}
