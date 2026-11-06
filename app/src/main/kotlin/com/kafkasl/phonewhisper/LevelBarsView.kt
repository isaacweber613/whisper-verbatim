package com.kafkasl.phonewhisper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Five white bars that rise and fall with how loud you're speaking, drawn on top of
 * the floating button while it records. Silence leaves short, gently moving bars so
 * you can still tell it's listening.
 */
class LevelBarsView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val rect = RectF()
    private val shape = floatArrayOf(0.55f, 0.85f, 1f, 0.85f, 0.55f)
    private val heights = FloatArray(shape.size) { MIN_HEIGHT }
    private var target = 0f
    private var level = 0f
    private var startTime = 0L

    /** Takes a loudness from 0 (silent) to 1 (loud). Safe to call from any thread. */
    fun setLevel(value: Float) {
        target = value.coerceIn(0f, 1f)
    }

    fun start() {
        target = 0f; level = 0f
        startTime = System.currentTimeMillis()
        heights.fill(MIN_HEIGHT)
        visibility = VISIBLE
        postInvalidateOnAnimation()
    }

    fun stop() {
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        // Rise quickly, fall slowly, so the bars follow speech without flickering.
        level += (target - level) * (if (target > level) 0.5f else 0.15f)
        val t = (System.currentTimeMillis() - startTime) / 1000f

        val n = shape.size
        val barW = width / (n * 2f - 1f) * 0.8f
        val gap = (width * 0.8f - barW * n) / (n - 1)
        var x = width * 0.1f
        for (i in 0 until n) {
            val idle = MIN_HEIGHT + 0.06f * (1 + sin(t * 4f + i * 1.3f)) // gentle motion in silence
            val goal = max(idle, level * shape[i])
            heights[i] += (goal - heights[i]) * 0.4f
            val h = min(1f, heights[i]) * height * 0.8f
            val top = (height - h) / 2f
            rect.set(x, top, x + barW, top + h)
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint)
            x += barW + gap
        }
        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }

    companion object {
        private const val MIN_HEIGHT = 0.12f
    }
}
