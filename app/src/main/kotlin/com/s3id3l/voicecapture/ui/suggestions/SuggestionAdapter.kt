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
        val item   = getItem(position)
        val queued = item.status == "queued"
        holder.b.tvTitle.text    = item.title
        holder.b.tvDesc.text     = item.description
        holder.b.btnQueue.text   = if (queued) "✅ Eingeplant" else "Einplanen"
        holder.b.btnQueue.isEnabled = !queued
        holder.b.btnQueue.setOnClickListener { onQueue(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SuggestionEntity>() {
            override fun areItemsTheSame(a: SuggestionEntity, b: SuggestionEntity) = a.id == b.id
            override fun areContentsTheSame(a: SuggestionEntity, b: SuggestionEntity) = a == b
        }
    }
}
