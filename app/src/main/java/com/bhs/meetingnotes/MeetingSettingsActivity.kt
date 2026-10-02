package com.bhs.meetingnotes

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bhs.meetingnotes.model.AppSettings
import com.mediatek.neuropilot.jnidemo.R

class MeetingSettingsActivity : AppCompatActivity() {

    private lateinit var appSettings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_settings)
        
        appSettings = AppSettings.getInstance(this)

        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            finish()
        }
        
        setupSliders()
    }
    
    private fun setupSliders() {
        val tvCpuThreads = findViewById<TextView>(R.id.tv_cpu_threads_val)
        val sbCpuThreads = findViewById<SeekBar>(R.id.sb_cpu_threads)
        val tvTemp = findViewById<TextView>(R.id.tv_temperature_val)
        val sbTemp = findViewById<SeekBar>(R.id.sb_temperature)
        
        // Init
        sbCpuThreads.progress = appSettings.cpuThreads
        tvCpuThreads.text = appSettings.cpuThreads.toString()
        
        sbTemp.progress = (appSettings.temperature * 10).toInt()
        tvTemp.text = String.format("%.1f", appSettings.temperature)
        
        sbCpuThreads.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceAtLeast(1)
                tvCpuThreads.text = value.toString()
                appSettings.cpuThreads = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        sbTemp.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 10f
                tvTemp.text = String.format("%.1f", value)
                appSettings.temperature = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}
