// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Real-world regression suite. Each test pins a body shape that
 * actually arrives in production from banks, marketplaces, mailers,
 * messaging apps and IoT push notifications. They double as
 * documentation of what the extractor must (or must not) surface.
 *
 * If a future change reorders the trigger / cleanup / regex
 * pipeline, this is the suite that fails most loudly — exact strings
 * lifted from anonymised user reports.
 */
class OtpExtractorRealWorldTest {

    private fun defaults(): OtpExtractorSettings = OtpExtractorSettings(
        triggerWords = listOf(
            "code",
            "otp",
            "verification",
            "verify",
            "auth",
            "одноразов",
            "код",
            "пароль",
        ),
        stopWords = emptyList(),
        stopWordsEnabled = false,
        ignoredPhrases = emptyList(),
        ignoreEnabled = false,
        cleanupPhrases = emptyList(),
        cleanupEnabled = false,
        regex = OtpExtractor.DEFAULT_REGEX,
        currencyTokens = listOf(
            "USD", "EUR", "RUB", "BYN", "GBP", "UAH", "PLN", "TRY",
            "KZT", "AZN", "$", "€", "£", "руб", "р.",
        ),
        currencySkipEnabled = true,
        normalizeDigits = true,
    )

    // ── Banks ───────────────────────────────────────────────────────

    @Test
    fun russianBankSmsExtractsCode() {
        val body = "Никому не сообщайте код: 348711. " +
            "Операция: вход в приложение. Альфа-Банк."
        assertEquals("348711", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun englishBankSmsExtractsCode() {
        val body = "Your one-time verification code is 902347. Do not share with anyone. — Bank"
        assertEquals("902347", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun bankPaymentReceiptIsNotAnOtp() {
        // Receipt notifications must NEVER trigger the overlay —
        // the digits are an amount, not an OTP, and there's no
        // trigger keyword.
        val body = "Покупка 4521.00 RUB в RIVE GAUCHE. Доступно 12345.00 RUB. Карта *4172."
        assertNull(OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun bankReceiptWithKeywordStillSkipsAmount() {
        // Crafted edge: receipt mentioning "code" elsewhere — the
        // amount must still be filtered out by currency adjacency.
        val body = "code: ATM-3 — purchase 12345 RUB processed, balance 9988 RUB"
        // Both numbers are currency-adjacent, so we fall back to
        // the split-code helper, which finds nothing OTP-shaped.
        assertNull(OtpExtractorCore.extract(body, defaults()))
    }

    // ── Marketplaces / delivery ─────────────────────────────────────

    @Test
    fun deliveryPickupSmsExtractsCode() {
        val body = "Заказ #998877 готов к выдаче. Код для получения: 7421."
        assertEquals("7421", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun deliveryWithoutKeywordIgnored() {
        // Order number in isolation must not surface.
        val body = "Заказ #998877 готов к выдаче в пункте на ул. Ленина 5."
        assertNull(OtpExtractorCore.extract(body, defaults()))
    }

    // ── Two-factor / messengers ─────────────────────────────────────

    @Test
    fun telegramLoginCodeExtracts() {
        val body = "Login code: 12345. Do not give this code to anyone, even if they say they're from Telegram."
        assertEquals("12345", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun whatsappCodeWithDashSurvivesSplitFallback() {
        // WhatsApp reliably ships codes as "AAA-BBB". The default
        // regex skips the dash; the split-code fallback rejoins.
        // Trailing whitespace / EOL after the code keeps the
        // negative lookahead happy (a literal `.` would break it,
        // which is documented behaviour: dotted runs are treated
        // as IPs / version numbers, not OTPs).
        val body = "Your WhatsApp code: 482-915"
        assertEquals("482915", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun discordSpacedCodeRejoined() {
        val body = "Your Discord verification code is 123 456"
        assertEquals("123456", OtpExtractorCore.extract(body, defaults()))
    }

    // ── Negatives ───────────────────────────────────────────────────

    @Test
    fun phoneNumberNeverTriggers() {
        val body = "Call back at +7 916 123-45-67 to confirm the booking"
        assertNull(OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun ipAddressNeverTriggers() {
        // IP-shaped string in a security alert that DOES contain
        // a trigger word — we still want the OTP, not the IP. In
        // this body there is no real OTP, so the result must be null.
        val body = "Login attempt from 192.168.001.215 — verify it was you."
        assertNull(OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun copyrightYearNeverTriggers() {
        // No trigger word, just a marketing body with a year.
        // Without "verify"/"code"/etc. the extractor must not fire.
        val body = "Privacy notice. © 2026 Acme — read our policy at example.com"
        assertNull(OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun copyrightYearWithTriggerWordIsExtracted_documentedLimitation() {
        // Documented limitation: when a trigger word IS present and
        // the only 4-digit run is a copyright year, the extractor
        // still returns it. The tradeoff is intentional — being
        // stricter would lose 4-digit OTPs entirely. Locking the
        // behaviour in so a future scoring tweak that drops this
        // body to null is a deliberate decision, not an accident.
        val body = "Privacy verify your account © 2026"
        assertEquals("2026", OtpExtractorCore.extract(body, defaults()))
    }

    // ── Multilingual real bodies ────────────────────────────────────

    @Test
    fun arabicBankSmsWithIndicDigitsExtracts() {
        // Real shape from a UAE bank: keyword in English, digits
        // in Arabic-Indic. After normalisation we must surface
        // the ASCII form.
        val body = "code: \u0660\u0664\u0668\u0662\u0669\u0661 - do not share with anyone"
        assertEquals("048291", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun cyrillicAndLatinKeywordsBothWork() {
        val a = "Код подтверждения: 555444"
        val b = "Verification code: 555444"
        assertEquals("555444", OtpExtractorCore.extract(a, defaults()))
        assertEquals("555444", OtpExtractorCore.extract(b, defaults()))
    }

    // ── Pathological inputs ─────────────────────────────────────────

    @Test
    fun emojiNoiseDoesNotConfuseExtractor() {
        // Real push from a "secure messenger" — embeds emoji
        // around digits.
        val body = "🔐 Your auth code is 987654 — expires in 60s 🔐"
        assertEquals("987654", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun multilineExtrasFlattenedExtract() {
        // The notification body builder concatenates extras with
        // single spaces. Verify the extractor sees a normal body.
        val body = "New sign-in detected verify it was you Authentication code: 482915"
        assertEquals("482915", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun trailingPunctuationDoesNotEatDigits() {
        val body = "Your code is 482915."
        assertEquals("482915", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun extremelyShortCodeBelowMinIsRejected() {
        // 3 digits is below the 4-digit floor of the default regex.
        val body = "Your code is 123 — do not share"
        assertNull(OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun ridiculouslyLongCodeAboveMaxIsRejected() {
        // 10+ digits is past the OTP window — must NOT match the
        // primary regex. The split-code fallback also requires
        // separators.
        val body = "Your code is 1234567890 — do not share"
        assertNull(OtpExtractorCore.extract(body, defaults()))
    }
}
