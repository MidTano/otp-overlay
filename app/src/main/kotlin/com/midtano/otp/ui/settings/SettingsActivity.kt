// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.midtano.otp.R
import com.midtano.otp.core.BaseActivity
import com.midtano.otp.data.Prefs
import com.midtano.otp.extractor.OtpExtractor
import com.midtano.otp.service.NotificationMirror
import com.midtano.otp.system.CrashLogger
import com.midtano.otp.system.LastNotification
import com.midtano.otp.ui.debug.DebugActivity
import com.midtano.otp.util.LogTextFormatter
import com.midtano.otp.widget.SpringSwitch
import java.util.regex.Pattern

/**
 * Settings screen.
 *
 * The activity itself owns the toggle wiring and lifecycle hooks.
 * Every editor that has its own state lives in a dedicated binder
 * (see `Settings*Binder`), so adding a new section means dropping
 * in another binder call from [onCreate] rather than growing this
 * file.
 *
 * Sections:
 * - toggle switches (back action, auto-paste, silence push, …);
 * - regex editor with live validation;
 * - test-extraction panel and last-notification diagnostic;
 * - per-app filter list (delegated to [SettingsAppListBinder]);
 * - phrase / trigger / extraction editors (delegated to their
 *   respective `Settings*Binder` objects).
 */
class SettingsActivity : BaseActivity() {

    private lateinit var appList: SettingsAppListBinder

    /**
     * Memoised compiled-regex preview. We re-validate on every
     * keystroke but cache the last result so paste-then-keep-typing
     * hits a no-op fast path.
     */
    private var lastValidatedRegex: String? = null
    private var lastValidatedOk: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Clear the locale tags inserted by LocaleInflaterFactory so
        // LocaleSwitcher does not fight us by re-applying the
        // layout-default `@string/` on a locale change. Dynamic
        // descriptions are then populated manually by
        // [SettingsDescriptionsBinder].
        SettingsDescriptionsBinder.clearDynamicDescTags(this)

        appList = SettingsAppListBinder(this, lifecycleScope)

        bindToggles()
        bindRegexEditor()
        bindTestExtractionPanel()
        bindLastNotificationPanel()
        bindResetAndAdvanced()

        SettingsFxLevelBinder.bind(this)
        SettingsFxTunerBinder.bind(this) { updateDynamicDescriptions() }
        SettingsLanguageBinder.bind(this)
        SettingsTriggerWordsBinder.bind(this)
        SettingsStopWordsBinder.bind(this)
        SettingsExtractionBinder.bind(this)
        SettingsDisplayModeBinder.bind(this)
        appList.bindControls()

