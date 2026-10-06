package com.bhs.meetingnotes.ai

import android.content.Context
import android.util.Log
import com.bhs.meetingnotes.db.MeetingDatabase
import com.bhs.meetingnotes.db.MeetingEntity
import com.bhs.meetingnotes.db.SegmentEntity
import com.mediatek.neuropilot.jnidemo.aibox.ai.NeuroPilotLlmBridge
import com.mediatek.neuropilot.jnidemo.aibox.ai.WhisperServerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Điều phối Pipeline AI 4 bước theo chuẩn BA v2:
 * 1. ASR đa phân đoạn: whisper-server loopback (127.0.0.1, vẫn offline - C1); không có server thì dùng văn bản mẫu.
 * 2. Sửa lỗi thuật ngữ bằng luật xác định + từ điển alias (I7), ghi edit_log (I8). KHÔNG dùng LLM sinh lại toàn văn (D6).
 * 3. Tóm tắt cuộc họp (Qwen2.5 3B trên NPU).
 * 4. Trích xuất Action Items.
 *
 * Plan A/C: NeuroPilot NPU. Plan B (C13): máy không có NPU (vd LDPlayer) vẫn chạy trọn luồng UI.
 */
class MeetingAiPipeline(private val context: Context, private val whisperPort: Int = 8080) {
    private val TAG = "MeetingAiPipeline"
    private val db = MeetingDatabase.getInstance(context)
    private val glossary = GlossaryRepository(db)

    suspend fun runPipeline(
        meetingId: Long,
        onProgress: (step: Int, progress: Int, currentText: String) -> Unit
    ): MeetingEntity? = withContext(Dispatchers.IO) {
        var meeting = db.meetingDao().getMeetingById(meetingId) ?: return@withContext null

        meeting = meeting.copy(status = "PROCESSING")
        db.meetingDao().updateMeeting(meeting)

        val bridge = NeuroPilotLlmBridge()
        if (!bridge.initDefaultModel()) {
            Log.w(TAG, "NeuroPilot NPU unavailable. Switching to Plan B (CPU / baseline fallback).")
            return@withContext runPlanB(meeting, onProgress)
        }

        try {
            // STEP 1: ASR (whisper-server nếu có) -> raw transcript, luôn được lưu (C10)
            val segments = db.segmentDao().getSelectedSegmentsForMeeting(meetingId)
            var rawTranscript = meeting.rawTranscript
            if (rawTranscript.isBlank() && segments.isNotEmpty()) {
                rawTranscript = transcribeSegments(segments, onProgress) ?: placeholderTranscript(segments)
                meeting = meeting.copy(rawTranscript = rawTranscript, wordCount = countWords(rawTranscript))
                db.meetingDao().updateMeeting(meeting)
            }
            onProgress(1, 100, rawTranscript)

            // STEP 2: luật + alias (không LLM)
            onProgress(2, 0, "")
            val corrected = glossary.correctAndLog(meetingId, rawTranscript)
            meeting = meeting.copy(
                correctedTranscript = corrected.text,
                wordCount = countWords(corrected.text),
                step2Progress = 100
            )
            db.meetingDao().updateMeeting(meeting)
            onProgress(2, 100, corrected.text + ruleNote(corrected))

            delay(500)

            // STEP 3: Summarization
            onProgress(3, 0, "")
            val summaryResult = bridge.summarizeBlocking(corrected.text) { text, stats ->
                onProgress(3, 50, text + (stats ?: ""))
            }
            meeting = meeting.copy(summary = summaryResult.text, step3Progress = 100)
            db.meetingDao().updateMeeting(meeting)
            onProgress(3, 100, summaryResult.text + summaryResult.statsLog)

            delay(500)

            // STEP 4: Action Extraction
            onProgress(4, 0, "")
            val actionResult = bridge.extractActionsBlocking(corrected.text) { text, stats ->
                onProgress(4, 50, text + (stats ?: ""))
            }
            meeting = meeting.copy(
                actionItemsJson = formatActionsAsJson(actionResult.text),
                step4Progress = 100,
                status = "COMPLETED"
            )
            db.meetingDao().updateMeeting(meeting)
            onProgress(4, 100, actionResult.text + actionResult.statsLog)
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error", e)
            meeting = meeting.copy(status = "FAILED")
            db.meetingDao().updateMeeting(meeting)
        } finally {
            bridge.close()
        }

        meeting
    }

