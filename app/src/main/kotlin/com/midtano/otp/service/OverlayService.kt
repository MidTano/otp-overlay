// SPDX-License-Identifier: MIT
package com.midtano.otp.service

import android.animation.ValueAnimator
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.WindowManager
import com.midtano.otp.data.Prefs
import com.midtano.otp.extractor.OtpSource
import com.midtano.otp.extractor.OtpStats
import com.midtano.otp.locale.LocaleHelper
import com.midtano.otp.overlay.OtpRevealLayout
import com.midtano.otp.service.overlay.AppIconResolver
import com.midtano.otp.service.overlay.AutoPasteToastController
import com.midtano.otp.service.overlay.AutoPasteToastHost
import com.midtano.otp.service.overlay.ForegroundNotifier
import com.midtano.otp.service.overlay.OtpShadeNotifier
import com.midtano.otp.service.overlay.OverlayCardHost
import com.midtano.otp.service.overlay.OverlayCardPresenter
import com.midtano.otp.service.overlay.OverlayClipboard
import com.midtano.otp.service.overlay.OverlayHaptics
import com.midtano.otp.service.overlay.OverlayLayoutParams
import com.midtano.otp.service.overlay.OverlayQueue
import com.midtano.otp.service.overlay.OverlayServiceConfig
import com.midtano.otp.service.overlay.OverlaySounds
import com.midtano.otp.service.overlay.PendingShow
import com.midtano.otp.service.overlay.QueueUiController
import com.midtano.otp.service.overlay.QueueUiHost
import com.midtano.otp.service.overlay.ScreenOffBroadcastBridge
import com.midtano.otp.system.CrashLogger
import com.midtano.otp.system.LastNotification
import com.midtano.otp.system.LogRedactor

/**
 * Foreground service that owns the OTP overlay window.
 *
 * Responsibilities are deliberately narrow:
 * - lifecycle — runs as a foreground service so the OS doesn't
 *   kill it,
 * - intent routing — `SHOW_OTP` / `DISMISS` / `SHOW_TEST`,
 * - single-overlay invariant + queue serialisation,
 * - dismiss / copy / detach orchestration with generation tracking,
 * - auto-paste delegation to [OtpAccessibilityService],
 * - haptics, sounds, screen-off teardown, and proper foreground
 *   notification management.
 *
 * The card-build pipeline (inflate, header reveal, brand colour,
 * auto-copy timers) lives in [OverlayCardPresenter]; the queue
 * chip + panel UI in [QueueUiController]; the auto-paste toast in
 * [AutoPasteToastController]; the screen-off bridge in
 * [ScreenOffBroadcastBridge]. Each owns its own state and talks
 * back through one of the three `*Host` interfaces this service
 * implements directly, so the dependency graph stays one-way and
 * each subsystem can still be tested against a stub host.
 */
