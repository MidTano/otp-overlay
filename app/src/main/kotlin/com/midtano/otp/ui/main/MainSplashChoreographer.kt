// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.midtano.otp.R
import com.midtano.otp.overlay.SuckInOverlayView
import com.midtano.otp.ui.splash.SplashAnimTimings
import kotlin.random.Random

/**
 * Cold-launch greeting choreography for [MainActivity].
 *
 * Plays a Lottie animation layered above the main content, then
 * shrinks it together with a randomly-picked greeting message.
 * Choreography mirrors the onboarding intro and the splash crash
 * screen so the three Lotties feel like a single design system.
 *
 * Stateless from the caller's point of view — `MainActivity`
 * builds an instance, calls [start], then forgets about it. All
 * `postDelayed` callbacks are scheduled on the supplied [handler]
 * so [MainActivity.onDestroy] can yank everything in flight by
 * calling `handler.removeCallbacksAndMessages(null)`.
 *
 * Suppression: when [skipAnim] is `true` (a pre-warm cold launch
 * after the crash dialog or after onboarding), the splash root is
 * hidden immediately and no animation runs. The rule is one
 * greeting per cold launch.
 */
internal class MainSplashChoreographer(
    private val activity: Activity,
    private val handler: Handler,
) {

    /**
     * Run the greeting choreography against the views inside
     * [activity]. Safe to call multiple times — only the first
     * invocation does anything, subsequent calls are no-ops.
     *
     * @param skipAnim when `true` the splash root is hidden
     *                 immediately, no Lottie plays.
     */
    fun start(skipAnim: Boolean) {
        val splashRoot = activity.findViewById<View?>(R.id.main_splash_root) ?: return

        if (skipAnim) {
            splashRoot.visibility = View.GONE
            return
        }

        val lottie = activity.findViewById<LottieAnimationView?>(R.id.main_lottie_splash) ?: run {
            splashRoot.visibility = View.GONE
            return
        }
        val suckIn = activity.findViewById<SuckInOverlayView?>(R.id.main_suck_in_overlay)
        val tvMessage = activity.findViewById<TextView?>(R.id.tv_main_splash_message)

        // Pick a random greeting from the pool. The user has been
        // here before (this only runs after onboarding), so any of
        // the 12 lines is fair game.
        tvMessage?.setText(MESSAGES_ALL[Random.nextInt(MESSAGES_ALL.size)])

        lottie.setAnimation(R.raw.lottie_splash)
        lottie.repeatCount = 0

        lottie.addLottieOnCompositionLoadedListener { composition ->
            // Recolour every layer to white so the splash matches
            // the dark monochrome design language used in
            // SplashActivity and OnboardingActivity.
            lottie.addValueCallback(KeyPath("**"), LottieProperty.COLOR, LottieValueCallback(Color.WHITE))
            lottie.addValueCallback(
                KeyPath("**"),
                LottieProperty.STROKE_COLOR,
                LottieValueCallback(Color.WHITE),
            )

            val timings = SplashAnimTimings.forDuration(composition.duration.toLong())

            lottie.progress = 0f
            lottie.playAnimation()

            // Reset any callbacks queued before the composition
            // loaded; we re-post them with the proper timings.
            handler.removeCallbacksAndMessages(null)

            handler.postDelayed({ fadeInMessage(tvMessage, timings.textFadeDurationMs) }, timings.textAppearDelayMs)
            handler.postDelayed({ animateSuckIn(suckIn, timings.suckInDurationMs) }, timings.suckInStartMs)
            handler.postDelayed({ collapse(lottie, tvMessage, timings.collapseDurationMs) }, timings.collapseStartMs)

            // Failsafe: if the Lottie animator never fires
            // onAnimationEnd (rare but observed under GPU
            // throttling), hide the overlay anyway.
            handler.postDelayed({ splashRoot.visibility = View.GONE }, timings.fallbackMs)
        }

        lottie.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                splashRoot.visibility = View.GONE
            }
        })
    }

    private fun fadeInMessage(tvMessage: TextView?, durationMs: Long) {
        if (tvMessage == null) return
        tvMessage.visibility = View.VISIBLE
        tvMessage.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    /** Animate the dark backdrop sucking toward the centre. */
    private fun animateSuckIn(suckIn: SuckInOverlayView?, durationMs: Long) {
        if (suckIn == null) return
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = PathInterpolator(0.4f, 0f, 0.6f, 1f)
            addUpdateListener { suckIn.setProgress(it.animatedValue as Float) }
            start()
        }
    }

    /**
     * Collapse the Lottie and the greeting [TextView] toward the
     * centre on the same easing curve so they vanish as a single
     * composite element. The text additionally translates upward
     * by its own height plus a small gap so its visual centre
     * trails the Lottie's collapse rather than collapsing in
     * place.
     */
    private fun collapse(lottie: LottieAnimationView?, message: TextView?, durationMs: Long) {
        val ease = PathInterpolator(0.4f, 0f, 1f, 1f)
        lottie?.animate()
            ?.scaleX(0f)
            ?.scaleY(0f)
            ?.setDuration(durationMs)
            ?.setInterpolator(ease)
            ?.start()

        if (message != null && message.isVisible) {
            // Pivot the scale at the top edge of the text so the
            // bottom of the text retracts upward into the Lottie
            // — paired with the upward translation below this
            // produces the "letters fly into the icon" feel.
            message.pivotX = message.width / 2f
            message.pivotY = 0f

            val gapPx = COLLAPSE_GAP_DP * activity.resources.displayMetrics.density
            val pullY = -(message.height + gapPx)
            message.animate()
                .scaleX(0f)
                .scaleY(0f)
                .translationY(pullY)
                .alpha(0f)
                .setDuration(durationMs)
                .setInterpolator(ease)
                .start()
        }
    }

    private companion object {
        /**
         * Half of the marginTop="56dp" between the Lottie and the
         * greeting in `activity_main.xml`. Used as additional
         * upward travel for the text on collapse so the two
         * elements meet in the middle of the gap.
         */
        const val COLLAPSE_GAP_DP: Float = 28f

        /** Random greeting pool — 12 lines shown after onboarding. */
        val MESSAGES_ALL = intArrayOf(
            R.string.splash_msg_welcome_back,
            R.string.splash_msg_back_in_action,
            R.string.splash_msg_waited,
            R.string.splash_msg_codes_controlled,
            R.string.splash_msg_ready,
            R.string.splash_msg_again,
            R.string.splash_msg_watching,
            R.string.splash_msg_no_miss,
            R.string.splash_msg_system_ok,
            R.string.splash_msg_long_time,
            R.string.splash_msg_starting,
            R.string.splash_msg_guarding,
        )
    }
}
