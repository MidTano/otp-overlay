// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

import android.app.Activity
import android.view.View
import com.midtano.otp.R
import com.midtano.otp.data.Prefs
import com.midtano.otp.data.prefs.FxLevel
import com.midtano.otp.widget.AnimatedCheckbox

/**
 * Wires the radio-style checkboxes that map to [FxLevel.LITE] and
 * [FxLevel.ULTRA]. Defaults to [FxLevel.DEFAULT] when nothing is
 * stored.
 *
 * The setting takes effect on the next overlay; the value is read
 * in `OtpRevealLayout.onAttachedToWindow`.
 */
internal object SettingsFxLevelBinder {

    fun bind(host: Activity) {
        val rbL = host.findViewById<AnimatedCheckbox?>(R.id.rb_fx_lite)
        val rbU = host.findViewById<AnimatedCheckbox?>(R.id.rb_fx_ultra)
        val rowL = host.findViewById<View?>(R.id.row_fx_lite)
        val rowU = host.findViewById<View?>(R.id.row_fx_ultra)
        if (rbL == null || rbU == null) return

        val level = Prefs.getFxLevelTyped(host)
        rbL.setChecked(level == FxLevel.LITE, false)
        rbU.setChecked(level == FxLevel.ULTRA, false)

        fun select(picked: FxLevel) {
            rbL.isChecked = picked == FxLevel.LITE
            rbU.isChecked = picked == FxLevel.ULTRA
            Prefs.setFxLevel(host, picked)
        }

        rowL?.setOnClickListener { select(FxLevel.LITE) }
        rowU?.setOnClickListener { select(FxLevel.ULTRA) }
        rbL.setOnCheckedChangeListener { c, b ->
            if (b) {
                select(FxLevel.LITE)
            } // Don't allow both checkboxes to clear — one must stay
            // selected for the radio-group invariant.
            else if (!rbU.isChecked) c.setChecked(true, false)
        }
        rbU.setOnCheckedChangeListener { c, b ->
            if (b) {
                select(FxLevel.ULTRA)
            } else if (!rbL.isChecked) c.setChecked(true, false)
        }
    }
}
