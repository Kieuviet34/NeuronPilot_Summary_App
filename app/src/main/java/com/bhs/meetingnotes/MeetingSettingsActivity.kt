package com.bhs.meetingnotes

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.bhs.meetingnotes.model.AppSettings
import com.mediatek.neuropilot.jnidemo.R

/**
 * Màn hình Cài đặt Hệ thống (Screen 6 theo chuẩn BA v2).
 * Cấu hình Ngôn ngữ/Model ASR, CPU Threads/Temp Qwen2.5, I2S MIC, Biểu đồ Bộ nhớ, và Định dạng Xuất báo cáo.
 */
class MeetingSettingsActivity : AppCompatActivity() {

    private lateinit var appSettings: AppSettings

    // Sidebar Tabs
    private var tabMenuAsr: LinearLayout? = null
    private var tabMenuLlm: LinearLayout? = null
    private var tabMenuHw: LinearLayout? = null
    private var ivIconTabAsr: ImageView? = null
    private var ivIconTabLlm: ImageView? = null
    private var ivIconTabHw: ImageView? = null
    private var tvTitleTabAsr: TextView? = null
    private var tvTitleTabLlm: TextView? = null
    private var tvTitleTabHw: TextView? = null

    // ScrollView & Sections
    private var scrollSettingsContent: ScrollView? = null
    private var sectionAsr: View? = null
    private var sectionLlm: View? = null
    private var sectionHw: View? = null

    // ASR Cards
    private var cardAsrVi: LinearLayout? = null
    private var cardAsrEn: LinearLayout? = null
    private var tvAsrViCode: TextView? = null
    private var tvAsrEnCode: TextView? = null

    // Sliders
    private var tvCpuThreadsVal: TextView? = null
    private var sbCpuThreads: SeekBar? = null
    private var tvTemperatureVal: TextView? = null
    private var sbTemperature: SeekBar? = null

    // Hardware Switch
    private var swSilenceDetection: SwitchCompat? = null

