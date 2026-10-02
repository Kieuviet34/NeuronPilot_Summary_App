package com.bhs.meetingnotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bhs.meetingnotes.adapter.RecordSegmentAdapter
import com.bhs.meetingnotes.audio.I2SAudioRecorder
import com.bhs.meetingnotes.db.MeetingDatabase
import com.bhs.meetingnotes.db.MeetingEntity
import com.bhs.meetingnotes.db.SegmentEntity
import com.bhs.meetingnotes.model.AppSettings
import com.bhs.meetingnotes.model.SegmentItem
import com.bhs.meetingnotes.util.FormatUtils
import com.bhs.meetingnotes.util.WaveformView
import com.mediatek.neuropilot.jnidemo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Màn hình Ghi âm cuộc họp đa phân đoạn (Multi-Segment) theo chuẩn BA v2.
 * Hỗ trợ chia đoạn, pause/resume độc lập từng đoạn, lưu file WAV theo chuẩn:
 * {Tên_cuộc_họp}_{số_thứ_tự:02d}.wav
 */
class MeetingRecordActivity : AppCompatActivity() {

    private lateinit var audioRecorder: I2SAudioRecorder
    private lateinit var appSettings: AppSettings
    private lateinit var db: MeetingDatabase
    private lateinit var segmentAdapter: RecordSegmentAdapter

    // Views
    private var tvMeetingTitle: TextView? = null
    private var tvHeaderSegmentBadge: TextView? = null
    private var tvCurrentSegmentName: TextView? = null
    private var tvTimer: TextView? = null
    private var tvRecordStatus: TextView? = null
    private var vRecordingIndicator: View? = null
    private var vWaveform: WaveformView? = null
    private var ivPauseIcon: ImageView? = null
    private var tvSegmentsCount: TextView? = null
    private var tvTotalDuration: TextView? = null
    private var tvTotalSize: TextView? = null
    private var tvModelAsrInfo: TextView? = null

