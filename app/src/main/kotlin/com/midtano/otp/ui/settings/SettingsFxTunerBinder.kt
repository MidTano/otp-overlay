// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

import android.app.Activity
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import com.midtano.otp.R
import com.midtano.otp.data.Prefs
import com.midtano.otp.util.toLocaleString
import com.midtano.otp.widget.AnimatedCheckbox
import com.midtano.otp.widget.SpringSwitch
import java.util.Locale

/**
 * Wires every overlay-effect tuner control (toggles + SeekBars) to
 * [Prefs]. Each listener writes the new value immediately so the
 * live overlay (if any is on screen) picks it up via its own
 * SharedPreferences listener; no "save and re-show" step needed.
 */
internal object SettingsFxTunerBinder {

    /** Functional shims — keep the wiring code terse. */
    fun interface IntSink { fun accept(v: Int) }
    fun interface BoolSink { fun accept(v: Boolean) }

    /**
     * Wire the FX tuner. After the descriptions are touched (via
     * the "Copy animation" toggles), [descriptionRefresher] is
     * invoked so [SettingsDescriptionsBinder.update] sees the
     * fresh state.
     */
    fun bind(host: Activity, descriptionRefresher: Runnable?) {
        // Master "panel mute" switch.
        host.findViewById<SpringSwitch?>(R.id.sw_fx_panel_mute)?.let { swPanel ->
            swPanel.setOnCheckedChangeListener(null)
            swPanel.isChecked = Prefs.isFxPanelMute(host)
            SettingsDescriptionsBinder.applyAnimated(
                host,
                R.id.tv_fx_panel_mute_desc,
                swPanel.isChecked,
                R.string.desc_panel_mute_on,
                R.string.desc_panel_mute_off,
                animate = false,
            )
            swPanel.setOnCheckedChangeListener { _, v ->
                Prefs.setFxPanelMute(host, v)
                SettingsDescriptionsBinder.applyAnimated(
                    host,
                    R.id.tv_fx_panel_mute_desc,
                    v,
                    R.string.desc_panel_mute_on,
                    R.string.desc_panel_mute_off,
                )
            }
        }

        // Perimeter glow group.
        bindToggle(
            host.findViewById(R.id.sw_fx_perim_en),
            Prefs.isFxPerimEn(host),
        ) { Prefs.setFxPerimEn(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_perim_inten,
            R.id.val_fx_perim_inten,
            Prefs.getFxPerimInten(host),
        ) { Prefs.setFxPerimInten(host, it) }
        bindToggle(
            host.findViewById(R.id.sw_fx_perim_outer),
            Prefs.isFxPerimOuterEn(host),
        ) { Prefs.setFxPerimOuterEn(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_perim_outer_w,
            R.id.val_fx_perim_outer_w,
            Prefs.getFxPerimOuterW(host),
        ) { Prefs.setFxPerimOuterW(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_perim_outer_bl,
            R.id.val_fx_perim_outer_bl,
            Prefs.getFxPerimOuterBl(host),
        ) { Prefs.setFxPerimOuterBl(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_perim_mid_w,
            R.id.val_fx_perim_mid_w,
            Prefs.getFxPerimMidW(host),
        ) { Prefs.setFxPerimMidW(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_perim_mid_bl,
            R.id.val_fx_perim_mid_bl,
            Prefs.getFxPerimMidBl(host),
        ) { Prefs.setFxPerimMidBl(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_perim_in_w,
            R.id.val_fx_perim_in_w,
            Prefs.getFxPerimInWidthSteps(host),
        ) { Prefs.setFxPerimInWidthSteps(host, it) }

        // Breathing.
        bindToggle(
            host.findViewById(R.id.sw_fx_breath_en),
            Prefs.isFxBreathEn(host),
        ) { Prefs.setFxBreathEn(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_breath_amt,
            R.id.val_fx_breath_amt,
            Prefs.getFxBreathAmt(host),
        ) { Prefs.setFxBreathAmt(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_breath_period,
            R.id.val_fx_breath_period,
            Prefs.getFxBreathPeriod(host),
        ) { Prefs.setFxBreathPeriod(host, maxOf(500, it)) }

        // Rotation.
        bindToggle(
            host.findViewById(R.id.sw_fx_rot_en),
            Prefs.isFxRotEn(host),
        ) { Prefs.setFxRotEn(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_rot_period,
            R.id.val_fx_rot_period,
            Prefs.getFxRotPeriod(host),
        ) { Prefs.setFxRotPeriod(host, maxOf(1000, it)) }

        // Sweep stops + hue range.
        bindSeekVal(
            host,
            R.id.sb_fx_sweep_stops,
            R.id.val_fx_sweep_stops,
            Prefs.getFxSweepStops(host),
        ) { Prefs.setFxSweepStops(host, maxOf(1, it)) }
        bindSeekVal(
            host,
            R.id.sb_fx_sweep_hue_range,
            R.id.val_fx_sweep_hue_range,
            Prefs.getFxSweepHueRange(host),
        ) { Prefs.setFxSweepHueRange(host, it) }

        // Reveal-phase channels.
        bindToggle(
            host.findViewById(R.id.sw_fx_halo_en),
            Prefs.isFxHaloEn(host),
        ) { Prefs.setFxHaloEn(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_halo_inten,
            R.id.val_fx_halo_inten,
            Prefs.getFxHaloInten(host),
        ) { Prefs.setFxHaloInten(host, it) }

        bindToggle(
            host.findViewById(R.id.sw_fx_wave_en),
            Prefs.isFxWaveEn(host),
        ) { Prefs.setFxWaveEn(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_wave_inten,
            R.id.val_fx_wave_inten,
            Prefs.getFxWaveInten(host),
        ) { Prefs.setFxWaveInten(host, it) }

        bindToggle(
            host.findViewById(R.id.sw_fx_halftone_en),
            Prefs.isFxHalftoneEn(host),
        ) { Prefs.setFxHalftoneEn(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_halftone_inten,
            R.id.val_fx_halftone_inten,
            Prefs.getFxHalftoneInten(host),
        ) { Prefs.setFxHalftoneInten(host, it) }

        bindToggle(
            host.findViewById(R.id.sw_fx_spark_en),
            Prefs.isFxSparkEn(host),
        ) { Prefs.setFxSparkEn(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_spark_inten,
            R.id.val_fx_spark_inten,
            Prefs.getFxSparkInten(host),
        ) { Prefs.setFxSparkInten(host, it) }

        // Countdown.
        bindToggle(
            host.findViewById(R.id.sw_fx_countdown_en),
            Prefs.isFxCountdownEn(host),
        ) { Prefs.setFxCountdownEn(host, it) }
        bindSeekVal(
            host,
            R.id.sb_fx_countdown_inten,
            R.id.val_fx_countdown_inten,
            Prefs.getFxCountdownInten(host),
        ) { Prefs.setFxCountdownInten(host, it) }

        // Countdown style picker (Shrink / Sweep).
        val cbShrink = host.findViewById<AnimatedCheckbox?>(R.id.rb_fx_cd_shrink)
        val cbSweep = host.findViewById<AnimatedCheckbox?>(R.id.rb_fx_cd_sweep)
        val rowShrink = host.findViewById<View?>(R.id.row_fx_cd_shrink)
        val rowSweep = host.findViewById<View?>(R.id.row_fx_cd_sweep)
        if (cbShrink != null && cbSweep != null) {
            val s = Prefs.getFxCountdownStyle(host)
            cbShrink.setChecked(s == Prefs.COUNTDOWN_SHRINK_BOTTOM, false)
            cbSweep.setChecked(s == Prefs.COUNTDOWN_SWEEP_FULL, false)

            val selectShrink = Runnable {
                cbShrink.isChecked = true
                cbSweep.isChecked = false
                Prefs.setFxCountdownStyle(host, Prefs.COUNTDOWN_SHRINK_BOTTOM)
            }
            val selectSweep = Runnable {
                cbShrink.isChecked = false
                cbSweep.isChecked = true
                Prefs.setFxCountdownStyle(host, Prefs.COUNTDOWN_SWEEP_FULL)
            }

            rowShrink?.setOnClickListener { selectShrink.run() }
            rowSweep?.setOnClickListener { selectSweep.run() }
            cbShrink.setOnCheckedChangeListener { c, b ->
                if (b) {
                    selectShrink.run()
                } else if (!cbSweep.isChecked) c.setChecked(true, false)
            }
            cbSweep.setOnCheckedChangeListener { c, b ->
                if (b) {
                    selectSweep.run()
                } else if (!cbShrink.isChecked) c.setChecked(true, false)
            }
        }

        // Frosted-glass blur.
        bindToggle(
            host.findViewById(R.id.sw_fx_blur_en),
            Prefs.isFxBlurEn(host),
        ) { Prefs.setFxBlurEn(host, it) }

        // Timings.
        bindSeekVal(
            host,
            R.id.sb_fx_reveal_ms,
            R.id.val_fx_reveal_ms,
            Prefs.getFxRevealMs(host),
        ) { Prefs.setFxRevealMs(host, maxOf(200, it)) }
        bindSeekVal(
            host,
            R.id.sb_fx_dismiss_ms,
            R.id.val_fx_dismiss_ms,
            Prefs.getFxDismissMs(host),
        ) { Prefs.setFxDismissMs(host, maxOf(50, it)) }

        // Edge-fade.
        bindSeekVal(
            host,
            R.id.sb_fx_edge_fade,
            R.id.val_fx_edge_fade,
            Prefs.getFxEdgeFade(host),
        ) { Prefs.setFxEdgeFade(host, it) }

        // Lottie copy animation.
        bindToggle(
            host.findViewById(R.id.sw_fx_copy_lottie_en),
            Prefs.isFxCopyLottieEn(host),
        ) {
            Prefs.setFxCopyLottieEn(host, it)
            descriptionRefresher?.run()
        }
        // Lottie inside the "code pasted" pill.
        bindToggle(
            host.findViewById(R.id.sw_fx_autopaste_lottie_en),
            Prefs.isFxAutopasteLottieEn(host),
        ) {
            Prefs.setFxAutopasteLottieEn(host, it)
            descriptionRefresher?.run()
        }

        // Lottie copy-animation playback speed.
        bindCopyLottieSpeed(host)
    }

    private fun bindToggle(s: SpringSwitch?, initial: Boolean, sink: BoolSink) {
        if (s == null) return
        s.setOnCheckedChangeListener(null)
        s.setChecked(initial, false)
        s.setOnCheckedChangeListener { _, v -> sink.accept(v) }
    }

    /**
     * Bind a SeekBar to a Prefs setter and a sibling TextView.
     * Writes to Prefs on every change. Asks the parent ScrollView
     * not to intercept touches during a horizontal drag.
     */
    private fun bindSeekVal(host: Activity, sbId: Int, valId: Int, initial: Int, sink: IntSink) {
        val sb = host.findViewById<SeekBar?>(sbId) ?: return
        val valLabel = host.findViewById<TextView?>(valId)
        sb.setOnSeekBarChangeListener(null)
        val clamped = initial.coerceIn(0, sb.max)
        sb.progress = clamped
        valLabel?.text = clamped.toLocaleString()

        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                valLabel?.text = progress.toLocaleString()
                if (fromUser) sink.accept(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                seekBar.parent?.requestDisallowInterceptTouchEvent(true)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sink.accept(seekBar.progress)
                seekBar.parent?.requestDisallowInterceptTouchEvent(false)
            }
        })
    }

    /** Lottie speed-slider binding with `1.5x` formatting. */
    private fun bindCopyLottieSpeed(host: Activity) {
        val sb = host.findViewById<SeekBar?>(R.id.sb_fx_copy_lottie_speed) ?: return
        val valLabel = host.findViewById<TextView?>(R.id.val_fx_copy_lottie_speed)
        sb.setOnSeekBarChangeListener(null)
        val initial = Prefs.getFxCopyLottieSpeed(host)
        val clamped = initial.coerceIn(50, 300)
        sb.progress = clamped
        valLabel?.text = String.format(Locale.US, "%.2fx", clamped / 100f)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val p = maxOf(50, progress)
                valLabel?.text = String.format(Locale.US, "%.2fx", p / 100f)
                if (fromUser) Prefs.setFxCopyLottieSpeed(host, p)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                seekBar.parent?.requestDisallowInterceptTouchEvent(true)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val p = maxOf(50, seekBar.progress)
                Prefs.setFxCopyLottieSpeed(host, p)
                seekBar.parent?.requestDisallowInterceptTouchEvent(false)
            }
        })
    }
}