    /**
     * Plan B (C13): không có NPU/LLM. ASR thật nếu có whisper-server, sửa lỗi bằng luật luôn chạy thật.
     * Tóm tắt/hành động chỉ là DỮ LIỆU MẪU khi không có ASR thật, và được gắn nhãn rõ ràng.
     */
    private suspend fun runPlanB(
        initialMeeting: MeetingEntity,
        onProgress: (step: Int, progress: Int, currentText: String) -> Unit
    ): MeetingEntity {
        var meeting = initialMeeting
        val meetingId = meeting.id

        // STEP 1
        val segments = db.segmentDao().getSelectedSegmentsForMeeting(meetingId)
        onProgress(1, 10, "Đang nhận dạng giọng nói...")
        val asrText = if (meeting.rawTranscript.isBlank()) transcribeSegments(segments, onProgress) else meeting.rawTranscript
        
        // Kiểm tra xem có giọng nói hợp lệ hay toàn bộ là khoảng lặng/hallucination
        val hasRealSpeech = !asrText.isNullOrBlank() && asrText.any { it.isLetterOrDigit() }
        val isSampleData = !hasRealSpeech && segments.isEmpty()
        
        val rawTranscript = when {
            hasRealSpeech -> asrText!!
            segments.isNotEmpty() -> "[Không phát hiện giọng nói trong các đoạn ghi âm đã chọn. Toàn bộ phân đoạn ở trạng thái im lặng hoặc âm lượng quá nhỏ]."
            else -> SAMPLE_RAW_TRANSCRIPT
        }
        
        meeting = meeting.copy(rawTranscript = rawTranscript, wordCount = countWords(rawTranscript), step1Progress = 100)
        db.meetingDao().updateMeeting(meeting)
        val sampleTag = if (isSampleData) "\n\n[Plan B: không có whisper-server, dùng văn bản mẫu]" else ""
        onProgress(1, 100, rawTranscript + sampleTag)
        delay(400)

        // STEP 2 (luật thật)
        onProgress(2, 30, "Đang áp dụng từ điển thuật ngữ (alias)...")
        val corrected = if (hasRealSpeech) {
            glossary.correctAndLog(meetingId, rawTranscript)
        } else {
            CorrectionResult(rawTranscript, emptyList())
        }
        meeting = meeting.copy(
            correctedTranscript = corrected.text,
            wordCount = countWords(corrected.text),
            step2Progress = 100
        )
        db.meetingDao().updateMeeting(meeting)
        onProgress(2, 100, corrected.text + ruleNote(corrected))
        delay(400)

        // STEP 3 + 4 (không có LLM)
        val summary = when {
            !hasRealSpeech && segments.isNotEmpty() -> "Không thể tạo tóm tắt do cuộc họp không có nội dung giọng nói nào được ghi nhận."
            isSampleData -> SAMPLE_SUMMARY
            else -> NO_LLM_SUMMARY
        }
        val actionsJson = if (isSampleData) SAMPLE_ACTIONS_JSON else "[]"

        onProgress(3, 60, "Plan B: hoàn tất xử lý...")
        delay(400)
        meeting = meeting.copy(summary = summary, step3Progress = 100)
        db.meetingDao().updateMeeting(meeting)
        onProgress(3, 100, summary)
        delay(400)

        onProgress(4, 60, "Plan B: kiểm tra hành động...")
        delay(400)
        meeting = meeting.copy(actionItemsJson = actionsJson, step4Progress = 100, status = "COMPLETED")
        db.meetingDao().updateMeeting(meeting)
        onProgress(4, 100, if (isSampleData) "Dữ liệu mẫu Plan B: 3 hành động" else "Đã hoàn tất xử lý cuộc họp")

        return meeting
    }

    /**
     * Lọc sạch kết quả ASR, loại bỏ ảo giác (Hallucination) của Whisper khi audio chỉ có tiếng ồn hoặc im lặng.
     */
    private fun sanitizeAsrText(raw: String): String {
        var text = raw.trim()
        text = text.trim('.', ',', '!', '?', '-', '_', '"', '\'', ':', ';', ' ')
        val lower = text.lowercase().trim()
        val hallucinations = listOf(
            "andra", "subscribe", "đăng ký kênh", "hãy đăng ký",
            "cảm ơn các bạn đã theo dõi", "cảm ơn đã xem", "hẹn gặp lại",
            "like và share", "để không bỏ lỡ", "video hấp dẫn",
            "ghiền mì gõ", "thank you for watching", "bye", "you", "thanks for watching"
        )
        for (h in hallucinations) {
            if (lower == h || lower == "$h." || lower == "$h!") {
                return ""
            }
        }
        if (!text.any { it.isLetterOrDigit() }) {
            return ""
        }
        return text
    }

