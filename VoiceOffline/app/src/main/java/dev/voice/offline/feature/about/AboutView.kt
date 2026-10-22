package dev.voice.offline.feature.about

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

class AboutView(context: Context) : ScrollView(context) {

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
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_nav_about))
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
            text = "About Voice"
            textSize = 24f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(GeminiTheme.TextPrimary)
        }
        headerRow.addView(header)

        val subheader = TextView(context).apply {
            text = "Air-gapped on-device intelligence built for privacy, performance, and complete user agency."
            textSize = 13f
            setTextColor(GeminiTheme.TextSecondary)
            setPadding(0, 0, 0, GeminiTheme.dpToPx(context, 20f))
            setLineSpacing(GeminiTheme.dpToPx(context, 3f).toFloat(), 1f)
        }
        root.addView(subheader)

        fun addInfoSection(title: String, content: String, accentColor: Int = GeminiTheme.GeminiBlue) {
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

            val titleView = TextView(context).apply {
                text = title
                textSize = 15f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(accentColor)
            }
            card.addView(titleView)

            val contentView = TextView(context).apply {
                text = content
                textSize = 13f
                setTextColor(GeminiTheme.TextPrimary)
                setPadding(0, GeminiTheme.dpToPx(context, 8f), 0, 0)
                setLineSpacing(GeminiTheme.dpToPx(context, 3f).toFloat(), 1f)
            }
            card.addView(contentView)

            root.addView(card)
        }

        addInfoSection(
            "Air-Gapped Offline Guarantee",
            "The offline build of Voice contains NO android.permission.INTERNET in its AndroidManifest. It physically cannot connect to remote servers, cloud APIs, telemetry services, or analytics platforms. Fully functional in Airplane Mode.",
            GeminiTheme.GeminiMint
        )

        addInfoSection(
            "On-Device AI Models",
            "• Intent Model: FunctionGemma 270M Mobile Actions (quantized)\n" +
            "• Runtime Engine: Google LiteRT-LM (.litertlm)\n" +
            "• Speech Recognition: Vosk Offline STT with Indian English acoustic phonetics",
            GeminiTheme.GeminiPurple
        )

        addInfoSection(
            "Microphone & Audio Privacy",
            "Raw PCM microphone audio is streamed strictly to volatile RAM for wake-word and command transcription, and immediately discarded. Voice does not record, log, or persist audio files to storage.",
            GeminiTheme.GeminiCyan
        )

        addInfoSection(
            "Local Data Encryption",
            "All notes are stored in local SQLite encrypted with AES-256-GCM under Android Keystore. Sensitive actions require BiometricPrompt with cryptographic signature verification.",
            GeminiTheme.GeminiAmber
        )

        addInfoSection(
            "Product Architecture",
            "Voice v0.1.0 (Mobile Actions Edition)\nPackage: dev.voice.offline\nCompile SDK: 36 (Android 16 Ready)\nTarget: 100% On-Device Neural Execution",
            GeminiTheme.GeminiBlue
        )
    }
}
