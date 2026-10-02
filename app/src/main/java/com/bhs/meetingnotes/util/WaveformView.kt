package com.bhs.meetingnotes.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.parseColor("#EF4444")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var currentLevel: Int = 20
    private val barCount = 15
    private val barHeights = FloatArray(barCount) { 15f }

    fun updateAudioLevel(normalizedLevel: Int) {
        currentLevel = normalizedLevel.coerceIn(5, 100)
        val baseHeight = (height * (currentLevel / 100f)).coerceAtLeast(12f)

        for (i in 0 until barCount) {
            val randomFactor = 0.4f + Random.nextFloat() * 0.6f
            barHeights[i] = (baseHeight * randomFactor).coerceAtMost(height.toFloat() * 0.9f)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val barWidth = w / (barCount * 2)
        val space = barWidth

        var startX = (w - (barCount * barWidth + (barCount - 1) * space)) / 2f

        for (i in 0 until barCount) {
            val barH = barHeights[i]
            val top = (h - barH) / 2f
            val bottom = top + barH
            canvas.drawRoundRect(
                startX,
                top,
                startX + barWidth,
                bottom,
                barWidth / 2f,
                barWidth / 2f,
                paint
            )
            startX += barWidth + space
        }
    }
}