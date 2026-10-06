package com.mediatek.neuropilot.jnidemo.aibox.ai

import android.util.Log
import androidx.annotation.Keep
import java.io.File

import java.util.Locale

class NeuroPilotLlmBridge {
    private var modelHandle: Long = 0L

    fun initDefaultModel(): Boolean {
        if (!nativeLibrariesLoaded) {
            Log.w(TAG, "Native LLM libraries are not loaded")
            return false
        }

        setupNpuPermissions()

        val candidate = selectModelConfig()
        if (candidate == null) {
            Log.w(TAG, "No complete NeuroPilot Qwen 2.5 file set found")
            return false
        }

        return try {
            modelHandle = nativeInitModel(candidate.path)
            if (modelHandle == 0L) {
                Log.e(TAG, "nativeInitModel returned 0 for ${candidate.path}")
                false
            } else {
                Log.i(TAG, "Loaded NeuroPilot LLM model from ${candidate.path}")
                true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "initDefaultModel failed", t)
            modelHandle = 0L
            false
        }
    }

    private fun setupNpuPermissions() {
        try {
            Runtime.getRuntime().exec(arrayOf(
                "su", "-c",
                "chmod 666 /dev/apusys /dev/apuext /dev/apusys_apummu /dev/dma_heap/*; setenforce 0; setprop vendor.debug.mtk_llm.loglevel 0"
            )).waitFor()
            Log.d(TAG, "NPU permissions configured via su")
        } catch (t: Throwable) {
            Log.w(TAG, "setupNpuPermissions note: ${t.message}")
        }
    }

    data class LlmResult(val text: String, val statsLog: String)

    fun computeStreamingBlocking(userPrompt: String): String {
        val handle = modelHandle
        if (handle == 0L) return ""

        val formattedPrompt = buildChatPrompt(userPrompt)
        val tokens = StringBuilder()
        nativeResetModel(handle)
        nativeComputeStreaming(handle, formattedPrompt, object : TokenCallback {
            override fun onTokenReceived(token: String): Boolean {
                tokens.append(token)
                return true
            }
        })
        return stripStopMarkers(tokens.toString())
    }

    fun correctTextBlocking(rawText: String, onToken: ((String, String?) -> Unit)? = null): LlmResult {
        val handle = modelHandle
        if (handle == 0L) return LlmResult("", "")

        val systemPrompt = "Bạn là trợ lý sửa lỗi văn bản tiếng Việt. Hãy sửa lỗi chính tả, ngữ pháp, thêm dấu câu đúng, sửa tên riêng và thuật ngữ kỹ thuật. Giữ nguyên nội dung và ý nghĩa, không thêm bớt thông tin. Trả về văn bản đã sửa."
        val formattedPrompt = buildString {
            append(QWEN_SYS_OPEN)
            append(systemPrompt)
            append('\n')
            append(QWEN_EOT)
            append('\n')
            append(QWEN_USR_OPEN)
            append(rawText)
            append('\n')
            append(QWEN_EOT)
            append('\n')
            append(QWEN_AST_OPEN)
        }
        
        val inputTokens = nativeCountTokens(formattedPrompt)
        
        val tokens = StringBuilder()
        var outputTokens = 0
        val startTime = System.currentTimeMillis()
        
        nativeResetModel(handle)
        nativeComputeStreaming(handle, formattedPrompt, object : TokenCallback {
            override fun onTokenReceived(token: String): Boolean {
                outputTokens++
                tokens.append(token)
                onToken?.invoke(stripStopMarkers(tokens.toString()), null)
                return true
            }
        })
        val endTime = System.currentTimeMillis()
        val elapsed = (endTime - startTime) / 1000f
        val speed = if (elapsed > 0) outputTokens / elapsed else 0f
        val cleanText = stripStopMarkers(tokens.toString())
        val statsLog = "\n\n[Debug] Input tokens: $inputTokens | Generate speed: ${String.format(Locale.US, "%.2f", speed)} token/s"
        onToken?.invoke(cleanText, statsLog)
        
        return LlmResult(cleanText, statsLog)
    }

