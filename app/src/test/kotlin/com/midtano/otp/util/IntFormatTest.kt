// SPDX-License-Identifier: MIT
package com.midtano.otp.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Pure-JVM tests for [toLocaleString]. Each test pins the default
 * locale to the value the assertion expects, then restores the
 * machine default in [After] so we don't poison tests that run
 * after us.
 *
 * The extension formats via [java.text.NumberFormat], which is what
 * we want for status-row counters: ASCII for en-US, Eastern Arabic
 * digits in fa-IR, locale-appropriate separators for ru-RU, etc.
 */
class IntFormatTest {

    private val originalLocale = Locale.getDefault()

    @Before
    fun setup() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun teardown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun zeroFormats() {
        assertEquals("0", 0.toLocaleString())
    }

    @Test
    fun smallPositiveFormats() {
        assertEquals("42", 42.toLocaleString())
    }

    @Test
    fun thousandSeparatorEnUs() {
        assertEquals("1,234", 1234.toLocaleString())
        assertEquals("1,234,567", 1_234_567.toLocaleString())
    }

    @Test
    fun thousandSeparatorRu() {
        // ru-RU: non-breaking space U+00A0.
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))
        val out = 1234.toLocaleString()
        // The non-breaking space character is locale-implementation
        // dependent; we only assert the digits land in the right
        // order with some grouping char in between.
        assert(out.startsWith("1") && out.endsWith("234")) {
            "expected ru-RU 1234 to render with ASCII digits, got '$out'"
        }
    }

    @Test
    fun negativeFormats() {
        assertEquals("-1,234", (-1234).toLocaleString())
    }

    @Test
    fun maxIntValueFormats() {
        // Sanity: no overflow when the int is at the boundary.
        assertEquals("2,147,483,647", Int.MAX_VALUE.toLocaleString())
    }

    @Test
    fun minIntValueFormats() {
        assertEquals("-2,147,483,648", Int.MIN_VALUE.toLocaleString())
    }
}
