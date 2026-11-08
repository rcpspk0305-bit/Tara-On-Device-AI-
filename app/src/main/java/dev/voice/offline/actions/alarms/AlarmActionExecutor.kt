package dev.voice.offline.actions.alarms

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction

class AlarmActionExecutor(private val context: Context) {

    fun setAlarm(action: StructuredAction.SetAlarm): ExecutionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            val time = String.format("%02d:%02d", action.hour, action.minute)
            ExecutionResult.Success("Alarm set for $time (${action.label})")
        } catch (e: Exception) {
            ExecutionResult.Failed("Could not set alarm: ${e.message}")
        }
    }
}
