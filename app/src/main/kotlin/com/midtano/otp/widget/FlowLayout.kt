// SPDX-License-Identifier: MIT
package com.midtano.otp.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.view.isGone

/**
 * Lightweight flow container: lays children out left-to-right and
 * wraps to the next row when the parent's width is exceeded.
 *
 * Used by the trigger-word editor in `SettingsActivity` so an
 * arbitrary number of keyword chips can sit comfortably inside the
 * card without horizontal scrolling. Intentionally minimal — no
 * per-child margins, no gravity, no RTL handling — because the only
 * consumer is a single chip row that uses uniform margins set on
 * each child.
 */
class FlowLayout @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(ctx, attrs) {

    /** Default vertical spacing applied between wrapped rows (px). */
    private val lineSpacingPx: Int =
        (6 * ctx.resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val maxWidth = if (widthMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else widthSize

        val padL = paddingLeft
        val padR = paddingRight
        val padT = paddingTop
        val padB = paddingBottom
        val innerWidth = maxOf(0, maxWidth - padL - padR)

        var rowWidth = 0
        var rowHeight = 0
        var totalHeight = 0
        var maxRowWidth = 0

        val childWidthSpec = MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.AT_MOST)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.isGone) continue
            c.measure(childWidthSpec, childHeightSpec)
            val lp = c.layoutParams as MarginLayoutParams
            val cw = c.measuredWidth + lp.leftMargin + lp.rightMargin
            val ch = c.measuredHeight + lp.topMargin + lp.bottomMargin

            if (rowWidth + cw > innerWidth && rowWidth > 0) {
                maxRowWidth = maxOf(maxRowWidth, rowWidth)
                totalHeight += rowHeight + lineSpacingPx
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth += cw
            rowHeight = maxOf(rowHeight, ch)
        }
        maxRowWidth = maxOf(maxRowWidth, rowWidth)
        totalHeight += rowHeight

        val resolvedWidth = if (widthMode == MeasureSpec.EXACTLY) {
            widthSize
        } else {
            minOf(maxRowWidth + padL + padR, maxWidth)
        }
        val resolvedHeight = totalHeight + padT + padB
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val padL = paddingLeft
        val padR = paddingRight
        val padT = paddingTop
        val innerWidth = maxOf(0, r - l - padL - padR)

        var x = padL
        var y = padT
        var rowHeight = 0

        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.isGone) continue
            val lp = c.layoutParams as MarginLayoutParams
            val cw = c.measuredWidth + lp.leftMargin + lp.rightMargin
            val ch = c.measuredHeight + lp.topMargin + lp.bottomMargin

            if (x - padL + cw > innerWidth && x > padL) {
                x = padL
                y += rowHeight + lineSpacingPx
                rowHeight = 0
            }
            val childLeft = x + lp.leftMargin
            val childTop = y + lp.topMargin
            c.layout(
                childLeft,
                childTop,
                childLeft + c.measuredWidth,
                childTop + c.measuredHeight,
            )
            x += cw
            rowHeight = maxOf(rowHeight, ch)
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(p: LayoutParams): LayoutParams = MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams
}
