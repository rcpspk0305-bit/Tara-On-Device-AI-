package dev.tara.androidactions

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.admin.DevicePolicyManager

class TaraAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(context: Context) = ComponentName(context, TaraAdminReceiver::class.java)

        fun activationIntent(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Allow Tara to lock this device only when you explicitly request and confirm it. No other administrator policy is requested.",
            )
    }
}
