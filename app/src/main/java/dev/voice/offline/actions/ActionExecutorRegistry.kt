package dev.voice.offline.actions

import android.content.Context
import dev.voice.offline.actions.alarms.AlarmActionExecutor
import dev.voice.offline.actions.apps.AppActionExecutor
import dev.voice.offline.actions.calls.CallActionExecutor
import dev.voice.offline.actions.camera.CameraActionExecutor
import dev.voice.offline.actions.flashlight.FlashlightActionExecutor
import dev.voice.offline.actions.messaging.MessagingActionExecutor
import dev.voice.offline.actions.navigation.NavigationActionExecutor
import dev.voice.offline.actions.notes.NoteActionExecutor
import dev.voice.offline.actions.notifications.NotificationActionExecutor
import dev.voice.offline.actions.screen.ScreenActionExecutor
import dev.voice.offline.actions.settings.SettingsActionExecutor
import dev.voice.offline.actions.volume.VolumeActionExecutor
import dev.voice.offline.ai.functioncalling.StructuredAction

/**
 * Central registry and dispatcher for all native Android actions.
 * Decouples model inference from platform execution and permission handling.
 */
class ActionExecutorRegistry(private val context: Context) {

    private val callExecutor = CallActionExecutor(context)
    private val messagingExecutor = MessagingActionExecutor(context)
    private val notificationExecutor = NotificationActionExecutor(context)
    private val cameraExecutor = CameraActionExecutor(context)
    private val screenExecutor = ScreenActionExecutor(context)
    private val flashlightExecutor = FlashlightActionExecutor(context)
    private val alarmExecutor = AlarmActionExecutor(context)
    private val noteExecutor = NoteActionExecutor(context)
    private val appExecutor = AppActionExecutor(context)
    private val settingsExecutor = SettingsActionExecutor(context)
    private val volumeExecutor = VolumeActionExecutor(context)
    private val navigationExecutor = NavigationActionExecutor(context)

    fun execute(action: StructuredAction): ExecutionResult {
        return try { when (action) {
            is StructuredAction.MakeCall -> callExecutor.makeCall(action)
            StructuredAction.AnswerCall -> callExecutor.answerCall()
            StructuredAction.RejectCall -> callExecutor.rejectCall()
            StructuredAction.OpenCamera -> cameraExecutor.openCamera()
            StructuredAction.LockScreen -> screenExecutor.lockScreen()
            is StructuredAction.TakeNotes -> noteExecutor.takeNotes(action)
            is StructuredAction.OpenApp -> appExecutor.openApp(action)
            is StructuredAction.SetAlarm -> alarmExecutor.setAlarm(action)
            StructuredAction.TurnFlashlightOn -> flashlightExecutor.setTorchMode(true)
            StructuredAction.TurnFlashlightOff -> flashlightExecutor.setTorchMode(false)
            is StructuredAction.TextMsg -> messagingExecutor.sendText(action)
            StructuredAction.ReadNotifications -> notificationExecutor.readNotifications()
            is StructuredAction.ReplyToNotification -> notificationExecutor.replyToNotification(action)
            is StructuredAction.OpenSettings -> settingsExecutor.openSettings(action)
            is StructuredAction.AdjustVolume -> volumeExecutor.adjustVolume(action)
            StructuredAction.NavigateHome -> navigationExecutor.navigateHome()
        } } catch (_: SecurityException) {
            ExecutionResult.Failed("Permission or device policy denied")
        } catch (error: Exception) {
            ExecutionResult.Failed("Action could not be completed: ${error.message}")
        }
    }
}
