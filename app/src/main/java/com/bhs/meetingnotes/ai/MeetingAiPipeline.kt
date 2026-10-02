package com.bhs.meetingnotes.ai

import android.content.Context
import android.util.Log
import com.bhs.meetingnotes.db.MeetingDatabase
import com.bhs.meetingnotes.db.MeetingEntity
import com.mediatek.neuropilot.jnidemo.aibox.ai.NeuroPilotLlmBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MeetingAiPipeline(private val context: Context) {
    private val TAG = "MeetingAiPipeline"
    private val db = MeetingDatabase.getInstance(context)
    private val DEBUG_MODE = true

    suspend fun runPipeline(
        meetingId: Long,
        onProgress: (step: Int, progress: Int, currentText: String) -> Unit
    ): MeetingEntity? = withContext(Dispatchers.IO) {
        var meeting = db.meetingDao().getMeetingById(meetingId) ?: return@withContext null

        meeting = meeting.copy(status = "PROCESSING")
        db.meetingDao().updateMeeting(meeting)

        val bridge = NeuroPilotLlmBridge()
        if (!bridge.initDefaultModel()) {
            Log.e(TAG, "Failed to init NeuroPilotLlmBridge")
            meeting = meeting.copy(status = "FAILED")
            db.meetingDao().updateMeeting(meeting)
            return@withContext meeting
        }

        try {
            // STEP 1: Show original transcript
            onProgress(1, 100, meeting.rawTranscript)

            // STEP 2: Correction
            onProgress(2, 0, "")
            val rawTranscript = meeting.rawTranscript
            
            if (DEBUG_MODE) {
                Log.d(TAG, "Debug: Raw Transcript = $rawTranscript")
            }

            val correctedResult = bridge.correctTextBlocking(rawTranscript) { text, stats ->
                onProgress(2, 50, text + (stats ?: ""))
            }
            meeting = meeting.copy(correctedTranscript = correctedResult.text, step2Progress = 100)
            db.meetingDao().updateMeeting(meeting)
            onProgress(2, 100, correctedResult.text + correctedResult.statsLog)
            
            if (DEBUG_MODE) {
                Log.d(TAG, "Debug: DB Record after step 2 = ${db.meetingDao().getMeetingById(meetingId)}")
            }

            delay(500)

            // STEP 3: Summarization
            onProgress(3, 0, "")
            val summaryResult = bridge.summarizeBlocking(correctedResult.text) { text, stats ->
                onProgress(3, 50, text + (stats ?: ""))
            }
            meeting = meeting.copy(summary = summaryResult.text, step3Progress = 100)
            db.meetingDao().updateMeeting(meeting)
            onProgress(3, 100, summaryResult.text + summaryResult.statsLog)

            if (DEBUG_MODE) {
                Log.d(TAG, "Debug: DB Record after step 3 = ${db.meetingDao().getMeetingById(meetingId)}")
            }

            delay(500)

            // STEP 4: Action Extraction
            onProgress(4, 0, "")
            val actionResult = bridge.extractActionsBlocking(correctedResult.text) { text, stats ->
                onProgress(4, 50, text + (stats ?: ""))
            }
            
            meeting = meeting.copy(
                actionItemsJson = formatActionsAsJson(actionResult.text),
                step4Progress = 100,
                status = "COMPLETED"
            )
            db.meetingDao().updateMeeting(meeting)
            onProgress(4, 100, actionResult.text + actionResult.statsLog)
            
            if (DEBUG_MODE) {
                Log.d(TAG, "Debug: DB Record after step 4 (final) = ${db.meetingDao().getMeetingById(meetingId)}")
            }

        } finally {
            bridge.close()
        }

        meeting
    }

    private fun formatActionsAsJson(rawOutput: String): String {
        val cleaned = rawOutput.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (cleaned.startsWith("[")) {
            return cleaned
        }
        return "[]"
    }
}