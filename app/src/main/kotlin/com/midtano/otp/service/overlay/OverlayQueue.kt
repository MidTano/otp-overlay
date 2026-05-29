// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import java.util.ArrayDeque

/**
 * FIFO queue of [PendingShow] entries waiting for an open overlay
 * slot.
 *
 * Centralises three concerns the service no longer has to think
 * about itself:
 * - the [HARD_CAP] ceiling that protects against a buggy
 *   notification stream OOM-ing the process,
 * - content-based deduplication (same OTP queued twice from SMS
 *   and a push mirror within the dedup window),
 * - the `currentOtp` race where the OTP that's on screen arrives
 *   again as a backlog entry.
 *
 * Not thread-safe by design — every caller already runs on the
 * main thread.
 */
/**
 * In-order ring of pending OTP shows. Backed by an [ArrayDeque]
 * so head poll and tail offer are O(1).
 *
 * Caps at [HARD_CAP] entries; a runaway burst of pushes (an SMS
 * gateway gone wrong, a malicious app spam-firing notifications)
 * can never push more than that without the offer rejecting, so
 * the service can't OOM under load.
 *
 * NOT thread-safe — every read and write goes through
 * `OverlayService` on the main thread.
 *
 * Note on visibility: kept `public` because [OverlayService] —
 * itself a `public Service` instantiated by the OS — exposes a
 * `queue()` accessor on its `QueueUiHost` contract. A Kotlin
 * `internal` class cannot appear in a public override's signature.
 */
class OverlayQueue : Iterable<PendingShow> {

    private val backing = ArrayDeque<PendingShow>()

    /**
     * Try to add a pending show.
     *
     * @param show       the entry to enqueue
     * @param currentOtp OTP currently surfaced on screen (may be
     *                   `null`); matching entries are dropped to
     *                   avoid double-show.
     * @return `false` when the entry was rejected (cap reached, the
     *         input was empty, or the same OTP is already queued or
     *         on screen).
     */
    fun offer(show: PendingShow?, currentOtp: String?): Boolean {
        if (show == null || show.otp.isEmpty()) return false
        if (currentOtp != null && currentOtp == show.otp) return false
        for (existing in backing) if (show.otp == existing.otp) return false
        if (backing.size >= HARD_CAP) return false
        backing.add(show)
        return true
    }

    /** `true` if any entry's OTP equals [otp]. */
    fun contains(otp: String?): Boolean {
        if (otp == null) return false
        for (p in backing) if (otp == p.otp) return true
        return false
    }

    /** Remove and return the head entry, or `null` if empty. */
    fun pollFirst(): PendingShow? = backing.pollFirst()

    /** Remove a specific entry. */
    fun remove(show: PendingShow): Boolean = backing.remove(show)

    /** Drop every queued entry. */
    fun clear() {
        backing.clear()
    }

    fun size(): Int = backing.size
    fun isEmpty(): Boolean = backing.isEmpty()

    override fun iterator(): Iterator<PendingShow> = backing.iterator()

    companion object {
        /**
         * Defensive cap so a flood of distinct OTPs cannot OOM the
         * service. Far above any realistic burst — even an
         * SMS-gateway misconfiguration that fires 50 codes in a
         * minute leaves headroom of 4x. Lowered from 2000 in the
         * audit cleanup: the original ceiling was a paranoia number
         * that made `rejectsBeyondHardCap` insert 2000 entries on
         * every test run for no real-world benefit.
         */
        const val HARD_CAP: Int = 200
    }
}
