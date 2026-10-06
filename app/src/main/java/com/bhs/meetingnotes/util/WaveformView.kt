package com.bhs.meetingnotes.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.mediatek.neuropilot.jnidemo.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Thanh sóng âm thanh trực quan cao cấp (Fluid Studio Audio Wave Visualizer)
 * - 42 thanh âm thanh bo tròn mềm mại chuẩn Studio (Capsule Bars)
 * - Đa phổ màu chuyển động: Indigo -> Royal Blue -> Cyber Cyan -> Emerald
 * - Hiệu ứng lan tỏa sóng hữu cơ (Organic Gaussian Spread) khi phát hiện giọng nói
 * - Nội suy vật lý 60fps (Fast Attack 0.38 / Gentle Decay 0.16)
 * - Sóng thở âm học kép tự nhiên (Dual-Harmonic Acoustic Breathing)
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val BARS = 42
        private const val MIN_BAR_HEIGHT_RATIO = 0.10f
    }

    private val currentLevels = FloatArray(BARS) { MIN_BAR_HEIGHT_RATIO }
    private val targetLevels = FloatArray(BARS) { MIN_BAR_HEIGHT_RATIO }

    private var isRecording = true
    private var isPaused = false
    private var phase = 0f

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var cachedWidth = 0
    private var cachedHeight = 0
    private var gradientShader: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedWidth = w
        cachedHeight = h
        if (w > 0 && h > 0) {
            // Gradient dải màu cao cấp hiện đại
            gradientShader = LinearGradient(
                0f, 0f, w.toFloat(), 0f,
                intArrayOf(
                    ContextCompat.getColor(context, R.color.audio_wave_indigo),
                    ContextCompat.getColor(context, R.color.audio_wave_blue),
                    ContextCompat.getColor(context, R.color.audio_wave_cyan),
                    ContextCompat.getColor(context, R.color.audio_wave_green)
                ),
                floatArrayOf(0f, 0.32f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            barPaint.shader = gradientShader
        }
    }

    /** Cập nhật trạng thái ghi âm */
    fun setRecordingState(recording: Boolean, paused: Boolean) {
        isRecording = recording
        isPaused = paused
        if (!recording) {
            reset()
        }
        postInvalidateOnAnimation()
    }

    /** Đưa các thanh về mức tối thiểu */
    fun reset() {
        for (i in 0 until BARS) {
            targetLevels[i] = MIN_BAR_HEIGHT_RATIO
        }
        postInvalidateOnAnimation()
    }

    /**
     * Đẩy mức âm thanh (0.0 .. 1.0) vào dải sóng với thuật toán lan tỏa hữu cơ.
     */
    fun pushLevel(level: Float) {
        val coerced = level.coerceIn(MIN_BAR_HEIGHT_RATIO, 1.0f)
        // Dịch chuyển các thanh sang trái
        System.arraycopy(targetLevels, 1, targetLevels, 0, BARS - 1)
        targetLevels[BARS - 1] = coerced

        // Lan tỏa Gaussian nhẹ sang các thanh liền kề để tạo gợn sóng mượt mà
        if (coerced > 0.25f) {
            val idx = BARS - 1
            if (idx - 1 >= 0) targetLevels[idx - 1] = max(targetLevels[idx - 1], coerced * 0.82f)
            if (idx - 2 >= 0) targetLevels[idx - 2] = max(targetLevels[idx - 2], coerced * 0.62f)
            if (idx - 3 >= 0) targetLevels[idx - 3] = max(targetLevels[idx - 3], coerced * 0.40f)
        }

        postInvalidateOnAnimation()
    }

    /** Tương thích với API gọi cấp độ số nguyên (5 .. 100) */
    fun updateAudioLevel(normalizedLevel: Int) {
        val fl = (normalizedLevel.coerceIn(5, 100) / 100f)
        pushLevel(fl)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val slot = w / BARS.toFloat()
        val barWidth = slot * 0.56f
        val radius = barWidth / 2f
        val centerY = h / 2f
        val minHalfHeight = radius * 1.4f

        // Cập nhật pha chuyển động sóng thở âm học
        if (isRecording && !isPaused) {
            phase += 0.07f
            if (phase > 2000f) phase = 0f
            barPaint.alpha = 255
        } else if (isRecording && isPaused) {
            barPaint.alpha = 140
        } else {
            barPaint.alpha = 90
        }

        var hasPendingAnimation = false

        for (i in 0 until BARS) {
            // Sóng thở kép (Dual-harmonic acoustic breathing) khi không có âm lớn
            val breathingOffset = if (isRecording && !isPaused) {
                val wave1 = sin(phase + i * 0.22f) * 0.06f
                val wave2 = sin(phase * 0.6f + i * 0.14f) * 0.03f
                (wave1 + wave2).toFloat()
            } else {
                0f
            }

            val target = (targetLevels[i] + breathingOffset).coerceIn(MIN_BAR_HEIGHT_RATIO, 1.0f)
            val diff = target - currentLevels[i]

            // Vật lý Attack / Decay: vút lên nhanh, hạ xuống chậm rãi mượt mà
            if (abs(diff) > 0.003f) {
                val factor = if (diff > 0) 0.38f else 0.16f
                currentLevels[i] += diff * factor
                hasPendingAnimation = true
            } else {
                currentLevels[i] = target
            }

            val barHeight = currentLevels[i] * h
            val halfHeight = max(minHalfHeight, barHeight / 2f)
            val left = i * slot + (slot - barWidth) / 2f

            canvas.drawRoundRect(
                left,
                centerY - halfHeight,
                left + barWidth,
                centerY + halfHeight,
                radius,
                radius,
                barPaint
            )
        }

        // Tự động yêu cầu frame tiếp theo để duy trì 60fps mượt mà
        if (hasPendingAnimation || (isRecording && !isPaused)) {
            postInvalidateOnAnimation()
        }
    }
}
