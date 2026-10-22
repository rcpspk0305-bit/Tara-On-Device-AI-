package dev.voice.offline.ai.functiongemma

import dev.voice.offline.ai.functioncalling.StructuredAction
import dev.voice.offline.benchmark.ModelBenchmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FunctionGemmaEngineTest {

    @Test
    fun propagatesCancellationFromAdapter() = runBlocking {
        val adapter = object : LiteRtInferenceAdapter {
            override val isReady = true
            override fun initialize() = Unit
            override suspend fun generate(prompt: String): Flow<String> = flow {
                throw CancellationException("Service stopped")
            }
            override fun close() = Unit
        }
        try {
            FunctionGemmaEngine(adapter).inferAction("open camera")
            fail("Cancellation must propagate to the service")
        } catch (_: CancellationException) {
            // Expected: stopping the service must cancel inference.
        }
    }

    @Test
    fun timesOutWhileInitializingAdapter() = runBlocking {
        val adapter = object : LiteRtInferenceAdapter {
            override val isReady = false
            override fun initialize() { Thread.sleep(60) }
            override suspend fun generate(prompt: String): Flow<String> =
                flowOf("<start_function_call>call:open_camera{}<end_function_call>")
            override fun close() = Unit
        }
        assertTrue(FunctionGemmaEngine(adapter).inferAction("open camera", timeoutMs = 10) is FunctionGemmaResult.Error)
    }

    @Test
    fun rejectsUnboundedGeneratedOutput() = runBlocking {
        val adapter = FakeLiteRtAdapter("x".repeat(100_000))
        assertTrue(FunctionGemmaEngine(adapter).inferAction("open camera") is FunctionGemmaResult.Error)
    }

    private class FakeLiteRtAdapter(private val response: String) : LiteRtInferenceAdapter {
        override var isReady: Boolean = true
        override fun initialize() {}
        override suspend fun generate(prompt: String): Flow<String> = flowOf(response)
        override fun close() { isReady = false }
    }

    @Test
    fun testSuccessfulInferenceAndActionExtraction() = runBlocking {
        val adapter = FakeLiteRtAdapter("<start_function_call>call:turn_flashlight_on{}<end_function_call>")
        val engine = FunctionGemmaEngine(adapter)

        val result = engine.inferAction("turn on the flashlight")
        assertTrue("Inference should succeed", result is FunctionGemmaResult.Success)
        val success = result as FunctionGemmaResult.Success
        assertEquals(StructuredAction.TurnFlashlightOn, success.action)
        assertTrue(success.latencyMs >= 0)
    }

    @Test
    fun testInferenceWithArguments() = runBlocking {
        val adapter = FakeLiteRtAdapter("<start_function_call>call:make_call{contact_name:<escape>Amma<escape>}<end_function_call>")
        val engine = FunctionGemmaEngine(adapter)

        val result = engine.inferAction("call Amma")
        assertTrue(result is FunctionGemmaResult.Success)
        val action = (result as FunctionGemmaResult.Success).action
        assertTrue(action is StructuredAction.MakeCall)
        assertEquals("Amma", (action as StructuredAction.MakeCall).contactName)
    }

    @Test
    fun testInferenceRejectedDueToInvalidFunction() = runBlocking {
        val adapter = FakeLiteRtAdapter("<start_function_call>call:unauthorized_action{}<end_function_call>")
        val engine = FunctionGemmaEngine(adapter)

        val result = engine.inferAction("do something dangerous")
        assertTrue(result is FunctionGemmaResult.Rejected)
        val rejected = result as FunctionGemmaResult.Rejected
        assertTrue(rejected.reason.contains("Unsupported or unknown function"))
    }

    @Test
    fun testModelManagerIntegrityCheck() {
        val tempFile = File.createTempFile("test_model", ".litertlm").apply {
            writeBytes("model_bytes_test".toByteArray())
            deleteOnExit()
        }
        val sha = MobileActionsModelManager.calculateSha256(tempFile)
        assertNotNull(sha)
        assertEquals(64, sha.length)
        assertTrue(MobileActionsModelManager.verifyIntegrity(tempFile, sha))
        assertFalse(MobileActionsModelManager.verifyIntegrity(tempFile, "wrong_hash"))
    }

    @Test
    fun testBenchmarkMeasurement() = runBlocking {
        val adapter = FakeLiteRtAdapter("<start_function_call>call:open_camera{}<end_function_call>")
        val engine = FunctionGemmaEngine(adapter)
        val benchmark = ModelBenchmark(engine)

        val report = benchmark.benchmarkPrompt("open camera", iterations = 3)
        assertEquals(3, report.iterations)
        assertTrue(report.averageLatencyMs >= 0)
        assertEquals(1.0, report.successRate, 0.01)
    }
}
