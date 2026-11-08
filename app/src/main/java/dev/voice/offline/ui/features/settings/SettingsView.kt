package dev.voice.offline.ui.features.settings

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
import dev.voice.offline.ui.theme.GeminiTheme

class SettingsView(context: Context,
    private val onEnableHandsFree: () -> Unit = {},
    private val onChooseAssistant: () -> Unit = {},
    private val onOpenAssistantSettings: () -> Unit = {}
) : ScrollView(context) {
    private var assistantStatus: TextView? = null

    fun refreshAssistantStatus() {
        val roles = dev.voice.offline.assistant.AssistantRoleManager(context)
        assistantStatus?.text = when {
            roles.isSelected -> "Tara is the selected assistant. Gestures depend on this device."
            roles.isAvailable -> "Tara is not the selected assistant. Tap Choose to request the role."
            else -> "Assistant role requests are unavailable. Manual activation remains available."
        }
    }

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        isFillViewport = true
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        setBackgroundColor(GeminiTheme.Background)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                GeminiTheme.dpToPx(context, 20f),
                GeminiTheme.dpToPx(context, 24f),
                GeminiTheme.dpToPx(context, 20f),
                GeminiTheme.dpToPx(context, 32f)
            )
        }
        addView(root)

        // Header
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, GeminiTheme.dpToPx(context, 6f))
        }
        root.addView(headerRow)

        val headerIcon = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_nav_settings))
            setColorFilter(GeminiTheme.GeminiBlue)
            layoutParams = LinearLayout.LayoutParams(
                GeminiTheme.dpToPx(context, 24f),
                GeminiTheme.dpToPx(context, 24f)
            ).apply {
                setMargins(0, 0, GeminiTheme.dpToPx(context, 8f), 0)
            }
        }
        headerRow.addView(headerIcon)

        val header = TextView(context).apply {
            text = "Tara Settings"
            textSize = 24f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(GeminiTheme.TextPrimary)
        }
        headerRow.addView(header)

        val subheader = TextView(context).apply {
            text = "Acoustic parameters, wake sensitivity, silence detection, and audio feedback chime."
            textSize = 13f
            setTextColor(GeminiTheme.TextSecondary)
            setPadding(0, 0, 0, GeminiTheme.dpToPx(context, 20f))
            setLineSpacing(GeminiTheme.dpToPx(context, 3f).toFloat(), 1f)
        }
        root.addView(subheader)

        fun addSettingItem(title: String, currentVal: String, description: String) {
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
                    setMargins(0, 0, 0, GeminiTheme.dpToPx(context, 14f))
                }
            }

            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            card.addView(topRow)

            val titleView = TextView(context).apply {
                text = title
                textSize = 15f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(GeminiTheme.TextPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            topRow.addView(titleView)

            val valPill = TextView(context).apply {
                text = currentVal
                textSize = 11f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(GeminiTheme.GeminiCyan)
                setPadding(
                    GeminiTheme.dpToPx(context, 10f),
                    GeminiTheme.dpToPx(context, 4f),
                    GeminiTheme.dpToPx(context, 10f),
                    GeminiTheme.dpToPx(context, 4f)
                )
                background = GeminiTheme.createPillDrawable(
                    context,
                    fillColor = Color.parseColor("#122330"),
                    strokeColor = Color.parseColor("#1B4560"),
                    strokeWidthDp = 1f
                )
            }
            topRow.addView(valPill)

            val descView = TextView(context).apply {
                text = description
                textSize = 13f
                setTextColor(GeminiTheme.TextSecondary)
                setPadding(0, GeminiTheme.dpToPx(context, 8f), 0, 0)
                setLineSpacing(GeminiTheme.dpToPx(context, 2f).toFloat(), 1f)
            }
            card.addView(descView)

            root.addView(card)
        }

        assistantStatus = TextView(context).apply {
            setTextColor(GeminiTheme.TextSecondary)
        }
        root.addView(assistantStatus)
        refreshAssistantStatus()
        root.addView(android.widget.Button(context).apply {
            text = "Choose Tara as default assistant"
            setOnClickListener { onChooseAssistant() }
        })
        root.addView(android.widget.Button(context).apply {
            text = "Open Android assistant settings"
            setOnClickListener { onOpenAssistantSettings() }
        })
        root.addView(android.widget.Button(context).apply {
            text = "Enable hands-free Hey Tara"
            setOnClickListener { onEnableHandsFree() }
        })
        root.addView(TextView(context).apply {
            text = dev.voice.offline.assistant.AssistantCapabilityChecker(context).hotwordDescription
            setTextColor(GeminiTheme.TextSecondary)
        })

        addSettingItem("Wake Phrase", "“Hey Tara” / “Okay Tara”", "Optional Vosk software wake decoder. Microphone stays active; battery cost is unmeasured.")
        addSettingItem("Recognition Language", "Packaged English model", "Accuracy depends on the packaged Vosk model. Indian English accuracy needs device testing.")
        addSettingItem("Listening Sensitivity", "Standard (Cooldown 2.0s)", "Prevents false-positive duplicate triggers during conversations.")
        addSettingItem("Silence Timeout", "3.0 Seconds", "Automatically finishes recording command after user stops speaking.")
        addSettingItem("Audio Feedback Chime", "Enabled", "Plays a short confirmation chime when wake word or action completes.")
    }
}
