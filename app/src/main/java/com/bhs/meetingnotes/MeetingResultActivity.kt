package com.bhs.meetingnotes

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bhs.meetingnotes.db.EditLogEntity
import com.bhs.meetingnotes.db.MeetingDatabase
import com.bhs.meetingnotes.db.MeetingEntity
import com.bhs.meetingnotes.model.ActionItem
import com.bhs.meetingnotes.util.FormatUtils
import com.bhs.meetingnotes.util.ReportExporter
import com.bhs.meetingnotes.util.TtsManager
import com.mediatek.neuropilot.jnidemo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Màn hình Kết quả Cuộc họp (3 Tab: Tóm tắt, Hành động, Transcript) theo chuẩn BA v2.
 * Hỗ trợ Text-to-Speech (TTS) đọc tóm tắt qua loa và Xuất báo cáo PDF/DOCX/TXT.
 */
class MeetingResultActivity : AppCompatActivity() {

    private lateinit var db: MeetingDatabase
    private var ttsManager: TtsManager? = null

    private var currentMeeting: MeetingEntity? = null
    private var summaryText: String = ""
    private var meetingLang: String = "vi"
    private var isTtsPlaying = false

    // Tabs Header
    private var tabSummary: TextView? = null
    private var tabActions: TextView? = null
    private var tabTranscript: TextView? = null

    // Tabs Containers
    private var layoutTabSummary: ScrollView? = null
    private var layoutTabActions: ScrollView? = null
    private var layoutTabTranscript: ScrollView? = null

    // Content Views
    private var tvSummaryContent: TextView? = null
    private var tvTranscriptContent: TextView? = null
    private var llTabActionsContainer: LinearLayout? = null
    private var llActionsContainer: LinearLayout? = null
    private var btnReadSummary: TextView? = null
    private var tvTranscriptTitle: TextView? = null
    private var btnTranscriptToggle: TextView? = null
    private var btnTranscriptEdits: TextView? = null

