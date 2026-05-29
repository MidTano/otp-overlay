// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

/**
 * Compact record of a queued OTP show request.
 *
 * Note on visibility: kept `public` because it's the parameter
 * and return type of [OverlayQueue.offer] / [OverlayQueue.pollFirst],
 * and [OverlayQueue] itself stays `public` to satisfy the
 * [com.midtano.otp.service.overlay.QueueUiHost.queue] override on
 * the public `OverlayService`.
 */
data class PendingShow(
    val otp: String,
    val sender: String?,
    val source: String?,
    val pkg: String?,
)
