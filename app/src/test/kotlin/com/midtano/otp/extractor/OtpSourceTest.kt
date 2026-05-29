// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip tests for the OTP-source enum. The decoder must accept
 * both modern lower-case ids and any legacy upper-case payload that
 * may still ride an older intent extra.
 */
class OtpSourceTest {

    @Test
    fun storageIdsAreLowerCase() {
        assertEquals("sms", OtpSource.SMS.storageId)
        assertEquals("push", OtpSource.PUSH.storageId)
        assertEquals("test", OtpSource.TEST.storageId)
    }

    @Test
    fun decodesExactMatch() {
        assertEquals(OtpSource.SMS, OtpSource.fromStorageId("sms"))
        assertEquals(OtpSource.PUSH, OtpSource.fromStorageId("push"))
        assertEquals(OtpSource.TEST, OtpSource.fromStorageId("test"))
    }

    @Test
    fun decodesLegacyUpperCase() {
        assertEquals(OtpSource.SMS, OtpSource.fromStorageId("SMS"))
        assertEquals(OtpSource.PUSH, OtpSource.fromStorageId("Push"))
    }

    @Test
    fun unknownReturnsNull() {
        assertNull(OtpSource.fromStorageId(null))
        assertNull(OtpSource.fromStorageId(""))
        assertNull(OtpSource.fromStorageId("email"))
    }
}
