package dev.voice.offline.feature.security

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
import dev.voice.offline.security.BiometricAuthorizationManager
import dev.voice.offline.security.BiometricReadiness
import dev.voice.offline.security.HardwareSecurityTier
import dev.voice.offline.security.KeystoreManager
import dev.voice.offline.ui.theme.GeminiTheme

class SecurityView(context: Context) : ScrollView(context) {

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
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_nav_security))
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
            text = "Hardware Security"
            textSize = 24f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(GeminiTheme.TextPrimary)
        }
        headerRow.addView(header)

        val subheader = TextView(context).apply {
            text = "Device-bound cryptographic backing, biometric authentication gates, and zero-trust verification."
            textSize = 13f
            setTextColor(GeminiTheme.TextSecondary)
            setPadding(0, 0, 0, GeminiTheme.dpToPx(context, 20f))
            setLineSpacing(GeminiTheme.dpToPx(context, 3f).toFloat(), 1f)
        }
        root.addView(subheader)

        fun addStatusCard(title: String, value: String, subtitle: String, isOk: Boolean) {
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
                textSize = 11f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(GeminiTheme.TextTertiary)
                letterSpacing = 0.08f
            }
            card.addView(titleView)

            val valRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, GeminiTheme.dpToPx(context, 4f), 0, GeminiTheme.dpToPx(context, 4f))
            }
            card.addView(valRow)

            val valView = TextView(context).apply {
                text = value
                textSize = 18f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(if (isOk) GeminiTheme.GeminiMint else GeminiTheme.GeminiAmber)
            }
            valRow.addView(valView)

            val badgePill = TextView(context).apply {
                text = if (isOk) "ACTIVE" else "ATTENTION"
                textSize = 10f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(if (isOk) GeminiTheme.GeminiMint else GeminiTheme.GeminiAmber)
                setPadding(
                    GeminiTheme.dpToPx(context, 8f),
                    GeminiTheme.dpToPx(context, 2f),
                    GeminiTheme.dpToPx(context, 8f),
                    GeminiTheme.dpToPx(context, 2f)
                )
                background = GeminiTheme.createPillDrawable(
                    context,
                    fillColor = if (isOk) Color.parseColor("#12251D") else Color.parseColor("#282010"),
                    strokeColor = if (isOk) Color.parseColor("#1A4D37") else Color.parseColor("#4A3B18"),
                    strokeWidthDp = 1f
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(GeminiTheme.dpToPx(context, 10f), 0, 0, 0)
                }
            }
            valRow.addView(badgePill)

            val subView = TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(GeminiTheme.TextSecondary)
                setPadding(0, GeminiTheme.dpToPx(context, 4f), 0, 0)
                setLineSpacing(GeminiTheme.dpToPx(context, 2f).toFloat(), 1f)
            }
            card.addView(subView)

            root.addView(card)
        }

        val bioManager = BiometricAuthorizationManager(context)
        val readiness = bioManager.checkBiometricReadiness()
        val hwTier = KeystoreManager.getHardwareSecurityTier()

        addStatusCard(
            "BIOMETRIC ENROLLMENT",
            readiness.name,
            "Required to authorize sensitive voice commands like messaging and notifications.",
            readiness == BiometricReadiness.READY
        )

        addStatusCard(
            "HARDWARE SECURITY TIER",
            hwTier.name,
            "Audited device cryptographic hardware backing for Android Keystore operations.",
            hwTier == HardwareSecurityTier.STRONGBOX || hwTier == HardwareSecurityTier.TRUSTED_ENVIRONMENT
        )

        addStatusCard(
            "KEYSTORE CRYPTOGRAPHY",
            "AES-256-GCM + EC P-256",
            "Notes encrypted on-device with hardware-backed AES keys; invalidates on biometric changes.",
            true
        )

        addStatusCard(
            "COMMAND TTL TIMEOUT",
            "30 SECONDS",
            "High-risk pending actions expire automatically to prevent replay attacks.",
            true
        )
    }
}
