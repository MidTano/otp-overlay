// SPDX-License-Identifier: MIT
//
// AccessibilityNodeInfo.recycle() became a no-op on API 33+ (object
// pooling was discontinued), but we still target API 31/32 where
// the pool exists, so the calls have to stay.
@file:Suppress("DEPRECATION")

package com.midtano.otp.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.midtano.otp.data.Prefs
import com.midtano.otp.system.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * Auto-pastes the latest OTP into a focused editable field.
 *
 * Two flows:
 * 1. **Immediate** — when the overlay appears the service calls
 *    [pasteNow]. If a code field already has focus we paste right
 *    away.
 * 2. **On-event** — otherwise the OTP is kept in [pendingOtpRef] and
 *    we paste the next time any accessibility event fires (focus,
 *    window change, click) and finds an editable field.
 *
 * The pending OTP is auto-cleared [PENDING_TTL_MS] after it was
 * set, so an OTP nobody pastes does not linger in process memory.
 *
 * Smart-paste mode (default ON): only pastes into fields that look
 * like OTP / code inputs — by `autofillHints`, hint text,
 * `viewIdResourceName`, or `inputType + maxLength` heuristics.
 * Detection lives in [OtpFieldFinder]; the actual paste mechanics
 * live in [AccessibilityPaster].
 */
class OtpAccessibilityService : AccessibilityService() {

    private var mainHandler: Handler? = null

    private val pasteRetry: Runnable = Runnable {
        val otp = peekPendingOtp() ?: return@Runnable
        val result = pasteNow(otp)
        if (result.isSuccess()) {
            setPendingOtp(null)
            fireDismiss()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef.set(WeakReference(this))
        mainHandler = Handler(Looper.getMainLooper())
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceRef.set(WeakReference(null))
        setPendingOtp(null)
    }

    /**
     * Paste the OTP immediately if any editable field is focused or
     * available.
     */
    fun pasteNow(otp: String?): PasteResult {
        if (otp.isNullOrEmpty()) return PasteResult.NO_OTP
        val smart = Prefs.isSmartPaste(this)
        CrashLogger.log("pasteNow invoked (smart=$smart)")

        // Try the active window first.
        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            CrashLogger.logErr("getRootInActiveWindow failed", e)
            null
        }
        if (root != null) {
            val result = tryPasteInTree(root, otp, smart)
            recycleQuietly(root)
            if (result != null) return result
        }

        // Fall back to walking every window (split-screen / multi-window).
        try {
            val ws = windows
            if (ws != null) {
                for (w in ws) {
                    if (w == null) continue
                    val r = try {
                        w.root
                    } catch (e: Exception) {
                        CrashLogger.logErr("AccessibilityWindowInfo.getRoot failed", e)
                        null
                    } ?: continue
                    val result = tryPasteInTree(r, otp, smart)
                    recycleQuietly(r)
                    if (result != null) return result
                }
            }
        } catch (e: Exception) {
            CrashLogger.logErr("getWindows() walk failed", e)
        }

        return PasteResult.NO_EDITABLE_FIELD
    }

