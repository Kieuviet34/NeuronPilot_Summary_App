package com.mediatek.neuropilot.jnidemo.aibox.ai

import java.util.concurrent.CopyOnWriteArrayList

data class TranscriptEntry(
    val speaker: String,
    val sourceText: String,
    val timestamp: Long
)

object TranscriptHistory {
    private val entries = CopyOnWriteArrayList<TranscriptEntry>()

    fun append(speaker: String, sourceText: String, timestamp: Long) {
        entries.add(TranscriptEntry(speaker, sourceText, timestamp))
    }

    fun getAll(): List<TranscriptEntry> = entries.toList()

    fun isEmpty(): Boolean = entries.isEmpty()

    fun clear() {
        entries.clear()
    }

    fun buildTranscriptText(): String {
        return entries.joinToString("\n") { "[${it.speaker}]: ${it.sourceText}" }
    }
}
