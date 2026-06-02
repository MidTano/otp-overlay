// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.splash

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.midtano.otp.R
import com.midtano.otp.core.BaseActivity
import com.midtano.otp.data.Prefs
import com.midtano.otp.overlay.SuckInOverlayView
import com.midtano.otp.system.CrashLogger
import com.midtano.otp.ui.main.MainActivity
import com.midtano.otp.ui.onboarding.OnboardingActivity

/**
 * Cold-launch entry point.
 *
 * Three exclusive paths, picked once on `onCreate`:
 *
 *  1. **Crash recovery** — a previous run wrote a crash report.
 *     Plays `lottie_crash.json` with the same suck-in + Lottie
 *     collapse choreography the rest of the app uses, then reveals
 *     the report-or-ignore panel underneath. After the user taps
 *     either button we forward to the next screen with
 *     [MainActivity.EXTRA_SKIP_SPLASH_ANIM] so the greeting Lottie
 *     does not stack on top of the crash one. Crash takes priority
 *     over everything else.
 *
 *  2. **Onboarding required** — first launch, or one of the three
 *     required permissions has been revoked. We forward to
 *     [OnboardingActivity] without playing any Lottie here.
 *     Onboarding will play its own permission-grant intro
 *     animation, so showing a greeting first would stack two
 *     Lotties back-to-back.
 *
 *  3. **Normal launch** — everything is granted and there is no
 *     pending crash. We forward straight to [MainActivity], which
 *     plays the greeting Lottie inline above its content.
 *
 * Decision matrix:
 *
 * |                  | OK perms | Missing perms |
 * |------------------|----------|----------------|
 * | No crash         | Main+anim| Onboarding+anim|
 * | After a crash    | Crash → Main (no greeting) | Crash → Onboarding (no greeting) |
 *
 * Rule: at most one Lottie ever plays per cold launch path —
 * except when a crash forces a second screen to follow, in which
 * case the second screen suppresses its greeting via
 * [MainActivity.EXTRA_SKIP_SPLASH_ANIM].
 *
 * `CustomSplashScreen` lint warning is suppressed because we
 * deliberately keep a custom splash to host the crash dialog and
 * a Lottie animation that the system splash API does not support.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {

    /**
     * The splash draws its Lottie edge-to-edge (the system status
     * and nav bars are transparent; the dark background shows
     * through). Padding the content view would cap the Lottie
     * before it reaches the screen edges.
     */
    override fun shouldApplySystemBarsInsets(): Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private var transitioned: Boolean = false

    /**
     * Captured at `onCreate` time so that the navigation decision
     * stays consistent across the lifetime of this Activity. If the
     * user toggles a permission while looking at the crash dialog
     * the next-screen choice still reflects the state we observed
     * at launch.
     */
    private var nextDestinationNeedsOnboarding: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        // The crash check has to win over onboarding: if we crashed
        // last run AND the user is missing permissions, they get
        // the crash dialog first and only then the permissions
        // screen — explicitly per the product rule.
        val afterCrash = DEBUG_FORCE_CRASH_SCREEN || CrashLogger.hasRecentCrash(this)
        nextDestinationNeedsOnboarding = needsOnboarding()

        if (!afterCrash) {
            // No crash to surface — skip the splash UI entirely
            // and let the next screen own the cold-launch
            // animation. This is what enforces "exactly one
            // greeting Lottie per cold launch" without the user
            // ever seeing this Activity flicker.
            routeWithoutSplashUi()
            return
        }

        // Crash mode: we keep the existing crash UI as the priority
        // animation. The crash Lottie + report panel still owns the
        // first frame.
        setContentView(R.layout.activity_splash)
        showCrashUi()
    }

    /**
     * Direct hand-off to the next screen with no UI of our own.
     * The next screen plays its single greeting/permission Lottie
     * and we vanish behind a fade.
     */
    private fun routeWithoutSplashUi() {
        // No setContentView — the activity stays at its window
        // background colour for the brief moment before we finish.
        // BaseActivity already wraps the locale and configures the
        // theme, so a black flash is fine.
        goNext(skipMainAnim = false)
    }

    private fun showCrashUi() {
        val splashRoot = findViewById<View?>(R.id.splash_root)
        val crashPanelRoot = findViewById<View?>(R.id.crash_panel_root)
        val lottie = findViewById<LottieAnimationView?>(R.id.lottie_splash)
        val suckIn = findViewById<SuckInOverlayView?>(R.id.suck_in_overlay)
        val tvCrashTitle = findViewById<android.widget.TextView?>(R.id.tv_crash_title)
        val tvCrashSubtitle = findViewById<android.widget.TextView?>(R.id.tv_crash_subtitle)
        val btnReport = findViewById<View?>(R.id.btn_crash_report)
        val btnIgnore = findViewById<Button?>(R.id.btn_crash_ignore)

        // Crash mode: play the crash Lottie, the panel sits beneath
        // and is revealed as the suck-in punches a hole through
        // the dark overlay.
        //
        // Do not clear the crash file until the user picks an
        // action — otherwise "Report on GitHub" would copy an
        // empty payload to the clipboard.
        tvCrashTitle?.setText(R.string.crash_title)
        tvCrashSubtitle?.setText(R.string.crash_subtitle)

        // Panel starts faded out so it fades up gently as the
        // overlay collapses. It's already laid out in the
        // background, so the suck-in already reveals its
        // *position* — the alpha tween only smooths the entrance
        // of the title/buttons.
        crashPanelRoot?.alpha = 0f

        btnReport?.setOnClickListener {
            copyCrashInfoToClipboard()
            if (!DEBUG_FORCE_CRASH_SCREEN) CrashLogger.clear(this)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, getString(R.string.url_github_issues).toUri()))
            } catch (e: android.content.ActivityNotFoundException) {
                // Browser missing on the device — rare on AOSP-shaped
                // builds without a default browser. The crash report
                // is already on the clipboard, so the user can paste
                // it manually if they go find one.
                CrashLogger.logErr("SplashActivity: open GitHub issues failed", e)
            }
            // After the crash dialog the user has already
            // seen one cold-launch animation. Suppress the
            // greeting on Main but keep onboarding's intro
            // (it still plays its permission Lottie if the
            // route is OnboardingActivity, since that one
            // is the *only* animation onboarding ever
            // plays).
            goNext(skipMainAnim = true)
        }
        btnIgnore?.let { ignore ->
            ignore.setText(R.string.crash_btn_ignore)
            ignore.setOnClickListener {
                if (!DEBUG_FORCE_CRASH_SCREEN) CrashLogger.clear(this)
                goNext(skipMainAnim = true)
            }
        }

        if (lottie == null) {
            // No Lottie — surface the panel immediately so the
            // user is never trapped staring at the dark overlay.
            splashRoot?.visibility = View.GONE
            crashPanelRoot?.alpha = 1f
            return
        }

        // 60-second hard fallback: if the user neither taps a
        // button nor the activity reaches its own timeout, forward
        // anyway so they aren't stuck. Skip-greeting flag is on
        // because by then they've watched the crash Lottie.
        handler.postDelayed({ goNext(skipMainAnim = true) }, CRASH_DIALOG_TIMEOUT_MS)

        lottie.setAnimation(R.raw.lottie_crash)
        lottie.repeatCount = 0

        lottie.addLottieOnCompositionLoadedListener { composition ->
            // Recolour every layer to white so the crash Lottie
            // matches the rest of the app's monochrome intro
            // language.
            lottie.addValueCallback(KeyPath("**"), LottieProperty.COLOR, LottieValueCallback(Color.WHITE))
            lottie.addValueCallback(KeyPath("**"), LottieProperty.STROKE_COLOR, LottieValueCallback(Color.WHITE))

            val timings = SplashAnimTimings.forDuration(composition.duration.toLong())

            lottie.progress = 0f
            lottie.playAnimation()

            // Clear any pre-load callbacks (we don't post any in
            // this branch but stay defensive in case a future
            // edit adds one before composition is loaded).
            handler.removeCallbacksAndMessages(null)
            // Re-arm the dialog timeout we removed above.
            handler.postDelayed({ goNext(skipMainAnim = true) }, CRASH_DIALOG_TIMEOUT_MS)

            // Suck-in / collapse driven by composition fractions —
            // identical pacing to OnboardingActivity and
            // MainActivity's greeting.
            handler.postDelayed(
                { animateSuckIn(suckIn, timings.suckInDurationMs) },
                timings.suckInStartMs,
            )
            handler.postDelayed(
                { collapseLottie(lottie, timings.collapseDurationMs) },
                timings.collapseStartMs,
            )

            // Fade in the crash panel content slightly later than
            // the suck-in start so the title doesn't pop in
            // before the dark overlay has begun retreating. Lands
            // at the same moment the Lottie collapses to zero, so
            // the panel takes over the centre of the screen.
            val panelFadeDelay = (
                (timings.suckInStartMs + timings.collapseStartMs) / 2L
                ).coerceAtLeast(0L)
            handler.postDelayed({
                crashPanelRoot?.let { panel ->
                    panel.animate()
                        .alpha(1f)
                        .setDuration(timings.textFadeDurationMs)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
            }, panelFadeDelay)
        }

        // Once the Lottie animator ends, hide the splash overlay
        // entirely so taps reach the buttons beneath. The panel
        // alpha tween above guarantees the buttons are visible by
        // then.
        lottie.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                splashRoot?.visibility = View.GONE
                crashPanelRoot?.alpha = 1f
            }
        })
    }

    /** Animate the dark background sucking toward the centre. */
    private fun animateSuckIn(suckIn: SuckInOverlayView?, durationMs: Long) {
        if (suckIn == null) return
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = PathInterpolator(0.4f, 0f, 0.6f, 1f)
            addUpdateListener { suckIn.setProgress(it.animatedValue as Float) }
            start()
        }
    }

    /** Collapse the Lottie animation toward the centre (scale 1 → 0). */
    private fun collapseLottie(lottie: LottieAnimationView?, durationMs: Long) {
        if (lottie == null) return
        lottie.animate()
            .scaleX(0f)
            .scaleY(0f)
            .setDuration(durationMs)
            .setInterpolator(PathInterpolator(0.4f, 0f, 1f, 1f))
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun copyCrashInfoToClipboard() {
        CrashReportClipboard.copy(this)
    }

    /**
     * Forward to the next screen.
     *
     * @param skipMainAnim when `true`, the destination Activity
     *   should suppress its own greeting Lottie. We pass it via
     *   [MainActivity.EXTRA_SKIP_SPLASH_ANIM] when forwarding to
     *   [MainActivity]; the onboarding screen always plays its
     *   single permission animation, so the flag is irrelevant
     *   there.
     */
    private fun goNext(skipMainAnim: Boolean) {
        if (transitioned || isFinishing || isDestroyed) return
        transitioned = true

        val next = if (nextDestinationNeedsOnboarding) {
            Intent(this, OnboardingActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java).apply {
                if (skipMainAnim) putExtra(MainActivity.EXTRA_SKIP_SPLASH_ANIM, true)
            }
        }
        startActivity(next)

        // The typed replacement (overrideActivityTransition) only
        // exists on API 34+; gate so newer builds use the typed API
        // and pre-34 falls back to the older overridePendingTransition
        // call.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()
    }

    /**
     * `true` when the user must first complete onboarding — either
     * because they have never finished it or because one of the
     * required permissions was revoked from system settings.
     *
     * Mirrors the same predicate [MainActivity] uses so the routing
     * decision here matches what Main would do anyway.
     */
    private fun needsOnboarding(): Boolean {
        if (!Prefs.isOnboardingDone(this)) return true
        val overlayOk = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        val smsOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
        val notifOk = isNotificationListenerEnabled(this)
        return !(overlayOk && smsOk && notifOk)
    }

    private fun isNotificationListenerEnabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(
            ctx.contentResolver,
            "enabled_notification_listeners",
        )
        return !flat.isNullOrEmpty() && flat.contains(ctx.packageName)
    }

    private companion object {
        /**
         * Set to `true` during development to always show the
         * crash screen regardless of actual crash state. Must be
         * `false` for any release build.
         */
        const val DEBUG_FORCE_CRASH_SCREEN = false

        /**
         * Hard upper bound on the crash dialog. If neither button
         * is tapped within this window we forward anyway so the
         * user is never trapped here.
         */
        const val CRASH_DIALOG_TIMEOUT_MS: Long = 60_000L
    }
}
