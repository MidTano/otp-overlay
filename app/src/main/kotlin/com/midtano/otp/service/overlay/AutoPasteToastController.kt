// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.midtano.otp.R
import com.midtano.otp.data.Prefs
import com.midtano.otp.overlay.CopyLottiePool
import com.midtano.otp.overlay.OtpRevealLayout
import java.util.Random

/**
 * "Code inserted" pill shown when the accessibility service has
 * silently pasted the OTP. Owns the toast's layout inflation, brand
 * tinting, Lottie playback and dismissal timers.
 *
 * The controller does not own the overlay window — it asks the
 * [AutoPasteToastHost] to attach the inflated root view through
 * the host's [android.view.WindowManager] and to set the host's
 * `overlayRoot` / `reveal` fields so the rest of the service still
 * drives dismissal and watchdog.
 */
internal class AutoPasteToastController(
    ctx: Context,
    private val host: AutoPasteToastHost,
) {

    private val appCtx: Context = ctx.applicationContext

    /**
     * Inflate the toast, attach it to the window, kick off Lottie
     * playback, schedule dismiss + watchdog. The caller has already
     * cleared any prior overlay state.
     */
    fun show(otp: String, sender: String?, pkg: String?) {
        host.clearDeferredCardAttach()
        val inflater = LayoutInflater.from(appCtx)
        // null parent is intentional: the view attaches to a
        // WindowManager surface, not a ViewGroup. The root layout
        // params come from WindowManager.LayoutParams we build
        // separately, so the inflated layout's root attributes are
        // not needed.

        @Suppress("InflateParams")
        val overlayRoot = inflater.inflate(R.layout.overlay_autopaste, null)
        host.setOverlayRoot(overlayRoot)

        val reveal = overlayRoot.findViewById<OtpRevealLayout?>(R.id.reveal_layout)
        host.setReveal(reveal)
        // Compact rendering: thinner strokes plus a softer
        // multi-stop edge fade so the pill's bleed boundary never
        // reads as a visible rectangular outline.
        reveal?.setCompact(true)

        val ivApp = overlayRoot.findViewById<ImageView?>(R.id.iv_app_icon)
        val tvLabel = overlayRoot.findViewById<TextView?>(R.id.tv_autopaste_label)
        val tvCode = overlayRoot.findViewById<TextView?>(R.id.tv_autopaste_code)

        // Pull the same brand colour the big card uses so the
        // mini-pill's glow / outline tints to match the sender app.
        var appIcon: Drawable? = host.resolveAppIcon(pkg)
        var brand = host.dominantColor(appIcon, null)
        if (appIcon == null) {
            val seed = if (brand != 0) brand else host.pickTestPalette(sender)
            appIcon = host.makeSyntheticAppIcon(sender, seed)
            if (brand == 0) brand = seed
        }
        ivApp?.let {
            it.setImageDrawable(appIcon)
            it.visibility = View.VISIBLE
        }
        reveal?.setBrandColor(brand)
        tvLabel?.text = appCtx.getString(R.string.overlay_code_pasted)
        tvCode?.text = otp

        // Random Lottie from the same 9-animation pool the overlay
        // copy effect uses; recoloured to white so the brand glow
        // reads through it cleanly.
        val lottieAuto = overlayRoot.findViewById<LottieAnimationView?>(R.id.lottie_autopaste)
        val lottieDurationMs = setupLottie(lottieAuto)

        // WRAP_CONTENT width so the pill sizes to its content
        // (icon + label + digits) instead of stretching the whole
        // screen.
        val params = OverlayLayoutParams.buildToastParams(appCtx)
        try {
            host.windowManager().addView(overlayRoot, params)
        } catch (_: WindowManager.BadTokenException) {
            // SYSTEM_ALERT_WINDOW revoked, or token went stale on a
            // teardown race.
            host.setOverlayRoot(null)
            return
        } catch (_: IllegalStateException) {
            // OEM-specific "view already added" race during a
            // back-to-back paste.
            host.setOverlayRoot(null)
            return
        }

        host.vibrateLight()
        host.playAutoPasteSound()

        // No countdown on the toast — its lifetime is tied to a
        // fixed timer.
        reveal?.setCountdown(0f)

        // Pill duration = max(2000 ms, lottie length + 400 ms),
        // falling back to 2000 ms when Lottie is disabled.
        val pillDurationMs = if (lottieDurationMs > 0) {
            maxOf(DEFAULT_PILL_MS, lottieDurationMs + 400L)
        } else {
            DEFAULT_PILL_MS
        }

        val myGen = host.currentGen()
        val autoCopy = Runnable {
            if (!host.isCurrent(myGen)) return@Runnable
            host.dismissOverlay()
        }
        host.setAutoCopyRunnable(autoCopy)
        host.handler().postDelayed(autoCopy, pillDurationMs)

        val watchdog = Runnable {
            if (!host.isCurrent(myGen)) return@Runnable
            host.removeOverlayImmediately()
        }
        host.setWatchdogRunnable(watchdog)
        host.handler().postDelayed(watchdog, pillDurationMs + 2_000L)
    }

    /**
     * Configure the optional Lottie player. Returns the playback
     * length in milliseconds (used to compute the pill duration),
     * or 0 when Lottie is disabled.
     */
    private fun setupLottie(lottieAuto: LottieAnimationView?): Long {
        if (lottieAuto == null) return 0L
        if (!Prefs.isFxAutopasteLottieEn(appCtx)) {
            lottieAuto.visibility = View.GONE
            return 0L
        }
        val res = CopyLottiePool.RES[Random().nextInt(CopyLottiePool.RES.size)]
        lottieAuto.setAnimation(res)
        lottieAuto.repeatCount = 0
        val speed = Prefs.getFxCopyLottieSpeedFloat(appCtx)
        lottieAuto.speed = speed
        lottieAuto.visibility = View.VISIBLE
        lottieAuto.addLottieOnCompositionLoadedListener {
            lottieAuto.addValueCallback(KeyPath("**"), LottieProperty.COLOR, LottieValueCallback(Color.WHITE))
            lottieAuto.addValueCallback(KeyPath("**"), LottieProperty.STROKE_COLOR, LottieValueCallback(Color.WHITE))
            lottieAuto.progress = 0f
            lottieAuto.playAnimation()
        }
        // Lottie playback length scaled by the configured speed:
        // ~3000 ms at 1.0x shrinks to ~2000 ms at 1.5x.
        return (3000L / maxOf(0.5f, speed)).toLong()
    }

    private companion object {
        /** Auto-paste toast lifetime — long enough to read digits + see the icon. */
        const val DEFAULT_PILL_MS: Long = 2_000L
    }
}
