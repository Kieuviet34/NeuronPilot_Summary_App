package com.bhs.meetingnotes.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private val TAG = "TtsManager"
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale("vi", "VN")
        } else {
            Log.e(TAG, "TTS Initialization failed")
        }
    }

    fun speak(text: String, language: String = "vi") {
        if (!isInitialized || text.isBlank()) return

        val locale = if (language.equals("en", ignoreCase = true)) Locale.US else Locale("vi", "VN")
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MeetingNotesTTS")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}