class OverlayService :
    Service(),
    QueueUiHost,
    AutoPasteToastHost,
    OverlayCardHost {

    private lateinit var windowManager: WindowManager
    private lateinit var handler: Handler
    private lateinit var queueUi: QueueUiController
    private lateinit var cardPresenter: OverlayCardPresenter

    private var overlayRoot: View? = null
    private var reveal: OtpRevealLayout? = null
    private var progressAnimator: ValueAnimator? = null
    private var autoCopyRunnable: Runnable? = null
    private var watchdogRunnable: Runnable? = null
    private var deferredCardAttach: Runnable? = null
    private var currentOtp: String? = null

    /**
     * Latched on the first successful copy of the live overlay.
     * Prevents a second tap on Copy (or another auto-copy callback
     * that lost its generation race) from queueing a duplicate
     * Lottie burst while the queue panel is collapsing.
     */
    private var copyInProgress: Boolean = false

    /** Owns the screen-off / screen-on broadcast lifecycle and debounce. */
    private var screenOffBridge: ScreenOffBroadcastBridge? = null

    /**
     * Generation counter for the currently-attached overlay window.
     * Bumped on every successful [showOverlayInternal]. Dismiss /
     * copy / watchdog callbacks capture the value at attach time
     * and refuse to act if it has since moved on, preventing stale
     * callbacks from tearing down a freshly-attached overlay.
     * Main-thread only.
     */
    private var currentGen: Int = 0

    /**
     * Pending shows that arrived while another overlay was on
     * screen. [OverlayQueue] carries the hard-cap ceiling and
     * content-based dedup.
     */
    private val queue = OverlayQueue()

    /** Self-contained auto-paste toast subsystem; lazy. */
    private var autoPasteToast: AutoPasteToastController? = null

    /** Sound effects (pop / success / auto-paste). */
    private var sounds: OverlaySounds? = null

    override fun onCreate() {
        super.onCreate()
        applyLocale()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        handler = Handler(Looper.getMainLooper())

        queueUi = QueueUiController(this, this)
        cardPresenter = OverlayCardPresenter(this, this)

        createForegroundChannel()
        startOverlayForeground()
        registerScreenReceiver()
        initSoundPool()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground first thing — even if the intent is
        // null or carries an unrecognised action. Android 14+ kills
        // the process with ForegroundServiceDidNotStartInTimeException
        // if startForegroundService() is followed by anything other
        // than startForeground() within 5 seconds.
        startOverlayForeground()
        if (intent == null) return START_STICKY
        routeIntent(intent)
        return START_STICKY
    }

    private fun routeIntent(intent: Intent) {
        when (intent.action) {
            ACTION_SHOW_OTP -> {
                val otp = intent.getStringExtra(EXTRA_OTP)
                val sender = intent.getStringExtra(EXTRA_SENDER)
                val source = intent.getStringExtra(EXTRA_SOURCE)
                val pkg = intent.getStringExtra(EXTRA_PKG)
                if (otp.isNullOrEmpty()) return
                if (Prefs.isShadeMode(this)) {
                    handler.post { OtpShadeNotifier.show(this, otp, sender, pkg) }
                } else {
                    handler.post { enqueueShow(otp, sender, source, pkg) }
                }
            }
            ACTION_DISMISS -> handler.post(::dismissOverlay)
            OtpShadeNotifier.ACTION_COPY -> {
                val otp = intent.getStringExtra(OtpShadeNotifier.EXTRA_OTP)
                val notifId = intent.getIntExtra(OtpShadeNotifier.EXTRA_NOTIF_ID, 0)
                OtpShadeNotifier.onCopyAction(this, otp, notifId)
            }
            OtpShadeNotifier.ACTION_DISMISS -> {
                val notifId = intent.getIntExtra(OtpShadeNotifier.EXTRA_NOTIF_ID, 0)
                OtpShadeNotifier.onDismissAction(this, notifId)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Re-clamp the overlay window width on rotation.
     *
     * The card is locked to the device's portrait short-edge so it
     * doesn't stretch the whole way across in landscape. Without
     * this hook, an overlay that's already attached when the user
     * flips the phone would keep the old width (which the system
     * scales as if the device were still in the previous
     * orientation), so we re-issue [WindowManager.LayoutParams]
     * with the freshly-computed portrait short-edge.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val root = overlayRoot ?: return
        if (!root.isAttachedToWindow) return
        try {
            val current = root.layoutParams as? WindowManager.LayoutParams ?: return
            val target = OverlayLayoutParams.portraitCardWidthPx(this)
            if (current.width == target) return
            current.width = target
            windowManager.updateViewLayout(root, current)
        } catch (e: IllegalArgumentException) {
            // updateViewLayout throws IAE when the view is no longer
            // a known WindowManager child (mid-detach race). Log and
            // skip — the next attach will rebuild the params.
            CrashLogger.logErr("onConfigurationChanged: relayout failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlayImmediately()
        unregisterScreenReceiver()
        sounds?.release()
        sounds = null
    }

    /**
     * Promote the service to foreground with the right
     * `foregroundServiceType` for the current OS version:
     * `SPECIAL_USE` on API 34+, `DATA_SYNC` otherwise.
     */
    private fun startOverlayForeground() {
        val notif = buildForegroundNotification()
        try {
            // SPECIAL_USE on Android 14+: Play Protect's long-running
            // data-sync heuristic would otherwise flag an overlay
            // that stays alive while the user authenticates.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    ForegroundNotifier.FG_NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    ForegroundNotifier.FG_NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(ForegroundNotifier.FG_NOTIF_ID, notif)
            }
        } catch (e: SecurityException) {
            // FOREGROUND_SERVICE_SPECIAL_USE not held by the OEM
            // build despite a positive API check. Fall through to
            // the untyped variant.
            CrashLogger.logErr("startForeground typed call denied", e)
            tryUntypedForeground(notif)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException and
            // MissingForegroundServiceTypeException are both
            // subclasses of IllegalStateException. Without
            // foreground the OS will kill us sooner, but the
            // overlay still works for the current task.
            CrashLogger.logErr("startForeground typed call refused", e)
            tryUntypedForeground(notif)
        }
    }

    private fun tryUntypedForeground(notif: Notification) {
        try {
            startForeground(ForegroundNotifier.FG_NOTIF_ID, notif)
        } catch (inner: SecurityException) {
            CrashLogger.logErr("startForeground untyped fallback denied", inner)
        } catch (inner: IllegalStateException) {
            CrashLogger.logErr("startForeground untyped fallback refused", inner)
        }
    }

    /**
     * Public entry point used by all SHOW_OTP intents. Either
     * shows the overlay immediately (if nothing else is on screen)
     * or queues it for replay after the current overlay tears down.
     *
     * The same OTP is dropped if it's already on screen or already
     * in the queue, preventing the "same code shows twice" case
     * for SMS + push mirror pairs that arrive farther apart than
     * `OtpDeduplicator.WINDOW_MS`.
     */
    private fun enqueueShow(otp: String, sender: String?, source: String?, pkg: String?) {
        if (otp.isEmpty()) return
        if (overlayRoot != null) {
            if (queue.offer(PendingShow(otp, sender, source, pkg), currentOtp)) {
                queueUi.refresh(true)
            }
            return
        }
        if (otp == currentOtp) return
        showOverlayInternal(otp, sender, source, pkg)
    }

    /** Pop the next queued show. Called after an overlay tears down. */
    private fun pumpQueue() {
        if (overlayRoot != null) return
        val next = queue.pollFirst()
        if (next == null) {
            // Nothing pending — retire the foreground notification
            // so it doesn't sit in the tray indefinitely.
            maybeStopForeground()
            return
        }
        showOverlayInternal(next.otp, next.sender, next.source, next.pkg)
    }

    /**
     * Drop the foreground notification when nothing is on screen
     * and the queue is empty. The next ACTION_SHOW_OTP intent will
     * re-promote the service.
     */
    private fun maybeStopForeground() {
        if (overlayRoot != null) return
        if (!queue.isEmpty()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: IllegalStateException) {
            // Service was already detached from foreground (race
            // with onDestroy) — treat as no-op.
            CrashLogger.logErr("stopForeground failed", e)
        }
    }

    private fun showOverlayInternal(otp: String, sender: String?, source: String?, pkg: String?) {
        // Refresh locale on every show so language changes from
        // Settings take effect on the next overlay without needing
        // to restart the service.
        applyLocale()
        // enqueueShow only calls this when overlayRoot is null, so
        // there is nothing to remove. Bumping the generation
        // up-front means any stale callbacks still in flight from a
        // previous overlay are now strictly outdated and will no-op.
        currentGen++
        currentOtp = otp

        try {
            OtpStats.record(this, sender, source, pkg)
        } catch (e: IllegalStateException) {
            // OtpStats internally launches on IoScope; the only
            // failure mode that surfaces synchronously is a
            // CoroutineScope tear-down race during process death.
            CrashLogger.logErr("OtpStats.record failed", e)
        }
        CrashLogger.log(
            "showOverlayInternal: otp=*** sender=${LogRedactor.redactSender(sender)} " +
                "source=$source pkg=$pkg",
        )

        if (tryAutoPaste(otp, sender, pkg)) return

        vibrateLight()
        attachCard(otp, sender, source, pkg)
    }

    /**
     * Try the silent accessibility-service auto-paste path.
     *
     * @return `true` iff the paste succeeded and the toast is being
     *         shown (the caller then skips [attachCard]).
     */
    private fun tryAutoPaste(otp: String, sender: String?, pkg: String?): Boolean {
        if (!Prefs.isAutoPaste(this)) return false
        val svc = OtpAccessibilityService.peekInstance()
        if (svc == null) {
            LastNotification.save(
                this,
                "autopaste",
                otp,
                "autopaste skipped: Accessibility service not running " +
                    "(enable in Android: Settings → Accessibility)",
            )
            OtpAccessibilityService.setPendingOtp(otp)
            return false
        }
        val result = svc.pasteNow(otp)
        LastNotification.save(this, "autopaste", otp, "autopaste: ${result.message()}")
        if (!result.isSuccess()) {
            OtpAccessibilityService.setPendingOtp(otp)
            return false
        }
        if (!Prefs.isAutopasteNoCopy(this)) {
            copyToClipboard(otp)
        }
        showAutoPasteToast(otp, sender, pkg)
        return true
    }

    private fun attachCard(otp: String, sender: String?, source: String?, pkg: String?) {
        deferredCardAttach = null
        if (!cardPresenter.attach(otp, sender, source, pkg)) {
            // Failed attach — surface the next queued OTP rather
            // than stranding it behind a window that never made it
            // on-screen.
            handler.post(::pumpQueue)
        }
    }

    private fun showAutoPasteToast(otp: String, sender: String?, pkg: String?) {
        val toast = autoPasteToast ?: AutoPasteToastController(this, this)
            .also { autoPasteToast = it }
        toast.show(otp, sender, pkg)
    }

    /** Run the dismiss animation, then detach. */
    override fun dismissOverlay() {
        if (overlayRoot == null) return
        cancelAutoCopy()
        deferredCardAttach?.let { handler.removeCallbacks(it) }
        deferredCardAttach = null
        val myGen = currentGen
        // Belt-and-braces fallback: even if no animator end-callback
        // fires (cancellation, lost frame, …) the overlay still
        // gets torn down. Gen-checked so a stale safety from a
        // previous overlay cannot tear down a fresh one.
        val safety = Runnable {
            if (!isCurrent(myGen)) return@Runnable
            detachAfterAnimation()
        }
        handler.postDelayed(safety, OverlayServiceConfig.SAFETY_DETACH_MS)
        val r = reveal
        if (r != null) {
            r.startDismiss {
                handler.removeCallbacks(safety)
                if (!isCurrent(myGen)) return@startDismiss
                detachAfterAnimation()
            }
        } else {
            handler.removeCallbacks(safety)
            detachAfterAnimation()
        }
    }

    /** Tear-down callback used by both copy and dismiss animations. */
    private fun detachAfterAnimation() = tearDown()

    /**
     * Single chokepoint for overlay tear-down. Cancels every
     * running timer, removes the window from the
     * [WindowManager], clears every cross-component state field,
     * bumps [currentGen] so stale callbacks become no-ops, and
     * pumps the next queued OTP if any.
     */
    private fun tearDown() {
        cancelAutoCopy()
        cancelWatchdog()
        deferredCardAttach?.let { handler.removeCallbacks(it) }
        deferredCardAttach = null
        val root = overlayRoot
        overlayRoot = null
        reveal = null
        currentOtp = null
        copyInProgress = false
        queueUi.clear()
        currentGen++
        OtpAccessibilityService.setPendingOtp(null)
        if (root != null) {
            try {
                if (root.isAttachedToWindow) windowManager.removeView(root)
            } catch (_: IllegalArgumentException) {
                // removeView throws IllegalArgumentException when
                // the view is mid-detach on a different code path
                // (the view has already been removed from the
                // WindowManager) — legitimate no-op on dismiss races.
            }
        }
        // Drain the queue: even a watchdog-driven teardown should
        // let the rest of the burst surface rather than disappearing.
        handler.post(::pumpQueue)
    }

    /**
     * Copy + celebratory burst. The card stays on screen for the
     * duration of the copy animation, then auto-dismisses.
     *
     * If the queue panel is expanded, we first collapse it back to
     * the header height and only then play the Lottie burst. This
     * keeps the celebration visually centred over the card without
     * any per-frame relayout of the Lottie view.
     */
    private fun copyWithCelebrationInternal(otp: String, codeView: View) {
        if (overlayRoot == null) return
        // Re-entrancy guard: a second tap on Copy while the queue
        // is still collapsing would otherwise stack two bursts.
        if (copyInProgress) return
        copyInProgress = true
        copyToClipboard(otp)
        cancelAutoCopy()
        // Audio feedback fires immediately on tap so the success
        // chime stays tight to the user gesture even when we are
        // about to defer the visible Lottie behind a panel
        // collapse.
        playSound(currentSuccessId(), 1.0f)

        // Re-arm a fresh service-level safety so even the
        // copy → dismiss path has a guarantee of teardown. Lottie
        // can play up to ~8 s at 0.5x speed, plus shrink 280 ms
        // and dismiss 420 ms ≈ 9 s. The collapse adds another
        // ~400 ms when the panel was open, still well within
        // COPY_WATCHDOG_MS.
        cancelWatchdog()
        val watchdog = Runnable { removeOverlayImmediately() }
        watchdogRunnable = watchdog
        handler.postDelayed(watchdog, OverlayServiceConfig.COPY_WATCHDOG_MS)

        // Shrink the queue panel first so the Lottie lands on a
        // card that's the same shape as in the non-expanded case.
        // Returns 0 when nothing was collapsed → fast path.
        val collapseDelay = queueUi.collapsePanelIfExpanded()
        val myGen = currentGen
        val burst = Runnable {
            if (!isCurrent(myGen)) return@Runnable
            runCopyBurst(codeView)
        }
        if (collapseDelay <= 0L) {
            burst.run()
        } else {
            handler.postDelayed(burst, collapseDelay)
        }
    }

    /**
     * Drive the visible part of the copy celebration once the
     * card has settled into its post-collapse shape. Split out so
     * both the immediate and the post-collapse paths share the
     * exact same animation logic.
     */
    private fun runCopyBurst(codeView: View) {
        val r = reveal
        if (r != null) {
            val myGen = currentGen
            r.startCopyAnimation(codeView) {
                if (!isCurrent(myGen)) return@startCopyAnimation
                detachAfterAnimation()
            }
        } else {
            detachAfterAnimation()
        }
    }

    private fun registerScreenReceiver() {
        if (screenOffBridge == null) {
            screenOffBridge = ScreenOffBroadcastBridge(this, handler) {
                // Sustained screen-off — drop the queue and tear
                // down the visible overlay. The bridge has already
                // absorbed proximity / AOD glitches.
                queue.clear()
                removeOverlayImmediately()
            }
        }
        screenOffBridge?.register()
    }

    private fun unregisterScreenReceiver() {
        screenOffBridge?.unregister()
    }

    private fun createForegroundChannel() = ForegroundNotifier.createChannel(this)
    private fun buildForegroundNotification(): Notification = ForegroundNotifier.build(this)

    private fun initSoundPool() {
        val s = OverlaySounds(this)
        s.init()
        sounds = s
    }

    private fun currentSuccessId(): Int = sounds?.currentSuccessId() ?: 0
    private fun playSound(id: Int, volume: Float) {
        sounds?.playSound(id, volume)
    }

    private fun applyLocale() = LocaleHelper.applyToContext(this)

    // ── QueueUiHost / AutoPasteToastHost / OverlayCardHost ─────────
    // Direct overrides, no adapter classes. The three callback
    // surfaces overlap heavily; sharing them here keeps the boilerplate
    // out and lets the controllers stay agnostic of the host class.

    override fun handler(): Handler = handler
    override fun windowManager(): WindowManager = windowManager
    override fun currentGen(): Int = currentGen
    override fun isCurrent(gen: Int): Boolean = gen == currentGen && overlayRoot != null

    override fun reveal(): OtpRevealLayout? = reveal
    override fun setReveal(r: OtpRevealLayout?) { reveal = r }
    override fun overlayRoot(): View? = overlayRoot
    override fun setOverlayRoot(v: View?) { overlayRoot = v }

    override fun queueUi(): QueueUiController = queueUi
    override fun queue(): OverlayQueue = queue

    override fun resolveAppIcon(pkg: String?): Drawable? =
        AppIconResolver.resolveAppIcon(this, pkg)
    override fun pickTestPalette(seed: String?): Int =
        AppIconResolver.pickTestPalette(seed)
    override fun makeSyntheticAppIcon(sender: String?, seed: Int): Drawable =
        AppIconResolver.makeSyntheticAppIcon(resources, sender, seed)
    override fun dominantColor(icon: Drawable?, source: String?): Int =
        AppIconResolver.dominantColor(icon, source)

    override fun copyToClipboard(otp: String?) = OverlayClipboard.copy(this, otp)
    override fun vibrateLight() = OverlayHaptics.vibrateLight(this)

    override fun playPopSound() = playSound(sounds?.currentPopId() ?: 0, 1.0f)
    override fun playAutoPasteSound() {
        sounds?.playAutoPasteSound()
    }

    override fun copyWithCelebration(otp: String, codeView: View) =
        copyWithCelebrationInternal(otp, codeView)

    override fun showShade(otp: String, sender: String?, pkg: String?) {
        OtpShadeNotifier.show(this, otp, sender, pkg)
    }

    /** Tear down the overlay without running the dismiss animation. */
    override fun removeOverlayImmediately() = tearDown()

    /**
     * Cancel auto-copy + countdown only. Does NOT touch the hard
     * watchdog — the watchdog is a service-level last resort and
     * must outlive the auto-copy timer until the window is
     * actually torn down.
     */
    override fun cancelAutoCopy() {
        progressAnimator?.cancel()
        progressAnimator = null
        autoCopyRunnable?.let { handler.removeCallbacks(it) }
        autoCopyRunnable = null
        // Quickly retract the countdown line so it doesn't sit on
        // the card mid-shrink while the dismiss / copy animation
        // plays.
        reveal?.collapseCountdown(OverlayServiceConfig.COUNTDOWN_COLLAPSE_MS)
    }

    override fun cancelWatchdog() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    override fun setProgressAnimator(a: ValueAnimator?) { progressAnimator = a }
    override fun setAutoCopyRunnable(r: Runnable?) { autoCopyRunnable = r }
    override fun setWatchdogRunnable(r: Runnable?) { watchdogRunnable = r }
    override fun clearDeferredCardAttach() { deferredCardAttach = null }

    companion object {
        const val ACTION_SHOW_OTP: String = "com.midtano.otp.SHOW_OTP"
        const val ACTION_DISMISS: String = "com.midtano.otp.DISMISS"

        const val EXTRA_OTP: String = "otp"
        const val EXTRA_SENDER: String = "sender"
        const val EXTRA_SOURCE: String = "source"
        const val EXTRA_PKG: String = "pkg"

        // Source identifiers wired to the typed [OtpSource] enum so
        // the StatsActivity comparator and the OverlayService writer
        // never disagree on case.
        val SOURCE_SMS: String = OtpSource.SMS.storageId
        val SOURCE_PUSH: String = OtpSource.PUSH.storageId
        val SOURCE_TEST: String = OtpSource.TEST.storageId
    }
}
