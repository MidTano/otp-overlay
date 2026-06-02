// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.onboarding

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.midtano.otp.R
import com.midtano.otp.core.BaseActivity
import com.midtano.otp.data.Prefs
import com.midtano.otp.overlay.SuckInOverlayView
import com.midtano.otp.permissions.OnboardingPermissions
import com.midtano.otp.service.OverlayService
import com.midtano.otp.ui.main.MainActivity
import com.midtano.otp.ui.splash.SplashAnimTimings

/**
 * First-launch onboarding.
 *
 * Plays a brief Lottie splash, then surfaces four permission cards
 * with per-card "Grant" actions and a CTA that activates once the
 * three required permissions are in place.
 *
 * Required: SYSTEM_ALERT_WINDOW (overlay), SMS, Notification
 * Listener. Optional: Accessibility (auto-paste). Re-entering from
 * Settings keeps the same UI so the user can re-grant a revoked
 * permission.
 */
class OnboardingActivity : BaseActivity() {

    /** Permission cards shown in onboarding. */
    private enum class Card { OVERLAY, SMS, NOTIF, ACCESS }

    private lateinit var btnDone: Button

    private lateinit var cardOverlay: View
    private lateinit var cardSms: View
    private lateinit var cardNotif: View
    private lateinit var cardAccess: View

    /**
     * Single Handler used by the splash sequencing. Both the
     * background-suck-in and the Lottie-collapse posts go through
     * this handler so [onDestroy] can yank everything in flight if
     * the user backs out before the splash finishes.
     */
    private var splashHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        btnDone = findViewById(R.id.btn_onboarding_done)

        cardOverlay = findViewById(R.id.card_overlay)
        cardSms = findViewById(R.id.card_sms)
        cardNotif = findViewById(R.id.card_notif)
        cardAccess = findViewById(R.id.card_access)

        btnDone.setOnClickListener { finishOnboarding() }

        bindCardText(cardOverlay, Card.OVERLAY)
        bindCardText(cardSms, Card.SMS)
        bindCardText(cardNotif, Card.NOTIF)
        bindCardText(cardAccess, Card.ACCESS)

        cardOverlay.findViewById<View>(R.id.card_btn).setOnClickListener { requestOverlay() }
        cardSms.findViewById<View>(R.id.card_btn).setOnClickListener { requestSms() }
        cardNotif.findViewById<View>(R.id.card_btn).setOnClickListener { requestNotifAccess() }
        cardAccess.findViewById<View>(R.id.card_btn).setOnClickListener { requestAccessibility() }

        // Splash overlay choreography:
        // - cards_root is already visible underneath the overlay;
        // - Lottie plays over the dark background ([SuckInOverlayView]);
        // - the suck-in / collapse start times are derived from the
        //   Lottie composition duration via [SplashAnimTimings] so a
        //   future Lottie of a different length keeps the same
        //   visual rhythm.
        // - once the animator ends `splash_root` is removed and the
        //   cards become tappable.

        val lottie = findViewById<LottieAnimationView>(R.id.lottie_onboarding)
        val suckIn = findViewById<SuckInOverlayView>(R.id.suck_in_overlay)

        lottie.addLottieOnCompositionLoadedListener { composition ->
            // Recolour every layer to white.
            lottie.addValueCallback(KeyPath("**"), LottieProperty.COLOR, LottieValueCallback(Color.WHITE))
            lottie.addValueCallback(KeyPath("**"), LottieProperty.STROKE_COLOR, LottieValueCallback(Color.WHITE))

            val timings = SplashAnimTimings.forDuration(composition.duration.toLong())

            lottie.playAnimation()

            val handler = splashHandler ?: Handler(Looper.getMainLooper()).also { splashHandler = it }
            handler.postDelayed(
                { animateSuckIn(suckIn, timings.suckInDurationMs) },
                timings.suckInStartMs,
            )
            handler.postDelayed(
                { collapseLottie(lottie, timings.collapseDurationMs) },
                timings.collapseStartMs,
            )
        }

