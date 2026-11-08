package dev.voice.offline.ai.functiongemma

import dev.voice.offline.ai.functioncalling.FunctionCallParser
import dev.voice.offline.ai.functioncalling.FunctionCallingPromptBuilder
import dev.voice.offline.ai.functioncalling.FunctionSchemaValidator
import dev.voice.offline.ai.functioncalling.StructuredAction
import dev.voice.offline.ai.functioncalling.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed interface FunctionGemmaResult {
    data class Success(
        val action: StructuredAction,
        val rawOutput: String,
        val latencyMs: Long
    ) : FunctionGemmaResult

    data class Rejected(
        val reason: String,
        val rawOutput: String
    ) : FunctionGemmaResult

    data class Error(
        val error: Throwable
    ) : FunctionGemmaResult
}

/**
 * High-level engine executing FunctionGemma 270M Mobile Actions locally.
 * Manages prompt construction, model inference dispatch, streaming token accumulation,
 * deterministic function call parsing, and schema validation.
 */
class FunctionGemmaEngine(
    private val adapter: LiteRtInferenceAdapter
) : AutoCloseable {

    suspend fun inferAction(transcript: String, timeoutMs: Long = 5000L): FunctionGemmaResult =
        withContext(Dispatchers.Default) {
            val startTime = System.nanoTime()
            try {
                val prompt = FunctionCallingPromptBuilder.buildPrompt(transcript)
                val outputBuilder = StringBuilder()

                withTimeout(timeoutMs) {
                    if (!adapter.isReady) adapter.initialize()
                    ensureActive()
                    adapter.generate(prompt).collect { chunk ->
                        require(outputBuilder.length + chunk.length <= 16_384) { "Inference output exceeds limit" }
                        outputBuilder.append(chunk)
                    }
                }

                val rawOutput = outputBuilder.toString()
                val rawCall = FunctionCallParser.parse(rawOutput)
                    ?: return@withContext FunctionGemmaResult.Rejected(
                        reason = "Model did not emit a recognized function call syntax",
                        rawOutput = rawOutput
                    )

                val latency = (System.nanoTime() - startTime) / 1_000_000

                when (val validation = FunctionSchemaValidator.validate(rawCall)) {
                    is ValidationResult.Valid -> FunctionGemmaResult.Success(
                        action = validation.action,
                        rawOutput = rawOutput,
                        latencyMs = latency
                    )
                    is ValidationResult.Invalid -> FunctionGemmaResult.Rejected(
                        reason = validation.reason,
                        rawOutput = rawOutput
                    )
                }
            } catch (timeout: TimeoutCancellationException) {
                FunctionGemmaResult.Error(timeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                FunctionGemmaResult.Error(error)
            }
        }

    override fun close() {
        adapter.close()
    }
}
