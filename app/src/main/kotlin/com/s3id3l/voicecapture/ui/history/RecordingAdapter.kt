package com.s3id3l.voicecapture.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.s3id3l.voicecapture.data.db.RecordingEntity
import com.s3id3l.voicecapture.databinding.ItemRecordingBinding
import java.text.SimpleDateFormat
import java.util.*

class RecordingAdapter(
    private val onClick: (RecordingEntity) -> Unit
) : ListAdapter<RecordingEntity, RecordingAdapter.VH>(DIFF) {

    inner class VH(val b: ItemRecordingBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.b.tvTitle.text = item.title.ifEmpty { "Aufnahme" }
        holder.b.tvDate.text = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(item.createdAt))
        holder.b.tvDuration.text = if (item.durationMs > 0) {
            val s = item.durationMs / 1000
            if (s < 60) "${s}s" else "%d:%02ds".format(s / 60, s % 60)
        } else ""
        holder.b.tvFormat.text = item.format.replaceFirstChar { it.uppercase() }
        holder.b.tvStatus.text = when (item.status) {
            RecordingEntity.STATUS_DONE       -> "✅"
            RecordingEntity.STATUS_PROCESSING -> "⏳"
            RecordingEntity.STATUS_ERROR      -> "❌"
            else                              -> "⏳"
        }
        holder.b.root.setOnClickListener { onClick(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<RecordingEntity>() {
            override fun areItemsTheSame(a: RecordingEntity, b: RecordingEntity) = a.id == b.id
            override fun areContentsTheSame(a: RecordingEntity, b: RecordingEntity) = a == b
        }
    }
}
