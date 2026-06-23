package com.s3id3l.voicecapture.live

import android.graphics.Paint
import android.widget.Toast
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.s3id3l.voicecapture.data.GoogleTasksSender
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.databinding.ItemActionItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActionItemAdapter(
    private val onToggle: (ActionItem) -> Unit,
    private val onRemove: (ActionItem) -> Unit
) : ListAdapter<ActionItem, ActionItemAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ActionItem>() {
            override fun areItemsTheSame(a: ActionItem, b: ActionItem) = a.id == b.id
            override fun areContentsTheSame(a: ActionItem, b: ActionItem) = a == b
        }
    }

    inner class VH(val b: ItemActionItemBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemActionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.b.cbDone.setOnCheckedChangeListener(null)
        holder.b.cbDone.isChecked = item.done
        holder.b.tvActionText.text = item.text
        holder.b.tvActionText.paintFlags = if (item.done)
            holder.b.tvActionText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else
            holder.b.tvActionText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        holder.b.cbDone.setOnCheckedChangeListener { _, checked ->
            onToggle(item.copy(done = checked))
        }
        holder.b.btnRemoveItem.setOnClickListener { onRemove(item) }
        holder.b.btnSendTasks.setOnClickListener {
            val ctx = holder.itemView.context
            val webhook = PrefsManager(ctx).googleDocWebhookUrl
            holder.b.btnSendTasks.isEnabled = false
            CoroutineScope(Dispatchers.IO).launch {
                val ok = GoogleTasksSender.sendTask(webhook, item.text)
                withContext(Dispatchers.Main) {
                    holder.b.btnSendTasks.isEnabled = true
                    Toast.makeText(
                        ctx,
                        if (ok) "✓ An Google Tasks gesendet" else "⚠️ Fehler beim Senden",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun getItemAt(position: Int): ActionItem = getItem(position)
}
