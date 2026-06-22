package com.s3id3l.voicecapture.live

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class ContinuousSpeechRecognizer(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var onPartialCallback: ((String) -> Unit)? = null
    private var onFinalCallback: ((String) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopped = false

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) onPartialCallback?.invoke(text)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) onFinalCallback?.invoke(text)
            if (!stopped) restart(100)
        }

        override fun onError(error: Int) {
            if (stopped) return
            val delay = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 100L
                else -> 500L
            }
            restart(delay)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit) {
        stopped = false
        onPartialCallback = onPartial
        onFinalCallback = onFinal
        createAndStart()
    }

    private fun createAndStart() {
        if (stopped) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(intent)
        }
    }

    private fun restart(delayMs: Long) {
        handler.postDelayed({ createAndStart() }, delayMs)
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        onPartialCallback = null
        onFinalCallback = null
    }
}
