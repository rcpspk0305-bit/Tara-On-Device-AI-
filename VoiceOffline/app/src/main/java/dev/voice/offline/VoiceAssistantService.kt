package dev.voice.offline

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import dev.voice.offline.actions.ActionExecutorRegistry
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction
import dev.voice.offline.ai.functiongemma.EmulatedLiteRtInferenceAdapter
import dev.voice.offline.ai.functiongemma.FunctionGemmaEngine
import dev.voice.offline.ai.functiongemma.FunctionGemmaResult
import dev.voice.offline.ai.stt.OfflineSpeechRecognizer
import dev.voice.offline.ai.stt.SpeechRecognitionResult
import dev.voice.offline.ai.stt.VoskSpeechRecognizer
import dev.voice.offline.ai.wakeword.VoskWakeWordDetector
import dev.voice.offline.ai.wakeword.WakeWordDetector
import dev.voice.offline.ai.wakeword.WakeWordResult
import dev.voice.offline.audio.AssetModelInstaller
import dev.voice.offline.core.audio.AudioRecordManager
import dev.voice.offline.security.AuthorizationActivity
import dev.voice.offline.security.PendingCommandStore
import dev.voice.offline.security.SensitiveActionPolicy
import dev.voice.offline.service.AssistantState
import dev.voice.offline.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vosk.Model
import java.util.concurrent.atomic.AtomicBoolean

class VoiceAssistantService : Service() {

    companion object {
        const val ACTION_START = "dev.voice.offline.START"
        const val ACTION_STOP = "dev.voice.offline.STOP"

        private const val CHANNEL_SERVICE = "voice_listening"
        private const val CHANNEL_ACTION = "voice_actions"
        private const val NOTIFICATION_ID_SERVICE = 10

        private val _stateFlow = MutableStateFlow<AssistantState>(AssistantState.STOPPED)
        val stateFlow: StateFlow<AssistantState> = _stateFlow.asStateFlow()

        private val _lastResultFlow = MutableStateFlow<String?>(null)
        val lastResultFlow: StateFlow<String?> = _lastResultFlow.asStateFlow()

        internal fun reportActionResult(message: String) {
            _lastResultFlow.value = message
        }
    }

    private val isRunning = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var assistantJob: Job? = null
    private val audioChunks = Channel<ShortArray>(4, BufferOverflow.DROP_OLDEST)

    private var audioRecordManager: AudioRecordManager? = null
    private var voskModel: Model? = null
    private var wakeWordDetector: WakeWordDetector? = null
    private var speechRecognizer: OfflineSpeechRecognizer? = null
    private var functionGemmaEngine: FunctionGemmaEngine? = null

