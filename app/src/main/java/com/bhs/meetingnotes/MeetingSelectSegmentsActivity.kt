package com.bhs.meetingnotes

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bhs.meetingnotes.adapter.SelectSegmentAdapter
import com.bhs.meetingnotes.db.MeetingDatabase
import com.bhs.meetingnotes.db.MeetingEntity
import com.bhs.meetingnotes.model.SegmentItem
import com.bhs.meetingnotes.util.ProcessingEstimator
import com.mediatek.neuropilot.jnidemo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Màn hình Chọn đoạn ghi âm để xử lý AI (Màn hình 4 theo BA v2).
 * Cho phép user tick/untick từng segment, nghe preview, tính toán thời gian chạy ước tính.
 */
class MeetingSelectSegmentsActivity : AppCompatActivity() {

    private lateinit var db: MeetingDatabase
    private lateinit var adapter: SelectSegmentAdapter

    private var meetingId: Long = -1L
    private var currentMeeting: MeetingEntity? = null
    private val segmentsList = mutableListOf<SegmentItem>()

    // Audio Preview
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingId: Long? = null

    // Views
    private var tvHeaderSubtitle: TextView? = null
    private var tvAllSegmentsCount: TextView? = null
    private var btnToggleSelectAll: TextView? = null
    private var btnStartAiPipeline: TextView? = null

    // Stats
    private var tvSelectedCountStat: TextView? = null
    private var tvSelectedDurationStat: TextView? = null
    private var tvSelectedSizeStat: TextView? = null
    private var tvSkippedStat: TextView? = null