    // State
    private var meetingId: Long = 0L
    private var meetingBaseTitle: String = "Hop_BSP"
    private var currentSegmentIndex = 1
    private var isRecordingSegment = false
    private var isPausedSegment = false
    private val segmentsList = mutableListOf<SegmentItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_record)

        appSettings = AppSettings.getInstance(this)
        db = MeetingDatabase.getInstance(this)
        audioRecorder = I2SAudioRecorder(this)

        initMeetingData()
        initViews()
        checkPermissionsAndStart()
    }

    private fun initMeetingData() {
        // Tên cuộc họp từ Intent hoặc mặc định theo ngày
        val intentTitle = intent.getStringExtra("MEETING_TITLE")
        meetingBaseTitle = if (!intentTitle.isNullOrBlank()) {
            intentTitle.trim().replace("\\s+".toRegex(), "_")
        } else {
            "Hop_BSP"
        }
    }

    private fun initViews() {
        tvMeetingTitle = findViewById(R.id.tv_header_meeting_title)
        tvHeaderSegmentBadge = findViewById(R.id.tv_header_segment_badge)
        tvCurrentSegmentName = findViewById(R.id.tv_current_segment_name)
        tvTimer = findViewById(R.id.tv_timer)
        tvRecordStatus = findViewById(R.id.tv_record_status)
        vRecordingIndicator = findViewById(R.id.v_recording_indicator)
        vWaveform = findViewById(R.id.v_waveform)
        ivPauseIcon = findViewById(R.id.iv_pause_icon)
        tvSegmentsCount = findViewById(R.id.tv_segments_count)
        tvTotalDuration = findViewById(R.id.tv_total_duration)
        tvTotalSize = findViewById(R.id.tv_total_size)
        tvModelAsrInfo = findViewById(R.id.tv_model_asr_info)

        tvMeetingTitle?.text = meetingBaseTitle.replace("_", " ")
        tvModelAsrInfo?.text = appSettings.asrModel

        val rvSegments = findViewById<RecyclerView>(R.id.rv_segments)
        rvSegments.layoutManager = LinearLayoutManager(this)
        segmentAdapter = RecordSegmentAdapter(segmentsList)
        rvSegments.adapter = segmentAdapter

        // Nút Back
        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Nút Hoàn tất ở Top Bar
        findViewById<View>(R.id.btn_finish_top)?.setOnClickListener {
            completeRecordingAndProceed()
        }

        // 4 Nút điều khiển
        // 1. Tạm dừng / Tiếp tục
        findViewById<View>(R.id.btn_pause)?.setOnClickListener {
            togglePauseResume()
        }

        // 2. Dừng đoạn
        findViewById<View>(R.id.btn_stop_segment)?.setOnClickListener {
            stopCurrentSegment()
        }

        // 3. Đoạn mới
        findViewById<View>(R.id.btn_new_segment)?.setOnClickListener {
            startNextSegment()
        }

        // 4. Đánh dấu
        findViewById<View>(R.id.btn_bookmark)?.setOnClickListener {
            Toast.makeText(this, "Đã đánh dấu thời điểm ${tvTimer?.text} tại Đoạn $currentSegmentIndex", Toast.LENGTH_SHORT).show()
        }

        // Nút hoàn tất lớn góc dưới phải
        findViewById<View>(R.id.btn_complete_and_select)?.setOnClickListener {
            completeRecordingAndProceed()
        }
    }

    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        } else {
            createMeetingAndStartFirstSegment()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            createMeetingAndStartFirstSegment()
        } else {
            Toast.makeText(this, "Cần cấp quyền ghi âm để sử dụng chức năng này", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun createMeetingAndStartFirstSegment() {
        lifecycleScope.launch {
            meetingId = withContext(Dispatchers.IO) {
                val newMeeting = MeetingEntity(
                    title = meetingBaseTitle.replace("_", " "),
                    language = appSettings.language,
                    asrModel = appSettings.asrModel,
                    llmModel = "Qwen2.5 3B (Q4)",
                    status = "RECORDING"
                )
                db.meetingDao().insertMeeting(newMeeting)
            }

            setupRecorderListeners()
            startSegment(currentSegmentIndex)
        }
    }

    private fun setupRecorderListeners() {
        audioRecorder.onTickListener = { durationMs: Long, fileSizeBytes: Long ->
            tvTimer?.text = FormatUtils.formatTimer(durationMs)
            // Update active segment size & duration in memory
            val activeItem = segmentsList.find { it.segmentIndex == currentSegmentIndex && it.status == "RECORDING" }
            if (activeItem != null) {
                val updatedItem = activeItem.copy(
                    durationMs = durationMs,
                    fileSizeBytes = fileSizeBytes,
                    pauseCount = audioRecorder.pauseCount
                )
                val index = segmentsList.indexOf(activeItem)
                if (index != -1) {
                    segmentsList[index] = updatedItem
                    segmentAdapter.updateList(segmentsList.toList())
                }
            }
            updateSummaryStats()
        }

        audioRecorder.onAudioLevelListener = { _: Double, normalizedLevel: Int ->
            vWaveform?.updateAudioLevel(normalizedLevel)
        }
    }

    private fun startSegment(index: Int) {
        val segmentFileName = "${meetingBaseTitle}_${String.format("%02d", index)}.wav"
        val segmentNameDisplay = "${meetingBaseTitle}_${String.format("%02d", index)}"

        tvCurrentSegmentName?.text = segmentNameDisplay
        tvHeaderSegmentBadge?.text = "Đoạn $index/$index"
        tvRecordStatus?.text = "Đang ghi âm..."
        vRecordingIndicator?.setBackgroundResource(R.drawable.bg_circle_red)
        ivPauseIcon?.setImageResource(android.R.drawable.ic_media_pause)

        val filePath = audioRecorder.startRecording(segmentFileName)
        isRecordingSegment = true
        isPausedSegment = false

        val newSegmentItem = SegmentItem(
            meetingId = meetingId,
            segmentIndex = index,
            fileName = segmentFileName,
            filePath = filePath,
            status = "RECORDING",
            isSelected = true
        )
        segmentsList.add(newSegmentItem)
        segmentAdapter.updateList(segmentsList.toList())
        tvSegmentsCount?.text = "(${segmentsList.size})"

        updateSummaryStats()
    }

    private fun togglePauseResume() {
        if (!isRecordingSegment) {
            // Đang không ghi đoạn nào -> tạo đoạn mới
            startNextSegment()
            return
        }

        if (isPausedSegment) {
            audioRecorder.resumeRecording()
            tvRecordStatus?.text = "Đang ghi âm..."
            vRecordingIndicator?.setBackgroundResource(R.drawable.bg_circle_red)
            ivPauseIcon?.setImageResource(android.R.drawable.ic_media_pause)
            isPausedSegment = false
        } else {
            audioRecorder.pauseRecording()
            tvRecordStatus?.text = "Tạm dừng"
            vRecordingIndicator?.setBackgroundResource(R.drawable.bg_circle_gray)
            ivPauseIcon?.setImageResource(android.R.drawable.ic_media_play)
            isPausedSegment = true
        }
    }

    private fun stopCurrentSegment() {
        if (!isRecordingSegment) return

        val finalPath = audioRecorder.stopRecording()
        val durationMs = audioRecorder.durationMs
        val fileSizeBytes = audioRecorder.currentFileSize
        val pauseCount = audioRecorder.pauseCount

        isRecordingSegment = false
        isPausedSegment = false

        tvRecordStatus?.text = "Đã lưu đoạn"
        vRecordingIndicator?.setBackgroundResource(R.drawable.bg_circle_gray)
        ivPauseIcon?.setImageResource(android.R.drawable.ic_media_play)

        // Cập nhật segment trong danh sách & lưu vào Room DB
        val activeIndex = segmentsList.indexOfFirst { it.segmentIndex == currentSegmentIndex }
        if (activeIndex != -1) {
            val savedItem = segmentsList[activeIndex].copy(
                filePath = finalPath,
                durationMs = durationMs,
                fileSizeBytes = fileSizeBytes,
                pauseCount = pauseCount,
                status = "SAVED"
            )
            segmentsList[activeIndex] = savedItem
            segmentAdapter.updateList(segmentsList.toList())

            lifecycleScope.launch(Dispatchers.IO) {
                db.segmentDao().insertSegment(savedItem.toEntity())
            }
        }

        updateSummaryStats()
        Toast.makeText(this, "Đã lưu ${segmentsList.getOrNull(activeIndex)?.fileName}", Toast.LENGTH_SHORT).show()
    }

    private fun startNextSegment() {
        // Nếu đoạn hiện tại đang ghi dở -> lưu trước
        if (isRecordingSegment) {
            stopCurrentSegment()
        }

        currentSegmentIndex++
        startSegment(currentSegmentIndex)
    }

    private fun updateSummaryStats() {
        var totalMs = 0L
        var totalBytes = 0L
        for (item in segmentsList) {
            totalMs += item.durationMs
            totalBytes += item.fileSizeBytes
        }

        val totalSeconds = totalMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val durationStr = if (hours > 0) {
            String.format("%dh %02d phút", hours, minutes)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }

        tvTotalDuration?.text = durationStr
        val totalMb = totalBytes.toDouble() / (1024.0 * 1024.0)
        tvTotalSize?.text = String.format("%.1f MB", totalMb)
        tvHeaderSegmentBadge?.text = "Đoạn $currentSegmentIndex/${segmentsList.size}"
    }

    private fun completeRecordingAndProceed() {
        // Nếu đoạn cuối đang ghi -> lưu trước khi kết thúc
        if (isRecordingSegment) {
            stopCurrentSegment()
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Cập nhật tổng thời lượng cho meeting
                var totalDuration = 0L
                for (item in segmentsList) {
                    totalDuration += item.durationMs
                }
                val currentMeeting = db.meetingDao().getMeetingById(meetingId)
                if (currentMeeting != null) {
                    db.meetingDao().updateMeeting(
                        currentMeeting.copy(
                            durationMs = totalDuration,
                            status = "RECORDED"
                        )
                    )
                }
            }

            val intent = Intent(this@MeetingRecordActivity, MeetingSelectSegmentsActivity::class.java)
            intent.putExtra("MEETING_ID", meetingId)
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecordingSegment) {
            audioRecorder.stopRecording()
        }
    }
}