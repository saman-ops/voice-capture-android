package com.s3id3l.voicecapture.ui.history

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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
                if (vm.showTrash.value) {
                    showRestoreDialog(rec)
                } else if (vm.selectedIds.value.isNotEmpty()) {
                    vm.toggleSelection(rec.id)
                } else {
                    startActivity(
                        Intent(requireContext(), DetailActivity::class.java)
                            .putExtra(DetailActivity.EXTRA_ID, rec.id)
                    )
                }
            },
            onLongClick = { rec ->
                if (!vm.showTrash.value) vm.toggleSelection(rec.id)
            }
        )
        b.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerHistory.adapter = adapter

        // Search
        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                vm.setSearchQuery(s?.toString() ?: "")
            }
        })

        // Filter chips (active only when not in trash)
        listOf(
            "all"                             to "Alle",
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

        // Swipe: left = soft-delete (active), left = permanently delete (trash)
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                if (vm.selectedIds.value.isNotEmpty()) {
                    adapter.notifyItemChanged(vh.adapterPosition)
                    return
                }
                val rec = adapter.currentList[vh.adapterPosition]
                if (vm.showTrash.value) {
                    vm.permanentlyDelete(rec)
                    Snackbar.make(b.root, "Endgültig gelöscht", Snackbar.LENGTH_SHORT).show()
                } else {
                    vm.delete(rec)
                    Snackbar.make(b.root, "In Papierkorb verschoben", Snackbar.LENGTH_LONG)
                        .setAction("Rückgängig") { vm.restore(rec) }
                        .show()
                }
            }
        }).attachToRecyclerView(b.recyclerHistory)

        // Observe active or trash list
        viewLifecycleOwner.lifecycleScope.launch {
            vm.showTrash.collect { inTrash ->
                b.btnTrash.text = if (inTrash) "← Verlauf" else "🗑 Papierkorb"
                b.filterChips.visibility = if (inTrash) View.GONE else View.VISIBLE
                b.filterScroll.visibility = if (inTrash) View.GONE else View.VISIBLE
                b.etSearch.visibility = if (inTrash) View.GONE else View.VISIBLE
                // Date-group headers only make sense in the active (createdAt-sorted) view
                adapter.groupingEnabled = !inTrash
                // Push the correct list immediately when the view switches
                if (inTrash) {
                    val list = vm.trashRecordings.value
                    adapter.submitList(list)
                    b.emptyHistory.text = "Papierkorb ist leer"
                    b.emptyHistory.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    val list = vm.recordings.value
                    adapter.submitList(list)
                    b.emptyHistory.text = "Keine Aufnahmen vorhanden"
                    b.emptyHistory.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.recordings.collect { list ->
                if (!vm.showTrash.value) {
                    adapter.submitList(list)
                    b.emptyHistory.text = "Keine Aufnahmen vorhanden"
                    b.emptyHistory.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.trashRecordings.collect { list ->
                if (vm.showTrash.value) {
                    adapter.submitList(list)
                    b.emptyHistory.text = "Papierkorb ist leer"
                    b.emptyHistory.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        // Multi-select bar
        viewLifecycleOwner.lifecycleScope.launch {
            vm.selectedIds.collect { ids ->
                adapter.selectedIds = ids
                if (ids.isEmpty()) {
                    b.multiSelectBar.visibility = View.GONE
                } else {
                    b.multiSelectBar.visibility = View.VISIBLE
                    b.tvSelectionCount.text = "${ids.size} ausgewählt"
                    b.btnMerge.visibility = if (ids.size >= 2) View.VISIBLE else View.GONE
                }
            }
        }

        b.btnTrash.setOnClickListener { vm.toggleTrash() }

        b.btnMultiChat.setOnClickListener {
            val ids = vm.selectedIds.value.toList()
            if (ids.isNotEmpty()) {
                MultiChatSheet.newInstance(ids).show(parentFragmentManager, "multi_chat")
            }
        }

        b.btnMerge.setOnClickListener {
            val count = vm.selectedIds.value.size
            AlertDialog.Builder(requireContext())
                .setTitle("$count Aufnahmen zusammenführen?")
                .setMessage("Die ausgewählten Aufnahmen werden zu einer neuen Aufnahme zusammengeführt. Die Originale bleiben erhalten.")
                .setPositiveButton("Zusammenführen") { _, _ -> vm.mergeSelected() }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        b.btnCancelSelection.setOnClickListener { vm.clearSelection() }
    }

    private fun showRestoreDialog(rec: RecordingEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(rec.title.ifEmpty { "Aufnahme" })
            .setMessage("Diese Aufnahme aus dem Papierkorb wiederherstellen?")
            .setPositiveButton("Wiederherstellen") { _, _ ->
                vm.restore(rec)
                Snackbar.make(b.root, "Wiederhergestellt", Snackbar.LENGTH_SHORT).show()
            }
            .setNeutralButton("Endgültig löschen") { _, _ ->
                vm.permanentlyDelete(rec)
                Snackbar.make(b.root, "Endgültig gelöscht", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    override fun onDestroyView() {
        vm.clearSelection()
        super.onDestroyView()
        _b = null
    }
}
