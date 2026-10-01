package com.mediatek.neuropilot.jnidemo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.mediatek.neuropilot.jnidemo.chat.AppSettings

class SettingsActivity : Activity() {

    private var currentModel: String? = null
    private var llamaAvailable = false
    private var qwenAvailable = false
    private var maxTurnsInput: EditText? = null
    private var currentMaxTurns = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContentView(R.layout.activity_settings)

        currentModel = intent.getStringExtra(EXTRA_CURRENT_MODEL) ?: MODEL_LLAMA
        llamaAvailable = intent.getBooleanExtra(EXTRA_LLAMA_AVAILABLE, false)
        qwenAvailable = intent.getBooleanExtra(EXTRA_QWEN_AVAILABLE, false)
        currentMaxTurns = AppSettings.getMaxTurns(this)

        val backButton = findViewById<View>(R.id.btn_back)
        val modelRadioGroup = findViewById<RadioGroup>(R.id.modelRadioGroup)
        val status = findViewById<TextView>(R.id.tv_settings_model_status)
        maxTurnsInput = findViewById(R.id.et_max_turns)
        val saveTurnsButton = findViewById<View>(R.id.btn_save_turns)

        status?.text = "${modelDisplayName(currentModel ?: MODEL_LLAMA)} Active"
        maxTurnsInput?.setText(currentMaxTurns.toString())

        modelRadioGroup.findViewById<View>(R.id.radioLlama).isEnabled = llamaAvailable
        modelRadioGroup.findViewById<View>(R.id.radioQwen).isEnabled = qwenAvailable
        modelRadioGroup.check(if (MODEL_QWEN == currentModel) R.id.radioQwen else R.id.radioLlama)

        modelRadioGroup.setOnCheckedChangeListener(object : RadioGroup.OnCheckedChangeListener {
            override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
                val selected = if (checkedId == R.id.radioQwen) MODEL_QWEN else MODEL_LLAMA
                if (selected == currentModel) return
                if (!isAvailable(selected)) {
                    group.setOnCheckedChangeListener(null)
                    group.check(if (MODEL_QWEN == currentModel) R.id.radioQwen else R.id.radioLlama)
                    group.setOnCheckedChangeListener(this)
                    Toast.makeText(
                        this@SettingsActivity,
                        "Model khong co san tren thiet bi nay", Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                val result = Intent()
                result.putExtra(EXTRA_SELECTED_MODEL, selected)
                setResult(RESULT_OK, result)
                finish()
            }
        })

        saveTurnsButton?.setOnClickListener {
            saveMaxTurns(true)
        }

        backButton.setOnClickListener {
            saveMaxTurns(false)
            finish()
        }

        bindPlaceholderFeature(R.id.settings_vision_section)
        bindPlaceholderFeature(R.id.settings_inference_section)
        bindPlaceholderFeature(R.id.settings_voice_section)
    }

    override fun onPause() {
        saveMaxTurns(false)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun isAvailable(model: String): Boolean {
        return if (MODEL_QWEN == model) qwenAvailable else llamaAvailable
    }

    private fun modelDisplayName(model: String): String {
        return if (MODEL_QWEN == model) "Qwen 2.5" else "Llama 3.2"
    }

    private fun saveMaxTurns(showToast: Boolean) {
        val input = maxTurnsInput ?: return

        val rawValue = input.text.toString().trim()
        if (rawValue.isEmpty()) {
            input.setText(currentMaxTurns.toString())
            if (showToast) {
                Toast.makeText(this, "Số turn phải lớn hơn 0", Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            val parsed = rawValue.toInt()
            if (parsed < 1) {
                input.setText(currentMaxTurns.toString())
                if (showToast) {
                    Toast.makeText(this, "Số turn phải lớn hơn 0", Toast.LENGTH_SHORT).show()
                }
                return
            }

            currentMaxTurns = parsed
            AppSettings.setMaxTurns(this, parsed)
            if (showToast) {
                Toast.makeText(this, "Lưu thành công", Toast.LENGTH_SHORT).show()
            }
        } catch (e: NumberFormatException) {
            input.setText(currentMaxTurns.toString())
            if (showToast) {
                Toast.makeText(this, "Số turn không hợp lệ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindPlaceholderFeature(viewId: Int) {
        val view = findViewById<View>(viewId) ?: return
        view.setOnClickListener {
            Toast.makeText(this@SettingsActivity, FEATURE_IN_DEVELOPMENT, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    companion object {
        const val EXTRA_CURRENT_MODEL = "current_model"
        const val EXTRA_SELECTED_MODEL = "selected_model"
        const val EXTRA_LLAMA_AVAILABLE = "llama_available"
        const val EXTRA_QWEN_AVAILABLE = "qwen_available"
        const val EXTRA_MAX_TURNS = "max_turns"

        const val MODEL_LLAMA = "LLAMA"
        const val MODEL_QWEN = "QWEN"

        private const val FEATURE_IN_DEVELOPMENT = "Tính năng đang phát triển"
    }
}
