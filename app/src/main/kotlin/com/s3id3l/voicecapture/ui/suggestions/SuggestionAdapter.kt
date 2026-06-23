package com.s3id3l.voicecapture.ui.suggestions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.s3id3l.voicecapture.data.db.SuggestionEntity
import com.s3id3l.voicecapture.databinding.ItemSuggestionBinding

class SuggestionAdapter(
    private val onQueue: (SuggestionEntity) -> Unit
) : ListAdapter<SuggestionEntity, SuggestionAdapter.VH>(DIFF) {

    inner class VH(val b: ItemSuggestionBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val implementing = item.status == SuggestionEntity.STATUS_IMPLEMENTING
        val done = item.status == SuggestionEntity.STATUS_DONE
        holder.b.tvTitle.text = item.title
        holder.b.tvDesc.text = item.description
        holder.b.btnQueue.text = when {
            done -> "✅ Umgesetzt"
            implementing -> "…"
            else -> "UMSETZEN"
        }
        holder.b.btnQueue.isEnabled = !done && !implementing
        holder.b.btnQueue.alpha = if (implementing) 0.5f else 1.0f
        if (!done && !implementing) {
            holder.b.btnQueue.setOnClickListener { onQueue(item) }
        } else {
            holder.b.btnQueue.setOnClickListener(null)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SuggestionEntity>() {
            override fun areItemsTheSame(a: SuggestionEntity, b: SuggestionEntity) = a.id == b.id
            override fun areContentsTheSame(a: SuggestionEntity, b: SuggestionEntity) = a == b
        }
    }
}
