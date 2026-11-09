package dev.tara.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tara.audio.AudioState
import dev.tara.audio.SpeechController
import dev.tara.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val session: AssistantSessionManager,
    private val speech: SpeechController,
    private val permissions: PermissionStatusRepository,
) : ViewModel() {
    val state = session.state
    private val mutableCommand = MutableStateFlow("")
    val command = mutableCommand.asStateFlow()
    private val mutablePermissions = MutableStateFlow(emptyMap<Capability, PermissionStatus>())
    val statuses = mutablePermissions.asStateFlow()
    private var operation: Job? = null
    init {
        refreshStatuses()
        viewModelScope.launch {
            speech.state.collect { audio ->
                when (audio) {
                    AudioState.Idle -> Unit
                    AudioState.Listening -> Unit
                    AudioState.Transcribing -> session.transcribing()
                    is AudioState.Transcript -> { mutableCommand.value = audio.text; session.audioReady(audio.text) }
                    is AudioState.Diagnostic -> session.audioDiagnostic(audio.message)
                    is AudioState.Error -> session.audioError(audio.message)
                }
            }
        }
        viewModelScope.launch {
            state.collectLatest { current ->
                if (current.state == AssistantState.AwaitingConfirmation || current.options.isNotEmpty()) {
                    delay(30_000)
                    session.expire()
                }
            }
        }
    }
    fun edit(value: String) {
        if (state.value.state == AssistantState.AwaitingConfirmation || state.value.options.isNotEmpty()) session.cancel()
        mutableCommand.value = value.take(2000)
    }
    fun execute() {
        if (state.value.state in setOf(AssistantState.Listening, AssistantState.Transcribing, AssistantState.Executing, AssistantState.Resolving, AssistantState.Parsing)) return
        speech.cancel()
        operation = viewModelScope.launch { session.submit(command.value) }
    }
    fun listen() {
        if (permissions.status(Capability.MICROPHONE) != PermissionStatus.GRANTED) { session.microphonePermission(); return }
        if (session.listening()) speech.start()
    }
    fun stop() = speech.stop()
    fun cancel() { speech.cancel(); operation?.cancel(); session.cancel() }
    fun confirm() { operation = viewModelScope.launch { session.confirm() } }
    fun choose(id: String) { operation = viewModelScope.launch { session.choose(id) } }
    fun pickContact(number: String) {
        val normalized = number.replace(Regex("[ ()-]"), "")
        if (ActionValidator().validatePhone(PhoneNumber(normalized)) != null) { session.audioError("The selected telephone number is invalid or contains unsupported control codes."); return }
        val parsed = DeterministicCommandParser().parse(command.value)
        edit(if (parsed is ParseResult.ResolveContact && parsed.smsBody != null) "sms $normalized saying ${parsed.smsBody}" else "call $normalized")
    }
    fun refreshStatuses() { mutablePermissions.value = Capability.entries.associateWith(permissions::status) }
    fun interrupted() {
        speech.cancel()
        if (state.value.state in setOf(AssistantState.Listening, AssistantState.Transcribing, AssistantState.AwaitingConfirmation, AssistantState.Parsing, AssistantState.Resolving) || state.value.options.isNotEmpty()) cancel()
    }
    override fun onCleared() { operation?.cancel(); speech.close(); session.cancel() }
}
