package com.bhs.meetingnotes.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object FormatUtils {
    fun formatDate(timestampMs: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            String.format(Locale.getDefault(), "%d phút", minutes)
        } else {
            String.format(Locale.getDefault(), "%d giây", seconds)
        }
    }

    fun formatTimer(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    fun formatWordCount(count: Int, lang: String = "vi"): String {
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(count)
        return if (lang.equals("en", ignoreCase = true)) {
            "$formatted words"
        } else {
            "$formatted từ"
        }
    }

    fun formatActionCount(count: Int, lang: String = "vi"): String {
        return if (lang.equals("en", ignoreCase = true)) {
            "$count actions"
        } else {
            "$count hành động"
        }
    }

    fun formatFileSize(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format(Locale.US, "%.1f MB", mb)
    }
}