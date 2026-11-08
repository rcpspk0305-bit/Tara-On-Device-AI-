package dev.voice.offline.feature.commands

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import dev.voice.offline.R
import dev.voice.offline.ui.theme.GeminiTheme

class CommandsView(context: Context) : ScrollView(context) {

    private val cardsContainer: LinearLayout
    private val allCommandCards = mutableListOf<CommandData>()
    private var activeFilter: String = "ALL"

    data class CommandData(
        val category: String,
        val actionName: String,
        val examples: List<String>,
        val description: String,
        val securityNote: String = ""
    )

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
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_nav_commands))
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
            text = "Mobile Actions"
            textSize = 24f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(GeminiTheme.TextPrimary)
        }
        headerRow.addView(header)

        val subheader = TextView(context).apply {
            text = "13 native on-device actions parsed by FunctionGemma 270M. Say 'Hey Tara' followed by:"
            textSize = 13f
            setTextColor(GeminiTheme.TextSecondary)
            setPadding(0, 0, 0, GeminiTheme.dpToPx(context, 16f))
            setLineSpacing(GeminiTheme.dpToPx(context, 3f).toFloat(), 1f)
        }
        root.addView(subheader)

        // Category Filter Chips
        val filterScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, GeminiTheme.dpToPx(context, 20f))
            }
        }
        val filterContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        filterScrollView.addView(filterContainer)
        root.addView(filterScrollView)

        val filterChips = mutableMapOf<String, TextView>()
        val categories = listOf("ALL", "CALLS", "CONTROL", "NOTES", "MESSAGES")

        fun renderCards() {
            cardsContainer.removeAllViews()
            val filtered = if (activeFilter == "ALL") {
                allCommandCards
            } else {
                allCommandCards.filter { it.category == activeFilter }
            }

            for (data in filtered) {
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

                // Top row with function badge & category
                val cardTopRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                card.addView(cardTopRow)

                val fnBadge = TextView(context).apply {
                    text = data.actionName
                    textSize = 13f
                    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
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
                cardTopRow.addView(fnBadge)

                val spacer = android.view.View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                }
                cardTopRow.addView(spacer)

                val catLabel = TextView(context).apply {
                    text = data.category
                    textSize = 10f
                    setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                    setTextColor(GeminiTheme.TextTertiary)
                    letterSpacing = 0.08f
                }
                cardTopRow.addView(catLabel)

                // Description
                val desc = TextView(context).apply {
                    text = data.description
                    textSize = 13f
                    setTextColor(GeminiTheme.TextSecondary)
                    setPadding(0, GeminiTheme.dpToPx(context, 8f), 0, GeminiTheme.dpToPx(context, 10f))
                    setLineSpacing(GeminiTheme.dpToPx(context, 2f).toFloat(), 1f)
                }
                card.addView(desc)

                // Example speech container
                val exampleContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        GeminiTheme.dpToPx(context, 14f),
                        GeminiTheme.dpToPx(context, 10f),
                        GeminiTheme.dpToPx(context, 14f),
                        GeminiTheme.dpToPx(context, 10f)
                    )
                    background = GeminiTheme.createCardDrawable(
                        context,
                        cornerRadiusDp = 12f,
                        fillColor = Color.parseColor("#11131B"),
                        strokeColor = Color.TRANSPARENT,
                        strokeWidthDp = 0f
                    )
                }

                for (ex in data.examples) {
                    val exView = TextView(context).apply {
                        text = "• “$ex”"
                        textSize = 13f
                        setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
                        setTextColor(GeminiTheme.TextPrimary)
                        setPadding(0, GeminiTheme.dpToPx(context, 2f), 0, GeminiTheme.dpToPx(context, 2f))
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            Toast.makeText(context, "Say: “Hey Tara, $ex”", Toast.LENGTH_SHORT).show()
                        }
                    }
                    exampleContainer.addView(exView)
                }
                card.addView(exampleContainer)

                // Security Note if present
                if (data.securityNote.isNotBlank()) {
                    val secBadge = TextView(context).apply {
                        text = "🔒 ${data.securityNote}"
                        textSize = 11f
                        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                        setTextColor(GeminiTheme.GeminiAmber)
                        setPadding(
                            GeminiTheme.dpToPx(context, 8f),
                            GeminiTheme.dpToPx(context, 4f),
                            GeminiTheme.dpToPx(context, 8f),
                            GeminiTheme.dpToPx(context, 4f)
                        )
                        background = GeminiTheme.createPillDrawable(
                            context,
                            fillColor = Color.parseColor("#282010"),
                            strokeColor = Color.parseColor("#4A3B18"),
                            strokeWidthDp = 1f
                        )
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, GeminiTheme.dpToPx(context, 10f), 0, 0)
                        }
                    }
                    card.addView(secBadge)
                }

                cardsContainer.addView(card)
            }
        }

        for (cat in categories) {
            val chip = TextView(context).apply {
                text = cat
                textSize = 12f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setPadding(
                    GeminiTheme.dpToPx(context, 14f),
                    GeminiTheme.dpToPx(context, 8f),
                    GeminiTheme.dpToPx(context, 14f),
                    GeminiTheme.dpToPx(context, 8f)
                )
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, GeminiTheme.dpToPx(context, 8f), 0)
                }
                setOnClickListener {
                    activeFilter = cat
                    filterChips.forEach { (c, tv) ->
                        if (c == activeFilter) {
                            tv.setTextColor(Color.WHITE)
                            tv.background = GeminiTheme.createPillDrawable(
                                context,
                                fillColor = Color.parseColor("#252A3C"),
                                strokeColor = GeminiTheme.GeminiBlue,
                                strokeWidthDp = 1f
                            )
                        } else {
                            tv.setTextColor(GeminiTheme.TextSecondary)
                            tv.background = GeminiTheme.createPillDrawable(
                                context,
                                fillColor = GeminiTheme.CardSurface,
                                strokeColor = GeminiTheme.BorderStroke,
                                strokeWidthDp = 1f
                            )
                        }
                    }
                    renderCards()
                }
            }

            if (cat == activeFilter) {
                chip.setTextColor(Color.WHITE)
                chip.background = GeminiTheme.createPillDrawable(
                    context,
                    fillColor = Color.parseColor("#252A3C"),
                    strokeColor = GeminiTheme.GeminiBlue,
                    strokeWidthDp = 1f
                )
            } else {
                chip.setTextColor(GeminiTheme.TextSecondary)
                chip.background = GeminiTheme.createPillDrawable(
                    context,
                    fillColor = GeminiTheme.CardSurface,
                    strokeColor = GeminiTheme.BorderStroke,
                    strokeWidthDp = 1f
                )
            }

            filterChips[cat] = chip
            filterContainer.addView(chip)
        }

        cardsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(cardsContainer)

        // Populate commands
        allCommandCards.add(CommandData("CALLS", "make_call", listOf("call Amma", "phone Rahul", "call Alice on mobile"), "Resolves contact in address book and places a direct phone call.", "User confirmation required for calls"))
        allCommandCards.add(CommandData("CALLS", "answer_call", listOf("answer call", "answer the phone", "pick up"), "Answers an incoming ringing phone call via telecom subsystem."))
        allCommandCards.add(CommandData("CALLS", "reject_call", listOf("reject call", "decline call", "hang up"), "Declines the incoming ringing phone call."))
        allCommandCards.add(CommandData("CONTROL", "open_camera", listOf("open camera", "launch camera", "take a picture"), "Launches native camera viewfinder."))
        allCommandCards.add(CommandData("CONTROL", "lock_screen", listOf("lock screen", "lock my phone"), "Immediately locks the display via Device Policy Manager."))
        allCommandCards.add(CommandData("NOTES", "take_notes", listOf("take a note buy milk", "remember to pick up groceries", "note down meeting at 3pm"), "Stores an AES-256-GCM encrypted note locally in Keystore SQLite."))
        allCommandCards.add(CommandData("CONTROL", "open_app", listOf("open YouTube", "launch Maps", "open Calculator"), "Launches any installed application by name or package identifier."))
        allCommandCards.add(CommandData("CONTROL", "set_alarm", listOf("set alarm for 7:30 AM", "set an alarm at 8 AM labeled workout"), "Schedules a native clock alarm on the phone."))
        allCommandCards.add(CommandData("CONTROL", "turn_flashlight_on", listOf("turn on flashlight", "flashlight on", "torch on"), "Activates camera LED torch."))
        allCommandCards.add(CommandData("CONTROL", "turn_flashlight_off", listOf("turn off flashlight", "flashlight off", "torch off"), "Deactivates camera LED torch."))
        allCommandCards.add(CommandData("MESSAGES", "text_msg", listOf("text Amma saying I will be home soon", "message Rahul saying see you tomorrow"), "Drafts or sends a text message to a contact.", "Biometric authentication required"))
        allCommandCards.add(CommandData("MESSAGES", "read_notifications", listOf("read notifications", "what are my notifications"), "Reads recent unread notifications aloud."))
        allCommandCards.add(CommandData("MESSAGES", "reply_to_notification", listOf("reply saying on my way", "respond to WhatsApp saying yes"), "Sends a direct reply to an active notification via RemoteInput.", "Biometric authentication required"))

        renderCards()
    }
}
