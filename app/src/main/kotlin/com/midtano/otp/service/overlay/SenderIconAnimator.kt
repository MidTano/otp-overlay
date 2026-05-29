// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.content.Context
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.midtano.otp.R

/** "Sender label gets eaten by the source icon" animation. */
internal object SenderIconAnimator {

    /**
     * Run the full sender-tuck animation: fade the centred label
     * in, dwell, then "tuck" the sender into the source icon while
     * the icon does its parallel "eating" wobble.
     *
     * [onTucked] fires near the tail of the tuck so the digit row
     * and buttons cross-fade in just as the sender vanishes.
     */
    fun playSenderTuck(
        ctx: Context,
        icon: ImageView?,
        sender: TextView?,
        divider: View?,
        srcIcon: View?,
        onTucked: Runnable?,
    ) {
        if (sender == null) {
            onTucked?.run()
            return
        }

        val appearMs = 220L
        val readMs = 600L
        val tuckMs = 580L
        val settleMs = 420L

        val easeOut = PathInterpolator(0.16f, 1f, 0.30f, 1f)
        val easeIn = PathInterpolator(0.45f, 0f, 0.55f, 1f)
        // Strong overshoot for the icon's mouth-opening pulse.
        val easeBack = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
        val easeSettle = PathInterpolator(0.16f, 1f, 0.20f, 1f)

        // Phase 1: position the sender invisibly at the visible
        // card centre. We centre against `card_root` (not the outer
        // OtpRevealLayout, which includes 28 dp of padding for the
        // chromatic glow bleed) so the sender doesn't land off-axis
        // by ~14 dp.
        val cardRoot = sender.rootView?.findViewById<View>(R.id.card_root)
        if (cardRoot != null) {
            val sLoc = IntArray(2)
            val cLoc = IntArray(2)
            sender.getLocationInWindow(sLoc)
            cardRoot.getLocationInWindow(cLoc)
            val cardCx = cLoc[0] + cardRoot.width / 2f
            val cardCy = cLoc[1] + cardRoot.height / 2f
            val senderCx = sLoc[0] + sender.width / 2f
            val senderCy = sLoc[1] + sender.height / 2f
            sender.translationX = cardCx - senderCx
            sender.translationY = cardCy - senderCy
        }
        sender.scaleX = 0.92f
        sender.scaleY = 0.92f
        sender.alpha = 0f

        // Phase 2: fade-in / settle in the centre.
        sender.animate()
            .alpha(1f)
            .scaleX(1.10f)
            .scaleY(1.10f)
            .setDuration(appearMs)
            .setInterpolator(easeOut)
            .start()

        // Phase 3: after the dwell, run the tuck plus the parallel
        // icon eating wobble.
        sender.postDelayed({
            val senderCx2 = sender.x + sender.width / 2f
            val senderCy2 = sender.y + sender.height / 2f
            val density = ctx.resources.displayMetrics.density
            val iconCx = if (icon != null) icon.x + icon.width / 2f else senderCx2 - 40f * density
            val iconCy = if (icon != null) icon.y + icon.height / 2f else senderCy2
            val curTx = sender.translationX
            val curTy = sender.translationY
            val dx = iconCx - senderCx2 + curTx
            val dy = iconCy - senderCy2 + curTy

            sender.animate()
                .translationX(dx)
                .translationY(dy)
                .scaleX(0.18f)
                .scaleY(0.18f)
                .alpha(0f)
                .setDuration(tuckMs)
                .setInterpolator(easeIn)
                .withEndAction {
                    sender.visibility = View.GONE
                    sender.translationX = 0f
                    sender.translationY = 0f
                    sender.scaleX = 1f
                    sender.scaleY = 1f
                    sender.alpha = 1f
                }
                .start()

            divider?.animate()
                ?.alpha(0f)
                ?.setDuration(tuckMs / 3)
                ?.withEndAction { divider.visibility = View.GONE }
                ?.start()

            srcIcon?.animate()
                ?.alpha(0f)
                ?.setDuration(tuckMs / 3)
                ?.withEndAction { srcIcon.visibility = View.GONE }
                ?.start()

            // The icon eats the sender label in parallel with the
            // tuck. Wrapped in a 110 ms delay so the user FIRST sees
            // the sender start sliding before the icon reacts; the
            // open-and-wobble feels like the icon is reacting to
            // the incoming label rather than firing simultaneously.
            if (icon != null) {
                val iconDelay = 110L
                val openMs = 220L
                val wobbleMs = tuckMs - iconDelay - openMs
                icon.postDelayed({
                    playInhaleTuck(icon, openMs, wobbleMs, settleMs, easeBack, easeSettle)
                }, iconDelay)
            }

            // Phase 4: caller populates digits + buttons.
            if (onTucked != null) {
                sender.postDelayed(onTucked, tuckMs - 100L)
            }
        }, readMs)
    }

    /**
     * Anisotropic stretch: icon goes tall + narrow first
     * (X 0.85, Y 1.30) like inhaling, then snaps wide and short
     * (X 1.40, Y 1.10) as the text "lands", then settles back to 1.
     */
    private fun playInhaleTuck(
        icon: ImageView,
        openMs: Long,
        wobbleMs: Long,
        settleMs: Long,
        easeBack: PathInterpolator,
        easeSettle: PathInterpolator,
    ) {
        // Phase 1: inhale — icon grows tall and narrow.
        icon.animate()
            .scaleX(0.85f).scaleY(1.30f)
            .setDuration(openMs)
            .setInterpolator(PathInterpolator(0.42f, 0f, 0.58f, 1f))
            .start()
        // Phase 2: snap wide and shorter, like air rushed in.
        icon.postDelayed({
            icon.animate()
                .scaleX(1.40f).scaleY(1.10f)
                .setDuration(wobbleMs / 2)
                .setInterpolator(easeBack)
                .start()
        }, openMs)
        // Phase 3: soft contraction toward isotropic 1.20.
        icon.postDelayed({
            icon.animate()
                .scaleX(1.20f).scaleY(1.20f)
                .setDuration(wobbleMs / 2)
                .setInterpolator(easeSettle)
                .withEndAction { settleIcon(icon, settleMs, easeSettle) }
                .start()
        }, openMs + wobbleMs / 2)
    }

    /** Settle to scale 1.0, rotation 0°, translation 0. */
    fun settleIcon(icon: ImageView, settleMs: Long, easeSettle: PathInterpolator) {
        icon.animate()
            .scaleX(1f).scaleY(1f)
            .rotation(0f)
            .translationX(0f).translationY(0f)
            .setDuration(settleMs)
            .setInterpolator(easeSettle)
            .start()
    }
}
