package dev.voice.offline.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.voice.offline.R
import dev.voice.offline.VoiceAssistantService
import dev.voice.offline.feature.about.AboutView
import dev.voice.offline.feature.commands.CommandsView
import dev.voice.offline.feature.home.HomeView
import dev.voice.offline.feature.notes.NotesView
import dev.voice.offline.feature.onboarding.OnboardingView
import dev.voice.offline.feature.permissions.PermissionsView
import dev.voice.offline.feature.security.SecurityView
import dev.voice.offline.feature.settings.SettingsView
import dev.voice.offline.ui.theme.TaraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val assistantRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        settingsView.refreshAssistantStatus()
        if (!dev.voice.offline.assistant.AssistantRoleManager(this).isSelected) {
            Toast.makeText(this, "Assistant unchanged. You can select Tara in Android assistant settings.",
                Toast.LENGTH_LONG).show()
        }
    }

    private val activityScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var contentContainer: FrameLayout
    private lateinit var tabContainer: LinearLayout

    private lateinit var homeView: HomeView
    private lateinit var commandsView: CommandsView
    private lateinit var permissionsView: PermissionsView
    private lateinit var securityView: SecurityView
    private lateinit var notesView: NotesView
    private lateinit var settingsView: SettingsView
    private lateinit var aboutView: AboutView
    private lateinit var onboardingView: OnboardingView

    private var currentTabTag: String = TAB_HOME
    private val tabButtons = mutableMapOf<String, Pair<ImageView, TextView>>()
    private val tabItemContainers = mutableMapOf<String, LinearLayout>()

    companion object {
        const val TAB_HOME = "home"
        const val TAB_COMMANDS = "commands"
        const val TAB_PERMISSIONS = "permissions"
        const val TAB_SECURITY = "security"
        const val TAB_NOTES = "notes"
        const val TAB_SETTINGS = "settings"
        const val TAB_ABOUT = "about"
        const val TAB_ONBOARDING = "onboarding"

        private const val REQUEST_AUDIO_PERMISSION = 100
        private const val PREFS_NAME = "voice_app_prefs"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root layout with TARA deep obsidian background
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(TaraTheme.Background)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(rootLayout)

        // Content Area (fills remaining vertical space)
        contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        rootLayout.addView(contentContainer)

        // Floating Bottom Navigation Bar (TARA Glassmorphic Pill)
        val bottomNavWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(TaraTheme.Background)
            setPadding(
                TaraTheme.dpToPx(this@MainActivity, 12f),
                0,
                TaraTheme.dpToPx(this@MainActivity, 12f),
                TaraTheme.dpToPx(this@MainActivity, 12f)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(bottomNavWrapper)

        val navCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = TaraTheme.createCardDrawable(
                this@MainActivity,
                cornerRadiusDp = 28f,
                fillColor = Color.parseColor("#121622"),
                strokeColor = TaraTheme.BorderStroke,
                strokeWidthDp = 1f
            )
            setPadding(
                TaraTheme.dpToPx(this@MainActivity, 8f),
                TaraTheme.dpToPx(this@MainActivity, 6f),
                TaraTheme.dpToPx(this@MainActivity, 8f),
                TaraTheme.dpToPx(this@MainActivity, 6f)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        bottomNavWrapper.addView(navCard)

        val tabScrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        tabContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabScrollView.addView(tabContainer)
        navCard.addView(tabScrollView)

        // Initialize All 8 Views
        homeView = HomeView(
            context = this,
            onStartListening = { handleStartListening() },
            onStopListening = { handleStopListening() },
            onNavigate = { screenTag -> selectTab(screenTag) }
        )
        commandsView = CommandsView(this)
        permissionsView = PermissionsView(this) { permissions, requestCode ->
            requestPermissions(permissions, requestCode)
        }
        securityView = SecurityView(this)
        notesView = NotesView(this)
        settingsView = SettingsView(this,
            onEnableHandsFree = {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Enable software wake listening for Hey Tara?")
                    .setMessage(dev.voice.offline.assistant.AssistantCapabilityChecker(this).hotwordDescription +
                        " Stop it using Cancel or the microphone notification.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Enable") { _, _ ->
                        requestedHandsFree = true
                        handleStartListening()
                    }.show()
            },
            onChooseAssistant = {
                val roles = dev.voice.offline.assistant.AssistantRoleManager(this)
                try {
                    assistantRoleRequest.launch(roles.requestIntent())
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, "Assistant selection is unavailable on this device", Toast.LENGTH_LONG).show()
                }
            },
            onOpenAssistantSettings = {
                try {
                    startActivity(dev.voice.offline.assistant.AssistantRoleManager(this).settingsIntent())
                } catch (_: android.content.ActivityNotFoundException) {
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    } catch (_: android.content.ActivityNotFoundException) {
                        Toast.makeText(this, "Open Android Settings and choose Tara as default digital assistant",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
        aboutView = AboutView(this)
        onboardingView = OnboardingView(this) {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ONBOARDING_DONE, true)
                .apply()
            selectTab(TAB_HOME)
        }

        // Setup Navigation Tabs with TARA Vector Icons
        setupTab(TAB_HOME, "Tara", R.drawable.ic_tara_logo)
        setupTab(TAB_COMMANDS, "Commands", R.drawable.ic_nav_commands)
        setupTab(TAB_NOTES, "Notes", R.drawable.ic_nav_notes)
        setupTab(TAB_SECURITY, "Security", R.drawable.ic_nav_security)
        setupTab(TAB_PERMISSIONS, "Access", R.drawable.ic_nav_permissions)
        setupTab(TAB_SETTINGS, "Settings", R.drawable.ic_nav_settings)
        setupTab(TAB_ABOUT, "About", R.drawable.ic_nav_about)

        // Check if first run
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val onboardingDone = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        if (!onboardingDone) {
            selectTab(TAB_ONBOARDING)
        } else {
            selectTab(TAB_HOME)
        }

        // Observe Assistant State and Last Action Result
        activityScope.launch {
            VoiceAssistantService.stateFlow.collect { state ->
                homeView.updateState(state)
            }
        }
        activityScope.launch {
            VoiceAssistantService.lastResultFlow.collect { result ->
                homeView.updateLastResult(result)
            }
        }
    }

    private fun setupTab(tag: String, label: String, iconResId: Int) {
        val tabItem = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                TaraTheme.dpToPx(this@MainActivity, 14f),
                TaraTheme.dpToPx(this@MainActivity, 10f),
                TaraTheme.dpToPx(this@MainActivity, 14f),
                TaraTheme.dpToPx(this@MainActivity, 10f)
            )
            background = TaraTheme.createPillDrawable(
                this@MainActivity,
                fillColor = Color.TRANSPARENT,
                strokeColor = Color.TRANSPARENT,
                strokeWidthDp = 0f
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { selectTab(tag) }
        }

        val iconView = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@MainActivity, iconResId))
            if (tag == TAB_HOME) {
                clearColorFilter()
            } else {
                setColorFilter(TaraTheme.TextSecondary)
            }
            layoutParams = LinearLayout.LayoutParams(
                TaraTheme.dpToPx(this@MainActivity, 18f),
                TaraTheme.dpToPx(this@MainActivity, 18f)
            ).apply {
                setMargins(0, 0, TaraTheme.dpToPx(this@MainActivity, 6f), 0)
            }
        }
        tabItem.addView(iconView)

        val labelView = TextView(this).apply {
            text = label
            textSize = 12f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setTextColor(TaraTheme.TextSecondary)
            gravity = Gravity.CENTER
        }
        tabItem.addView(labelView)

        tabButtons[tag] = Pair(iconView, labelView)
        tabItemContainers[tag] = tabItem
        tabContainer.addView(tabItem)
    }

    private fun selectTab(tag: String) {
        currentTabTag = tag
        contentContainer.removeAllViews()

        val activeView: View = when (tag) {
            TAB_HOME -> homeView
            TAB_COMMANDS -> commandsView
            TAB_PERMISSIONS -> permissionsView
            TAB_SECURITY -> securityView
            TAB_NOTES -> {
                notesView.loadNotes()
                notesView
            }
            TAB_SETTINGS -> settingsView
            TAB_ABOUT -> aboutView
            TAB_ONBOARDING -> onboardingView
            else -> homeView
        }
        contentContainer.addView(activeView)

        // Update Tab Button Styles with Gemini Active Glow Pill
        tabButtons.forEach { (tabTag, pair) ->
            val (iconView, labelView) = pair
            val container = tabItemContainers[tabTag]

            if (tabTag == tag) {
                if (tabTag == TAB_HOME) {
                    iconView.clearColorFilter()
                } else {
                    iconView.setColorFilter(TaraTheme.TaraCyan)
                }
                labelView.setTextColor(TaraTheme.TextPrimary)
                labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                container?.background = TaraTheme.createPillDrawable(
                    this,
                    fillColor = TaraTheme.TabActivePill,
                    strokeColor = TaraTheme.TaraCyan,
                    strokeWidthDp = 1f
                )
            } else {
                if (tabTag == TAB_HOME) {
                    iconView.setColorFilter(TaraTheme.adjustAlpha(Color.WHITE, 0.45f))
                } else {
                    iconView.setColorFilter(TaraTheme.TextSecondary)
                }
                labelView.setTextColor(TaraTheme.TextSecondary)
                labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                container?.background = TaraTheme.createPillDrawable(
                    this,
                    fillColor = Color.TRANSPARENT,
                    strokeColor = Color.TRANSPARENT,
                    strokeWidthDp = 0f
                )
            }
        }
    }

    private var requestedHandsFree = false

    private fun handleStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION)
        } else {
            try {
                startForegroundService(
                    Intent(this, VoiceAssistantService::class.java)
                        .setAction(if (requestedHandsFree) VoiceAssistantService.ACTION_HANDS_FREE else VoiceAssistantService.ACTION_START)
                )
                requestedHandsFree = false
            } catch (e: Exception) {
                requestedHandsFree = false
                Toast.makeText(this, "Cannot start microphone service: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleStopListening() {
        requestedHandsFree = false
        stopService(Intent(this, VoiceAssistantService::class.java))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            handleStartListening()
        } else if (requestCode == REQUEST_AUDIO_PERMISSION) {
            requestedHandsFree = false
            Toast.makeText(this, "Microphone permission is needed to listen", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        settingsView.refreshAssistantStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
