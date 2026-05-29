// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.midtano.otp.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Self-contained controller for the queue chip plus expandable
 * backlog panel that lives on top of every OTP overlay card.
 *
 * The controller does not own any of the views it draws into;
 * [setViews] hands ownership over from the service when a fresh
 * overlay window is attached, and [clear] drops every cached
 * reference when the window tears down.
 */
/**
 * Owns every queue-affordance view in the OTP overlay: the
 * vertical chip on the left edge, the count label inside it, the
 * scrollable rows panel that drops out of the chip, and the
 * single-row dock that spans the card width when the panel is
 * collapsed.
 *
 * Note on visibility: kept `public` because [OverlayService] —
 * itself a `public Service` instantiated by the OS — exposes a
 * `queueUi()` accessor on its `QueueUiHost` contract. A Kotlin
 * `internal` class cannot appear in a public override's signature.
 */
class QueueUiController(
    ctx: Context,
    private val host: QueueUiHost,
) {

    private val appCtx: Context = ctx.applicationContext

    private var queueChip: FrameLayout? = null
    private var queueCountText: TextView? = null
    private var queueScroll: ScrollView? = null
    private var queuePanel: LinearLayout? = null
    private var queueDock: FrameLayout? = null
    private var queueDockCard: LinearLayout? = null
    private var queueTitle: TextView? = null
    private var queueClearBtn: View? = null

    private var queuePanelExpanded: Boolean = false
    private var queueCounterAnim: ValueAnimator? = null
    private var queuePanelAnim: ValueAnimator? = null
    private var lastDisplayedQueueCount: Int = 0

    /**
     * Latched `true` once the reveal-in sequence has placed the
     * digit row and buttons on the card. Until then, [refresh] only
     * updates the cached count so the chip never pops mid-tuck.
     */
    private var revealSequenceComplete: Boolean = false

    /** Attach a fresh set of view references — called from `attachCard`. */
    fun setViews(
        queueChip: FrameLayout?,
        queueCountText: TextView?,
        queueScroll: ScrollView?,
        queuePanel: LinearLayout?,
        queueDock: FrameLayout?,
        queueDockCard: LinearLayout?,
        queueTitle: TextView?,
        queueClearBtn: View?,
    ) {
        this.queueChip = queueChip
        this.queueCountText = queueCountText
        this.queueScroll = queueScroll
        this.queuePanel = queuePanel
        this.queueDock = queueDock
        this.queueDockCard = queueDockCard
        this.queueTitle = queueTitle
        this.queueClearBtn = queueClearBtn
    }

    /** Latch / un-latch the "reveal placed digits" guard. */
    fun setRevealSequenceComplete(complete: Boolean) {
        revealSequenceComplete = complete
    }

    /** Whether the backlog panel is currently expanded. */
    val isPanelExpanded: Boolean get() = queuePanelExpanded

    /** Reset cached queue-panel view references when the overlay tears down. */
    fun clear() {
        queueCounterAnim?.let { runCatching { it.cancel() } }
        queueCounterAnim = null
        queueChip = null
        queueCountText = null
        queueScroll = null
        queuePanel = null
        queueDock = null
        queueDockCard = null
        queueTitle = null
        queueClearBtn = null
        queuePanelExpanded = false
        lastDisplayedQueueCount = 0
        revealSequenceComplete = false
    }

    /**
     * Refresh the "+N" badge and the queue panel against the host's
     * current queue state. Safe to call before the views are
     * attached.
     */
    fun refresh(animate: Boolean) {
        val chip = queueChip ?: return
        val countText = queueCountText ?: return
        // Block chip mutation until the reveal sequence finishes
        // placing digits + buttons. Otherwise an SMS arriving
        // mid-tuck would pop the chip into a still-animating slot.
        if (!revealSequenceComplete) {
            val cur = host.queue().size()
            countText.text = appCtx.getString(R.string.queue_chip_count, cur)
            lastDisplayedQueueCount = cur
            return
        }
        val n = host.queue().size()
        val chipShouldBeVisible = n > 0
        val chipIsVisible = chip.isVisible && chip.alpha > 0.5f
        if (chipShouldBeVisible != chipIsVisible) {
            // Slide the digit row before the chip pops, in parallel.
            adjustDigitRowForChip(chipShouldBeVisible)
        }
        if (n <= 0) {
            // Capture queueChip locally so withEndAction can't NPE
            // if `clear()` nulls the field mid-animation.
            chip.animate().cancel()
            chip.animate()
                .alpha(0f)
                .scaleX(0.6f).scaleY(0.6f)
                .setDuration(CHIP_HIDE_MS)
                .setInterpolator(PathInterpolator(0.4f, 0f, 0.7f, 0.85f))
                .withEndAction {
                    if (chip.isAttachedToWindow) chip.visibility = View.GONE
                }
                .start()
            if (queuePanelExpanded) collapsePanel()
            countText.text = appCtx.getString(R.string.queue_chip_count, 0)
            lastDisplayedQueueCount = 0
            return
        }
        val wasInvisible = chip.visibility != View.VISIBLE || chip.alpha < 0.99f
        chip.visibility = View.VISIBLE
        if (wasInvisible) {
            chip.animate().cancel()
            chip.scaleX = 0.6f
            chip.scaleY = 0.6f
            chip.alpha = 0f
            chip.animate()
                .alpha(1f)
                .scaleX(1f).scaleY(1f)
                .setDuration(CHIP_SHOW_MS)
                .setInterpolator(PathInterpolator(0.34f, 1.56f, 0.64f, 1f))
                .start()
        }
        animateQueueCount(lastDisplayedQueueCount, n, animate)
        lastDisplayedQueueCount = n
        if (queuePanelExpanded) {
            rebuildPanel()
            queueDock?.post { resizePanelToContent() }
        }
    }

    /**
     * Snap the queue counter from [from] to [to] at the peak of the
     * chip's bump scale.
     *
     * The previous version interpolated the integer linearly from
     * `from` to `to` over the bump's full 460ms — for an
     * increment that meant the new value only flipped at the very
     * end (`floor((from + delta * t).toFloat())` only crosses the
     * boundary at `t = 1`), so the chip popped first and the
     * number arrived after it had already settled back. Decrements
     * looked fine by accident: `floor(3 + (-1) * 0.001) == 2` flips
     * on the very first frame.
     *
     * Both directions now snap together to [to] right when the
     * sinusoidal bump (`sin(t * ПЂ)`) crosses its maximum at
     * interpolated `t = 0.5`. The bump itself still drives the
     * spring-y "tick" feel; only the digit jump moves to the peak.
     */
    private fun animateQueueCount(from: Int, to: Int, animate: Boolean) {
        val label = queueCountText ?: return
        queueCounterAnim?.let { runCatching { it.cancel() } }
        queueCounterAnim = null
        if (!animate || from == to) {
            label.text = appCtx.getString(R.string.queue_chip_count, to)
            return
        }
        val chip = queueChip
        // Make sure the label is showing `from` before the bump
        // starts — the previous animator may have left it on a
        // partial value when it was cancelled mid-frame.
        label.text = appCtx.getString(R.string.queue_chip_count, from)
        var snapped = false
        val va = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COUNTER_BUMP_MS
            interpolator = EASE_OUT
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                if (!snapped && t >= 0.5f) {
                    snapped = true
                    label.text = appCtx.getString(R.string.queue_chip_count, to)
                }
                // Sinusoidal bump peaking at ~1.13x for a tick feel.
                val bump = 1f + 0.13f * sin(t * PI).toFloat()
                chip?.scaleX = bump
                chip?.scaleY = bump
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    label.text = appCtx.getString(R.string.queue_chip_count, to)
                    chip?.scaleX = 1f
                    chip?.scaleY = 1f
                    if (queueCounterAnim === a) queueCounterAnim = null
                }
            })
        }
        queueCounterAnim = va
        va.start()
    }

    /** Tap-handler for the queue chip. */
    fun togglePanel() {
        if (queuePanelExpanded) collapsePanel() else expandPanel()
    }

    /**
     * Collapse the queue panel if currently expanded.
     *
     * Returns the duration (ms) the caller should wait before
     * acting on the freshly-collapsed card geometry. Returns 0
     * when nothing was collapsed so callers can stay on the fast
     * path without a [android.os.Handler.postDelayed] hop.
     *
     * Used by the copy / gesture flow to defer the celebratory
     * Lottie until the card has shrunk back to its header height.
     */
    fun collapsePanelIfExpanded(): Long {
        if (!queuePanelExpanded) return 0L
        collapsePanel()
        return COLLAPSE_DURATION_MS
    }

    /**
     * Slide the centred digit row sideways via `translationX` so it
     * never overlaps the chip on appear and clears the slot on
     * dismiss.
     */
    private fun adjustDigitRowForChip(chipVisible: Boolean) {
        val root = host.overlayRoot() ?: return
        val digits = root.findViewById<View?>(R.id.ll_otp_digits) ?: return
        val chip = queueChip
        val density = appCtx.resources.displayMetrics.density
        val chipW: Int = if (chip != null) {
            val us = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            chip.measure(us, us)
            // Plus the marginEnd we apply to the chip.
            chip.measuredWidth + (4f * density).toInt()
        } else {
            (36f * density).toInt()
        }
        val target = if (chipVisible) -chipW / 2f else 0f
        digits.animate().cancel()
        digits.animate()
            .translationX(target)
            .setDuration(DIGITS_SHIFT_MS)
            .setInterpolator(PathInterpolator(0.16f, 1f, 0.30f, 1f))
            .start()
    }

    private fun expandPanel() {
        if (queueScroll == null || queuePanel == null) return
        rebuildPanel()
        animatePanelHeight(true)
        queuePanelExpanded = true
        // Cancel auto-copy while the panel is open and re-arm the
        // watchdog at a longer interval so the user has time to
        // scroll.
        host.cancelAutoCopy()
        host.cancelWatchdog()
        val myGen = host.currentGen()
        val watchdog = Runnable {
            if (!host.isCurrent(myGen)) return@Runnable
            host.removeOverlayImmediately()
        }
        host.setWatchdogRunnable(watchdog)
        host.handler().postDelayed(watchdog, PANEL_OPEN_WATCHDOG_MS)
    }

    private fun collapsePanel() {
        if (queueScroll == null) return
        animatePanelHeight(false)
        queuePanelExpanded = false
    }

    /**
     * Animate the queue panel's height between 0 and its natural
     * content height with a cubic-bezier ease-in-out for a
     * sheet-like feel.
     */
    private fun animatePanelHeight(expanding: Boolean) {
        val dock = queueDock ?: return
        queueDockCard ?: return
        queuePanelAnim?.let { runCatching { it.cancel() } }
        queuePanelAnim = null

        val contentH = measureDockHeight()

        // Mute every visual effect channel before the resize so the
        // height tween paints a flat 1-dp brand outline. Cross-fade
        // the channels back once collapse settles.
        host.reveal()?.let { r ->
            if (expanding) {
                r.setPanelEffectsAlpha(0f, EFFECTS_MUTE_MS)
            } else {
                r.setPanelEffectsAlpha(1f, EFFECTS_RESTORE_MS)
            }
        }

        val startH = if (expanding) 0 else (if (dock.height > 0) dock.height else contentH)
        val endH = if (expanding) contentH else 0

        dock.visibility = View.VISIBLE
        dock.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val dlp = dock.layoutParams
        val va = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (expanding) PANEL_EXPAND_MS else COLLAPSE_DURATION_MS
            interpolator = EASE_IN_OUT
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                val h = (startH + (endH - startH) * t).toInt()
                if (dlp.height != h) {
                    dlp.height = h
                    dock.layoutParams = dlp
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    dock.setLayerType(View.LAYER_TYPE_NONE, null)
                    dlp.height = endH
                    dock.layoutParams = dlp
                    if (!expanding) dock.visibility = View.GONE
                    if (queuePanelAnim === a) queuePanelAnim = null
                }
            })
        }
        queuePanelAnim = va
        va.start()
    }

    /**
     * Measure the queue dock card's natural height, capped to
     * ~30 % of the screen so a long backlog scrolls inside the
     * dock instead of pushing the overlay off-screen.
     */
    private fun measureDockHeight(): Int {
        val card = queueDockCard ?: return 0
        val root = host.overlayRoot()
        var parentW = if (root != null && root.width > 0) root.width else card.width
        if (parentW <= 0) parentW = appCtx.resources.displayMetrics.widthPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(parentW, View.MeasureSpec.AT_MOST)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        card.measure(wSpec, hSpec)
        var contentH = card.measuredHeight
        if (contentH <= 0) contentH = (PANEL_FALLBACK_HEIGHT_DP * appCtx.resources.displayMetrics.density).toInt()
        val maxH = (appCtx.resources.displayMetrics.heightPixels * MAX_PANEL_HEIGHT_FRACTION).toInt()
        if (contentH > maxH) contentH = maxH
        return contentH
    }

    /**
     * Smoothly re-tween the dock's height to its current natural
     * content size when a row is added or removed while the panel
     * is already expanded. Short 340 ms ease so consecutive
     * notifications still look "live" rather than waiting on a
     * long sheet settle each time.
     */
    private fun resizePanelToContent() {
        val dock = queueDock ?: return
        if (dock.visibility != View.VISIBLE) return
        queuePanelAnim?.let { runCatching { it.cancel() } }
        queuePanelAnim = null
        val targetH = measureDockHeight()
        val startH = dock.height
        if (targetH == startH) return

        val dlp = dock.layoutParams
        val from = startH
        val to = targetH

        val va = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PANEL_RESIZE_MS
            interpolator = EASE_IN_OUT
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                val h = (from + (to - from) * t).toInt()
                if (dlp.height != h) {
                    dlp.height = h
                    dock.layoutParams = dlp
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    dlp.height = to
                    dock.layoutParams = dlp
                    if (queuePanelAnim === a) queuePanelAnim = null
                }
            })
        }
        queuePanelAnim = va
        va.start()
    }

    /**
     * Rebuild the queue panel from the current queue contents.
     * Each row mirrors the chrome of the main card: source-app
     * icon + sender label + code digits + dedicated copy button.
     * Tapping anywhere on the row also copies, so the whole row is
     * the touch target.
     */
    private fun rebuildPanel() {
        val panel = queuePanel ?: return
        panel.removeAllViews()
        val inflater = LayoutInflater.from(appCtx)
        val density = appCtx.resources.displayMetrics.density
        // Hairline divider — kept dim enough to read as a soft
        // separator instead of a full grid line.
        val dividerColor = ContextCompat.getColor(appCtx, R.color.queue_panel_divider)
        var first = true
        for (p in host.queue()) {
            if (!first) {
                val divider = View(appCtx).apply {
                    setBackgroundColor(dividerColor)
                }
                val dlp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    maxOf(1, (density * 0.5f).toInt()),
                ).apply {
                    val sideMargin = (density * 10f).toInt()
                    setMargins(sideMargin, 0, sideMargin, 0)
                }
                divider.layoutParams = dlp
                panel.addView(divider)
            }
            first = false
            val row = inflater.inflate(R.layout.queue_row, panel, false)
            val ivApp = row.findViewById<ImageView>(R.id.q_iv_app)
            val tvSender = row.findViewById<TextView>(R.id.q_tv_sender)
            val tvCode = row.findViewById<TextView>(R.id.q_tv_code)
            val btnCopy = row.findViewById<View>(R.id.q_btn_copy)

            var icon: Drawable? = host.resolveAppIcon(p.pkg)
            // Mirror the main card's icon-or-letter rule: real app
            // icon if we have it, otherwise a synthetic coloured
            // disc with the sender's first letter so the list never
            // shows empty avatars.
            if (icon == null) {
                val seed = host.pickTestPalette(p.sender)
                icon = host.makeSyntheticAppIcon(p.sender, seed)
            }
            ivApp.setImageDrawable(icon)
            ivApp.visibility = View.VISIBLE
            tvSender.text = p.sender ?: ""
            tvCode.text = p.otp

            val pick = View.OnClickListener {
                // Picking from the backlog copies directly; the user
                // is not reading a just-arrived code.
                host.copyToClipboard(p.otp)
                host.queue().remove(p)
                refresh(true)
            }
            row.setOnClickListener(pick)
            btnCopy.setOnClickListener(pick)
            row.findViewById<View?>(R.id.q_btn_delete)?.setOnClickListener {
                host.queue().remove(p)
                refresh(true)
            }
            panel.addView(row)
        }
        queueTitle?.let { title ->
            val n = host.queue().size()
            title.text = appCtx.getString(
                R.string.queue_title_with_count,
                appCtx.getString(R.string.overlay_queue), n,
            )
        }
    }

    private companion object {
        val EASE_OUT: Interpolator = PathInterpolator(0.16f, 1f, 0.30f, 1f)
        val EASE_IN_OUT: Interpolator = PathInterpolator(0.65f, 0f, 0.35f, 1f)

        /**
         * Duration of the collapse tween used by
         * [collapsePanelIfExpanded]. Kept in sync with the second
         * branch of [animatePanelHeight] (`!expanding`).
         */
        const val COLLAPSE_DURATION_MS: Long = 400L

        /** Chip pop-out (alpha to 0 + scale to 0.6) on empty queue. */
        const val CHIP_HIDE_MS: Long = 220L

        /** Chip pop-in (alpha to 1 + scale to 1) on first queued OTP. */
        const val CHIP_SHOW_MS: Long = 280L

        /** Counter bump tick on every queue size change. */
        const val COUNTER_BUMP_MS: Long = 460L

        /** Digit-row sideways slide so it never overlaps the chip. */
        const val DIGITS_SHIFT_MS: Long = 320L

        /** Sheet expand tween. */
        const val PANEL_EXPAND_MS: Long = 460L

        /** Resize tween when adding / removing rows on an open panel. */
        const val PANEL_RESIZE_MS: Long = 340L

        /** How long the panel auto-stays open before the watchdog fires. */
        const val PANEL_OPEN_WATCHDOG_MS: Long = 60_000L

        /**
         * Quick fade-in of the chromatic effect channels after the
         * sheet collapses; see `setPanelEffectsAlpha` calls.
         */
        const val EFFECTS_RESTORE_MS: Long = 340L

        /** Fast effect mute when expanding the sheet. */
        const val EFFECTS_MUTE_MS: Long = 180L

        /** Maximum panel height as a fraction of the screen height. */
        const val MAX_PANEL_HEIGHT_FRACTION: Float = 0.30f

        /** Default fallback panel height (in dp) when measure returns 0. */
        const val PANEL_FALLBACK_HEIGHT_DP: Float = 180f
    }
}
