// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.View
import android.view.WindowManager
import com.midtano.otp.overlay.OtpRevealLayout

/**
 * Callback surface that [AutoPasteToastController] needs from its
 * hosting service.
 */
interface AutoPasteToastHost {

    /** Main-thread handler for `postDelayed` callbacks. */
    fun handler(): Handler

    /** Window manager used to attach the toast view. */
    fun windowManager(): WindowManager

    /** Current overlay-window generation counter. */
    fun currentGen(): Int

    /** True if the captured generation still represents the live overlay. */
    fun isCurrent(gen: Int): Boolean

    /** Run the dismiss animation then detach. */
    fun dismissOverlay()

    /** Tear down without playing the dismiss animation. */
    fun removeOverlayImmediately()

    /** Soft tactile confirmation buzz. */
    fun vibrateLight()

    /** Play the auto-paste SFX. */
    fun playAutoPasteSound()

    /** Resolve the source app's launcher icon, or `null`. */
    fun resolveAppIcon(pkg: String?): Drawable?

    /** Pick a deterministic palette colour for a synthetic icon. */
    fun pickTestPalette(seed: String?): Int

    /** Build the fallback "first letter on a coloured disc" icon. */
    fun makeSyntheticAppIcon(sender: String?, seed: Int): Drawable

    /**
     * Extract the dominant brand colour from [icon] (or fall back
     * to [pickTestPalette] when [icon] is `null`).
     */
    fun dominantColor(icon: Drawable?, source: String?): Int

    /** Current attached overlay view, or `null`. */
    fun overlayRoot(): View?

    /** Setter for `overlayRoot`. */
    fun setOverlayRoot(v: View?)

    /** Current [OtpRevealLayout] backing the toast. */
    fun reveal(): OtpRevealLayout?

    /** Setter for `reveal`. */
    fun setReveal(r: OtpRevealLayout?)

    /** Setter for the deferred attach Runnable (cleared by toast). */
    fun clearDeferredCardAttach()

    /** Setter for the auto-copy / dismiss Runnable. */
    fun setAutoCopyRunnable(r: Runnable?)

    /** Setter for the safety-watchdog Runnable. */
    fun setWatchdogRunnable(r: Runnable?)
}
