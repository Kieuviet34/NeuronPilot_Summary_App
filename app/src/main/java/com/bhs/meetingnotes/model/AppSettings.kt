package com.bhs.meetingnotes.model

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("meeting_notes_prefs", Context.MODE_PRIVATE)

    var language: String
        get() = prefs.getString("language", "vi") ?: "vi"
        set(value) = prefs.edit().putString("language", value).apply()

    var asrModel: String
        get() = prefs.getString("asr_model", if (language == "vi") "PhoWhisper (VI)" else "Whisper (EN)") ?: "PhoWhisper (VI)"
        set(value) = prefs.edit().putString("asr_model", value).apply()

    var cpuThreads: Int
        get() = prefs.getInt("cpu_threads", 4)
        set(value) = prefs.edit().putInt("cpu_threads", value).apply()

    var temperature: Float
        get() = prefs.getFloat("temperature", 0.3f)
        set(value) = prefs.edit().putFloat("temperature", value).apply()

    var sampleRate: Int
        get() = prefs.getInt("sample_rate", 16000)
        set(value) = prefs.edit().putInt("sample_rate", value).apply()

    var bitDepth: Int
        get() = prefs.getInt("bit_depth", 16)
        set(value) = prefs.edit().putInt("bit_depth", value).apply()

    var channels: String
        get() = prefs.getString("channels", "Mono") ?: "Mono"
        set(value) = prefs.edit().putString("channels", value).apply()

    var silenceDetection: Boolean
        get() = prefs.getBoolean("silence_detection", true)
        set(value) = prefs.edit().putBoolean("silence_detection", value).apply()

    var defaultExportFormat: String
        get() = prefs.getString("default_export_format", "PDF") ?: "PDF"
        set(value) = prefs.edit().putString("default_export_format", value).apply()

    companion object {
        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                val instance = AppSettings(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}