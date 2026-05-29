// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import com.midtano.otp.system.CrashLogger

/**
 * [FrameLayout] that intercepts the hardware / gesture BACK at the
 * `dispatchKeyEvent` level. Used by the OTP overlay so the user can
 * dismiss it with a swipe back.
 *
 * The listener fires exactly once. After the first BACK press the
 * listener is cleared AND the window is made non-focusable so any
 * subsequent key events (including repeated BACK during the dismiss
 * animation) pass straight through to the app below.
 */
class BackInterceptLayout @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    style: Int = 0,
) : FrameLayout(ctx, attrs, style) {

    fun interface BackListener {
        fun onBack()
    }

    private var listener: BackListener? = null

    fun setBackListener(l: BackListener?) {
        this.listener = l
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK &&
            event.action == KeyEvent.ACTION_UP &&
            listener != null
        ) {
            val l = listener
            listener = null
            // Drop focus so subsequent key events fall through to the
            // underlying app instead of being swallowed here.
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                if (wm != null && isAttachedToWindow) {
                    val lp = layoutParams as? WindowManager.LayoutParams
                    if (lp != null) {
                        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
                        wm.updateViewLayout(this, lp)
                    }
                }
            } catch (e: IllegalArgumentException) {
                // updateViewLayout throws IAE when the view is no
                // longer a known WindowManager child (mid-detach
                // race). Cosmetic — the BACK still propagates
                // because the listener fires below.
                CrashLogger.logErr("BackInterceptLayout: focus drop failed", e)
            }
            l?.onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
