package dev.voice.offline.actions.apps

import android.content.Context
import android.content.pm.PackageManager
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction

class AppActionExecutor(private val context: Context) {

    fun openApp(action: StructuredAction.OpenApp): ExecutionResult {
        val target = action.packageName.trim()
        val pm = context.packageManager

        // 1. Direct package match
        val directIntent = pm.getLaunchIntentForPackage(target)
        if (directIntent != null) {
            directIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(directIntent)
            return ExecutionResult.Success("Launching $target")
        }

        // 2. Search installed apps by label
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (appInfo in installed) {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.equals(target, ignoreCase = true) || label.contains(target, ignoreCase = true)) {
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ExecutionResult.Success("Opening $label")
                }
            }
        }

        return ExecutionResult.Failed("Application '$target' is not installed or cannot be launched")
    }
}
