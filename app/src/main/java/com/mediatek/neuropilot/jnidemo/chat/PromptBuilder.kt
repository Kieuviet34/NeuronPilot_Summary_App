package com.mediatek.neuropilot.jnidemo.chat

object PromptBuilder {
    private const val LLAMA_EOT = "<|eot_id|>"
    private const val LLAMA_SYS_OPEN = "<|start_header_id|>system<|end_header_id|>\n\n"
    private const val LLAMA_USR_OPEN = "<|start_header_id|>user<|end_header_id|>\n\n"
    private const val LLAMA_AST_OPEN = "<|start_header_id|>assistant<|end_header_id|>\n\n"
    private const val LLAMA_BOS = "<|begin_of_text|>\n"

    private const val QWEN_EOT = "<|im_end|>"
    private const val QWEN_SYS_OPEN = "<|im_start|>system\n"
    private const val QWEN_USR_OPEN = "<|im_start|>user\n"
    private const val QWEN_AST_OPEN = "<|im_start|>assistant\n"

    enum class ModelType { LLAMA, QWEN }

    fun buildSystemPrompt(modelType: ModelType): String {
        val systemContent = "You are a helpful assistant. Answer briefly and clearly."
        return if (modelType == ModelType.QWEN) {
            "$QWEN_SYS_OPEN$systemContent\n$QWEN_EOT\n"
        } else {
            "$LLAMA_SYS_OPEN$systemContent\n$LLAMA_EOT\n"
        }
    }

    fun buildUserTurn(modelType: ModelType, userText: String): String {
        return if (modelType == ModelType.QWEN) {
            "$QWEN_USR_OPEN$userText\n$QWEN_EOT\n$QWEN_AST_OPEN"
        } else {
            "$LLAMA_USR_OPEN$userText$LLAMA_EOT\n$LLAMA_AST_OPEN"
        }
    }

    fun getEotToken(modelType: ModelType): String {
        return if (modelType == ModelType.QWEN) QWEN_EOT else LLAMA_EOT
    }

    fun buildFullPrompt(modelType: ModelType, history: String): String {
        val sysPrompt = buildSystemPrompt(modelType)
        return if (modelType == ModelType.QWEN) {
            sysPrompt + history
        } else {
            LLAMA_BOS + sysPrompt + history
        }
    }

    /** Dead code, giữ để tham khảo */
    fun buildSummarizePrompt(modelType: ModelType, conversationText: String): String {
        val instruction = "Tóm tắt ngắn gọn nội dung hội thoại sau bằng tiếng Việt, nêu các ý chính:\n\n$conversationText"
        return if (modelType == ModelType.QWEN) {
            "${QWEN_SYS_OPEN}Bạn là trợ lý tóm tắt hội thoại.\n$QWEN_EOT\n" +
                    "$QWEN_USR_OPEN$instruction\n$QWEN_EOT\n$QWEN_AST_OPEN"
        } else {
            LLAMA_BOS +
                    "${LLAMA_SYS_OPEN}Bạn là trợ lý tóm tắt hội thoại.\n$LLAMA_EOT\n" +
                    "$LLAMA_USR_OPEN$instruction$LLAMA_EOT\n$LLAMA_AST_OPEN"
        }
    }

    fun buildPairPhrasePrompt(modelType: ModelType, userText: String, aiText: String): String {
        val u = truncateForBudget(userText, 150)
        val a = truncateForBudget(aiText, 150)

        val instruction = """
            Viết lại nội dung trao đổi sau thành MỘT CÂU NGẮN GỌN (8-15 từ), đầy đủ ý, đọc tự nhiên như văn nói. TUYỆT ĐỐO KHÔNG dùng các từ 'người dùng', 'AI', 'hỏi rằng', 'trả lời rằng'. Chỉ nêu đúng chủ đề và nội dung chính, bằng tiếng Việt.
            
            Ví dụ:
            Câu hỏi: Thủ đô của Pháp là gì
            Câu trả lời: Thủ đô của Pháp là Paris
            Câu tóm tắt: Thủ đô của Pháp là Paris
            
            Câu hỏi: $u
            Câu trả lời: $a
            Câu tóm tắt:
        """.trimIndent()

        val sysPrompt = "Bạn viết tóm tắt tự nhiên, không dùng từ 'người dùng' hay 'AI'."
        return if (modelType == ModelType.QWEN) {
            "$QWEN_SYS_OPEN$sysPrompt\n$QWEN_EOT\n" +
                    "$QWEN_USR_OPEN$instruction\n$QWEN_EOT\n$QWEN_AST_OPEN"
        } else {
            LLAMA_BOS + "$LLAMA_SYS_OPEN$sysPrompt\n$LLAMA_EOT\n" +
                    "$LLAMA_USR_OPEN$instruction$LLAMA_EOT\n$LLAMA_AST_OPEN"
        }
    }

    fun buildFinalCompressPrompt(modelType: ModelType, summary: String): String {
        val systemPrompt = "Bạn là trợ lý tóm tắt chuyên nghiệp."
        val instruction = """
            Dưới đây là một chuỗi các ý tóm tắt:
            $summary
            
            Hãy viết lại thành một đoạn văn ngắn DUY NHẤT (liền mạch, nối bằng dấu chấm), loại bỏ các ý lặp lại, giữ lại thông tin quan trọng nhất bằng tiếng Việt. Không dùng danh sách gạch đầu dòng. Chỉ trả về đoạn văn tóm tắt, không giải thích thêm.
        """.trimIndent()

        return if (modelType == ModelType.QWEN) {
            "$QWEN_SYS_OPEN$systemPrompt\n$QWEN_EOT\n" +
                    "$QWEN_USR_OPEN$instruction\n$QWEN_EOT\n$QWEN_AST_OPEN"
        } else {
            LLAMA_BOS +
                    "$LLAMA_SYS_OPEN$systemPrompt\n$LLAMA_EOT\n" +
                    "$LLAMA_USR_OPEN$instruction$LLAMA_EOT\n$LLAMA_AST_OPEN"
        }
    }

    private fun truncateForBudget(text: String?, maxChars: Int): String {
        if (text == null) return ""
        return if (text.length <= maxChars) text else text.substring(0, maxChars) + "..."
    }
}
