// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.View
import android.view.WindowManager
import com.midtano.otp.overlay.OtpRevealLayout

/**
 * Callback surface that [OverlayCardPresenter] needs from its
 * hosting service to inflate, attach and tear down the OTP card
 * window.
 */
internal interface OverlayCardHost {
    fun handler(): Handler
    fun windowManager(): WindowManager
    fun currentGen(): Int
    fun isCurrent(gen: Int): Boolean

    fun setOverlayRoot(v: View?)
    fun reveal(): OtpRevealLayout?
    fun setReveal(r: OtpRevealLayout?)

    fun queueUi(): QueueUiController
    fun queue(): OverlayQueue
    fun resolveAppIcon(pkg: String?): Drawable?
    fun makeSyntheticAppIcon(sender: String?, seed: Int): Drawable
    fun dominantColor(icon: Drawable?, source: String?): Int

    fun copyWithCelebration(otp: String, codeView: View)
    fun dismissOverlay()
    fun showShade(otp: String, sender: String?, pkg: String?)
    fun playPopSound()

    fun setProgressAnimator(a: android.animation.ValueAnimator?)
    fun setAutoCopyRunnable(r: Runnable?)
    fun setWatchdogRunnable(r: Runnable?)
    fun removeOverlayImmediately()
}