    /**
     * ASR từng segment qua whisper-server loopback. Trả về null nếu không có kết quả
     * (server tắt/không có file/audio im lặng) để caller dùng đường dự phòng.
     * Các đoạn ngăn cách bởi `---` (I5).
     */
    private fun transcribeSegments(
        segments: List<SegmentEntity>,
        onProgress: (step: Int, progress: Int, currentText: String) -> Unit
    ): String? {
        if (segments.isEmpty()) return null
        val client = WhisperServerClient()
        val parts = ArrayList<String>()
        for ((idx, seg) in segments.withIndex()) {
            val file = File(seg.filePath)
            if (!file.exists()) continue
            val rawResult = try {
                client.transcribeFile(file, seg.fileName).trim()
            } catch (e: Exception) {
                Log.w(TAG, "whisper-server unavailable: ${e.message}")
                return parts.joinToString(SEGMENT_SEPARATOR).ifBlank { null }
            }
            val cleanedText = sanitizeAsrText(rawResult)
            if (cleanedText.isNotEmpty()) {
                parts.add(cleanedText)
            }
            onProgress(1, (idx + 1) * 100 / segments.size, parts.joinToString(SEGMENT_SEPARATOR))
        }
        return parts.joinToString(SEGMENT_SEPARATOR).ifBlank { null }
    }

    private fun placeholderTranscript(segments: List<SegmentEntity>): String =
        segments.joinToString(SEGMENT_SEPARATOR) {
            "...Nội dung ghi âm đoạn ${it.segmentIndex} (${it.fileName}) qua I2S MIC..."
        }

    private fun ruleNote(result: CorrectionResult): String =
        "\n\n[Luật xác định: ${result.edits.size} chỉnh sửa thuật ngữ, có thể xem/hoàn tác ở tab Transcript]"

    private fun countWords(text: String): Int = text.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }

    private fun formatActionsAsJson(rawOutput: String): String {
        val cleaned = rawOutput.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return if (cleaned.startsWith("[")) cleaned else "[]"
    }

    private companion object {
        const val SEGMENT_SEPARATOR = "\n---\n"

        const val NO_LLM_SUMMARY =
            "[Plan B] Thiết bị này chưa có LLM (NPU) nên chưa tóm tắt tự động.\nXem nội dung đầy đủ ở tab Transcript."

        val SAMPLE_RAW_TRANSCRIPT = """
            hôm nay chúng ta họp review tiến độ mang lại b ét pê cho bo g bảy hai mươi ai bốc.
            anh hoàng đã tích hợp xong i hai ét đờ rai vơ và kiểm tra mức âm lượng rờ mờ ét ổn định.
            tiếp theo nhóm phần mềm cần đo đạc nhiệt độ ét ô xi dưới bảy mươi lăm độ xê và trần ram dưới năm phẩy năm giga bai.
            trong sờ prin tới, anh tuấn sẽ hoàn thiện bản vá kơ nần và gửi báo cáo trước mười tháng mười.
            chị linh phụ trách kiểm thử mô hình pho quýt xờ bơ trên môi trường pít đúp bờ liu.
        """.trimIndent()

        val SAMPLE_SUMMARY = """
            [Dữ liệu mẫu Plan B - không phải tóm tắt thật]
            1. Nội dung đã thảo luận:
            - Tiến độ bring-up BSP trên G720 AI Box, kiểm thử I2S driver.
            - Kế hoạch đo nhiệt SoC và ngân sách RAM.
            2. Quyết định:
            - Giới hạn RAM ≤ 5.5 GB và nhiệt SoC < 75°C.
            3. Vấn đề kỹ thuật:
            - Độ ổn định ghi âm trên Foreground Service microphone.
        """.trimIndent()

        val SAMPLE_ACTIONS_JSON = """
            [
              {"id": 1, "task": "Hoàn thiện bản vá kernel và gửi báo cáo", "assignee": "Anh Tuấn", "deadline": "10/10/2026"},
              {"id": 2, "task": "Đo nhiệt SoC (<75°C) và RAM peak (≤5.5 GB)", "assignee": "Anh Hoàng", "deadline": "12/10/2026"},
              {"id": 3, "task": "Kiểm thử chất lượng ASR PhoWhisper", "assignee": "Chị Linh", "deadline": "15/10/2026"}
            ]
        """.trimIndent()
    }
}