package com.bhs.meetingnotes

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bhs.meetingnotes.ai.MeetingAiPipeline
import com.mediatek.neuropilot.jnidemo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MeetingProcessingActivity : AppCompatActivity() {

    private var tvStep1Preview: TextView? = null
    private var tvStep2Preview: TextView? = null
    private var pbStep2: ProgressBar? = null
    private var tvTotalTimeEstimate: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_processing)

        tvStep1Preview = findViewById(R.id.tv_step1_preview)
        tvStep2Preview = findViewById(R.id.tv_step2_preview)
        pbStep2 = findViewById(R.id.pb_step2)
        tvTotalTimeEstimate = findViewById(R.id.tv_total_time_estimate)

        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            finish()
        }

        val meetingId = intent.getLongExtra("MEETING_ID", -1)
        if (meetingId != -1L) {
            startPipeline(meetingId)
        } else {
            // Optional fallback or show error
            tvStep1Preview?.text = "Lỗi: Không nhận được ID cuộc họp"
        }
    }

    private fun startPipeline(meetingId: Long) {
        val pipeline = MeetingAiPipeline(this)
        lifecycleScope.launch {
            val result = pipeline.runPipeline(meetingId) { step, progress, currentText ->
                lifecycleScope.launch(Dispatchers.Main) {
                    when (step) {
                        1 -> {
                            tvStep1Preview?.text = currentText
                        }
                        2 -> {
                            pbStep2?.progress = progress
                            tvStep2Preview?.text = currentText
                            tvTotalTimeEstimate?.text = "Tổng thời gian ước tính: ~5 phút - Bước $step/4"
                        }
                        3 -> {
                            tvTotalTimeEstimate?.text = "Tổng thời gian ước tính: ~5 phút - Bước $step/4"
                        }
                        4 -> {
                            tvTotalTimeEstimate?.text = "Tổng thời gian ước tính: ~5 phút - Bước $step/4"
                        }
                    }
                }
            }

            if (result != null && !isFinishing) {
                val intent = Intent(this@MeetingProcessingActivity, MeetingResultActivity::class.java)
                intent.putExtra("MEETING_ID", meetingId)
                startActivity(intent)
                finish()
            }
        }
    }
}
