package dev.voice.offline.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import dev.voice.offline.R
import dev.voice.offline.service.AssistantState
import dev.voice.offline.ui.theme.TaraTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * TARA interactive fluid neon energy orb.
 * Centerpiece of TARA On-Device Voice & Gesture Controller.
 * Features a dual-color organic fluid perimeter (Cyan to Orange),
 * continuous breathing harmonics, central "Hey Tara..." greeting,
 * and reactive waving hand gesture icon.
 */
class TaraOrbView(
    context: Context,
    private val onOrbClicked: () -> Unit
) : View(context) {

    private var currentState: AssistantState = AssistantState.IDLE
    private var isPressedDown = false

    // Paints
    private val fluidRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fluidAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val coreBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val coreGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val coreBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = TaraTheme.BorderCore
        strokeWidth = TaraTheme.dpToPx(context, 1.5f).toFloat()
    }
    private val greetingTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TaraTheme.TextSilver
        textSize = TaraTheme.dpToPx(context, 18f).toFloat()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    // Hand and Mic Icons
    private val handIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_gesture_hand)
    private val micIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_mic)

    // Animation phases
    private var fluidPhase = 0f
    private var breathingScale = 1.0f
    private var rotationAngle = 0f
    private var isAttached = false

    private var masterAnimator: ValueAnimator? = null

    // Fluid path buffer
    private val fluidPath = Path()
    private val auraPath = Path()

    // Precomputed dimensions & cached shaders
    private var cachedWidth = 0f
    private var cachedHeight = 0f
    private var cachedBaseRadius = 0f
    private val iconSize = TaraTheme.dpToPx(context, 38f)
    private val textYOffset = TaraTheme.dpToPx(context, 10f).toFloat()
    private val iconYOffset = TaraTheme.dpToPx(context, 4f).toFloat()
    private val ringStrokeWidth = TaraTheme.dpToPx(context, 4.5f).toFloat()

    // Shader pre-parsed color arrays
    private val diskColors = intArrayOf(TaraTheme.CoreDiskDark, TaraTheme.CoreDiskMid, TaraTheme.CoreDiskBlack)
    private val diskPositions = floatArrayOf(0f, 0.7f, 1f)
    private val ringColors = intArrayOf(
        TaraTheme.TaraCyan,
        TaraTheme.TaraCyan,
        TaraTheme.SkyBlue,
        TaraTheme.NeonAmber,
        TaraTheme.TaraOrange,
        TaraTheme.NeonFlame
    )
    private val ringPositions = floatArrayOf(0f, 0.25f, 0.48f, 0.55f, 0.82f, 1f)
    private val auraColors = intArrayOf(
        TaraTheme.adjustAlpha(TaraTheme.TaraCyan, 0.40f),
        TaraTheme.adjustAlpha(TaraTheme.TaraOrange, 0.40f)
    )

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "TARA Voice Assistant Orb, Hey Tara. Tap to toggle."
        fluidRingPaint.strokeWidth = ringStrokeWidth
        fluidAuraPaint.strokeWidth = ringStrokeWidth * 2.2f
    }

    private fun rebuildShaders(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        cachedBaseRadius = min(w, h) * 0.36f
        if (cachedBaseRadius <= 0f) return

        // 1. Soft Ambient Glow Shader
        val cyanAlpha = 0.22f
        val orangeAlpha = 0.22f
        val glowColors = intArrayOf(
            TaraTheme.AmbientGlowBase,
            TaraTheme.adjustAlpha(TaraTheme.TaraCyan, cyanAlpha * 0.5f),
            TaraTheme.adjustAlpha(TaraTheme.TaraOrange, orangeAlpha * 0.3f),
            Color.TRANSPARENT
        )
        val glowPositions = floatArrayOf(0f, 0.45f, 0.8f, 1f)
        coreGlowPaint.shader = RadialGradient(
            cx, cy, cachedBaseRadius * 1.45f,
            glowColors, glowPositions, Shader.TileMode.CLAMP
        )

        // 2. Translucent Core Disk Shader
        coreBgPaint.shader = RadialGradient(
            cx, cy, cachedBaseRadius,
            diskColors, diskPositions, Shader.TileMode.CLAMP
        )

        // 3. Fluid Ring & Aura Shaders
        fluidRingPaint.shader = LinearGradient(
            cx - cachedBaseRadius * 1.15f, cy,
            cx + cachedBaseRadius * 1.15f, cy,
            ringColors, ringPositions, Shader.TileMode.CLAMP
        )
        fluidAuraPaint.shader = LinearGradient(
            cx - cachedBaseRadius * 1.2f, cy,
            cx + cachedBaseRadius * 1.2f, cy,
            auraColors, null, Shader.TileMode.CLAMP
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedWidth = w.toFloat()
        cachedHeight = h.toFloat()
        rebuildShaders(cachedWidth, cachedHeight)
    }

    private fun startAnimators() {
        if (masterAnimator?.isRunning == true) return
        masterAnimator?.cancel()
        masterAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = getAnimDurationForState(currentState)
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                fluidPhase = anim.animatedValue as Float
                val sine = sin(fluidPhase)
                breathingScale = 0.98f + 0.04f * ((sine + 1f) / 2f)
                rotationAngle = (fluidPhase / (2 * PI).toFloat()) * 360f
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimators() {
        masterAnimator?.cancel()
        masterAnimator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttached = true
        if (visibility == View.VISIBLE) {
            startAnimators()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAttached = false
        stopAnimators()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE && isAttached) {
            startAnimators()
        } else {
            stopAnimators()
        }
    }

    private fun getAnimDurationForState(state: AssistantState): Long {
        return when (state) {
            AssistantState.IDLE, AssistantState.STOPPED -> 3200L
            AssistantState.ACTIVATING, AssistantState.INITIALIZING -> 2000L
            AssistantState.WAKE_LISTENING, AssistantState.LISTENING,
            AssistantState.WAKE_DETECTED, AssistantState.RECORDING_COMMAND,
            AssistantState.TRANSCRIBING -> 1600L
            AssistantState.RECOGNIZING, AssistantState.VALIDATING, AssistantState.INFERENCING -> 1000L
            AssistantState.AUTHORIZING -> 1400L
            AssistantState.EXECUTING -> 800L
            AssistantState.CONFIRMING -> 1200L
            is AssistantState.ERROR -> 2400L
            else -> 2800L
        }
    }

    fun setState(state: AssistantState) {
        currentState = state
        masterAnimator?.duration = getAnimDurationForState(state)

        val greetingText = getGreetingText()
        contentDescription = "TARA Voice Assistant Orb, $greetingText. Tap to toggle."
        invalidate()
    }

    private fun getGreetingText(): String {
        return when (currentState) {
            AssistantState.IDLE, AssistantState.STOPPED -> "Hey Tara..."
            AssistantState.WAKE_LISTENING -> "Hey Tara..."
            AssistantState.WAKE_DETECTED -> "Listening..."
            AssistantState.LISTENING, AssistantState.RECORDING_COMMAND -> "Listening..."
            AssistantState.INFERENCING, AssistantState.RECOGNIZING -> "Thinking..."
            AssistantState.EXECUTING -> "Executing..."
            is AssistantState.ERROR -> "Try again"
            else -> "Hey Tara..."
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = cachedWidth
        val h = cachedHeight
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f

        val pressScale = if (isPressedDown) 0.94f else 1.0f
        val scale = pressScale * breathingScale
        val currentRadius = cachedBaseRadius * scale
        if (currentRadius <= 0f) return

        // 1. Soft Ambient Radiant Glow
        canvas.drawCircle(cx, cy, currentRadius * 1.45f, coreGlowPaint)

        // 2. Translucent Core Disk
        canvas.drawCircle(cx, cy, currentRadius, coreBgPaint)
        canvas.drawCircle(cx, cy, currentRadius, coreBorderPaint)

        // 3. Fluid Organic Neon Energy Ring (Cyan -> Orange)
        val steps = 90
        fluidPath.reset()
        auraPath.reset()

        val isListening = isActiveListeningState()
        val amp1 = if (isListening) ringStrokeWidth * 1.2f else ringStrokeWidth * 0.7f
        val amp2 = if (isListening) ringStrokeWidth * 0.8f else ringStrokeWidth * 0.4f

        canvas.save()
        if (currentState == AssistantState.INFERENCING || currentState == AssistantState.RECOGNIZING) {
            canvas.rotate(rotationAngle, cx, cy)
        }

        for (i in 0..steps) {
            val angle = (i.toFloat() / steps) * (2 * PI).toFloat()
            val wave = sin(3 * angle + fluidPhase) * amp1 + cos(5 * angle - fluidPhase * 1.3f) * amp2
            val r = currentRadius + wave
            val px = cx + r * cos(angle)
            val py = cy + r * sin(angle)

            if (i == 0) {
                fluidPath.moveTo(px, py)
                auraPath.moveTo(px, py)
            } else {
                fluidPath.lineTo(px, py)
                auraPath.lineTo(px, py)
            }
        }
        fluidPath.close()
        auraPath.close()

        canvas.drawPath(auraPath, fluidAuraPaint)
        canvas.drawPath(fluidPath, fluidRingPaint)
        canvas.restore()

        // 4. Center Content: "Hey Tara..." + Waving Hand Icon
        val textY = cy - textYOffset
        val greetingText = getGreetingText()
        canvas.drawText(greetingText, cx, textY, greetingTextPaint)

        // Center Hand Gesture Icon below the text
        val icon = if (currentState == AssistantState.LISTENING || currentState == AssistantState.RECORDING_COMMAND) {
            micIcon ?: handIcon
        } else {
            handIcon
        }

        icon?.let {
            val iconLeft = (cx - iconSize / 2f).toInt()
            val iconTop = (cy + iconYOffset).toInt()
            it.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            it.setTint(if (isListening) TaraTheme.TaraCyan else TaraTheme.IconSlate)
            it.draw(canvas)
        }
    }

    private fun isActiveListeningState(): Boolean {
        return when (currentState) {
            AssistantState.WAKE_LISTENING,
            AssistantState.WAKE_DETECTED,
            AssistantState.LISTENING,
            AssistantState.RECORDING_COMMAND,
            AssistantState.TRANSCRIBING -> true
            else -> false
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        onOrbClicked()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressedDown = true
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isPressedDown = false
                invalidate()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressedDown = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