    private lateinit var actionRegistry: ActionExecutorRegistry
    private lateinit var securityPolicy: SensitiveActionPolicy
    private var toneGenerator: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        setupNotificationChannels()
        actionRegistry = ActionExecutorRegistry(applicationContext)
        securityPolicy = SensitiveActionPolicy()
        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 30)
        } catch (_: Exception) {
            null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAssistant()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    _stateFlow.value = AssistantState.ERROR("Microphone permission denied")
                    stopSelf()
                    return START_NOT_STICKY
                }

                try {
                    val notif = buildServiceNotification("Voice is initializing...")
                    if (Build.VERSION.SDK_INT >= 29) {
                        startForeground(
                            NOTIFICATION_ID_SERVICE,
                            notif,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID_SERVICE, notif)
                    }
                } catch (e: Exception) {
                    _stateFlow.value = AssistantState.ERROR("Could not start foreground service: ${e.message}")
                    stopSelf()
                    return START_NOT_STICKY
                }

                startAssistant()
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startAssistant() {
        if (assistantJob?.isCompleted == false) return
        if (!isRunning.compareAndSet(false, true)) return

        _stateFlow.value = AssistantState.INITIALIZING
        assistantJob = serviceScope.launch(Dispatchers.IO) {
            try {
                // Initialize offline models
                val modelDir = AssetModelInstaller.install(applicationContext)
                val model = Model(modelDir.absolutePath)
                voskModel = model

                wakeWordDetector = VoskWakeWordDetector(model)
                speechRecognizer = VoskSpeechRecognizer(model)

                // Initialize FunctionGemma 270M adapter
                val adapter = EmulatedLiteRtInferenceAdapter()
                functionGemmaEngine = FunctionGemmaEngine(adapter)
                currentCoroutineContext().ensureActive()

                audioRecordManager = AudioRecordManager(
                    sampleRate = 16000,
                    onAudioChunk = { buffer, count ->
                        if (isRunning.get()) audioChunks.trySend(buffer.copyOf(count))
                    },
                    onError = { error ->
                        if (isRunning.get()) failAssistant(error)
                    }
                )

                _stateFlow.value = AssistantState.LISTENING
                updateServiceNotification("Listening for ‘Hey Voice’...")
                audioRecordManager?.start(serviceScope)
                // One coroutine owns the recognizers, inference, and cleanup. Audio captured
                // during inference is bounded, and native handles cannot close during decoding.
                for (chunk in audioChunks) {
                    currentCoroutineContext().ensureActive()
                    if (!isRunning.get()) break
                    handleAudioChunk(chunk, chunk.size)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                failAssistant("Assistant failed: ${e.message}")
            } finally {
                isRunning.set(false)
                audioRecordManager?.stop()
                runCatching { wakeWordDetector?.close() }
                runCatching { speechRecognizer?.close() }
                runCatching { functionGemmaEngine?.close() }
                runCatching { voskModel?.close() }
                audioRecordManager = null
                wakeWordDetector = null
                speechRecognizer = null
                functionGemmaEngine = null
                voskModel = null
                if (_stateFlow.value !is AssistantState.ERROR) _stateFlow.value = AssistantState.STOPPED
                stopSelf()
            }
        }
    }

    private suspend fun handleAudioChunk(buffer: ShortArray, count: Int) {
        when (_stateFlow.value) {
            is AssistantState.LISTENING -> {
                val detector = wakeWordDetector ?: return
                val result = detector.detect(buffer, count)
                if (result is WakeWordResult.Detected) {
                    _stateFlow.value = AssistantState.WAKE_DETECTED
                    playAckChime()
                    _stateFlow.value = AssistantState.RECORDING_COMMAND
                    speechRecognizer?.startListening()
                    updateServiceNotification("Listening to your command...")
                }
            }

            is AssistantState.RECORDING_COMMAND,
            is AssistantState.TRANSCRIBING -> {
                val recognizer = speechRecognizer ?: return
                val result = recognizer.offer(buffer, count)

                when (result) {
                    is SpeechRecognitionResult.Partial -> {
                        _stateFlow.value = AssistantState.TRANSCRIBING
                    }
                    is SpeechRecognitionResult.Final -> {
                        processTranscript(result.text)
                    }
                    SpeechRecognitionResult.SilenceTimeout,
                    SpeechRecognitionResult.MaxDurationExceeded -> {
                        val text = recognizer.finish()
                        if (text.isNotBlank()) {
                            processTranscript(text)
                        } else {
                            returnToListening("No speech detected")
                        }
                    }
                    SpeechRecognitionResult.Listening -> Unit
                }
            }

            else -> Unit
        }
    }

    private suspend fun processTranscript(transcript: String) {
        _stateFlow.value = AssistantState.INFERENCING
        updateServiceNotification("Understanding: \"$transcript\"...")

        val engine = functionGemmaEngine
        if (engine == null) {
            returnToListening("Engine unavailable")
            return
        }

        when (val inferResult = engine.inferAction(transcript)) {
            is FunctionGemmaResult.Success -> {
                dispatchAction(inferResult.action)
            }
            is FunctionGemmaResult.Rejected -> {
                returnToListening("Command not recognized: ${inferResult.reason}")
            }
            is FunctionGemmaResult.Error -> {
                returnToListening("Error during inference: ${inferResult.error.message}")
            }
        }
    }

    private fun dispatchAction(action: StructuredAction) {
        if (!isRunning.get()) return

        if (securityPolicy.requiresUserInteraction(action)) {
            _stateFlow.value = AssistantState.AUTHORIZING
            returnToListening(if (requestUserAuthorization(action)) "Action pending approval" else "Enable action approval notifications")
        } else {
            _stateFlow.value = AssistantState.EXECUTING
            val outcome = actionRegistry.execute(action)
            _lastResultFlow.value = outcome.message

            if (outcome.isAccepted) {
                _stateFlow.value = AssistantState.CONFIRMING
                playAckChime()
            } else {
                notifyAlert(outcome.message)
            }
            returnToListening(outcome.message)
        }
    }

    private fun returnToListening(statusMessage: String) {
        if (!isRunning.get()) return
        while (audioChunks.tryReceive().isSuccess) { /* Discard audio captured during the action. */ }
        speechRecognizer?.reset()
        wakeWordDetector?.reset()
        _stateFlow.value = AssistantState.LISTENING
        updateServiceNotification("Listening for ‘Hey Voice’ ($statusMessage)")
    }

    private fun requestUserAuthorization(action: StructuredAction): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(CHANNEL_ACTION)?.importance == NotificationManager.IMPORTANCE_NONE) {
            _lastResultFlow.value = "Enable notifications and the Action Approvals channel to approve this command"
            return false
        }
        val token = PendingCommandStore.default.enqueue(action)
        val tapIntent = Intent(this, AuthorizationActivity::class.java).apply {
            putExtra("token", token)
            data = Uri.parse("voiceapproval://local/$token")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            token.hashCode(),
            tapIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ACTION)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Voice Action Approval")
            .setContentText("Tap to review and authorize ${action.functionName}. Expires in 30s.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000L)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        val id = 100 + (token.hashCode() and Int.MAX_VALUE) % 10_000_000
        return try {
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            PendingCommandStore.default.take(token)
            false
        }
    }

    private fun playAckChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 80)
        } catch (_: Exception) {}
    }

    private fun setupNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Microphone Listening", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Active on-device microphone monitoring for wake phrase"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTION, "Action Approvals", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Explicit authorization for sensitive voice actions"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    private fun buildServiceNotification(contentText: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = PendingIntent.getService(
            this,
            2,
            Intent(this, VoiceAssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Voice Assistant (Offline)")
            .setContentText(contentText)
            .setContentIntent(openApp)
            .addAction(Notification.Action.Builder(null, "Stop", stopAction).build())
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateServiceNotification(contentText: String) {
        if (!isRunning.get()) return
        val notif = buildServiceNotification(contentText)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID_SERVICE, notif)
    }

    private fun notifyAlert(message: String) {
        val note = Notification.Builder(this, CHANNEL_ACTION)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Voice Alert")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(21, note) }
    }

    private fun stopAssistant() {
        isRunning.set(false)
        assistantJob?.cancel()
        _stateFlow.value = AssistantState.STOPPED
    }

    private fun failAssistant(message: String) {
        _lastResultFlow.value = message
        _stateFlow.value = AssistantState.ERROR(message)
        isRunning.set(false)
        notifyAlert(message)
        assistantJob?.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        isRunning.set(false)
        assistantJob?.cancel()
        serviceScope.cancel()
        toneGenerator?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
