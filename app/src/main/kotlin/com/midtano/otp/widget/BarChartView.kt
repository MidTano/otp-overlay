// SPDX-License-Identifier: MIT
package com.midtano.otp.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.midtano.otp.R

/**
 * Tiny dependency-free vertical bar chart for the stats dashboard.
 * Renders [data] as evenly-spaced rounded bars in monochrome with a
 * tiny x-axis label per bar.
 */
class BarChartView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    style: Int = 0,
) : View(ctx, attrs, style) {

    private var data: IntArray = intArrayOf(0)
    private var labels: Array<String> = arrayOf("")

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val density: Float = ctx.resources.displayMetrics.density

    private val colorBarFilled: Int = ContextCompat.getColor(ctx, R.color.bar_filled)
    private val colorBarEmpty: Int = ContextCompat.getColor(ctx, R.color.bar_empty)

    init {
        labelPaint.color = ContextCompat.getColor(ctx, R.color.bar_label)
        labelPaint.textSize = sp(10f)
        labelPaint.textAlign = Paint.Align.CENTER
        countPaint.color = ContextCompat.getColor(ctx, R.color.bar_count)
        countPaint.textSize = sp(10f)
        countPaint.textAlign = Paint.Align.CENTER
    }

    /** Set the bar values and per-bar labels (e.g. "Mon", "Tue", …). */
    fun setData(data: IntArray?, labels: Array<String>?) {
        this.data = if (data != null && data.isNotEmpty()) data else intArrayOf(0)
        this.labels = if (labels != null && labels.isNotEmpty()) labels else arrayOf("")
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        val n = data.size
        if (n <= 0) return

        var max = 1
        for (v in data) if (v > max) max = v

        val labelH = sp(14f)
        val countH = sp(14f)
        val chartH = h - labelH - countH - dp(4f)
        val chartTop = countH

        var barW = (w - dp(4f) * (n + 1)) / n
        if (barW < dp(2f)) barW = dp(2f)
        val spacing = dp(4f)

        for (i in 0 until n) {
            val left = spacing + i * (barW + spacing)
            val right = left + barW
            val fraction = data[i] / max.toFloat()
            var top = chartTop + chartH * (1f - fraction)
            val bottom = chartTop + chartH
            // Empty bars get a 1 dp baseline so the bin always reads
            // as present rather than as a gap.
            if (data[i] == 0) top = bottom - dp(1f)
            rect.set(left, top, right, bottom)
            barPaint.shader = null
            barPaint.color = if (data[i] > 0) colorBarFilled else colorBarEmpty
            val radius = minOf(dp(4f), barW / 2f)
            canvas.drawRoundRect(rect, radius, radius, barPaint)

            // Count label above the bar — only when the bin actually
            // has data, so empty bins don't read as "0 0 0 …".
            if (data[i] > 0) {
                canvas.drawText(
                    data[i].toString(),
                    (left + right) / 2f,
                    chartTop - dp(3f),
                    countPaint,
                )
            }

            val l = if (i < labels.size) labels[i] else ""
            canvas.drawText(
                l,
                (left + right) / 2f,
                chartTop + chartH + labelH * 0.85f,
                labelPaint,
            )
        }
    }

    private fun dp(v: Float): Float = v * density
    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
