// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.View
import com.midtano.otp.overlay.OtpRevealLayout

/**
 * Callback surface that [QueueUiController] needs from its hosting
 * service. Defined as an interface so the controller never depends
 * on the host class directly — the dependency graph stays one-way
 * and tests can stub a host.
 */
interface QueueUiHost {

    /** Main-thread handler used for `postDelayed` watchdog scheduling. */
    fun handler(): Handler

    /**
     * Current overlay-window generation counter. The controller
     * stamps delayed callbacks with this so a stale watchdog cannot
     * tear down a freshly-attached overlay.
     */
    fun currentGen(): Int

    /** True if the captured generation still represents the live overlay. */
    fun isCurrent(gen: Int): Boolean

    /** Cancel the auto-copy progress and runnable. */
    fun cancelAutoCopy()

    /** Cancel the safety-watchdog runnable. */
    fun cancelWatchdog()

    /** Tear down the overlay window without playing the dismiss animation. */
    fun removeOverlayImmediately()

    /** The [OtpRevealLayout] hosting the card, or `null`. */
    fun reveal(): OtpRevealLayout?

    /** The outer overlay container view, or `null`. */
    fun overlayRoot(): View?

    /** Resolve the source app's launcher icon, or `null`. */
    fun resolveAppIcon(pkg: String?): Drawable?

    /** Pick a deterministic palette colour for a synthetic icon. */
    fun pickTestPalette(seed: String?): Int

    /** Build the fallback "first letter on a coloured disc" icon. */
    fun makeSyntheticAppIcon(sender: String?, seed: Int): Drawable

    /** Copy [otp] onto the system clipboard. */
    fun copyToClipboard(otp: String?)

    /** Backing queue (live; mutated as rows are popped). */
    fun queue(): OverlayQueue

    /** Watchdog field setter. The controller can post / re-arm a watchdog through this. */
    fun setWatchdogRunnable(r: Runnable?)
}
