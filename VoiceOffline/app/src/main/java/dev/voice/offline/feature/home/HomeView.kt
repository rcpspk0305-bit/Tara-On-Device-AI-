package dev.voice.offline.feature.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import dev.voice.offline.R
import dev.voice.offline.service.AssistantState
import dev.voice.offline.ui.components.TaraAudioVisualizerView
import dev.voice.offline.ui.components.TaraOrbView
import dev.voice.offline.ui.theme.TaraTheme

/**
 * TARA: On-Device Voice & Gesture Controller.
 * Production-quality, accessible UI matching the cyber-neon dashboard:
 * - Fluid organic glowing TARA orb with "Hey Tara..." and hand gesture glyph
 * - 2x2 Quick Action Cards: Voice Commands, Gesture Detection, Recent Actions, Desktop Status
 * - Master Control Toggles: Voice Control (Cyan) and Gesture Control (Orange)
 * - Animated Cyber Waveform Visualizer with real-time status feedback
 */
class HomeView(
    context: Context,
    private val onStartListening: () -> Unit,
    private val onStopListening: () -> Unit,
    private val onNavigate: (String) -> Unit
) : ScrollView(context) {

    private val orbView: TaraOrbView
    private val visualizerView: TaraAudioVisualizerView
    private val statusCaption: TextView
    private val lastResultCard: LinearLayout
    private val lastResultTextView: TextView
    private val voiceToggle: SwitchCompat
    private val gestureToggle: SwitchCompat
    private val voiceStatusLabel: TextView
    private val gestureStatusLabel: TextView

    private var currentState: AssistantState = AssistantState.IDLE
    private var isGestureActive = true

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        isFillViewport = true
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        setBackgroundColor(TaraTheme.Background)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                TaraTheme.dpToPx(context, 20f),
                TaraTheme.dpToPx(context, 16f),
                TaraTheme.dpToPx(context, 20f),
                TaraTheme.dpToPx(context, 24f)
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }
        addView(root)

        // ==========================================
        // 1. TOP HEADER: "TARA" & Settings Action
        // ==========================================
        val topHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, TaraTheme.dpToPx(context, 8f))
            }
        }
        root.addView(topHeaderRow)

        // Left spacer (48dp) to ensure TARA title is mathematically centered
        val leftSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(TaraTheme.dpToPx(context, 48f), TaraTheme.dpToPx(context, 48f))
        }
        topHeaderRow.addView(leftSpacer)

        val title = TextView(context).apply {
            text = "Tara"
            textSize = 24f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(TaraTheme.TextPrimary)
            gravity = Gravity.CENTER
            letterSpacing = 0.16f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topHeaderRow.addView(title)

        // Right Settings Button (48dp x 48dp for WCAG touch target compliance)
        val settingsBtn = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_settings_gear))
            setColorFilter(TaraTheme.TextSecondary)
            isClickable = true
            isFocusable = true
            contentDescription = "Open Settings"
            setPadding(
                TaraTheme.dpToPx(context, 12f),
                TaraTheme.dpToPx(context, 12f),
                TaraTheme.dpToPx(context, 12f),
                TaraTheme.dpToPx(context, 12f)
            )
            val btnBg = TaraTheme.createPillDrawable(
                context,
                fillColor = Color.TRANSPARENT,
                strokeColor = Color.TRANSPARENT,
                strokeWidthDp = 0f
            )
            background = TaraTheme.createRippleDrawable(context, btnBg)
            layoutParams = LinearLayout.LayoutParams(
                TaraTheme.dpToPx(context, 48f),
                TaraTheme.dpToPx(context, 48f)
            )
            setOnClickListener {
                onNavigate("settings")
            }
        }
        topHeaderRow.addView(settingsBtn)

        // ==========================================
        // 2. CONNECTION STATUS PILL
        // ==========================================
        val statusPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                TaraTheme.dpToPx(context, 14f),
                TaraTheme.dpToPx(context, 6f),
                TaraTheme.dpToPx(context, 14f),
                TaraTheme.dpToPx(context, 6f)
            )
            background = TaraTheme.createPillDrawable(
                context,
                fillColor = Color.parseColor("#121622"),
                strokeColor = TaraTheme.BorderStroke,
                strokeWidthDp = 1f
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, TaraTheme.dpToPx(context, 14f))
            }
        }
        root.addView(statusPill)

        val greenDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(TaraTheme.TaraGreen)
            }
            layoutParams = LinearLayout.LayoutParams(
                TaraTheme.dpToPx(context, 8f),
                TaraTheme.dpToPx(context, 8f)
            ).apply {
                setMargins(0, 0, TaraTheme.dpToPx(context, 8f), 0)
            }
        }
        statusPill.addView(greenDot)

        val connectionText = TextView(context).apply {
            text = "Connected to Desktop (WiFi)"
            textSize = 12f
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
            setTextColor(Color.parseColor("#CBD5E1"))
        }
        statusPill.addView(connectionText)

        // ==========================================
        // 3. CENTERSTAGE: FLUID GLOWING TARA ORB
        // ==========================================
        val orbSizePx = TaraTheme.dpToPx(context, 230f)
        orbView = TaraOrbView(context) {
            handleOrbToggle()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(orbSizePx, orbSizePx).apply {
                setMargins(0, 0, 0, TaraTheme.dpToPx(context, 16f))
            }
        }
        root.addView(orbView)

        // ==========================================
        // 4. 2x2 ACTION CARDS GRID
        // ==========================================
        val gridContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, TaraTheme.dpToPx(context, 14f))
            }
        }
        root.addView(gridContainer)

        // Row 1: Voice Commands & Gesture Detection
        val gridRow1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, TaraTheme.dpToPx(context, 12f))
            }
        }
        gridContainer.addView(gridRow1)

        val voiceCard = createActionCard(
            title = "Voice\nCommands",
            iconRes = R.drawable.ic_mic,
            iconColor = TaraTheme.TaraCyan,
            onClick = { onNavigate("commands") }
        )
        gridRow1.addView(voiceCard)

        val gestureCard = createActionCard(
            title = "Gesture\nDetection",
            iconRes = R.drawable.ic_gesture_hand,
            iconColor = TaraTheme.TaraOrange,
            onClick = {
                Toast.makeText(context, "Gesture Detection active: Wave ✋ or Pinch to trigger actions", Toast.LENGTH_SHORT).show()
            }
        )
        gridRow1.addView(gestureCard)

        // Row 2: Recent Actions & Desktop Status
        val gridRow2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        gridContainer.addView(gridRow2)

        val recentCard = createActionCard(
            title = "Recent\nActions",
            iconRes = R.drawable.ic_history,
            iconColor = TaraTheme.TaraCyan,
            onClick = {
                onNavigate("notes")
            }
        )
        gridRow2.addView(recentCard)

        val desktopCard = createActionCard(
            title = "Desktop\nStatus",
            iconRes = R.drawable.ic_desktop_monitor,
            iconColor = TaraTheme.TaraCyan,
            onClick = {
                Toast.makeText(context, "Desktop Bridge: Connected via WiFi (192.168.1.100). Latency: 4ms", Toast.LENGTH_SHORT).show()
            }
        )
        gridRow2.addView(desktopCard)

        // ==========================================
        // 5. MASTER CONTROL TOGGLES CARD
        // ==========================================
        val togglesCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            padding(18f, 14f, 18f, 14f)
            background = TaraTheme.createCardDrawable(
                context,
                cornerRadiusDp = 18f,
                fillColor = Color.parseColor("#121622"),
                strokeColor = TaraTheme.BorderStroke,
                strokeWidthDp = 1f
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, TaraTheme.dpToPx(context, 16f))
            }
        }
        root.addView(togglesCard)

        // Row A: Voice Control
        val voiceRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        togglesCard.addView(voiceRow)

        val voiceLabelContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        voiceRow.addView(voiceLabelContainer)

        val voiceTitle = TextView(context).apply {
            text = "Voice Control "
            textSize = 14f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setTextColor(TaraTheme.TextPrimary)
        }
        voiceLabelContainer.addView(voiceTitle)

        voiceStatusLabel = TextView(context).apply {
            text = "(Off)"
            textSize = 13f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(TaraTheme.TextTertiary)
        }
        voiceLabelContainer.addView(voiceStatusLabel)

        voiceToggle = SwitchCompat(context).apply {
            isChecked = false
            contentDescription = "Voice Control switch"
            applySwitchColors(this, TaraTheme.TaraCyan)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (!isListeningActive()) onStartListening()
                } else {
                    if (isListeningActive()) onStopListening()
                }
                updateVoiceToggleLabel(isChecked)
            }
        }
        voiceRow.addView(voiceToggle)

        // Divider
        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#1C2333"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                TaraTheme.dpToPx(context, 1f)
            ).apply {
                setMargins(0, TaraTheme.dpToPx(context, 10f), 0, TaraTheme.dpToPx(context, 10f))
            }
        }
        togglesCard.addView(divider)

        // Row B: Gesture Control
        val gestureRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        togglesCard.addView(gestureRow)

        val gestureLabelContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        gestureRow.addView(gestureLabelContainer)

        val gestureTitle = TextView(context).apply {
            text = "Gesture Control "
            textSize = 14f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setTextColor(TaraTheme.TextPrimary)
        }
        gestureLabelContainer.addView(gestureTitle)

        gestureStatusLabel = TextView(context).apply {
            text = "(On)"
            textSize = 13f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(TaraTheme.TaraOrange)
        }
        gestureLabelContainer.addView(gestureStatusLabel)

        gestureToggle = SwitchCompat(context).apply {
            isChecked = true
            contentDescription = "Gesture Control switch"
            applySwitchColors(this, TaraTheme.TaraOrange)
            setOnCheckedChangeListener { _, isChecked ->
                isGestureActive = isChecked
                gestureStatusLabel.text = if (isChecked) "(On)" else "(Off)"
                gestureStatusLabel.setTextColor(if (isChecked) TaraTheme.TaraOrange else TaraTheme.TextTertiary)
                Toast.makeText(
                    context,
                    if (isChecked) "Gesture Control Enabled" else "Gesture Control Disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        gestureRow.addView(gestureToggle)

        // ==========================================
        // 6. LAST RESULT CARD (DYNAMIC)
        // ==========================================
        lastResultCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            padding(16f, 14f, 16f, 14f)
            background = TaraTheme.createCardDrawable(
                context,
                cornerRadiusDp = 16f,
                fillColor = Color.parseColor("#151A27"),
                strokeColor = TaraTheme.TaraCyan,
                strokeWidthDp = 1f
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, TaraTheme.dpToPx(context, 16f))
            }
            visibility = View.GONE
        }
        root.addView(lastResultCard)

        val resultBadge = TextView(context).apply {
            text = "LAST ACTION RESULT"
            textSize = 10f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(TaraTheme.TaraCyan)
            letterSpacing = 0.12f
        }
        lastResultCard.addView(resultBadge)

        lastResultTextView = TextView(context).apply {
            textSize = 13f
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
            setTextColor(TaraTheme.TextPrimary)
            setPadding(0, TaraTheme.dpToPx(context, 6f), 0, 0)
            setLineSpacing(TaraTheme.dpToPx(context, 2f).toFloat(), 1f)
        }
        lastResultCard.addView(lastResultTextView)

        // ==========================================
        // 7. BOTTOM CYBER AUDIO VISUALIZER & CAPTION
        // ==========================================
        visualizerView = TaraAudioVisualizerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                TaraTheme.dpToPx(context, 32f)
            ).apply {
                setMargins(0, 0, 0, TaraTheme.dpToPx(context, 8f))
            }
        }
        root.addView(visualizerView)

        statusCaption = TextView(context).apply {
            text = "Listening for commands..."
            textSize = 12f
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
            setTextColor(TaraTheme.TextSecondary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(statusCaption)
    }

    private fun createActionCard(
        title: String,
        iconRes: Int,
        iconColor: Int,
        onClick: () -> Unit
    ): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            padding(14f, 14f, 14f, 14f)
            val cardBg = TaraTheme.createCardDrawable(
                context,
                cornerRadiusDp = 18f,
                fillColor = Color.parseColor("#121622"),
                strokeColor = TaraTheme.BorderStroke,
                strokeWidthDp = 1f
            )
            background = TaraTheme.createRippleDrawable(context, cardBg)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                0,
                TaraTheme.dpToPx(context, 76f),
                1f
            ).apply {
                setMargins(
                    TaraTheme.dpToPx(context, 4f),
                    0,
                    TaraTheme.dpToPx(context, 4f),
                    0
                )
            }
            setOnClickListener { onClick() }
        }

        val textLabel = TextView(context).apply {
            text = title
            textSize = 13f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(TaraTheme.TextPrimary)
            setLineSpacing(TaraTheme.dpToPx(context, 1.5f).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        card.addView(textLabel)

        val iconView = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, iconRes))
            setColorFilter(iconColor)
            layoutParams = LinearLayout.LayoutParams(
                TaraTheme.dpToPx(context, 26f),
                TaraTheme.dpToPx(context, 26f)
            )
        }
        card.addView(iconView)

        return card
    }

    private fun applySwitchColors(switch: SwitchCompat, activeColor: Int) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbColors = intArrayOf(
            activeColor,
            TaraTheme.SwitchInactiveThumb
        )
        val trackColors = intArrayOf(
            TaraTheme.adjustAlpha(activeColor, 0.45f),
            TaraTheme.SwitchInactiveTrack
        )
        switch.thumbTintList = ColorStateList(states, thumbColors)
        switch.trackTintList = ColorStateList(states, trackColors)
    }

    private fun updateVoiceToggleLabel(isOn: Boolean) {
        voiceStatusLabel.text = if (isOn) "(On)" else "(Off)"
        voiceStatusLabel.setTextColor(if (isOn) TaraTheme.TaraCyan else TaraTheme.TextTertiary)
    }

    private fun handleOrbToggle() {
        if (isListeningActive()) {
            onStopListening()
        } else {
            onStartListening()
        }
    }

    private fun isListeningActive(): Boolean {
        return when (currentState) {
            AssistantState.IDLE,
            AssistantState.STOPPED,
            is AssistantState.ERROR -> false
            else -> true
        }
    }

    fun updateState(state: AssistantState) {
        currentState = state
        orbView.setState(state)
        visualizerView.setState(state)

        val active = isListeningActive()
        if (voiceToggle.isChecked != active) {
            voiceToggle.setOnCheckedChangeListener(null)
            voiceToggle.isChecked = active
            updateVoiceToggleLabel(active)
            voiceToggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (!isListeningActive()) onStartListening()
                } else {
                    if (isListeningActive()) onStopListening()
                }
                updateVoiceToggleLabel(isChecked)
            }
        }

        statusCaption.text = when (state) {
            AssistantState.IDLE, AssistantState.STOPPED -> "Microphone off • Tap orb to start"
            AssistantState.ACTIVATING, AssistantState.INITIALIZING -> "Preparing offline model..."
            AssistantState.WAKE_LISTENING -> "Listening for “Hey Tara”..."
            AssistantState.WAKE_DETECTED -> "Wake word detected • Listening for command..."
            AssistantState.LISTENING, AssistantState.RECORDING_COMMAND, AssistantState.TRANSCRIBING ->
                "Listening for commands..."
            AssistantState.RECOGNIZING -> "Transcribing speech on-device..."
            AssistantState.INFERENCING -> "Understanding command (FunctionGemma 270M)..."
            AssistantState.VALIDATING -> "Validating action schema..."
            AssistantState.AUTHORIZING -> "Awaiting biometric approval..."
            AssistantState.EXECUTING -> "Executing native action..."
            AssistantState.CONFIRMING -> "Action complete"
            AssistantState.CLEANUP -> "Finishing up..."
            is AssistantState.ERROR -> state.message
        }

        statusCaption.setTextColor(
            if (state is AssistantState.ERROR) TaraTheme.TaraRed else TaraTheme.TextSecondary
        )
    }

    fun updateLastResult(result: String?) {
        if (result.isNullOrBlank()) {
            lastResultCard.visibility = View.GONE
        } else {
            lastResultCard.visibility = View.VISIBLE
            lastResultTextView.text = result
        }
    }

    private fun View.padding(l: Float, t: Float, r: Float, b: Float) {
        setPadding(
            TaraTheme.dpToPx(context, l),
            TaraTheme.dpToPx(context, t),
            TaraTheme.dpToPx(context, r),
            TaraTheme.dpToPx(context, b)
        )
    }
}
