// SPDX-License-Identifier: MIT
package com.midtano.otp.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the notification-body flattener. Verifies
 * the order in which extras are concatenated, that null/empty fields
 * are dropped, and that the line array is appended at the tail.
 */
class NotificationBodyBuilderTest {

    @Test
    fun allEmptyReturnsEmptyString() {
        val out = NotificationBodyBuilder.build(null, null, null, null, null, null, null)
        assertEquals("", out)
    }

    @Test
    fun titleOnlyIsTrimmed() {
        val out = NotificationBodyBuilder.build("Title", null, null, null, null, null, null)
        assertEquals("Title", out)
    }

    @Test
    fun preservesFieldOrder() {
        val out = NotificationBodyBuilder.build(
            "T",
            "text",
            "big",
            "sub",
            "summary",
            "info",
            null,
        )
        assertEquals("T text big sub summary info", out)
    }

    @Test
    fun blankFieldsDropped() {
        val out = NotificationBodyBuilder.build("", "text", null, "", null, null, null)
        assertEquals("text", out)
    }

    @Test
    fun linesArrayAppended() {
        val out = NotificationBodyBuilder.build(
            "Title",
            "Body",
            null,
            null,
            null,
            null,
            arrayOf<CharSequence>("Line A", "Line B"),
        )
        assertEquals("Title Body Line A Line B", out)
    }

    @Test
    fun nullsInLinesArrayIgnored() {
        val out = NotificationBodyBuilder.build(
            "Title",
            null,
            null,
            null,
            null,
            null,
            arrayOf<CharSequence>("Visible", "", "Tail"),
        )
        // Empty entry between the two visible ones should not produce
        // a double-space. The trailing space before each surviving
        // entry is collapsed by the final trim().
        assertTrue(out.contains("Visible"))
        assertTrue(out.contains("Tail"))
        assertEquals("Title Visible Tail", out)
    }
}
