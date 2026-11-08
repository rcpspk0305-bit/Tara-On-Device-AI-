package dev.voice.offline.actions.volume

import android.media.AudioManager
import dev.voice.offline.actions.ExecutionResult
import dev.voice.offline.ai.functioncalling.StructuredAction
import org.junit.Assert.*
import org.junit.Test

class VolumeActionExecutorTest {

    @Test
    fun testVolumeUpDispatchesRaise() {
        var lastStream = -1
        var lastDir = -999
        val executor = VolumeActionExecutor(
            adjustStream = { stream, dir, _ ->
                lastStream = stream
                lastDir = dir
            }
        )

        val result = executor.adjustVolume(StructuredAction.AdjustVolume("up"))
        assertTrue(result is ExecutionResult.Success)
        assertEquals(AudioManager.STREAM_MUSIC, lastStream)
        assertEquals(AudioManager.ADJUST_RAISE, lastDir)
    }

    @Test
    fun testVolumeDownDispatchesLower() {
        var lastDir = -999
        val executor = VolumeActionExecutor(
            adjustStream = { _, dir, _ -> lastDir = dir }
        )

        val result = executor.adjustVolume(StructuredAction.AdjustVolume("down"))
        assertTrue(result is ExecutionResult.Success)
        assertEquals(AudioManager.ADJUST_LOWER, lastDir)
    }

    @Test
    fun testVolumeMuteAndUnmute() {
        var lastDir = -999
        val executor = VolumeActionExecutor(
            adjustStream = { _, dir, _ -> lastDir = dir }
        )

        val muteRes = executor.adjustVolume(StructuredAction.AdjustVolume("mute"))
        assertTrue(muteRes is ExecutionResult.Success)
        assertEquals(AudioManager.ADJUST_MUTE, lastDir)

        val unmuteRes = executor.adjustVolume(StructuredAction.AdjustVolume("unmute"))
        assertTrue(unmuteRes is ExecutionResult.Success)
        assertEquals(AudioManager.ADJUST_UNMUTE, lastDir)
    }

    @Test
    fun testVolumePercentageClamping() {
        var setVolume = -1
        val maxVol = 25
        val executor = VolumeActionExecutor(
            maxVolume = { maxVol },
            setStream = { _, vol, _ -> setVolume = vol }
        )

        // 50% of 25 = 12 or 13
        val res50 = executor.adjustVolume(StructuredAction.AdjustVolume("set", percent = 50))
        assertTrue(res50 is ExecutionResult.Success)
        assertTrue(setVolume in 12..13)

        // 0% -> 0
        executor.adjustVolume(StructuredAction.AdjustVolume("set", percent = 0))
        assertEquals(0, setVolume)

        // 100% -> maxVol
        executor.adjustVolume(StructuredAction.AdjustVolume("set", percent = 100))
        assertEquals(maxVol, setVolume)

        // Over 100% clamped to maxVol
        executor.adjustVolume(StructuredAction.AdjustVolume("set", percent = 150))
        assertEquals(maxVol, setVolume)

        // Negative clamped to 0
        executor.adjustVolume(StructuredAction.AdjustVolume("set", percent = -10))
        assertEquals(0, setVolume)
    }

    @Test
    fun testUnknownDirectionReturnsFailed() {
        val executor = VolumeActionExecutor()
        val result = executor.adjustVolume(StructuredAction.AdjustVolume("sideways"))
        assertTrue(result is ExecutionResult.Failed)
    }
}

