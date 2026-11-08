package dev.voice.offline.feature.onboarding

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.voice.offline.R
import dev.voice.offline.ui.theme.TaraTheme

/**
 * Modern onboarding experience for TARA.
 */
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
        setBackgroundColor(TaraTheme.Background)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                TaraTheme.dpToPx(context, 20f),
                TaraTheme.dpToPx(context, 32f),
                TaraTheme.dpToPx(context, 20f),
                TaraTheme.dpToPx(context, 40f)
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }
        addView(root)

        // TARA Hero Logo
        val heroIcon = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_tara_logo))
            layoutParams = LinearLayout.LayoutParams(
                TaraTheme.dpToPx(context, 64f),
                TaraTheme.dpToPx(context, 64f)
            ).apply {
                setMargins(0, 0, 0, TaraTheme.dpToPx(context, 16f))
            }
        }
        root.addView(heroIcon)

        val header = TextView(context).apply {
            text = "Welcome to Tara"
            textSize = 28f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(TaraTheme.TextPrimary)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, TaraTheme.dpToPx(context, 6f))
            letterSpacing = 0.08f
        }
        root.addView(header)

        val subheader = TextView(context).apply {
            text = "On-Device Voice & Gesture Controller. 100% offline neural intelligence."
            textSize = 14f
            setTextColor(TaraTheme.TextSecondary)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, TaraTheme.dpToPx(context, 28f))
        }
        root.addView(subheader)

        fun addFeatureCard(iconRes: Int, title: String, description: String, accentColor: Int = TaraTheme.TaraCyan) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(
                    TaraTheme.dpToPx(context, 16f),
                    TaraTheme.dpToPx(context, 16f),
                    TaraTheme.dpToPx(context, 16f),
                    TaraTheme.dpToPx(context, 16f)
                )
                background = TaraTheme.createCardDrawable(
                    context,
                    cornerRadiusDp = 18f,
                    fillColor = TaraTheme.CardSurface,
                    strokeColor = TaraTheme.BorderStroke,
                    strokeWidthDp = 1f
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, TaraTheme.dpToPx(context, 12f))
                }
            }

            val icon = ImageView(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, iconRes))
                setColorFilter(accentColor)
                layoutParams = LinearLayout.LayoutParams(
                    TaraTheme.dpToPx(context, 24f),
                    TaraTheme.dpToPx(context, 24f)
                ).apply {
                    setMargins(0, TaraTheme.dpToPx(context, 2f), TaraTheme.dpToPx(context, 12f), 0)
                }
            }
            card.addView(icon)

            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            card.addView(textContainer)

            val cardTitle = TextView(context).apply {
                text = title
                textSize = 15f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(TaraTheme.TextPrimary)
            }
            textContainer.addView(cardTitle)

            val cardDesc = TextView(context).apply {
                text = description
                textSize = 13f
                setTextColor(TaraTheme.TextSecondary)
                setPadding(0, TaraTheme.dpToPx(context, 4f), 0, 0)
                setLineSpacing(TaraTheme.dpToPx(context, 2f).toFloat(), 1f)
            }
            textContainer.addView(cardDesc)

            root.addView(card)
        }

        addFeatureCard(
            R.drawable.ic_mic,
            "Hands-Free Voice Actions",
            "Say “Hey Tara” to trigger 13 native Android actions without leaving your app.",
            TaraTheme.TaraCyan
        )

        addFeatureCard(
            R.drawable.ic_gesture_hand,
            "Intuitive Gesture Sensing",
            "Control apps and device states using on-device hand gesture detection.",
            TaraTheme.TaraOrange
        )

        addFeatureCard(
            R.drawable.ic_desktop_monitor,
            "Desktop Sync Bridge",
            "Pairs seamlessly with your desktop via low-latency local WiFi bridge.",
            TaraTheme.TaraCyan
        )

        addFeatureCard(
            R.drawable.ic_nav_security,
            "Keystore Hardware Security",
            "Your speech, gestures, and notes never leave your device. Protected by biometric authentication.",
            TaraTheme.TaraGreen
        )

        // Spacer
        val spacer = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        root.addView(spacer)

        // Get Started CTA Button
        val buttonDrawable = TaraTheme.createTaraGradientDrawable(context, cornerRadiusDp = 28f)
        val getStartedBtn = TextView(context).apply {
            text = "Get Started with Tara"
            textSize = 15f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(
                TaraTheme.dpToPx(context, 28f),
                TaraTheme.dpToPx(context, 16f),
                TaraTheme.dpToPx(context, 28f),
                TaraTheme.dpToPx(context, 16f)
            )
            background = TaraTheme.createRippleDrawable(context, buttonDrawable)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, TaraTheme.dpToPx(context, 24f), 0, 0)
            }
            setOnClickListener {
                onGetStarted()
            }
        }
        root.addView(getStartedBtn)
    }
}