        updateDynamicDescriptions()
    }

    override fun onResume() {
        super.onResume()
        updateDynamicDescriptions()
    }

    override fun onDestroy() {
        // Cancel the per-keystroke debounced filter runnable so the
        // global main Handler doesn't keep this activity reachable
        // for one debounce-window past finish().
        if (::appList.isInitialized) appList.dispose()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Refresh descriptions instantly after a locale change so
        // subsequent toggles only animate the single description
        // that actually changed (instead of all of them catching up
        // to the new language).
        updateDynamicDescriptions(animate = false)
    }

    private fun bindToggles() {
        val swBack = findViewById<SpringSwitch>(R.id.switch_back_action)
        val swAutoPaste = findViewById<SpringSwitch>(R.id.switch_auto_paste)
        val swApNoCopy = findViewById<SpringSwitch>(R.id.switch_autopaste_no_copy)
        val swHideHup = findViewById<SpringSwitch>(R.id.switch_hide_headsup)
        val swSilence = findViewById<SpringSwitch>(R.id.switch_silence_push)
        val swSounds = findViewById<SpringSwitch>(R.id.switch_sounds)
        val swFilter = findViewById<SpringSwitch>(R.id.switch_filter_apps)
        val swSmartPaste = findViewById<SpringSwitch?>(R.id.switch_smart_paste)
        val btnAccess = findViewById<Button>(R.id.btn_accessibility)

        swBack.setChecked(Prefs.isBackCopy(this), false)
        swAutoPaste.setChecked(Prefs.isAutoPaste(this), false)
        swApNoCopy.setChecked(Prefs.isAutopasteNoCopy(this), false)
        swHideHup.setChecked(Prefs.isHideHeadsUp(this), false)
        swSilence.setChecked(Prefs.isSilencePush(this), false)
        swSounds.setChecked(Prefs.isSounds(this), false)
        swFilter.setChecked(Prefs.isFilterApps(this), false)
        swSmartPaste?.setChecked(Prefs.isSmartPaste(this), false)

        swBack.setOnCheckedChangeListener { _, v ->
            Prefs.setBackCopy(this, v)
            // Back-tap-to-copy and back-to-shade are mutually
            // exclusive — flipping one off the other is the only
            // sensible UX, and easier to enforce here than to
            // explain in two separate dialog texts.
            if (v) {
                Prefs.setBackToShade(this, false)
                findViewById<SpringSwitch?>(R.id.switch_back_to_shade)?.setChecked(false, true)
                findViewById<TextView?>(R.id.tv_back_to_shade_desc)?.text =
                    getString(R.string.desc_back_to_shade_off)
            }
            updateDynamicDescriptions()
        }
        swAutoPaste.setOnCheckedChangeListener { _, v ->
            Prefs.setAutoPaste(this, v)
            updateDynamicDescriptions()
        }
        swApNoCopy.setOnCheckedChangeListener { _, v ->
            Prefs.setAutopasteNoCopy(this, v)
            updateDynamicDescriptions()
        }
        swHideHup.setOnCheckedChangeListener { _, v ->
            Prefs.setHideHeadsUp(this, v)
            // Materialise / drop the silent-mirror channel in
            // sync with the toggle so the per-app system
            // notification settings only show the channel for an
            // active feature.
            if (v) {
                NotificationMirror.ensureChannel(this)
            } else {
                NotificationMirror.deleteChannel(this)
            }
            updateDynamicDescriptions()
        }
        swSilence.setOnCheckedChangeListener { _, v ->
            Prefs.setSilencePush(this, v)
            updateDynamicDescriptions()
        }
        swSounds.setOnCheckedChangeListener { _, v ->
            Prefs.setSounds(this, v)
            updateDynamicDescriptions()
        }
        swSmartPaste?.setOnCheckedChangeListener { _, v ->
            Prefs.setSmartPaste(this, v)
            updateDynamicDescriptions()
        }
        swFilter.setOnCheckedChangeListener { _, v ->
            Prefs.setFilterApps(this, v)
            appList.toggleSection(v)
            updateDynamicDescriptions()
        }

        btnAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun bindRegexEditor() {
        val etRegex = findViewById<EditText>(R.id.et_regex)
        val btnRegexSave = findViewById<Button>(R.id.btn_regex_save)
        val btnRegexReset = findViewById<Button>(R.id.btn_regex_reset)
        val tvRegexStatus = findViewById<TextView>(R.id.tv_regex_status)

        etRegex.setText(Prefs.getRegex(this))
        etRegex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence, st: Int, b: Int, c: Int) {
                val src = s.toString()
                val ok: Boolean
                var error: String? = null
                if (src == lastValidatedRegex) {
                    ok = lastValidatedOk
                } else {
                    ok = try {
                        Pattern.compile(src)
                        true
                    } catch (e: java.util.regex.PatternSyntaxException) {
                        error = e.message
                        false
                    }
                    lastValidatedRegex = src
                    lastValidatedOk = ok
                }
                if (ok) {
                    tvRegexStatus.text = getString(R.string.regex_valid)
                    tvRegexStatus.setTextColor(
                        ContextCompat.getColor(this@SettingsActivity, R.color.status_pill_ok),
                    )
                } else {
                    tvRegexStatus.text = getString(R.string.regex_invalid, error.toString())
                    tvRegexStatus.setTextColor(
                        ContextCompat.getColor(this@SettingsActivity, R.color.status_pill_err),
                    )
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        btnRegexSave.setOnClickListener {
            val r = etRegex.text.toString().trim()
            try {
                Pattern.compile(r)
                Prefs.setRegex(this, r)
                Toast.makeText(this, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
            } catch (_: java.util.regex.PatternSyntaxException) {
                // Only catch the actual "regex didn't compile" exception so a
                // genuine bug elsewhere (NPE, OOM) still surfaces normally.
                Toast.makeText(this, getString(R.string.toast_invalid_regex), Toast.LENGTH_SHORT).show()
            }
        }
        btnRegexReset.setOnClickListener {
            Prefs.setRegex(this, OtpExtractor.DEFAULT_REGEX)
            etRegex.setText(OtpExtractor.DEFAULT_REGEX)
            Toast.makeText(this, getString(R.string.toast_reset), Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindTestExtractionPanel() {
        val etTest = findViewById<EditText?>(R.id.et_test_input) ?: return
        val btnTest = findViewById<Button?>(R.id.btn_test_run) ?: return
        val tvTestRes = findViewById<TextView?>(R.id.tv_test_result) ?: return
        btnTest.setOnClickListener {
            val report: String = try {
                val input = etTest.text?.toString().orEmpty()
                OtpExtractor.diagnose(this, input)
            } catch (t: Exception) {
                // Diagnostic surface — any failure must render
                // back to the user as the report instead of
                // crashing the activity. Wide catch is intentional.
                "${getString(R.string.settings_test_error)}${t.javaClass.simpleName}: ${t.message}"
            }
            try {
                tvTestRes.text = LogTextFormatter.format(report)
            } catch (e: IndexOutOfBoundsException) {
                // SpannableStringBuilder.setSpan can throw IOOBE when
                // a regex pattern in LogTextFormatter matches a span
                // boundary out of range; the unformatted text still
                // shows correctly above this line.
                CrashLogger.logErr("SettingsActivity: tvTestRes.setText failed", e)
            }
        }
    }

    private fun bindLastNotificationPanel() {
        val tvLastNotif = findViewById<TextView?>(R.id.tv_last_notif) ?: return
        val btnRefresh = findViewById<Button?>(R.id.btn_last_notif_refresh)
        val btnClearLog = findViewById<Button?>(R.id.btn_last_notif_clear)
        tvLastNotif.text = LogTextFormatter.format(LastNotification.read(this))
        btnRefresh?.setOnClickListener {
            tvLastNotif.text = LogTextFormatter.format(LastNotification.read(this))
        }
        btnClearLog?.setOnClickListener {
            LastNotification.clear(this)
            tvLastNotif.text = LogTextFormatter.format(LastNotification.read(this))
            Toast.makeText(this, getString(R.string.toast_log_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindResetAndAdvanced() {
        // Global "Reset all settings" — wipes every preference
        // back to defaults. Confirmation dialog so a stray tap
        // doesn't nuke the user's allow-list / regex / FX tuning.
        findViewById<Button?>(R.id.btn_reset_all)?.setOnClickListener {
            showResetAllDialog()
        }
        findViewById<Button?>(R.id.btn_open_debug)?.setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }
        // Toggle for the advanced settings panel — flips visibility
        // and updates its caption so the user always knows what
        // the next tap will do.
        val btnAdv = findViewById<Button?>(R.id.btn_toggle_advanced) ?: return
        val advPanel = findViewById<View?>(R.id.advanced_panel) ?: return
        btnAdv.setOnClickListener {
            val show = advPanel.visibility != View.VISIBLE
            advPanel.visibility = if (show) View.VISIBLE else View.GONE
            btnAdv.text = if (show) {
                getString(R.string.btn_advanced_hide)
            } else {
                getString(R.string.btn_advanced_show)
            }
        }
    }

    private fun showResetAllDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_reset_all_confirm_title)
            .setMessage(R.string.settings_reset_all_confirm_msg)
            .setPositiveButton(R.string.settings_reset_all_confirm_ok) { _, _ ->
                Prefs.resetAllExceptOnboarding(this)
                Toast.makeText(this, R.string.toast_reset_all_done, Toast.LENGTH_SHORT).show()
                recreate()
            }
            .setNegativeButton(R.string.settings_reset_all_confirm_cancel, null)
            .show()
    }

    private fun updateDynamicDescriptions(animate: Boolean = true) {
        SettingsDescriptionsBinder.update(this, animate)
    }
}
