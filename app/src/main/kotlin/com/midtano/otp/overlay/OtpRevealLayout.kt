// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.airbnb.lottie.LottieAnimationView
import com.midtano.otp.data.Prefs
import com.midtano.otp.data.prefs.FxLevel
import com.midtano.otp.system.CrashLogger
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Hosts the OTP card and orchestrates four animation phases on top
 * of it: REVEAL, IDLE, COPY, DISMISS. Composites a chromatic
 * perimeter glow, radial fade, blur snapshot and Lottie copy
 * animation around the card.
 *
 * The class deliberately stays single-file because the four phases
 * share mutable per-instance state (current alpha for each channel,
 * cached path scratch buffers, geometry caches keyed off the card
 * rect) and splitting them into four classes would force every
 * helper to hold a back-reference to the layout. Setters and
 * pure-math helpers live alongside in the package:
 *
 * - [RevealTimings] — duration / crossfade constants.
 * - [RevealPalette] — colour ramp and default sweep loop.
 * - [RevealMath], [RevealMaskCache], [SweepLoopBuilder] — pure-math
 *   helpers with no Android dependency.
 * - [RevealPaintFactory] — paint stroke / fill / xfermode setup.
 * - [CountdownDrawer], [CardBlurDrawer], [EdgeFadeMask],
 *   [RevealEffectsRenderer] — self-contained renderers owning their
 *   own scratch state.
 * - [FxKnobs] — runtime FX preset multipliers.
 *
 * What remains here is the animator orchestration — four
 * [ValueAnimator]s, two [SpringAnimation]s, the copy-pulse Lottie
 * listener — and the per-channel alpha plumbing.
 */
