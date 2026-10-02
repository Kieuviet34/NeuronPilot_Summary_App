package com.bhs.meetingnotes

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bhs.meetingnotes.db.MeetingDatabase
import com.bhs.meetingnotes.model.ActionItem
import com.bhs.meetingnotes.util.FormatUtils
import com.bhs.meetingnotes.util.ReportExporter
import com.bhs.meetingnotes.util.TtsManager
import com.mediatek.neuropilot.jnidemo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MeetingResultActivity : AppCompatActivity() {

    private lateinit var db: MeetingDatabase
    private var ttsManager: TtsManager? = null
    private var summaryText: String = ""
    private var meetingLang: String = "vi"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_result)
        
        db = MeetingDatabase.getInstance(this)
        ttsManager = TtsManager(this)

        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            finish()
        }
        
        val meetingId = intent.getLongExtra("MEETING_ID", -1)
        if (meetingId != -1L) {
            loadMeetingData(meetingId)
        }
        
        findViewById<View>(R.id.btn_read_summary)?.setOnClickListener {
            ttsManager?.speak(summaryText, meetingLang)
        }
        
        findViewById<View>(R.id.btn_export)?.setOnClickListener {
            lifecycleScope.launch {
                val meeting = withContext(Dispatchers.IO) {
                    db.meetingDao().getMeetingById(meetingId)
                }
                if (meeting != null) {
                    ReportExporter.exportReport(this@MeetingResultActivity, meeting, "PDF")
                }
            }
        }
    }
    
    private fun loadMeetingData(id: Long) {
        lifecycleScope.launch {
            val meeting = withContext(Dispatchers.IO) {
                db.meetingDao().getMeetingById(id)
            }
            if (meeting != null) {
                summaryText = meeting.summary
                meetingLang = meeting.language
                findViewById<TextView>(R.id.tv_summary_content)?.text = meeting.summary
                findViewById<TextView>(R.id.tv_header_title)?.text = meeting.title
                
                findViewById<TextView>(R.id.tv_duration)?.text = FormatUtils.formatDuration(meeting.durationMs)
                findViewById<TextView>(R.id.tv_word_count)?.text = "${meeting.wordCount} từ"
                
                val actionsContainer = findViewById<LinearLayout>(R.id.ll_actions_container)
                actionsContainer.removeAllViews()
                
                val actions = ActionItem.fromJson(meeting.actionItemsJson)
                findViewById<TextView>(R.id.tv_actions_header)?.text = "📋 Hành động tiếp theo (${actions.size})"
                
                for (action in actions) {
                    val actionView = layoutInflater.inflate(R.layout.item_action_detail, actionsContainer, false)
                    actionView.findViewById<TextView>(R.id.tv_task_name).text = "Task name: ${action.task}"
                    actionView.findViewById<TextView>(R.id.tv_assignee).text = "Assign to ${action.assignee}"
                    actionView.findViewById<TextView>(R.id.tv_deadline).text = "deadline: ${action.deadline}"
                    
                    // Note: ActionItem model currently doesn't have a 'summarize_task' field. 
                    // Using task field as a placeholder or we can omit it if not available in JSON
                    actionView.findViewById<TextView>(R.id.tv_summarize_task).text = "summarize task : ${action.task}"
                    
                    actionsContainer.addView(actionView)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ttsManager?.shutdown()
    }
}
