package dev.voice.offline.feature.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import dev.voice.offline.R
import dev.voice.offline.service.AssistantState
import dev.voice.offline.ui.components.GeminiOrbView
import dev.voice.offline.ui.theme.GeminiTheme

/**
 * Google Gemini-inspired Home screen for Voice assistant.
 * Features the interactive pulsating Gemini Live Orb, state badges,
 * Gemini prompt suggestion chips, and sleek on-device action cards.
 */
class HomeView(
    context: Context,
    private val onStartListening: () -> Unit,
    private val onStopListening: () -> Unit,
    private val onNavigate: (String) -> Unit
) : ScrollView(context) {

    private val orbView: GeminiOrbView
    private val stateBadge: TextView
    private val actionButton: TextView
    private val lastResultCard: LinearLayout
    private val lastResultTextView: TextView
    private var currentState: AssistantState = AssistantState.IDLE

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        isFillViewport = true
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                GeminiTheme.dpToPx(context, 20f),
                GeminiTheme.dpToPx(context, 24f),
                GeminiTheme.dpToPx(context, 20f),
                GeminiTheme.dpToPx(context, 32f)
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }
        addView(root)

        // 1. Header with Gemini Sparkle & Title
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, GeminiTheme.dpToPx(context, 6f))
        }
        root.addView(headerRow)

        val sparkleIcon = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_gemini_sparkle))
            setColorFilter(GeminiTheme.GeminiBlue)
            layoutParams = LinearLayout.LayoutParams(
                GeminiTheme.dpToPx(context, 26f),
                GeminiTheme.dpToPx(context, 26f)
            ).apply {
                setMargins(0, 0, GeminiTheme.dpToPx(context, 8f), 0)
            }
        }
        headerRow.addView(sparkleIcon)

        val title = TextView(context).apply {
            text = "Voice"
            textSize = 28f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(GeminiTheme.TextPrimary)
        }
        headerRow.addView(title)

        // Subtitle Pill
        val badgeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                GeminiTheme.dpToPx(context, 12f),
                GeminiTheme.dpToPx(context, 4f),
                GeminiTheme.dpToPx(context, 12f),
                GeminiTheme.dpToPx(context, 4f)
            )
            background = GeminiTheme.createPillDrawable(
                context,
                fillColor = Color.parseColor("#141620"),
                strokeColor = GeminiTheme.BorderStroke,
                strokeWidthDp = 1f
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, GeminiTheme.dpToPx(context, 24f))
            }
        }
        root.addView(badgeContainer)

        val badgeDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(GeminiTheme.GeminiMint)
            }
            layoutParams = LinearLayout.LayoutParams(
                GeminiTheme.dpToPx(context, 6f),
                GeminiTheme.dpToPx(context, 6f)
            ).apply {
                setMargins(0, 0, GeminiTheme.dpToPx(context, 6f), 0)
            }
        }
        badgeContainer.addView(badgeDot)

        val badgeText = TextView(context).apply {
            text = "OFFLINE • LOCAL COMMAND PARSER"
            textSize = 10f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setTextColor(GeminiTheme.TextSecondary)
            letterSpacing = 0.08f
        }
        badgeContainer.addView(badgeText)

        // 2. Interactive Gemini Live Orb Center Stage
        val orbSizePx = GeminiTheme.dpToPx(context, 210f)
        orbView = GeminiOrbView(context) {
            handleOrbToggle()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(orbSizePx, orbSizePx).apply {
                setMargins(0, GeminiTheme.dpToPx(context, 8f), 0, GeminiTheme.dpToPx(context, 16f))
            }
        }
        root.addView(orbView)

        // 3. State Status Pill
        stateBadge = TextView(context).apply {
            text = "Offline • Tap orb to start"
            textSize = 13f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setTextColor(GeminiTheme.TextSecondary)
            gravity = Gravity.CENTER
            setPadding(
                GeminiTheme.dpToPx(context, 16f),
                GeminiTheme.dpToPx(context, 8f),
                GeminiTheme.dpToPx(context, 16f),
                GeminiTheme.dpToPx(context, 8f)
            )
            background = GeminiTheme.createPillDrawable(
                context,
                fillColor = GeminiTheme.CardSurface,
                strokeColor = GeminiTheme.BorderStroke,
                strokeWidthDp = 1f
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, GeminiTheme.dpToPx(context, 20f))
            }
        }
        root.addView(stateBadge)

        // 4. Primary CTA Pill Button
        val buttonDrawable = GeminiTheme.createGeminiGradientDrawable(context, cornerRadiusDp = 28f)
        actionButton = TextView(context).apply {
            text = "Speak one command"
            textSize = 15f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(
                GeminiTheme.dpToPx(context, 28f),
                GeminiTheme.dpToPx(context, 14f),
                GeminiTheme.dpToPx(context, 28f),
                GeminiTheme.dpToPx(context, 14f)
            )
            background = GeminiTheme.createRippleDrawable(context, buttonDrawable)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, GeminiTheme.dpToPx(context, 24f))
            }
            setOnClickListener {
                handleOrbToggle()
            }
        }
        root.addView(actionButton)

        // 5. Prompt Suggestion Chips (Gemini style)
        val chipsLabel = TextView(context).apply {
            text = "TRY ASKING"
            textSize = 11f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(GeminiTheme.TextTertiary)
            letterSpacing = 0.12f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(GeminiTheme.dpToPx(context, 4f), 0, 0, GeminiTheme.dpToPx(context, 10f))
            }
        }
        root.addView(chipsLabel)

        val chipsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, GeminiTheme.dpToPx(context, 24f))
            }
        }
        val chipsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        chipsScroll.addView(chipsContainer)
        root.addView(chipsScroll)

        fun addSuggestionChip(icon: String, phrase: String) {
            val chip = TextView(context).apply {
                text = "$icon  $phrase"
                textSize = 13f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                setTextColor(GeminiTheme.TextPrimary)
                setPadding(
                    GeminiTheme.dpToPx(context, 16f),
                    GeminiTheme.dpToPx(context, 10f),
                    GeminiTheme.dpToPx(context, 16f),
                    GeminiTheme.dpToPx(context, 10f)
                )
                val chipBg = GeminiTheme.createCardDrawable(
                    context,
                    cornerRadiusDp = 20f,
                    fillColor = GeminiTheme.CardSurface,
                    strokeColor = GeminiTheme.BorderStroke,
                    strokeWidthDp = 1f
                )
                background = GeminiTheme.createRippleDrawable(context, chipBg)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, GeminiTheme.dpToPx(context, 10f), 0)
                }
                setOnClickListener {
                    Toast.makeText(context, "Tap Speak, then say: “$phrase”", Toast.LENGTH_SHORT).show()
                }
            }
            chipsContainer.addView(chip)
        }

        addSuggestionChip("📞", "Call Amma")
        addSuggestionChip("🔦", "Turn on flashlight")
        addSuggestionChip("📝", "Take a note buy milk")
        addSuggestionChip("🔒", "Lock phone screen")
        addSuggestionChip("⏰", "Set alarm for 7:00 AM")
        addSuggestionChip("💬", "Text Rahul saying on my way")
        addSuggestionChip("📷", "Open Camera")
        addSuggestionChip("🔔", "Read notifications")

        // 6. Last Result Card
        lastResultCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                GeminiTheme.dpToPx(context, 20f),
                GeminiTheme.dpToPx(context, 18f),
                GeminiTheme.dpToPx(context, 20f),
                GeminiTheme.dpToPx(context, 18f)
            )
            background = GeminiTheme.createCardDrawable(
                context,
                cornerRadiusDp = 20f,
                fillColor = GeminiTheme.CardSurface,
                strokeColor = GeminiTheme.GeminiBlue,
                strokeWidthDp = 1f
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, GeminiTheme.dpToPx(context, 24f))
            }
            visibility = View.GONE
        }
        root.addView(lastResultCard)

        val resultHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        lastResultCard.addView(resultHeaderRow)

        val resultBadge = TextView(context).apply {
            text = "LAST RESULT"
            textSize = 10f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(GeminiTheme.GeminiCyan)
            letterSpacing = 0.12f
        }
        resultHeaderRow.addView(resultBadge)

        lastResultTextView = TextView(context).apply {
            textSize = 14f
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
            setTextColor(GeminiTheme.TextPrimary)
            setPadding(0, GeminiTheme.dpToPx(context, 8f), 0, 0)
            setLineSpacing(GeminiTheme.dpToPx(context, 3f).toFloat(), 1f)
        }
        lastResultCard.addView(lastResultTextView)

        // 7. Privacy Shield Guarantee Banner
        val privacyBanner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                GeminiTheme.dpToPx(context, 16f),
                GeminiTheme.dpToPx(context, 12f),
                GeminiTheme.dpToPx(context, 16f),
                GeminiTheme.dpToPx(context, 12f)
            )
            background = GeminiTheme.createCardDrawable(
                context,
                cornerRadiusDp = 16f,
                fillColor = Color.parseColor("#10121A"),
                strokeColor = GeminiTheme.BorderSubtle,
                strokeWidthDp = 1f
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(privacyBanner)

        val shieldIcon = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_nav_security))
            setColorFilter(GeminiTheme.GeminiMint)
            layoutParams = LinearLayout.LayoutParams(
                GeminiTheme.dpToPx(context, 18f),
                GeminiTheme.dpToPx(context, 18f)
            ).apply {
                setMargins(0, 0, GeminiTheme.dpToPx(context, 8f), 0)
            }
        }
        privacyBanner.addView(shieldIcon)

        val privacyText = TextView(context).apply {
            text = "Wake word “Hey Voice” • Keystore AES-256 • Zero Cloud Telemetry"
            textSize = 11f
            setTextColor(GeminiTheme.TextTertiary)
        }
        privacyBanner.addView(privacyText)
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
            is AssistantState.ERROR -> false
            else -> true
        }
    }

    fun updateState(state: AssistantState) {
        currentState = state
        orbView.setState(state)
        actionButton.text = if (isListeningActive()) "Cancel interaction" else "Speak one command"
        stateBadge.text = when (state) {
            AssistantState.IDLE, AssistantState.STOPPED -> "Microphone off. Tap to speak."
            AssistantState.ACTIVATING, AssistantState.INITIALIZING -> "Preparing offline speech. Microphone off."
            AssistantState.WAKE_LISTENING -> "Microphone active for Hey Voice"
            AssistantState.WAKE_DETECTED -> "Wake phrase detected. Microphone active."
            AssistantState.LISTENING, AssistantState.RECORDING_COMMAND, AssistantState.TRANSCRIBING ->
                "Microphone active. Say one command."
            AssistantState.RECOGNIZING -> "Finalizing speech. Microphone off."
            AssistantState.INFERENCING -> "Understanding command. Microphone off."
            AssistantState.VALIDATING -> "Checking command. Microphone off."
            AssistantState.AUTHORIZING -> "Approval pending in notifications. Microphone off."
            AssistantState.EXECUTING -> "Executing action. Microphone off."
            AssistantState.CONFIRMING -> "Action result received. Microphone off."
            AssistantState.CLEANUP -> "Finishing. Microphone off."
            is AssistantState.ERROR -> state.message
        }
        stateBadge.setTextColor(if (state is AssistantState.ERROR) GeminiTheme.GeminiRed else GeminiTheme.TextSecondary)
    }

    fun updateLastResult(result: String?) {
        if (result.isNullOrBlank()) {
            lastResultCard.visibility = View.GONE
        } else {
            lastResultCard.visibility = View.VISIBLE
            lastResultTextView.text = result
        }
    }
}