    fun extractActionsBlocking(text: String, onToken: ((String, String?) -> Unit)? = null): LlmResult {
        val handle = modelHandle
        if (handle == 0L) return LlmResult("", "")

        val systemPrompt = "Bạn là trợ lý trích xuất hành động từ cuộc họp. Từ nội dung cuộc họp bên dưới, hãy liệt kê tất cả các hành động cần thực hiện sau cuộc họp. Định dạng kết quả thành JSON array chứa các object có key: 'id', 'task', 'assignee', 'deadline'. Chỉ trả về JSON array hợp lệ, không giải thích."
        val formattedPrompt = buildString {
            append(QWEN_SYS_OPEN)
            append(systemPrompt)
            append('\n')
            append(QWEN_EOT)
            append('\n')
            append(QWEN_USR_OPEN)
            append(text)
            append('\n')
            append(QWEN_EOT)
            append('\n')
            append(QWEN_AST_OPEN)
        }
        
        val inputTokens = nativeCountTokens(formattedPrompt)
        
        val tokens = StringBuilder()
        var outputTokens = 0
        val startTime = System.currentTimeMillis()
        
        nativeResetModel(handle)
        nativeComputeStreaming(handle, formattedPrompt, object : TokenCallback {
            override fun onTokenReceived(token: String): Boolean {
                outputTokens++
                tokens.append(token)
                onToken?.invoke(stripStopMarkers(tokens.toString()), null)
                return true
            }
        })
        
        val endTime = System.currentTimeMillis()
        val elapsed = (endTime - startTime) / 1000f
        val speed = if (elapsed > 0) outputTokens / elapsed else 0f
        val cleanText = stripStopMarkers(tokens.toString())
        val statsLog = "\n\n[Debug] Input tokens: $inputTokens | Generate speed: ${String.format(Locale.US, "%.2f", speed)} token/s"
        onToken?.invoke(cleanText, statsLog)

        return LlmResult(cleanText, statsLog)
    }

    fun summarizeBlocking(transcriptText: String, onToken: ((String, String?) -> Unit)? = null): LlmResult {
        val handle = modelHandle
        if (handle == 0L) return LlmResult("", "")

        val truncated = truncateForContext(transcriptText)
        val formattedPrompt = buildSummaryPrompt(truncated)
        
        val inputTokens = nativeCountTokens(formattedPrompt)
        val tokens = StringBuilder()
        var outputTokens = 0
        val startTime = System.currentTimeMillis()
        
        nativeResetModel(handle)
        nativeComputeStreaming(handle, formattedPrompt, object : TokenCallback {
            override fun onTokenReceived(token: String): Boolean {
                outputTokens++
                tokens.append(token)
                onToken?.invoke(stripStopMarkers(tokens.toString()), null)
                return true
            }
        })
        
        val endTime = System.currentTimeMillis()
        val elapsed = (endTime - startTime) / 1000f
        val speed = if (elapsed > 0) outputTokens / elapsed else 0f
        val cleanText = stripStopMarkers(tokens.toString())
        val statsLog = "\n\n[Debug] Input tokens: $inputTokens | Generate speed: ${String.format(Locale.US, "%.2f", speed)} token/s"
        onToken?.invoke(cleanText, statsLog)
        
        return LlmResult(cleanText, statsLog)
    }

    private fun buildSummaryPrompt(transcriptText: String): String {
        val systemPrompt = "Bạn là trợ lý tóm tắt cuộc họp. Từ nội dung cuộc họp bên dưới, hãy tóm tắt các điểm chính bao gồm: (1) Các nội dung đã thảo luận, (2) Các quyết định đã đưa ra, (3) Các vấn đề kỹ thuật được đề cập. Viết ngắn gọn, rõ ràng, có đánh số."

        return buildString {
            append(QWEN_SYS_OPEN)
            append(systemPrompt)
            append('\n')
            append(QWEN_EOT)
            append('\n')
            append(QWEN_USR_OPEN)
            append(transcriptText)
            append('\n')
            append(QWEN_EOT)
            append('\n')
            append(QWEN_AST_OPEN)
        }
    }

