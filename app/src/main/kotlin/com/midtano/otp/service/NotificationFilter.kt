// SPDX-License-Identifier: MIT
package com.midtano.otp.service

/**
 * Pure-function gate that decides whether a notification's source
 * package should be passed to the OTP extractor.
 *
 * Lives outside [NotificationListener] so it can be unit tested
 * directly — the listener itself is a `Service` that is awkward to
 * wire up in a JVM test.
 */
internal object NotificationFilter {

    /**
     * Package-name prefixes the listener never handles. Each prefix
     * matches via [String.startsWith] so OEMs that shadow a system
     * component under a longer name (e.g.
     * `com.android.systemui.intelligence`) are still rejected.
     */
    val IGNORE_PREFIXES: Array<String> = arrayOf(
        "com.android.systemui",
        "com.google.android.googlequicksearchbox",
        "android",
        "com.android.vpndialogs",
        "com.android.connectivity",
        "com.google.android.apps.vpn",
    )

    /**
     * @return the matched ignore prefix when [pkg] should be
     *         skipped, or `null` when the listener should continue.
     *         Returning the prefix lets the caller include it in a
     *         "skipped" diagnostic without rescanning the array.
     */
    fun matchedIgnorePrefix(pkg: String?): String? {
        if (pkg.isNullOrEmpty()) return null
        for (prefix in IGNORE_PREFIXES) {
            if (pkg.startsWith(prefix)) return prefix
        }
        return null
    }

    /** Convenience boolean variant of [matchedIgnorePrefix]. */
    fun shouldIgnore(pkg: String?): Boolean = matchedIgnorePrefix(pkg) != null

    /**
     * Boolean variant of [matchedIgnorePrefix] that also rejects
     * the caller's own package, so the listener doesn't re-process
     * its own foreground-service notification and feed back into
     * the overlay queue.
     */
    fun shouldIgnore(pkg: String?, selfPackage: String?): Boolean {
        if (pkg.isNullOrEmpty()) return true
        if (selfPackage != null && pkg == selfPackage) return true
        return shouldIgnore(pkg)
    }
}
