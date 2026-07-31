package com.aegis.av.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aegis.av.R
import com.aegis.av.data.QuarantineStore
import com.aegis.av.databinding.ActivityQuarantineBinding
import com.aegis.av.databinding.ItemQuarantineBinding
import java.text.DateFormat
import java.util.Date

class QuarantineActivity : AppCompatActivity() {

    private lateinit var b: ActivityQuarantineBinding
    private val data = ArrayList<QuarantineStore.Entry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuarantineBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.recycler.layoutManager = LinearLayoutManager(this)
        reload()
    }

    private fun reload() {
        data.clear()
        data.addAll(QuarantineStore.list(this))
        b.recycler.adapter = QuarAdapter(data,
            onRestore = { pos ->
                val ok = QuarantineStore.restore(this, data[pos].id)
                toast(if (ok) R.string.toast_restored else R.string.toast_action_failed)
                reload()
            },
            onDelete = { pos ->
                QuarantineStore.delete(this, data[pos].id)
                toast(R.string.toast_deleted)
                reload()
            },
        )
        b.tvEmpty.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    // ---------------------------------------------------------------------

    class QuarAdapter(
        private val data: List<QuarantineStore.Entry>,
        val onRestore: (Int) -> Unit,
        val onDelete: (Int) -> Unit,
    ) : RecyclerView.Adapter<QuarAdapter.Holder>() {

        class Holder(val binding: ItemQuarantineBinding) : RecyclerView.ViewHolder(binding.root)

        private val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemQuarantineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val e = data[position]
            h.binding.tvName.text = e.threatName
            h.binding.tvPath.text = h.binding.root.context
                .getString(R.string.quarantine_path_fmt, e.originalPath)
            h.binding.tvTime.text = df.format(Date(e.time))
            h.binding.btnRestore.setOnClickListener { onRestore(h.bindingAdapterPosition) }
            h.binding.btnDelete.setOnClickListener { onDelete(h.bindingAdapterPosition) }
        }
    }
}
