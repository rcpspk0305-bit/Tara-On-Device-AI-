package dev.voice.offline.feature.notes

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
import dev.voice.offline.storage.PrivateNoteStore
import dev.voice.offline.ui.theme.GeminiTheme

class NotesView(context: Context) : ScrollView(context) {

    private val notesContainer: LinearLayout

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
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_nav_notes))
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
            text = "Encrypted Vault"
            textSize = 24f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(GeminiTheme.TextPrimary)
        }
        headerRow.addView(header)

        val subheader = TextView(context).apply {
            text = "Notes are encrypted with hardware-backed Android Keystore AES-256-GCM and stored only in local SQLite."
            textSize = 13f
            setTextColor(GeminiTheme.TextSecondary)
            setPadding(0, 0, 0, GeminiTheme.dpToPx(context, 16f))
            setLineSpacing(GeminiTheme.dpToPx(context, 3f).toFloat(), 1f)
        }
        root.addView(subheader)

        // Refresh Pill Button
        val refreshBtn = TextView(context).apply {
            text = "🔄  Refresh Vault"
            textSize = 13f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(GeminiTheme.TextPrimary)
            gravity = Gravity.CENTER
            setPadding(
                GeminiTheme.dpToPx(context, 18f),
                GeminiTheme.dpToPx(context, 10f),
                GeminiTheme.dpToPx(context, 18f),
                GeminiTheme.dpToPx(context, 10f)
            )
            val pillBg = GeminiTheme.createCardDrawable(
                context,
                cornerRadiusDp = 20f,
                fillColor = GeminiTheme.CardSurface,
                strokeColor = GeminiTheme.BorderStroke,
                strokeWidthDp = 1f
            )
            background = GeminiTheme.createRippleDrawable(context, pillBg)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, GeminiTheme.dpToPx(context, 18f))
            }
            setOnClickListener { loadNotes() }
        }
        root.addView(refreshBtn)

        notesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(notesContainer)

        loadNotes()
    }

    fun loadNotes() {
        notesContainer.removeAllViews()

        val notes = try {
            PrivateNoteStore(context).listRecent(50)
        } catch (_: Exception) {
            emptyList()
        }

        if (notes.isEmpty()) {
            val emptyCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    GeminiTheme.dpToPx(context, 24f),
                    GeminiTheme.dpToPx(context, 48f),
                    GeminiTheme.dpToPx(context, 24f),
                    GeminiTheme.dpToPx(context, 48f)
                )
                background = GeminiTheme.createCardDrawable(
                    context,
                    cornerRadiusDp = 20f,
                    fillColor = Color.parseColor("#12141C"),
                    strokeColor = GeminiTheme.BorderSubtle,
                    strokeWidthDp = 1f
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val emptyIcon = ImageView(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_gemini_sparkle))
                setColorFilter(GeminiTheme.TextTertiary)
                layoutParams = LinearLayout.LayoutParams(
                    GeminiTheme.dpToPx(context, 36f),
                    GeminiTheme.dpToPx(context, 36f)
                ).apply {
                    setMargins(0, 0, 0, GeminiTheme.dpToPx(context, 12f))
                }
            }
            emptyCard.addView(emptyIcon)

            val emptyTitle = TextView(context).apply {
                text = "No notes stored yet"
                textSize = 16f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(GeminiTheme.TextPrimary)
                gravity = Gravity.CENTER
            }
            emptyCard.addView(emptyTitle)

            val emptyDesc = TextView(context).apply {
                text = "Say: “Hey Tara, take a note buy groceries” to encrypt your first on-device note."
                textSize = 13f
                setTextColor(GeminiTheme.TextSecondary)
                gravity = Gravity.CENTER
                setPadding(0, GeminiTheme.dpToPx(context, 6f), 0, 0)
                setLineSpacing(GeminiTheme.dpToPx(context, 2f).toFloat(), 1f)
            }
            emptyCard.addView(emptyDesc)

            notesContainer.addView(emptyCard)
            return
        }

        for ((idx, noteText) in notes.withIndex()) {
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

            // Top Row
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            card.addView(topRow)

            val numBadge = TextView(context).apply {
                text = "NOTE #${idx + 1}"
                textSize = 11f
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(GeminiTheme.GeminiCyan)
                setPadding(
                    GeminiTheme.dpToPx(context, 8f),
                    GeminiTheme.dpToPx(context, 3f),
                    GeminiTheme.dpToPx(context, 8f),
                    GeminiTheme.dpToPx(context, 3f)
                )
                background = GeminiTheme.createPillDrawable(
                    context,
                    fillColor = Color.parseColor("#102230"),
                    strokeColor = Color.parseColor("#1B4560"),
                    strokeWidthDp = 1f
                )
            }
            topRow.addView(numBadge)

            val spacer = android.view.View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            }
            topRow.addView(spacer)

            val secBadge = TextView(context).apply {
                text = "AES-256-GCM"
                textSize = 10f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(GeminiTheme.GeminiMint)
                letterSpacing = 0.08f
            }
            topRow.addView(secBadge)

            // Content
            val contentView = TextView(context).apply {
                text = noteText
                textSize = 14f
                setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
                setTextColor(GeminiTheme.TextPrimary)
                setPadding(0, GeminiTheme.dpToPx(context, 10f), 0, 0)
                setLineSpacing(GeminiTheme.dpToPx(context, 3f).toFloat(), 1f)
            }
            card.addView(contentView)

            notesContainer.addView(card)
        }
    }
}
