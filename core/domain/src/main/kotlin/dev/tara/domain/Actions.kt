package dev.tara.domain

@JvmInline value class PhoneNumber(val value: String)
@JvmInline value class AppId(val value: String)
enum class VolumeDirection { UP, DOWN }
sealed interface TaraAction {
    data object LockDevice : TaraAction
    data class DialNumber(val number: PhoneNumber) : TaraAction
    data class ComposeSms(val recipient: PhoneNumber, val body: String) : TaraAction
    data class OpenApp(val app: AppId) : TaraAction
    data class SetTorch(val enabled: Boolean) : TaraAction
    data class ChangeVolume(val direction: VolumeDirection) : TaraAction
    data class SetAlarm(val hour: Int, val minute: Int, val label: String = "Tara alarm") : TaraAction
    data class SetTimer(val seconds: Int, val label: String = "Tara timer") : TaraAction
}
enum class Capability { MICROPHONE, CAMERA, DEVICE_ADMIN, SECURE_LOCK }
enum class PermissionStatus { GRANTED, DENIED, PERMANENTLY_DENIED, REVOKED, UNAVAILABLE }
data class TargetOption(val id: String, val label: String)
sealed interface ActionResult {
    data class Completed(val message: String) : ActionResult
    data class ExternalHandoff(val message: String) : ActionResult
    data class PermissionRequired(val capability: Capability, val message: String) : ActionResult
    data class ConfirmationRequired(val action: TaraAction, val message: String) : ActionResult
    data class AmbiguousTarget(val message: String, val options: List<TargetOption>) : ActionResult
    data class Unsupported(val message: String) : ActionResult
    data object Cancelled : ActionResult
    data class Failure(val message: String) : ActionResult
}
fun ActionResult.message(): String = when (this) {
    is ActionResult.Completed -> message
    is ActionResult.ExternalHandoff -> message
    is ActionResult.PermissionRequired -> message
    is ActionResult.ConfirmationRequired -> message
    is ActionResult.AmbiguousTarget -> message
    is ActionResult.Unsupported -> message
    ActionResult.Cancelled -> "Cancelled."
    is ActionResult.Failure -> message
}
fun TaraAction.preview(): String = when (this) {
    TaraAction.LockDevice -> "Lock this device. Device administrator authorization is required."
    is TaraAction.DialNumber -> "Open dialer for ${number.value}. You place the call."
    is TaraAction.ComposeSms -> "To: ${recipient.value}\nMessage: $body\nOpen messaging composer; you send the message."
    is TaraAction.OpenApp -> "Open ${app.value}."
    is TaraAction.SetTorch -> "Turn flashlight ${if (enabled) "on" else "off"}."
    is TaraAction.ChangeVolume -> "Turn media volume ${direction.name.lowercase()}."
    is TaraAction.SetAlarm -> "Open clock to create an alarm at %02d:%02d in your local timezone.".format(hour, minute)
    is TaraAction.SetTimer -> "Open clock to create a timer for $seconds seconds."
}
