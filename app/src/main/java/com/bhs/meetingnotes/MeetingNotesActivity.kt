package com.bhs.meetingnotes

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bhs.meetingnotes.model.AppSettings
import com.mediatek.neuropilot.jnidemo.R
import java.io.File
import kotlin.concurrent.thread

import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.bhs.meetingnotes.db.MeetingDatabase
import com.bhs.meetingnotes.db.MeetingEntity
import com.bhs.meetingnotes.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MeetingNotesActivity : AppCompatActivity() {
    private val TAG = "MeetingNotesActivity"
    private val DEBUG_MODE = true
    private lateinit var appSettings: AppSettings
    private lateinit var db: MeetingDatabase

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleSelectedAudio(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_list)
        
        appSettings = AppSettings.getInstance(this)
        db = MeetingDatabase.getInstance(this)
        
        val btnUpload = findViewById<View>(R.id.btn_upload_audio)
        if (DEBUG_MODE) {
            btnUpload?.visibility = View.VISIBLE
            btnUpload?.setOnClickListener {
                showUploadModelSelectionDialog()
            }
        } else {
            btnUpload?.visibility = View.GONE
        }
        
        findViewById<View>(R.id.btn_record)?.setOnClickListener {
            showLanguageSelectionDialog()
        }
        
        findViewById<View>(R.id.btn_settings)?.setOnClickListener {
            startActivity(Intent(this, MeetingSettingsActivity::class.java))
        }
    }

    private fun showLanguageSelectionDialog() {
        val options = arrayOf("Tiếng Việt (PhoWhisper)", "English (Whisper)")
        val checkedItem = if (appSettings.language == "vi") 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Chọn ngôn ngữ cuộc họp")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val selectedLang = if (which == 0) "vi" else "en"
                appSettings.language = selectedLang
                appSettings.asrModel = if (selectedLang == "vi") "PhoWhisper (VI)" else "Whisper (EN)"
                
                // Save the port based on the selected language
                val port = if (selectedLang == "vi") 8080 else 8082
                getSharedPreferences("meeting_notes_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt("whisper_server_port", port)
                    .apply()
                
                dialog.dismiss()
                
                startServerAndProceed(selectedLang)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showUploadModelSelectionDialog() {
        val options = arrayOf("Tiếng Việt (PhoWhisper - Port 8080)", "English (Whisper - Port 8082)")
        val checkedItem = if (appSettings.language == "vi") 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Chọn Model nhận dạng cho file")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val selectedLang = if (which == 0) "vi" else "en"
                appSettings.language = selectedLang
                appSettings.asrModel = if (selectedLang == "vi") "PhoWhisper (VI)" else "Whisper (EN)"
                
                val port = if (selectedLang == "vi") 8080 else 8082
                getSharedPreferences("meeting_notes_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt("whisper_server_port", port)
                    .apply()
                
                dialog.dismiss()
                pickAudioLauncher.launch("*/*")
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun startServerAndProceed(language: String) {
        val port = if (language == "vi") 8080 else 8082
        Log.d(TAG, "Selected language $language, using Whisper server port $port")
        startActivity(Intent(this@MeetingNotesActivity, MeetingRecordActivity::class.java))
    }

    private fun handleSelectedAudio(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dir = File(getExternalFilesDir(null), "MeetingNotes")
                if (!dir.exists()) dir.mkdirs()

                val timestamp = System.currentTimeMillis()
                val localFile = File(dir, "upload_$timestamp.wav")

                contentResolver.openInputStream(uri)?.use { input ->
                    localFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val newMeeting = MeetingEntity(
                    title = "Cuộc họp (File) " + FormatUtils.formatDate(timestamp),
                    audioFilePath = localFile.absolutePath,
                    durationMs = 0L,
                    language = appSettings.language,
                    asrModel = appSettings.asrModel,
                    llmModel = "Qwen2.5 3B",
                    status = "PROCESSING",
                    rawTranscript = "" // Will be transcribed in MeetingAiPipeline Step 1!
                )

                val meetingId = db.meetingDao().insertMeeting(newMeeting)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MeetingNotesActivity, "Đã tải file âm thanh, chuyển sang xử lý AI...", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@MeetingNotesActivity, MeetingProcessingActivity::class.java)
                    intent.putExtra("MEETING_ID", meetingId)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload audio file", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MeetingNotesActivity, "Lỗi đọc file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