    /**
     * Try to paste into a tree rooted at [root].
     *
     * @return paste outcome, or `null` if nothing matched and the
     *         caller should keep walking other roots.
     */
    private fun tryPasteInTree(
        root: AccessibilityNodeInfo,
        otp: String,
        smart: Boolean,
    ): PasteResult? {
        if (smart) {
            // Smart mode: look for an OTP-specific field first.
            val otpField = OtpFieldFinder.findOtpField(root)
            if (otpField != null) {
                logTargetNode("smart-detected", otpField)
                try {
                    otpField.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                } catch (e: Exception) {
                    CrashLogger.logErr("smart focus failed", e)
                }
                val ok = AccessibilityPaster.pasteIntoNode(this, otpField, otp)
                if (otpField !== root) recycleQuietly(otpField)
                return if (ok) {
                    PasteResult.PASTED_SMART_DETECTED
                } else {
                    PasteResult.SMART_FOUND_PASTE_FAILED
                }
            }
            // Smart-mode fallback: focused editable that looks OTP-shaped.
            val focused = OtpFieldFinder.findFocusedEditable(root)
            if (focused != null && OtpFieldFinder.isLikelyOtpField(focused)) {
                logTargetNode("smart-focused", focused)
                val ok = AccessibilityPaster.pasteIntoNode(this, focused, otp)
                if (focused !== root) recycleQuietly(focused)
                return if (ok) {
                    PasteResult.PASTED_SMART_FOCUSED
                } else {
                    PasteResult.SMART_FOCUSED_PASTE_FAILED
                }
            }
            if (focused != null && focused !== root) recycleQuietly(focused)
            return null
        }

        // Non-smart mode: paste into any focused or first editable —
        // EXCEPT fields that the blocklist explicitly rejects. The
        // user opted out of smart matching, but never out of the
        // "do not stuff OTP into the URL bar / search box / email"
        // safety. The negative-marker check is fast and never
        // false-positives on a real OTP field, so it is safe to
        // apply unconditionally.
        val focused = OtpFieldFinder.findFocusedEditable(root)
        if (focused != null) {
            if (OtpFieldFinder.isObviouslyNotOtpField(focused)) {
                logTargetNode("focused-blocklisted", focused)
                if (focused !== root) recycleQuietly(focused)
                return PasteResult.NO_SMART_MATCH
            }
            logTargetNode("focused", focused)
            val ok = AccessibilityPaster.pasteIntoNode(this, focused, otp)
            if (focused !== root) recycleQuietly(focused)
            return if (ok) PasteResult.PASTED_FOCUSED else PasteResult.FOCUSED_PASTE_FAILED
        }
        val any = OtpFieldFinder.findAnyEditable(root)
        if (any != null) {
            if (OtpFieldFinder.isObviouslyNotOtpField(any)) {
                logTargetNode("first-editable-blocklisted", any)
                if (any !== root) recycleQuietly(any)
                return PasteResult.NO_SMART_MATCH
            }
            logTargetNode("first-editable-fallback", any)
            try {
                any.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            } catch (e: Exception) {
                CrashLogger.logErr("any-editable focus failed", e)
            }
            val ok = AccessibilityPaster.pasteIntoNode(this, any, otp)
            if (any !== root) recycleQuietly(any)
            return if (ok) {
                PasteResult.PASTED_FIRST_EDITABLE
            } else {
                PasteResult.FIRST_EDITABLE_PASTE_FAILED
            }
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val otp = peekPendingOtp() ?: return
        try {
            if (!Prefs.isAutoPaste(this)) return
        } catch (e: Exception) {
            CrashLogger.logErr("isAutoPaste check failed", e)
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> Unit
            else -> return
        }

        val smart = Prefs.isSmartPaste(this)

        // Try the event source first (cheap, no tree walk).
        val source = try {
            event.source
        } catch (e: Exception) {
            CrashLogger.logErr("event.getSource failed", e)
            null
        }
        if (source != null && OtpFieldFinder.isUsableEditable(source)) {
            // Even in non-smart mode the negative blocklist
            // applies — the user opted out of strict positive
            // matching, not out of the "do not stuff OTP into
            // URL / search / email / password" safety. Without
            // this check a focus event from Chrome's url_bar
            // would silently push the OTP into the address bar.
            val rejectedByBlocklist = OtpFieldFinder.isObviouslyNotOtpField(source)
            val acceptable = !rejectedByBlocklist &&
                (!smart || OtpFieldFinder.isLikelyOtpField(source))
            if (acceptable) {
                setPendingOtp(null)
                AccessibilityPaster.pasteIntoNode(this, source, otp)
                recycleQuietly(source)
                fireDismiss()
                return
            }
        }
        if (source != null) recycleQuietly(source)

        // Fall back to a window walk after a short debounce.
        val handler = mainHandler ?: Handler(Looper.getMainLooper()).also { mainHandler = it }
        handler.removeCallbacks(pasteRetry)
        handler.postDelayed(pasteRetry, PASTE_RETRY_DEBOUNCE_MS)
    }

    private fun fireDismiss() {
        try {
            val close = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_DISMISS
            }
            ContextCompat.startForegroundService(this, close)
        } catch (e: Exception) {
            CrashLogger.logErr("fireDismiss startForegroundService failed", e)
        }
    }

    override fun onInterrupt() { /* no-op */ }