    // Export RadioGroup
    private var rgExportFormat: RadioGroup? = null
    private var rbPdf: RadioButton? = null
    private var rbDocx: RadioButton? = null
    private var rbTxt: RadioButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_settings)

        appSettings = AppSettings.getInstance(this)

        initViews()
        loadCurrentSettings()
        setupListeners()
    }

    private fun initViews() {
        findViewById<View>(R.id.btn_back)?.setOnClickListener { finish() }

        // Sidebar Tabs
        tabMenuAsr = findViewById(R.id.tab_menu_asr)
        tabMenuLlm = findViewById(R.id.tab_menu_llm)
        tabMenuHw = findViewById(R.id.tab_menu_hardware)
        ivIconTabAsr = findViewById(R.id.iv_icon_tab_asr)
        ivIconTabLlm = findViewById(R.id.iv_icon_tab_llm)
        ivIconTabHw = findViewById(R.id.iv_icon_tab_hw)
        tvTitleTabAsr = findViewById(R.id.tv_title_tab_asr)
        tvTitleTabLlm = findViewById(R.id.tv_title_tab_llm)
        tvTitleTabHw = findViewById(R.id.tv_title_tab_hw)

        // Sections
        scrollSettingsContent = findViewById(R.id.scroll_settings_content)
        sectionAsr = findViewById(R.id.section_asr)
        sectionLlm = findViewById(R.id.section_llm)
        sectionHw = findViewById(R.id.section_hardware)

        // ASR Cards
        cardAsrVi = findViewById(R.id.card_asr_vi)
        cardAsrEn = findViewById(R.id.card_asr_en)
        tvAsrViCode = findViewById(R.id.tv_asr_vi_code)
        tvAsrEnCode = findViewById(R.id.tv_asr_en_code)

        // Sliders
        tvCpuThreadsVal = findViewById(R.id.tv_cpu_threads_val)
        sbCpuThreads = findViewById(R.id.sb_cpu_threads)
        tvTemperatureVal = findViewById(R.id.tv_temperature_val)
        sbTemperature = findViewById(R.id.sb_temperature)

        // Switch
        swSilenceDetection = findViewById(R.id.sw_silence_detection)

        // Export RadioGroup
        rgExportFormat = findViewById(R.id.rg_export_format)
        rbPdf = findViewById(R.id.rb_pdf)
        rbDocx = findViewById(R.id.rb_docx)
        rbTxt = findViewById(R.id.rb_txt)
    }

    private fun loadCurrentSettings() {
        // 1. Language & ASR Card
        updateAsrCardSelection(appSettings.language)

        // 2. CPU Threads
        val threads = appSettings.cpuThreads
        sbCpuThreads?.progress = threads
        tvCpuThreadsVal?.text = "$threads luồng (Tối ưu Genio 720)"

        // 3. Temperature
        val temp = appSettings.temperature
        sbTemperature?.progress = (temp * 10).toInt()
        tvTemperatureVal?.text = String.format("%.1f (Chống ảo giác)", temp)

        // 4. Silence detection
        swSilenceDetection?.isChecked = appSettings.silenceDetection

        // 5. Default export format
        when (appSettings.defaultExportFormat.uppercase()) {
            "DOCX" -> rbDocx?.isChecked = true
            "TXT" -> rbTxt?.isChecked = true
            else -> rbPdf?.isChecked = true
        }
    }

    private fun setupListeners() {
        // Tab Navigation Clicks
        tabMenuAsr?.setOnClickListener {
            setActiveTab(0)
            scrollToView(sectionAsr)
        }
        tabMenuLlm?.setOnClickListener {
            setActiveTab(1)
            scrollToView(sectionLlm)
        }
        tabMenuHw?.setOnClickListener {
            setActiveTab(2)
            scrollToView(sectionHw)
        }

        // ASR Card Clicks
        cardAsrVi?.setOnClickListener {
            appSettings.language = "vi"
            appSettings.asrModel = "PhoWhisper (VI)"
            updateAsrCardSelection("vi")
        }
        cardAsrEn?.setOnClickListener {
            appSettings.language = "en"
            appSettings.asrModel = "Whisper (EN)"
            updateAsrCardSelection("en")
        }

        // CPU Threads
        sbCpuThreads?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceAtLeast(1)
                tvCpuThreadsVal?.text = "$value luồng (Tối ưu Genio 720)"
                appSettings.cpuThreads = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Temperature
        sbTemperature?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = (progress.coerceAtLeast(1)) / 10f
                tvTemperatureVal?.text = String.format("%.1f (Chống ảo giác)", value)
                appSettings.temperature = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Silence detection switch
        swSilenceDetection?.setOnCheckedChangeListener { _, isChecked ->
            appSettings.silenceDetection = isChecked
        }

        // Export format RadioGroup
        rgExportFormat?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_docx -> appSettings.defaultExportFormat = "DOCX"
                R.id.rb_txt -> appSettings.defaultExportFormat = "TXT"
                else -> appSettings.defaultExportFormat = "PDF"
            }
        }
    }

    private fun updateAsrCardSelection(lang: String) {
        val isVi = lang.equals("vi", ignoreCase = true)
        val blue = ContextCompat.getColor(this, R.color.blue_primary)
        val gray = ContextCompat.getColor(this, R.color.text_secondary)

        if (isVi) {
            cardAsrVi?.setBackgroundResource(R.drawable.bg_card_setting_active)
            tvAsrViCode?.setTextColor(blue)

            cardAsrEn?.setBackgroundResource(R.drawable.bg_card_setting_inactive)
            tvAsrEnCode?.setTextColor(gray)
        } else {
            cardAsrVi?.setBackgroundResource(R.drawable.bg_card_setting_inactive)
            tvAsrViCode?.setTextColor(gray)

            cardAsrEn?.setBackgroundResource(R.drawable.bg_card_setting_active)
            tvAsrEnCode?.setTextColor(blue)
        }
    }

    private fun setActiveTab(tabIndex: Int) {
        val blue = ContextCompat.getColor(this, R.color.blue_primary)
        val gray = ContextCompat.getColor(this, R.color.text_secondary)

        tabMenuAsr?.setBackgroundResource(if (tabIndex == 0) R.drawable.bg_badge_active_local else 0)
        ivIconTabAsr?.setColorFilter(if (tabIndex == 0) blue else gray)
        tvTitleTabAsr?.setTextColor(if (tabIndex == 0) blue else gray)

        tabMenuLlm?.setBackgroundResource(if (tabIndex == 1) R.drawable.bg_badge_active_local else 0)
        ivIconTabLlm?.setColorFilter(if (tabIndex == 1) blue else gray)
        tvTitleTabLlm?.setTextColor(if (tabIndex == 1) blue else gray)

        tabMenuHw?.setBackgroundResource(if (tabIndex == 2) R.drawable.bg_badge_active_local else 0)
        ivIconTabHw?.setColorFilter(if (tabIndex == 2) blue else gray)
        tvTitleTabHw?.setTextColor(if (tabIndex == 2) blue else gray)
    }

    private fun scrollToView(view: View?) {
        view?.let {
            scrollSettingsContent?.smoothScrollTo(0, it.top)
        }
    }
}
