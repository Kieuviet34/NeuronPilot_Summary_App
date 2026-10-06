package com.bhs.meetingnotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bhs.meetingnotes.adapter.RecordSegmentAdapter
import com.bhs.meetingnotes.audio.AudioRecordingService
import com.bhs.meetingnotes.audio.I2SAudioRecorder
import com.bhs.meetingnotes.db.BookmarkEntity
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
import java.io.File

/**
 * Màn hình Ghi âm cuộc họp đa phân đoạn (Multi-Segment) theo chuẩn BA v2.
 * Hỗ trợ chia đoạn, pause/resume độc lập từng đoạn, nghe lại từng đoạn đã ghi,
 * cảnh báo thoát tránh mất dữ liệu, và điều hướng trạng thái chuẩn xác.
 */
class MeetingRecordActivity : AppCompatActivity() {

    private lateinit var audioRecorder: I2SAudioRecorder
    private lateinit var appSettings: AppSettings
    private lateinit var db: MeetingDatabase
    private lateinit var segmentAdapter: RecordSegmentAdapter
    private var segmentPlayer: MediaPlayer? = null
    private var playingSegmentIndex: Int? = null

    // Views
    private var tvMeetingTitle: TextView? = null
    private var tvHeaderSegmentBadge: TextView? = null
    private var tvCurrentSegmentName: TextView? = null
    private var tvTimer: TextView? = null
    private var tvRecordStatus: TextView? = null
    private var vRecordingIndicator: View? = null
    private var vWaveform: WaveformView? = null

    // 5 Control Button views
    private var llPauseCircle: LinearLayout? = null
    private var ivPauseIcon: ImageView? = null
    private var tvBtnPause: TextView? = null

    private var llStopCircle: LinearLayout? = null
    private var ivStopIcon: ImageView? = null
    private var tvBtnStop: TextView? = null

    private var llNewCircle: LinearLayout? = null
    private var ivNewIcon: ImageView? = null
    private var tvBtnNew: TextView? = null

    private var llStopMeetingCircle: LinearLayout? = null
    private var ivStopMeetingIcon: ImageView? = null
    private var tvBtnStopMeeting: TextView? = null

    private var llBookmarkCircle: LinearLayout? = null
    private var ivBookmarkIcon: ImageView? = null
    private var tvBtnBookmark: TextView? = null

    // Stats
    private var tvSegmentsCount: TextView? = null
    private var tvTotalDuration: TextView? = null
    private var tvTotalSize: TextView? = null
    private var tvModelAsrInfo: TextView? = null
    private var tvHeaderLangBadge: TextView? = null

