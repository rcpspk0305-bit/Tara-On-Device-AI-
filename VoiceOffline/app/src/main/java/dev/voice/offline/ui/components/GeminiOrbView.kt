package dev.voice.offline.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import dev.voice.offline.R
import dev.voice.offline.service.AssistantState
import dev.voice.offline.ui.theme.GeminiTheme
import kotlin.math.min

/**
 * High-performance, hardware-accelerated Google Gemini Live interactive Orb.
 * Features reactive audio wave aura rings, multi-stop Gemini gradient core,
 * and state-aware animated modes (Listening, Thinking, Authorizing, Executing).
 */
class GeminiOrbView(
    context: Context,
    private val onOrbClicked: () -> Unit
) : View(context) {

    private var currentState: AssistantState = AssistantState.STOPPED
    private var isPressedDown = false

    // Paints
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val coreBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#40FFFFFF")
        strokeWidth = GeminiTheme.dpToPx(context, 1.5f).toFloat()
    }

    // Drawables
    private val sparkleIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_gemini_sparkle)
    private val micIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_mic)

    // Animation values
    private var pulsePhase = 0f
    private var breathingScale = 1f
    private var rotationAngle = 0f

    private var pulseAnimator: ValueAnimator? = null
    private var rotationAnimator: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        setupAnimators()
    }

    private fun setupAnimators() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                pulsePhase = anim.animatedValue as Float
                breathingScale = 0.96f + 0.08f * (1f - kotlin.math.abs(pulsePhase - 0.5f) * 2f)
                invalidate()
            }
            start()
        }

        rotationAnimator?.cancel()
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                rotationAngle = anim.animatedValue as Float
                if (currentState == AssistantState.INFERENCING) {
                    invalidate()
                }
            }
            start()
        }
    }

    fun setState(state: AssistantState) {
        currentState = state
        when (state) {
            AssistantState.STOPPED -> {
                pulseAnimator?.duration = 2400
            }
            AssistantState.INITIALIZING -> {
                pulseAnimator?.duration = 1200
            }
            AssistantState.LISTENING,
            AssistantState.WAKE_DETECTED,
            AssistantState.RECORDING_COMMAND,
            AssistantState.TRANSCRIBING -> {
                pulseAnimator?.duration = 1400
            }
            AssistantState.INFERENCING -> {
                pulseAnimator?.duration = 1000
            }
            AssistantState.AUTHORIZING -> {
                pulseAnimator?.duration = 1200
            }
            AssistantState.EXECUTING -> {
                pulseAnimator?.duration = 800
            }
            AssistantState.CONFIRMING -> {
                pulseAnimator?.duration = 1000
            }
            is AssistantState.ERROR -> {
                pulseAnimator?.duration = 2000
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = min(width, height) * 0.28f

        val pressScale = if (isPressedDown) 0.93f else 1.0f
        val activeScale = if (isActiveListeningState()) breathingScale else 1.0f
        val currentRadius = baseRadius * pressScale * activeScale

        // Determine current theme colors
        val (primaryColor, secondaryColor, tertiaryColor) = getStateColors()

        // 1. Draw Glowing Aura Rings when active or idle
        drawAuraRings(canvas, centerX, centerY, currentRadius, primaryColor, secondaryColor)

        // 2. Draw Soft Center Radial Glow
        glowPaint.shader = RadialGradient(
            centerX, centerY, currentRadius * 1.5f,
            intArrayOf(adjustAlpha(secondaryColor, 0.45f), adjustAlpha(primaryColor, 0.15f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, currentRadius * 1.5f, glowPaint)

        // 3. Draw Core Gemini Orb with Linear Gradient
        canvas.save()
        if (currentState == AssistantState.INFERENCING) {
            canvas.rotate(rotationAngle, centerX, centerY)
        }
        corePaint.shader = LinearGradient(
            centerX - currentRadius, centerY - currentRadius,
            centerX + currentRadius, centerY + currentRadius,
            intArrayOf(primaryColor, secondaryColor, tertiaryColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, currentRadius, corePaint)
        canvas.restore()

        // 4. Subtle Inner Border Rim
        canvas.drawCircle(centerX, centerY, currentRadius, coreBorderPaint)

        // 5. Draw Icon inside core
        val iconSize = (currentRadius * 0.75f).toInt()
        val icon = if (currentState == AssistantState.STOPPED) sparkleIcon else (micIcon ?: sparkleIcon)
        icon?.let {
            it.setBounds(
                (centerX - iconSize / 2f).toInt(),
                (centerY - iconSize / 2f).toInt(),
                (centerX + iconSize / 2f).toInt(),
                (centerY + iconSize / 2f).toInt()
            )
            it.setTint(Color.WHITE)
            it.draw(canvas)
        }
    }

    private fun drawAuraRings(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        coreRadius: Float,
        c1: Int,
        c2: Int
    ) {
        val maxWaveRadius = coreRadius * (if (isActiveListeningState()) 1.85f else 1.35f)
        val numRings = if (isActiveListeningState()) 3 else 2

        for (i in 0 until numRings) {
            val ringProgress = (pulsePhase + i.toFloat() / numRings) % 1.0f
            val ringRadius = coreRadius + (maxWaveRadius - coreRadius) * ringProgress
            val ringAlpha = ((1.0f - ringProgress) * (if (isActiveListeningState()) 0.65f else 0.25f))

            auraPaint.color = if (i % 2 == 0) c1 else c2
            auraPaint.alpha = (ringAlpha * 255).toInt().coerceIn(0, 255)
            auraPaint.strokeWidth = GeminiTheme.dpToPx(context, 2f * (1f - ringProgress * 0.5f)).toFloat()

            canvas.drawCircle(centerX, centerY, ringRadius, auraPaint)
        }
    }

    private fun getStateColors(): Triple<Int, Int, Int> {
        return when (currentState) {
            AssistantState.STOPPED -> Triple(
                Color.parseColor("#2C2F3C"),
                GeminiTheme.GeminiBlue,
                GeminiTheme.GeminiPurple
            )
            AssistantState.INITIALIZING -> Triple(
                GeminiTheme.GeminiAmber,
                GeminiTheme.GeminiBlue,
                GeminiTheme.GeminiCyan
            )
            AssistantState.LISTENING,
            AssistantState.WAKE_DETECTED -> Triple(
                GeminiTheme.GeminiBlue,
                GeminiTheme.GeminiPurple,
                GeminiTheme.GeminiCyan
            )
            AssistantState.RECORDING_COMMAND,
            AssistantState.TRANSCRIBING -> Triple(
                GeminiTheme.GeminiPurple,
                GeminiTheme.GeminiCoral,
                GeminiTheme.GeminiBlue
            )
            AssistantState.INFERENCING -> Triple(
                GeminiTheme.GeminiCyan,
                GeminiTheme.GeminiPurple,
                GeminiTheme.GeminiCoral
            )
            AssistantState.AUTHORIZING -> Triple(
                GeminiTheme.GeminiAmber,
                Color.parseColor("#FF9800"),
                Color.parseColor("#FFC107")
            )
            AssistantState.EXECUTING -> Triple(
                GeminiTheme.GeminiCyan,
                GeminiTheme.GeminiBlue,
                GeminiTheme.GeminiMint
            )
            AssistantState.CONFIRMING -> Triple(
                GeminiTheme.GeminiMint,
                Color.parseColor("#00E676"),
                GeminiTheme.GeminiCyan
            )
            is AssistantState.ERROR -> Triple(
                GeminiTheme.GeminiRed,
                GeminiTheme.GeminiCoral,
                Color.parseColor("#B71C1C")
            )
        }
    }

    private fun isActiveListeningState(): Boolean {
        return when (currentState) {
            AssistantState.LISTENING,
            AssistantState.WAKE_DETECTED,
            AssistantState.RECORDING_COMMAND,
            AssistantState.TRANSCRIBING,
            AssistantState.INFERENCING,
            AssistantState.EXECUTING -> true
            else -> false
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
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
                onOrbClicked()
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
        rotationAnimator?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (pulseAnimator?.isStarted != true) pulseAnimator?.start()
        if (rotationAnimator?.isStarted != true) rotationAnimator?.start()
    }
}
