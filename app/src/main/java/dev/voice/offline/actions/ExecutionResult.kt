package dev.voice.offline.actions

import dev.voice.offline.ai.functioncalling.StructuredAction

/**
 * Result variants returned by action executors.
 * Strictly separates successful execution from missing permissions,
 * ambiguities, required authentications, and platform limitations.
 */
sealed interface ExecutionResult {
    val isAccepted: Boolean
    val message: String

    data class Success(
        override val message: String
    ) : ExecutionResult {
        override val isAccepted: Boolean = true
    }

    data class RequiresConfirmation(
        override val message: String,
        val action: StructuredAction
    ) : ExecutionResult {
        override val isAccepted: Boolean = false
    }

    data class RequiresAuthentication(
        override val message: String,
        val action: StructuredAction
    ) : ExecutionResult {
        override val isAccepted: Boolean = false
    }

    data class RequiresPermission(
        val permission: String,
        override val message: String
    ) : ExecutionResult {
        override val isAccepted: Boolean = false
    }

    data class Ambiguous(
        val candidates: List<String>,
        override val message: String
    ) : ExecutionResult {
        override val isAccepted: Boolean = false
    }

    data class Unsupported(
        override val message: String
    ) : ExecutionResult {
        override val isAccepted: Boolean = false
    }

    data class Failed(
        val error: String,
        override val message: String = error
    ) : ExecutionResult {
        override val isAccepted: Boolean = false
    }
}
