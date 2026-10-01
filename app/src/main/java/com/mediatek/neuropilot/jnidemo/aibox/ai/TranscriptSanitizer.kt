package com.mediatek.neuropilot.jnidemo.aibox.ai

import java.text.Normalizer
import java.util.Locale

object TranscriptSanitizer {
    private val exactNoise = setOf(
        "andre",
        "andra",
        "andrea",
        "andreas",
        "andren",
        "andrain",
        "andean",
        "and run run",
        "Andro",
        "And"
    )

    // Chỉ chứa số/dấu câu/khoảng trắng, không có chữ cái nào -> chắc chắn là rác (vd: "1.", "...", "12")
    private val onlyDigitsOrPunctuation = Regex("^[\\d\\s.,!?;:\\-]+$")

    // Các câu hallucination "kinh điển" của Whisper khi audio gần như không có giọng nói
    private val knownHallucinationPhrases = listOf(
        Regex("(?i)^thank(s)? (you )?for watching.*"),
        Regex("(?i)^phụ đề (được thực hiện|do).*"),
        Regex("(?i)^subscribe.*"),
        Regex("(?i)^\\[.*\\]$") // vd: "[BLANK_AUDIO]", "[music]"
    )

    fun cleanOrNull(rawText: String): String? {
        val cleaned = rawText
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isEmpty()) return null
        if (cleaned.startsWith("STT error:", ignoreCase = true)) return null

        // Chặn segment chỉ toàn số/dấu câu (vd: "1.", "...")
        if (onlyDigitsOrPunctuation.matches(cleaned)) return null

        // Chặn các câu hallucination quen thuộc
        if (knownHallucinationPhrases.any { it.matches(cleaned) }) return null

        val normalized = normalizeForNoiseCheck(cleaned)
        if (normalized.isEmpty()) return null
        if (normalized in exactNoise) return null
        if (normalized.startsWith("andr") && normalized.length <= 18) return null

        // Chặn segment quá ngắn (dưới 2 ký tự chữ thật sự) -> khả năng cao là nhiễu
        val letterCount = normalized.count { it.isLetter() }
        if (letterCount < 2) return null

        return cleaned
    }

    private fun normalizeForNoiseCheck(text: String): String {
        val noAccent = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        return noAccent
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
