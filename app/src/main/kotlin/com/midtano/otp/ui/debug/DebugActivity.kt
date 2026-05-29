// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.debug

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.midtano.otp.R
import com.midtano.otp.core.BaseActivity
import com.midtano.otp.system.CrashLogger
import com.midtano.otp.system.LastNotification
import com.midtano.otp.util.LogTextFormatter

/**
 * Debug + logs screen, reachable from Settings → "Logs and debug".
 *
 * Displays:
 * - the last crash report captured by [CrashLogger];
 * - the rolling log file (recent context for support).
 *
 * Both files live in the app's private files directory and can also
 * be pulled via adb (see [CrashLogger]).
 */
class DebugActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)
        bind()

        findViewById<Button?>(R.id.btn_log_refresh)?.setOnClickListener { bind() }
        findViewById<Button?>(R.id.btn_log_clear)?.setOnClickListener {
            CrashLogger.clear(this)
            bind()
            Toast.makeText(this, getString(R.string.toast_logs_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        bind()
    }

    private fun bind() {
        findViewById<TextView?>(R.id.tv_crash)?.text =
            LogTextFormatter.format(CrashLogger.readLastCrash(this))
        findViewById<TextView?>(R.id.tv_log)?.let { log ->
            val combined = buildString {
                append("══ NOTIFICATIONS ══\n\n")
                append(LastNotification.read(this@DebugActivity))
                append("\n\n══ GENERAL LOG ══\n\n")
                append(CrashLogger.readRollingLog(this@DebugActivity))
            }
            log.text = LogTextFormatter.format(combined)
        }
    }
}
