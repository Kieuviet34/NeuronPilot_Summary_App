package com.bhs.meetingnotes

import android.os.Bundle
import android.os.StatFs
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import com.bhs.meetingnotes.ai.GlossaryRepository
import com.bhs.meetingnotes.db.MeetingDatabase
import com.bhs.meetingnotes.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mediatek.neuropilot.jnidemo.R
import java.io.File
import java.util.Locale

/**
 * Màn hình Cài đặt Hệ thống (Screen 6 theo chuẩn BA v2).
 * Cấu hình Ngôn ngữ/Model ASR, CPU Threads/Temp Qwen2.5, I2S MIC, Biểu đồ Bộ nhớ thực tế, và Định dạng Xuất báo cáo.
 */
class MeetingSettingsActivity : AppCompatActivity() {

    private lateinit var appSettings: AppSettings
    private lateinit var glossaryRepo: GlossaryRepository

    // Glossary Views
    private var tvGlossaryStats: TextView? = null
    private var btnAddAlias: TextView? = null

    // Sidebar Tabs (5 Tabs)
    private var tabMenuAsr: LinearLayout? = null
    private var tabMenuLlm: LinearLayout? = null
    private var tabMenuHw: LinearLayout? = null
    private var tabMenuStorage: LinearLayout? = null
    private var tabMenuExport: LinearLayout? = null

    private var ivIconTabAsr: ImageView? = null
    private var ivIconTabLlm: ImageView? = null
    private var ivIconTabHw: ImageView? = null
    private var ivIconTabStorage: ImageView? = null
    private var ivIconTabExport: ImageView? = null

    private var tvTitleTabAsr: TextView? = null
    private var tvTitleTabLlm: TextView? = null
    private var tvTitleTabHw: TextView? = null
    private var tvTitleTabStorage: TextView? = null
    private var tvTitleTabExport: TextView? = null

    // ScrollView & Sections
    private var scrollSettingsContent: ScrollView? = null
    private var sectionAsr: View? = null
    private var sectionLlm: View? = null
    private var sectionHw: View? = null
    private var sectionStorage: View? = null
    private var sectionExport: View? = null

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

    // Dynamic Storage Breakdown
    private var vStorageModels: View? = null
    private var vStorageAudio: View? = null
    private var vStorageText: View? = null
    private var vStorageFree: View? = null
    private var tvLegendModels: TextView? = null
    private var tvLegendAudio: TextView? = null
    private var tvLegendText: TextView? = null
    private var tvLegendFree: TextView? = null

