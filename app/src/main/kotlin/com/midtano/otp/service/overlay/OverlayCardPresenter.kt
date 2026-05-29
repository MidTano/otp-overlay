// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.midtano.otp.R
import com.midtano.otp.data.Prefs
import com.midtano.otp.overlay.BackInterceptLayout
import com.midtano.otp.system.CrashLogger

/**
 * Inflates the OTP card overlay window, wires up its animations
 * and starts the auto-copy / watchdog timers.
 *
 * Talks back to the service through [OverlayCardHost] so all
 * cross-component state (overlayRoot / reveal / generation /
 * watchdog Runnable) keeps living on the service — which is the
 * only place those fields can sensibly live, since dismiss / copy /
 * detach paths reach for them from outside the card-build flow.
 */
internal class OverlayCardPresenter(
    private val ctx: Context,
    private val host: OverlayCardHost,
) {

    /**
     * Inflate the overlay window, hand it to [android.view.WindowManager]
     * and kick the reveal sequence and auto-copy timers.
     *
     * @return `false` if the window failed to attach. The caller
     *         then pumps the next queued OTP rather than stranding
     *         it behind a window that never made it on-screen.
     */
    fun attach(otp: String, sender: String?, source: String?, pkg: String?): Boolean {
        val inflater = LayoutInflater.from(ctx)
        // null parent is intentional: this card attaches to a
        // WindowManager surface, not to a ViewGroup. The card's
        // root layout params come from a separately built
        // WindowManager.LayoutParams.

        @Suppress("InflateParams")
        val root = inflater.inflate(R.layout.overlay_otp, null)
        host.setOverlayRoot(root)

        val backRoot = root as BackInterceptLayout
        host.setReveal(root.findViewById(R.id.reveal_layout))

        val tvOtp = root.findViewById<TextView>(R.id.tv_otp)
        val tvSender = root.findViewById<TextView>(R.id.tv_sender)
        val ivSource = root.findViewById<ImageView>(R.id.iv_source)
        val ivApp = root.findViewById<ImageView>(R.id.iv_app_icon)
        val llDigits = root.findViewById<LinearLayout>(R.id.ll_otp_digits)
        val btnCopy = root.findViewById<View>(R.id.btn_copy)
        val btnClose = root.findViewById<View>(R.id.btn_close)
        val divider = root.findViewById<View?>(R.id.sender_divider)

        wireQueueUiFromRoot(root)

        // tv_otp stays in the tree for ACTION_SET_TEXT consumers and
        // the captured-blur visibility, but it is invisible — the
        // per-digit LinearLayout below is what the user actually
        // sees.
        tvOtp.text = otp
        tvSender.text = sender ?: ""
        ivSource.setImageResource(
            if (sourceIsPush(source)) R.drawable.ic_bell else R.drawable.ic_sms,
        )

        scheduleHeaderRevealSequence(
            root, tvSender, ivSource, ivApp,
            llDigits, btnCopy, btnClose, divider, otp,
        )

        applyAppIconBrand(ivApp, sender, source, pkg)
        wireCardActions(backRoot, btnCopy, btnClose, tvOtp, otp, sender, pkg)

        val params = OverlayLayoutParams.buildCardParams(ctx)
        try {
            host.windowManager().addView(root, params)
        } catch (_: WindowManager.BadTokenException) {
            // SYSTEM_ALERT_WINDOW revoked, or token went stale on a
            // race with onDestroy.
            host.setOverlayRoot(null)
            return false
        } catch (_: IllegalStateException) {
            // Some OEMs throw IllegalStateException ("View has
            // already been added") when a teardown is in flight.
            host.setOverlayRoot(null)
            return false
        }
        host.playPopSound()

        // Initial population of the queue UI in case codes were
        // enqueued while the overlay was inflating.
        host.queueUi().refresh(false)

        startAutoCopyAndWatchdog(otp, tvOtp)
        return true
    }

    private fun wireQueueUiFromRoot(root: View) {
        val queueChip = root.findViewById<FrameLayout?>(R.id.btn_queue)
        val queueCountText = root.findViewById<TextView?>(R.id.tv_queue_count)
        val queueScroll = root.findViewById<ScrollView?>(R.id.queue_scroll)
        val queuePanel = root.findViewById<LinearLayout?>(R.id.queue_list)
        val queueDock = root.findViewById<FrameLayout?>(R.id.queue_dock)
        val queueDockCard = root.findViewById<LinearLayout?>(R.id.queue_dock_card)
        val queueTitle = root.findViewById<TextView?>(R.id.tv_queue_title)
        val queueClearBtn = root.findViewById<View?>(R.id.btn_queue_clear)
        host.queueUi().setViews(
            queueChip,
            queueCountText,
            queueScroll,
            queuePanel,
            queueDock,
            queueDockCard,
            queueTitle,
            queueClearBtn,
        )
        host.queueUi().setRevealSequenceComplete(false)
        queueChip?.setOnClickListener { host.queueUi().togglePanel() }
        queueClearBtn?.setOnClickListener {
            host.queue().clear()
            host.queueUi().refresh(true)
        }
    }

    /**
     * Schedule the header-reveal sequence to run once the chromatic
     * card-in animation has settled. Picks between the loose path
     * (sender + digits both fit) and the tight path (sender → icon
     * tuck) based on a measure of the natural digit-row width.
     */
    private fun scheduleHeaderRevealSequence(
        root: View,
        tvSender: TextView,
        ivSource: ImageView,
        ivApp: ImageView,
        llDigits: LinearLayout,
        btnCopy: View,
        btnClose: View,
        divider: View?,
        otp: String,
    ) {
        val revealMs: Long = try {
            Prefs.getFxRevealMs(ctx).toLong()
        } catch (e: ClassCastException) {
            // SharedPreferences.getInt throws ClassCastException
            // when the stored value is of another type — possible
            // if the user side-loaded a debug build that wrote the
            // same key with a different type. Falls back to the
            // visual default rather than crashing the reveal path.
            CrashLogger.logErr("getFxRevealMs failed; using default", e)
            1100L
        }
        // Card alpha-tween starts at 30 % of reveal and lasts 360 ms.
        // Anchor the sender placement at alphaStart + alphaDur*0.5
        // so it appears as the card-in spring is settling.
        val cardVisibleMs = (revealMs * 0.30f).toLong() + 180L

        root.postDelayed({
            if (!root.isAttachedToWindow) return@postDelayed
            decideAndRunHeaderPath(
                root, otp, tvSender, ivSource, ivApp,
                llDigits, btnCopy, btnClose, divider,
            )
        }, cardVisibleMs)
    }

    private fun decideAndRunHeaderPath(
        root: View,
        otp: String,
        tvSender: TextView,
        ivSource: ImageView,
        ivApp: ImageView,
        llDigits: LinearLayout,
        btnCopy: View,
        btnClose: View,
        divider: View?,
    ) {
        // The header is a FrameLayout where digits float over the
        // card centre and the left/right clusters overlay the edges.
        // "Tight" therefore means digit-row width plus
        // max(left, right) cluster width Г— 2 exceeds the card width.
        val neededDigitsW = DigitRowHelper.estimateDigitRowWidth(ctx, otp)
        val headerLeft = root.findViewById<View?>(R.id.header_left)
        val headerRight = root.findViewById<View?>(R.id.header_right)
        val headerRow = llDigits.parent as? ViewGroup
        val cardW = headerRow?.width ?: 0
        val unboundedW = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val unboundedH = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        headerLeft?.measure(unboundedW, unboundedH)
        headerRight?.measure(unboundedW, unboundedH)
        val leftW = headerLeft?.measuredWidth ?: 0
        val rightW = headerRight?.measuredWidth ?: 0
        val paddingW = dp(10f) + dp(8f)
        val clusterMax = maxOf(leftW, rightW)
        val requiredW = neededDigitsW + 2 * clusterMax + paddingW + dp(8f)
        val tight = cardW > 0 && requiredW > cardW

        if (!tight) {
            runLooseHeaderPath(
                root,
                otp,
                tvSender,
                ivSource,
                divider,
                llDigits,
                btnCopy,
                btnClose,
            )
        } else {
            runTightHeaderPath(
                root, otp, tvSender, ivSource, ivApp, divider,
                llDigits, btnCopy, btnClose,
                headerLeft, headerRight, unboundedW, unboundedH,
            )
        }
    }

    /** Loose path — sender + digits both fit, fade everything in. */
    private fun runLooseHeaderPath(
        root: View,
        otp: String,
        tvSender: TextView,
        ivSource: ImageView,
        divider: View?,
        llDigits: LinearLayout,
        btnCopy: View,
        btnClose: View,
    ) {
        val ease = PathInterpolator(0.16f, 1f, 0.30f, 1f)
        DigitRowHelper.resetDigitTextSize(ctx, llDigits)
        tvSender.animate().alpha(1f).setDuration(220L).setInterpolator(ease).start()
        divider?.animate()?.alpha(1f)?.setDuration(220L)?.start()
        ivSource.animate().alpha(0.5f).setDuration(220L).start()
        DigitRowHelper.populateDigitsAnimated(ctx, host.handler(), llDigits, otp)
        llDigits.animate().alpha(1f).setDuration(220L).setInterpolator(ease).start()
        btnCopy.animate().alpha(1f).setDuration(260L).start()
        btnClose.animate().alpha(1f).setDuration(260L).start()
        // Reveal sequence is now complete — let the queue chip pop
        // in if anything queued up while we were busy.
        root.postDelayed({
            if (root.isAttachedToWindow) {
                host.queueUi().setRevealSequenceComplete(true)
                host.queueUi().refresh(true)
            }
        }, 280L)
    }

    /**
     * Tight path — sender → icon tuck animation, then digits.
     * Hidden divider / source-icon stay invisible (centre-mounted
     * sender doesn't need them); they collapse to GONE inside the
     * tuck animation.
     */
    private fun runTightHeaderPath(
        root: View,
        otp: String,
        tvSender: TextView,
        ivSource: ImageView,
        ivApp: ImageView,
        divider: View?,
        llDigits: LinearLayout,
        btnCopy: View,
        btnClose: View,
        headerLeft: View?,
        headerRight: View?,
        unboundedW: Int,
        unboundedH: Int,
    ) {
        val ease = PathInterpolator(0.16f, 1f, 0.30f, 1f)
        SenderIconAnimator.playSenderTuck(ctx, ivApp, tvSender, divider, ivSource) {
            if (!root.isAttachedToWindow) return@playSenderTuck
            DigitRowHelper.populateDigitsAnimated(ctx, host.handler(), llDigits, otp)
            // Re-measure once sender is GONE — gives us the real
            // free width inside the FrameLayout.
            //
            // The tuck callback fires `tuckMs - 100ms` early so the
            // digit reveal can overlap the tail of the sender's
            // disappearance. By that point divider + source-icon
            // are already GONE (their alpha tween finishes at
            // `tuckMs / 3`, well before us), but `tv_sender` is
            // still VISIBLE for ~100 ms more — its `withEndAction`
            // fires only when the full tuck completes.
            //
            // Without forcing it GONE here, headerLeft.measuredWidth
            // would still include `tv_sender`'s natural text width
            // (sender is at alpha 0 / scale 0.18 visually, but
            // `measure()` ignores transforms), inflating `lW` by
            // tens of dp. That made `avail2` collapse to near-zero
            // and `shrinkDigitsToFit` ate the digit-row text size
            // for codes with 7+ characters even though the post-
            // tuck cluster is just `iv_app_icon` (~41 dp) and the
            // free width is plenty. Setting `tv_sender` GONE here
            // is harmless: it's already invisible to the user, and
            // the tuck animation's `withEndAction` will redundantly
            // set it GONE again on completion — a no-op.
            tvSender.visibility = View.GONE
            divider?.visibility = View.GONE
            ivSource.visibility = View.GONE

            val hr = llDigits.parent as? ViewGroup
            val hrW = hr?.width ?: 0
            headerLeft?.measure(unboundedW, unboundedH)
            headerRight?.measure(unboundedW, unboundedH)
            val lW = headerLeft?.measuredWidth ?: 0
            val rW = headerRight?.measuredWidth ?: 0
            val cMax = maxOf(lW, rW)
            val avail2 = maxOf(0, hrW - 2 * cMax - dp(16f))
            DigitRowHelper.shrinkDigitsToFit(ctx, llDigits, avail2)
            llDigits.animate().alpha(1f).setDuration(280L).setInterpolator(ease).start()
            btnCopy.animate().alpha(1f).setDuration(280L).start()
            btnClose.animate().alpha(1f).setDuration(280L).start()
            root.postDelayed({
                if (root.isAttachedToWindow) {
                    host.queueUi().setRevealSequenceComplete(true)
                    host.queueUi().refresh(true)
                }
            }, 320L)
        }
    }

    private fun applyAppIconBrand(
        ivApp: ImageView,
        sender: String?,
        source: String?,
        pkg: String?,
    ) {
        var appIcon: Drawable? = host.resolveAppIcon(pkg)
        val brand = host.dominantColor(appIcon, source)
        if (sourceIsTest(source) && appIcon == null) {
            appIcon = host.makeSyntheticAppIcon(sender, brand)
        }
        if (appIcon != null) {
            ivApp.setImageDrawable(appIcon)
            ivApp.visibility = View.VISIBLE
        } else {
            ivApp.visibility = View.GONE
        }
        host.reveal()?.setBrandColor(brand)
    }

    private fun wireCardActions(
        backRoot: BackInterceptLayout,
        btnCopy: View,
        btnClose: View,
        tvOtp: TextView,
        otp: String,
        sender: String?,
        pkg: String?,
    ) {
        btnCopy.setOnClickListener { host.copyWithCelebration(otp, tvOtp) }
        btnClose.setOnClickListener {
            if (Prefs.isCloseToShade(ctx)) {
                host.showShade(otp, sender, pkg)
                host.dismissOverlay()
            } else {
                host.dismissOverlay()
            }
        }
        backRoot.setBackListener {
            when {
                Prefs.isBackToShade(ctx) -> {
                    host.showShade(otp, sender, pkg)
                    host.dismissOverlay()
                }
                Prefs.isBackCopy(ctx) -> host.copyWithCelebration(otp, tvOtp)
                else -> host.dismissOverlay()
            }
        }
    }

    /**
     * Drive the chromatic countdown stroke, the auto-copy timer
     * and the hard service-level watchdog. Generation-checked so a
     * stale callback can never tear down a fresh overlay.
     */
    private fun startAutoCopyAndWatchdog(otp: String, codeView: TextView) {
        val myGen = host.currentGen()

        host.reveal()?.setCountdown(1f)
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = OverlayServiceConfig.AUTO_COPY_MS.toLong()
            // Strict LINEAR — a countdown needs constant velocity
            // so the remaining-time reading stays honest.
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                if (!host.isCurrent(myGen)) return@addUpdateListener
                host.reveal()?.setCountdown(1f - (a.animatedValue as Float))
            }
            start()
        }
        host.setProgressAnimator(animator)

        val autoCopy = Runnable {
            if (!host.isCurrent(myGen)) return@Runnable
            host.copyWithCelebration(otp, codeView)
        }
        host.setAutoCopyRunnable(autoCopy)
        host.handler().postDelayed(autoCopy, OverlayServiceConfig.AUTO_COPY_MS.toLong())

        // Hard service-level watchdog. NOT cancelled by
        // cancelAutoCopy() — it must outlive the auto-copy timer.
        val watchdog = Runnable {
            if (!host.isCurrent(myGen)) return@Runnable
            host.removeOverlayImmediately()
        }
        host.setWatchdogRunnable(watchdog)
        host.handler().postDelayed(watchdog, OverlayServiceConfig.WATCHDOG_MS)
    }

    private fun dp(v: Float): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun sourceIsPush(source: String?): Boolean =
        source == com.midtano.otp.extractor.OtpSource.PUSH.storageId

    private fun sourceIsTest(source: String?): Boolean =
        source == com.midtano.otp.extractor.OtpSource.TEST.storageId
}
