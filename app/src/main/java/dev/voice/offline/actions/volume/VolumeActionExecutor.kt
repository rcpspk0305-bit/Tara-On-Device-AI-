package dev.voice.offline.actions.volume

import android.content.Context
import android.media.AudioManager
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction
import kotlin.math.roundToInt

/**
 * Adjusts device media volume using standard Android AudioManager APIs.
 */
class VolumeActionExecutor(
    private val context: Context? = null,
    private val adjustStream: (stream: Int, direction: Int, flags: Int) -> Unit = { stream, dir, flags ->
        context?.getSystemService(AudioManager::class.java)?.adjustStreamVolume(stream, dir, flags)
    },
    private val setStream: (stream: Int, index: Int, flags: Int) -> Unit = { stream, index, flags ->
        context?.getSystemService(AudioManager::class.java)?.setStreamVolume(stream, index, flags)
    },
    private val maxVolume: (stream: Int) -> Int = { stream ->
        context?.getSystemService(AudioManager::class.java)?.getStreamMaxVolume(stream) ?: 15
    }
) {
    constructor(context: Context) : this(
        context = context,
        adjustStream = { stream, dir, flags ->
            context.getSystemService(AudioManager::class.java)?.adjustStreamVolume(stream, dir, flags)
        },
        setStream = { stream, index, flags ->
            context.getSystemService(AudioManager::class.java)?.setStreamVolume(stream, index, flags)
        },
        maxVolume = { stream ->
            context.getSystemService(AudioManager::class.java)?.getStreamMaxVolume(stream) ?: 15
        }
    )

    fun adjustVolume(action: StructuredAction.AdjustVolume): ExecutionResult {
        val stream = AudioManager.STREAM_MUSIC
        val flags = AudioManager.FLAG_SHOW_UI

        return try {
            when (action.direction.lowercase().trim()) {
                "up", "raise", "increase" -> {
                    adjustStream(stream, AudioManager.ADJUST_RAISE, flags)
                    ExecutionResult.Success("Volume increased")
                }
                "down", "lower", "decrease" -> {
                    adjustStream(stream, AudioManager.ADJUST_LOWER, flags)
                    ExecutionResult.Success("Volume decreased")
                }
                "mute" -> {
                    adjustStream(stream, AudioManager.ADJUST_MUTE, flags)
                    ExecutionResult.Success("Volume muted")
                }
                "unmute" -> {
                    adjustStream(stream, AudioManager.ADJUST_UNMUTE, flags)
                    ExecutionResult.Success("Volume unmuted")
                }
                "set" -> {
                    val percent = action.percent ?: 50
                    val max = maxVolume(stream)
                    val target = ((max * percent) / 100.0).roundToInt().coerceIn(0, max)
                    setStream(stream, target, flags)
                    ExecutionResult.Success("Volume set to $percent%")
                }
                else -> ExecutionResult.Failed("Unsupported volume direction: ${action.direction}")
            }
        } catch (e: Exception) {
            ExecutionResult.Failed("Failed to adjust volume: ${e.message}")
        }
    }
}