    companion object {

        /**
         * Maximum lifetime of a pending OTP. Two minutes covers the
         * slowest realistic auto-paste flow (user sees the overlay,
         * opens the target app, focuses the field) and is well
         * shorter than any sensible OTP validity.
         */
        const val PENDING_TTL_MS: Long = 2L * 60L * 1000L

        /**
         * Debounce window between an "event.getSource() didn't help"
         * conclusion and the first window-walk fallback. Short
         * enough that the user perceives the paste as instant, long
         * enough that focus / window-state events arriving in a
         * burst (Samsung keyboard up-down spam, MIUI animation
         * events) coalesce into one tree walk instead of dozens.
         */
        private const val PASTE_RETRY_DEBOUNCE_MS: Long = 80L

        /**
         * Latest OTP awaiting the next focus event. Held in an
         * [AtomicReference] because it crosses thread boundaries
         * (broadcast receiver writes, accessibility thread reads).
         */
        private val pendingOtpRef: AtomicReference<String?> = AtomicReference(null)

        /**
         * Live reference to the running service so [OverlayService]
         * can call [pasteNow] without going through an intent
         * round-trip. Held as a [WeakReference] inside an
         * [AtomicReference] so [onDestroy] nulls it deterministically
         * and a stale lookup between [onDestroy] and the GC cycle
         * still returns `null` instead of a half-dead Context.
         */
        private val instanceRef: AtomicReference<WeakReference<OtpAccessibilityService>> =
            AtomicReference(WeakReference(null))

        /**
         * Process-wide scope for the pending-OTP TTL timer.
         * Uses [SupervisorJob] so a single failed delay can never
         * poison subsequent timers.
         *
         * Intentionally NOT cancelled in [onDestroy] — Android can
         * tear an accessibility service down and re-bind it within
         * the same process for OEM-specific reasons (system UI
         * theme reload, accessibility-volume gestures), and a
         * pending OTP set just before the rebind must still be
         * pasted on the next event. The scope lives for the lifetime
         * of the process and is GCed when the process is itself
         * killed.
         */
        private val ttlScope: CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Currently-armed TTL coroutine. Replaced atomically by every
         * [setPendingOtp] call so a re-arm cancels the previous
         * timer without losing the new value to a racing clear.
         */
        private val ttlJobRef: AtomicReference<Job?> = AtomicReference(null)

        /**
         * Read-only accessor for the current accessibility service
         * instance, or `null` if the service is not running or its
         * weak reference has been cleared.
         */
        fun peekInstance(): OtpAccessibilityService? = instanceRef.get().get()

        /**
         * Set the pending OTP and arm the TTL coroutine. Pass `null`
         * to immediately clear and disarm.
         */
        fun setPendingOtp(otp: String?) {
            pendingOtpRef.set(otp)
            ttlJobRef.getAndSet(null)?.cancel()
            if (!otp.isNullOrEmpty()) {
                val job = ttlScope.launch {
                    delay(PENDING_TTL_MS)
                    // CAS against the OTP we armed for — a fresh
                    // setPendingOtp(other) between launch and delay
                    // expiry must not have its new value erased.
                    pendingOtpRef.compareAndSet(otp, null)
                }
                ttlJobRef.set(job)
            }
        }

        /** Read the currently pending OTP, or `null` if none. */
        fun peekPendingOtp(): String? = pendingOtpRef.get()

        /**
         * Audit-trail log line for an auto-paste target. The OTP
         * value itself is never written; only the field metadata is.
         */
        private fun logTargetNode(reason: String, node: AccessibilityNodeInfo?) {
            if (node == null) return
            val viewId = try {
                node.viewIdResourceName
            } catch (_: Exception) {
                // Privileged accessibility frameworks throw here —
                // best-effort; we log "?" instead.
                null
            }
            val pkg = try {
                node.packageName?.toString()
            } catch (_: Exception) {
                null
            }
            CrashLogger.log(
                "autopaste target[$reason] pkg=${pkg ?: "?"} viewId=${viewId ?: "?"}",
            )
        }

        private fun recycleQuietly(node: AccessibilityNodeInfo?) {
            if (node == null) return
            try {
                node.recycle()
            } catch (_: Exception) {
                // recycle() throws on an already-recycled node —
                // harmless, not worth logging.
            }
        }
    }
}
