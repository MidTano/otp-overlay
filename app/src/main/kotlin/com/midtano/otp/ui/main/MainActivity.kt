// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.midtano.otp.BuildConfig
import com.midtano.otp.R
import com.midtano.otp.core.BaseActivity
import com.midtano.otp.data.Prefs
import com.midtano.otp.service.OverlayService
import com.midtano.otp.system.CrashLogger
import com.midtano.otp.ui.about.AboutDialog
import com.midtano.otp.ui.about.CreditsDialog
import com.midtano.otp.ui.onboarding.OnboardingActivity
import com.midtano.otp.ui.settings.SettingsActivity
import com.midtano.otp.ui.stats.StatsActivity

/**
 * App entry point.
 *
 * Shows the permission status row, a Settings shortcut and the OTP
 * preview tester. Redirects to [OnboardingActivity] on first
 * launch and whenever a required permission is missing.
 *
 * Cold-launch greeting:
 *   On every fresh start the activity plays a Lottie greeting
 *   (`lottie_splash` + one of the 12 `splash_msg_*` strings) layered
 *   above the main content. The choreography mirrors the onboarding
 *   intro so the two screens feel like a single design system. The
 *   actual animation lives in [MainSplashChoreographer]; this class
 *   only kicks it off and tears it down.
 *
 * The animation is suppressed via [EXTRA_SKIP_SPLASH_ANIM] when
 * MainActivity is started right after another animated transition
 * (post-crash dialog, finish-of-onboarding) so the user never sees
 * two greeting Lotties back-to-back.
 */
class MainActivity : BaseActivity() {

    private var statusText: TextView? = null
    private var etCode: EditText? = null
    private var etSender: EditText? = null
    private var versionLabel: TextView? = null

    /**
     * Single main-thread handler shared by both the splash greeting
     * choreography and the preview dispatch. All [postDelayed]
     * callbacks are cleared in [onDestroy] so a backed-out activity
     * cannot hit a recycled view.
     */
    private var mainHandler: Handler? = null

    /**
     * The single [Runnable] the preview path posts. Captured in a
     * field so [onDestroy] can yank it before the activity goes
     * away — without this, a [PREVIEW_DISPATCH_DELAY_MS] callback
     * could fire after `finish()` and resurrect a dead Activity
     * context.
     */
    private var previewDispatch: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Redirect before setContentView so the menu does not flash
        // before we bounce into onboarding.
        if (shouldShowOnboarding()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        etCode = findViewById(R.id.et_test_code)
        etSender = findViewById(R.id.et_test_sender)

        // Shrink the empty-field hint to ~13 sp so it does not
        // dominate the bold 22 sp typed input.
        try {
            etCode?.let { code ->
                code.hint?.let { hint ->
                    val sp = SpannableString(hint)
                    sp.setSpan(
                        AbsoluteSizeSpan(13, true),
                        0,
                        sp.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    code.hint = sp
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            // SpannableString.setSpan throws IOOBE if the layout
            // hint became shorter between the read and the span
            // attach. The plain hint is already on screen, so this
            // is purely cosmetic.
            CrashLogger.logErr("MainActivity: shrink hint span failed", e)
        }

        versionLabel = findViewById(R.id.tv_app_version)
        versionLabel?.text = getString(R.string.app_version_label, BuildConfig.VERSION_NAME)

        val buildInfo = findViewById<TextView?>(R.id.tv_build_info)
        buildInfo?.text = getString(
            R.string.main_build_info, BuildConfig.BUILD_ID, BuildConfig.BUILD_TIME,
        )
        // Easter egg: the technical-looking build line is also the
        // entry point into the About dialog. Wired through
        // setOnClickListener (not a layout `clickable=true`), so the
        // text never picks up a ripple, an underline, or a focus
        // outline — discoverable on touch, not advertised visually.
        // The supporting [tv_app_version] label opens the same
        // dialog so a user who taps the more obvious "v X.Y.Z"
        // first still gets there without an extra hunt.
        val openAbout = View.OnClickListener { AboutDialog.show(this) }
        buildInfo?.setOnClickListener(openAbout)
        versionLabel?.setOnClickListener(openAbout)

        // Same pattern for the third-party-emoji credits footer.
        // The chip is a tiny pill with a ripple + outline so it
        // reads as interactive without competing visually with the
        // primary CTAs above. Tapping it opens [CreditsDialog] with
        // the KawaiiEmoji attribution, the Telegram pack link and
        // an "if you are the author, file an issue" contact row.
        findViewById<View?>(R.id.tv_credits_footer)?.setOnClickListener {
            CreditsDialog.show(this)
        }

        findViewById<Button>(R.id.btn_open_onboarding).setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button?>(R.id.btn_stats)?.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_preview).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    getString(R.string.toast_overlay_permission),
                    Toast.LENGTH_LONG,
                ).show()
                return@setOnClickListener
            }
            val code = etCode?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: getString(R.string.preview_default_code)
            val sender = etSender?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: getString(R.string.preview_default_sender)
            previewOverlay(code, sender)
        }

