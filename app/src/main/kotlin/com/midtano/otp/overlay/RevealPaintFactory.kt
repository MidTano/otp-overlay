// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/**
 * One-off [Paint] configuration helpers. Pure setters, no draw logic.
 *
 * Each method returns the same [Paint] so calls compose:
 * ```
 * val p = RevealPaintFactory.strokeRound(Paint(Paint.ANTI_ALIAS_FLAG))
 * ```
 */
internal object RevealPaintFactory {

    /** Stroke + round join + round cap (idle perimeter sparks). */
    fun strokeRound(p: Paint): Paint {
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeCap = Paint.Cap.ROUND
        return p
    }

    /** Stroke + round cap only (default join — wave channel). */
    fun strokeRoundCap(p: Paint): Paint {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        return p
    }

    /** Plain stroke (perimeter ring layers). */
    fun stroke(p: Paint): Paint {
        p.style = Paint.Style.STROKE
        return p
    }

    /** Solid fill (halftone dots, copy flash, badge backdrop). */
    fun fill(p: Paint): Paint {
        p.style = Paint.Style.FILL
        return p
    }

    /**
     * Configure for [PorterDuff.Mode.DST_IN] masking — used to clip
     * the radial fade against the panel rect on the card layer.
     */
    fun dstInMask(p: Paint): Paint {
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        return p
    }
}
