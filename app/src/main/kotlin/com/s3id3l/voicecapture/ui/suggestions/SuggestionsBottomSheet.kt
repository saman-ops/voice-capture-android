package com.s3id3l.voicecapture.ui.suggestions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.s3id3l.voicecapture.databinding.BottomSheetSuggestionsBinding
import kotlinx.coroutines.launch

class SuggestionsBottomSheet : BottomSheetDialogFragment() {

    private var _b: BottomSheetSuggestionsBinding? = null
    private val b get() = _b!!
    private val vm: SuggestionsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = BottomSheetSuggestionsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.initDefaults()
        val adapter = SuggestionAdapter { vm.queue(it) }
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            vm.suggestions.collect { adapter.submitList(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.message.collect { Snackbar.make(b.root, it, Snackbar.LENGTH_LONG).show() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
