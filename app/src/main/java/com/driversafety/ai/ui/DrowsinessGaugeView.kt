package com.driversafety.ai.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom arc-based gauge view to display drowsiness level visually.
 * Draws a semi-circle arc from green → amber → red based on sensor value.
 */
class DrowsinessGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentValue = 0
    private var maxValue = 5000
    private var animatedSweep = 0f
    private var targetSweep = 0f

    // Paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 28f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#1C2339")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 28f
        strokeCap = Paint.Cap.ROUND
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0A0E1A")
    }

    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#607D8B")
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // Gradient shader for the arc
    private var arcGradient: SweepGradient? = null
    private val arcRect = RectF()

    // Arc spans 180° (half circle from left to right)
    private val startAngle = 180f
    private val sweepRange = 180f

    // Animator
    private val animRunnable = object : Runnable {
        override fun run() {
            if (Math.abs(animatedSweep - targetSweep) > 0.5f) {
                animatedSweep += (targetSweep - animatedSweep) * 0.15f
                invalidate()
                postDelayed(this, 16)
            } else {
                animatedSweep = targetSweep
                invalidate()
            }
        }
    }

    fun setValue(value: Int, criticalThreshold: Int = 5000) {
        currentValue = value
        maxValue = (criticalThreshold * 1.5f).toInt().coerceAtLeast(1000)
        targetSweep = (value.toFloat() / maxValue * sweepRange).coerceIn(0f, sweepRange)
        removeCallbacks(animRunnable)
        post(animRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height * 0.85f
        val radius = (min(width.toFloat(), height * 2f) / 2f - 32f)

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Setup sweep gradient (colors across the arc)
        if (arcGradient == null || arcRect.width() != radius * 2) {
            arcGradient = SweepGradient(
                cx, cy,
                intArrayOf(
                    Color.parseColor("#00D4AA"), // 0° = teal (safe)
                    Color.parseColor("#FFB300"), // 120° = amber (moderate)
                    Color.parseColor("#FF1744"), // 180° = red (critical)
                    Color.parseColor("#FF1744")  // close loop
                ),
                floatArrayOf(0f, 0.5f, 0.75f, 1f)
            )
            // Rotate gradient to match arc start angle
            val gradMatrix = Matrix()
            gradMatrix.setRotate(startAngle, cx, cy)
            arcGradient!!.setLocalMatrix(gradMatrix)
        }

        // Draw background track
        canvas.drawArc(arcRect, startAngle, sweepRange, false, trackPaint)

        // Draw progress arc
        progressPaint.shader = arcGradient
        canvas.drawArc(arcRect, startAngle, animatedSweep, false, progressPaint)

        // Draw needle
        val needleAngle = Math.toRadians((startAngle + animatedSweep).toDouble())
        val needleLength = radius - 10f
        val nx = (cx + needleLength * cos(needleAngle)).toFloat()
        val ny = (cy + needleLength * sin(needleAngle)).toFloat()

        val needlePath = Path().apply {
            moveTo(cx, cy)
            lineTo(nx - 3f, ny - 3f)
            lineTo(nx + 3f, ny + 3f)
            close()
        }
        canvas.drawPath(needlePath, needlePaint)

        // Center dot
        canvas.drawCircle(cx, cy, 10f, centerPaint)
        canvas.drawCircle(cx, cy, 6f, needlePaint)

        // Min/Max labels
        valueTextPaint.textSize = 20f
        canvas.drawText("0", cx - radius + 10f, cy + 24f, valueTextPaint)
        canvas.drawText("MAX", cx + radius - 24f, cy + 24f, valueTextPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(animRunnable)
    }
}
