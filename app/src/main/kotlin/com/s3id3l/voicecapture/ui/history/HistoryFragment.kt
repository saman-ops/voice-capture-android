package com.s3id3l.voicecapture.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.s3id3l.voicecapture.data.db.RecordingEntity
import com.s3id3l.voicecapture.databinding.FragmentHistoryBinding
import com.s3id3l.voicecapture.ui.chat.MultiChatSheet
import com.s3id3l.voicecapture.ui.detail.DetailActivity
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _b: FragmentHistoryBinding? = null
    private val b get() = _b!!
    private val vm: HistoryViewModel by viewModels()
    private lateinit var adapter: RecordingAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentHistoryBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = RecordingAdapter(
            onClick = { rec ->
                startActivity(
                    Intent(requireContext(), DetailActivity::class.java)
                        .putExtra(DetailActivity.EXTRA_ID, rec.id)
                )
            },
            onLongClick = { rec -> vm.toggleSelection(rec.id) }
        )
        b.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerHistory.adapter = adapter

        listOf(
            "all"        to "Alle",
            RecordingEntity.STATUS_DONE       to "Fertig",
            RecordingEntity.STATUS_PROCESSING to "In Bearbeitung",
            RecordingEntity.STATUS_ERROR      to "Fehler"
        ).forEach { (key, label) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = key == "all"
                setOnCheckedChangeListener { _, isChecked -> if (isChecked) vm.setFilter(key) }
            }
            b.filterChips.addView(chip)
        }

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                if (vm.selectedIds.value.isNotEmpty()) {
                    adapter.notifyItemChanged(vh.adapterPosition)
                    return
                }
                val rec = adapter.currentList[vh.adapterPosition]
                vm.delete(rec)
                Snackbar.make(b.root, "Aufnahme gelöscht", Snackbar.LENGTH_SHORT).show()
            }
        }).attachToRecyclerView(b.recyclerHistory)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.recordings.collect { list ->
                adapter.submitList(list)
                b.emptyHistory.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.selectedIds.collect { ids ->
                adapter.selectedIds = ids
                if (ids.isEmpty()) {
                    b.multiSelectBar.visibility = View.GONE
                } else {
                    b.multiSelectBar.visibility = View.VISIBLE
                    b.tvSelectionCount.text = "${ids.size} ausgewählt"
                }
            }
        }

        b.btnMultiChat.setOnClickListener {
            val ids = vm.selectedIds.value.toList()
            if (ids.isNotEmpty()) {
                MultiChatSheet.newInstance(ids)
                    .show(parentFragmentManager, "multi_chat")
            }
        }

        b.btnCancelSelection.setOnClickListener { vm.clearSelection() }
    }

    override fun onDestroyView() {
        vm.clearSelection()
        super.onDestroyView()
        _b = null
    }
}
