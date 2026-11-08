package dev.voice.offline.core.security

import dev.voice.offline.ai.functioncalling.StructuredAction
import java.util.concurrent.ConcurrentHashMap

enum class ActionRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Classifies actions into risk tiers with configurable overrides.
 * High risk actions enforce biometric authorization before execution.
 */
class SensitiveActionPolicy {

    private val overrides = ConcurrentHashMap<String, ActionRiskLevel>()

    fun setOverride(functionName: String, level: ActionRiskLevel) {
        overrides[functionName] = level
    }

    fun getRiskLevel(action: StructuredAction): ActionRiskLevel {
        val defaultLevel = when (action) {
            is StructuredAction.OpenCamera,
            is StructuredAction.TurnFlashlightOn,
            is StructuredAction.TurnFlashlightOff,
            is StructuredAction.TakeNotes,
            is StructuredAction.OpenApp -> ActionRiskLevel.LOW

            is StructuredAction.MakeCall,
            is StructuredAction.SetAlarm,
            is StructuredAction.ReadNotifications,
            is StructuredAction.AnswerCall,
            is StructuredAction.RejectCall -> ActionRiskLevel.MEDIUM

            is StructuredAction.LockScreen,
            is StructuredAction.TextMsg,
            is StructuredAction.ReplyToNotification -> ActionRiskLevel.HIGH
        }
        // Overrides may strengthen policy, but cannot bypass the built-in security floor.
        return maxOf(defaultLevel, overrides[action.functionName] ?: defaultLevel)
    }

    fun requiresBiometrics(action: StructuredAction): Boolean {
        return getRiskLevel(action) == ActionRiskLevel.HIGH
    }

    fun requiresUserInteraction(action: StructuredAction): Boolean = requiresBiometrics(action) || when (action) {
        is StructuredAction.MakeCall,
        is StructuredAction.OpenApp,
        is StructuredAction.SetAlarm,
        StructuredAction.OpenCamera -> true
        else -> false
    }
}
