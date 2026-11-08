package dev.voice.offline.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import dev.voice.offline.service.AssistantState
import dev.voice.offline.ui.theme.TaraTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Animated cyber audio visualizer waveform.
 * Renders pulsating equalizer bars with a smooth Cyan-to-Orange neon gradient.
 */
class TaraAudioVisualizerView(context: Context) : View(context) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val barRect = RectF()
    private val barCount = 36
    private var animPhase = 0f
    private var currentState: AssistantState = AssistantState.IDLE
    private var animator: ValueAnimator? = null
    private var isAttached = false

    private val minBarHeight = TaraTheme.dpToPx(context, 3f).toFloat()
    private val shaderColors = intArrayOf(TaraTheme.TaraCyan, TaraTheme.SkyBlue, TaraTheme.TaraOrange)
    private val shaderPositions = floatArrayOf(0f, 0.45f, 1f)

    private fun rebuildShader(w: Float) {
        if (w <= 0f) return
        barPaint.shader = LinearGradient(
            0f, 0f, w, 0f,
            shaderColors,
            shaderPositions,
            Shader.TileMode.CLAMP
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader(w.toFloat())
    }

    private fun startAnimator() {
        if (animator?.isRunning == true) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = getDurationForState(currentState)
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                animPhase = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimator() {
        animator?.cancel()
        animator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttached = true
        if (visibility == View.VISIBLE) {
            startAnimator()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAttached = false
        stopAnimator()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE && isAttached) {
            startAnimator()
        } else {
            stopAnimator()
        }
    }

    private fun getDurationForState(state: AssistantState): Long {
        return when (state) {
            AssistantState.WAKE_LISTENING,
            AssistantState.LISTENING,
            AssistantState.RECORDING_COMMAND,
            AssistantState.TRANSCRIBING -> 1000L
            AssistantState.INFERENCING,
            AssistantState.RECOGNIZING -> 700L
            else -> 2200L
        }
    }

    fun setState(state: AssistantState) {
        currentState = state
        animator?.duration = getDurationForState(state)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val centerY = h / 2f
        val totalSpacing = w * 0.15f
        val availableWidth = w - totalSpacing
        val barWidth = availableWidth / barCount
        val gap = totalSpacing / (barCount + 1)

        val isActive = when (currentState) {
            AssistantState.WAKE_LISTENING,
            AssistantState.WAKE_DETECTED,
            AssistantState.LISTENING,
            AssistantState.RECORDING_COMMAND,
            AssistantState.TRANSCRIBING,
            AssistantState.INFERENCING,
            AssistantState.RECOGNIZING -> true
            else -> false
        }

        val baseMaxHeight = if (isActive) h * 0.85f else h * 0.40f

        for (i in 0 until barCount) {
            val progress = i.toFloat() / barCount
            val envelope = sin(progress * PI.toFloat())

            val wave = abs(
                sin(progress * 4 * PI.toFloat() + animPhase) * 0.6f +
                sin(progress * 8 * PI.toFloat() - animPhase * 1.5f) * 0.4f
            )

            val barHeight = (envelope * wave * baseMaxHeight).coerceAtLeast(minBarHeight)
            val left = totalSpacing / 2f + i * (barWidth + gap)
            val top = centerY - barHeight / 2f
            val right = left + barWidth
            val bottom = centerY + barHeight / 2f

            barRect.set(left, top, right, bottom)
            val cornerRadius = barWidth / 2f
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)
        }
    }
}
