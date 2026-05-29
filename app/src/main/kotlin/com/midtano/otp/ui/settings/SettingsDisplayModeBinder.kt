 // SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

import android.app.Activity
import android.widget.SeekBar
import android.widget.TextView
import com.midtano.otp.R
import com.midtano.otp.data.Prefs
import com.midtano.otp.data.prefs.DisplayMode
import com.midtano.otp.service.overlay.OtpShadeNotifier
import com.midtano.otp.widget.SpringSwitch

/**
 * Wires the display-mode toggles, the dependent back/close-to-shade
 * switches and the shade-duration timer.
 */
internal object SettingsDisplayModeBinder {

    fun bind(host: Activity) {
        val swOverlay = host.findViewById<SpringSwitch?>(R.id.switch_mode_overlay)
        val swShade = host.findViewById<SpringSwitch?>(R.id.switch_mode_shade)
        val swBackToShade = host.findViewById<SpringSwitch?>(R.id.switch_back_to_shade)
        val swCloseToShade = host.findViewById<SpringSwitch?>(R.id.switch_close_to_shade)
        val swBackCopy = host.findViewById<SpringSwitch?>(R.id.switch_back_action)
        val sbDuration = host.findViewById<SeekBar?>(R.id.sb_shade_duration)
        val tvDurationValue = host.findViewById<TextView?>(R.id.tv_shade_duration_value)

        bindDisplayMode(host, swOverlay, swShade)
        bindBackToShade(host, swBackToShade, swBackCopy)
        bindCloseToShade(host, swCloseToShade)
        bindShadeDuration(host, sbDuration, tvDurationValue)
    }

    private fun bindDisplayMode(host: Activity, overlay: SpringSwitch?, shade: SpringSwitch?) {
        if (overlay == null || shade == null) return
        val isShade = Prefs.getDisplayModeTyped(host) == DisplayMode.SHADE
        overlay.setChecked(!isShade, false)
        shade.setChecked(isShade, false)

        val suppress = booleanArrayOf(false)

        overlay.setOnCheckedChangeListener { _, checked ->
            if (suppress[0]) return@setOnCheckedChangeListener
            if (checked) {
                Prefs.setDisplayMode(host, DisplayMode.OVERLAY)
                suppress[0] = true
                shade.setChecked(false, true)
                suppress[0] = false
                // Drop any in-flight shade notifications and remove
                // the channel from system notification settings so
                // the toggle does not linger as dead UI for a
                // feature the user just disabled.
                OtpShadeNotifier.cancelActive(host)
                OtpShadeNotifier.deleteChannel(host)
            } else {
                suppress[0] = true
                overlay.setChecked(true, true)
                suppress[0] = false
            }
        }

        shade.setOnCheckedChangeListener { _, checked ->
            if (suppress[0]) return@setOnCheckedChangeListener
            if (checked) {
                Prefs.setDisplayMode(host, DisplayMode.SHADE)
                suppress[0] = true
                overlay.setChecked(false, true)
                suppress[0] = false
                // Materialise the shade channel up-front so the
                // first OTP arrives without a one-frame gap while
                // the channel registers with the system.
                OtpShadeNotifier.ensureChannel(host)
            } else {
                suppress[0] = true
                shade.setChecked(true, true)
                suppress[0] = false
            }
        }
    }

    private fun bindBackToShade(
        host: Activity,
        backToShade: SpringSwitch?,
        backCopy: SpringSwitch?,
    ) {
        if (backToShade == null) return
        val enabled = Prefs.isBackToShade(host)
        backToShade.setChecked(enabled, false)
        SettingsDescriptionsBinder.applyAnimated(
            host,
            R.id.tv_back_to_shade_desc,
            enabled,
            R.string.desc_back_to_shade_on,
            R.string.desc_back_to_shade_off,
            animate = false,
        )

        backToShade.setOnCheckedChangeListener { _, checked ->
            Prefs.setBackToShade(host, checked)
            SettingsDescriptionsBinder.applyAnimated(
                host,
                R.id.tv_back_to_shade_desc,
                checked,
                R.string.desc_back_to_shade_on,
                R.string.desc_back_to_shade_off,
            )
            if (checked && backCopy != null) {
                Prefs.setBackCopy(host, false)
                backCopy.setChecked(false, true)
            }
        }
    }

    private fun bindCloseToShade(host: Activity, sw: SpringSwitch?) {
        if (sw == null) return
        val enabled = Prefs.isCloseToShade(host)
        sw.setChecked(enabled, false)
        SettingsDescriptionsBinder.applyAnimated(
            host,
            R.id.tv_close_to_shade_desc,
            enabled,
            R.string.desc_close_to_shade_on,
            R.string.desc_close_to_shade_off,
            animate = false,
        )
        sw.setOnCheckedChangeListener { _, checked ->
            Prefs.setCloseToShade(host, checked)
            SettingsDescriptionsBinder.applyAnimated(
                host,
                R.id.tv_close_to_shade_desc,
                checked,
                R.string.desc_close_to_shade_on,
                R.string.desc_close_to_shade_off,
            )
        }
    }

    private fun bindShadeDuration(host: Activity, sb: SeekBar?, label: TextView?) {
        if (sb == null) return
        val min = Prefs.SHADE_DURATION_MIN_MS
        val max = Prefs.SHADE_DURATION_MAX_MS
        sb.max = max - min
        val current = Prefs.getShadeDurationMs(host)
        sb.progress = maxOf(0, current - min)
        label?.text = formatSeconds(host, current)

        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val rounded = roundToStep(min + progress)
                label?.text = formatSeconds(host, rounded)
                if (fromUser) Prefs.setShadeDurationMs(host, rounded)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val rounded = roundToStep(min + seekBar.progress)
                seekBar.progress = rounded - min
                Prefs.setShadeDurationMs(host, rounded)
                label?.text = formatSeconds(host, rounded)
            }
        })
    }

    /** Snap to the nearest 5-second grid for predictable values. */
    private fun roundToStep(ms: Int): Int {
        val step = 5_000
        val snapped = ((ms + step / 2) / step) * step
        return snapped.coerceIn(Prefs.SHADE_DURATION_MIN_MS, Prefs.SHADE_DURATION_MAX_MS)
    }

    private fun formatSeconds(host: Activity, ms: Int): String {
        val seconds = (ms + 500) / 1000
        return host.getString(R.string.settings_shade_duration_value, seconds)
    }
}
