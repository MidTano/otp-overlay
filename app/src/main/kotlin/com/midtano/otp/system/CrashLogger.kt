// SPDX-License-Identifier: MIT
package com.midtano.otp.system

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Best-effort crash and structured-logging facility.
 *
 * On install registers a global [Thread.UncaughtExceptionHandler]
 * that captures the stack trace, thread name, app version, OS
 * version and the recent log ring, then writes them to a file in the
 * app's private files directory. The Settings → "Logs" pane reads
 * the file back; developers can also pull it via:
 *
 * ```
 *   adb shell run-as com.midtano.otp cat files/last_crash.txt
 *   adb shell run-as com.midtano.otp cat files/latest_log.txt
 * ```
 *
 * Also exposes a [LOG_LINES_KEPT]-entry in-memory ring buffer so a
 * crash report includes recent context, not just the stack trace.
 *
 * The [SuppressLint] suppresses [StaticFieldLeak] for [appCtx] —
 * holding the application context statically is safe because it
 * lives for the lifetime of the process, and a process-wide crash
 * handler genuinely needs a process-wide context.
 */
@SuppressLint("StaticFieldLeak")
internal object CrashLogger {

    private const val TAG = "OtpCrashLogger"
    private const val CRASH_FILE = "last_crash.txt"
    private const val LATEST_LOG = "latest_log.txt"
    private const val LOG_LINES_KEPT = 200

    /** In-memory ring buffer of the last N log lines. */
    private val ring = ArrayDeque<String>(LOG_LINES_KEPT)

    /** Used inside `synchronized(ring)` blocks; not safe to share. */
    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * Application context captured in [install]. Holding statically
     * is safe because the application context lives for the lifetime
     * of the process — see class-level `@SuppressLint("StaticFieldLeak")`.
     */
    @Volatile private var appCtx: Context? = null

    /**
     * Install on app start. Call from `Application.attachBaseContext`
     * or `onCreate` so a crash during overlay-service launch is also
     * captured.
     */
    fun install(ctx: Context?) {
        if (ctx == null) return
        appCtx = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                writeCrash(t, e)
            } catch (writeFail: Exception) {
                // Last-ditch fallback so a write failure cannot
                // re-crash the crash handler.
                Log.e(TAG, "writeCrash failed inside UncaughtExceptionHandler", writeFail)
            }
            // Chain the previous handler so the system still kills
            // the process and shows the standard crash dialog.
            prev?.uncaughtException(t, e)
        }
        log(
            "CrashLogger installed (app v${safeVersion(ctx)} on " +
                "${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT})",
        )
    }

    /**
     * Append one log line. Thread-safe. Lines are timestamped at
     * call time and persisted to [LATEST_LOG] so an external tool
     * can read them while the app is running.
     */
    fun log(msg: String?) {
        val safeMsg = msg ?: "(null)"
        val line = synchronized(ring) {
            val ts = tsFmt.format(Date())
            val l = "$ts  $safeMsg"
            if (ring.size >= LOG_LINES_KEPT) ring.removeFirst()
            ring.addLast(l)
            l
        }
        Log.i(TAG, safeMsg)
        val ctx = appCtx ?: return
        try {
            val f = File(ctx.filesDir, LATEST_LOG)
            FileWriter(f, /* append = */ true).use { w ->
                w.write(line)
                w.write("\n")
            }
            if (f.length() > 256L * 1024L) trimLog(f)
        } catch (_: Exception) {
            // log() is itself a logging primitive — recursively
            // logging this failure would risk infinite recursion.
        }
    }

    /** Convenience: log a message paired with a [Throwable]. */
    fun logErr(msg: String, e: Throwable?) {
        if (e == null) {
            log(msg)
            return
        }
        log("$msg | ${e.javaClass.simpleName}: ${e.message ?: ""}")
    }

    /** Read the persisted last-crash report, if any. */
    fun readLastCrash(ctx: Context?): String {
        if (ctx == null) return "(no data)"
        return try {
            val f = File(ctx.filesDir, CRASH_FILE)
            if (!f.exists()) {
                "(no crash recorded yet)"
            } else {
                readAll(f)?.toString(StandardCharsets.UTF_8) ?: "(could not read)"
            }
        } catch (t: Exception) {
            "Read error: ${t.message}"
        }
    }

    /**
     * `true` if a crash was recorded in the previous 24 hours. The
     * crash report file is only written by the uncaught-exception
     * handler.
     */
    fun hasRecentCrash(ctx: Context?): Boolean {
        if (ctx == null) return false
        return try {
            val f = File(ctx.filesDir, CRASH_FILE)
            if (!f.exists() || f.length() == 0L) {
                false
            } else {
                (System.currentTimeMillis() - f.lastModified())
                .let { it in 0L until 24L * 60L * 60L * 1000L }
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Read the rolling log file (recent context, not just crashes). */
    fun readRollingLog(ctx: Context?): String {
        if (ctx == null) return "(no data)"
        return try {
            val f = File(ctx.filesDir, LATEST_LOG)
            if (!f.exists()) {
                "(empty)"
            } else {
                readAll(f)?.toString(StandardCharsets.UTF_8) ?: "(could not read)"
            }
        } catch (t: Exception) {
            "Read error: ${t.message}"
        }
    }

    /** Erase the crash file, the rolling log and the in-memory ring. */
    fun clear(ctx: Context?) {
        if (ctx == null) return
        runCatching { File(ctx.filesDir, CRASH_FILE).delete() }
        runCatching { File(ctx.filesDir, LATEST_LOG).delete() }
        synchronized(ring) { ring.clear() }
    }

    private fun writeCrash(thread: Thread?, e: Throwable?) {
        val ctx = appCtx ?: return
        val sb = StringBuilder().apply {
            append("=== CRASH ===\n")
            append("time:    ").append(tsFmt.format(Date())).append('\n')
            append("thread:  ").append(thread?.name ?: "?").append('\n')
            append("version: ").append(safeVersion(ctx)).append('\n')
            append("device:  ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append(" / API ").append(Build.VERSION.SDK_INT).append('\n')
            append("\n--- stack trace ---\n")
            val sw = StringWriter()
            e?.printStackTrace(PrintWriter(sw))
            append(sw.toString())
            append("\n--- recent log ---\n")
            synchronized(ring) {
                for (line in ring) append(line).append('\n')
            }
        }
        try {
            val f = File(ctx.filesDir, CRASH_FILE)
            FileWriter(f, /* append = */ false).use { it.write(sb.toString()) }
        } catch (writeFail: Exception) {
            Log.e(TAG, "writeCrash: failed to persist crash report", writeFail)
        }
    }

    private fun safeVersion(ctx: Context): String = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        // PackageInfoCompat picks the long-form version code on API
        // 28+ and the int versionCode on older releases.
        "${pi.versionName} (${PackageInfoCompat.getLongVersionCode(pi)})"
    } catch (_: Exception) {
        "?"
    }

    private fun readAll(f: File): ByteArray? = try {
        FileInputStream(f).use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    } catch (_: Exception) {
        null
    }

    private fun trimLog(f: File) {
        try {
            val data = readAll(f) ?: return
            val keep = 128 * 1024
            if (data.size <= keep) return
            val from = data.size - keep
            FileOutputStream(f, /* append = */ false).use { it.write(data, from, keep) }
        } catch (trimFail: Exception) {
            Log.w(TAG, "trimLog failed", trimFail)
        }
    }
}