    // Estimates
    private var tvEstStep1: TextView? = null
    private var tvEstStep2: TextView? = null
    private var tvEstStep34: TextView? = null
    private var tvEstTotal: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_select_segments)

        meetingId = intent.getLongExtra("MEETING_ID", -1L)
        db = MeetingDatabase.getInstance(this)

        initViews()
        loadData()
    }

    private fun initViews() {
        tvHeaderSubtitle = findViewById(R.id.tv_header_subtitle)
        tvAllSegmentsCount = findViewById(R.id.tv_all_segments_count)
        btnToggleSelectAll = findViewById(R.id.btn_toggle_select_all)
        btnStartAiPipeline = findViewById(R.id.btn_start_ai_pipeline)

        tvSelectedCountStat = findViewById(R.id.tv_selected_count_stat)
        tvSelectedDurationStat = findViewById(R.id.tv_selected_duration_stat)
        tvSelectedSizeStat = findViewById(R.id.tv_selected_size_stat)
        tvSkippedStat = findViewById(R.id.tv_skipped_stat)

        tvEstStep1 = findViewById(R.id.tv_est_step1)
        tvEstStep2 = findViewById(R.id.tv_est_step2)
        tvEstStep34 = findViewById(R.id.tv_est_step3_4)
        tvEstTotal = findViewById(R.id.tv_est_total)

        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val rvSegments = findViewById<RecyclerView>(R.id.rv_select_segments)
        rvSegments.layoutManager = LinearLayoutManager(this)
        adapter = SelectSegmentAdapter(
            items = segmentsList,
            onSelectionChanged = { item, isSelected ->
                item.isSelected = isSelected
                if (!isSelected && item.note.isBlank()) {
                    item.note = "Giải lao - Bỏ chọn"
                }
                updateStatisticsAndEstimates()
            },
            onPlayPreview = { item ->
                toggleAudioPreview(item)
            }
        )
        rvSegments.adapter = adapter

        btnToggleSelectAll?.setOnClickListener {
            toggleSelectAll()
        }

        btnStartAiPipeline?.setOnClickListener {
            startAiProcessing()
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                currentMeeting = db.meetingDao().getMeetingById(meetingId)
                val entities = db.segmentDao().getSegmentsForMeeting(meetingId)
                val bookmarks = db.bookmarkDao().getBookmarksForMeeting(meetingId)
                val bookmarkCountMap = bookmarks.groupingBy { it.segmentIndex }.eachCount()

                segmentsList.clear()
                segmentsList.addAll(entities.map { entity ->
                    val count = bookmarkCountMap[entity.segmentIndex] ?: 0
                    SegmentItem.fromEntity(entity).copy(bookmarkCount = count)
                })
            }

            val meeting = currentMeeting
            val langDisplay = if (meeting?.language == "en") "English" else "Tiếng Việt"
            tvHeaderSubtitle?.text = "${meeting?.title ?: "Cuộc họp"} • ${segmentsList.size} đoạn • $langDisplay"
            tvAllSegmentsCount?.text = "(${segmentsList.size})"

            adapter.updateList(segmentsList)
            updateStatisticsAndEstimates()
        }
    }

    private fun toggleSelectAll() {
        val anyUnselected = segmentsList.any { !it.isSelected }
        val newSelectionState = anyUnselected // Nếu có đoạn chưa chọn -> Chọn tất cả; ngược lại Bỏ chọn hết

        for (item in segmentsList) {
            item.isSelected = newSelectionState
            if (!newSelectionState && item.note.isBlank()) {
                item.note = "Giải lao - Bỏ chọn"
            }
        }
        adapter.notifyDataSetChanged()
        updateStatisticsAndEstimates()
    }

    private fun updateStatisticsAndEstimates() {
        val totalCount = segmentsList.size
        val selectedItems = segmentsList.filter { it.isSelected }
        val skippedItems = segmentsList.filter { !it.isSelected }

        val selectedCount = selectedItems.size
        var selectedDurationMs = 0L
        var selectedSizeBytes = 0L
        for (item in selectedItems) {
            selectedDurationMs += item.durationMs
            selectedSizeBytes += item.fileSizeBytes
        }

        var skippedDurationMs = 0L
        for (item in skippedItems) {
            skippedDurationMs += item.durationMs
        }

        // 1. Panel Stats
        tvSelectedCountStat?.text = "$selectedCount / $totalCount đoạn"
        tvSelectedDurationStat?.text = formatDuration(selectedDurationMs)
        val selectedMb = selectedSizeBytes.toDouble() / (1024.0 * 1024.0)
        tvSelectedSizeStat?.text = String.format("%.1f MB", selectedMb)

        if (skippedItems.isNotEmpty()) {
            tvSkippedStat?.text = "${skippedItems.size} đoạn (${formatDuration(skippedDurationMs)})"
            tvSkippedStat?.visibility = View.VISIBLE
        } else {
            tvSkippedStat?.text = "0 đoạn"
        }

        // 2. AI Processing Estimates via ProcessingEstimator (BA v2 standard)
        val estimate = ProcessingEstimator.estimate(selectedDurationMs)
        val asrModelName = currentMeeting?.asrModel ?: "PhoWhisper"

        tvEstStep1?.text = "~${estimate.asrMin} phút"
        tvEstStep2?.text = "~${estimate.fixMin} phút"
        tvEstStep34?.text = "~${estimate.summaryMin} phút"
        tvEstTotal?.text = "~${estimate.totalMin} phút"

        // 3. Header Action Button
        btnStartAiPipeline?.text = "Xử lý AI ($selectedCount đoạn)"
        btnStartAiPipeline?.isEnabled = selectedCount > 0
        btnStartAiPipeline?.alpha = if (selectedCount > 0) 1.0f else 0.5f

        // 4. Toggle button text
        val allSelected = selectedCount == totalCount
        btnToggleSelectAll?.text = if (allSelected) "Bỏ chọn tất cả" else "Chọn tất cả"
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%dh %02d phút", hours, minutes)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun toggleAudioPreview(item: SegmentItem) {
        if (currentlyPlayingId == item.id) {
            stopAudioPreview()
            return
        }

        stopAudioPreview()

        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "Không tìm thấy file ${item.fileName}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(item.filePath)
                prepare()
                setOnCompletionListener {
                    stopAudioPreview()
                }
                start()
            }
            currentlyPlayingId = item.id
            adapter.setPlayingSegment(item.id)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể phát âm thanh: ${e.message}", Toast.LENGTH_SHORT).show()
            stopAudioPreview()
        }
    }

    private fun stopAudioPreview() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        currentlyPlayingId = null
        adapter.setPlayingSegment(null)
    }

    private fun startAiProcessing() {
        stopAudioPreview()

        val selectedCount = segmentsList.count { it.isSelected }
        if (selectedCount == 0) {
            Toast.makeText(this, "Vui lòng chọn ít nhất 1 đoạn để xử lý AI", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Đồng bộ trạng thái isSelected vào Room Database
                for (item in segmentsList) {
                    db.segmentDao().updateSegmentSelection(item.id, item.isSelected)
                    if (item.note.isNotBlank()) {
                        db.segmentDao().updateSegmentNote(item.id, item.note)
                    }
                }

                // Cập nhật trạng thái Meeting
                val meeting = currentMeeting
                if (meeting != null) {
                    val selectedDuration = segmentsList.filter { it.isSelected }.sumOf { it.durationMs }
                    db.meetingDao().updateMeeting(
                        meeting.copy(
                            durationMs = selectedDuration,
                            status = "PROCESSING"
                        )
                    )
                }
            }

            // Mở màn hình Tiến trình Xử lý AI (Screen 5)
            val intent = Intent(this@MeetingSelectSegmentsActivity, MeetingProcessingActivity::class.java)
            intent.putExtra("MEETING_ID", meetingId)
            startActivity(intent)
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        stopAudioPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioPreview()
    }
}
