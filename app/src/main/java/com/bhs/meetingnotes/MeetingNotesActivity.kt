package com.bhs.meetingnotes

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.mediatek.neuropilot.jnidemo.R

class MeetingNotesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_list)
        
        findViewById<View>(R.id.btn_record)?.setOnClickListener {
            startActivity(Intent(this, MeetingRecordActivity::class.java))
        }
        
        findViewById<View>(R.id.btn_settings)?.setOnClickListener {
            startActivity(Intent(this, MeetingSettingsActivity::class.java))
        }
    }
}