        // Sample chips → preview overlay. Iterating a list keeps the four
        // identical wirings in one place so a fifth example is a one-line
        // addition.
        val samples = listOf(
            R.id.sample_4 to (R.string.preview_sample_code_4 to R.string.preview_sample_sender_a),
            R.id.sample_6 to (R.string.preview_sample_code_6 to R.string.preview_sample_sender_b),
            R.id.sample_8 to (R.string.preview_sample_code_8 to R.string.preview_sample_sender_c),
            R.id.sample_9 to (R.string.preview_sample_code_9 to R.string.preview_sample_sender_d),
        )
        for ((viewId, payload) in samples) {
            val (codeRes, senderRes) = payload
            findViewById<View?>(viewId)?.setOnClickListener {
                previewOverlay(getString(codeRes), getString(senderRes))
            }
        }

        setupSplashAnim()
    }

    override fun onResume() {
        super.onResume()
        // Bounce back into onboarding if a required permission was
        // revoked from system settings while we were paused.
        if (statusText != null && shouldShowOnboarding()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        if (statusText != null) updateStatus()
    }

    /**
     * Onboarding is required until the user has reached the end of
     * it AND all three required permissions are granted.
     */
    private fun shouldShowOnboarding(): Boolean {
        if (!Prefs.isOnboardingDone(this)) return true
        val overlayOk = Settings.canDrawOverlays(this)
        val smsOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
        val notifOk = isNotificationListenerEnabled()
        return !(overlayOk && smsOk && notifOk)
    }

    /**
     * Stage the cold-launch greeting overlay.
     *
     * If [EXTRA_SKIP_SPLASH_ANIM] is set on the launching intent
     * (post-crash dialog, finish-of-onboarding) we hide the splash
     * layer immediately and never play any animation — the rule is
     * one greeting per cold launch.
     *
     * Otherwise we mirror [com.midtano.otp.ui.onboarding.OnboardingActivity]'s
     * intro: pick a random `splash_msg_*`, fade it in, drive the
     * suck-in 400 ms before the Lottie ends, collapse the Lottie
     * 420 ms before the end, and dismiss the overlay on
     * `onAnimationEnd`.
     */
    private fun setupSplashAnim() {
        val handler = mainHandler ?: Handler(Looper.getMainLooper()).also { mainHandler = it }
        val skipAnim = intent?.getBooleanExtra(EXTRA_SKIP_SPLASH_ANIM, false) == true
        MainSplashChoreographer(this, handler).start(skipAnim)
    }

    private fun previewOverlay(code: String, sender: String) {
        etCode?.setText(code)
        etSender?.setText(sender)

        startOverlayService()
        // Brief delay so the foreground service is fully alive
        // before the SHOW_OTP intent reaches it. Use a field-level
        // handler so onDestroy can cancel any pending dispatch —
        // otherwise the callback can fire after the activity has
        // been finished and the lambda holds onto a dead Context.
        val handler = mainHandler ?: Handler(Looper.getMainLooper()).also { mainHandler = it }
        previewDispatch?.let { handler.removeCallbacks(it) }

        // Use the application context inside the lambda — once the
        // intent has been queued for delivery the Activity is no
        // longer needed, and using `this` in a postDelayed leaks
        // the Activity if the user rotates / finishes during the
        // gap.
        val appCtx = applicationContext
        val dispatch = Runnable {
            previewDispatch = null
            val i = Intent(appCtx, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW_OTP
                putExtra(OverlayService.EXTRA_OTP, code)
                putExtra(OverlayService.EXTRA_SENDER, sender)
                putExtra(OverlayService.EXTRA_SOURCE, OverlayService.SOURCE_TEST)
            }
            try {
                ContextCompat.startForegroundService(appCtx, i)
            } catch (t: SecurityException) {
                // SYSTEM_ALERT_WINDOW revoked between launch and tap.
                CrashLogger.logErr("MainActivity preview: startForegroundService denied", t)
            } catch (t: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException is a
                // subclass of IllegalStateException — fires when the
                // app is in background-restricted state. Never
                // crash the host activity over a preview.
                CrashLogger.logErr("MainActivity preview: startForegroundService blocked", t)
            }
        }
        previewDispatch = dispatch
        handler.postDelayed(dispatch, PREVIEW_DISPATCH_DELAY_MS)
    }

    override fun onDestroy() {
        // Yank every postDelayed callback (preview dispatch, splash
        // greeting choreography) so a backed-out activity cannot
        // hit a recycled view.
        mainHandler?.removeCallbacksAndMessages(null)
        mainHandler = null
        previewDispatch = null

        super.onDestroy()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return !flat.isNullOrEmpty() && flat.contains(packageName)
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val smsOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
        val pushOk = isNotificationListenerEnabled()

        bindStatusRow(R.id.iv_status_overlay, R.id.tv_status_overlay, overlayOk)
        bindStatusRow(R.id.iv_status_sms, R.id.tv_status_sms, smsOk)
        bindStatusRow(R.id.iv_status_push, R.id.tv_status_push, pushOk)

        val sb = StringBuilder()
            .append("Overlay:  ").append(if (overlayOk) "OK" else getString(R.string.status_no_permission)).append('\n')
            .append("SMS:      ").append(if (smsOk) "OK" else getString(R.string.status_no_permission)).append('\n')
            .append("Push:     ").append(if (pushOk) "OK" else getString(R.string.status_no_permission))

        if (overlayOk && (smsOk || pushOk)) {
            sb.append("\n\n").append(getString(R.string.status_ready))
            startOverlayService()
        }
        statusText?.text = sb
    }

    private fun bindStatusRow(iconId: Int, labelId: Int, ok: Boolean) {
        try {
            findViewById<ImageView?>(iconId)?.setImageResource(
                if (ok) R.drawable.ic_check_circle else R.drawable.ic_warning_circle,
            )
            findViewById<TextView?>(labelId)?.text = ""
        } catch (e: android.content.res.Resources.NotFoundException) {
            // setImageResource throws when the drawable was stripped
            // by R8's resource shrinker. Fall through silently — the
            // text label still conveys the permission state.
            CrashLogger.logErr("MainActivity.bindStatusRow failed", e)
        }
    }

    private fun startOverlayService() {
        val i = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, i)
    }

    companion object {
        /**
         * Set on the launching Intent to suppress the Lottie
         * greeting. Used when the user has already watched another
         * cold-launch animation (crash dialog, onboarding) so we do
         * not stack a second greeting on top of the first.
         */
        const val EXTRA_SKIP_SPLASH_ANIM = "extra_skip_splash_anim"

        /**
         * Delay between `startOverlayService()` and the first
         * `SHOW_OTP` intent on the in-app preview path. The gap
         * lets the foreground service finish `onCreate` /
         * `startForeground` so it can actually receive the intent.
         */
        private const val PREVIEW_DISPATCH_DELAY_MS = 250L
    }
}
