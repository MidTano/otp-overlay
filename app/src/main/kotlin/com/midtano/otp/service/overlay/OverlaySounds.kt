// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.midtano.otp.R
import com.midtano.otp.data.Prefs
import com.midtano.otp.system.CrashLogger

/** SFX playback for the overlay (pop / success / auto-paste). */
internal class OverlaySounds(ctx: Context) {

    private val appCtx: Context = ctx.applicationContext
    private var soundPool: SoundPool? = null
    private var soundIdPop: Int = 0
    private var soundIdSuccess: Int = 0
    private var soundIdAutopaste: Int = 0

    @Volatile private var soundsLoaded: Boolean = false

    /** Build the [SoundPool] and load the SFX pack asynchronously. */
    fun init() {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pool = SoundPool.Builder()
                .setAudioAttributes(attrs)
                .setMaxStreams(4)
                .build()
            pool.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) soundsLoaded = true
            }
            soundIdPop = pool.load(appCtx, R.raw.sfx_pop, 1)
            soundIdSuccess = pool.load(appCtx, R.raw.sfx_success, 1)
            soundIdAutopaste = pool.load(appCtx, R.raw.sfx_autopaste, 1)
            soundPool = pool
        } catch (e: Exception) {
            CrashLogger.logErr("OverlaySounds: SoundPool init failed; SFX disabled", e)
            soundPool = null
        }
    }

    fun currentPopId(): Int = soundIdPop
    fun currentSuccessId(): Int = soundIdSuccess

    fun playAutoPasteSound() {
        playSound(soundIdAutopaste, 1.0f)
    }

    fun playSound(id: Int, volume: Float) {
        if (!Prefs.isSounds(appCtx)) return
        val pool = soundPool ?: return
        if (!soundsLoaded || id == 0) return
        try {
            pool.play(id, volume, volume, 1, 0, 1f)
        } catch (_: IllegalStateException) {
            // SoundPool.play throws IllegalStateException when the
            // pool is being recycled mid-call; silent on the hot
            // teardown path to avoid logspam.
        }
    }

    fun release() {
        soundPool?.let {
            try {
                it.release()
            } catch (_: IllegalStateException) {
                // release() throws when already torn down — harmless.
            }
        }
        soundPool = null
        soundsLoaded = false
    }
}
