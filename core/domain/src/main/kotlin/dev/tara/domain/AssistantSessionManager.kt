package dev.tara.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AssistantState { Idle, Listening, Transcribing, Parsing, Resolving, AwaitingPermission, AwaitingConfirmation, Executing, Completed, Cancelled, Error }
data class SessionState(
    val state: AssistantState = AssistantState.Idle,
    val transcript: String = "",
    val action: TaraAction? = null,
    val result: ActionResult? = null,
    val options: List<TargetOption> = emptyList(),
    val message: String = "Ready when you are.",
)
class AssistantSessionManager(
    private val parser: CommandParser,
    private val resolver: EntityResolver,
    private val executor: ActionExecutor,
    private val clockMillis: () -> Long,
) {
    private val mutable = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutable.asStateFlow()
    private var generation = 0L
    private var pendingResolution: ParseResult? = null
    private var expiresAt = 0L
    private val busy get() = state.value.state in setOf(AssistantState.Listening, AssistantState.Transcribing, AssistantState.Parsing, AssistantState.Resolving, AssistantState.Executing)

    fun listening(): Boolean {
        if (busy) return false
        generation++
        pendingResolution = null
        expiresAt = 0
        mutable.value = SessionState(AssistantState.Listening, message = "Listening. Tap Stop to finish or Cancel to discard.")
        return true
    }
    fun transcribing() { if (state.value.state == AssistantState.Listening) mutable.value = state.value.copy(state = AssistantState.Transcribing, message = "Finishing local recognition…") }
    fun audioReady(transcript: String) { mutable.value = SessionState(transcript = transcript, message = "Review the transcript, then execute.") }
    fun audioDiagnostic(message: String) { mutable.value = SessionState(message = message) }
    fun audioError(message: String) { cancel(); mutable.value = SessionState(AssistantState.Error, message = message) }
    fun microphonePermission() { mutable.value = SessionState(AssistantState.AwaitingPermission, result = ActionResult.PermissionRequired(Capability.MICROPHONE, "Allow microphone access to start listening."), message = "Microphone access is required.") }
    fun cancel() {
        generation++
        pendingResolution = null
        expiresAt = 0
        mutable.value = SessionState(AssistantState.Cancelled, result = ActionResult.Cancelled, message = "Cancelled.")
    }
    fun expire() {
        val pending = state.value.state == AssistantState.AwaitingConfirmation || state.value.options.isNotEmpty()
        if (pending && expiresAt > 0 && clockMillis() >= expiresAt) cancel()
    }
    suspend fun submit(text: String) {
        if (busy) return
        val token = ++generation
        pendingResolution = null
        mutable.value = SessionState(AssistantState.Parsing, transcript = state.value.transcript, message = "Parsing locally…")
        try {
            when (val parsed = parser.parse(text)) {
                is ParseResult.Ready -> runAction(parsed.action, token)
                is ParseResult.Clarification -> mutable.value = state.value.copy(state = AssistantState.Idle, message = parsed.message)
                is ParseResult.ResolveApp -> resolve(parsed, token)
                is ParseResult.ResolveContact -> resolve(parsed, token)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (generation == token) mutable.value = SessionState(AssistantState.Error, message = "Unable to process this command. Please retry.") }
    }
    private suspend fun resolve(parsed: ParseResult, token: Long) {
        mutable.value = state.value.copy(state = AssistantState.Resolving, message = "Resolving target locally…")
        val options = when (parsed) {
            is ParseResult.ResolveApp -> resolver.apps(parsed.name)
            is ParseResult.ResolveContact -> resolver.contacts(parsed.name)
            else -> emptyList()
        }.distinctBy { it.id }
        if (token != generation) return
        if (options.isEmpty()) { mutable.value = state.value.copy(state = AssistantState.Error, message = if (parsed is ParseResult.ResolveApp) "No matching launchable application." else "Choose a contact using the contact picker, or enter a telephone number."); return }
        pendingResolution = parsed
        expiresAt = clockMillis() + 30_000
        mutable.value = state.value.copy(state = AssistantState.Idle, options = options, result = ActionResult.AmbiguousTarget("Choose the exact target.", options), message = "Choose the exact target.")
        if (options.size == 1) choose(options.first().id)
    }
    suspend fun choose(id: String) {
        expire()
        val option = state.value.options.singleOrNull { it.id == id } ?: return
        val action = when (val pending = pendingResolution) {
            is ParseResult.ResolveApp -> TaraAction.OpenApp(AppId(option.id))
            is ParseResult.ResolveContact -> if (pending.smsBody == null) TaraAction.DialNumber(PhoneNumber(option.id)) else TaraAction.ComposeSms(PhoneNumber(option.id), pending.smsBody)
            else -> return
        }
        pendingResolution = null
        runAction(action, generation)
    }
    suspend fun confirm() {
        expire()
        if (state.value.state != AssistantState.AwaitingConfirmation) return
        val action = state.value.action ?: return
        runAction(action, generation, true)
    }
    private suspend fun runAction(action: TaraAction, token: Long, confirmed: Boolean = false) {
        if (token != generation) return
        // Drop pending UI and confirmation before a lock or external intent pauses the activity.
        expiresAt = 0
        mutable.value = state.value.copy(state = AssistantState.Executing, action = action, options = emptyList(), result = null, message = "Executing…")
        val result = executor.execute(action, confirmed)
        if (token != generation) return
        val next = when (result) {
            is ActionResult.ConfirmationRequired -> AssistantState.AwaitingConfirmation
            is ActionResult.PermissionRequired -> AssistantState.AwaitingPermission
            is ActionResult.Completed, is ActionResult.ExternalHandoff -> AssistantState.Completed
            ActionResult.Cancelled -> AssistantState.Cancelled
            is ActionResult.AmbiguousTarget -> AssistantState.Idle
            is ActionResult.Unsupported, is ActionResult.Failure -> AssistantState.Error
        }
        if (next == AssistantState.AwaitingConfirmation) expiresAt = clockMillis() + 30_000
        mutable.value = state.value.copy(state = next, result = result, message = result.message())
    }
}
