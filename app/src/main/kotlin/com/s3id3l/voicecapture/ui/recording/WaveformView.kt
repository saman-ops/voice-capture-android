package com.s3id3l.voicecapture.ui.recording

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEF4444.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 8f
    }

    private var amplitudes: List<Int> = emptyList()

    fun setAmplitudes(amps: List<Int>) {
        amplitudes = amps
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (amplitudes.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val count = amplitudes.size
        val barW = w / (count * 2)
        amplitudes.forEachIndexed { i, amp ->
            val x = i * (barW * 2) + barW
            val barH = (amp / 32767f * h * 0.8f).coerceAtLeast(4f)
            canvas.drawLine(x, h / 2f - barH / 2f, x, h / 2f + barH / 2f, paint)
        }
    }
}
