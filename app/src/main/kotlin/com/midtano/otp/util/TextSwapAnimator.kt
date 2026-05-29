// SPDX-License-Identifier: MIT
package com.midtano.otp.util

import android.view.animation.PathInterpolator
import android.widget.TextView

/**
 * Animates a [TextView]'s text change as a slide-up swap whose total
 * length matches the [com.midtano.otp.widget.SpringSwitch] toggle
 * timing.
 *
 * Old text slides up + fades out, new text enters from below + fades
 * in. The swap is a no-op when the new text equals the current text.
 */
internal object TextSwapAnimator {

    private const val EXIT_MS = 280L
    private const val ENTER_MS = 420L

    private val EASE_OUT = PathInterpolator(0.16f, 1f, 0.30f, 1f)
    private val EASE_IN = PathInterpolator(0.45f, 0f, 0.55f, 1f)

    /** Animate [v]'s content from current to [newText]. No-op if identical. */
    fun animateTo(v: TextView?, newText: CharSequence?) {
        if (v == null) return
        val target: CharSequence = newText ?: ""
        val cur: CharSequence? = v.text
        if (cur != null && cur.toString().contentEquals(target)) return

        val h = maxOf(v.height.toFloat(), v.textSize * 1.2f)
        v.animate().cancel()
        v.animate()
            .translationY(-h * 0.5f)
            .alpha(0f)
            .setDuration(EXIT_MS)
            .setInterpolator(EASE_IN)
            .withEndAction {
                v.text = target
                v.translationY = h * 0.5f
                v.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(ENTER_MS)
                    .setInterpolator(EASE_OUT)
                    .start()
            }
            .start()
    }
}
