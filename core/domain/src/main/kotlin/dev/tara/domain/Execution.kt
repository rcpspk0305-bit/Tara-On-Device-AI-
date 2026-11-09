package dev.tara.domain

fun interface ActionHandler { suspend fun handle(action: TaraAction): ActionResult }
interface PlatformActions {
    suspend fun lock(): ActionResult
    suspend fun dial(action: TaraAction.DialNumber): ActionResult
    suspend fun sms(action: TaraAction.ComposeSms): ActionResult
    suspend fun open(action: TaraAction.OpenApp): ActionResult
    suspend fun torch(action: TaraAction.SetTorch): ActionResult
    suspend fun volume(action: TaraAction.ChangeVolume): ActionResult
    suspend fun alarm(action: TaraAction.SetAlarm): ActionResult
    suspend fun timer(action: TaraAction.SetTimer): ActionResult
}
class ActionRegistry(private val platform: PlatformActions) : ActionHandler {
    override suspend fun handle(action: TaraAction): ActionResult = when (action) {
        TaraAction.LockDevice -> platform.lock()
        is TaraAction.DialNumber -> platform.dial(action)
        is TaraAction.ComposeSms -> platform.sms(action)
        is TaraAction.OpenApp -> platform.open(action)
        is TaraAction.SetTorch -> platform.torch(action)
        is TaraAction.ChangeVolume -> platform.volume(action)
        is TaraAction.SetAlarm -> platform.alarm(action)
        is TaraAction.SetTimer -> platform.timer(action)
    }
}
class ActionValidator {
    fun validate(action: TaraAction): String? = when (action) {
        TaraAction.LockDevice, is TaraAction.SetTorch, is TaraAction.ChangeVolume -> null
        is TaraAction.DialNumber -> validatePhone(action.number)
        is TaraAction.ComposeSms -> validatePhone(action.recipient)
            ?: if (action.body.isBlank() || action.body.length > 1600 || action.body.any { it == '\u0000' }) "Message must contain 1–1600 characters." else null
        is TaraAction.OpenApp -> if (!Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(action.app.value)) "Invalid application identifier." else null
        is TaraAction.SetAlarm -> if (action.hour !in 0..23 || action.minute !in 0..59 || action.label.length > 100) "Alarm must use a valid 24-hour time and short label." else null
        is TaraAction.SetTimer -> if (action.seconds !in 1..86400 || action.label.length > 100) "Timer duration must be between 1 second and 24 hours." else null
    }
    fun validatePhone(number: PhoneNumber): String? = if (!Regex("\\+?[0-9]{3,15}").matches(number.value))
        "Use 3–15 digits with an optional leading +. Control codes and extensions are not supported." else null
}
interface PermissionStatusRepository {
    fun status(capability: Capability): PermissionStatus
    fun refresh()
}
interface PermissionManager : PermissionStatusRepository {
    fun recordRequest(capability: Capability)
}
class ActionPolicy(private val permissions: PermissionStatusRepository) {
    fun check(action: TaraAction, confirmed: Boolean): ActionResult? {
        val required = when (action) {
            TaraAction.LockDevice -> Capability.DEVICE_ADMIN
            is TaraAction.SetTorch -> Capability.CAMERA
            else -> null
        }
        if (required != null && permissions.status(required) == PermissionStatus.UNAVAILABLE)
            return ActionResult.Unsupported("This device does not support ${required.name.lowercase().replace('_', ' ')}.")
        if (required != null && permissions.status(required) != PermissionStatus.GRANTED)
            return ActionResult.PermissionRequired(required, "${required.name.lowercase().replace('_', ' ')} access is required. Enable it explicitly and retry.")
        if (action == TaraAction.LockDevice && permissions.status(Capability.SECURE_LOCK) != PermissionStatus.GRANTED)
            return ActionResult.PermissionRequired(Capability.SECURE_LOCK, "No secure screen lock is configured. Set a PIN, password or pattern before locking.")
        if (!confirmed && (action is TaraAction.ComposeSms || action == TaraAction.LockDevice))
            return ActionResult.ConfirmationRequired(action, action.preview())
        return null
    }
}
class ActionExecutor(private val validator: ActionValidator, private val policy: ActionPolicy, private val registry: ActionHandler) {
    suspend fun execute(action: TaraAction, confirmed: Boolean = false): ActionResult {
        validator.validate(action)?.let { return ActionResult.Failure(it) }
        return try {
            policy.check(action, confirmed) ?: registry.handle(action)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ActionResult.Failure("Android could not complete this action. Review its access and availability, then retry.")
        }
    }
}
interface EntityResolver {
    suspend fun apps(name: String): List<TargetOption>
    suspend fun contacts(name: String): List<TargetOption>
}