    private fun truncateForContext(text: String): String {
        val maxChars = 3500 // ước lượng ~1500 token cho tiếng Việt, chừa margin an toàn
        if (text.length <= maxChars) return text
        // Giữ phần cuối (gần kết luận cuộc họp), cắt bớt phần đầu
        return "...(đã lược bớt phần đầu)...\n" + text.takeLast(maxChars)
    }

    fun close() {
        val handle = modelHandle
        modelHandle = 0L
        if (handle != 0L && nativeLibrariesLoaded) {
            try {
                nativeDestroyModel(handle)
            } catch (t: Throwable) {
                Log.w(TAG, "destroyModel failed", t)
            }
        }
    }

    private fun selectModelConfig(): ModelConfig? {
        if (File(QWEN_YAML_PATH).exists()) {
             return ModelConfig(QWEN_YAML_PATH)
        }
        return null
    }

    private fun buildChatPrompt(userPrompt: String): String {
        val systemPrompt =
            "You are a professional meeting interpreter. Translate faithfully and return only the translated text."

        return buildString {
            append(QWEN_SYS_OPEN)
            append(systemPrompt)
            append('\n')
            append(QWEN_EOT)
            append('\n')
            append(QWEN_USR_OPEN)
            append(userPrompt)
            append('\n')
            append(QWEN_EOT)
            append('\n')
            append(QWEN_AST_OPEN)
        }
    }

    private fun stripStopMarkers(raw: String): String {
        var text = raw
        for (marker in QWEN_STOP_MARKERS) {
            val index = text.indexOf(marker)
            if (index >= 0) {
                text = text.substring(0, index)
            }
        }
        return text.trim()
    }

    private external fun nativeInitModel(yamlConfigPath: String): Long
    private external fun nativeComputeStreaming(
        modelHandle: Long,
        inputText: String,
        callback: TokenCallback
    )
    private external fun nativeCountTokens(text: String): Int

    private external fun nativeResetModel(modelHandle: Long)
    private external fun nativeDestroyModel(modelHandle: Long)

    @Keep
    interface TokenCallback {
        @Keep
        fun onTokenReceived(token: String): Boolean
    }

    private data class ModelConfig(val path: String)

    companion object {
        private const val TAG = "NeuroPilotLlmBridge"

        private const val QWEN_YAML_PATH = "/system_ext/llm_sdk/config_qwen2.5_1.5b_instruct.yaml"

        private const val QWEN_EOT = "<|im_end|>"
        private const val QWEN_SYS_OPEN = "<|im_start|>system\n"
        private const val QWEN_USR_OPEN = "<|im_start|>user\n"
        private const val QWEN_AST_OPEN = "<|im_start|>assistant\n"

        private val QWEN_STOP_MARKERS = listOf(
            "<|im_start|",
            "<|im_end|",
            "<|endoftext|"
        )

        private val nativeLibrariesLoaded: Boolean = loadNativeLibraries()

        private fun loadNativeLibraries(): Boolean {
            val libs = listOf(
                "c++",
                "base",
                "dmabufheap",
                "cutils",
                "apu_mdw",
                "apu_mdw_batch",
                "neuron_adapter",
                "neuron_runtime",
                "common",
                "mtk_llm",
                "nn_sample"
            )

            for (lib in libs) {
                try {
                    System.loadLibrary(lib)
                    Log.d(TAG, "Loaded native library: $lib")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to load native library: $lib", t)
                    // We don't return false immediately because some might be optional or already loaded
                }
            }
            return true
        }
    }
}
