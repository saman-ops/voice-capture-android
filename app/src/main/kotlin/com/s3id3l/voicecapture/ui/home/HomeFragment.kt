package com.s3id3l.voicecapture.ui.home

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
import com.google.android.material.snackbar.Snackbar
import com.s3id3l.voicecapture.RecordingActivity
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.live.LiveRecordingActivity
import com.s3id3l.voicecapture.databinding.FragmentHomeBinding
import com.s3id3l.voicecapture.ui.detail.DetailActivity
import com.s3id3l.voicecapture.ui.history.RecordingAdapter
import com.s3id3l.voicecapture.ui.suggestions.SuggestionsBottomSheet
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private val vm: HomeViewModel by viewModels()
    private lateinit var adapter: RecordingAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = RecordingAdapter(onClick = { rec ->
            startActivity(
                Intent(requireContext(), DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_ID, rec.id)
            )
        })
        adapter.groupingEnabled = false   // Home shows a short recent list — no date headers
        b.recyclerRecent.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerRecent.adapter = adapter

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val rec = adapter.currentList[vh.adapterPosition]
                vm.delete(rec)
                Snackbar.make(b.root, "Aufnahme gelöscht", Snackbar.LENGTH_SHORT).show()
            }
        }).attachToRecyclerView(b.recyclerRecent)

        b.fabRecord.setOnClickListener { showRecordChooser() }

        b.btnSuggestions.setOnClickListener {
            SuggestionsBottomSheet().show(parentFragmentManager, "suggestions")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.recentRecordings.collect { list ->
                adapter.submitList(list)
                b.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                b.recyclerRecent.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showRecordChooser() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(
            com.s3id3l.voicecapture.R.layout.bottom_sheet_record_chooser, null
        )
        sheet.setContentView(view)

        view.findViewById<View>(com.s3id3l.voicecapture.R.id.opt_standard).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(requireContext(), RecordingActivity::class.java))
        }
        view.findViewById<View>(com.s3id3l.voicecapture.R.id.opt_transcription).setOnClickListener {
            sheet.dismiss()
            startActivity(
                Intent(requireContext(), RecordingActivity::class.java)
                    .putExtra(RecordingActivity.EXTRA_FORMAT, PrefsManager.FORMAT_RAW)
            )
        }
        view.findViewById<View>(com.s3id3l.voicecapture.R.id.opt_live).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(requireContext(), LiveRecordingActivity::class.java))
        }
        sheet.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
