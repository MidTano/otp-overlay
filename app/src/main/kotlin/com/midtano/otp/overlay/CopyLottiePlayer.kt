// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateInterpolator
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.midtano.otp.data.Prefs
import com.midtano.otp.system.CrashLogger
import java.util.Random

/**
 * Plays the random Lottie animation that decorates the copy moment
 * on the OTP card.
 *
 * Owns the lifecycle of a single [LottieAnimationView] child:
 * create on [play], shrink + remove on completion (or watchdog),
 * recolour every layer to white before playback starts.
 *
 * [OtpRevealLayout] keeps a reference to the live view because its
 * `dispatchDraw` skips this child from the regular pass and
 * re-draws it at the end so it sits above every other effect — see
 * [currentView].
 */
internal class CopyLottiePlayer(private val parent: ViewGroup) {

    private var lottieView: LottieAnimationView? = null

    /** The currently attached Lottie view, or `null`. */
    fun currentView(): LottieAnimationView? = lottieView

    /**
     * Drop any Lottie view still attached. Idempotent; safe to call
     * from `onDetachedFromWindow` even if no animation has played.
     */
    fun release() {
        lottieView?.let {
            try {
                it.cancelAnimation()
                parent.removeView(it)
            } catch (_: Exception) {
                // The view was already detached on a different code
                // path — nothing to do beyond clearing our reference.
            }
        }
        lottieView = null
    }

