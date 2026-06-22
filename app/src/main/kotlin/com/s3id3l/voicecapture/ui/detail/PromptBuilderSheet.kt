package com.s3id3l.voicecapture.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.s3id3l.voicecapture.databinding.BottomSheetPromptBuilderBinding

class PromptBuilderSheet(
    private val context: Context,
    private val vm: DetailViewModel,
    private val anchorView: View,
) {
    fun show() {
        val sheet = BottomSheetDialog(context)
        val b = BottomSheetPromptBuilderBinding.inflate(LayoutInflater.from(context))
        sheet.setContentView(b.root)

        b.btnBuildPrompt.setOnClickListener {
            val input = b.etPromptInput.text.toString().trim()
            if (input.isEmpty()) return@setOnClickListener
            b.progressPrompt.visibility = View.VISIBLE
            b.tvPromptResult.visibility = View.GONE
            b.promptActions.visibility = View.GONE
            b.btnBuildPrompt.isEnabled = false

            vm.buildPrompt(input) { result ->
                b.progressPrompt.visibility = View.GONE
                b.btnBuildPrompt.isEnabled = true
                if (result != null) {
                    b.tvPromptResult.text = result
                    b.tvPromptResult.visibility = View.VISIBLE
                    b.promptActions.visibility = View.VISIBLE
                } else {
                    Snackbar.make(anchorView, "Fehler beim Erstellen des Prompts", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        b.btnCopyPrompt.setOnClickListener {
            val text = b.tvPromptResult.text.toString()
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Prompt", text))
            Snackbar.make(anchorView, "Prompt kopiert", Snackbar.LENGTH_SHORT).show()
        }

        b.btnAddToRecording.setOnClickListener {
            val text = b.tvPromptResult.text.toString()
            vm.appendToRecording(text)
            Snackbar.make(anchorView, "Zur Aufnahme hinzugefügt", Snackbar.LENGTH_SHORT).show()
            sheet.dismiss()
        }

        sheet.show()
    }
}
