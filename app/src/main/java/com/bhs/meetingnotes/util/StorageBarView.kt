package com.bhs.meetingnotes.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.mediatek.neuropilot.jnidemo.R

/**
 * Thanh lưu trữ ngang chia 4 phần trực quan: Models (Tím), Audio (Xanh dương), Text (Cam), Trống (Xám).
 */
class StorageBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val clip = Path()
    private val bounds = RectF()
    private val colors = IntArray(4)
    private val fractions = FloatArray(4)

    init {
        colors[0] = ContextCompat.getColor(context, R.color.storage_models)
        colors[1] = ContextCompat.getColor(context, R.color.primary)
        colors[2] = ContextCompat.getColor(context, R.color.warn_orange)
        colors[3] = ContextCompat.getColor(context, R.color.chip_grey)
        fractions[3] = 1f
    }

    fun setBreakdown(b: StorageBreakdown) {
        fractions[0] = b.modelsFraction()
        fractions[1] = b.audioFraction()
        fractions[2] = b.textFraction()
        fractions[3] = if (b.total == 0L) 1f else b.freeFraction()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.reset()
        clip.addRoundRect(bounds, h / 2f, h / 2f, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.clipPath(clip)
        var left = 0f
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in fractions.indices) {
            val right = left + fractions[i] * w
            paint.color = colors[i]
            canvas.drawRect(left, 0f, right, h, paint)
            left = right
        }
        canvas.restore()
    }
}
