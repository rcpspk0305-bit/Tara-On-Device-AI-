package dev.tara.permissions

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.edit
import dev.tara.domain.Capability
import dev.tara.domain.PermissionManager
import dev.tara.domain.PermissionStatus

/** Stores permission prompt history only; each status query checks the current OS grant. */
class AndroidPermissionManager(context: Context) : PermissionManager {
    private val context = context.applicationContext
    private val history = this.context.getSharedPreferences("permission_history", Context.MODE_PRIVATE)
    private val inFlight = mutableSetOf<Capability>()
    private val rationale = mutableMapOf<Capability, Boolean>()
    private val admin = ComponentName(this.context.packageName, "dev.tara.androidactions.TaraAdminReceiver")

    override fun status(capability: Capability): PermissionStatus = when (capability) {
        Capability.MICROPHONE, Capability.CAMERA -> runtimeStatus(capability)
        Capability.DEVICE_ADMIN -> {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)) PermissionStatus.UNAVAILABLE
            else if (context.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(admin) == true) PermissionStatus.GRANTED
            else PermissionStatus.DENIED
        }
        Capability.SECURE_LOCK -> if (context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true)
            PermissionStatus.GRANTED else PermissionStatus.DENIED
    }

    private fun runtimeStatus(capability: Capability): PermissionStatus {
        val available = context.packageManager.hasSystemFeature(
            if (capability == Capability.MICROPHONE) PackageManager.FEATURE_MICROPHONE else PackageManager.FEATURE_CAMERA_ANY,
        )
        val permission = requireNotNull(runtimePermission(capability))
        val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        if (granted) history.edit {
            putBoolean("granted_${capability.name}", true)
            putBoolean("responded_${capability.name}", false)
        }
        return classifyPermission(
            available = available,
            granted = granted,
            previouslyGranted = history.getBoolean("granted_${capability.name}", false),
            responseRecorded = history.getBoolean("responded_${capability.name}", false) && capability !in inFlight,
            showRationale = rationale[capability],
        )
    }

    override fun refresh() {
        Capability.entries.forEach(::status)
    }

    fun refresh(activity: Activity) {
        listOf(Capability.MICROPHONE, Capability.CAMERA).forEach { capability ->
            rationale[capability] = activity.shouldShowRequestPermissionRationale(requireNotNull(runtimePermission(capability)))
        }
        refresh()
    }

    override fun recordRequest(capability: Capability) {
        if (runtimePermission(capability) != null) inFlight += capability
    }

    /** Call from the Activity Result callback, then refresh(activity). */
    fun recordResult(capability: Capability) {
        inFlight -= capability
        val permission = runtimePermission(capability)
        val denied = permission != null && context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        history.edit { putBoolean("responded_${capability.name}", denied) }
        refresh()
    }

    fun runtimePermission(capability: Capability): String? = when (capability) {
        Capability.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        Capability.CAMERA -> Manifest.permission.CAMERA
        Capability.DEVICE_ADMIN, Capability.SECURE_LOCK -> null
    }

    fun setupIntent(capability: Capability): Intent = when (capability) {
        Capability.DEVICE_ADMIN -> Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Tara uses only force-lock, after you explicitly confirm a lock command. You can deactivate it in Android settings.")
        Capability.SECURE_LOCK -> Intent(Settings.ACTION_SECURITY_SETTINGS)
        Capability.MICROPHONE, Capability.CAMERA -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
}

internal fun classifyPermission(
    available: Boolean,
    granted: Boolean,
    previouslyGranted: Boolean,
    responseRecorded: Boolean,
    showRationale: Boolean?,
): PermissionStatus = when {
    !available -> PermissionStatus.UNAVAILABLE
    granted -> PermissionStatus.GRANTED
    responseRecorded && showRationale == false -> PermissionStatus.PERMANENTLY_DENIED
    previouslyGranted -> PermissionStatus.REVOKED
    else -> PermissionStatus.DENIED
}
