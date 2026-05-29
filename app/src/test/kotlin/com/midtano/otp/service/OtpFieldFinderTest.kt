// SPDX-License-Identifier: MIT
package com.midtano.otp.service

import android.os.Bundle
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests for the OTP-field heuristic. The function is the single
 * gatekeeper between "we have a code" and "we paste it into a
 * field on screen", so its accept/reject behaviour is the
 * project's last line of defence against an OTP landing in the
 * wrong control (Chrome url-bar, search box, password input,
 * email composer, …).
 *
 * Two big classes of cases:
 *  1. **Positive** — fields the heuristic MUST accept, with the
 *     expected confidence level.
 *  2. **Negative** — fields the heuristic MUST reject, with the
 *     critical case being the URL bar / search / password
 *     pattern.
 *
 * Robolectric gives us real `AccessibilityNodeInfo` instances so
 * the tests cover the same property-getter paths the live service
 * uses.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OtpFieldFinderTest {

    private fun makeNode(
        editable: Boolean = true,
        focused: Boolean = false,
        enabled: Boolean = true,
        visibleToUser: Boolean = true,
        viewId: String? = null,
        hint: CharSequence? = null,
        contentDesc: CharSequence? = null,
        autofillHints: Array<String>? = null,
        inputType: Int = 0,
        maxTextLength: Int = -1,
        text: CharSequence? = null,
    ): AccessibilityNodeInfo {
        // AccessibilityNodeInfo() public constructor is the API-30+
        // replacement for the deprecated obtain() pool. Robolectric
        // runs us under @Config(sdk = [34]) so the constructor is
        // always available.
        val node = AccessibilityNodeInfo()
        node.isEditable = editable
        node.isFocused = focused
        node.isEnabled = enabled
        node.isVisibleToUser = visibleToUser
        if (viewId != null) node.viewIdResourceName = viewId
        if (hint != null) node.hintText = hint
        if (contentDesc != null) node.contentDescription = contentDesc
        if (autofillHints != null) {
            // Robolectric stores the hint array via the same
            // extras key the platform reads from at runtime.
            val extras = node.extras ?: Bundle()
            extras.putStringArray("android.view.autofill.hintsArray", autofillHints)
            // node.extras returns the live Bundle on every call;
            // setting it back explicitly is unnecessary.
        }
        node.inputType = inputType
        node.maxTextLength = maxTextLength
        if (text != null) node.text = text
        return node
    }

    // ── Positive cases (must accept) ────────────────────────────

    @Test
    fun autofillHintSmsOtpCode_levelOne() {
        val node = makeNode(autofillHints = arrayOf("smsOTPCode"))
        assertEquals(OtpFieldFinder.LEVEL_AUTOFILL, OtpFieldFinder.otpFieldConfidence(node))
        assertTrue(OtpFieldFinder.isLikelyOtpField(node))
    }

    @Test
    fun autofillHintOneTimeCode_levelOne() {
        val node = makeNode(autofillHints = arrayOf("oneTimeCode"))
        assertEquals(OtpFieldFinder.LEVEL_AUTOFILL, OtpFieldFinder.otpFieldConfidence(node))
    }

    @Test
    fun hintTextEnglishOtp_levelTwo() {
        val node = makeNode(hint = "Enter SMS code")
        assertEquals(OtpFieldFinder.LEVEL_HINT_TEXT, OtpFieldFinder.otpFieldConfidence(node))
    }

    @Test
    fun hintTextRussianOtp_levelTwo() {
        val node = makeNode(hint = "Код из SMS")
        assertEquals(OtpFieldFinder.LEVEL_HINT_TEXT, OtpFieldFinder.otpFieldConfidence(node))
    }

    @Test
    fun hintTextOneTimePassword_levelTwo() {
        val node = makeNode(hint = "One-time password")
        assertEquals(OtpFieldFinder.LEVEL_HINT_TEXT, OtpFieldFinder.otpFieldConfidence(node))
    }

    @Test
    fun viewIdContainingOtp_levelThree() {
        val node = makeNode(viewId = "com.bank.app:id/otp_input")
        assertEquals(OtpFieldFinder.LEVEL_VIEW_ID, OtpFieldFinder.otpFieldConfidence(node))
    }

    @Test
    fun viewIdContainingVerificationCode_levelThree() {
        val node = makeNode(viewId = "com.bank.app:id/verification_code_field")
        assertEquals(OtpFieldFinder.LEVEL_VIEW_ID, OtpFieldFinder.otpFieldConfidence(node))
    }

    @Test
    fun numericInputWithMaxLengthSix_levelFour() {
        val node = makeNode(
            inputType = InputType.TYPE_CLASS_NUMBER,
            maxTextLength = 6,
        )
        assertEquals(OtpFieldFinder.LEVEL_NUMERIC, OtpFieldFinder.otpFieldConfidence(node))
    }

    // ── Negative cases (must reject) ────────────────────────────

    @Test
    fun urlBar_rejected() {
        // Chrome's url_bar carries every signal a search-box
        // would: editable, focused, visible, autofill `username`
        // hint sometimes, viewId ending in "url_bar". The
        // heuristic must NEVER accept it.
        val node = makeNode(
            viewId = "com.android.chrome:id/url_bar",
            hint = "Search or type web address",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
        )
        assertEquals(OtpFieldFinder.LEVEL_REJECT, OtpFieldFinder.otpFieldConfidence(node))
        assertFalse(OtpFieldFinder.isLikelyOtpField(node))
        assertTrue(OtpFieldFinder.isObviouslyNotOtpField(node))
    }

    @Test
    fun searchBox_rejected() {
        val node = makeNode(
            viewId = "com.example.app:id/search_src_text",
            hint = "Search",
        )
        assertTrue(OtpFieldFinder.isObviouslyNotOtpField(node))
        assertEquals(OtpFieldFinder.LEVEL_REJECT, OtpFieldFinder.otpFieldConfidence(node))
    }

    @Test
    fun passwordField_rejected() {
        val node = makeNode(
            autofillHints = arrayOf("password"),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            hint = "Password",
        )
        assertTrue(OtpFieldFinder.isObviouslyNotOtpField(node))
        assertEquals(OtpFieldFinder.LEVEL_REJECT, OtpFieldFinder.otpFieldConfidence(node))
    }

    @Test
    fun emailField_rejected() {
        val node = makeNode(
            autofillHints = arrayOf("emailAddress"),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            hint = "Email",
        )
        assertTrue(OtpFieldFinder.isObviouslyNotOtpField(node))
    }

    @Test
    fun usernameField_rejected() {
        val node = makeNode(
            autofillHints = arrayOf("username"),
            hint = "Username",
        )
        assertTrue(OtpFieldFinder.isObviouslyNotOtpField(node))
    }

    @Test
    fun phoneField_rejected() {
        val node = makeNode(
            autofillHints = arrayOf("phoneNumber"),
            hint = "Phone number",
        )
        assertTrue(OtpFieldFinder.isObviouslyNotOtpField(node))
    }

    @Test
    fun composerMessageField_rejected() {
        val node = makeNode(
            viewId = "com.messenger.app:id/message_text",
            hint = "Type a message",
        )
        assertTrue(OtpFieldFinder.isObviouslyNotOtpField(node))
    }

    @Test
    fun emptyNumericFieldNoMaxLength_rejected() {
        // Old Level 5 used to accept this — leading to
        // false-positives in age / quantity / order-number
        // forms. Without an explicit OTP marker an empty
        // numeric field MUST NOT win.
        val node = makeNode(
            inputType = InputType.TYPE_CLASS_NUMBER,
            maxTextLength = -1,
        )
        assertFalse(OtpFieldFinder.isLikelyOtpField(node))
    }

    @Test
    fun longMaxLengthNumericField_rejected() {
        // A 16-digit numeric field is much more likely a card
        // number than an OTP — we don't auto-paste into it.
        val node = makeNode(
            inputType = InputType.TYPE_CLASS_NUMBER,
            maxTextLength = 16,
        )
        assertEquals(OtpFieldFinder.LEVEL_REJECT, OtpFieldFinder.otpFieldConfidence(node))
    }

    @Test
    fun textFieldWithCodeKeywordButPasswordHint_rejected() {
        // A field that says "Password" without any OTP marker is
        // a password field, not an OTP field — even if some random
        // word "code" sneaks into a longer hint.
        val node = makeNode(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            hint = "Password",
        )
        assertTrue(OtpFieldFinder.isObviouslyNotOtpField(node))
    }

    @Test
    fun bankOneTimePasswordHint_accepted() {
        // Real-world bank case: hint is "Одноразовый пароль из
        // SMS" or "One-time password" — the word "пароль" /
        // "password" appears, which would normally mean a
        // permanent password field. But the explicit OTP marker
        // ("одноразов" / "one-time") MUST override the blocklist.
        val ru = makeNode(hint = "Введите одноразовый пароль из SMS")
        assertEquals(OtpFieldFinder.LEVEL_HINT_TEXT, OtpFieldFinder.otpFieldConfidence(ru))
        val en = makeNode(hint = "One-time password")
        assertEquals(OtpFieldFinder.LEVEL_HINT_TEXT, OtpFieldFinder.otpFieldConfidence(en))
    }

    @Test
    fun nonEditable_rejectedAtTreeLevel() {
        // Even if a non-editable node has all the OTP signals,
        // findOtpField walks past it (only `isUsableEditable`
        // candidates contribute). The confidence function
        // itself doesn't check editability — the tree walker
        // does.
        val node = makeNode(
            editable = false,
            autofillHints = arrayOf("smsOTPCode"),
        )
        assertFalse(OtpFieldFinder.isUsableEditable(node))
    }

    // ── Word-boundary regression ────────────────────────────────

    @Test
    fun bareCodeKeywordRequiresBoundary() {
        // "code" inside "decode" must not match — that was a
        // historical false-positive.
        assertFalse(OtpFieldFinder.matchesAtBoundary("decode the file", "code"))
        // "code" with a clean left and right boundary matches.
        assertTrue(OtpFieldFinder.matchesAtBoundary("enter your code now", "code"))
    }

    @Test
    fun verifyKeywordRequiresBoundary() {
        // "verify" inside "verifyCard" must not match.
        assertFalse(OtpFieldFinder.matchesAtBoundary("verifycard payment", "verify"))
        assertTrue(OtpFieldFinder.matchesAtBoundary("please verify it was you", "verify"))
    }

    @Test
    fun cyrillicLeftBoundaryOnly() {
        // Russian morphology routinely concatenates suffixes —
        // the bounded check should accept "кодом" (instrumental
        // case) and "коды" (plural) for keyword "код".
        assertTrue(OtpFieldFinder.matchesAtBoundary("введите кодом", "код"))
        assertTrue(OtpFieldFinder.matchesAtBoundary("используйте коды", "код"))
        // But still requires SOME left boundary — substring of
        // a bigger word with a Cyrillic prefix should not match.
        assertFalse(OtpFieldFinder.matchesAtBoundary("прекодом", "код"))
    }

    // ── Tree-walk integration ───────────────────────────────────

    @Test
    fun confidenceLevels_resolveCorrectlyForEachMarker() {
        // Sanity check that each tier produces the right
        // confidence value when a single-marker node is
        // evaluated. The full tree-walk integration is covered
        // by the instrumented test (`OtpFieldFinderInstrumentedTest`)
        // because constructing a real parent/child accessibility
        // tree off-device requires a live View hierarchy that
        // Robolectric does not synthesise.
        val viewIdNode = makeNode(viewId = "com.example:id/otp_input")
        val autofillNode = makeNode(autofillHints = arrayOf("oneTimeCode"))
        assertEquals(OtpFieldFinder.LEVEL_VIEW_ID, OtpFieldFinder.otpFieldConfidence(viewIdNode))
        assertEquals(OtpFieldFinder.LEVEL_AUTOFILL, OtpFieldFinder.otpFieldConfidence(autofillNode))
    }
}
