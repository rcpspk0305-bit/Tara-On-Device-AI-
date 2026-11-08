package dev.voice.offline.ai.benchmark

import dev.voice.offline.ai.functiongemma.FunctionGemmaEngine
import dev.voice.offline.ai.functiongemma.FunctionGemmaResult

data class BenchmarkReport(
    val prompt: String,
    val iterations: Int,
    val successfulRuns: Int,
    val averageLatencyMs: Double,
    val minLatencyMs: Long,
    val maxLatencyMs: Long,
    val memoryDeltaBytes: Long
) {
    val successRate: Double get() = if (iterations == 0) 0.0 else successfulRuns.toDouble() / iterations
}

/**
 * Benchmark harness for measuring on-device FunctionGemma 270M latency,
 * throughput, and memory consumption.
 */
class ModelBenchmark(
    private val engine: FunctionGemmaEngine
) {

    suspend fun benchmarkPrompt(prompt: String, iterations: Int = 5): BenchmarkReport {
        require(iterations > 0) { "Iterations must be > 0" }

        val latencies = mutableListOf<Long>()
        var successCount = 0

        System.gc()
        val memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        for (i in 0 until iterations) {
            val result = engine.inferAction(prompt)
            if (result is FunctionGemmaResult.Success) {
                successCount++
                latencies.add(result.latencyMs)
            } else if (result is FunctionGemmaResult.Rejected) {
                // Counted as evaluated but rejected
            }
        }

        val memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memDelta = (memAfter - memBefore).coerceAtLeast(0L)

        val avg = if (latencies.isEmpty()) 0.0 else latencies.average()
        val min = latencies.minOrNull() ?: 0L
        val max = latencies.maxOrNull() ?: 0L

        return BenchmarkReport(
            prompt = prompt,
            iterations = iterations,
            successfulRuns = successCount,
            averageLatencyMs = avg,
            minLatencyMs = min,
            maxLatencyMs = max,
            memoryDeltaBytes = memDelta
        )
    }
}
