package dev.voice.offline.feature.permissions

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.voice.offline.R
import dev.voice.offline.system.ReplyNotificationListener
import dev.voice.offline.system.VoiceAdminReceiver
import dev.voice.offline.ui.theme.GeminiTheme

class PermissionsView(
    context: Context,
    private val onRequestPermission: (Array<String>, Int) -> Unit
) : ScrollView(context) {

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
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_nav_permissions))
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
            text = "Permissions & Access"
            textSize = 24f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(GeminiTheme.TextPrimary)
        }
        headerRow.addView(header)

        val subheader = TextView(context).apply {
            text = "Voice requires explicit permissions to interact with your phone offline. No permission is granted silently."
            textSize = 13f
            setTextColor(GeminiTheme.TextSecondary)
            setPadding(0, 0, 0, GeminiTheme.dpToPx(context, 20f))
            setLineSpacing(GeminiTheme.dpToPx(context, 3f).toFloat(), 1f)
        }
        root.addView(subheader)

        fun addPermissionRow(
            name: String,
            description: String,
            isGranted: Boolean,
            actionLabel: String,
            onAction: () -> Unit
        ) {
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
                text = name
                textSize = 16f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(GeminiTheme.TextPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            topRow.addView(titleView)

            val statusBadge = TextView(context).apply {
                text = if (isGranted) "GRANTED" else "REQUIRED"
                textSize = 10f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(if (isGranted) GeminiTheme.GeminiMint else GeminiTheme.GeminiAmber)
                setPadding(
                    GeminiTheme.dpToPx(context, 10f),
                    GeminiTheme.dpToPx(context, 3f),
                    GeminiTheme.dpToPx(context, 10f),
                    GeminiTheme.dpToPx(context, 3f)
                )
                background = GeminiTheme.createPillDrawable(
                    context,
                    fillColor = if (isGranted) Color.parseColor("#12251D") else Color.parseColor("#282010"),
                    strokeColor = if (isGranted) Color.parseColor("#1A4D37") else Color.parseColor("#4A3B18"),
                    strokeWidthDp = 1f
                )
            }
            topRow.addView(statusBadge)

            val descView = TextView(context).apply {
                text = description
                textSize = 13f
                setTextColor(GeminiTheme.TextSecondary)
                setPadding(0, GeminiTheme.dpToPx(context, 8f), 0, if (!isGranted) GeminiTheme.dpToPx(context, 12f) else 0)
                setLineSpacing(GeminiTheme.dpToPx(context, 2f).toFloat(), 1f)
            }
            card.addView(descView)

            if (!isGranted) {
                val btn = TextView(context).apply {
                    text = actionLabel
                    textSize = 13f
                    setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(
                        GeminiTheme.dpToPx(context, 18f),
                        GeminiTheme.dpToPx(context, 10f),
                        GeminiTheme.dpToPx(context, 18f),
                        GeminiTheme.dpToPx(context, 10f)
                    )
                    val btnBg = GeminiTheme.createCardDrawable(
                        context,
                        cornerRadiusDp = 20f,
                        fillColor = Color.parseColor("#1F273D"),
                        strokeColor = GeminiTheme.GeminiBlue,
                        strokeWidthDp = 1f
                    )
                    background = GeminiTheme.createRippleDrawable(context, btnBg)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onAction() }
                }
                card.addView(btn)
            }

            root.addView(card)
        }

        // Microphone
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        addPermissionRow("Microphone", "Needed to capture voice commands and detect wake phrase offline in RAM.", micGranted, "Grant Microphone") {
            onRequestPermission(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }

        // Notifications
        val notifGranted = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        addPermissionRow("Notifications", "Displays listening state and biometric verification prompts.", notifGranted, "Grant Notifications") {
            if (Build.VERSION.SDK_INT >= 33) {
                onRequestPermission(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Contacts & Phone
        val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val phoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val callAnswerGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
        val telephonyGranted = contactsGranted && phoneGranted && callAnswerGranted

        addPermissionRow("Phone & Contacts", "Allows searching contacts by name to initiate calls or answer incoming calls.", telephonyGranted, "Grant Phone & Contacts") {
            onRequestPermission(arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ANSWER_PHONE_CALLS
            ), 102)
        }

        // Device Admin (Screen Locking)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, VoiceAdminReceiver::class.java)
        val adminActive = dpm?.isAdminActive(admin) == true
        addPermissionRow("Device Administrator", "Allows Voice to lock the screen when you say 'lock screen'.", adminActive, "Enable Screen Lock Admin") {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Voice only uses this permission to lock your screen upon voice command.")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        // Notification Listener (Replies)
        val listenerActive = ReplyNotificationListener.isConnected()
        addPermissionRow("Notification Listener", "Required to read incoming messages and perform direct replies via RemoteInput.", listenerActive, "Enable Notification Access") {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
