package com.bhs.meetingnotes.util

/**
 * Phân bổ dung lượng lưu trữ (models, audio, text, free) để vẽ thanh lưu trữ trực quan.
 */
data class StorageBreakdown(
    val models: Long,
    val audio: Long,
    val text: Long,
    val free: Long
) {
    val total: Long = maxOf(0L, models) + maxOf(0L, audio) + maxOf(0L, text) + maxOf(0L, free)

    fun modelsFraction(): Float = fraction(models)
    fun audioFraction(): Float = fraction(audio)
    fun textFraction(): Float = fraction(text)
    fun freeFraction(): Float = if (total == 0L) 1f else fraction(free)

    private fun fraction(part: Long): Float {
        return if (total == 0L) 0f else maxOf(0L, part).toFloat() / total.toFloat()
    }

    companion object {
        fun of(models: Long, audio: Long, text: Long, free: Long): StorageBreakdown {
            return StorageBreakdown(
                maxOf(0L, models),
                maxOf(0L, audio),
                maxOf(0L, text),
                maxOf(0L, free)
            )
        }
    }
}
