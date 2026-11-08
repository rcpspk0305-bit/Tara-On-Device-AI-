package dev.voice.offline.core.audio

/**
 * Thread-safe, bounded circular ring buffer for 16-bit Mono PCM audio samples.
 * Prevents memory allocation spikes and manages burst audio input.
 */
class AudioRingBuffer(val capacitySamples: Int) {

    private val buffer = ShortArray(capacitySamples)
    private var writeHead = 0
    private var readHead = 0
    private var count = 0
    private val lock = Any()

    val available: Int
        get() = synchronized(lock) { count }

    fun write(input: ShortArray, length: Int): Int = synchronized(lock) {
        val toWrite = minOf(length, capacitySamples - count)
        if (toWrite <= 0) return 0

        for (i in 0 until toWrite) {
            buffer[writeHead] = input[i]
            writeHead = (writeHead + 1) % capacitySamples
        }
        count += toWrite
        return toWrite
    }

    fun read(output: ShortArray, length: Int): Int = synchronized(lock) {
        val toRead = minOf(length, count)
        if (toRead <= 0) return 0

        for (i in 0 until toRead) {
            output[i] = buffer[readHead]
            readHead = (readHead + 1) % capacitySamples
        }
        count -= toRead
        return toRead
    }

    fun clear() = synchronized(lock) {
        writeHead = 0
        readHead = 0
        count = 0
    }
}
