package com.bhs.meetingnotes.util

/**
 * Ước tính thời gian xử lý AI theo tổng thời lượng audio, hiệu chỉnh theo mục 3.1
 * của tài liệu kỹ thuật BA v2:
 * - ASR (PhoWhisper): ≈ 2/15 thời lượng
 * - Sửa lỗi ngữ pháp/thuật ngữ: ≈ 1/10 thời lượng
 * - Tóm tắt + Actions (Qwen2.5 3B): ≈ 1 phút + 1/25 thời lượng (tối thiểu 2 phút)
 */
object ProcessingEstimator {

    private const val MIN_MS = 60_000L

    data class Estimate(
        val asrMin: Int,
        val fixMin: Int,
        val summaryMin: Int,
        val totalMin: Int
    )

    fun estimate(totalDurationMs: Long): Estimate {
        if (totalDurationMs <= 0) {
            return Estimate(0, 0, 0, 0)
        }
        val asr = ceilDiv(totalDurationMs * 2L, 15L * MIN_MS).toInt().coerceAtLeast(1)
        val fix = ceilDiv(totalDurationMs, 10L * MIN_MS).toInt().coerceAtLeast(1)
        val summary = Math.max(2L, 1L + ceilDiv(totalDurationMs, 25L * MIN_MS)).toInt()
        val total = asr + fix + summary
        return Estimate(asr, fix, summary, total)
    }

    private fun ceilDiv(a: Long, b: Long): Long {
        return (a + b - 1L) / b
    }
}