    private var correctedText: String = ""
    private var rawText: String = ""
    private var showingRaw = false
    private var edits: List<EditLogEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_result)

        db = MeetingDatabase.getInstance(this)
        ttsManager = TtsManager(this)

        initViews()

        val meetingId = intent.getLongExtra("MEETING_ID", -1L)
        if (meetingId != -1L) {
            loadMeetingData(meetingId)
        }
    }

    private fun initViews() {
        tabSummary = findViewById(R.id.tab_summary)
        tabActions = findViewById(R.id.tab_actions)
        tabTranscript = findViewById(R.id.tab_transcript)

        layoutTabSummary = findViewById(R.id.layout_tab_summary)
        layoutTabActions = findViewById(R.id.layout_tab_actions)
        layoutTabTranscript = findViewById(R.id.layout_tab_transcript)

        tvSummaryContent = findViewById(R.id.tv_summary_content)
        tvTranscriptContent = findViewById(R.id.tv_transcript_content)
        llTabActionsContainer = findViewById(R.id.ll_tab_actions_container)
        llActionsContainer = findViewById(R.id.ll_actions_container)
        btnReadSummary = findViewById(R.id.btn_read_summary)
        tvTranscriptTitle = findViewById(R.id.tv_transcript_title)
        btnTranscriptToggle = findViewById(R.id.btn_transcript_toggle)
        btnTranscriptEdits = findViewById(R.id.btn_transcript_edits)

        btnTranscriptToggle?.setOnClickListener {
            showingRaw = !showingRaw
            renderTranscript()
        }
        btnTranscriptEdits?.setOnClickListener { showEditsDialog() }

        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            finish()
        }

        // Tab switching
        tabSummary?.setOnClickListener { selectTab(0) }
        tabActions?.setOnClickListener { selectTab(1) }
        tabTranscript?.setOnClickListener { selectTab(2) }

        // TTS Read Summary
        btnReadSummary?.setOnClickListener {
            toggleTts()
        }

        // Export Report
        findViewById<View>(R.id.btn_export)?.setOnClickListener {
            showExportDialog()
        }
    }

    private fun selectTab(tabIndex: Int) {
        val selectedColor = ContextCompat.getColor(this, R.color.blue_primary)
        val normalColor = ContextCompat.getColor(this, R.color.text_secondary)

        // Reset Tab Text Colors
        tabSummary?.setTextColor(if (tabIndex == 0) selectedColor else normalColor)
        tabActions?.setTextColor(if (tabIndex == 1) selectedColor else normalColor)
        tabTranscript?.setTextColor(if (tabIndex == 2) selectedColor else normalColor)

        // Toggle Container Visibility
        layoutTabSummary?.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        layoutTabActions?.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        layoutTabTranscript?.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
    }

    private fun loadMeetingData(id: Long) {
        lifecycleScope.launch {
            val meeting = withContext(Dispatchers.IO) {
                db.meetingDao().getMeetingById(id)
            }
            if (meeting != null) {
                currentMeeting = meeting
                summaryText = meeting.summary
                meetingLang = meeting.language

                findViewById<TextView>(R.id.tv_header_title)?.text = meeting.title
                findViewById<TextView>(R.id.tv_duration)?.text = FormatUtils.formatDuration(meeting.durationMs)
                findViewById<TextView>(R.id.tv_word_count)?.text = "${meeting.wordCount} từ"
                findViewById<TextView>(R.id.tv_language_display)?.text = if (meeting.language == "en") "🇺🇸 English" else "🇻🇳 Tiếng Việt"
                findViewById<TextView>(R.id.tv_models_display)?.text = "✓ ${meeting.asrModel} + ${meeting.llmModel}"

                tvSummaryContent?.text = if (meeting.summary.isNotBlank()) meeting.summary else "Chưa có bản tóm tắt."
                correctedText = meeting.correctedTranscript.ifBlank { meeting.rawTranscript }
                rawText = meeting.rawTranscript
                edits = withContext(Dispatchers.IO) { db.editLogDao().getForMeeting(id) }
                renderTranscript()

                // Populate Actions
                val actions = ActionItem.fromJson(meeting.actionItemsJson)
                findViewById<TextView>(R.id.tv_actions_header)?.text = "📋 Hành động tiếp theo (${actions.size})"

                populateActionsViews(actions)
            }
        }
    }

    private fun renderTranscript() {
        val text = if (showingRaw) rawText else correctedText
        if (text.isBlank()) {
            tvTranscriptContent?.text = "Chưa có nội dung văn bản."
        } else if (rawText.isNotBlank() && correctedText.isNotBlank()) {
            val diffResult = com.bhs.meetingnotes.util.WordDiff.diff(rawText, correctedText)
            val tokens = if (showingRaw) diffResult.raw else diffResult.clean
            val ssb = android.text.SpannableStringBuilder()
            val bgCol = ContextCompat.getColor(this, if (showingRaw) R.color.diff_error_bg else R.color.diff_fix_bg)
            val fgCol = ContextCompat.getColor(this, if (showingRaw) R.color.diff_error else R.color.ok_green)

            for (token in tokens) {
                val start = ssb.length
                ssb.append(token.text).append(" ")
                val end = ssb.length - 1
                if (token.changed) {
                    ssb.setSpan(android.text.style.BackgroundColorSpan(bgCol), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(android.text.style.ForegroundColorSpan(fgCol), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            tvTranscriptContent?.text = ssb
        } else {
            tvTranscriptContent?.text = text
        }
        tvTranscriptTitle?.text = if (showingRaw) "📝 Văn bản gốc (PhoWhisper ASR - Chưa sửa)" else "📝 Toàn bộ văn bản cuộc họp (Đã chuẩn hoá thuật ngữ)"
        btnTranscriptToggle?.text = if (showingRaw) "✅ Xem bản đã sửa" else "👁 Xem bản gốc"
        btnTranscriptEdits?.text = "✏️ ${edits.size} chỉnh sửa"
    }

    private fun showEditsDialog() {
        if (edits.isEmpty()) {
            Toast.makeText(this, "Không có chỉnh sửa nào trong cuộc họp này", Toast.LENGTH_SHORT).show()
            return
        }
        val lines = edits.map { "“${it.beforeText}” → “${it.afterText}”  (${it.rule})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Danh sách chỉnh sửa (${edits.size})")
            .setItems(lines, null)
            .setPositiveButton("Đóng", null)
            .show()
    }

    private fun populateActionsViews(actions: List<ActionItem>) {
        llActionsContainer?.removeAllViews()
        llTabActionsContainer?.removeAllViews()

        if (actions.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Không phát hiện hành động nào cần thực hiện sau cuộc họp."
                setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                textSize = 14f
                setPadding(0, 16, 0, 16)
            }
            llActionsContainer?.addView(emptyTv)
            return
        }

        for (action in actions) {
            // Sidebar item
            val sidebarView = layoutInflater.inflate(R.layout.item_action_detail, llActionsContainer, false)
            sidebarView.findViewById<TextView>(R.id.tv_task_name).text = action.task
            sidebarView.findViewById<TextView>(R.id.tv_assignee).text = "Người phụ trách: ${action.assignee}"
            sidebarView.findViewById<TextView>(R.id.tv_deadline).text = "Hạn chót: ${action.deadline}"
            sidebarView.findViewById<TextView>(R.id.tv_summarize_task).visibility = View.GONE
            llActionsContainer?.addView(sidebarView)

            // Tab container item (larger)
            val tabItemView = layoutInflater.inflate(R.layout.item_action_detail, llTabActionsContainer, false)
            tabItemView.findViewById<TextView>(R.id.tv_task_name).text = "${action.id}. ${action.task}"
            tabItemView.findViewById<TextView>(R.id.tv_assignee).text = "Người phụ trách: ${action.assignee}"
            tabItemView.findViewById<TextView>(R.id.tv_deadline).text = "Hạn chót: ${action.deadline}"
            tabItemView.findViewById<TextView>(R.id.tv_summarize_task).visibility = View.GONE
            llTabActionsContainer?.addView(tabItemView)
        }
    }

    private fun toggleTts() {
        if (summaryText.isBlank()) {
            Toast.makeText(this, "Không có nội dung tóm tắt để đọc", Toast.LENGTH_SHORT).show()
            return
        }

        if (isTtsPlaying) {
            ttsManager?.stop()
            btnReadSummary?.text = "🔊 Đọc tóm tắt"
            isTtsPlaying = false
        } else {
            ttsManager?.speak(summaryText, meetingLang)
            btnReadSummary?.text = "⏹ Dừng đọc"
            isTtsPlaying = true
        }
    }

    private fun showExportDialog() {
        val formats = arrayOf("Báo cáo đầy đủ (PDF)", "Văn bản tóm tắt & Transcript (TXT)")
        AlertDialog.Builder(this)
            .setTitle("Chọn định dạng xuất báo cáo")
            .setItems(formats) { _, which ->
                val format = if (which == 0) "PDF" else "TXT"
                exportReport(format)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun exportReport(format: String) {
        val meeting = currentMeeting
        if (meeting == null) {
            Toast.makeText(this, "Chưa tải xong dữ liệu cuộc họp", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                ReportExporter.exportReport(this@MeetingResultActivity, meeting, format)
                Toast.makeText(this@MeetingResultActivity, "Đã xuất báo cáo $format thành công vào thư mục Download", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MeetingResultActivity, "Lỗi xuất báo cáo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager?.shutdown()
    }
}