    // Export RadioGroup
    private var rgExportFormat: RadioGroup? = null
    private var rbPdf: RadioButton? = null
    private var rbTxt: RadioButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_settings)

        appSettings = AppSettings.getInstance(this)
        glossaryRepo = GlossaryRepository(MeetingDatabase.getInstance(this))

        initViews()
        loadCurrentSettings()
        updateDynamicStorageBreakdown()
        setupListeners()
    }

    private fun initViews() {
        findViewById<View>(R.id.btn_back)?.setOnClickListener { finish() }

        // Sidebar Tabs
        tabMenuAsr = findViewById(R.id.tab_menu_asr)
        tabMenuLlm = findViewById(R.id.tab_menu_llm)
        tabMenuHw = findViewById(R.id.tab_menu_hardware)
        tabMenuStorage = findViewById(R.id.tab_menu_storage)
        tabMenuExport = findViewById(R.id.tab_menu_export)

        ivIconTabAsr = findViewById(R.id.iv_icon_tab_asr)
        ivIconTabLlm = findViewById(R.id.iv_icon_tab_llm)
        ivIconTabHw = findViewById(R.id.iv_icon_tab_hw)
        ivIconTabStorage = findViewById(R.id.iv_icon_tab_storage)
        ivIconTabExport = findViewById(R.id.iv_icon_tab_export)

        tvTitleTabAsr = findViewById(R.id.tv_title_tab_asr)
        tvTitleTabLlm = findViewById(R.id.tv_title_tab_llm)
        tvTitleTabHw = findViewById(R.id.tv_title_tab_hw)
        tvTitleTabStorage = findViewById(R.id.tv_title_tab_storage)
        tvTitleTabExport = findViewById(R.id.tv_title_tab_export)

        // Sections
        scrollSettingsContent = findViewById(R.id.scroll_settings_content)
        sectionAsr = findViewById(R.id.section_asr)
        sectionLlm = findViewById(R.id.section_llm)
        sectionHw = findViewById(R.id.section_hardware)
        sectionStorage = findViewById(R.id.section_storage)
        sectionExport = findViewById(R.id.section_export)

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

        // Storage Views
        vStorageModels = findViewById(R.id.v_storage_models)
        vStorageAudio = findViewById(R.id.v_storage_audio)
        vStorageText = findViewById(R.id.v_storage_text)
        vStorageFree = findViewById(R.id.v_storage_free)
        tvLegendModels = findViewById(R.id.tv_legend_models)
        tvLegendAudio = findViewById(R.id.tv_legend_audio)
        tvLegendText = findViewById(R.id.tv_legend_text)
        tvLegendFree = findViewById(R.id.tv_legend_free)

        // Export RadioGroup
        rgExportFormat = findViewById(R.id.rg_export_format)
        rbPdf = findViewById(R.id.rb_pdf)
        rbTxt = findViewById(R.id.rb_txt)

        // Glossary & Alias
        tvGlossaryStats = findViewById(R.id.tv_glossary_stats)
        btnAddAlias = findViewById(R.id.btn_add_alias)
        btnAddAlias?.setOnClickListener { showAddAliasDialog() }
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
        tvTemperatureVal?.text = String.format(Locale.US, "%.1f (Chống ảo giác)", temp)

        // 4. Silence detection
        swSilenceDetection?.isChecked = appSettings.silenceDetection

        // 5. Default export format (PDF or TXT)
        when (appSettings.defaultExportFormat.uppercase()) {
            "TXT" -> rbTxt?.isChecked = true
            else -> rbPdf?.isChecked = true
        }

        // 6. Glossary & Alias stats
        loadGlossaryStats()
    }

    private fun loadGlossaryStats() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                glossaryRepo.ensureSeeded()
            }
            val terms = withContext(Dispatchers.IO) { glossaryRepo.termCount() }
            val aliases = withContext(Dispatchers.IO) { glossaryRepo.aliasCount() }
            tvGlossaryStats?.text = "$terms thuật ngữ chuẩn • $aliases quy tắc alias sửa lỗi"
        }
    }

    private fun showAddAliasDialog() {
        val ctx = this
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val etAlias = EditText(ctx).apply {
            hint = "Từ nghe nhầm / phát âm (vd: b ét pê, đờ rai vơ)"
            textSize = 14f
        }
        val etTerm = EditText(ctx).apply {
            hint = "Thuật ngữ chuẩn thay thế (vd: BSP, driver)"
            textSize = 14f
            setPadding(0, 24, 0, 16)
        }
        val cbSuggest = CheckBox(ctx).apply {
            text = "Chỉ gợi ý (khi có ngữ cảnh kỹ thuật xung quanh)"
            textSize = 13f
            isChecked = false
        }

        container.addView(etAlias)
        container.addView(etTerm)
        container.addView(cbSuggest)

        AlertDialog.Builder(ctx)
            .setTitle("Thêm quy tắc sửa lỗi (Alias)")
            .setView(container)
            .setPositiveButton("Lưu") { _, _ ->
                val alias = etAlias.text.toString().trim()
                val term = etTerm.text.toString().trim()
                val suggest = cbSuggest.isChecked

                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        glossaryRepo.addAlias(alias, term, suggest)
                    }
                    when (result) {
                        GlossaryRepository.AddResult.ADDED -> {
                            Toast.makeText(ctx, "Đã thêm alias: $alias → $term", Toast.LENGTH_SHORT).show()
                            loadGlossaryStats()
                        }
                        GlossaryRepository.AddResult.DUPLICATE -> {
                            Toast.makeText(ctx, "Alias này đã tồn tại trong từ điển", Toast.LENGTH_SHORT).show()
                        }
                        GlossaryRepository.AddResult.INVALID -> {
                            Toast.makeText(ctx, "Nội dung alias hoặc thuật ngữ không hợp lệ", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun updateDynamicStorageBreakdown() {
        try {
            // Models (PhoWhisper + Qwen2.5 3B ~ 2.1 GB)
            val modelMb = 2100.0

            // Audio files in app internal/external storage
            val audioDir = File(getExternalFilesDir(null), "MeetingNotes")
            val audioBytes = if (audioDir.exists()) {
                audioDir.listFiles()?.sumOf { it.length() } ?: 0L
            } else 0L
            val audioMb = (audioBytes.toDouble() / (1024.0 * 1024.0)).coerceAtLeast(1.0)

            // Database & text
            val dbFile = getDatabasePath("meeting_notes_db")
            val dbBytes = if (dbFile.exists()) dbFile.length() else 0L
            val dbMb = (dbBytes.toDouble() / (1024.0 * 1024.0)).coerceAtLeast(0.5)

            // Free space on disk
            val stat = StatFs(filesDir.absolutePath)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val freeMb = freeBytes.toDouble() / (1024.0 * 1024.0)
            val freeGb = freeMb / 1024.0

            // Update texts
            tvLegendModels?.text = "🔵 Models: ${String.format(Locale.US, "%.1f GB", modelMb / 1024.0)}"
            tvLegendAudio?.text = "🟠 Tệp WAV: ${String.format(Locale.US, "%.1f MB", audioMb)}"
            tvLegendText?.text = "🟢 Dữ liệu DB: ${String.format(Locale.US, "%.1f MB", dbMb)}"
            tvLegendFree?.text = "⚪ Trống: ${String.format(Locale.US, "%.1f GB", freeGb)}"

            // Update layout weights proportionally
            setWeight(vStorageModels, (modelMb / 100).toInt().coerceAtLeast(10))
            setWeight(vStorageAudio, (audioMb / 50).toInt().coerceAtLeast(2))
            setWeight(vStorageText, 1)
            setWeight(vStorageFree, (freeMb / 100).toInt().coerceAtLeast(30))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setWeight(view: View?, weight: Int) {
        val params = view?.layoutParams as? LinearLayout.LayoutParams
        if (params != null) {
            params.weight = weight.toFloat()
            view.layoutParams = params
        }
    }

    private fun setupListeners() {
        // Tab Clicks (5 tabs)
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
        tabMenuStorage?.setOnClickListener {
            setActiveTab(3)
            scrollToView(sectionStorage)
        }
        tabMenuExport?.setOnClickListener {
            setActiveTab(4)
            scrollToView(sectionExport)
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
                tvTemperatureVal?.text = String.format(Locale.US, "%.1f (Chống ảo giác)", value)
                appSettings.temperature = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Silence detection switch
        swSilenceDetection?.setOnCheckedChangeListener { _, isChecked ->
            appSettings.silenceDetection = isChecked
        }

        // Export format RadioGroup (PDF or TXT)
        rgExportFormat?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
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

        tabMenuStorage?.setBackgroundResource(if (tabIndex == 3) R.drawable.bg_badge_active_local else 0)
        ivIconTabStorage?.setColorFilter(if (tabIndex == 3) blue else gray)
        tvTitleTabStorage?.setTextColor(if (tabIndex == 3) blue else gray)

        tabMenuExport?.setBackgroundResource(if (tabIndex == 4) R.drawable.bg_badge_active_local else 0)
        ivIconTabExport?.setColorFilter(if (tabIndex == 4) blue else gray)
        tvTitleTabExport?.setTextColor(if (tabIndex == 4) blue else gray)
    }

    private fun scrollToView(view: View?) {
        view?.let {
            scrollSettingsContent?.smoothScrollTo(0, it.top)
        }
    }
}
