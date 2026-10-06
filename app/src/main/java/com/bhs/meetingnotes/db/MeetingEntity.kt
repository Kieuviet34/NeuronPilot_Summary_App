package com.bhs.meetingnotes.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val wordCount: Int = 0,
    val actionCount: Int = 0,
    val language: String = "vi", // "vi" or "en"
    val status: String = "COMPLETED", // "RECORDING", "PROCESSING", "COMPLETED", "PAUSED", "FAILED"
    val audioFilePath: String = "",
    val asrModel: String = "PhoWhisper (VI)",
    val llmModel: String = "Qwen2.5 3B (Q4)",
    val rawTranscript: String = "",
    val correctedTranscript: String = "",
    val summary: String = "",
    val actionItemsJson: String = "[]",
    val step1Progress: Int = 100,
    val step2Progress: Int = 100,
    val step3Progress: Int = 100,
    val step4Progress: Int = 100,
    val step1TextPreview: String = "",
    val step2TextPreview: String = "",
    val currentStep: Int = 4,
    val totalEstimatedTimeSeconds: Int = 300,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)