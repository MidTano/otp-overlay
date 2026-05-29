// SPDX-License-Identifier: MIT
package com.midtano.otp.service

/**
 * Strongly-typed outcome of an auto-paste attempt.
 *
 * Each constant carries a stable diagnostic message that the
 * `OverlayService` "Last action" pane surfaces, plus an [isSuccess]
 * flag the call site checks before branching the success / failure
 * path.
 */
enum class PasteResult(private val message: String) {

    /** Empty / null OTP — caller should not have called us. */
    NO_OTP("no otp"),

    /** Smart mode pasted into an OTP-shaped field. */
    PASTED_SMART_DETECTED("pasted (smart: OTP field detected)"),

    /** Smart mode found an OTP-shaped field but the paste call failed. */
    SMART_FOUND_PASTE_FAILED("found OTP field but paste failed"),

    /** Smart mode pasted into the focused field after heuristic confirm. */
    PASTED_SMART_FOCUSED("pasted (smart: focused field matches OTP heuristic)"),

    /** Smart mode focused the right field but the paste call failed. */
    SMART_FOCUSED_PASTE_FAILED("focused OTP-like field but paste failed"),

    /** Non-smart mode pasted into the currently focused editable. */
    PASTED_FOCUSED("pasted (focused editable)"),

    /** Non-smart mode found a focused editable but the paste call failed. */
    FOCUSED_PASTE_FAILED("found focused editable but paste failed"),

    /** Non-smart mode pasted into the first editable field in the tree. */
    PASTED_FIRST_EDITABLE("pasted (first editable)"),

    /** Non-smart mode found an editable but the paste call failed. */
    FIRST_EDITABLE_PASTE_FAILED("found editable but paste failed"),

    /** No editable field anywhere — the paste will fire on the next focus event. */
    NO_EDITABLE_FIELD("no editable field found \u2014 will paste on next focus"),

    /** Smart mode found nothing matching its heuristic. */
    NO_SMART_MATCH("no smart match"),
    ;

    /** Stable English diagnostic string for the debug pane. */
    fun message(): String = message

    /** `true` if the paste attempt successfully wrote into a field. */
    fun isSuccess(): Boolean = when (this) {
        PASTED_SMART_DETECTED,
        PASTED_SMART_FOCUSED,
        PASTED_FOCUSED,
        PASTED_FIRST_EDITABLE,
        -> true
        else -> false
    }
}
