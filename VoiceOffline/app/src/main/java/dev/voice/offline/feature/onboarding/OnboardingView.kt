package dev.voice.offline.feature.onboarding

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.voice.offline.R
import dev.voice.offline.ui.theme.GeminiTheme

class OnboardingView(
    context: Context,
    private val onGetStarted: () -> Unit
) : ScrollView(context) {

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
                GeminiTheme.dpToPx(context, 32f),
                GeminiTheme.dpToPx(context, 20f),
                GeminiTheme.dpToPx(context, 40f)
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }
        addView(root)

        // Gemini Sparkle Hero Icon
        val sparkleHero = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_gemini_sparkle))
            setColorFilter(GeminiTheme.GeminiBlue)
            layoutParams = LinearLayout.LayoutParams(
                GeminiTheme.dpToPx(context, 48f),
                GeminiTheme.dpToPx(context, 48f)
            ).apply {
                setMargins(0, 0, 0, GeminiTheme.dpToPx(context, 16f))
            }
        }
        root.addView(sparkleHero)

        val header = TextView(context).apply {
            text = "Welcome to Voice"
            textSize = 28f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(GeminiTheme.TextPrimary)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, GeminiTheme.dpToPx(context, 6f))
        }
        root.addView(header)

        val subheader = TextView(context).apply {
            text = "On-device intelligence. Zero cloud dependency."
            textSize = 14f
            setTextColor(GeminiTheme.TextSecondary)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, GeminiTheme.dpToPx(context, 28f))
        }
        root.addView(subheader)

        fun addFeatureCard(title: String, description: String, iconTag: String, accentColor: Int) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    GeminiTheme.dpToPx(context, 18f),
                    GeminiTheme.dpToPx(context, 16f),
                    GeminiTheme.dpToPx(context, 18f),
                    GeminiTheme.dpToPx(context, 16f)
                )
                background = GeminiTheme.createCardDrawable(
                    context,
                    cornerRadiusDp = 18f,
                    fillColor = GeminiTheme.CardSurface,
                    strokeColor = GeminiTheme.BorderStroke,
                    strokeWidthDp = 1f
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, GeminiTheme.dpToPx(context, 16f))
                }
            }

            val cardTop = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            card.addView(cardTop)

            val iconBadge = TextView(context).apply {
                text = iconTag
                textSize = 16f
                setPadding(0, 0, GeminiTheme.dpToPx(context, 10f), 0)
            }
            cardTop.addView(iconBadge)

            val cardTitle = TextView(context).apply {
                text = title
                textSize = 16f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(accentColor)
            }
            cardTop.addView(cardTitle)

            val cardDesc = TextView(context).apply {
                text = description
                textSize = 13f
                setTextColor(GeminiTheme.TextSecondary)
                setPadding(0, GeminiTheme.dpToPx(context, 8f), 0, 0)
                setLineSpacing(GeminiTheme.dpToPx(context, 2f).toFloat(), 1f)
            }
            card.addView(cardDesc)

            root.addView(card)
        }

        addFeatureCard(
            "100% On-Device AI",
            "Speech recognition and FunctionGemma 270M run entirely on your phone. No internet required, zero cloud telemetry, fully functional in Airplane Mode.",
            "🔒",
            GeminiTheme.GeminiMint
        )
        addFeatureCard(
            "Actions, Not Chatbot",
            "Built for execution: make calls, send texts, lock your screen, take encrypted notes, set alarms, and control flashlight instantly.",
            "⚡",
            GeminiTheme.GeminiCyan
        )
        addFeatureCard(
            "Biometric Hardware Security",
            "Sensitive operations like sending messages require Android Keystore biometric confirmation to safeguard your personal data.",
            "🛡️",
            GeminiTheme.GeminiPurple
        )
        addFeatureCard(
            "Full User Control",
            "Microphone streams only into volatile memory when you explicitly start Voice, with instant notification controls.",
            "🎙️",
            GeminiTheme.GeminiBlue
        )

        // Primary Action Button
        val buttonDrawable = GeminiTheme.createGeminiGradientDrawable(context, cornerRadiusDp = 28f)
        val startBtn = TextView(context).apply {
            text = "Get Started"
            textSize = 16f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(
                GeminiTheme.dpToPx(context, 28f),
                GeminiTheme.dpToPx(context, 16f),
                GeminiTheme.dpToPx(context, 28f),
                GeminiTheme.dpToPx(context, 16f)
            )
            background = GeminiTheme.createRippleDrawable(context, buttonDrawable)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, GeminiTheme.dpToPx(context, 16f), 0, GeminiTheme.dpToPx(context, 12f))
            }
            setOnClickListener { onGetStarted() }
        }
        root.addView(startBtn)
    }
}