    // State
    private var meetingId: Long = 0L
    private var meetingBaseTitle: String = "Hop_BSP"
    private var meetingLanguage: String = "vi"
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
        setupBackHandler()
        checkPermissionsAndStart()
    }

    private fun initMeetingData() {
        val intentTitle = intent.getStringExtra("MEETING_TITLE")
        meetingBaseTitle = if (!intentTitle.isNullOrBlank()) {
            intentTitle.trim()
                .replace("[\\\\/:*?\"<>|]".toRegex(), "-")
                .replace("\\s+".toRegex(), "_")
        } else {
            "Hop_BSP"
        }
        val intentLang = intent.getStringExtra("MEETING_LANGUAGE")
        if (!intentLang.isNullOrBlank()) {
            meetingLanguage = intentLang
        }
    }

    private fun initViews() {
        tvMeetingTitle = findViewById(R.id.tv_header_meeting_title)
        tvHeaderSegmentBadge = findViewById(R.id.tv_header_segment_badge)
        tvHeaderLangBadge = findViewById(R.id.tv_header_lang_badge)
        tvCurrentSegmentName = findViewById(R.id.tv_current_segment_name)
        tvTimer = findViewById(R.id.tv_timer)
        tvRecordStatus = findViewById(R.id.tv_record_status)
        vRecordingIndicator = findViewById(R.id.v_recording_indicator)
        vWaveform = findViewById(R.id.v_waveform)

        llPauseCircle = findViewById(R.id.ll_pause_circle)
        ivPauseIcon = findViewById(R.id.iv_pause_icon)
        tvBtnPause = findViewById(R.id.tv_btn_pause)

        llStopCircle = findViewById(R.id.ll_stop_circle)
        ivStopIcon = findViewById(R.id.iv_stop_icon)
        tvBtnStop = findViewById(R.id.tv_btn_stop)

        llNewCircle = findViewById(R.id.ll_new_circle)
        ivNewIcon = findViewById(R.id.iv_new_icon)
        tvBtnNew = findViewById(R.id.tv_btn_new)

        llStopMeetingCircle = findViewById(R.id.ll_stop_meeting_circle)
        ivStopMeetingIcon = findViewById(R.id.iv_stop_meeting_icon)
        tvBtnStopMeeting = findViewById(R.id.tv_btn_stop_meeting)

        llBookmarkCircle = findViewById(R.id.ll_bookmark_circle)
        ivBookmarkIcon = findViewById(R.id.iv_bookmark_icon)
        tvBtnBookmark = findViewById(R.id.tv_btn_bookmark)

        tvSegmentsCount = findViewById(R.id.tv_segments_count)
        tvTotalDuration = findViewById(R.id.tv_total_duration)
        tvTotalSize = findViewById(R.id.tv_total_size)
        tvModelAsrInfo = findViewById(R.id.tv_model_asr_info)

        tvMeetingTitle?.text = meetingBaseTitle.replace("_", " ")
        if (meetingLanguage == "en") {
            tvHeaderLangBadge?.text = "Tiếng Anh"
            tvHeaderLangBadge?.setBackgroundResource(R.drawable.bg_badge_en)
            tvHeaderLangBadge?.setTextColor(ContextCompat.getColor(this, R.color.warn_orange))
            tvModelAsrInfo?.text = "Whisper Base (EN)"
        } else {
            tvHeaderLangBadge?.text = "Tiếng Việt"
            tvHeaderLangBadge?.setBackgroundResource(R.drawable.bg_badge_vi)
            tvHeaderLangBadge?.setTextColor(ContextCompat.getColor(this, R.color.primary))
            tvModelAsrInfo?.text = appSettings.asrModel
        }

        val rvSegments = findViewById<RecyclerView>(R.id.rv_segments)
        rvSegments.layoutManager = LinearLayoutManager(this)
        segmentAdapter = RecordSegmentAdapter(segmentsList) { item ->
            playOrStopSegmentAudio(item)
        }
        rvSegments.adapter = segmentAdapter

        // Nút Back trên Top Bar
        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            handleUserExitRequest()
        }

        // Nút Hoàn tất ở Top Bar (Dừng hẳn cuộc họp)
        findViewById<View>(R.id.btn_finish_top)?.setOnClickListener {
            showStopMeetingConfirmationDialog()
        }

        // Nút 1: Tạm dừng / Tiếp tục / Bắt đầu ghi
        findViewById<View>(R.id.btn_pause)?.setOnClickListener {
            togglePauseResume()
        }

        // Nút 2: Dừng đoạn (Lưu đoạn hiện tại, chuyển sang trạng thái chờ)
        findViewById<View>(R.id.btn_stop_segment)?.setOnClickListener {
            if (isRecordingSegment) {
                stopCurrentSegment()
            } else {
                Toast.makeText(this, "Đoạn hiện tại đã lưu. Nhấn 'Bắt đầu ghi' hoặc 'Đoạn mới' để ghi tiếp", Toast.LENGTH_SHORT).show()
            }
        }

        // Nút 3: Thêm đoạn mới (Chốt đoạn cũ và ghi tiếp đoạn mới ngay)
        findViewById<View>(R.id.btn_new_segment)?.setOnClickListener {
            startNextSegment()
        }

        // Nút 4: Dừng hẳn cuộc họp (Hiện thông báo xác nhận an toàn)
        findViewById<View>(R.id.btn_stop_meeting)?.setOnClickListener {
            showStopMeetingConfirmationDialog()
        }

        // Nút 5: Đánh dấu Bookmark (Hỗ trợ cả khi Đang ghi và Tạm dừng phân đoạn)
        findViewById<View>(R.id.btn_bookmark)?.setOnClickListener {
            if (!isRecordingSegment) {
                Toast.makeText(this, getString(R.string.rec_bookmark_unavailable), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val durationMs = audioRecorder.durationMs
            val formattedTime = FormatUtils.formatTimer(durationMs)
            
            // Cập nhật ngay bookmark count trên giao diện danh sách phân đoạn
            val activeIndex = segmentsList.indexOfFirst { it.segmentIndex == currentSegmentIndex }
            var currentBookmarks = 1
            if (activeIndex != -1) {
                val currentItem = segmentsList[activeIndex]
                currentBookmarks = currentItem.bookmarkCount + 1
                segmentsList[activeIndex] = currentItem.copy(bookmarkCount = currentBookmarks)
                segmentAdapter.updateList(segmentsList.toList())
            }

            // Hiệu ứng nhấp nháy xác nhận trên nút Bookmark
            llBookmarkCircle?.animate()?.scaleX(1.25f)?.scaleY(1.25f)?.setDuration(130)?.withEndAction {
                llBookmarkCircle?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(130)?.start()
            }?.start()

            lifecycleScope.launch(Dispatchers.IO) {
                val bookmark = BookmarkEntity(
                    meetingId = meetingId,
                    segmentIndex = currentSegmentIndex,
                    timestampMs = durationMs,
                    formattedTime = formattedTime,
                    note = "Đánh dấu mốc $currentBookmarks tại $formattedTime"
                )
                db.bookmarkDao().insertBookmark(bookmark)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MeetingRecordActivity,
                        getString(R.string.rec_bookmark_success, currentBookmarks, formattedTime, currentSegmentIndex),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Nút hoàn tất lớn góc dưới phải (Dừng hẳn cuộc họp & Xử lý AI)
        findViewById<View>(R.id.btn_complete_and_select)?.setOnClickListener {
            showStopMeetingConfirmationDialog()
        }
    }

    /**
     * Hộp thoại xác nhận khi người dùng nhấn nút Dừng hẳn cuộc họp:
     * Đảm bảo thông báo rõ ràng "Có chắc chắn muốn dừng cuộc họp không?" trước khi hoàn tất.
     */
    private fun showStopMeetingConfirmationDialog() {
        val totalSegs = segmentsList.size + if (isRecordingSegment) 1 else 0
        AlertDialog.Builder(this)
            .setTitle("Dừng hẳn cuộc họp?")
            .setMessage("Bạn có chắc chắn muốn dừng hẳn cuộc họp này không?\n\nToàn bộ dữ liệu ghi âm ($totalSegs đoạn) sẽ được lưu lại an toàn và chuyển sang bước chọn đoạn để phân tích AI.")
            .setPositiveButton("Xác nhận dừng & Xử lý AI") { _, _ ->
                completeRecordingAndProceed()
            }
            .setNegativeButton("Hủy (Tiếp tục ghi)", null)
            .show()
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleUserExitRequest()
            }
        })
    }

    /**
     * Hộp thoại cảnh báo thoát an toàn:
     * Tránh vô tình bấm nhầm phím Back làm gián đoạn cuộc họp hoặc mất dữ liệu.
     */
    private fun handleUserExitRequest() {
        if (isRecordingSegment || isPausedSegment) {
            AlertDialog.Builder(this)
                .setTitle("Đang trong phiên ghi âm")
                .setMessage("Cuộc họp đang ghi âm. Bạn muốn tạm dừng ghi âm và quay lại danh sách cuộc họp không?\n\n(Đoạn âm thanh hiện tại sẽ được lưu an toàn vào máy).")
                .setPositiveButton("Tạm dừng & Quay lại") { _, _ ->
                    stopCurrentSegment()
                    AudioRecordingService.stopService(this)
                    finish()
                }
                .setNeutralButton("Dừng hẳn & Xử lý AI") { _, _ ->
                    completeRecordingAndProceed()
                }
                .setNegativeButton("Tiếp tục ghi", null)
                .show()
        } else if (segmentsList.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Rời khỏi phiên ghi âm")
                .setMessage("Cuộc họp có ${segmentsList.size} đoạn đã lưu. Bạn muốn hoàn tất để xử lý AI hay quay lại danh sách?")
                .setPositiveButton("Hoàn tất & Xử lý AI") { _, _ ->
                    completeRecordingAndProceed()
                }
                .setNegativeButton("Quay lại danh sách") { _, _ ->
                    finish()
                }
                .setNeutralButton("Ở lại", null)
                .show()
        } else {
            finish()
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
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
                val asr = if (meetingLanguage == "en") "Whisper Base (EN)" else appSettings.asrModel
                val newMeeting = MeetingEntity(
                    title = meetingBaseTitle.replace("_", " "),
                    language = meetingLanguage,
                    asrModel = asr,
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
        stopSegmentAudio()

        val segmentFileName = "${meetingBaseTitle}_${String.format("%02d", index)}.wav"
        val segmentNameDisplay = "${meetingBaseTitle}_${String.format("%02d", index)}"

        tvCurrentSegmentName?.text = segmentNameDisplay
        tvHeaderSegmentBadge?.text = "Đoạn $index/$index"

        val filePath = audioRecorder.startRecording(segmentFileName)
        AudioRecordingService.startService(this, meetingBaseTitle)
        isRecordingSegment = true
        isPausedSegment = false

        updateControlButtonsState()

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
            // Khi đang ở trạng thái dừng đoạn -> Nút 1 có tác dụng "Bắt đầu ghi đoạn mới"
            startNextSegment()
            return
        }

        if (isPausedSegment) {
            audioRecorder.resumeRecording()
            AudioRecordingService.updateState(this, false)
            isPausedSegment = false
            updateControlButtonsState()
        } else {
            audioRecorder.pauseRecording()
            AudioRecordingService.updateState(this, true)
            isPausedSegment = true
            updateControlButtonsState()
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

        updateControlButtonsState()

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
        Toast.makeText(this, "Đã lưu đoạn $currentSegmentIndex vào máy", Toast.LENGTH_SHORT).show()
    }

    private fun startNextSegment() {
        if (isRecordingSegment) {
            stopCurrentSegment()
        }

        currentSegmentIndex++
        startSegment(currentSegmentIndex)
    }

    /**
     * Cập nhật trạng thái hiển thị của 5 nút điều khiển theo logic nghiệp vụ rõ ràng:
     * - Khi đang ghi: Nút 1 là Tạm dừng, Nút 2 là Dừng đoạn (Amber), Nút 3 là Đoạn mới (+), Nút 4 là Dừng hẳn (Đỏ), Nút 5 là Đánh dấu
     * - Khi tạm dừng: Nút 1 là Tiếp tục, Nút 2 là Dừng đoạn
     * - Khi dừng đoạn: Nút 1 biến thành "Bắt đầu ghi" (Xanh lá), Nút 2 hiển thị "Đã lưu" (disabled), Nút 4 vẫn cho phép Dừng hẳn cuộc họp
     */
    private fun updateControlButtonsState() {
        when {
            isRecordingSegment && !isPausedSegment -> {
                llPauseCircle?.setBackgroundResource(R.drawable.bg_circle_gray)
                ivPauseIcon?.setImageResource(R.drawable.ic_pause)
                ivPauseIcon?.setColorFilter(ContextCompat.getColor(this, R.color.text_primary))
                tvBtnPause?.text = "Tạm dừng"

                llStopCircle?.setBackgroundResource(R.drawable.bg_circle_amber)
                ivStopIcon?.setImageResource(R.drawable.ic_save_segment)
                ivStopIcon?.setColorFilter(ContextCompat.getColor(this, R.color.bg_card))
                tvBtnStop?.text = "Dừng đoạn"
                findViewById<View>(R.id.btn_stop_segment)?.isEnabled = true
                llStopCircle?.alpha = 1.0f

                llNewCircle?.setBackgroundResource(R.drawable.bg_circle_blue)
                ivNewIcon?.setImageResource(R.drawable.ic_add)
                ivNewIcon?.setColorFilter(ContextCompat.getColor(this, R.color.bg_card))
                tvBtnNew?.text = "Đoạn mới"

                llStopMeetingCircle?.setBackgroundResource(R.drawable.bg_circle_red)
                ivStopMeetingIcon?.setImageResource(R.drawable.ic_stop)
                ivStopMeetingIcon?.setColorFilter(ContextCompat.getColor(this, R.color.bg_card))
                tvBtnStopMeeting?.text = "Dừng hẳn"

                findViewById<View>(R.id.btn_bookmark)?.isEnabled = true
                llBookmarkCircle?.setBackgroundResource(R.drawable.bg_circle_bookmark)
                ivBookmarkIcon?.setColorFilter(ContextCompat.getColor(this, R.color.bg_card))
                llBookmarkCircle?.alpha = 1.0f

                tvRecordStatus?.text = "Đang ghi âm..."
                vRecordingIndicator?.setBackgroundResource(R.drawable.bg_circle_red)
                vWaveform?.setRecordingState(recording = true, paused = false)
            }
            isRecordingSegment && isPausedSegment -> {
                llPauseCircle?.setBackgroundResource(R.drawable.bg_circle_blue)
                ivPauseIcon?.setImageResource(R.drawable.ic_play)
                ivPauseIcon?.setColorFilter(ContextCompat.getColor(this, R.color.bg_card))
                tvBtnPause?.text = "Tiếp tục"

                llStopCircle?.setBackgroundResource(R.drawable.bg_circle_amber)
                ivStopIcon?.setImageResource(R.drawable.ic_save_segment)
                ivStopIcon?.setColorFilter(ContextCompat.getColor(this, R.color.bg_card))
                tvBtnStop?.text = "Dừng đoạn"
                findViewById<View>(R.id.btn_stop_segment)?.isEnabled = true
                llStopCircle?.alpha = 1.0f

                llNewCircle?.setBackgroundResource(R.drawable.bg_circle_blue)
                ivNewIcon?.setImageResource(R.drawable.ic_add)
                ivNewIcon?.setColorFilter(ContextCompat.getColor(this, R.color.bg_card))
                tvBtnNew?.text = "Đoạn mới"

                llStopMeetingCircle?.setBackgroundResource(R.drawable.bg_circle_red)
                ivStopMeetingIcon?.setImageResource(R.drawable.ic_stop)
                ivStopMeetingIcon?.setColorFilter(ContextCompat.getColor(this, R.color.bg_card))
                tvBtnStopMeeting?.text = "Dừng hẳn"

                // Vẫn cho phép đánh dấu mốc khi tạm dừng
                findViewById<View>(R.id.btn_bookmark)?.isEnabled = true
                llBookmarkCircle?.setBackgroundResource(R.drawable.bg_circle_bookmark)
                ivBookmarkIcon?.setColorFilter(ContextCompat.getColor(this, R.color.bg_card))
                llBookmarkCircle?.alpha = 0.85f

                tvRecordStatus?.text = "Tạm dừng"
                vRecordingIndicator?.setBackgroundResource(R.drawable.bg_circle_gray)
                vWaveform?.setRecordingState(recording = true, paused = true)
            }
            else -> {
                // Đã dừng đoạn (Idle - sẵn sàng bắt đầu ghi đoạn mới)
                llPauseCircle?.setBackgroundResource(R.drawable.bg_circle_green)
                ivPauseIcon?.setImageResource(R.drawable.ic_play)
                ivPauseIcon?.setColorFilter(ContextCompat.getColor(this, R.color.bg_card))
                tvBtnPause?.text = "Bắt đầu ghi"

                llStopCircle?.setBackgroundResource(R.drawable.bg_circle_gray)
                ivStopIcon?.setImageResource(R.drawable.ic_check)
                ivStopIcon?.setColorFilter(ContextCompat.getColor(this, R.color.green_text))
                tvBtnStop?.text = "Đã lưu"
                findViewById<View>(R.id.btn_stop_segment)?.isEnabled = false
                llStopCircle?.alpha = 0.5f

                llNewCircle?.setBackgroundResource(R.drawable.bg_circle_blue)
                ivNewIcon?.setImageResource(R.drawable.ic_add)
                ivNewIcon?.setColorFilter(ContextCompat.getColor(this, R.color.bg_card))
                tvBtnNew?.text = "Đoạn mới"

                llStopMeetingCircle?.setBackgroundResource(R.drawable.bg_circle_red)
                ivStopMeetingIcon?.setImageResource(R.drawable.ic_stop)
                ivStopMeetingIcon?.setColorFilter(ContextCompat.getColor(this, R.color.bg_card))
                tvBtnStopMeeting?.text = "Dừng hẳn"

                findViewById<View>(R.id.btn_bookmark)?.isEnabled = false
                llBookmarkCircle?.setBackgroundResource(R.drawable.bg_circle_gray)
                ivBookmarkIcon?.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
                llBookmarkCircle?.alpha = 0.45f

                tvRecordStatus?.text = "Đã lưu đoạn (Sẵn sàng)"
                vRecordingIndicator?.setBackgroundResource(R.drawable.bg_circle_gray)
                vWaveform?.setRecordingState(recording = false, paused = false)
            }
        }
    }

    /**
     * Nghe lại đoạn âm thanh đã lưu trực tiếp trong danh sách phân đoạn
     */
    private fun playOrStopSegmentAudio(item: SegmentItem) {
        if (playingSegmentIndex == item.segmentIndex) {
            stopSegmentAudio()
            return
        }

        stopSegmentAudio()

        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "Không tìm thấy file ${item.fileName}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            segmentPlayer = MediaPlayer().apply {
                setDataSource(item.filePath)
                prepare()
                setOnCompletionListener {
                    stopSegmentAudio()
                }
                setOnErrorListener { _, _, _ ->
                    stopSegmentAudio()
                    false
                }
                start()
            }
            playingSegmentIndex = item.segmentIndex
            segmentAdapter.setPlayingSegment(item.segmentIndex)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể phát âm thanh: ${e.message}", Toast.LENGTH_SHORT).show()
            stopSegmentAudio()
        }
    }

    private fun stopSegmentAudio() {
        segmentPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        segmentPlayer = null
        playingSegmentIndex = null
        segmentAdapter.setPlayingSegment(null)
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
        stopSegmentAudio()

        if (isRecordingSegment) {
            stopCurrentSegment()
        }

        AudioRecordingService.stopService(this)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
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
        stopSegmentAudio()
        AudioRecordingService.stopService(this)
        if (isRecordingSegment) {
            audioRecorder.stopRecording()
        }
    }
}