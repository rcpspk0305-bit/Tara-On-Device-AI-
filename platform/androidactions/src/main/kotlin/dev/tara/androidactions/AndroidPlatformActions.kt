package dev.tara.androidactions

import android.Manifest
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import dev.tara.domain.ActionResult
import dev.tara.domain.ActionValidator
import dev.tara.domain.Capability
import dev.tara.domain.PlatformActions
import dev.tara.domain.TaraAction
import dev.tara.domain.VolumeDirection
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AndroidPlatformActions(context: Context) : PlatformActions, Closeable {
    private val application = context.applicationContext
    private val validator = ActionValidator()
    private val camera = application.getSystemService(CameraManager::class.java)
    private val torchLock = Mutex()
    private val unavailableCameras = ConcurrentHashMap.newKeySet<String>()
    private val torchStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private var observingTorch = false
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeUnavailable(cameraId: String) {
            unavailableCameras.add(cameraId)
            torchStates.value = torchStates.value - cameraId
        }

        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            unavailableCameras.remove(cameraId)
            torchStates.value = torchStates.value + (cameraId to enabled)
        }
    }

    override suspend fun lock(): ActionResult = withContext(Dispatchers.Main.immediate) {
        if (!application.packageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)) {
            return@withContext ActionResult.Unsupported("Device administrator locking is unavailable on this device.")
        }
        val policy = application.getSystemService(DevicePolicyManager::class.java)
            ?: return@withContext ActionResult.Unsupported("Device administrator service is unavailable.")
        val keyguard = application.getSystemService(KeyguardManager::class.java)
        try {
            if (!policy.isAdminActive(TaraAdminReceiver.component(application))) {
                return@withContext ActionResult.PermissionRequired(Capability.DEVICE_ADMIN, "Activate Tara device administrator access in Android settings, then retry.")
            }
            if (keyguard?.isDeviceSecure != true) {
                return@withContext ActionResult.PermissionRequired(Capability.SECURE_LOCK, "Set a secure PIN, password or pattern before using device lock.")
            }
            policy.lockNow()
            ActionResult.Completed("Device lock requested through Android device administration.")
        } catch (_: SecurityException) {
            ActionResult.PermissionRequired(Capability.DEVICE_ADMIN, "Device administrator access changed. Review Android settings and retry.")
        } catch (_: IllegalStateException) {
            ActionResult.Failure("Android could not lock this device. Review its device administrator settings.")
        }
    }

    override suspend fun dial(action: TaraAction.DialNumber): ActionResult = validate(action) ?: launch(
        ActionIntents.dial(action),
        "Dialer opened. Review the number and place the call yourself; Tara has not called anyone.",
        "No dialer is available on this device.",
    )

    override suspend fun sms(action: TaraAction.ComposeSms): ActionResult = validate(action) ?: launch(
        ActionIntents.sms(action),
        "Messaging composer opened. Review and send the message yourself; Tara has not sent it.",
        "No messaging composer is available on this device.",
    )

    override suspend fun open(action: TaraAction.OpenApp): ActionResult {
        validate(action)?.let { return it }
        return withContext(Dispatchers.Main.immediate) {
            try {
                if (launchableApps(application).none { it.id == action.app.value }) {
                    return@withContext ActionResult.Unsupported("This application is no longer available to launch.")
                }
                val intent = application.packageManager.getLaunchIntentForPackage(action.app.value)
                    ?: return@withContext ActionResult.Unsupported("This application has no accessible launcher.")
                launch(intent, "Application launch handed to Android.", "The application is unavailable or disabled.")
            } catch (_: SecurityException) {
                ActionResult.Failure("Android denied access to this application.")
            }
        }
    }

    override suspend fun torch(action: TaraAction.SetTorch): ActionResult {
        if (application.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return ActionResult.PermissionRequired(Capability.CAMERA, "Allow camera access to control the flashlight.")
        }
        if (!application.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) || camera == null) {
            return ActionResult.Unsupported("This device does not provide a flashlight.")
        }
        if (!torchLock.tryLock()) return ActionResult.Failure("A flashlight operation is already in progress.")
        var selectedCamera: String? = null
        try {
            if (!observingTorch) {
                camera.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
                observingTorch = true
            }
            val id = camera.cameraIdList
                .map { it to camera.getCameraCharacteristics(it) }
                .filter { (_, characteristics) -> characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                .sortedBy { (_, characteristics) -> if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) 0 else 1 }
                .firstOrNull()?.first
                ?: return ActionResult.Unsupported("No accessible camera has a flashlight.")
            selectedCamera = id
            if (id in unavailableCameras) {
                return ActionResult.Failure("The flashlight is unavailable while another camera operation is active. Close the camera and retry.")
            }
            if (torchStates.value[id] == action.enabled) {
                return ActionResult.Completed("Flashlight is already ${if (action.enabled) "on" else "off"}.")
            }
            camera.setTorchMode(id, action.enabled)
            val observed = withTimeoutOrNull(1500) { torchStates.first { it[id] == action.enabled } }
            if (observed == null) return ActionResult.Failure("Android accepted the flashlight request but did not confirm its state. Check the flashlight before retrying.")
            return ActionResult.Completed("Flashlight turned ${if (action.enabled) "on" else "off"}.")
        } catch (failure: CameraAccessException) {
            return when (failure.reason) {
                CameraAccessException.CAMERA_IN_USE, CameraAccessException.MAX_CAMERAS_IN_USE -> {
                    // Wait for the system callback to report availability before allowing a retry.
                    selectedCamera?.let(unavailableCameras::add)
                    ActionResult.Failure("The camera is in use. Close the conflicting camera operation before retrying.")
                }
                CameraAccessException.CAMERA_DISABLED -> ActionResult.Unsupported("Camera access is disabled by Android device policy.")
                else -> ActionResult.Failure("The flashlight is temporarily unavailable. Check Android camera settings.")
            }
        } catch (_: SecurityException) {
            return ActionResult.PermissionRequired(Capability.CAMERA, "Camera access was revoked. Review permissions and retry.")
        } catch (_: IllegalArgumentException) {
            return ActionResult.Unsupported("The flashlight camera is no longer available.")
        } finally {
            torchLock.unlock()
        }
    }

    override suspend fun volume(action: TaraAction.ChangeVolume): ActionResult = withContext(Dispatchers.Main.immediate) {
        val audio = application.getSystemService(AudioManager::class.java)
            ?: return@withContext ActionResult.Unsupported("Audio controls are unavailable.")
        if (audio.isVolumeFixed) return@withContext ActionResult.Unsupported("This device uses fixed volume. Use its external audio controls.")
        try {
            val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (action.direction == VolumeDirection.UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI,
            )
            val after = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (before == after) {
                ActionResult.Completed("Media volume is unchanged. It may be at its limit or controlled by the connected audio route.")
            } else {
                ActionResult.Completed("Media volume adjusted to $after of ${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)} on the current route.")
            }
        } catch (_: SecurityException) {
            ActionResult.Failure("Android blocked this volume change. Check Do Not Disturb and the connected audio route; Tara will not bypass them.")
        } catch (_: IllegalArgumentException) {
            ActionResult.Unsupported("The current audio route does not support this volume change.")
        }
    }

    override suspend fun alarm(action: TaraAction.SetAlarm): ActionResult = validate(action) ?: launch(
        ActionIntents.alarm(action),
        "Clock app opened with the local-time alarm request. Review it there; Tara cannot verify that the alarm was saved.",
        "No clock app supports alarm creation on this device.",
    )

    override suspend fun timer(action: TaraAction.SetTimer): ActionResult = validate(action) ?: launch(
        ActionIntents.timer(action),
        "Clock app opened with the timer request. Review it there; Tara cannot verify that the timer started.",
        "No clock app supports timer creation on this device.",
    )

    private fun validate(action: TaraAction): ActionResult.Failure? = validator.validate(action)?.let { ActionResult.Failure(it) }

    private suspend fun launch(intent: Intent, handoff: String, unavailable: String): ActionResult = withContext(Dispatchers.Main.immediate) {
        try {
            application.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionResult.ExternalHandoff(handoff)
        } catch (_: ActivityNotFoundException) {
            ActionResult.Unsupported(unavailable)
        } catch (_: SecurityException) {
            ActionResult.Failure("Android denied this handoff. Check the target application's availability and permissions.")
        }
    }

    override fun close() {
        if (observingTorch) {
            camera?.unregisterTorchCallback(torchCallback)
            observingTorch = false
        }
    }
}
