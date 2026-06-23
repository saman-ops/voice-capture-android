package com.s3id3l.voicecapture.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.s3id3l.voicecapture.R

class DetailActionItemAdapter(
    private val onSendToTasks: (Int, DetailActionItem) -> Unit
) : ListAdapter<DetailActionItem, DetailActionItemAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvText: TextView = view.findViewById(R.id.tv_detail_action_text)
        val btnTasks: AppCompatImageButton = view.findViewById(R.id.btn_detail_send_tasks)
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
            holder.btnTasks.setImageResource(android.R.drawable.checkbox_on_background)
            holder.btnTasks.alpha = 0.45f
            holder.btnTasks.isEnabled = false
            holder.tvText.alpha = 0.55f
        } else {
            holder.btnTasks.setImageResource(android.R.drawable.ic_menu_share)
            holder.btnTasks.alpha = 1.0f
            holder.btnTasks.isEnabled = true
            holder.tvText.alpha = 1.0f
        }

        holder.btnTasks.setOnClickListener {
            if (!item.sentToTasks) {
                openGoogleTasks(holder.itemView.context, item.text)
                onSendToTasks(position, item)
            }
        }
    }

    companion object {
        fun openGoogleTasks(context: Context, title: String) {
            val uri = Uri.Builder()
                .scheme("https").authority("tasks.google.com").path("/tasks/create")
                .appendQueryParameter("title", title).build()
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        val DIFF = object : DiffUtil.ItemCallback<DetailActionItem>() {
            override fun areItemsTheSame(a: DetailActionItem, b: DetailActionItem) = a.text == b.text
            override fun areContentsTheSame(a: DetailActionItem, b: DetailActionItem) = a == b
        }
    }
}
