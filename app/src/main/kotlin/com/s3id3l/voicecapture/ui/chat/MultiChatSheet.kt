package com.s3id3l.voicecapture.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.s3id3l.voicecapture.api.LlmClient
import com.s3id3l.voicecapture.data.PrefsManager
import com.s3id3l.voicecapture.data.db.ChatMessageEntity
import com.s3id3l.voicecapture.data.db.RecordingDatabase
import com.s3id3l.voicecapture.data.db.RecordingEntity
import com.s3id3l.voicecapture.databinding.BottomSheetMultiChatBinding
import com.s3id3l.voicecapture.ui.detail.ChatAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MultiChatSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_IDS = "recording_ids"

        fun newInstance(ids: List<Long>): MultiChatSheet {
            return MultiChatSheet().apply {
                arguments = Bundle().apply {
                    putLongArray(ARG_IDS, ids.toLongArray())
                }
            }
        }
    }

    private var _b: BottomSheetMultiChatBinding? = null
    private val b get() = _b!!

    private val chatHistory = mutableListOf<ChatMessageEntity>()
    private var chatAdapter: ChatAdapter? = null
    private var recordings: List<RecordingEntity> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = BottomSheetMultiChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ids = arguments?.getLongArray(ARG_IDS)?.toList() ?: return

        val adapter = ChatAdapter().also { chatAdapter = it }
        b.recyclerChat.adapter = adapter
        b.recyclerChat.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }

        viewLifecycleOwner.lifecycleScope.launch {
            val db = RecordingDatabase.getInstance(requireContext())
            recordings = withContext(Dispatchers.IO) {
                ids.mapNotNull { db.recordingDao().getById(it) }
            }
            b.tvContextLabel.text = "Kontext: ${recordings.joinToString(" · ") { it.title.ifEmpty { "Aufnahme" } }}"
        }

        b.btnSend.setOnClickListener {
            val text = b.etInput.text.toString().trim()
            if (text.isEmpty() || recordings.isEmpty()) return@setOnClickListener
            b.etInput.text?.clear()

            val userMsg = ChatMessageEntity(recordingId = -1, role = "user", content = text)
            chatHistory.add(userMsg)
            chatAdapter?.submitList(chatHistory.toList())
            b.recyclerChat.scrollToPosition(chatHistory.size - 1)

            b.progressChat.visibility = View.VISIBLE
            b.btnSend.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val prefs = PrefsManager(requireContext())
                    val reply = withContext(Dispatchers.IO) {
                        LlmClient(prefs).chatMultiple(recordings, chatHistory, text)
                    }
                    val assistantMsg = ChatMessageEntity(recordingId = -1, role = "assistant", content = reply)
                    chatHistory.add(assistantMsg)
                    chatAdapter?.submitList(chatHistory.toList())
                    b.recyclerChat.scrollToPosition(chatHistory.size - 1)
                } catch (e: Exception) {
                    val errMsg = ChatMessageEntity(recordingId = -1, role = "assistant", content = "Fehler: ${e.message}")
                    chatHistory.add(errMsg)
                    chatAdapter?.submitList(chatHistory.toList())
                } finally {
                    b.progressChat.visibility = View.GONE
                    b.btnSend.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