class OtpRevealLayout @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    style: Int = 0,
) : FrameLayout(ctx, attrs, style) {

    private var sweepLoop: IntArray = RevealPalette.defaultSweepLoop()

    private val easeOut = PathInterpolator(0.16f, 1f, 0.30f, 1f)
    private val easeStd = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val easeIn = PathInterpolator(0.32f, 0f, 0.7f, 0.85f)

    /** Extreme deceleration so the blur swells in rather than snapping on. */
    private val blurEase = PathInterpolator(0.42f, 0f, 0.0f, 1f)

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waveStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val perimeterIn = Paint(Paint.ANTI_ALIAS_FLAG)
    private val perimeterOut = Paint(Paint.ANTI_ALIAS_FLAG)
    private val perimeterMid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val countdownGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val countdownCore = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Path / paint scratch for the countdown stroke. */
    private val countdownDrawer = CountdownDrawer()

    /** 1 = full time remaining, 0 = expired. Drives the countdown stroke. */
    private var countdownT: Float = 1f

    private val sparkPath = Path()
    private val cardRect = RectF()

    /** Scratch [RectF] reused per frame so [drawPerimeterGlow] doesn't allocate. */
    private val scratchRect = RectF()
    private var cx: Float = 0f
    private var cy: Float = 0f
    private var maxRadius: Float = 0f
    private var density: Float = 1f

    private var animStartMs: Long = 0L
    private var revealStarted: Boolean = false
    private var dismissing: Boolean = false
    private var copyActive: Boolean = false
    private var effectsAlpha: Float = 1f

    /**
     * Master multiplier for ALL drawn idle/reveal effect channels.
     * Driven by [setPanelEffectsAlpha] so the queue panel can fade
     * everything out before stretching the card. While this is
     * below 1, a 1-dp brand-coloured outline is drawn around the
     * card so the silhouette never disappears mid-animation.
     */
    private var panelEffectsAlpha: Float = 1f
    private var panelEffectsAnim: ValueAnimator? = null

    /** Lazy paint for the placeholder outline drawn while [panelEffectsAlpha] < 1. */
    private var simpleOutlinePaint: Paint? = null

    private var perimeterPhase: Float = 0f // 0..360°
    private var perimeterBreath: Float = 1f // 0.75..1.0 pulse

    /**
     * Frosted-glass blur applied after copy. Snapshot lifecycle and
     * the per-frame composite live in [CardBlurDrawer].
     */
    private val cardBlur = CardBlurDrawer()

    /** Strength of the blur overlay 0..1. Stays at 1 once set. */
    private var blurAlpha: Float = 0f
    private var blurAnimator: ValueAnimator? = null

    /**
     * Every overlay-effect knob the user can adjust, collected into
     * a single mutable holder so the hot draw loop can read them
     * without 30+ field lookups.
     */
    private val knobs = FxKnobs()

    /** Last brand colour applied via [setBrandColor], kept for sweep rebuilds. */
    private var lastBrandColor: Int = 0

    /** Live-edit listener so changes in Settings update an attached overlay. */
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    // Shaders (sweep / linear / radial) are allocated once and
    // rotated with `setLocalMatrix` per frame instead of being
    // rebuilt every draw.
    private val sweepMatrix = Matrix()

    /** Two-pass edge-fade mask owns its own gradient cache. */
    private val edgeFadeMask = EdgeFadeMask()

    /** Radial mask used by the reveal composite. */
    private val revealMaskCache = RevealMaskCache()

    private var cachedPerimeterSweep: SweepGradient? = null
    private var cachedPerimeterCx: Float = Float.NaN
    private var cachedPerimeterCy: Float = Float.NaN
    private var cachedWaveSweep: SweepGradient? = null

    /** Single-element holder so [RevealEffectsRenderer.drawWave] can update it. */
    private val waveSweepCache: Array<SweepGradient?> = arrayOfNulls(1)

    /** Reveal-effect intensity — read once on attach to keep prefs out of draw. */
    private var fxLevel: FxLevel = FxLevel.LITE
    private var fxUltra: Boolean = false

    /**
     * Reduce-and-soften pass for the small auto-paste pill: shrinks
     * perimeter strokes, drops the outer halo and uses a softer
     * multi-stop edge fade.
     */
    private var compact: Boolean = false

    /** Card-in springs, kept as fields so [startDismiss] can cancel them. */
    private var springX: SpringAnimation? = null
    private var springY: SpringAnimation? = null

    /** Posted [Runnable] that starts the springs after the alpha delay. */
    private var cardSpringRunnable: Runnable? = null

    private val ticker: Runnable = Runnable { tick() }

    /** Lottie copy animation player — owns its own [LottieAnimationView]. */
    private val copyLottiePlayer = CopyLottiePlayer(this)

    /** True while drawing the Lottie copy view in the final phase of `dispatchDraw`. */
    private var drawingCopyLottieFinal: Boolean = false

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false

        // Paint configuration is delegated to RevealPaintFactory so
        // the boilerplate stroke/cap/fill setup lives in one place.
        RevealPaintFactory.strokeRound(sparkPaint)
        RevealPaintFactory.strokeRound(sparkGlow)
        RevealPaintFactory.strokeRoundCap(waveStroke)

        RevealPaintFactory.fill(dotPaint)
        RevealPaintFactory.fill(dotGlow)

        RevealPaintFactory.stroke(perimeterIn)
        RevealPaintFactory.stroke(perimeterMid)
        RevealPaintFactory.stroke(perimeterOut)

        RevealPaintFactory.dstInMask(maskPaint)

        RevealPaintFactory.strokeRound(countdownGlow)
        RevealPaintFactory.strokeRound(countdownCore)
    }

    /**
     * Drive the countdown stroke. `1f` = full time remaining,
     * `0f` = expired.
     */
    fun setCountdown(t: Float) {
        countdownT = t.coerceIn(0f, 1f)
        invalidate()
    }

    /**
     * Snap the countdown line down to zero with an accelerating
     * curve. Called when the overlay is about to dismiss so the bar
     * does not stay frozen at half-shrunk while the rest of the card
     * fades.
     */
    fun collapseCountdown(durationMs: Long) {
        if (countdownT <= 0f) return
        val from = countdownT
        ValueAnimator.ofFloat(from, 0f).apply {
            duration = maxOf(40L, durationMs)
            interpolator = PathInterpolator(0.55f, 0f, 0.85f, 0.45f)
            addUpdateListener {
                countdownT = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    countdownT = 0f
                    invalidate()
                }
            })
            start()
        }
        ensureTicker()
    }

    /**
     * Tint the perimeter / wave / spark sweep gradient from one
     * brand colour. Stop count and hue spread are user-tunable in
     * Settings. Pass `0` to restore the default indigo palette.
     */
    fun setBrandColor(color: Int) {
        lastBrandColor = color
        rebuildSweepLoop()
        // Invalidate every cached sweep so the next draw rebuilds at
        // the new palette. Cheap — these allocate exactly once per
        // overlay show.
        cachedPerimeterSweep = null
        cachedWaveSweep = null
        invalidate()
    }

    /**
     * Build the [SweepGradient] stops using the current brand colour
     * ([lastBrandColor]) and the user's stop-count / hue-range knobs.
     */
    private fun rebuildSweepLoop() {
        sweepLoop = SweepLoopBuilder.build(knobs.sweepStops, knobs.sweepHueRange, lastBrandColor)
    }

    /**
     * Master crossfade for ALL idle / reveal effect channels. Used
     * by `OverlayService` when the queue panel is expanded or
     * collapsed. Honours the user's "mute effects on panel expand"
     * toggle: when off this is a no-op.
     */
    fun setPanelEffectsAlpha(target: Float, duration: Long) {
        if (!knobs.panelMute) {
            panelEffectsAnim?.cancel()
            panelEffectsAlpha = 1f
            invalidate()
            return
        }
        val clamped = target.coerceIn(0f, 1f)
        panelEffectsAnim?.cancel()
        if (duration <= 0L) {
            panelEffectsAlpha = clamped
            invalidate()
            return
        }
        panelEffectsAnim = ValueAnimator.ofFloat(panelEffectsAlpha, clamped).apply {
            this.duration = duration
            interpolator = easeOut
            addUpdateListener {
                panelEffectsAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        ensureTicker()
    }

    /**
     * Snapshot every Prefs.* knob into a local field. Called once
     * on attach and again whenever the user moves a slider in
     * Settings.
     */
    private fun loadKnobs() {
        val sweepDirty = knobs.loadFrom(context)
        if (sweepDirty) {
            rebuildSweepLoop()
            cachedPerimeterSweep = null
            cachedWaveSweep = null
        }
        applyMaskFilters()
        edgeFadeMask.invalidate()
    }

    /**
     * Re-derive every [BlurMaskFilter] from the current density and
     * user-tuned knob values.
     *
     * Single source of truth: both [onSizeChanged] (density may have
     * changed on the fly via configuration change) and [loadKnobs]
     * (the user moved a slider) route through here, so a sliders
     * adjustment is no longer overwritten by a later size-change
     * callback that reset the filters to compile-time defaults.
     */
    private fun applyMaskFilters() {
        try {
            sparkGlow.maskFilter = BlurMaskFilter(dp(SPARK_GLOW_BLUR_DP), BlurMaskFilter.Blur.NORMAL)
            dotGlow.maskFilter = BlurMaskFilter(dp(DOT_GLOW_BLUR_DP), BlurMaskFilter.Blur.NORMAL)
            perimeterIn.maskFilter = BlurMaskFilter(dp(PERIM_IN_BLUR_DP), BlurMaskFilter.Blur.NORMAL)
            perimeterMid.maskFilter = BlurMaskFilter(
                dp(maxOf(MIN_BLUR_DP, knobs.perimMidBl)),
                BlurMaskFilter.Blur.NORMAL,
            )
            perimeterOut.maskFilter = BlurMaskFilter(
                dp(maxOf(MIN_BLUR_DP, knobs.perimOuterBl)),
                BlurMaskFilter.Blur.NORMAL,
            )
        } catch (e: Exception) {
            CrashLogger.logErr("applyMaskFilters failed", e)
        }
    }

    /**
     * Smoothly fade the perimeter glow's alpha to a fraction of its
     * default intensity. Animated so the user sees the glow ease
     * down rather than snap when the queue panel is expanded.
     */
    fun setCompact(compact: Boolean) {
        if (this.compact == compact) return
        this.compact = compact
        edgeFadeMask.invalidate()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        density = resources.displayMetrics.density
        cx = w / 2f
        cy = h / 2f
        maxRadius = hypot(w.toDouble(), h.toDouble()).toFloat() / 2f
        buildSparkPath(dp(28f))

        edgeFadeMask.invalidate()
        revealMaskCache.invalidate()
        cachedPerimeterSweep = null
        cachedWaveSweep = null

        val sweep = SweepGradient(cx, cy, sweepLoop, null)
        sparkPaint.shader = sweep
        sparkGlow.shader = sweep
        applyMaskFilters()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        fxLevel = try {
            Prefs.getFxLevelTyped(context)
        } catch (_: Exception) {
            FxLevel.LITE
        }
        // Two tiers — LITE = default, ULTRA = lowest cost.
        fxUltra = fxLevel >= FxLevel.ULTRA
        loadKnobs()
        try {
            // Listen for prefs changes through the Prefs facade so we
            // never reach for SharedPreferences directly.
            val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                loadKnobs()
                ensureTicker()
                invalidate()
            }
            prefsListener = l
            Prefs.registerChangeListener(context, l)
        } catch (e: Exception) {
            // Listener registration only fails when the SP is null
            // (rare detached context). The reveal still works on the
            // snapshot loaded above; surface the failure in the
            // diagnostic so a chronic detach loop can be spotted.
            CrashLogger.logErr("OtpRevealLayout: prefs listener registration failed", e)
        }
        card()?.apply {
            alpha = 0f
            scaleX = 0.85f
            scaleY = 0.85f
            translationY = 0f
        }
        post(::startReveal)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(ticker)
        copyLottiePlayer.release()
        prefsListener?.let {
            try {
                Prefs.unregisterChangeListener(context, it)
            } catch (_: Exception) {
                // Idempotent — unregister-twice is harmless.
            }
        }
        prefsListener = null
        // Clear any RenderEffect set during the copy blur — leaving
        // it on a re-used card view would compound the blur.
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                card()?.setRenderEffect(null)
            }
        } catch (_: Exception) {}
        cardSpringRunnable?.let {
            removeCallbacks(it)
            card()?.removeCallbacks(it)
        }
        cardSpringRunnable = null
        springX?.let { runCatching { it.cancel() } }
        springX = null
        springY?.let { runCatching { it.cancel() } }
        springY = null
        blurAnimator?.let { runCatching { it.cancel() } }
        blurAnimator = null
        cardBlur.recycle()
        CardBlurDrawer.clearRenderEffect(card())
    }

    private fun startReveal() {
        if (revealStarted) return
        revealStarted = true
        animStartMs = System.currentTimeMillis()
        animateCardIn()
        invalidate()
        postOnAnimation(ticker)
    }

    /**
     * Run the copy feedback: blur the card, optionally play a Lottie
     * animation, then trigger dismiss.
     */
    fun startCopyAnimation(codeView: View?, onDone: Runnable?) {
        if (copyActive || dismissing) return
        copyActive = true

        applyCardBlur()

        if (!Prefs.isFxCopyLottieEn(context)) {
            postDelayed({ startDismiss(onDone) }, 300L)
            ensureTicker()
            return
        }

        playCopyLottie(codeView, onDone)
        ensureTicker()
    }

    /** Plays a random Lottie centred on the card and removes it on completion. */
    private fun playCopyLottie(card: View?, onDone: Runnable?) {
        if (card == null) {
            postDelayed({ startDismiss(onDone) }, 760L)
            return
        }
        copyLottiePlayer.play(card) {
            if (dismissing) return@play
            startDismiss(onDone)
        }
    }

    /**
     * Smooth fade-out: card alpha → 0 with an ease-in curve, with
     * the chromatic effects fading in lockstep so everything
     * dissolves as a single wash.
     */
    fun startDismiss(onDone: Runnable?) {
        if (dismissing) {
            onDone?.run()
            return
        }
        dismissing = true

        // Cancel the card-in springs so they don't fight the dismiss
        // fade if the user hits Close (or auto-copy fires) before
        // the reveal has settled.
        cardSpringRunnable?.let {
            removeCallbacks(it)
            card()?.removeCallbacks(it)
        }
        cardSpringRunnable = null
        springX?.let { runCatching { it.cancel() } }
        springX = null
        springY?.let { runCatching { it.cancel() } }
        springY = null

        // Fire-once wrapper so the callback runs whether the
        // animator finishes naturally or is cancelled / lost. Both
        // paths must collapse into a single tear-down.
        val safeDone: Runnable? = onDone?.let {
            object : Runnable {
                private var fired = false
                override fun run() {
                    if (!fired) {
                        fired = true
                        it.run()
                    }
                }
            }
        }

        val card = card()
        if (card != null) {
            card.animate().cancel()
            card.animate()
                .alpha(0f)
                .setDuration(knobs.dismissMs)
                .setInterpolator(easeIn)
                .withEndAction(safeDone)
                .start()
            // Belt-and-braces in case `withEndAction` never fires.
            if (safeDone != null) postDelayed(safeDone, knobs.dismissMs + 80)
        } else {
            safeDone?.run()
        }

        // Fade chromatic effects (perimeter glow, countdown stroke,
        // any lingering reveal trails) together with the card alpha.
        ValueAnimator.ofFloat(effectsAlpha, 0f).apply {
            duration = knobs.dismissMs
            interpolator = easeIn
            addUpdateListener {
                effectsAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        ensureTicker()
    }

    private fun animateCardIn() {
        val card = card() ?: return
        val alphaDelay = (knobs.revealMs * RevealTimings.CARD_IN).toLong()

        ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f).apply {
            startDelay = alphaDelay
            duration = 360
            interpolator = easeStd
            start()
        }

        val spring = SpringForce(1f)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
            .setStiffness(420f)

        springX = SpringAnimation(card, SpringAnimation.SCALE_X).setSpring(spring).apply {
            setStartValue(0.85f)
        }
        springY = SpringAnimation(card, SpringAnimation.SCALE_Y).setSpring(spring).apply {
            setStartValue(0.85f)
        }
        val springRunnable = Runnable {
            if (dismissing) return@Runnable
            try {
                springX?.start()
                springY?.start()
            } catch (_: Exception) {}
        }
        cardSpringRunnable = springRunnable
        card.postDelayed(springRunnable, alphaDelay)
    }

    private fun tick() {
        invalidate()
        // Stop pumping once the reveal has settled and no channel is
        // animating, so SurfaceFlinger does not re-rasterise this
        // window for unrelated transitions like a system shade pull.
        if (!isAttachedToWindow) return
        if (needsTicker()) postOnAnimation(ticker)
    }

    /** True while at least one channel still needs per-frame redraws. */
    private fun needsTicker(): Boolean {
        val elapsed = System.currentTimeMillis() - animStartMs
        val revealDur = if (knobs.revealMs > 0) knobs.revealMs else RevealTimings.REVEAL_TOTAL_MS
        if (elapsed < revealDur) return true
        if (dismissing) return true
        if (copyActive) return true
        if (panelEffectsAlpha < 0.999f) return true
        val breathing = knobs.breathEn && knobs.breathAmt > 0.005f
        val rotating = knobs.rotEn && knobs.rotPeriod > 0L
        return breathing || rotating
    }

    /** Wake the ticker if it has settled but a new animation has started. */
    private fun ensureTicker() {
        if (!isAttachedToWindow) return
        removeCallbacks(ticker)
        postOnAnimation(ticker)
    }

    /**
     * Skip the Lottie copy view from the regular `dispatchDraw` pass —
     * it is rendered separately at the end so it sits above every
     * other effect.
     */
    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child === copyLottiePlayer.currentView() && !drawingCopyLottieFinal) return false
        return super.drawChild(canvas, child, drawingTime)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!revealStarted) {
            super.dispatchDraw(canvas)
            return
        }

        val elapsed = System.currentTimeMillis() - animStartMs
        val revealDur = if (knobs.revealMs > 0) knobs.revealMs else RevealTimings.REVEAL_TOTAL_MS
        var revealT = if (elapsed >= revealDur) 1f else elapsed.toFloat() / revealDur
        if (revealT < 0f) revealT = 0f

        // Slow perimeter rotation and a gentle breathing pulse —
        // both periods are user-tunable.
        perimeterPhase = if (knobs.rotEn && knobs.rotPeriod > 0L) {
            computePerimeterPhase(elapsed)
        } else {
            0f
        }
        perimeterBreath = if (knobs.breathEn && knobs.breathAmt > 0f && knobs.breathPeriod > 0L) {
            val wave = (0.5 - 0.5 * cos(elapsed / knobs.breathPeriod.toDouble() * 2.0 * Math.PI)).toFloat()
            // Default amplitude 0.88..1.05 (range 0.17), scaled by
            // the user amount so the slider at 0 yields a perfectly
            // steady glow.
            val lo = 1f - 0.12f * knobs.breathAmt
            val hi = 1f + 0.05f * knobs.breathAmt
            lo + (hi - lo) * wave
        } else {
            1f
        }

        val perimeterAlpha = stage(revealT, RevealTimings.PERIMETER_IN, 1f) * effectsAlpha
        val panelA = panelEffectsAlpha

        // Wrap the composition in an offscreen layer ONLY when an
        // edge fade pass is going to run — `saveLayer` allocates a
        // full-window FBO and is the most expensive op per frame.
        val needsOuterLayer = knobs.edgeFade > 0.01f
        val outer = if (needsOuterLayer) {
            canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        } else {
            -1
        }

        // 1. Reveal effects on a separate layer with a radial alpha
        //    mask applied at the end so edges fade softly. Skipped
        //    while the queue panel is muting the heavy layers.
        if (panelA > 0.01f) {
            drawRevealMasked(canvas, revealT, panelA)
        }

        // 2. Card content.
        super.dispatchDraw(canvas)

        // 3a. Idle perimeter glow — cross-fades in during the back
        //     half of the reveal. Dimmed by `panelEffectsAlpha`
        //     during a queue expand.
        if (knobs.perimEn && perimeterAlpha * panelA > 0f) {
            drawPerimeterGlow(canvas, perimeterAlpha * panelA)
        }
        // 3b. Simple stroke — flat 1-dp brand outline drawn whenever
        //     the heavy chromatic ring is faded down. Cross-fades
        //     opposite to `panelEffectsAlpha` so the silhouette is
        //     preserved.
        if (panelA < 0.999f) {
            drawSimpleOutline(canvas, 1f - panelA)
        }

        // 4. Blurred card overlay — once copied, the card content
        //    becomes a soft blur so the eye lands on the check badge.
        if (cardBlur.isApplied && knobs.blurEn) drawCardBlurOverlay(canvas)

        // 5. Countdown stroke — gated on `panelEffectsAlpha` so it
        //    dims along with the rest while the panel is expanded.
        if (knobs.countdownEn && countdownT > 0f && !copyActive) {
            val cardForLine = card()
            val cardA = cardForLine?.alpha ?: 1f
            val gate = cardA * cardA
            if (gate > 0.01f) {
                drawCountdown(canvas, effectsAlpha * gate * panelA * knobs.countdownInten)
            }
        }

        // 6. Edge fade so nothing draws a hard line at any edge.
        if (needsOuterLayer) {
            applyEdgeFadeMask(canvas)
            canvas.restoreToCount(outer)
        }

        // 7. Lottie copy animation — drawn above everything else.
        copyLottiePlayer.currentView()?.let { lottie ->
            if (lottie.isVisible) {
                drawingCopyLottieFinal = true
                try {
                    super.drawChild(canvas, lottie, drawingTime)
                } finally {
                    drawingCopyLottieFinal = false
                }
            }
        }
    }

    /**
     * Flat 1-dp brand-coloured outline drawn around the card while
     * the chromatic glow is faded down. Preserves the silhouette
     * without the per-frame cost of three blurred sweep-gradient
     * strokes.
     */
    private fun drawSimpleOutline(canvas: Canvas, alpha: Float) {
        val card = card() ?: return
        if (alpha <= 0f) return
        val paint = simpleOutlinePaint ?: Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.0f)
            color = brandBase()
        }.also { simpleOutlinePaint = it }
        cardRect.set(
            card.left.toFloat(),
            card.top.toFloat(),
            card.right.toFloat(),
            card.bottom.toFloat(),
        )
        // Use the dominant brand colour from the live sweep loop so
        // a notification with a non-default tint draws a matching
        // outline.
        val color = sweepLoop[sweepLoop.size / 2]
        paint.color = (color and 0x00FFFFFF) or ((200 * alpha).toInt() shl 24)
        val corner = dp(12f)
        canvas.drawRoundRect(cardRect, corner, corner, paint)
    }

    /**
     * Multiply alpha by a soft fade on all four sides so any glow
     * that bleeds past the card fades into the host bounds.
     * Implemented as two `DST_IN` passes inside [EdgeFadeMask].
     */
    private fun applyEdgeFadeMask(canvas: Canvas) {
        edgeFadeMask.apply(
            canvas, maskPaint,
            width, height,
            paddingTop, paddingBottom,
            paddingLeft, paddingRight,
            compact, knobs.edgeFade,
        )
    }

    /**
     * Render the four reveal layers into an offscreen layer, then
     * apply a radial alpha mask so the composite fades to fully
     * transparent before reaching the host bounds.
     */
    private fun drawRevealMasked(canvas: Canvas, t: Float, panelA: Float) {
        if (effectsAlpha <= 0f) return
        // Skip the saveLayer entirely when fully muted — saves a GPU
        // offscreen pass that would otherwise still pay the round-trip
        // cost even with zero ink.
        val layeredA = effectsAlpha * panelA
        if (layeredA <= 0.01f) return

        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        if (knobs.haloEn) {
            drawHalo(canvas, stage(t, RevealTimings.HALO_IN, RevealTimings.HALO_OUT), layeredA * knobs.haloInten)
        }
        if (!fxUltra) {
            if (knobs.waveEn) {
                drawWave(canvas, stage(t, RevealTimings.WAVE_IN, RevealTimings.WAVE_OUT), layeredA * knobs.waveInten)
            }
            if (knobs.halftoneEn) {
                drawHalftone(
                    canvas,
                    stage(t, RevealTimings.HALFTONE_IN, RevealTimings.HALFTONE_OUT),
                    layeredA * knobs.halftoneInten,
                )
            }
        }
        if (knobs.sparkEn) {
            drawSpark(canvas, stage(t, RevealTimings.SPARK_IN, RevealTimings.SPARK_OUT), layeredA * knobs.sparkInten)
        }

        // Radial alpha mask: opaque around the card centre, falls
        // off smoothly to fully transparent before reaching the host
        // bounds. Cached across frames; geometry only changes on
        // size change.
        val w = width
        val hgt = height
        revealMaskCache.install(maskPaint, w, hgt, cx, cy, maxRadius)
        canvas.drawRect(0f, 0f, w.toFloat(), hgt.toFloat(), maskPaint)

        canvas.restoreToCount(layer)
    }

    /**
     * Compute the perimeter sweep phase in degrees. Used as the
     * base rotation for every mode that needs it. Always
     * non-negative.
     */
    private fun computePerimeterPhase(elapsed: Long): Float {
        if (knobs.rotPeriod <= 0L) return 0f
        val t = (elapsed % knobs.rotPeriod) / knobs.rotPeriod.toFloat()
        return t * 360f
    }

    /** Continuous chromatic glow around the card perimeter. */
    private fun drawPerimeterGlow(canvas: Canvas, gAlphaIn: Float) {
        val card = card() ?: return
        if (gAlphaIn <= 0f) return
        // User-facing damping plus the perimeter intensity multiplier
        // so callers can dim everything without threading alphas
        // through every sub-layer below.
        val gAlpha = gAlphaIn * knobs.perimInten
        if (gAlpha <= 0f) return

        cardRect.set(
            card.left.toFloat(),
            card.top.toFloat(),
            card.right.toFloat(),
            card.bottom.toFloat(),
        )
        // Must match `@drawable/overlay_bg`'s corner radius so the
        // chromatic outline traces the card's actual rounded shape.
        val corner = dp(12f)

        val pcx = cardRect.centerX()
        val pcy = cardRect.centerY()
        if (cachedPerimeterSweep == null ||
            cachedPerimeterCx != pcx ||
            cachedPerimeterCy != pcy
        ) {
            cachedPerimeterSweep = SweepGradient(pcx, pcy, sweepLoop, null)
            cachedPerimeterCx = pcx
            cachedPerimeterCy = pcy
        }
        sweepMatrix.setRotate(perimeterPhase, pcx, pcy)
        cachedPerimeterSweep?.setLocalMatrix(sweepMatrix)

        val breath = perimeterBreath

        // Wide outer halo (very soft, breathes the most).
        if (knobs.perimOuterEn && !fxUltra && !compact && knobs.perimOuterW > 0.1f) {
            val strokeBreathOuter = lerp(0.85f, 1.18f, breath)
            perimeterOut.shader = cachedPerimeterSweep
            perimeterOut.strokeWidth = dp(knobs.perimOuterW) * strokeBreathOuter
            perimeterOut.alpha = clamp(110 * gAlpha * breath, 0f, 255f).toInt()
            scratchRect.set(cardRect)
            scratchRect.inset(-dp(4f), -dp(4f))
            canvas.drawRoundRect(scratchRect, corner + dp(4f), corner + dp(4f), perimeterOut)
        }

        // Mid halo (medium blur, a touch of breathing).
        val strokeBreathMid = lerp(0.92f, 1.08f, breath)
        val midStrokeDp = if (compact) minOf(3f, knobs.perimMidW) else knobs.perimMidW
        val midAlphaMax = if (compact) 110f else 150f
        if (midStrokeDp > 0.1f) {
            perimeterMid.shader = cachedPerimeterSweep
            perimeterMid.strokeWidth = dp(midStrokeDp) * strokeBreathMid
            perimeterMid.alpha = clamp(midAlphaMax * gAlpha * (0.85f + 0.15f * breath), 0f, 255f).toInt()
            scratchRect.set(cardRect)
            val midOutsetDp = if (compact) 0.25f else 1f
            scratchRect.inset(-dp(midOutsetDp), -dp(midOutsetDp))
            canvas.drawRoundRect(scratchRect, corner + dp(midOutsetDp), corner + dp(midOutsetDp), perimeterMid)
        }

        // Crisp inner edge — steady to avoid any flicker on the outline.
        val innerW = if (compact) minOf(0.6f, knobs.perimInW) else knobs.perimInW
        if (innerW > 0.05f) {
            perimeterIn.shader = cachedPerimeterSweep
            perimeterIn.strokeWidth = dp(innerW)
            perimeterIn.alpha = ((if (compact) 140 else 190) * gAlpha).toInt()
            canvas.drawRoundRect(cardRect, corner, corner, perimeterIn)
        }
    }

    /** Geometry / colour rules live in [CountdownDrawer]. */
    private fun drawCountdown(canvas: Canvas, gAlpha: Float) {
        countdownDrawer.draw(
            canvas,
            card(),
            countdownGlow,
            countdownCore,
            density,
            countdownT,
            gAlpha,
            knobs.countdownStyle,
        )
    }

    /**
     * Apply the frosted-glass blur to the card content and run the
     * crossfade plus [blurAlpha] reveal animation. The blur itself
     * (RenderEffect / software fallback / overlay composite) lives
     * in [CardBlurDrawer]; this method coordinates the crossfade of
     * the live card children and the value animator that drives the
     * per-frame intensity ramp.
     */
    private fun applyCardBlur() {
        if (cardBlur.isApplied) return
        val card = card()
        cardBlur.apply(card, knobs.blurEn)
        if (!knobs.blurEn) return
        if (card == null) return
        val w = card.width
        val h = card.height
        if (w <= 0 || h <= 0) return

        // Crossfade the live card content (text / icons / buttons)
        // to 0 while the blurred snapshot fades to 1. We animate the
        // FIRST CHILD (the inner row) rather than the card itself,
        // so the gray frame stays visible. ~520 ms with a deep
        // ease-out gives the content a sense of floating away
        // rather than snapping.
        if (card is ViewGroup) {
            for (i in 0 until card.childCount) {
                val child = card.getChildAt(i) ?: continue
                child.animate().cancel()
                child.animate()
                    .alpha(0f)
                    .setDuration(520)
                    .setInterpolator(blurEase)
                    .start()
            }
        }

        blurAnimator?.let { runCatching { it.cancel() } }
        // Frosted-glass reveal: the blur opacity, the tap radius and
        // the bitmap scale all grow from 0 → 1 together over ~680 ms
        // with a heavy deceleration curve.
        blurAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 680
            interpolator = blurEase
            addUpdateListener {
                blurAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * Composite the frosted-glass overlay above the card. All
     * round-rect clipping, the 9-tap composite and the top-edge
     * highlight live in [CardBlurDrawer].
     */
    private fun drawCardBlurOverlay(canvas: Canvas) {
        cardBlur.drawOverlay(canvas, card(), density, blurAlpha, effectsAlpha)
    }

    private fun drawHalo(canvas: Canvas, local: Float, gAlpha: Float) {
        RevealEffectsRenderer.drawHalo(
            canvas, haloPaint, cx, cy, density,
            maxRadius, brandBase(), brandDeep(), local, gAlpha,
        )
    }

    private fun drawSpark(canvas: Canvas, local: Float, gAlpha: Float) {
        RevealEffectsRenderer.drawSpark(
            canvas, sparkPath, sparkPaint, sparkGlow,
            cx, cy, density, local, gAlpha,
        )
    }

    private fun drawWave(canvas: Canvas, local: Float, gAlpha: Float) {
        // Mirror the cached gradient back to the field-level holder
        // so the reset paths in onSizeChanged / onAttached can null
        // it.
        waveSweepCache[0] = cachedWaveSweep
        RevealEffectsRenderer.drawWave(
            canvas, waveStroke, waveSweepCache,
            sweepMatrix, sweepLoop, cx, cy, density, maxRadius, local, gAlpha,
        )
        cachedWaveSweep = waveSweepCache[0]
    }

    private fun drawHalftone(canvas: Canvas, local: Float, gAlpha: Float) {
        RevealEffectsRenderer.drawHalftone(
            canvas, dotPaint, dotGlow, sweepLoop,
            brandBase(), cx, cy, density, maxRadius,
            // Pin to the cheaper 3-ring variant — both visible FX
            // tiers render the same density.
            fxLite = true,
            local, gAlpha,
        )
    }

    /** Rotated-square ("rhombus") spark used by the reveal animation. */
    private fun buildSparkPath(r: Float) {
        RevealEffectsRenderer.buildSparkPath(sparkPath, cx, cy, r)
    }

    private fun card(): View? = if (isNotEmpty()) getChildAt(0) else null

    private fun stage(t: Float, start: Float, stop: Float): Float =
        RevealMath.stage(t, start, stop)

    /**
     * Primary brand colour — middle of the live sweep loop. Used by
     * halo / badge / simple outline so they all follow the
     * sender-icon palette set up by [setBrandColor].
     */
    private fun brandBase(): Int =
        if (sweepLoop.isEmpty()) {
            RevealPalette.CLR_ICON_BASE
        } else {
            sweepLoop[sweepLoop.size / 2]
        }

    /**
     * Deepest brand colour — first stop of the sweep loop. Used by
     * [drawHalo]'s mid radial gradient so the falloff stays in the
     * brand palette.
     */
    private fun brandDeep(): Int =
        if (sweepLoop.isEmpty()) RevealPalette.CLR_ICON_DEEP else sweepLoop[0]

    private fun lerp(a: Float, b: Float, t: Float): Float = RevealMath.lerp(a, b, t)
    private fun clamp(v: Float, lo: Float, hi: Float): Float = RevealMath.clamp(v, lo, hi)

    private fun dp(v: Float): Float = v * density

    companion object {
        /**
         * Smallest blur radius we will ever pass to
         * [BlurMaskFilter]. Below this the system collapses the
         * filter to a no-op, so we floor here to keep the user-tuned
         * sliders meaningful at the low end.
         */
        private const val MIN_BLUR_DP: Float = 0.1f

        /** Spark perimeter glow blur. */
        private const val SPARK_GLOW_BLUR_DP: Float = 8f

        /** Halftone dot glow blur. */
        private const val DOT_GLOW_BLUR_DP: Float = 4f

        /** Inner perimeter stroke blur — sharp, just a touch of softness. */
        private const val PERIM_IN_BLUR_DP: Float = 1.6f
    }
}
