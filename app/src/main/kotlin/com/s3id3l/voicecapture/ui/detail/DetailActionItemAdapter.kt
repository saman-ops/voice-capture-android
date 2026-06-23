package com.s3id3l.voicecapture.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.s3id3l.voicecapture.R

class DetailActionItemAdapter(
    private val onSendToTasks: (Int, DetailActionItem) -> Unit
) : ListAdapter<DetailActionItem, DetailActionItemAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvStatus: TextView = view.findViewById(R.id.tv_detail_action_status)
        val tvText: TextView = view.findViewById(R.id.tv_detail_action_text)
        val btnTasks: AppCompatButton = view.findViewById(R.id.btn_detail_send_tasks)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detail_action_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvText.text = item.text

        if (item.sentToTasks) {
            holder.tvStatus.text = "☑"
            holder.tvStatus.alpha = 1.0f
            holder.tvText.alpha = 0.55f
            holder.btnTasks.text = "✓ Gesendet"
            holder.btnTasks.isEnabled = false
            holder.btnTasks.alpha = 0.5f
        } else {
            holder.tvStatus.text = "☐"
            holder.tvStatus.alpha = 0.7f
            holder.tvText.alpha = 1.0f
            holder.btnTasks.text = "📋 Senden"
            holder.btnTasks.isEnabled = true
            holder.btnTasks.alpha = 1.0f
        }

        holder.btnTasks.setOnClickListener {
            if (!item.sentToTasks) onSendToTasks(holder.bindingAdapterPosition, item)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DetailActionItem>() {
            override fun areItemsTheSame(a: DetailActionItem, b: DetailActionItem) = a.text == b.text
            override fun areContentsTheSame(a: DetailActionItem, b: DetailActionItem) = a == b
        }
    }
}
