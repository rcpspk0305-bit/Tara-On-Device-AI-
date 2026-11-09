package dev.tara.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.tara.assistant.AssistantScreen
import dev.tara.designsystem.TaraTheme
import dev.tara.domain.*
import dev.tara.onboarding.PermissionsOnboardingScreen
import dev.tara.onboarding.WelcomeScreen
import dev.tara.permissions.AndroidPermissionManager
import dev.tara.privacy.PrivacyScreen
import dev.tara.settings.SettingsScreen
import dev.tara.settingsdata.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var permissions: AndroidPermissionManager
    @Inject lateinit var settings: SettingsRepository
    private val model: AssistantViewModel by viewModels()
    private var pendingPermission: Capability? = null
    private var notice by mutableStateOf<String?>(null)
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionResult(Capability.MICROPHONE) }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionResult(Capability.CAMERA) }
    private fun permissionResult(capability: Capability) {
        permissions.recordResult(capability)
        pendingPermission = null
        permissions.refresh(this)
        model.refreshStatuses()
    }
    private val setup = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissions.refresh(this)
        model.refreshStatuses()
    }
    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val number = try {
                    contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                } catch (_: Exception) { null }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (number != null) model.pickContact(number) else notice = "Unable to read the chosen number. Type the telephone number instead."
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val persisted by settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = persisted
            val dark = when (current?.theme ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            TaraTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                if (current == null) {
                    Surface(Modifier.fillMaxSize()) { Box(contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() } }
                } else {
                    var onboardingStep by remember { mutableIntStateOf(0) }
                    var page by remember { mutableIntStateOf(0) }
                    val state by model.state.collectAsStateWithLifecycle()
                    val command by model.command.collectAsStateWithLifecycle()
                    val statuses by model.statuses.collectAsStateWithLifecycle()
                    BackHandler(enabled = page != 0 || state.state == AssistantState.AwaitingConfirmation || state.options.isNotEmpty()) {
                        model.cancel()
                        page = 0
                    }
                    if (!current.onboardingComplete) {
                        if (onboardingStep == 0) Box(Modifier.safeDrawingPadding()) { WelcomeScreen { onboardingStep = 1 } }
                        else Box(Modifier.safeDrawingPadding()) { PermissionsOnboardingScreen(
                            onContinue = { lifecycleScope.launch { settings.completeOnboarding() } },
                            onPermission = ::requestCapability,
                        ) }
                    } else {
                        Scaffold(bottomBar = {
                            NavigationBar {
                                listOf("Assistant", "Privacy", "Settings").forEachIndexed { index, label ->
                                    NavigationBarItem(selected = page == index, onClick = { model.interrupted(); page = index },
                                        icon = { Text(listOf("T", "P", "S")[index]) }, label = { Text(label) })
                                }
                            }
                        }) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                                when (page) {
                                    0 -> AssistantScreen(state, command, model::edit, model::execute, model::listen,
                                        model::stop, model::cancel, model::confirm, model::choose, ::requestCapability, ::pickContact, BuildConfig.DEBUG)
                                    1 -> PrivacyScreen(statuses, ::requestCapability)
                                    2 -> SettingsScreen(current, { theme -> lifecycleScope.launch { settings.setTheme(theme) } },
                                        { lifecycleScope.launch { model.cancel(); onboardingStep = 0; page = 0; settings.resetOnboarding() } })
                                }
                            }
                        }
                    }
                }
                }
                notice?.let { text -> AlertDialog(onDismissRequest = { notice = null }, title = { Text("Action unavailable") }, text = { Text(text) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } }) }
            }
        }
    }
    override fun onResume() { super.onResume(); permissions.refresh(this); model.refreshStatuses() }
    override fun onStop() { model.interrupted(); super.onStop() }
    private fun requestCapability(capability: Capability) {
        model.interrupted()
        permissions.refresh(this)
        val status = permissions.status(capability)
        if (status == PermissionStatus.GRANTED) {
            try {
                val intent = if (capability == Capability.DEVICE_ADMIN || capability == Capability.SECURE_LOCK)
                    Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS) else permissions.setupIntent(capability)
                setup.launch(intent)
            } catch (_: ActivityNotFoundException) { notice = "Android has no settings screen for this capability." }
            catch (_: SecurityException) { notice = "Android denied access to this settings screen." }
            return
        }
        if (status == PermissionStatus.UNAVAILABLE) { notice = "This capability is unavailable on this device."; return }
        val runtime = permissions.runtimePermission(capability)
        if (runtime != null && status != PermissionStatus.PERMANENTLY_DENIED) {
            if (pendingPermission != null) return
            pendingPermission = capability
            permissions.recordRequest(capability)
            when (capability) {
                Capability.MICROPHONE -> microphonePermission.launch(runtime)
                Capability.CAMERA -> cameraPermission.launch(runtime)
                Capability.DEVICE_ADMIN, Capability.SECURE_LOCK -> Unit
            }
        } else {
            try { setup.launch(permissions.setupIntent(capability)) }
            catch (_: ActivityNotFoundException) { notice = "Android has no settings screen for this capability." }
            catch (_: SecurityException) { notice = "Android denied access to this settings screen." }
        }
    }
    private fun pickContact() {
        model.interrupted()
        try { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
        catch (_: ActivityNotFoundException) { notice = "No contacts picker is installed. Type a telephone number instead." }
        catch (_: SecurityException) { notice = "Contact selection is restricted on this device. Type a number instead." }
    }
}
