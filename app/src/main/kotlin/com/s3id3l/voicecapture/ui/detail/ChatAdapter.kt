package com.s3id3l.voicecapture.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.s3id3l.voicecapture.data.db.ChatMessageEntity
import com.s3id3l.voicecapture.databinding.ItemChatMessageBinding

class ChatAdapter(
    private val onAddToRecording: (String) -> Unit = {}
) : ListAdapter<ChatMessageEntity, ChatAdapter.VH>(DIFF) {

    inner class VH(val b: ItemChatMessageBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = getItem(position)
        val isUser = msg.role == "user"
        holder.b.tvContent.text = msg.content

        val density = holder.b.root.context.resources.displayMetrics.density
        val margin = (80 * density).toInt()

        if (isUser) {
            holder.b.bubbleCard.setCardBackgroundColor(0xFF1E40AF.toInt())
            val lp = holder.b.bubbleCard.layoutParams as android.widget.LinearLayout.LayoutParams
            lp.marginStart = margin
            lp.marginEnd = 0
            lp.gravity = android.view.Gravity.END
            holder.b.bubbleCard.layoutParams = lp
            holder.b.actionRow.visibility = View.GONE
        } else {
            holder.b.bubbleCard.setCardBackgroundColor(0xFF374151.toInt())
            val lp = holder.b.bubbleCard.layoutParams as android.widget.LinearLayout.LayoutParams
            lp.marginStart = 0
            lp.marginEnd = margin
            lp.gravity = android.view.Gravity.START
            holder.b.bubbleCard.layoutParams = lp
            holder.b.actionRow.visibility = View.VISIBLE
            holder.b.btnCopyMsg.setOnClickListener {
                val ctx = holder.itemView.context
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Chat", msg.content))
            }
            holder.b.btnAddMsg.setOnClickListener {
                onAddToRecording(msg.content)
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChatMessageEntity>() {
            override fun areItemsTheSame(a: ChatMessageEntity, b: ChatMessageEntity) = a.id == b.id
            override fun areContentsTheSame(a: ChatMessageEntity, b: ChatMessageEntity) = a == b
        }
    }
}
