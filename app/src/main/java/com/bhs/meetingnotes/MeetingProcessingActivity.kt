package com.bhs.meetingnotes

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bhs.meetingnotes.ai.MeetingAiPipeline
import com.bhs.meetingnotes.db.MeetingDatabase
import com.bhs.meetingnotes.util.FormatUtils
import com.mediatek.neuropilot.jnidemo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Màn hình Tiến trình Xử lý AI (Timeline 4 bước + Dual Text Preview) theo chuẩn BA v2.
 */
class MeetingProcessingActivity : AppCompatActivity() {

    private lateinit var db: MeetingDatabase

    // Header Views
    private var tvHeaderTitle: TextView? = null
    private var tvHeaderSubtitle: TextView? = null
    private var tvTotalTimeEstimate: TextView? = null

    // Step 1 Views
    private var ivStep1Icon: ImageView? = null
    private var pbStep1: ProgressBar? = null
    private var tvStep1StatusBottom: TextView? = null
    private var lineStep12: View? = null

    // Step 2 Views
    private var ivStep2Icon: ImageView? = null
    private var pbStep2: ProgressBar? = null
    private var tvStep2StatusBottom: TextView? = null
    private var lineStep23: View? = null

    // Step 3 Views
    private var ivStep3Icon: ImageView? = null
    private var pbStep3: ProgressBar? = null
    private var tvStep3StatusBottom: TextView? = null
    private var lineStep34: View? = null

    // Step 4 Views
    private var ivStep4Icon: ImageView? = null
    private var pbStep4: ProgressBar? = null
    private var tvStep4StatusBottom: TextView? = null

