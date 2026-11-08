package dev.voice.offline.actions.navigation

import android.content.Context
import android.content.Intent
import dev.voice.offline.actions.ExecutionResult

/**
 * Executes permitted system navigation actions using standard intents.
 * Avoids using AccessibilityService as an unrestricted AI automation engine.
 */
class NavigationActionExecutor(
    private val context: Context? = null,
    private val homeLauncher: () -> Unit = {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context?.startActivity(homeIntent)
    }
) {
    fun navigateHome(): ExecutionResult {
        return try {
            homeLauncher()
            ExecutionResult.Success("Navigated to Home screen")
        } catch (e: Exception) {
            ExecutionResult.Failed("Could not navigate home: ${e.message}")
        }
    }
}
