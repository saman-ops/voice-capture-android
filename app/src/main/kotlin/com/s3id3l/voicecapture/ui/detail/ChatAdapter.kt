package com.s3id3l.voicecapture.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.s3id3l.voicecapture.data.db.ChatMessageEntity
import com.s3id3l.voicecapture.databinding.ItemChatMessageBinding

class ChatAdapter : ListAdapter<ChatMessageEntity, ChatAdapter.VH>(DIFF) {

    inner class VH(val b: ItemChatMessageBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item   = getItem(position)
        val isUser = item.role == "user"
        holder.b.tvContent.text = item.content
        holder.b.bubbleCard.setCardBackgroundColor(
            if (isUser) 0xFF1E40AF.toInt() else 0xFF374151.toInt()
        )
        val density = holder.b.root.context.resources.displayMetrics.density
        val margin  = (80 * density).toInt()
        val lp = holder.b.bubbleCard.layoutParams as LinearLayout.LayoutParams
        lp.marginStart = if (isUser) margin else 0
        lp.marginEnd   = if (isUser) 0 else margin
        lp.gravity     = if (isUser) android.view.Gravity.END else android.view.Gravity.START
        holder.b.bubbleCard.layoutParams = lp
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChatMessageEntity>() {
            override fun areItemsTheSame(a: ChatMessageEntity, b: ChatMessageEntity) = a.id == b.id
            override fun areContentsTheSame(a: ChatMessageEntity, b: ChatMessageEntity) = a == b
        }
    }
}
