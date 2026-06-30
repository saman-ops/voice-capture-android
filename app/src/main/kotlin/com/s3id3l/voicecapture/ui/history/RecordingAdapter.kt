package com.s3id3l.voicecapture.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.s3id3l.voicecapture.R
import com.s3id3l.voicecapture.data.db.RecordingEntity
import com.s3id3l.voicecapture.databinding.ItemRecordingBinding
import java.text.SimpleDateFormat
import java.util.*

class RecordingAdapter(
    private val onClick: (RecordingEntity) -> Unit,
    private val onLongClick: ((RecordingEntity) -> Unit)? = null
) : ListAdapter<RecordingEntity, RecordingAdapter.VH>(DIFF) {

    var selectedIds: Set<Long> = emptySet()
        set(value) {
            val old = field
            field = value
            if (old.isEmpty() != value.isEmpty()) {
                // Toggling selection mode on/off changes every row's dimming → rebind all (status/alpha only)
                notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
            } else {
                // Only the rows whose selected-state flipped need rebinding
                val changed = (old - value) + (value - old)
                for (i in 0 until itemCount) {
                    if (getItem(i).id in changed) notifyItemChanged(i, PAYLOAD_SELECTION)
                }
            }
        }

    /** When false (e.g. trash view), date-group headers are hidden. */
    var groupingEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    inner class VH(val b: ItemRecordingBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            bindSelection(holder, getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    /** Binds only the selection-dependent visuals (status glyph + row dimming). */
    private fun bindSelection(holder: VH, item: RecordingEntity) {
        val selected = item.id in selectedIds
        holder.b.tvStatus.text = if (selected) "✓" else when (item.status) {
            RecordingEntity.STATUS_DONE       -> "✅"
            RecordingEntity.STATUS_PROCESSING -> "⏳"
            RecordingEntity.STATUS_ERROR      -> "❌"
            else                              -> "⏳"
        }
        holder.b.root.alpha = if (selectedIds.isEmpty() || selected) 1f else 0.55f
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.b.root.context

        // Date-group header (Heute / Gestern / Diese Woche / Älter)
        val label = groupLabel(item.createdAt)
        val showHeader = groupingEnabled &&
            (position == 0 || groupLabel(getItem(position - 1).createdAt) != label)
        if (showHeader) {
            holder.b.tvDateGroup.text = label
            holder.b.tvDateGroup.visibility = View.VISIBLE
        } else {
            holder.b.tvDateGroup.visibility = View.GONE
        }

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
            holder.b.tvFormat.setTextColor(ContextCompat.getColor(ctx, R.color.accent_red))
        } else {
            holder.b.tvFormat.text = item.format.replaceFirstChar { it.uppercase() }
            holder.b.tvFormat.setTextColor(ContextCompat.getColor(ctx, R.color.text_muted))
        }
        val actionCount = countActionItems(item.liveActionItems)
        if (actionCount > 0) {
            holder.b.tvActionCount.text = "📋 $actionCount"
            holder.b.tvActionCount.visibility = android.view.View.VISIBLE
        } else {
            holder.b.tvActionCount.visibility = android.view.View.GONE
        }
        bindSelection(holder, item)
        holder.b.root.setOnClickListener {
            if (selectedIds.isNotEmpty()) onLongClick?.invoke(item) else onClick(item)
        }
        holder.b.root.setOnLongClickListener { onLongClick?.invoke(item); true }
    }

    private fun countActionItems(json: String): Int {
        if (json.isBlank() || json == "[]") return 0
        return try { org.json.JSONArray(json).length() } catch (_: Exception) { 0 }
    }

    private fun groupLabel(timestamp: Long): String {
        val now = Calendar.getInstance()
        val item = Calendar.getInstance().apply { timeInMillis = timestamp }

        fun sameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            sameDay(item, now) -> "Heute"
            sameDay(item, yesterday) -> "Gestern"
            now.timeInMillis - timestamp <= 7L * 24 * 60 * 60 * 1000 -> "Diese Woche"
            else -> "Älter"
        }
    }

    companion object {
        private const val PAYLOAD_SELECTION = "selection"

        val DIFF = object : DiffUtil.ItemCallback<RecordingEntity>() {
            override fun areItemsTheSame(a: RecordingEntity, b: RecordingEntity) = a.id == b.id
            override fun areContentsTheSame(a: RecordingEntity, b: RecordingEntity) = a == b
        }
    }
}
