// SPDX-License-Identifier: MIT
//
// AccessibilityNodeInfo.recycle() became a no-op on API 33+ (object
// pooling was discontinued), but we still target API 31/32 where
// the pool exists, so the calls have to stay.
@file:Suppress("DEPRECATION")

package com.midtano.otp.service

import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Heuristics for spotting OTP-input fields inside an
 * [AccessibilityNodeInfo] tree.
 *
 * # Design philosophy: «better miss than misfire»
 *
 * We would rather refuse to auto-paste and let the user copy the
 * code by hand than dump six digits into a browser URL bar, a
 * search box, an email field or a chat composer. Two layers
 * enforce that:
 *
 * 1. **Negative blocklist** ([isObviouslyNotOtpField]). If the
 *    node carries a clear "I am not an OTP field" marker — URL
 *    inputType, autofillHint=username/email/password, view-id
 *    containing "url"/"search"/"address" — the function returns
 *    [LEVEL_REJECT] no matter how many positive signals also
 *    match. This is checked FIRST.
 *
 * 2. **Positive confidence ladder** ([otpFieldConfidence]).
 *    Lower number = stronger signal:
 *    - 1 ([LEVEL_AUTOFILL])  — `autofillHints` carries a known
 *                              OTP hint. Definitive.
 *    - 2 ([LEVEL_HINT_TEXT]) — hint text or contentDescription
 *                              contains an OTP keyword.
 *    - 3 ([LEVEL_VIEW_ID])   — `viewIdResourceName` contains an
 *                              OTP keyword.
 *    - 4 ([LEVEL_NUMERIC])   — numeric inputType (no TEXT class)
 *                              + a `maxLength` in 4..9.
 *    - 99 ([LEVEL_REJECT])   — not an OTP field.
 *
 * The previous version had a Level 5 ("empty numeric field with
 * no maxLength") that was dropped: empty numeric fields are
 * common in price / age / quantity forms and made the auto-paste
 * land in the wrong control. The current ladder requires either
 * an explicit OTP marker (1..3) or the strong shape match in
 * Level 4.
 *
 * `isLikelyOtpField` therefore accepts confidence ≤ 4. Anything
 * weaker is silently ignored; the user is trusted to copy the
 * code from the overlay.
 */
internal object OtpFieldFinder {

    /** Hard cap on accessibility-tree recursion to bound the cost. */
    private const val MAX_DEPTH: Int = 32

    // ── Confidence levels ───────────────────────────────────────

    /** `autofillHints` is the most authoritative signal. */
    const val LEVEL_AUTOFILL: Int = 1

    /** OTP keyword in hint text / contentDescription. */
    const val LEVEL_HINT_TEXT: Int = 2

    /** OTP keyword in `viewIdResourceName`. */
    const val LEVEL_VIEW_ID: Int = 3

    /** Pure-numeric input with `maxLength in 4..9`. */
    const val LEVEL_NUMERIC: Int = 4

    /** Everything else — and explicitly-blocklisted fields. */
    const val LEVEL_REJECT: Int = 99

    // ── Positive markers ────────────────────────────────────────

    /**
     * Substrings searched inside hint text and contentDescription.
     * Word-bounded keywords ([HINT_KEYWORDS_BOUNDED]) require an
     * exact match against trimmed text or a clear surrounding
     * boundary; substring keywords ([HINT_KEYWORDS_SUBSTRING])
     * accept any occurrence. Splitting them ensures `"code"` does
     * NOT fire on `"please enter your password code-of-conduct"`,
     * but `"sms code"` still fires on `"Enter sms code from message"`.
     */
    val HINT_KEYWORDS_SUBSTRING: Array<String> = arrayOf(
        // Russian — multi-word phrases are unambiguous.
        "одноразов", "подтвержд", "верифик",
        "введите код", "код из", "код подтвержд", "пароль из смс",
        "цифры из", "код доступа", "проверочный",
        // English — multi-word phrases.
        "one-time", "onetime", "one time", "passcode",
        "sms code", "enter code", "digit code", "security code",
        "auth code", "login code", "access code", "verification",
    )

    /**
     * Short ASCII keywords that need word-boundary matching to
     * avoid hits inside `"username"`, `"verifyCard"`, etc.
     */
    val HINT_KEYWORDS_BOUNDED: Array<String> = arrayOf(
        // Russian (left boundary only — Cyrillic morphology).
        "код", "пин", "смс",
        // English.
        "code", "otp", "pin", "verify", "confirm",
        "token", "mfa", "2fa",
        // Transliterated.
        "kod", "parol",
    )

    /**
     * Substrings for `viewIdResourceName` (developer naming
     * conventions). View-ids are not natural language so substring
     * matching is fine — but we still keep the list narrow.
     */
    val VIEW_ID_KEYWORDS: Array<String> = arrayOf(
        "otp", "pin_code", "pincode", "passcode",
        "verify_code", "verification_code", "verification",
        "auth_code", "security_code", "confirm_code",
        "one_time", "onetime", "onetimecode",
        "smscode", "sms_code", "tan", "mtan", "smstan",
        "mfa", "tfa", "2fa",
    )

    /** `autofillHints` values that definitively mark an OTP field. */
    val AUTOFILL_OTP_HINTS: Array<String> = arrayOf(
        "smsOTPCode", // Android AUTOFILL_HINT_SMS_OTP
        "oneTimeCode", // cross-platform autofill hint
        "one-time-code", // Web autocomplete standard
    )

    // ── Negative markers (blocklist) ────────────────────────────

    /**
     * `autofillHints` values that are explicitly NOT OTP. Wins over
     * any positive signal — a field tagged `username` MUST not
     * receive an OTP even if its `viewIdResourceName` contains
     * `"code"` (e.g. `"login_code_username"`).
     */
    val BLOCKLIST_AUTOFILL_HINTS: Array<String> = arrayOf(
        "username", "password", "emailAddress", "email",
        "name", "personName",
        "phone", "phoneNumber", "tel",
        "postalAddress", "postalCode", "addressCountry",
        "creditCardNumber", "creditCardSecurityCode",
        "creditCardExpirationDate",
        "current-password", "new-password",
        "search", "url",
    )

    /**
     * Substrings in `viewIdResourceName` that mark a field as
     * obviously-not-OTP. The browser URL bar
     * (`com.android.chrome:id/url_bar`) and the search bar in any
     * Material toolbar (`*:id/search_src_text`) are caught here.
     */
    val BLOCKLIST_VIEW_IDS: Array<String> = arrayOf(
        "url", "search", "query", "address_bar",
        "compose", "message_text", "chat_input",
        "send_message", "comment_text",
        "username", "user_name", "login_name", "email", "mail",
        "password", "passwd",
        "phone", "tel", "address", "street", "city",
    )

    /**
     * Substrings in hint text / contentDescription that mark the
     * field as obviously-not-OTP. Lowercased before comparison.
     */
    val BLOCKLIST_HINT_KEYWORDS: Array<String> = arrayOf(
        // Russian.
        "поиск", "найти", "адрес", "ссылка", "url",
        "почта", "e-mail", "email",
        "имя пользователя", "логин",
        "пароль",
        "сообщение", "комментарий", "напишите",
        // English.
        "search", "find", "url", "address", "website",
        "username", "user name", "login",
        "email", "e-mail", "mail",
        "password",
        "message", "comment", "write a", "type a",
    )

    /**
     * `inputType` text-variation bits that are obviously not OTP.
     * These are sub-types of `TYPE_CLASS_TEXT`.
     */
    private val BLOCKLIST_TEXT_VARIATIONS: IntArray = intArrayOf(
        InputType.TYPE_TEXT_VARIATION_URI,
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
        InputType.TYPE_TEXT_VARIATION_FILTER,
        InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
        InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
        InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
        InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
    )

    // ── Public API ──────────────────────────────────────────────

    /**
     * Walk the tree rooted at [root] and return the best OTP
     * candidate, or `null` if nothing matches strongly enough.
     * The caller becomes the owner of the returned node and must
     * `recycle()` it.
     */
    fun findOtpField(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        return findOtpFieldRecursive(root, intArrayOf(Int.MAX_VALUE), 0)
    }

    private fun findOtpFieldRecursive(
        node: AccessibilityNodeInfo?,
        bestLevel: IntArray,
        depth: Int,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (depth > MAX_DEPTH) return null

        var best: AccessibilityNodeInfo? = null

        if (isUsableEditable(node)) {
            val level = otpFieldConfidence(node)
            if (level < bestLevel[0]) {
                bestLevel[0] = level
                best = node
                // Level 1 is definitive — no need to search further.
                if (level <= LEVEL_AUTOFILL) return best
            }
        }

        val count = try { node.childCount } catch (_: Exception) { return best }
        for (i in 0 until count) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue

            val found = findOtpFieldRecursive(child, bestLevel, depth + 1)
            if (found != null) {
                val previous = best
                if (previous != null && previous !== found && previous !== node) {
                    runCatching { previous.recycle() }
                }
                best = found
                if (bestLevel[0] <= LEVEL_AUTOFILL) {
                    if (child !== found) runCatching { child.recycle() }
                    return best
                }
            }
            if (child !== best) runCatching { child.recycle() }
        }
        return best
    }

    /**
     * Determine how likely [node] is to be an OTP input field.
     *
     * **Negative signals win.** If the node trips the blocklist
     * — URI inputType, autofillHint=username/email, view-id
     * containing `"url"`/`"search"`, hint text mentioning
     * `"search"`/`"password"`/etc. — the function returns
     * [LEVEL_REJECT] regardless of any positive match.
     *
     * @return confidence level: [LEVEL_AUTOFILL] best,
     *         [LEVEL_NUMERIC] last acceptable,
     *         [LEVEL_REJECT] otherwise.
     */
    fun otpFieldConfidence(node: AccessibilityNodeInfo?): Int {
        if (node == null) return LEVEL_REJECT
        if (isObviouslyNotOtpField(node)) return LEVEL_REJECT

        // Level 1: autofillHints (API 26+).
        val autofillLevel = checkAutofillHints(node)
        if (autofillLevel != LEVEL_REJECT) return autofillLevel

        // Level 2: hint text or contentDescription contains a keyword.
        val hintLevel = checkHintKeywords(node)
        if (hintLevel != LEVEL_REJECT) return hintLevel

        // Level 3: viewIdResourceName.
        val viewIdLevel = checkViewIdKeywords(node)
        if (viewIdLevel != LEVEL_REJECT) return viewIdLevel

        // Level 4: pure-numeric inputType + maxLength 4..9.
        return checkNumericShape(node)
    }

    /**
     * Hard reject signals — any single hit means the node is NOT
     * an OTP field, regardless of any positive marker on the same
     * node. Order matters only for early-exit; semantically each
     * branch is "reject if matches".
     */
    @Suppress("ReturnCount")
    fun isObviouslyNotOtpField(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return true

        // 1. Reject by autofill hint.
        try {
            val extras = node.extras
            val afHints = extras?.getStringArray("android.view.autofill.hintsArray")
            if (afHints != null) {
                for (h in afHints) {
                    if (h == null) continue
                    val low = h.lowercase(Locale.ROOT)
                    for (banned in BLOCKLIST_AUTOFILL_HINTS) {
                        if (low == banned.lowercase(Locale.ROOT)) return true
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Reject by viewIdResourceName.
        val viewId = try { node.viewIdResourceName } catch (_: Exception) { null }
        if (viewId != null) {
            // Only match the local-name suffix after the last "/"
            // (e.g. "com.android.chrome:id/url_bar" → "url_bar"),
            // so the package name itself can't accidentally match
            // "url" inside something benign like "iCarouselView".
            val localName = viewId.substringAfterLast('/').lowercase(Locale.ROOT)
            for (banned in BLOCKLIST_VIEW_IDS) {
                if (localName.contains(banned)) return true
            }
        }

        // 3. Reject by inputType — URI / EMAIL / FILTER / WEB_EDIT_TEXT.
        try {
            val inputType = node.inputType
            val cls = inputType and InputType.TYPE_MASK_CLASS
            if (cls == InputType.TYPE_CLASS_TEXT) {
                val variation = inputType and InputType.TYPE_MASK_VARIATION
                for (banned in BLOCKLIST_TEXT_VARIATIONS) {
                    if (variation == banned) return true
                }
            }
        } catch (_: Exception) {}

        // 4. Reject by hint / contentDescription keyword.
        //    BUT: positive OTP markers in the same hint override
        //    the blocklist. A bank's "Введите одноразовый пароль
        //    из SMS" mentions both "пароль" (banned: looks like a
        //    permanent password field) and "одноразов" (OTP).
        //    The user's intent is clearly OTP, so we let it
        //    through. Same for English "One-time password",
        //    "SMS password", "OTP password".
        val hintText = safeGetHint(node)
        val contentDesc = safeGetContentDescription(node)
        val combined = ((hintText ?: "") + " " + (contentDesc ?: ""))
            .lowercase(Locale.ROOT)
            .trim()
        if (combined.isNotEmpty()) {
            val hasOtpOverride = HINT_KEYWORDS_SUBSTRING.any { combined.contains(it) }
            if (!hasOtpOverride) {
                for (banned in BLOCKLIST_HINT_KEYWORDS) {
                    if (combined.contains(banned)) return true
                }
            }
        }

        return false
    }

    private fun checkAutofillHints(node: AccessibilityNodeInfo): Int {
        try {
            val extras = node.extras
            val afHints = extras?.getStringArray("android.view.autofill.hintsArray")
            if (afHints != null) {
                for (h in afHints) {
                    if (h == null) continue
                    // Locale.ROOT keeps "İ" → "i̇" stable on
                    // Turkish devices, where the default locale's
                    // toLowerCase() would produce a dotless 'ı'.
                    val low = h.lowercase(Locale.ROOT)
                    for (otpHint in AUTOFILL_OTP_HINTS) {
                        if (low.contains(otpHint.lowercase(Locale.ROOT))) {
                            return LEVEL_AUTOFILL
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return LEVEL_REJECT
    }

    private fun checkHintKeywords(node: AccessibilityNodeInfo): Int {
        val hintText = safeGetHint(node)
        val contentDesc = safeGetContentDescription(node)
        val combined = ((hintText ?: "") + " " + (contentDesc ?: ""))
            .lowercase(Locale.ROOT)
        if (combined.isBlank()) return LEVEL_REJECT

        for (kw in HINT_KEYWORDS_SUBSTRING) {
            if (combined.contains(kw)) return LEVEL_HINT_TEXT
        }
        for (kw in HINT_KEYWORDS_BOUNDED) {
            if (matchesAtBoundary(combined, kw)) return LEVEL_HINT_TEXT
        }
        return LEVEL_REJECT
    }

    private fun checkViewIdKeywords(node: AccessibilityNodeInfo): Int {
        val viewId = try { node.viewIdResourceName } catch (_: Exception) { null }
            ?: return LEVEL_REJECT
        val localName = viewId.substringAfterLast('/').lowercase(Locale.ROOT)
        for (kw in VIEW_ID_KEYWORDS) {
            if (localName.contains(kw)) return LEVEL_VIEW_ID
        }
        return LEVEL_REJECT
    }

    /**
     * Level 4: pure-numeric input with `maxLength` in 4..9.
     * Crucially we require the input class to be NUMBER and NOT
     * TEXT — a TYPE_CLASS_TEXT field with `inputmode=numeric` IS
     * still a text field and must not match (Level 1..3 cover the
     * legitimate "text + OTP marker" path).
     */
    private fun checkNumericShape(node: AccessibilityNodeInfo): Int {
        try {
            val inputType = node.inputType
            val cls = inputType and InputType.TYPE_MASK_CLASS
            if (cls != InputType.TYPE_CLASS_NUMBER) return LEVEL_REJECT
            val maxLen = node.maxTextLength
            if (maxLen in OTP_MIN_LEN..OTP_MAX_LEN) return LEVEL_NUMERIC
        } catch (_: Exception) {}
        return LEVEL_REJECT
    }

    /**
     * `text` contains [keyword] AND the character immediately
     * before the match is not a letter / digit / underscore.
     * Cheap word-boundary check that avoids hits like `"verifyCard"`
     * for keyword `"verify"` or `"username"` for keyword `"name"`.
     *
     * For Cyrillic keywords we only require the LEFT boundary —
     * Russian morphology routinely concatenates suffixes
     * (`"кодом"` is still a code reference, but `"коды"` would
     * fail a strict right-bound).
     */
    fun matchesAtBoundary(text: String, keyword: String): Boolean {
        if (keyword.isEmpty()) return false
        var from = 0
        val cyrillic = keyword.any { it in '\u0400'..'\u04FF' }
        while (true) {
            val idx = text.indexOf(keyword, from)
            if (idx < 0) return false
            val leftOk = idx == 0 || !text[idx - 1].isLetterOrDigit() && text[idx - 1] != '_'
            val rightPos = idx + keyword.length
            val rightOk = cyrillic ||
                rightPos == text.length ||
                (!text[rightPos].isLetterOrDigit() && text[rightPos] != '_')
            if (leftOk && rightOk) return true
            from = idx + 1
        }
    }

    /** Quick check used on the on-event path. Accepts confidence ≤ 4. */
    fun isLikelyOtpField(node: AccessibilityNodeInfo?): Boolean =
        otpFieldConfidence(node) <= LEVEL_NUMERIC

    fun safeGetHint(node: AccessibilityNodeInfo): String? = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            node.hintText?.toString()
        } else {
            null
        }
    } catch (_: Exception) { null }

    fun safeGetContentDescription(node: AccessibilityNodeInfo): String? = try {
        node.contentDescription?.toString()
    } catch (_: Exception) { null }

    fun isUsableEditable(n: AccessibilityNodeInfo?): Boolean {
        if (n == null) return false
        return try {
            n.isEditable && n.isEnabled && n.isVisibleToUser
        } catch (_: Exception) {
            false
        }
    }

    fun findFocusedEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        findFocusedEditableRecursive(node, 0)

    private fun findFocusedEditableRecursive(
        node: AccessibilityNodeInfo?,
        depth: Int,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (depth > MAX_DEPTH) return null
        try {
            if (node.isEditable && node.isFocused && node.isEnabled) return node
        } catch (_: Exception) {}
        val count = try { node.childCount } catch (_: Exception) { return null }
        for (i in 0 until count) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            val result = findFocusedEditableRecursive(child, depth + 1)
            if (result != null) {
                if (child !== result) runCatching { child.recycle() }
                return result
            }
            runCatching { child.recycle() }
        }
        return null
    }

    fun findAnyEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        findAnyEditableRecursive(node, 0)

    private fun findAnyEditableRecursive(
        node: AccessibilityNodeInfo?,
        depth: Int,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (depth > MAX_DEPTH) return null
        try {
            if (node.isEditable && node.isEnabled && node.isVisibleToUser) return node
        } catch (_: Exception) {}
        val count = try { node.childCount } catch (_: Exception) { return null }
        for (i in 0 until count) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            val result = findAnyEditableRecursive(child, depth + 1)
            if (result != null) {
                if (child !== result) runCatching { child.recycle() }
                return result
            }
            runCatching { child.recycle() }
        }
        return null
    }

    /** Inclusive bounds of an OTP-shaped digit run. Same as the extractor. */
    private const val OTP_MIN_LEN: Int = 4
    private const val OTP_MAX_LEN: Int = 9
}