        lottie.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                findViewById<View?>(R.id.splash_root)?.visibility = View.GONE
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refreshCardStates()
    }

    override fun onDestroy() {
        // Cancel any in-flight splash sequencing so the
        // postDelayed callbacks cannot fire after finish.
        splashHandler?.removeCallbacksAndMessages(null)
        splashHandler = null
        super.onDestroy()
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

    private fun bindCardText(card: View, spec: Card) {
        val title = card.findViewById<TextView>(R.id.card_title)
        val desc = card.findViewById<TextView>(R.id.card_desc)
        val (titleStr, descStr) = when (spec) {
            Card.OVERLAY -> getString(R.string.perm_overlay_title) to getString(R.string.perm_overlay_desc)
            Card.SMS -> getString(R.string.perm_sms_title) to getString(R.string.perm_sms_desc)
            Card.NOTIF -> getString(R.string.perm_notif_title) to getString(R.string.perm_notif_desc)
            Card.ACCESS -> getString(R.string.perm_accessibility_title) to getString(R.string.perm_accessibility_desc)
        }
        title.text = titleStr
        desc.text = descStr
    }

    private fun refreshCardStates() {
        val overlayOk = OnboardingPermissions.isOverlayGranted(this)
        val smsOk = OnboardingPermissions.isSmsGranted(this)
        val notifOk = OnboardingPermissions.isNotificationListenerEnabled(this)
        val accessOk = OnboardingPermissions.isAccessibilityEnabled(this)

        bindCardStatus(cardOverlay, overlayOk, required = true)
        bindCardStatus(cardSms, smsOk, required = true)
        bindCardStatus(cardNotif, notifOk, required = true)
        bindCardStatus(cardAccess, accessOk, required = false)

        val canFinish = overlayOk && smsOk && notifOk
        btnDone.isEnabled = canFinish
        btnDone.alpha = if (canFinish) 1f else 0.55f

        // Persist the onboarding-done flag once all three required
        // permissions are in place. Without this, the user would
        // be redirected back here on every cold launch even after
        // granting.
        if (canFinish && !Prefs.isOnboardingDone(this)) {
            Prefs.setOnboardingDone(this, true)
        }
    }

    private fun bindCardStatus(card: View, granted: Boolean, required: Boolean) {
        val chip = card.findViewById<TextView>(R.id.card_chip)
        val btn = card.findViewById<Button>(R.id.card_btn)

        if (granted) {
            chip.text = getString(R.string.perm_chip_granted)
            chip.setTextColor(ContextCompat.getColor(this, R.color.onboarding_chip_dim))
            btn.text = getString(R.string.perm_granted)
            btn.setTextColor(ContextCompat.getColor(this, R.color.onboarding_btn_done))
            btn.isEnabled = false
            btn.background = ContextCompat.getDrawable(this, R.drawable.onboard_grant_btn_done)
        } else {
            chip.text = if (required) getString(R.string.perm_chip_required) else getString(R.string.perm_chip_optional)
            chip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (required) R.color.onboarding_chip_required else R.color.onboarding_chip_optional,
                ),
            )
            btn.text = getString(R.string.perm_allow)
            btn.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            btn.isEnabled = true
            btn.background = ContextCompat.getDrawable(this, R.drawable.onboard_grant_btn)
        }
    }

    private fun finishOnboarding() {
        Prefs.setOnboardingDone(this, true)
        // Start the overlay service so it is running once the user
        // exits the app.
        val svc = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, svc)
        val main = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // The user has just watched the onboarding intro
            // Lottie. Suppress the Main greeting so two
            // animations never stack on the same cold launch —
            // see MainActivity.setupSplashAnim and the rule
            // documented on SplashActivity.
            putExtra(MainActivity.EXTRA_SKIP_SPLASH_ANIM, true)
        }
        startActivity(main)
        finish()
    }

    private fun requestOverlay() = OnboardingPermissions.requestOverlay(this)
    private fun requestSms() = OnboardingPermissions.requestSms(this)
    private fun requestNotifAccess() = OnboardingPermissions.requestNotifAccess(this)
    private fun requestAccessibility() = OnboardingPermissions.requestAccessibility(this)

    override fun onRequestPermissionsResult(req: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(req, perms, results)
        if (req == OnboardingPermissions.REQ_SMS) {
            // After SMS is granted, try POST_NOTIFICATIONS on 13+.
            // Failure is non-fatal — the user just won't see our
            // foreground icon.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    OnboardingPermissions.REQ_POST_NOTIF,
                )
            }
        }
        refreshCardStates()
    }
}
