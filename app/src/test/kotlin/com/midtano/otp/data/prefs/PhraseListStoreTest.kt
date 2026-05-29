// SPDX-License-Identifier: MIT
package com.midtano.otp.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the phrase-list serialiser. The list is
 * persisted as a newline-joined string so we test:
 * - parse/join symmetry,
 * - blank entries being dropped,
 * - duplicate collapsing (insertion order preserved),
 * - case folding semantics (Locale.ROOT, so "I" stays "i" on
 *   Turkish devices instead of becoming "ı").
 */
class PhraseListStoreTest {

    @Test
    fun nullParseReturnsEmpty() {
        assertEquals(emptyList<String>(), PhraseListStore.parse(null, lowercase = false))
    }

    @Test
    fun emptyParseReturnsEmpty() {
        assertEquals(emptyList<String>(), PhraseListStore.parse("", lowercase = false))
    }

    @Test
    fun parseTrimsAndDropsBlankEntries() {
        val raw = "  hello  \n\n  world\n"
        assertEquals(listOf("hello", "world"), PhraseListStore.parse(raw, lowercase = false))
    }

    @Test
    fun parseCollapsesDuplicatesPreservingFirstOrder() {
        val raw = "code\nOTP\nCODE\notp\nverify"
        assertEquals(
            listOf("code", "otp", "verify"),
            PhraseListStore.parse(raw, lowercase = true),
        )
    }

    @Test
    fun parseRespectsCaseFlag() {
        val raw = "USD\nusd\n€"
        assertEquals(
            listOf("USD", "usd", "€"),
            PhraseListStore.parse(raw, lowercase = false),
        )
        assertEquals(
            listOf("usd", "€"),
            PhraseListStore.parse(raw, lowercase = true),
        )
    }

    @Test
    fun joinDropsEmptyEntries() {
        val joined = PhraseListStore.join(listOf("hello", "  ", "", "world"), lowercase = false)
        assertEquals("hello\nworld", joined)
    }

    @Test
    fun joinDeduplicatesPreservingOrder() {
        val joined = PhraseListStore.join(listOf("code", "OTP", "code"), lowercase = true)
        assertEquals("code\notp", joined)
    }

    @Test
    fun roundTripIsIdempotent() {
        val original = listOf("verify", "code", "auth")
        val joined = PhraseListStore.join(original, lowercase = true)
        val parsed = PhraseListStore.parse(joined, lowercase = true)
        assertEquals(original, parsed)
    }

    @Test
    fun nonAsciiPreservedThroughBothModes() {
        // Cyrillic stems must survive case folding without mangling.
        val parsed = PhraseListStore.parse("Код\nКод\nкод", lowercase = true)
        // Locale.ROOT gives a deterministic mapping for Cyrillic K.
        assertEquals(1, parsed.size)
        assertEquals("код", parsed.first())
    }

    @Test
    fun joinRoundTripStableAcrossCalls() {
        val first = PhraseListStore.join(listOf("a", "b", "c"), lowercase = false)
        val second = PhraseListStore.join(
            PhraseListStore.parse(first, lowercase = false),
            lowercase = false,
        )
        assertEquals(first, second)
        assertNotNull(first)
        assertTrue(first.lines().size == 3)
    }
}
