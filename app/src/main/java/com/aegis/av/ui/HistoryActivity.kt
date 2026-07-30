package com.aegis.av.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aegis.av.R
import com.aegis.av.data.HistoryStore
import com.aegis.av.databinding.ActivityHistoryBinding
import com.aegis.av.databinding.ItemHistoryBinding
import java.text.DateFormat
import java.util.Date

class HistoryActivity : AppCompatActivity() {

    private lateinit var b: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.recycler.layoutManager = LinearLayoutManager(this)
        reload()

        b.btnClear.setOnClickListener {
            HistoryStore.clear(this)
            reload()
        }
    }

    private fun reload() {
        val list = HistoryStore.load(this)
        b.recycler.adapter = HistoryAdapter(list) { entry -> showDetail(entry) }
        b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showDetail(entry: HistoryStore.HistoryEntry) {
        val threats = entry.threats.orEmpty()
        val msg = if (threats.isEmpty()) {
            getString(R.string.history_no_threats)
        } else {
            threats.joinToString("\n\n") { "• ${it.title}\n${it.detail}" }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.history_detail_title)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------------------------------------------------------------------

    class HistoryAdapter(
        private val data: List<HistoryStore.HistoryEntry>,
        val onClick: (HistoryStore.HistoryEntry) -> Unit,
    ) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

        class Holder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

        private val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val entry = data[position]
            val s = entry.summary ?: return
            val ctx = h.binding.root.context
            h.binding.tvTime.text = df.format(Date(s.finishedAt))
            val secs = (s.finishedAt - s.startedAt) / 1000
            val status = if (s.cancelled)
                ctx.getString(R.string.scan_cancelled)
            else ctx.getString(R.string.scan_finished)
            h.binding.tvSummary.text = ctx.getString(
                R.string.history_item_fmt,
                status, s.scannedApps, s.scannedFiles, s.threatCount, secs,
            )
            h.binding.root.setOnClickListener { onClick(entry) }
        }
    }
}
