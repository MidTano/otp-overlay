// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import androidx.core.content.ContextCompat

/**
 * Owns the `SCREEN_OFF` / `SCREEN_ON` broadcast lifecycle for
 * [com.midtano.otp.service.OverlayService].
 *
 * Behaviour:
 * - `SCREEN_OFF` schedules a teardown after
 *   [OverlayServiceConfig.SCREEN_OFF_DEBOUNCE_MS] on the supplied
 *   [Handler]. The debounce window absorbs proximity-sensor / AOD
 *   transitions, so a phone call doesn't blow away the queued
 *   overlays.
 * - `SCREEN_ON` cancels any pending teardown — the off event was
 *   transient.
 * - [unregister] also cancels any pending teardown so a service
 *   stop while debouncing doesn't leak the [Runnable].
 *
 * The bridge does not own the action that runs on screen-off.
 * Callers supply it through [Listener.onScreenOffSettled].
 */
internal class ScreenOffBroadcastBridge(
    context: Context,
    private val handler: Handler,
    private val listener: Listener,
) {

    /** Settled callback — invoked on the supplied handler thread. */
    fun interface Listener {
        /** Fired once the debounce window elapsed without a SCREEN_ON. */
        fun onScreenOffSettled()
    }

    private val context: Context = context.applicationContext

    private var receiver: BroadcastReceiver? = null
    private var pendingTeardown: Runnable? = null

    /** Register the underlying broadcast receiver. Idempotent. */
    fun register() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> handler.post(::scheduleTeardown)
                    Intent.ACTION_SCREEN_ON -> handler.post(::cancelTeardown)
                }
            }
        }
        receiver = r
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Tear down the receiver and cancel any pending settled
     * callback. Safe to call when [register] was never invoked.
     */
    fun unregister() {
        receiver?.let {
            // unregisterReceiver throws when the receiver was never
            // registered (e.g. unregister twice during teardown);
            // that path is the correct outcome.
            try { context.unregisterReceiver(it) } catch (_: IllegalArgumentException) {}
        }
        receiver = null
        cancelTeardown()
    }

    private fun scheduleTeardown() {
        pendingTeardown?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            pendingTeardown = null
            listener.onScreenOffSettled()
        }
        pendingTeardown = r
        handler.postDelayed(r, OverlayServiceConfig.SCREEN_OFF_DEBOUNCE_MS)
    }

    private fun cancelTeardown() {
        pendingTeardown?.let { handler.removeCallbacks(it) }
        pendingTeardown = null
    }
}