    // Preview Views
    private var tvStep1Preview: TextView? = null
    private var tvStep1Badge: TextView? = null
    private var tvStep2Preview: TextView? = null
    private var tvStep2Badge: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_processing)

        db = MeetingDatabase.getInstance(this)
        initViews()

        val meetingId = intent.getLongExtra("MEETING_ID", -1L)
        if (meetingId != -1L) {
            loadMeetingInfo(meetingId)
            startPipeline(meetingId)
        } else {
            tvStep1Preview?.text = "Lỗi: Không tìm thấy ID cuộc họp"
        }
    }

    private fun initViews() {
        tvHeaderTitle = findViewById(R.id.tv_header_title)
        tvHeaderSubtitle = findViewById(R.id.tv_header_subtitle)
        tvTotalTimeEstimate = findViewById(R.id.tv_total_time_estimate)

        // Step 1
        ivStep1Icon = findViewById(R.id.iv_step1_icon)
        pbStep1 = findViewById(R.id.pb_step1)
        tvStep1StatusBottom = findViewById(R.id.tv_step1_status_bottom)
        lineStep12 = findViewById(R.id.line_step1_2)

        // Step 2
        ivStep2Icon = findViewById(R.id.iv_step2_icon)
        pbStep2 = findViewById(R.id.pb_step2)
        tvStep2StatusBottom = findViewById(R.id.tv_step2_status_bottom)
        lineStep23 = findViewById(R.id.line_step2_3)

        // Step 3
        ivStep3Icon = findViewById(R.id.iv_step3_icon)
        pbStep3 = findViewById(R.id.pb_step3)
        tvStep3StatusBottom = findViewById(R.id.tv_step3_status_bottom)
        lineStep34 = findViewById(R.id.line_step3_4)

        // Step 4
        ivStep4Icon = findViewById(R.id.iv_step4_icon)
        pbStep4 = findViewById(R.id.pb_step4)
        tvStep4StatusBottom = findViewById(R.id.tv_step4_status_bottom)

        // Previews
        tvStep1Preview = findViewById(R.id.tv_step1_preview)
        tvStep1Badge = findViewById(R.id.tv_step1_badge)
        tvStep2Preview = findViewById(R.id.tv_step2_preview)
        tvStep2Badge = findViewById(R.id.tv_step2_badge)

        tvStep1Preview?.text = ""
        tvStep2Preview?.text = ""

        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            finish()
        }
    }

    private fun loadMeetingInfo(meetingId: Long) {
        lifecycleScope.launch {
            val meeting = withContext(Dispatchers.IO) {
                db.meetingDao().getMeetingById(meetingId)
            }
            if (meeting != null) {
                val durationStr = FormatUtils.formatDuration(meeting.durationMs)
                val langStr = if (meeting.language == "en") "English" else "Tiếng Việt"
                tvHeaderSubtitle?.text = "${meeting.title} • $durationStr • $langStr"
            }
        }
    }

    private fun startPipeline(meetingId: Long) {
        val port = getSharedPreferences("meeting_notes_prefs", MODE_PRIVATE).getInt("whisper_server_port", 8080)
        val pipeline = MeetingAiPipeline(this, port)
        lifecycleScope.launch {
            val result = pipeline.runPipeline(meetingId) { step, progress, currentText ->
                lifecycleScope.launch(Dispatchers.Main) {
                    updateUiForStep(step, progress, currentText)
                }
            }

            if (result != null && !isFinishing) {
                if (result.status == "COMPLETED") {
                    val intent = Intent(this@MeetingProcessingActivity, MeetingResultActivity::class.java)
                    intent.putExtra("MEETING_ID", meetingId)
                    startActivity(intent)
                    finish()
                } else {
                    tvTotalTimeEstimate?.text = "Lỗi xử lý AI: Không thể hoàn tất pipeline."
                    tvTotalTimeEstimate?.setTextColor(ContextCompat.getColor(this@MeetingProcessingActivity, R.color.orange_text))
                }
            }
        }
    }

    private fun updateUiForStep(step: Int, progress: Int, currentText: String) {
        when (step) {
            1 -> {
                // Step 1: ASR PhoWhisper/Whisper
                pbStep1?.progress = progress
                if (progress >= 100) {
                    ivStep1Icon?.setImageResource(R.drawable.ic_step_check)
                    tvStep1StatusBottom?.text = "Hoàn tất"
                    tvStep1StatusBottom?.setTextColor(ContextCompat.getColor(this, R.color.green_success))
                    lineStep12?.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_primary))
                    tvStep1Badge?.text = "HOÀN TẤT"
                    tvStep1Badge?.setBackgroundResource(R.drawable.bg_badge_saved)
                    tvStep1Badge?.setTextColor(ContextCompat.getColor(this, R.color.green_text))
                }
                if (currentText.isNotBlank()) {
                    tvStep1Preview?.text = currentText
                }
                tvTotalTimeEstimate?.text = "Tổng thời gian ước tính: ~5 phút • Bước 1/4"
            }

            2 -> {
                // Step 2: Sửa lỗi văn bản
                ivStep2Icon?.setImageResource(R.drawable.bg_circle_blue_ring)
                pbStep2?.progress = progress
                tvStep2StatusBottom?.text = "$progress% - Đang xử lý..."
                tvStep2StatusBottom?.setTextColor(ContextCompat.getColor(this, R.color.blue_primary))

                if (currentText.isNotBlank()) {
                    tvStep2Preview?.text = currentText
                }

                if (progress >= 100) {
                    ivStep2Icon?.setImageResource(R.drawable.ic_step_check)
                    tvStep2StatusBottom?.text = "Hoàn tất sửa lỗi"
                    tvStep2StatusBottom?.setTextColor(ContextCompat.getColor(this, R.color.green_success))
                    lineStep23?.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_primary))
                    tvStep2Badge?.text = "HOÀN TẤT"
                    tvStep2Badge?.setBackgroundResource(R.drawable.bg_badge_saved)
                    tvStep2Badge?.setTextColor(ContextCompat.getColor(this, R.color.green_text))
                }
                tvTotalTimeEstimate?.text = "Tổng thời gian ước tính: ~4 phút • Bước 2/4"
            }

            3 -> {
                // Step 3: Tóm tắt cuộc họp
                ivStep3Icon?.setImageResource(R.drawable.bg_circle_blue_ring)
                pbStep3?.progress = progress
                tvStep3StatusBottom?.text = "Đang tóm tắt cuộc họp..."
                tvStep3StatusBottom?.setTextColor(ContextCompat.getColor(this, R.color.blue_primary))

                if (progress >= 100) {
                    ivStep3Icon?.setImageResource(R.drawable.ic_step_check)
                    tvStep3StatusBottom?.text = "Hoàn tất tóm tắt"
                    tvStep3StatusBottom?.setTextColor(ContextCompat.getColor(this, R.color.green_success))
                    lineStep34?.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_primary))
                }
                tvTotalTimeEstimate?.text = "Tổng thời gian ước tính: ~2 phút • Bước 3/4"
            }

            4 -> {
                // Step 4: Trích xuất hành động
                ivStep4Icon?.setImageResource(R.drawable.bg_circle_blue_ring)
                pbStep4?.progress = progress
                tvStep4StatusBottom?.text = "Đang trích xuất action items..."
                tvStep4StatusBottom?.setTextColor(ContextCompat.getColor(this, R.color.blue_primary))

                if (progress >= 100) {
                    ivStep4Icon?.setImageResource(R.drawable.ic_step_check)
                    tvStep4StatusBottom?.text = "Hoàn tất toàn bộ pipeline"
                    tvStep4StatusBottom?.setTextColor(ContextCompat.getColor(this, R.color.green_success))
                }
                tvTotalTimeEstimate?.text = "Hoàn tất! Đang chuyển sang kết quả... • Bước 4/4"
            }
        }
    }
}
