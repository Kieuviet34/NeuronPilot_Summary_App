package com.bhs.meetingnotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bhs.meetingnotes.audio.I2SAudioRecorder
import com.bhs.meetingnotes.db.MeetingDatabase
import com.bhs.meetingnotes.db.MeetingEntity
import com.bhs.meetingnotes.model.AppSettings
import com.bhs.meetingnotes.util.FormatUtils
import com.bhs.meetingnotes.util.WaveformView
import com.mediatek.neuropilot.jnidemo.R
import com.mediatek.neuropilot.jnidemo.aibox.ai.WhisperServerSTTEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MeetingRecordActivity : AppCompatActivity() {

    private val DEBUG_MODE = true
    private val TAG = "MeetingRecordActivity"


    private lateinit var audioRecorder: I2SAudioRecorder
    private lateinit var appSettings: AppSettings
    private lateinit var db: MeetingDatabase
    private lateinit var sttEngine: WhisperServerSTTEngine

    private var tvTimer: TextView? = null
    private var tvFileSize: TextView? = null
    private var tvModelAsr: TextView? = null
    private var tvModelLlm: TextView? = null
    private var vWaveform: WaveformView? = null
    private var tvRecordStatus: TextView? = null
    private var vRecordingIndicator: View? = null
    private var tvRealtimeStt: TextView? = null
    private var isPaused = false

    // To store transcribed text during recording
    private val transcriptBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_record)

        appSettings = AppSettings.getInstance(this)
        db = MeetingDatabase.getInstance(this)
        audioRecorder = I2SAudioRecorder(this)
        
        val port = getSharedPreferences("meeting_notes_prefs", MODE_PRIVATE).getInt("whisper_server_port", 8080)
        sttEngine = WhisperServerSTTEngine(com.mediatek.neuropilot.jnidemo.aibox.ai.WhisperServerClient("http://127.0.0.1:$port"))

        initViews()
        checkPermissionsAndStart()
    }

    private fun initViews() {
        tvTimer = findViewById(R.id.tv_timer)
        tvFileSize = findViewById(R.id.tv_file_size)
        tvModelAsr = findViewById(R.id.tv_model_asr)
        tvModelLlm = findViewById(R.id.tv_model_llm)
        vWaveform = findViewById(R.id.v_waveform)
        tvRecordStatus = findViewById(R.id.tv_record_status)
        vRecordingIndicator = findViewById(R.id.v_recording_indicator)
        tvRealtimeStt = findViewById(R.id.tv_realtime_stt)

        tvModelAsr?.text = appSettings.asrModel
        tvModelLlm?.text = "Qwen2.5 3B (Q4) [Threads: ${appSettings.cpuThreads}, Temp: ${appSettings.temperature}]"

        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            audioRecorder.stopRecording()
            finish()
        }

        findViewById<View>(R.id.btn_pause)?.setOnClickListener {
            if (isPaused) {
                audioRecorder.resumeRecording()
                tvRecordStatus?.text = "Đang ghi âm..."
                vRecordingIndicator?.setBackgroundResource(R.drawable.bg_circle_red)
                isPaused = false
            } else {
                audioRecorder.pauseRecording()
                tvRecordStatus?.text = "Tạm dừng"
                vRecordingIndicator?.setBackgroundResource(R.drawable.bg_circle_gray)
                isPaused = true
            }
        }

        findViewById<View>(R.id.btn_stop)?.setOnClickListener {
            stopAndProcess()
        }

        findViewById<View>(R.id.btn_bookmark)?.setOnClickListener {
            Toast.makeText(this, "Đã đánh dấu thời điểm ${tvTimer?.text}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        } else {
            startAudioRecording()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAudioRecording()
        } else {
            Toast.makeText(this, "Cần cấp quyền ghi âm để sử dụng chức năng này", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startAudioRecording() {
        audioRecorder.onTickListener = { durationMs: Long, fileSizeBytes: Long ->
            tvTimer?.text = FormatUtils.formatTimer(durationMs)
            tvFileSize?.text = FormatUtils.formatFileSize(fileSizeBytes)
        }

        audioRecorder.onAudioLevelListener = { _: Double, normalizedLevel: Int ->
            vWaveform?.updateAudioLevel(normalizedLevel)
        }

        audioRecorder.onSpeechSegmentListener = { segment ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val text = sttEngine.transcribe(segment, audioRecorder.sampleRate)
                    if (DEBUG_MODE) {
                        Log.d(TAG, "Debug: Transcribed text segment: $text")
                    }
                    if (text.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            if (transcriptBuilder.isNotEmpty()) transcriptBuilder.append(" ")
                            transcriptBuilder.append(text)
                            tvRealtimeStt?.text = transcriptBuilder.toString()
                        }
                    }
                } catch (e: Exception) {
                    if (DEBUG_MODE) {
                        Log.e(TAG, "Debug: Live transcription failed", e)
                    }
                    e.printStackTrace()
                }
            }
        }

        audioRecorder.startRecording()
    }

    private fun stopAndProcess() {
        val finalPath = audioRecorder.stopRecording()
        val finalDuration = audioRecorder.durationMs
        val currentTranscript = transcriptBuilder.toString()

        tvRecordStatus?.text = "Đang lưu..."
        vRecordingIndicator?.setBackgroundResource(R.drawable.bg_circle_gray)

        lifecycleScope.launch {
            val meetingId = withContext<Long>(Dispatchers.IO) {
                val newMeeting = MeetingEntity(
                    title = "Cuộc họp " + FormatUtils.formatDate(System.currentTimeMillis()),
                    audioFilePath = finalPath,
                    durationMs = finalDuration,
                    language = appSettings.language,
                    asrModel = appSettings.asrModel,
                    llmModel = "Qwen2.5 3B",
                    status = "PROCESSING",
                    rawTranscript = currentTranscript // Save real-time STT to DB!
                )
                db.meetingDao().insertMeeting(newMeeting)
            }

            val intent = Intent(this@MeetingRecordActivity, MeetingProcessingActivity::class.java)
            intent.putExtra("MEETING_ID", meetingId)
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.stopRecording()
    }
}