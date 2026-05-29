// SPDX-License-Identifier: MIT
package com.midtano.otp.widget

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.SoundEffectConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Checkable
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withScale
import com.midtano.otp.R

/**
 * Animated checkbox: an outlined circle with a centred dot that pops
 * in with an overshoot scale-bounce when toggled on.
 */
class AnimatedCheckbox @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(ctx, attrs, defStyle), Checkable {

    /** Listener for state-change events. */
    fun interface OnCheckedChangeListener {
        fun onCheckedChanged(cb: AnimatedCheckbox, checked: Boolean)
    }

    private var checked: Boolean = false
    private var progress: Float = 0f // 0 = off, 1 = on (drives colour blend)
    private var drawProgress: Float = 0f // 0..1 dot scale-in
    private var scaleAnim: Float = 1f // outer overshoot bounce

    private var boxWidth: Float
    private var boxHeight: Float
    private var borderWidth: Float

    private var colorOn: Int
    private var colorOff: Int
    private var borderOn: Int
    private var borderOff: Int
    private var checkColor: Int

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tmpRect = RectF()
    private val argb = ArgbEvaluator()

    private var anim: ValueAnimator? = null
    private var listener: OnCheckedChangeListener? = null

    init {
        isClickable = true
        isFocusable = true

        val density = resources.displayMetrics.density
        boxWidth = 24 * density
        boxHeight = 24 * density
        borderWidth = 2 * density

        colorOn = ContextCompat.getColor(ctx, R.color.ac_bg_on)
        colorOff = ContextCompat.getColor(ctx, R.color.ac_bg_off)
        borderOn = ContextCompat.getColor(ctx, R.color.ac_border_on)
        borderOff = ContextCompat.getColor(ctx, R.color.ac_border_off)
        checkColor = ContextCompat.getColor(ctx, R.color.ac_check)

        if (attrs != null) {
            ctx.withStyledAttributes(attrs, R.styleable.AnimatedCheckbox, defStyle, 0) {
                boxWidth = getDimension(R.styleable.AnimatedCheckbox_acBoxWidth, boxWidth)
                boxHeight = getDimension(R.styleable.AnimatedCheckbox_acBoxHeight, boxHeight)
                borderWidth = getDimension(R.styleable.AnimatedCheckbox_acBorderWidth, borderWidth)

                colorOn = getColor(R.styleable.AnimatedCheckbox_acColorOn, colorOn)
                colorOff = getColor(R.styleable.AnimatedCheckbox_acColorOff, colorOff)
                borderOn = getColor(R.styleable.AnimatedCheckbox_acBorderOn, borderOn)
                borderOff = getColor(R.styleable.AnimatedCheckbox_acBorderOff, borderOff)
                checkColor = getColor(R.styleable.AnimatedCheckbox_acCheckColor, checkColor)

                checked = getBoolean(R.styleable.AnimatedCheckbox_acChecked, false)
            }
        }

        progress = if (checked) 1f else 0f
        drawProgress = progress

        setOnClickListener { toggle() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredW = (boxWidth + paddingLeft + paddingRight + 4).toInt() + 1
        val desiredH = (boxHeight + paddingTop + paddingBottom + 4).toInt() + 1
        val w = resolveSize(desiredW, widthMeasureSpec)
        val h = resolveSize(desiredH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.withScale(scaleAnim, scaleAnim, width / 2f, height / 2f) {
            val cx = width / 2f
            val cy = height / 2f
            val halfW = boxWidth / 2f
            val halfH = boxHeight / 2f
            tmpRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)

            val colorT = clamp01(progress)
            boxPaint.color = argb.evaluate(colorT, colorOff, colorOn) as Int
            borderPaint.color = argb.evaluate(colorT, borderOff, borderOn) as Int
            borderPaint.strokeWidth = borderWidth

            val r = minOf(halfW, halfH) - borderWidth / 2f
            drawCircle(cx, cy, r, boxPaint)
            if (borderWidth > 0f) drawCircle(cx, cy, r, borderPaint)

            if (drawProgress > 0.05f) {
                dotPaint.color = checkColor
                val dotR = minOf(halfW, halfH) * 0.30f * drawProgress
                drawCircle(cx, cy, dotR, dotPaint)
            }
        }
    }

    override fun isChecked(): Boolean = checked
    override fun setChecked(c: Boolean) = setChecked(c, animate = true)
    override fun toggle() = setChecked(!checked)

    override fun performClick(): Boolean {
        val handled = super.performClick()
        playSoundEffect(SoundEffectConstants.CLICK)
        return handled
    }

    fun setChecked(c: Boolean, animate: Boolean) {
        if (this.checked == c) return
        this.checked = c
        if (animate) {
            startAnim(c)
        } else {
            cancelAnim()
            progress = if (c) 1f else 0f
            drawProgress = progress
            scaleAnim = 1f
            invalidate()
        }
        listener?.onCheckedChanged(this, checked)
    }

    fun setOnCheckedChangeListener(l: OnCheckedChangeListener?) {
        this.listener = l
    }

    /**
     * Run the colour blend and dot scale-in. The
     * [OvershootInterpolator] already bakes the bounce into the
     * progress, so [scaleAnim] stays at 1 to avoid double-multiplying.
     */
    private fun startAnim(toOn: Boolean) {
        cancelAnim()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = OvershootInterpolator(3f)
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                progress = if (toOn) t else (1f - t)
                drawProgress = progress
                scaleAnim = 1f
                invalidate()
            }
            start()
        }
    }

    private fun cancelAnim() {
        anim?.let {
            try {
                it.cancel()
            } catch (_: IllegalStateException) {
                // Animator.cancel() throws IllegalStateException when
                // the animation already ended — harmless on teardown.
            }
        }
        anim = null
    }

    override fun onDetachedFromWindow() {
        cancelAnim()
        super.onDetachedFromWindow()
    }

    override fun onSaveInstanceState(): Parcelable =
        SavedState(super.onSaveInstanceState()).also { it.checked = checked }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            setChecked(state.checked, animate = false)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    /** Persists the [checked] flag across configuration changes / process death. */
    private class SavedState : BaseSavedState {
        var checked: Boolean = false

        constructor(superState: Parcelable?) : super(superState)
        constructor(parcel: Parcel) : super(parcel) { checked = parcel.readInt() == 1 }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(if (checked) 1 else 0)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(parcel: Parcel): SavedState = SavedState(parcel)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    companion object {
        private fun clamp01(v: Float): Float = when {
            v < 0f -> 0f
            v > 1f -> 1f
            else -> v
        }
    }
}
