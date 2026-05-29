// SPDX-License-Identifier: MIT
package com.midtano.otp.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Checkable
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import com.midtano.otp.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Spring-driven toggle switch: 550 ms ease-in-out, the thumb
 * stretches into a capsule mid-flight and snaps back to a circle
 * at the destination.
 */
class SpringSwitch @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(ctx, attrs, defStyle), Checkable {

    /** Listener for state-change events. */
    fun interface OnCheckedChangeListener {
        fun onCheckedChanged(sw: SpringSwitch, checked: Boolean)
    }

    private var checked: Boolean = false
    private var progress: Float = 0f // 0=OFF, 1=ON
    private var thumbStretch: Float = 1f // 1=circle, >1=horizontally stretched

    private var trackWidth: Float
    private var trackHeight: Float
    private var thumbSize: Float
    private var thumbInset: Float

    private var trackColorOn: Int
    private var trackColorOff: Int
    private var thumbColorOn: Int
    private var thumbColorOff: Int

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tmpRect = RectF()
    private val argb = ArgbEvaluator()

    private var valueAnim: ValueAnimator? = null
    private var listener: OnCheckedChangeListener? = null

    init {
        isClickable = true
        isFocusable = true

        val density = resources.displayMetrics.density
        trackWidth = 48 * density
        trackHeight = 28 * density
        thumbSize = 24 * density
        thumbInset = 2 * density

        trackColorOn = ContextCompat.getColor(ctx, R.color.ss_default_track_on)
        trackColorOff = ContextCompat.getColor(ctx, R.color.ss_default_track_off)
        thumbColorOn = ContextCompat.getColor(ctx, R.color.ss_track_on)
        thumbColorOff = ContextCompat.getColor(ctx, R.color.ss_thumb_off)

        if (attrs != null) {
            ctx.withStyledAttributes(attrs, R.styleable.SpringSwitch, defStyle, 0) {
                trackWidth = getDimension(R.styleable.SpringSwitch_ssTrackWidth, trackWidth)
                trackHeight = getDimension(R.styleable.SpringSwitch_ssTrackHeight, trackHeight)
                thumbSize = getDimension(R.styleable.SpringSwitch_ssThumbSize, thumbSize)
                thumbInset = getDimension(R.styleable.SpringSwitch_ssThumbInset, thumbInset)

                trackColorOn = getColor(R.styleable.SpringSwitch_ssTrackColorOn, trackColorOn)
                trackColorOff = getColor(R.styleable.SpringSwitch_ssTrackColorOff, trackColorOff)
                thumbColorOn = getColor(R.styleable.SpringSwitch_ssThumbColorOn, thumbColorOn)
                thumbColorOff = getColor(R.styleable.SpringSwitch_ssThumbColorOff, thumbColorOff)

                checked = getBoolean(R.styleable.SpringSwitch_ssChecked, false)
            }
        }

        progress = if (checked) 1f else 0f

        setOnClickListener { toggle() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredW = (maxOf(trackWidth, thumbSize) + paddingLeft + paddingRight).toInt() + 1
        val desiredH = (maxOf(trackHeight, thumbSize) + paddingTop + paddingBottom).toInt() + 1
        val w = resolveSize(desiredW, widthMeasureSpec)
        val h = resolveSize(desiredH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f

        val trackLeft = cx - trackWidth / 2f
        val trackTop = cy - trackHeight / 2f
        val trackRight = cx + trackWidth / 2f
        val trackBottom = cy + trackHeight / 2f

        val colorT = clamp01(progress)
        val trackColor = argb.evaluate(colorT, trackColorOff, trackColorOn) as Int
        trackPaint.color = trackColor
        tmpRect.set(trackLeft, trackTop, trackRight, trackBottom)
        val cornerRadius = trackHeight / 2f
        canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, trackPaint)

        val travel = trackWidth - thumbSize - 2 * thumbInset
        val thumbCx = trackLeft + thumbInset + thumbSize / 2f + travel * progress

        thumbPaint.color = argb.evaluate(colorT, thumbColorOff, thumbColorOn) as Int

        val halfH = thumbSize / 2f
        val halfW = halfH * thumbStretch

        tmpRect.set(thumbCx - halfW, cy - halfH, thumbCx + halfW, cy + halfH)
        canvas.drawRoundRect(tmpRect, halfH, halfH, thumbPaint)
    }

    override fun performClick(): Boolean {
        val handled = super.performClick()
        playSoundEffect(SoundEffectConstants.CLICK)
        return handled
    }

    override fun isChecked(): Boolean = checked
    override fun setChecked(c: Boolean) = setChecked(c, animate = true)
    override fun toggle() = setChecked(!checked)

    fun setChecked(c: Boolean, animate: Boolean) {
        if (this.checked == c) return
        this.checked = c
        if (animate) {
            startMorph(if (c) 1f else 0f)
        } else {
            cancelAnim()
            progress = if (c) 1f else 0f
            thumbStretch = 1f
            invalidate()
        }
        listener?.onCheckedChanged(this, checked)
    }

    fun setOnCheckedChangeListener(l: OnCheckedChangeListener?) {
        this.listener = l
    }

    /** The thumb stretches into a capsule mid-flight. */
    private fun startMorph(target: Float) {
        cancelAnim()
        val start = progress
        valueAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                progress = start + (target - start) * t
                // Sinusoidal stretch: 1.0 → 1.6 → 1.0.
                thumbStretch = 1f + 0.6f * sin(t * PI).toFloat()
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    thumbStretch = 1f
                    invalidate()
                }
            })
            start()
        }
    }

    private fun cancelAnim() {
        valueAnim?.let {
            try {
                it.cancel()
            } catch (_: IllegalStateException) {
                // Animator.cancel() throws IllegalStateException when
                // the animation already ended — harmless on teardown.
            }
        }
        valueAnim = null
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
