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
import dev.voice.offline.ui.theme.TaraTheme

/**
 * About screen for TARA: On-Device Voice & Gesture Controller.
 */
class AboutView(context: Context) : ScrollView(context) {

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
                TaraTheme.dpToPx(context, 24f),
                TaraTheme.dpToPx(context, 20f),
                TaraTheme.dpToPx(context, 32f)
            )
        }
        addView(root)

        // Header
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, TaraTheme.dpToPx(context, 6f))
        }
        root.addView(headerRow)

        val headerIcon = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_tara_logo))
            layoutParams = LinearLayout.LayoutParams(
                TaraTheme.dpToPx(context, 28f),
                TaraTheme.dpToPx(context, 28f)
            ).apply {
                setMargins(0, 0, TaraTheme.dpToPx(context, 8f), 0)
            }
        }
        headerRow.addView(headerIcon)

        val header = TextView(context).apply {
            text = "About Tara"
            textSize = 24f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(TaraTheme.TextPrimary)
        }
        headerRow.addView(header)

        val subheader = TextView(context).apply {
            text = "On-Device Voice & Gesture Controller. Air-gapped neural intelligence for high-agency mobile command."
            textSize = 13f
            setTextColor(TaraTheme.TextSecondary)
            setPadding(0, 0, 0, TaraTheme.dpToPx(context, 20f))
            setLineSpacing(TaraTheme.dpToPx(context, 3f).toFloat(), 1f)
        }
        root.addView(subheader)

        fun addInfoSection(title: String, content: String, accentColor: Int = TaraTheme.TaraCyan) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    TaraTheme.dpToPx(context, 18f),
                    TaraTheme.dpToPx(context, 16f),
                    TaraTheme.dpToPx(context, 18f),
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
                    setMargins(0, 0, 0, TaraTheme.dpToPx(context, 14f))
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
                setTextColor(TaraTheme.TextPrimary)
                setPadding(0, TaraTheme.dpToPx(context, 8f), 0, 0)
                setLineSpacing(TaraTheme.dpToPx(context, 3f).toFloat(), 1f)
            }
            card.addView(contentView)

            root.addView(card)
        }

        addInfoSection(
            "Dual-Modal Controller: Voice & Gesture",
            "Tara seamlessly pairs hands-free voice commands with responsive gesture sensing. Wave, pinch, or speak to execute native device actions with zero cloud dependency.",
            TaraTheme.TaraCyan
        )

        addInfoSection(
            "Air-Gapped Offline Guarantee",
            "Tara contains NO android.permission.INTERNET. It physically cannot upload voice, sensor, or biometric data to remote servers or cloud analytics. 100% operational in Airplane Mode.",
            TaraTheme.TaraGreen
        )

        addInfoSection(
            "On-Device AI Engine",
            "• Intent Model: FunctionGemma 270M Mobile Actions (Quantized)\n" +
            "• Runtime Engine: Google LiteRT-LM (.litertlm)\n" +
            "• Speech Engine: Vosk Offline STT with local acoustic phonetics\n" +
            "• Gesture Detection: On-device optical & sensor heuristics",
            TaraTheme.TaraOrange
        )

        addInfoSection(
            "Desktop WiFi Bridge",
            "Low-latency local network bridge synchronizes status with your paired desktop station for seamless cross-device control.",
            TaraTheme.TaraCyan
        )

        addInfoSection(
            "Hardware-Backed Biometrics",
            "High-risk actions (SMS dispatch, biometric lock, system changes) are cryptographically gated by Android Keystore EC P-256 signatures and BiometricPrompt.",
            TaraTheme.TaraOrangeLight
        )

        addInfoSection(
            "System Information",
            "Tara v1.0.0 (On-Device Voice & Gesture Edition)\nPackage: dev.voice.offline\nCompile SDK: 36 (Android 16 Ready)",
            TaraTheme.TextSecondary
        )
    }
}
