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
    private val onClick: (RecordingEntity) -> Unit,
    private val onLongClick: ((RecordingEntity) -> Unit)? = null
) : ListAdapter<RecordingEntity, RecordingAdapter.VH>(DIFF) {

    var selectedIds: Set<Long> = emptySet()
        set(value) { field = value; notifyDataSetChanged() }

    inner class VH(val b: ItemRecordingBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.b.tvTitle.text = item.title.ifEmpty { "Aufnahme" }
        val date = Date(item.createdAt)
        val dayFmt = SimpleDateFormat("EEE", Locale.GERMANY)
        val dateFmt = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
        holder.b.tvDate.text = "${dayFmt.format(date)}, ${dateFmt.format(date)}"
        holder.b.tvDuration.text = if (item.durationMs > 0) {
            val s = item.durationMs / 1000
            if (s < 60) "${s}s" else "%d:%02ds".format(s / 60, s % 60)
        } else ""
        if (item.isLiveSession) {
            holder.b.tvFormat.text = "🔴 Live"
            holder.b.tvFormat.setTextColor(0xFFEF4444.toInt())
        } else {
            holder.b.tvFormat.text = item.format.replaceFirstChar { it.uppercase() }
            holder.b.tvFormat.setTextColor(0xFF6B7280.toInt())
        }
        val actionCount = countActionItems(item.liveActionItems)
        if (actionCount > 0) {
            holder.b.tvActionCount.text = "📋 $actionCount"
            holder.b.tvActionCount.visibility = android.view.View.VISIBLE
        } else {
            holder.b.tvActionCount.visibility = android.view.View.GONE
        }
        val selected = item.id in selectedIds
        holder.b.tvStatus.text = if (selected) "✓" else when (item.status) {
            RecordingEntity.STATUS_DONE       -> "✅"
            RecordingEntity.STATUS_PROCESSING -> "⏳"
            RecordingEntity.STATUS_ERROR      -> "❌"
            else                              -> "⏳"
        }
        holder.b.root.alpha = if (selectedIds.isEmpty() || selected) 1f else 0.55f
        holder.b.root.setOnClickListener {
            if (selectedIds.isNotEmpty()) onLongClick?.invoke(item) else onClick(item)
        }
        holder.b.root.setOnLongClickListener { onLongClick?.invoke(item); true }
    }

    private fun countActionItems(json: String): Int {
        if (json.isBlank() || json == "[]") return 0
        return try { org.json.JSONArray(json).length() } catch (_: Exception) { 0 }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<RecordingEntity>() {
            override fun areItemsTheSame(a: RecordingEntity, b: RecordingEntity) = a.id == b.id
            override fun areContentsTheSame(a: RecordingEntity, b: RecordingEntity) = a == b
        }
    }
}
