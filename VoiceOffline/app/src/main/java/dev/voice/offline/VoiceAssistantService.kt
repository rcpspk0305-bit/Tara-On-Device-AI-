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
import dev.voice.offline.service.AssistantSessionController
import dev.voice.offline.service.AssistantSessionResources
import dev.voice.offline.service.ListeningMode
import android.os.ResultReceiver
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
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
        const val ACTION_HANDS_FREE = "dev.voice.offline.HANDS_FREE"
        const val EXTRA_INVOCATION_ID = "invocation_id"
        const val EXTRA_COMPLETION = "completion"
        @Volatile var activeInvocationId: String? = null
            private set
        const val ACTION_STOP = "dev.voice.offline.STOP"

        private const val CHANNEL_SERVICE = "voice_listening"
        private const val CHANNEL_ACTION = "voice_actions"
        private const val NOTIFICATION_ID_SERVICE = 10
        private val invocationLock = Any()

        private val _stateFlow = MutableStateFlow<AssistantState>(AssistantState.IDLE)
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
    private var completion: ResultReceiver? = null
    private var ownedInvocationId: String? = null

    @Volatile private var audioRecordManager: AudioRecordManager? = null
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
            ACTION_START, ACTION_HANDS_FREE -> {
                @Suppress("DEPRECATION")
                val receiver = intent.getParcelableExtra<ResultReceiver>(EXTRA_COMPLETION)
                if (assistantJob?.isCompleted == false || isRunning.get()) {
                    receiver?.send(1, Bundle().apply { putString("message", "Voice is already handling an interaction") })
                    return START_NOT_STICKY
                }
                synchronized(invocationLock) {
                    completion = receiver
                    ownedInvocationId = intent.getStringExtra(EXTRA_INVOCATION_ID)
                    activeInvocationId = ownedInvocationId
                }
                if (activeInvocationId?.let {
                        !dev.voice.offline.assistant.AssistantInvocationRouter.invocations.mayStart(it)
                    } == true) {
                    completeInvocation("Interaction cancelled before activation")
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    _stateFlow.value = AssistantState.ERROR("Microphone permission denied")
                    completeInvocation("Microphone permission denied")
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
                    completeInvocation("Foreground microphone service unavailable")
                    stopSelf()
                    return START_NOT_STICKY
                }

                startAssistant(if (intent.action == ACTION_HANDS_FREE) ListeningMode.HANDS_FREE else ListeningMode.ON_DEMAND)
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startAssistant(mode: ListeningMode) {
        if (!isRunning.compareAndSet(false, true)) return
        _lastResultFlow.value = null
        val resources = object : AssistantSessionResources {
            override suspend fun prepare(mode: ListeningMode) {
                val modelDir = AssetModelInstaller.install(applicationContext)
                currentCoroutineContext().ensureActive()
                val model = Model(modelDir.absolutePath)
                voskModel = model
                currentCoroutineContext().ensureActive()
                if (mode == ListeningMode.HANDS_FREE) wakeWordDetector = VoskWakeWordDetector(model)
                speechRecognizer = VoskSpeechRecognizer(model)
                // Existing deterministic compatibility backend; no claim of real model inference.
                functionGemmaEngine = FunctionGemmaEngine(EmulatedLiteRtInferenceAdapter())
            }
            override suspend fun captureCommand(mode: ListeningMode, transition: (AssistantState) -> Unit): String =
                captureOneCommand(mode, transition)
            override fun stopCapture() { audioRecordManager?.stop() }
            override suspend fun processTranscript(text: String, transition: (AssistantState) -> Unit) {
                processTranscriptAction(text, transition)
            }
            override fun close() {
                runCatching { wakeWordDetector?.close() }
                runCatching { speechRecognizer?.close() }
                runCatching { functionGemmaEngine?.close() }
                runCatching { voskModel?.close() }
                audioRecordManager = null
                wakeWordDetector = null
                speechRecognizer = null
                functionGemmaEngine = null
                voskModel = null
            }
        }
        val controller = AssistantSessionController(resources) { state ->
            _stateFlow.value = state
            android.util.Log.i("VoiceLifecycle", "mode=$mode state=$state")
            if (state != AssistantState.IDLE && state != AssistantState.CLEANUP) {
                updateServiceNotification(when (state) {
                    AssistantState.WAKE_LISTENING -> "Hands-free: microphone active for Hey Voice"
                    AssistantState.LISTENING -> "Microphone active: say one command"
                    else -> "Processing your command"
                })
            }
        }
        assistantJob = serviceScope.launch(Dispatchers.IO) {
            try {
                controller.run(mode)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _lastResultFlow.value = "Assistant stopped: ${error.message}"
                notifyAlert(_lastResultFlow.value.orEmpty())
            } finally {
                isRunning.set(false)
                _stateFlow.value = AssistantState.IDLE
                completeInvocation(_lastResultFlow.value ?: "Interaction ended")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun captureOneCommand(mode: ListeningMode, transition: (AssistantState) -> Unit): String =
        coroutineScope {
            val chunks = Channel<ShortArray>(4, BufferOverflow.DROP_OLDEST)
            val ready = CompletableDeferred<Unit>()
            val recognizer = checkNotNull(speechRecognizer)
            var waitingForWake = mode == ListeningMode.HANDS_FREE
            recognizer.reset()
            wakeWordDetector?.reset()
            val manager = AudioRecordManager(getSystemService(AudioManager::class.java), onAudioChunk = { buffer, count ->
                chunks.trySend(buffer.copyOf(count))
            })
            audioRecordManager = manager
            val capture = launch(Dispatchers.IO) {
                manager.capture {
                    if (!waitingForWake) recognizer.startListening()
                    transition(if (waitingForWake) AssistantState.WAKE_LISTENING else AssistantState.LISTENING)
                    ready.complete(Unit)
                }
            }
            try {
                ready.await()
                var commandStarted = SystemClock.elapsedRealtime()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val chunk = withTimeoutOrNull(1500L) { chunks.receive() }
                        ?: error("Microphone stopped supplying audio")
                    if (waitingForWake) {
                        if (wakeWordDetector?.detect(chunk, chunk.size) is WakeWordResult.Detected) {
                            transition(AssistantState.WAKE_DETECTED)
                            playAckChime()
                            recognizer.startListening()
                            commandStarted = SystemClock.elapsedRealtime()
                            waitingForWake = false
                            transition(AssistantState.LISTENING)
                        }
                        continue
                    }
                    if (SystemClock.elapsedRealtime() - commandStarted >= 7000L) {
                        return@coroutineScope recognizer.finish()
                    }
                    when (val result = recognizer.offer(chunk, chunk.size)) {
                        is SpeechRecognitionResult.Final -> return@coroutineScope result.text
                        SpeechRecognitionResult.SilenceTimeout, SpeechRecognitionResult.MaxDurationExceeded ->
                            return@coroutineScope recognizer.finish()
                        else -> Unit
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                ""
            } finally {
                manager.stop()
                withContext(NonCancellable) { capture.cancelAndJoin() }
                chunks.close()
                audioRecordManager = null
            }
        }.also { if (it.isBlank()) _lastResultFlow.value = "No speech detected" }

    private suspend fun processTranscriptAction(transcript: String, transition: (AssistantState) -> Unit) {
        when (val result = checkNotNull(functionGemmaEngine).inferAction(transcript)) {
            is FunctionGemmaResult.Success -> {
                // The engine has already parsed and schema-validated this exact action.
                transition(AssistantState.VALIDATING)
                currentCoroutineContext().ensureActive()
                dispatchAction(result.action, transition)
            }
            is FunctionGemmaResult.Rejected -> _lastResultFlow.value = "Command not recognized: ${result.reason}"
            is FunctionGemmaResult.Error -> _lastResultFlow.value = "Command processing failed: ${result.error.message}"
        }
    }

    private fun dispatchAction(action: StructuredAction, transition: (AssistantState) -> Unit) {
        if (!isRunning.get()) return
        if (securityPolicy.requiresUserInteraction(action)) {
            transition(AssistantState.AUTHORIZING)
            _lastResultFlow.value = if (requestUserAuthorization(action)) "Action pending approval"
                else "Enable action approval notifications"
        } else {
            transition(AssistantState.EXECUTING)
            val outcome = actionRegistry.execute(action)
            _lastResultFlow.value = outcome.message
            if (outcome.isAccepted) {
                transition(AssistantState.CONFIRMING)
                playAckChime()
            } else notifyAlert(outcome.message)
        }
    }

    private fun completeInvocation(message: String) {
        val receiver = synchronized(invocationLock) {
            val receiver = completion
            completion = null
            if (activeInvocationId == ownedInvocationId) activeInvocationId = null
            ownedInvocationId = null
            receiver
        }
        receiver?.send(0, Bundle().apply { putString("message", message) })
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
        audioRecordManager?.stop()
    }

    override fun onDestroy() {
        stopAssistant()
        serviceScope.cancel()
        completeInvocation(_lastResultFlow.value ?: "Interaction cancelled")
        stopForeground(STOP_FOREGROUND_REMOVE)
        toneGenerator?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