    /**
     * Pick a random Lottie resource, attach it centred over [card],
     * recolour every layer to white, and start playback. When the
     * animation ends (or after the watchdog timeout) the view is
     * shrunk to zero scale and [onDone] is invoked exactly once.
     *
     * Returns immediately if [card] is `null`; the caller is expected
     * to fall back to a plain delay-then-dismiss path in that case.
     */
    fun play(card: View?, onDone: Runnable?) {
        if (card == null) return

        // Drop any previous instance so a double-tap on copy does
        // not stack two views on top of each other.
        release()

        // Single-shot wrapper so [onDone] runs whether the animator
        // finishes naturally or the watchdog fires first.
        val dismissOnce = arrayOfNulls<Runnable>(1)
        dismissOnce[0] = Runnable { onDone?.run() }

        val ctx: Context = parent.context
        try {
            val lottie = LottieAnimationView(ctx)
            val res = CopyLottiePool.RES[RANDOM.nextInt(CopyLottiePool.RES.size)]
            lottie.repeatCount = 0
            lottie.speed = Prefs.getFxCopyLottieSpeedFloat(ctx)

            // Fixed 42 dp square, centred in the padded content area
             // of [parent] (the OtpRevealLayout that hosts the OTP
             // card). [parent] has 28 dp horizontal + 20 dp vertical
             // padding so the chromatic card glow can bleed past the
             // visible card edge; positioning a child via plain
             // `MarginLayoutParams` puts it at `padding + margin`,
             // not at `margin` — so we must subtract the padding
             // from the centring math, otherwise the burst lands
             // 28 dp right and 20 dp below the card centre.
             //
             // The previous size formula `0.8 Г— min(card.width,
             // card.height)` with `card` = `tv_otp` was geometry-
             // dependent (4-digit codes have a narrower TextView,
             // dropping `min` below the 52 dp header height) and
             // its positioning math `card.left + (card.width -
             // side) / 2` only happened to land near the card
             // centre because `tv_otp.left` (in `header_row`'s
             // coordinate space) numerically equals the OtpRevealLayout
             // padding the FrameLayout adds back when it places
             // the lottie — a coincidence that broke as soon as
             // any geometry shifted (e.g. queue collapse re-laying
             // out the card root).
             //
             // A constant size centred in the explicit content
             // rectangle is the simplest correct model.
             val density = ctx.resources.displayMetrics.density
             val side = (42f * density).toInt()
             val contentW = parent.width - parent.paddingLeft - parent.paddingRight
             val contentH = parent.height - parent.paddingTop - parent.paddingBottom
             val lp = ViewGroup.MarginLayoutParams(side, side).apply {
                 leftMargin = (contentW - side) / 2
                 topMargin = (contentH - side) / 2
             }
             lottie.layoutParams = lp

            // Publish the view reference BEFORE setAnimation, because
            // setAnimation can synchronously deliver the composition
            // when the resource is already in Lottie's process-wide
            // cache (which happens on every show after the first per
            // resource). startBurst() needs lottieView to be set so
            // dispatchDraw can keep painting the view above other
            // effects.
            parent.addView(lottie)
            lottie.bringToFront()
            lottieView = lottie

            // Idempotency latch: setAnimation() may deliver the
            // composition synchronously (cache hit) AND register
            // the on-loaded listener — only one of the two paths
            // should drive recolour + play. The latch is also a
            // safety net against Lottie ever calling the listener
            // twice for the same composition.
            val started = booleanArrayOf(false)
            val startBurst: (LottieAnimationView) -> Unit = { v ->
                if (!started[0]) {
                    started[0] = true
                    recolourWhite(v)
                    v.progress = 0f
                    v.playAnimation()
                }
            }

            // Listener FIRST so the async path is also covered when
            // the composition isn't cached. The listener fires once
            // the composition has been parsed and assigned. addView
            // is already done so the view is on screen even on the
            // async path.
            lottie.addLottieOnCompositionLoadedListener { startBurst(lottie) }

            lottie.addAnimatorListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    shrinkAndDismiss(dismissOnce)
                }
            })

            // Now wire the animation. Two outcomes:
            //   1. Cache miss: setAnimation kicks off an async task,
            //      our listener will fire when it finishes.
            //   2. Cache hit: setAnimation synchronously assigns the
            //      composition; the listener may or may not fire
            //      before we return — see below.
            lottie.setAnimation(res)

            // Belt-and-braces sync path. If the composition is
            // already attached (cache hit) but the listener never
            // ran (e.g. Lottie internals decided not to invoke
            // already-loaded listeners on a synchronous attach),
            // start the burst right now. The latch prevents a
            // double-play if both paths fire.
            if (lottie.composition != null) {
                startBurst(lottie)
            }

            // Watchdog: Lottie can play up to ~6 s at 0.5x speed; 8 s
            // gives a safe margin in case the animator never completes.
            parent.postDelayed({
                if (dismissOnce[0] != null) shrinkAndDismiss(dismissOnce)
            }, 8000L)
        } catch (e: Exception) {
            // Lottie failed to load — fall straight through to the
            // dismiss path so the overlay still tears down. Log so
            // a recurring asset / decoder failure is visible
            // through the on-device diagnostic instead of being
            // silently swallowed.
            CrashLogger.logErr("CopyLottiePlayer.play failed; falling back to plain dismiss", e)
            parent.postDelayed({
                dismissOnce[0]?.let {
                    it.run()
                    dismissOnce[0] = null
                }
            }, 760L)
        }
    }

    /**
     * Recolour every layer of the loaded composition to white so
     * the burst always reads on the dark card regardless of the
     * source palette. Must be called only after the composition
     * has been attached to [view].
     */
    private fun recolourWhite(view: LottieAnimationView) {
        view.addValueCallback(
            KeyPath("**"),
            LottieProperty.COLOR,
            LottieValueCallback(Color.WHITE),
        )
        view.addValueCallback(
            KeyPath("**"),
            LottieProperty.STROKE_COLOR,
            LottieValueCallback(Color.WHITE),
        )
    }

    /** Shrink the Lottie view to zero scale, then run [dismissOnce]. */
    private fun shrinkAndDismiss(dismissOnce: Array<Runnable?>) {
        val r = dismissOnce[0] ?: return
        val lottie = lottieView
        if (lottie == null) {
            dismissOnce[0] = null
            r.run()
            return
        }
        lottie.pivotX = lottie.width / 2f
        lottie.pivotY = lottie.height / 2f
        lottie.animate()
            .scaleX(0f)
            .scaleY(0f)
            .setDuration(280L)
            .setInterpolator(AnticipateInterpolator(1.2f))
            .withEndAction {
                val rr = dismissOnce[0]
                if (rr != null) {
                    dismissOnce[0] = null
                    rr.run()
                }
            }
            .start()
    }

    private companion object {
        /**
         * Shared RNG: picking a random copy emoji isn't security
         * sensitive and the [Random] state being shared across
         * shows is fine. Avoids a fresh allocation on every burst.
         */
        private val RANDOM: Random = Random()
    }
}
